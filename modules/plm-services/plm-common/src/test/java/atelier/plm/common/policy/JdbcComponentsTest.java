// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.policy;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class JdbcComponentsTest {

    @Test
    void acceptsHostNamesAndIpv4Addresses() {
        for (String host : new String[] {"core-db.cluster-abc.eu-west-1.rds.amazonaws.com", "localhost", "10.0.12.34",
                "a.b", "x1-y2.example", "a".repeat(63) + ".example"}) {
            assertThat(JdbcComponents.host(host, "CORE_DB_HOST")).as(host).isEqualTo(host);
        }
        String longest = String.join(".", Collections.nCopies(4, "a".repeat(62))) + ".a";
        assertThat(longest).hasSize(253);
        assertThat(JdbcComponents.host(longest, "CORE_DB_HOST")).isEqualTo(longest);
    }

    @Test
    void rejectsHostsThatAreNotPlainNamesWithoutEchoingThem() {
        for (String host : new String[] {"", "-core.example", "core-.example", "a".repeat(64) + ".example",
                "core.example/atelier_core", "core.example?sslmode=disable", "core example", "core.example:5432",
                "core..example", ".core.example", "core.example.", "user:secret@example.com",
                String.join(".", Collections.nCopies(4, "a".repeat(62))) + ".aa"}) {
            assertThatIllegalArgumentException().as(host).isThrownBy(() -> JdbcComponents.host(host, "CORE_DB_HOST"))
                    .withMessage("CORE_DB_HOST is not a host name (RFC 1123) or an IPv4 address");
        }
    }

    @Test
    void acceptsPortsFromOneTo65535AndRejectsTheRest() {
        assertThat(JdbcComponents.port("5432", "CORE_DB_PORT")).isEqualTo(5432);
        assertThat(JdbcComponents.port("1", "CORE_DB_PORT")).isEqualTo(1);
        assertThat(JdbcComponents.port("65535", "CORE_DB_PORT")).isEqualTo(65535);
        for (String port : new String[] {"0", "65536", "", "5432a", "-1", "+5432", " 5432", "5432/x", "543210"}) {
            assertThatIllegalArgumentException().as(port).isThrownBy(() -> JdbcComponents.port(port, "CORE_DB_PORT"))
                    .withMessage("CORE_DB_PORT is not a port number between 1 and 65535");
        }
    }

    @Test
    void acceptsLowerCaseIdentifiersAsDatabaseNamesAndRejectsTheRest() {
        for (String database : new String[] {"fr_plm", "atelier_core", "_x", "a", "a".repeat(63)}) {
            assertThat(JdbcComponents.database(database, "CORE_DB_NAME")).as(database).isEqualTo(database);
        }
        for (String database : new String[] {"", "Atelier_core", "1core", "a".repeat(64), "core-db", "core db",
                "core;drop", "core?x=y", "fr_plm\n", "core/x"}) {
            assertThatIllegalArgumentException().as(database).isThrownBy(() -> JdbcComponents.database(database, "CORE_DB_NAME"))
                    .withMessage("CORE_DB_NAME is not a database name ([a-z_][a-z0-9_]{0,62})");
        }
    }
}
