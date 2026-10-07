// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.policy;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Opens the read-only connection to the Atelier core database from CORE_DB_HOST, CORE_DB_PORT (5432),
 * CORE_DB_NAME ({@code atelier_core}), CORE_DB_USER and CORE_DB_PASSWORD; ECS supplies the first four
 * but the name through CORE_DB_SECRET_JSON (see DbSecretEnvironmentPostProcessor). Without a host
 * and user the store is unconfigured and the {@code /tables} policy denies by default. Host, port and
 * database name are checked by {@link JdbcComponents} and handed to the driver's data source as
 * separate properties, never spliced into a URL, so a setting cannot carry extra connection
 * parameters. The pool is private to the store: it is not a DataSource bean, so the service's own
 * datasource stays primary.
 */
@Configuration
public class PartTagStoreConfiguration {

    @Bean
    public PartTagStore partTagStore(@Value("${plm.code}") String plm,
                                     @Value("${CORE_DB_HOST:}") String host,
                                     @Value("${CORE_DB_PORT:5432}") String port,
                                     @Value("${CORE_DB_NAME:atelier_core}") String database,
                                     @Value("${CORE_DB_USER:}") String user,
                                     @Value("${CORE_DB_PASSWORD:}") String password) {
        if (host.isEmpty() || user.isEmpty()) {
            return PartTagStore.unconfigured(plm);
        }
        return new PartTagStore(plm, corePool(host, port, database, user, password));
    }

    /** The store's pool over the PostgreSQL data source, fed the checked components one property each. */
    static HikariDataSource corePool(String host, String port, String database, String user, String password) {
        HikariDataSource pool = new HikariDataSource();
        pool.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource");
        pool.addDataSourceProperty("serverName", JdbcComponents.host(host, "CORE_DB_HOST"));
        pool.addDataSourceProperty("portNumber", JdbcComponents.port(port, "CORE_DB_PORT"));
        pool.addDataSourceProperty("databaseName", JdbcComponents.database(database, "CORE_DB_NAME"));
        pool.setUsername(user);
        pool.setPassword(password);
        pool.setPoolName("core-tags");
        pool.setMaximumPoolSize(2);
        pool.setReadOnly(true);
        return pool;
    }
}
