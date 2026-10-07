// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.policy;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.entry;

/** The pool behind a configured store: the PostgreSQL data source fed one property per component, no URL. */
class PartTagStoreConfigurationTest {

    private final PartTagStoreConfiguration configuration = new PartTagStoreConfiguration();

    @Test
    void handsTheCheckedComponentsToThePostgresDataSourceAsProperties() {
        try (HikariDataSource pool = PartTagStoreConfiguration.corePool("core-db.internal.example", "5433", "atelier_core", "reader", "pw")) {
            assertThat(pool.getDataSourceClassName()).isEqualTo("org.postgresql.ds.PGSimpleDataSource");
            assertThat(pool.getJdbcUrl()).isNull();
            assertThat(pool.getDataSourceProperties()).containsOnly(
                    entry("serverName", "core-db.internal.example"), entry("portNumber", 5433), entry("databaseName", "atelier_core"));
            assertThat(pool.getUsername()).isEqualTo("reader");
            assertThat(pool.getPassword()).isEqualTo("pw");
            assertThat(pool.getPoolName()).isEqualTo("core-tags");
            assertThat(pool.getMaximumPoolSize()).isEqualTo(2);
            assertThat(pool.isReadOnly()).isTrue();
        }
    }

    @Test
    void withoutAHostOrAUserTheStoreIsUnconfigured() {
        assertThat(configuration.partTagStore("uk", "", "5432", "atelier_core", "reader", "pw").isConfigured()).isFalse();
        assertThat(configuration.partTagStore("uk", "core-db.internal.example", "5432", "atelier_core", "", "").isConfigured()).isFalse();
    }

    @Test
    void refusesAHostPortOrDatabaseThatIsNotAPlainComponentAndNamesTheSettingOnly() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> configuration.partTagStore("uk", "core.example/atelier_core?sslmode=disable", "5432", "atelier_core", "reader", "pw"))
                .withMessage("CORE_DB_HOST is not a host name (RFC 1123) or an IPv4 address");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> configuration.partTagStore("uk", "core.example", "5432 ", "atelier_core", "reader", "pw"))
                .withMessage("CORE_DB_PORT is not a port number between 1 and 65535");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> configuration.partTagStore("uk", "core.example", "5432", "atelier_core?x=y", "reader", "pw"))
                .withMessage("CORE_DB_NAME is not a database name ([a-z_][a-z0-9_]{0,62})");
    }
}
