// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class JdbcComponentsTest {

    /** The Ontop source codes (fr, de, uk, es, core) are database names; anything with URL syntax is not. */
    @Test
    void acceptsLowerCaseIdentifiersAsDatabaseNamesAndRejectsTheRest() {
        for (String database : new String[] {"fr_plm", "atelier_core", "fr", "de", "uk", "es", "core", "_x", "a".repeat(63)}) {
            assertThat(JdbcComponents.database(database, "Ontop source")).as(database).isEqualTo(database);
        }
        for (String database : new String[] {"", "Atelier_core", "1core", "a".repeat(64), "core-db", "core db",
                "core;drop", "core?x=y", "fr_plm\n", "core/x"}) {
            assertThatIllegalArgumentException().as(database).isThrownBy(() -> JdbcComponents.database(database, "Ontop source"))
                    .withMessage("Ontop source is not a database name ([a-z_][a-z0-9_]{0,62})");
        }
    }

    /** The URL Ontop reads the dialect from: the placeholder host, port 5432 and the accepted name, nothing else. */
    @Test
    void writesThePlaceholderUrlForAnAcceptedDatabaseName() {
        assertThat(JdbcComponents.placeholderUrl("fr")).isEqualTo("jdbc:postgresql://nohost.invalid:5432/fr");
        assertThat(JdbcComponents.placeholderUrl("atelier_core")).isEqualTo("jdbc:postgresql://nohost.invalid:5432/atelier_core");
    }
}
