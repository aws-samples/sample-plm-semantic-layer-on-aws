// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Expands the RDS secrets ECS injects as JSON (username, password, host, port, dbname) into the
 * properties the datasources read: {@code DB_SECRET_JSON} into DB_USER, DB_PASSWORD, DB_HOST,
 * DB_PORT and DB_NAME for the service's own database; {@code CORE_DB_SECRET_JSON} into
 * CORE_DB_USER, CORE_DB_PASSWORD, CORE_DB_HOST and CORE_DB_PORT for the read-only connection to
 * {@code atelier_core}, whose database name is CORE_DB_NAME and never taken from the secret. The
 * property source sits after the OS environment, so an explicitly set variable wins over the
 * corresponding secret field.
 */
public class DbSecretEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** One injected secret: the variable holding its JSON and the properties its fields expand to. */
    record Secret(String variable, Map<String, String> fieldToProperty) {
    }

    static final Secret OWN_DATABASE = new Secret("DB_SECRET_JSON", Map.of(
            "username", "DB_USER",
            "password", "DB_PASSWORD",
            "host", "DB_HOST",
            "port", "DB_PORT",
            "dbname", "DB_NAME"));

    static final Secret CORE_DATABASE = new Secret("CORE_DB_SECRET_JSON", Map.of(
            "username", "CORE_DB_USER",
            "password", "CORE_DB_PASSWORD",
            "host", "CORE_DB_HOST",
            "port", "CORE_DB_PORT"));

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = new HashMap<>();
        for (Secret secret : new Secret[] {OWN_DATABASE, CORE_DATABASE}) {
            String secretJson = environment.getProperty(secret.variable());
            if (secretJson != null && !secretJson.isBlank()) {
                properties.putAll(parse(secret, secretJson));
            }
        }
        if (properties.isEmpty()) {
            return;
        }
        MapPropertySource source = new MapPropertySource("dbSecretJson", properties);
        if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
        } else {
            environment.getPropertySources().addLast(source);
        }
    }

    static Map<String, Object> parse(Secret secret, String secretJson) {
        JsonNode json;
        try {
            json = new ObjectMapper().readTree(secretJson);
        } catch (IOException e) {
            // The message deliberately omits the payload: it holds the database password.
            throw new IllegalStateException(secret.variable() + " is not valid JSON");
        }
        Map<String, Object> properties = new HashMap<>();
        secret.fieldToProperty().forEach((field, property) -> {
            JsonNode value = json.get(field);
            if (value != null && !value.isNull()) {
                properties.put(property, value.asText());
            }
        });
        return properties;
    }
}
