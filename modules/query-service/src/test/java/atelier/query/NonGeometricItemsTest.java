// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.policy.Caller;
import atelier.query.validation.RuleValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Software and documents are items of a product without geometry: data/generate.py writes each as a row of its site's part
 * table with its part type, no CAD file and its site's revision form, a line under its site kit, a core membership and tag,
 * and no file-index entry; the parts answer lists it with its part type and no cadMissing finding.
 */
class NonGeometricItemsTest {
    private static final Path SERVICES = Path.of("../plm-services");

    private static String read(String path) throws IOException {
        return Files.readString(SERVICES.resolve(path));
    }

    @Test
    void theGeneratorWritesTheFlightSoftwareAsAUkRowWithoutCad() throws IOException {
        String uk = read("plm-uk/src/main/resources/db/migration/R__products_seed.sql");
        assertThat(uk).as("the version is in the name, the revision keeps the UK form, no CAD file, no mass, no purchased-item description, no gear and no option code")
                .contains("('UK-3511', 'Flight software package 1.4.2', NULL, 'C1', 'Released', NULL, NULL, 'SOFTWARE', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)");
        assertThat(uk).as("one line under the UK site kit").containsPattern("\\(\\d+, 1, 'UK-3500', 'UK-3511', 1\\)");
        assertThat(uk).as("the local id UK/3690 of the setting instructions is the row UK-3690")
                .contains("('UK-3690', 'Operating and setting instructions', NULL, 'C1', 'Released', NULL, NULL, 'DOCUMENT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)");
        assertThat(read("plm-de/src/main/resources/db/migration/R__products_seed.sql")).as("a DE document under the DE site kit")
                .contains("('D-36060', 'Nockenprofil- und Steuerzeitentabelle', NULL, '02', 'Freigegeben', NULL, NULL, 'DOCUMENT', 'D-36000', 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)");
        String core = read("atelier-core/src/main/resources/db/migration/R__products_seed.sql");
        assertThat(core).contains("('cubesat', 'uk', 'UK-3511')", "('uk', 'UK-3511', 'NONE', 'ALL', 'UK', ",
                "('de', 'D-36060', 'NATIONAL-DE', 'DE', 'DE', ");
        assertThat(Files.readString(Path.of("../../data/fileindex.ttl"))).as("no CAD file, no file-index entry")
                .doesNotContain("/part/UK-3511>", "/part/UK-3690>", "/part/D-36060>");
    }

    @Test
    void thePartsAnswerListsSoftwareWithItsTypeAndNoCadFinding() {
        QueryService service = new QueryService(new FixtureFederator(ProductFixture.of("cubesat")), new RuleValidator(), new NoOntopSql(),
                new CadUrls(null, null));
        List<Json.Part> parts = service.parts(ProductRulesTest.OFFICER, "cubesat").parts().stream()
                .filter(Json.Part.class::isInstance).map(Json.Part.class::cast).toList();
        Json.Part software = parts.stream().filter(p -> p.id().equals("UK-3511")).findFirst().orElseThrow();
        assertThat(software.partType()).isEqualTo("SOFTWARE");
        assertThat(software.name()).isEqualTo("Flight software package 1.4.2");
        assertThat(software.cadFile()).isNull();
        assertThat(software.cadUrl()).isNull();
        assertThat(software.findings()).as("cadMissing is for parts with geometry").isNullOrEmpty();
        assertThat(parts).filteredOn(p -> "PART".equals(p.partType()))
                .as("the fixture publishes no file index: every part with geometry has its cadMissing finding")
                .allSatisfy(p -> assertThat(p.findings()).extracting(Json.Finding::rule).contains("cadMissing"));
    }
}
