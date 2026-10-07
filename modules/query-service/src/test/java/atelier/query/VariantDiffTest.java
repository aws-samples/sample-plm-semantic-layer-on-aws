// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import atelier.query.api.Json;
import atelier.query.api.VariantJson;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.federation.UnknownItem;
import atelier.query.policy.Caller;
import atelier.query.validation.RuleValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

/**
 * The variant diff over fixtures/variants.ttl and its options graph fixtures/variants-options.ttl: features paired by
 * (host, port), a port the option turns and one it drops, a port not modelled never reported as matching, the
 * interface rules run on the option, the option's rows left out of the base product, and a hidden option part never named.
 */
class VariantDiffTest {
    static final Caller PROGRAMME = Caller.user(EvidenceTest.POLICY.profile("programme-cleared"));
    static final Caller FR = Caller.user(EvidenceTest.POLICY.profile("fr-engineer"));

    private final FixtureFederator federator = new FixtureFederator(fixture(), RDFDataMgr.loadModel("fixtures/variants-options.ttl"));
    private final QueryService service = new QueryService(federator, new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));

    static Model fixture() {
        return RDFDataMgr.loadModel("fixtures/variants.ttl");
    }

    private static Map<String, VariantJson.Port> byPort(VariantJson.Diff diff) {
        return diff.ports().stream().collect(Collectors.toMap(p -> host(p) + "|" + p.port(), Function.identity()));
    }

    private static String host(VariantJson.Port port) {
        return port.host() instanceof Json.PartRef ref ? ref.id() : "hidden";
    }

    @Test
    void featuresArePairedByHostAndPortAndOnlyTheGroupsPortsAreReported() {
        Map<String, VariantJson.Port> ports = byPort(service.variantDiff(PROGRAMME, "slot", "return", "spring"));
        assertThat(ports).containsOnlyKeys("FR-FRAME|return eye", "FR-FRAME|lock pin", "FR-FRAME|spring anchor",
                "ES-PEDAL|heel eye", "ES-PEDAL|toe eye");
        VariantJson.Port eye = ports.get("FR-FRAME|return eye");
        assertThat(eye.change()).isEqualTo("changed");
        assertThat(eye.differences()).as("the frame keeps its feature; 0.3937 in is the 10 mm of the ES pin")
                .containsExactly("interface", "mate", "mate.feature");
        assertThat(eye.against().interfaceId()).isEqualTo("IF-01");
        assertThat(eye.option().interfaceId()).isEqualTo("IF-11");
        assertThat(eye.option().mate()).isEqualTo(new Json.PartRef("UK-SPRING", "uk", null));
        assertThat(((Json.Feature) eye.option().mateFeature()).properties().get("diameter"))
                .isEqualTo(new Json.Quantity(0.3937, "IN", 9.99998, null, null));
    }

    @Test
    void aTurnedPortIsChangedWithItsNewFeatureAndPositionAndADroppedPortIsRemoved() {
        Map<String, VariantJson.Port> ports = byPort(service.variantDiff(PROGRAMME, "slot", "return", "spring"));
        VariantJson.Port heel = ports.get("ES-PEDAL|heel eye");
        assertThat(heel.change()).isEqualTo("changed");
        assertThat(heel.differences()).contains("interface", "mate", "host.feature", "host.position");
        assertThat(((Json.Feature) heel.option().feature()).id()).isEqualTo("ES-PEDAL-R05");
        VariantJson.Port lock = ports.get("FR-FRAME|lock pin");
        assertThat(lock.change()).isEqualTo("removed");
        assertThat(lock.option()).isNull();
        assertThat(lock.against().interfaceId()).isEqualTo("IF-04");
    }

    @Test
    void aPortTheOptionDoesNotModelIsNeverReportedAsMatchingOrRemoved() {
        VariantJson.Diff diff = service.variantDiff(PROGRAMME, "slot", "return", "spring");
        VariantJson.Port toe = byPort(diff).get("ES-PEDAL|toe eye");
        assertThat(toe.change()).isEqualTo("not-modelled");
        assertThat(toe.differences()).isEmpty();
        assertThat(diff.option().portsNotModelled()).containsExactly("toe eye");
    }

    @Test
    void theInterfaceRulesRunOnTheOption() {
        VariantJson.Diff diff = service.variantDiff(PROGRAMME, "slot", "return", "spring");
        VariantJson.Port anchor = byPort(diff).get("FR-FRAME|spring anchor");
        assertThat(anchor.change()).isEqualTo("added");
        assertThat(anchor.option().status()).isEqualTo("fail");
        assertThat(anchor.option().rules()).containsExactly("fastener");
        assertThat(diff.configuration().tally()).isEqualTo(new VariantJson.Tally(3, 1, 0));
        assertThat(diff.baseline().tally()).isEqualTo(new VariantJson.Tally(5, 0, 0));
        assertThat(diff.interfaces()).extracting(Json.Interface::id).containsExactly("IF-11", "IF-12", "IF-13");
        assertThat(diff.interfaces()).as("the options graph labels its interfaces as the links graph does, with rdfs:label")
                .extracting(Json.Interface::label).containsExactly("Spring / frame (return eye)", "Anchor block / frame (spring anchor)", "Spring / pedal (heel eye)");
        assertThat(diff.removed().interfaces()).containsExactly("IF-01", "IF-02", "IF-04", "IF-05");
        assertThat(diff.removed().items()).containsExactly(new Json.PartRef("ES-ARM", "es", null));
        assertThat(diff.added().items()).containsExactly(new Json.PartRef("DE-ANCHOR", "de", null), new Json.PartRef("UK-SPRING", "uk", null));
    }

    @Test
    void eachConfigurationCountsItsOwnTreesAndMass() {
        VariantJson.Diff diff = service.variantDiff(PROGRAMME, "slot", "return", "spring");
        assertThat(diff.baseline().occurrences()).isEqualTo(Map.of("es", 3.0, "fr", 1.0));
        assertThat(diff.baseline().massKg()).isEqualTo(17.0);
        assertThat(diff.configuration().occurrences()).as("the ES arms leave with the cord; two UK springs and the DE block come")
                .isEqualTo(Map.of("es", 1.0, "fr", 1.0, "uk", 2.0, "de", 1.0));
        assertThat(diff.configuration().massKg()).isEqualTo(18.5);
    }

    @Test
    void theOptionsRowsStayOutOfTheBaseProduct() {
        Json.InterfacesResponse base = service.interfaces(PROGRAMME, "slot");
        assertThat(base.interfaces()).extracting(Json.Interface::id).containsExactly("IF-01", "IF-02", "IF-03", "IF-04", "IF-05");
        assertThat(federator.last().model().listSubjects().toList()).extracting(r -> String.valueOf(r.getURI()))
                .noneMatch(iri -> iri.contains("FR-FRAME-F03") || iri.contains("ES-PEDAL-R05") || iri.contains("UK-SPRING"));
    }

    @Test
    void aHiddenOptionPartIsAMarkerAndItsInterfaceIsNotEvaluable() throws Exception {
        VariantJson.Diff diff = service.variantDiff(FR, "slot", "return", "spring");
        VariantJson.Port anchor = byPort(diff).get("FR-FRAME|spring anchor");
        assertThat(anchor.option().status()).isEqualTo("not-evaluable");
        assertThat(anchor.option().mate()).isEqualTo(new Json.Redacted(true, "de"));
        assertThat(anchor.option().mateFeature()).isEqualTo(new Json.Redacted(true, "de"));
        assertThat(diff.added().items()).containsExactly(new Json.Redacted(true, "de"), new Json.PartRef("UK-SPRING", "uk", null));
        assertThat(diff.configuration().tally()).isEqualTo(new VariantJson.Tally(3, 0, 1));
        // The request texts name the parts the core graph is asked to tag, hidden ones too, as every answer's do.
        com.fasterxml.jackson.databind.node.ObjectNode answer = new ObjectMapper().valueToTree(diff);
        answer.remove("sparql");
        assertThat(answer.toString()).doesNotContain("DE-ANCHOR").doesNotContain("Ankerblock");
    }

    @Test
    void theProductsGroupsListTheirOptionsTheDefaultFirst() {
        assertThat(service.variants("slot").groups()).containsExactly(
                new VariantJson.Group("return", "Arm return", "what returns the arm to its rest", "cord", List.of("cord", "spring")));
        assertThat(service.variants("ornithopter").groups()).isEmpty();
    }

    @Test
    void theDefaultOptionAndAnUnknownOptionAreRefused() {
        assertThatThrownBy(() -> service.variantDiff(PROGRAMME, "slot", "return", "cord")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.variantDiff(PROGRAMME, "slot", "return", "magnet")).isInstanceOf(UnknownItem.class)
                .hasMessageContaining("return (cord, spring)");
        assertThatThrownBy(() -> service.variantDiff(PROGRAMME, "slot", "return", "x' } ?s ?p ?o {"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
