// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static atelier.plm.common.config.DbSecretEnvironmentPostProcessor.CORE_DATABASE;
import static atelier.plm.common.config.DbSecretEnvironmentPostProcessor.OWN_DATABASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DbSecretEnvironmentPostProcessorTest {

    static final String SECRET =
            "{\"username\":\"u\",\"password\":\"p\",\"host\":\"h\",\"port\":5433,\"dbname\":\"d\",\"engine\":\"postgres\"}";

    @Test
    void mapsRdsSecretFieldsToDbVariables() {
        assertThat(DbSecretEnvironmentPostProcessor.parse(OWN_DATABASE, SECRET)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "DB_USER", "u", "DB_PASSWORD", "p", "DB_HOST", "h", "DB_PORT", "5433", "DB_NAME", "d"));
    }

    @Test
    void coreSecretMapsTheConnectionButLeavesTheDatabaseNameToCoreDbName() {
        assertThat(DbSecretEnvironmentPostProcessor.parse(CORE_DATABASE, SECRET)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "CORE_DB_USER", "u", "CORE_DB_PASSWORD", "p", "CORE_DB_HOST", "h", "CORE_DB_PORT", "5433"));
    }

    @Test
    void invalidJsonErrorNamesTheVariableButDoesNotEchoPayload() {
        assertThatThrownBy(() -> DbSecretEnvironmentPostProcessor.parse(CORE_DATABASE, "{\"password\":\"s3cret\""))
                .hasMessageContaining("CORE_DB_SECRET_JSON")
                .hasMessageNotContaining("s3cret");
    }
}
