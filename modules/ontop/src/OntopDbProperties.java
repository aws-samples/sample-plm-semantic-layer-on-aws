// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;
import java.util.regex.Pattern;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Writes the Ontop JDBC properties file (owner read/write only) from the environment:
 * JDBC_URL, DB_USER and DB_PASSWORD, or the RDS secret ECS injects as DB_SECRET_JSON
 * (host, port, username, password, optional dbname) together with DB_NAME. An explicitly set
 * variable wins over the corresponding secret field. The host, port and database name are each
 * checked (RFC 1123 host name or IPv4 address, port 1 to 65535, name matching
 * {@code [a-z_][a-z0-9_]{0,62}}) so that a field cannot smuggle a path or extra connection
 * parameters in, then handed to the driver's {@link PGSimpleDataSource}, which writes the URL text
 * Ontop reads from {@code jdbc.url}. Error messages never include values.
 */
public final class OntopDbProperties {

    private static final Pattern HOST = Pattern.compile(
            "[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*");
    private static final Pattern PORT = Pattern.compile("[0-9]{1,5}");
    private static final Pattern DATABASE = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    public static void main(String[] args) throws IOException {
        JsonNode secret = secret();
        String user = firstOf("DB_USER", secret, "username");
        String password = firstOf("DB_PASSWORD", secret, "password");
        String url = env("JDBC_URL");
        if (url == null) {
            String host = host(require(field(secret, "host"), "JDBC_URL or DB_SECRET_JSON.host"), "DB_SECRET_JSON.host");
            String port = field(secret, "port");
            int portNumber = port == null ? 5432 : port(port, "DB_SECRET_JSON.port");
            String db = database(require(firstOf("DB_NAME", secret, "dbname"), "DB_NAME"), "DB_NAME or DB_SECRET_JSON.dbname");
            PGSimpleDataSource dataSource = new PGSimpleDataSource();
            dataSource.setServerNames(new String[] {host});
            dataSource.setPortNumbers(new int[] {portNumber});
            dataSource.setDatabaseName(db);
            url = dataSource.getUrl();
        }

        Properties properties = new Properties();
        properties.setProperty("jdbc.url", url);
        properties.setProperty("jdbc.user", require(user, "DB_USER or DB_SECRET_JSON.username"));
        properties.setProperty("jdbc.password", require(password, "DB_PASSWORD or DB_SECRET_JSON.password"));
        properties.setProperty("jdbc.driver", "org.postgresql.Driver");

        Path target = Paths.get(args[0]);
        Files.deleteIfExists(target);
        Files.createFile(target, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try (Writer out = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            properties.store(out, "Ontop JDBC settings written at container start");
        }
    }

    private static JsonNode secret() {
        String json = env("DB_SECRET_JSON");
        if (json == null) {
            return null;
        }
        try {
            return new ObjectMapper().readTree(json);
        } catch (IOException e) {
            // No cause attached: the parser's message can quote the payload, which holds the password.
            throw new IllegalStateException("DB_SECRET_JSON is not valid JSON");
        }
    }

    private static String firstOf(String variable, JsonNode secret, String secretField) {
        String value = env(variable);
        return value != null ? value : field(secret, secretField);
    }

    private static String field(JsonNode secret, String name) {
        JsonNode value = secret == null ? null : secret.get(name);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? null : value;
    }

    private static String require(String value, String what) {
        if (value == null) {
            throw new IllegalStateException("missing " + what);
        }
        return value;
    }

    /** An RFC 1123 host name: labels of letters, digits and hyphens, 1 to 63 characters each, 253 in all; an IPv4 dotted quad is one. */
    private static String host(String value, String what) {
        if (value.length() > 253 || !HOST.matcher(value).matches()) {
            throw new IllegalStateException(what + " is not a host name (RFC 1123) or an IPv4 address");
        }
        return value;
    }

    private static int port(String value, String what) {
        int port = PORT.matcher(value).matches() ? Integer.parseInt(value) : 0;
        if (port < 1 || port > 65535) {
            throw new IllegalStateException(what + " is not a port number between 1 and 65535");
        }
        return port;
    }

    private static String database(String value, String what) {
        if (!DATABASE.matcher(value).matches()) {
            throw new IllegalStateException(what + " is not a database name ([a-z_][a-z0-9_]{0,62})");
        }
        return value;
    }
}
