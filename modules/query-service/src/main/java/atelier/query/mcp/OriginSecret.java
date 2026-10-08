// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mcp;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

/**
 * The {@code x-origin-verify} value the API Gateway authorizer expects: {@code ORIGIN_SECRET} when
 * set, else the secret string at {@code ORIGIN_SECRET_ARN} in Secrets Manager (the ECS task role
 * may read it), read once on first use; null when neither is set (local runs), in which case no
 * header is sent.
 */
@Component
public class OriginSecret implements Supplier<String> {
    private static final Logger log = LoggerFactory.getLogger(OriginSecret.class);

    private final String value;
    private final String arn;
    private volatile String resolved;

    public OriginSecret(@Value("${ORIGIN_SECRET:}") String value, @Value("${ORIGIN_SECRET_ARN:}") String arn) {
        this.value = value.isBlank() ? null : value;
        this.arn = arn.isBlank() ? null : arn;
        if (this.value == null && this.arn == null) {
            log.info("Neither ORIGIN_SECRET nor ORIGIN_SECRET_ARN is set: PLM calls carry no x-origin-verify header");
        }
    }

    @Override
    public String get() {
        if (value != null || arn == null) return value;
        if (resolved == null) {
            synchronized (this) {
                if (resolved == null) resolved = read();
            }
        }
        return resolved;
    }

    private String read() {
        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder()).build()) {
            String secret = client.getSecretValue(GetSecretValueRequest.builder().secretId(arn).build()).secretString();
            log.info("Origin secret read from {}", arn);
            return secret;
        } catch (RuntimeException e) {
            log.warn("Origin secret {} could not be read: {}", arn, e.toString());
            throw new IllegalStateException("the origin secret could not be read");
        }
    }
}
