// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.policy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The visibility decision over the core tags of the tables fixture (one British part per releasability
 * token, one untagged) and the shipped policy: what each profile's plain routes may return.
 */
class PartVisibilityTest {

    /** A British feature row: its own key and the part carrying it. */
    record Feature(String ref, String compId) {
    }

    static final List<Feature> CONNECTORS = List.of(
            new Feature("PL 6190-01", "ACTR-6190-L"), new Feature("PL 6200-01", "HARN-6200-L"),
            new Feature("PL ALL-01", "PNL-ALL"), new Feature("PL DE-01", "BRK-DE"), new Feature("PL FR-01", "RIB-FR"),
            new Feature("PL LIC-01", "ACT-LIC"), new Feature("PL UNT-01", "UNT-UK"));

    static final Policy POLICY = Policy.load(Path.of("src/test/resources/policy.json"));

    static PartTagStore coreTags() {
        DriverManagerDataSource core = new DriverManagerDataSource(
                "jdbc:h2:mem:visibility;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("tables/core-fixture.sql")).execute(core);
        return new PartTagStore("uk", core);
    }

    final PartVisibility visibility = new PartVisibility(POLICY, coreTags());

    @Test
    void nationalEngineerSeesOwnNationalPartsNotAnotherNationsNorTheLicensedOne() {
        assertThat(visibility.parts("de-engineer")).containsExactlyInAnyOrder("ACTR-6190-L", "HARN-6200-L", "PNL-ALL", "BRK-DE");
        assertThat(visibility.sees("RIB-FR", "de-engineer")).isFalse();
        assertThat(visibility.sees("RIB-FR", "fr-engineer")).isTrue();
        assertThat(visibility.sees("ACT-LIC", "programme-cleared")).isFalse();
    }

    @Test
    void officerSeesEveryTaggedPartAndNobodySeesAnUntaggedOne() {
        assertThat(visibility.parts("export-officer"))
                .containsExactlyInAnyOrder("ACTR-6190-L", "HARN-6200-L", "PNL-ALL", "BRK-DE", "RIB-FR", "ACT-LIC");
        assertThat(visibility.sees("UNT-UK", "export-officer")).isFalse();
    }

    @Test
    void missingOrUnknownProfileSeesPartsReleasableToAllOnly() {
        assertThat(visibility.parts(null)).containsExactly("PNL-ALL");
        assertThat(visibility.parts("nobody")).containsExactly("PNL-ALL");
    }

    @Test
    void featureRowsFollowTheirPartInTheOrderGiven() {
        assertThat(visibility.keep(CONNECTORS, Feature::compId, "de-engineer")).extracting(Feature::ref)
                .containsExactly("PL 6190-01", "PL 6200-01", "PL ALL-01", "PL DE-01");
        assertThat(visibility.keep(CONNECTORS, Feature::compId, "export-officer")).extracting(Feature::ref)
                .containsExactly("PL 6190-01", "PL 6200-01", "PL ALL-01", "PL DE-01", "PL FR-01", "PL LIC-01");
        assertThat(visibility.keep(CONNECTORS, Feature::compId, null)).extracting(Feature::ref).containsExactly("PL ALL-01");
    }

    @Test
    void unconfiguredTagStoreMakesEveryPartInvisible() {
        PartVisibility denied = new PartVisibility(POLICY, PartTagStore.unconfigured("uk"));

        assertThat(denied.parts("export-officer")).isEmpty();
        assertThat(denied.sees("PNL-ALL", "export-officer")).isFalse();
        assertThat(denied.keep(CONNECTORS, Feature::compId, "export-officer")).isEmpty();
    }
}
