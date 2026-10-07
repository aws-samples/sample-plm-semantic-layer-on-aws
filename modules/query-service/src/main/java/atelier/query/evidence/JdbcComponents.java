// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.evidence;

import java.util.regex.Pattern;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Checks a database name before it enters a JDBC URL, so that a setting cannot smuggle a path or
 * extra connection parameters into it: the name matches {@code [a-z_][a-z0-9_]{0,62}}. A rejection
 * names the setting, never its value. The host and port of the query service's URL are constants,
 * and the driver's {@link PGSimpleDataSource} writes the URL text.
 */
final class JdbcComponents {
    private static final Pattern DATABASE = Pattern.compile("[a-z_][a-z0-9_]{0,62}");
    private static final String PLACEHOLDER_HOST = "nohost.invalid";
    private static final int PLACEHOLDER_PORT = 5432;

    private JdbcComponents() {
    }

    static String database(String value, String setting) {
        if (!DATABASE.matcher(value).matches()) {
            throw new IllegalArgumentException(setting + " is not a database name ([a-z_][a-z0-9_]{0,62})");
        }
        return value;
    }

    /**
     * The JDBC URL Ontop reads the PostgreSQL dialect from, for a database name {@link #database}
     * has accepted: the placeholder host is never connected.
     */
    static String placeholderUrl(String database) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setServerNames(new String[] {PLACEHOLDER_HOST});
        dataSource.setPortNumbers(new int[] {PLACEHOLDER_PORT});
        dataSource.setDatabaseName(database);
        return dataSource.getUrl();
    }
}
