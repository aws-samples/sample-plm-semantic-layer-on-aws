// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import atelier.query.api.Json;
import atelier.query.api.StationsJson;
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
 * The stations and sections answers over the drivetrain of fixtures/bom.ttl with the placements of fixtures/placements.ttl
 * and the stations, sections and spans of fixtures/stations.ttl: the parts between two stations inside or crossing, a
 * placed part's occurrences in the range (a rotated occurrence by its stored envelope), a British span in inches
 * converted to millimetres, the inferred station labelled in every answer, the joints with the parts crossing them, and
 * the parts and the section part a profile may not see left out or redacted.
 */
class StationsTest {
    static final String PRODUCT = "drivetrain";
    static final Caller UK = PlacementsTest.UK;
    static final Caller FR = PlacementsTest.FR;

    private final FixtureFederator federator = new FixtureFederator(fixture());
    private final QueryService service = new QueryService(federator, new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));

    static Model fixture() {
        Model model = FixtureFederator.fixture();
        for (String file : List.of("fixtures/bom.ttl", "fixtures/placements.ttl", "fixtures/stations.ttl")) RDFDataMgr.read(model, file);
        return Closures.with(model);
    }

    private static Map<String, StationsJson.SpanPart> byPart(StationsJson.Between answer) {
        return answer.parts().stream().collect(Collectors.toMap(p -> p.plm() + "/" + p.id(), p -> p));
    }

    @Test
    void thePartsBetweenTwoStationsAreInsideOrCrossingTheRange() {
        StationsJson.Between answer = service.stations(UK, PRODUCT, "ST 300", "ST 600", "right");
        Map<String, StationsJson.SpanPart> parts = byPart(answer);
        assertThat(answer.range().intervalsMm()).containsExactly(List.of(300.0, 600.0));
        assertThat(parts.keySet()).containsExactlyInAnyOrder("de/de-gear", "de/de-arm", "uk/uk-motor", "de/de-housing", "fr/fr-panel", "es/es-frame");
        assertThat(parts.get("de/de-gear").position()).isEqualTo("inside");
        assertThat(parts.get("de/de-housing").position()).isEqualTo("crossing");
        assertThat(parts.get("es/es-frame").position()).isEqualTo("crossing");
        assertThat(answer.inside()).isEqualTo(3);
        assertThat(answer.crossing()).isEqualTo(3);
        assertThat(answer.parts().subList(0, 2)).extracting(StationsJson.SpanPart::position).containsOnly("inside");
    }

    @Test
    void aPlacedPartListsItsOccurrencesInTheRange() {
        Map<String, StationsJson.SpanPart> parts = byPart(service.stations(UK, PRODUCT, "ST 300", "ST 600", "right"));
        StationsJson.SpanPart panel = parts.get("fr/fr-panel");
        assertThat(panel.occurrenceCount()).as("the cover set, FR only, contributes no occurrence").isEqualTo(2);
        assertThat(panel.occurrences()).containsExactly(new StationsJson.Occurrence(2, 550.0, 650.0, "crossing"));
        assertThat(parts.get("de/de-gear").occurrenceCount()).isEqualTo(6);
        assertThat(parts.get("de/de-housing").occurrences()).as("used once").isNull();
    }

    @Test
    void aRotatedOccurrenceSpansItsStoredEnvelope() {
        Map<String, StationsJson.SpanPart> parts = byPart(service.stations(UK, PRODUCT, "ST 0", "ST 300", "left"));
        StationsJson.SpanPart bracket = parts.get("es/es-bracket");
        assertThat(bracket.occurrences()).as("turned 90 degrees about x, the second bracket reaches to y -100")
                .containsExactly(new StationsJson.Occurrence(2, -100.0, 0.0, "inside"));
        assertThat(bracket.fromMm()).isEqualTo(0.0);
    }

    @Test
    void aChildUnderATurnedParentSpansItsTrueEnvelope() {
        StationsJson.SpanPart arm = byPart(service.stations(UK, PRODUCT, "ST 300", "ST 600", "right")).get("de/de-arm");
        assertThat(arm.occurrenceCount()).isEqualTo(4);
        assertThat(arm.occurrences()).as("the second arm set, turned 180 degrees about z at y 1000, sends y to 1000 - y").containsExactly(
                new StationsJson.Occurrence(1, 400.0, 420.0, "inside"), new StationsJson.Occurrence(2, 450.0, 470.0, "inside"),
                new StationsJson.Occurrence(3, 580.0, 600.0, "inside"), new StationsJson.Occurrence(4, 530.0, 550.0, "inside"));
    }

    @Test
    void aBritishSpanIsStoredInInchesAndAnsweredInMillimetres() {
        StationsJson.SpanPart motor = byPart(service.stations(UK, PRODUCT, "ST 300", "ST 600", "right")).get("uk/uk-motor");
        assertThat(motor.span()).containsExactly(11.811, 15.748);
        assertThat(motor.unit()).isEqualTo("IN");
        assertThat(motor.fromMm()).isEqualTo(299.999);
        assertThat(motor.toMm()).isEqualTo(399.999);
        assertThat(motor.occurrences()).extracting(StationsJson.Occurrence::index).containsExactly(1);
        assertThat(motor.stations()).extracting(StationsJson.StationRef::id).containsExactly("ST 300");
    }

    @Test
    void anInferredStationIsLabelledInferredInEveryAnswer() {
        StationsJson.Between between = service.stations(UK, PRODUCT, "ST 0", "ST 300", null);
        assertThat(between.range().from()).isEqualTo(new StationsJson.StationRef("ST 0", "inferred"));
        assertThat(between.range().side()).isEqualTo("both");
        assertThat(between.range().intervalsMm()).containsExactly(List.of(-300.0, 300.0));
        assertThat(between.notes()).anyMatch(n -> n.startsWith("ST 0 is inferred"));
        StationsJson.Sections sections = service.sections(UK, PRODUCT);
        assertThat(sections.sections().getFirst().stations()).first().isEqualTo(new StationsJson.StationRef("ST 0", "inferred"));
        assertThat(sections.notes()).anyMatch(n -> n.startsWith("ST 0 is inferred"));
        assertThat(sections.stations()).extracting(StationsJson.Station::basis).containsExactly("inferred", "joint", "measured");
        assertThat(sections.stations().getLast().toleranceMm()).isEqualTo(5.0);
    }

    @Test
    void eachJointListsTheSectionsTheirOwnersAndThePartsCrossingIt() {
        StationsJson.Sections answer = service.sections(UK, PRODUCT);
        Map<Double, StationsJson.Joint> joints = answer.joints().stream().collect(Collectors.toMap(StationsJson.Joint::atMm, j -> j));
        assertThat(joints.keySet()).as("the two wing sections lie on opposite sides: no joint between them").containsExactlyInAnyOrder(-300.0, 300.0);
        StationsJson.Joint right = joints.get(300.0);
        assertThat(right.sections()).containsExactly("S-C", "S-R");
        assertThat(right.owners()).containsExactly("FR", "DE");
        assertThat(right.side()).isEqualTo("right");
        assertThat(right.crossing()).extracting(StationsJson.SectionPart::id).containsExactly("de-housing", "es-frame");
        assertThat(joints.get(-300.0).crossing()).extracting(StationsJson.SectionPart::id).containsExactly("es-frame");
        StationsJson.Section wing = answer.sections().stream().filter(s -> s.id().equals("S-R")).findFirst().orElseThrow();
        assertThat(wing.foreign()).extracting(StationsJson.SectionPart::id).containsExactlyInAnyOrder("fr-panel", "uk-motor", "es-frame");
    }

    @Test
    void aPartAProfileMayNotSeeIsNeverNamed() {
        assertThat(byPart(service.stations(FR, PRODUCT, "ST 0", "ST 300", "right"))).containsKey("fr/fr-hinge");
        StationsJson.Between uk = service.stations(UK, PRODUCT, "ST 0", "ST 300", "right");
        assertThat(byPart(uk)).doesNotContainKey("fr/fr-hinge");
        assertThat(federator.last().queries().get("ontop-fr").stream()
                .filter(q -> q.contains("?part atelier:spanFrom"))).singleElement().asString().doesNotContain("fr-hinge");
        StationsJson.Section centre = service.sections(UK, PRODUCT).sections().stream().filter(s -> s.id().equals("S-C")).findFirst().orElseThrow();
        assertThat(centre.part()).isEqualTo(new Json.Redacted(true, "fr"));
        assertThat(service.sections(FR, PRODUCT).sections().stream().filter(s -> s.id().equals("S-C")).findFirst().orElseThrow().part())
                .isEqualTo(new Json.PartRef("fr-hinge", "fr", null));
    }

    @Test
    void anUnknownStationOrSideIsRefused() {
        assertThatThrownBy(() -> service.stations(UK, PRODUCT, "ST 300", "ST 900", null)).hasMessageContaining("unknown station ST 900");
        assertThatThrownBy(() -> service.stations(UK, PRODUCT, "ST 0", "ST 300", "up")).hasMessageContaining("side is left, right or both");
    }

    @Test
    void theStationsRunAsksEachSiteForItsSpansAndTheLinkStoreForTheStations() {
        service.stations(UK, PRODUCT, "ST 0", "ST 600", null);
        Map<String, List<String>> queries = federator.last().queries();
        assertThat(queries.get(Endpoints.LINK_STORE).stream().filter(q -> q.contains("atelier:Station"))).hasSize(1);
        assertThat(queries.get("ontop-uk").stream().filter(q -> q.contains("atelier:spanFrom"))).hasSize(2);
    }
}
