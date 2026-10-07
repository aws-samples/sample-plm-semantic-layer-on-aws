// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.policy;

import java.util.regex.Pattern;

/**
 * Checks the components of a PostgreSQL connection before they reach the driver, so that a
 * setting cannot smuggle a different host, a path or extra connection parameters into the
 * connection: the host is an RFC 1123 host name (labels of letters, digits and hyphens, 1 to 63
 * characters each, 253 in all; an IPv4 dotted quad is one), the port is an integer from 1 to 65535
 * and the database name matches {@code [a-z_][a-z0-9_]{0,62}}. A rejection names the setting,
 * never its value: a value may travel with a credential.
 */
final class JdbcComponents {

    private static final Pattern HOST = Pattern.compile(
            "[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*");
    private static final Pattern PORT = Pattern.compile("[0-9]{1,5}");
    private static final Pattern DATABASE = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    private JdbcComponents() {
    }

    static String host(String value, String setting) {
        if (value.length() > 253 || !HOST.matcher(value).matches()) {
            throw new IllegalArgumentException(setting + " is not a host name (RFC 1123) or an IPv4 address");
        }
        return value;
    }

    static int port(String value, String setting) {
        int port = PORT.matcher(value).matches() ? Integer.parseInt(value) : 0;
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(setting + " is not a port number between 1 and 65535");
        }
        return port;
    }

    static String database(String value, String setting) {
        if (!DATABASE.matcher(value).matches()) {
            throw new IllegalArgumentException(setting + " is not a database name ([a-z_][a-z0-9_]{0,62})");
        }
        return value;
    }
}
