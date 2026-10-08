// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.federation.Endpoints;
import atelier.query.policy.Caller;
import atelier.query.validation.RuleValidator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

/**
 * The world placements over the drivetrain of fixtures/bom.ttl with the line placements of fixtures/placements.ttl:
 * occurrences composed down a three-level tree (the parent's after the child's), a British placement in inches converted
 * to millimetres, a line without placements drawn once per occurrence of its parent, the placements of a hidden item
 * left in its database and a path through a hidden assembly contributing nothing, one request per site by its roots,
 * and the subtree of one assembly composed from the assembly.
 */
class PlacementsTest {
    static final String PRODUCT = "drivetrain";
    static final Caller UK = Caller.user(EvidenceTest.POLICY.profile("uk-engineer"));
    static final Caller FR = Caller.user(EvidenceTest.POLICY.profile("fr-engineer"));

    private final FixtureFederator federator = new FixtureFederator(fixture());
    private final QueryService service = new QueryService(federator, new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));

    static Model fixture() {
        Model model = FixtureFederator.fixture();
        for (String file : List.of("fixtures/bom.ttl", "fixtures/placements.ttl")) RDFDataMgr.read(model, file);
        return Closures.with(model);
    }

    private static Map<String, List<List<Double>>> byPart(Json.Placements answer) {
        return answer.parts().stream().collect(Collectors.toMap(p -> p.plm() + "/" + p.id(), Json.PartPlacements::occurrences));
    }

    private static List<Double> at(double... values) {
        return java.util.Arrays.stream(values).boxed().toList();
    }

    @Test
    void occurrencesComposeDownAThreeLevelTreeTheParentsAfterTheChilds() {
        Map<String, List<List<Double>>> parts = byPart(service.placements(UK, PRODUCT, null));
        assertThat(parts.get("de/de-gear")).containsExactly(
                at(0, 0, 0, 0, 0, 0), at(10, 0, 0, 0, 0, 0), at(20, 0, 0, 0, 0, 0),
                at(0, 0, 100, 0, 0, 90), at(0, 10, 100, 0, 0, 90), at(0, 20, 100, 0, 0, 90));
        assertThat(parts.get("de/de-bolt-kit")).as("a line without placements, once per occurrence of its parent")
                .containsExactly(at(0, 0, 0, 0, 0, 0), at(0, 0, 100, 0, 0, 90));
        assertThat(parts.get("de/de-housing")).containsExactly(at(0, 0, 0, 0, 0, 0));
        assertThat(parts).doesNotContainKeys("de/de-kit", "de/de-gearbox", "de/de-stage");
    }

    @Test
    void aBritishPlacementInInchesIsAnsweredInMillimetres() {
        assertThat(byPart(service.placements(UK, PRODUCT, null)).get("uk/uk-motor"))
                .containsExactly(at(0, 0, 0, 0, 0, 0), at(254, 0, 0, 0, 0, 180));
    }

    @Test
    void theSpanishDocumentPlacementTurnsAboutX() {
        assertThat(byPart(service.placements(UK, PRODUCT, null)).get("es/es-bracket"))
                .containsExactly(at(0, 0, 0, 0, 0, 0), at(0, 0, 0, 90, 0, 0));
    }

    @Test
    void aHiddenItemHasNoPlacementsAndAPathThroughAHiddenAssemblyContributesNothing() {
        Json.Placements answer = service.placements(UK, PRODUCT, null);
        Map<String, List<List<Double>>> parts = byPart(answer);
        assertThat(parts).doesNotContainKey("fr/fr-hinge");
        assertThat(parts.get("fr/fr-panel")).as("under the kit only: the cover set is hidden")
                .containsExactly(at(0, 0, 0, 0, 0, 0), at(0, 500, 0, 0, 0, 0));
        assertThat(federator.last().model().listSubjectsWithProperty(federator.last().model().createProperty(Atelier.ONT + "ofLine"))
                .toList()).noneMatch(o -> o.getURI().contains("/fr-hinge/"));
        String fr = placementsRequest("fr");
        assertThat(fr).as("the hidden items stay in the database").contains("FILTER (?child NOT IN (")
                .contains("fr/part/fr-hinge").contains("fr/part/fr-cover-set");
    }

    @Test
    void aProfileThatSeesTheCoverSetGetsBothPathsTheParentsInIdOrder() {
        Map<String, List<List<Double>>> parts = byPart(service.placements(FR, PRODUCT, null));
        assertThat(parts.get("fr/fr-panel")).containsExactly(at(0, 0, 0, 0, 0, 0), at(0, 0, 50, 0, 0, 0), at(0, 0, 100, 0, 0, 0),
                at(0, 0, 150, 0, 0, 0), at(0, 0, 0, 0, 0, 0), at(0, 500, 0, 0, 0, 0));
        assertThat(parts.get("fr/fr-hinge")).containsExactly(at(0, 0, 0, 0, 0, 0), at(5, 0, 0, 0, 0, 0));
    }

    @Test
    void eachSiteIsAskedOnceForItsPlacementsByItsKitNeverByItsParts() {
        Json.Placements answer = service.placements(UK, PRODUCT, null);
        for (String plm : Atelier.PLMS) {
            String request = placementsRequest(plm);
            assertThat(request).contains("VALUES ?root {\n    <https://example.com/atelier/" + plm + "/part/" + plm + "-kit>\n  }")
                    .doesNotContain("VALUES ?part");
        }
        assertThat(answer.occurrences()).isEqualTo(answer.parts().stream().mapToInt(p -> p.occurrences().size()).sum());
    }

    @Test
    void aSubtreeComposesFromItsRootAtItsReferenceOccurrence() {
        Json.Placements answer = service.placements(UK, PRODUCT, "de-stage");
        Map<String, List<List<Double>>> parts = byPart(answer);
        assertThat(parts.keySet()).containsExactlyInAnyOrder("de/de-gear", "de/de-bolt-kit");
        assertThat(parts.get("de/de-gear")).containsExactly(at(0, 0, 0, 0, 0, 0), at(10, 0, 0, 0, 0, 0), at(20, 0, 0, 0, 0, 0));
        assertThat(answer.subtree()).isNotNull();
        assertThat(placementsRequest("de")).contains("<https://example.com/atelier/de/part/de-stage>");
    }

    /** The placements request the last run sent to a site's endpoint. */
    private String placementsRequest(String plm) {
        List<String> sent = federator.last().queries().get(Endpoints.ontopName(plm));
        return sent.stream().filter(q -> q.contains("atelier:ofLine")).reduce((a, b) -> b).orElseThrow();
    }
}
