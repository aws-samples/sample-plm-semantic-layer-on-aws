// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import atelier.query.api.Json;
import atelier.query.mapping.InterfaceMapper;
import atelier.query.mapping.Lifecycles;
import atelier.query.mapping.Units;
import atelier.query.validation.RuleValidator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

/** SHACL report to contract JSON, on fixtures/neighbourhood.ttl (every part visible, no CAD bucket). */
class InterfaceMapperTest {
    private final RuleValidator validator = new RuleValidator();
    private final Model model = RulesTest.fixture();
    private final Map<String, Json.Interface> byId = interfaces(model);

    private Map<String, Json.Interface> interfaces(Model data) {
        return new InterfaceMapper(data, new Units(validator.units()), new Lifecycles(validator.lifecycle()), validator.validate(data), cad -> null)
                .interfaces().stream().collect(Collectors.toMap(Json.Interface::id, i -> i));
    }

    @Test
    void statusPerInterface() {
        assertThat(byId).hasSize(8);
        assertThat(byId.get("IF-01").status()).isEqualTo("pass");
        assertThat(byId.get("IF-05").status()).isEqualTo("pass");
        assertThat(byId.get("IF-08").status()).isEqualTo("pass");
        assertThat(byId.get("IF-05").violations()).isEmpty();
        assertThat(byId.get("IF-02").violations()).extracting(Json.Violation::rule).containsExactly("position");
        assertThat(byId.get("IF-03").violations()).extracting(Json.Violation::rule).containsExactly("connector");
        assertThat(byId.get("IF-04").violations()).extracting(Json.Violation::rule).containsExactly("fastener");
        assertThat(byId.get("IF-07").violations()).extracting(Json.Violation::rule).containsExactly("orphan");
        assertThat(byId.get("IF-06").violations()).extracting(Json.Violation::rule)
                .containsExactlyInAnyOrder("unit", "unit", "unit", "hydraulic");
        assertThat(byId.values()).extracting(Json.Interface::status).doesNotContain("not-evaluable");
    }

    @Test
    void featuresCarryKindAndClassSpecificProperties() {
        assertThat(byId.get("IF-05").features()).extracting(f -> ((Json.Feature) f).kind())
                .containsExactly("plug", "fastener", "coupling", "plug", "fastener", "coupling");

        Json.Feature hc51 = feature("IF-05", "HC 51");
        assertThat(hc51.plm()).isEqualTo("uk");
        assertThat(hc51.partId()).isEqualTo("uk-left-root-fitting");
        assertThat(hc51.properties()).containsExactly(Map.entry("connectorType", "EN3645"), Map.entry("pinCount", 19));
        assertThat(hc51.source().unit()).isEqualTo("IN");
        assertThat(hc51.source().x()).isEqualTo(608.2677);
        assertThat(hc51.positionMm().x()).isCloseTo(15450.0, within(0.001));
        assertThat(hc51.positionMm().y()).isCloseTo(1500.0, within(0.001));
        assertThat(hc51.matesWith()).containsExactly((Object) "J51");

        Json.Feature fs5 = feature("IF-05", "FS 5");
        assertThat(fs5.properties().keySet()).containsExactly("fastenerStandard", "diameter", "fastenerCount", "gripLength");
        assertThat(fs5.properties()).containsEntry("fastenerStandard", "EN6115").containsEntry("fastenerCount", 24);
        assertThat((Json.Quantity) fs5.properties().get("diameter")).isEqualTo(new Json.Quantity(0.25, "IN", 6.35, null, null));
        assertThat((Json.Quantity) fs5.properties().get("gripLength")).isEqualTo(new Json.Quantity(0.5, "IN", 12.7, null, null));
        assertThat(fs5.positionMm().z()).isCloseTo(-1500.0, within(0.001));
        assertThat(fs5.matesWith()).containsExactly((Object) "RF-5");

        Json.Feature hyd5 = feature("IF-05", "HYD 5");
        assertThat(hyd5.properties().keySet()).containsExactly("couplingStandard", "dashSize", "pressureRating", "fluid");
        assertThat(hyd5.properties()).containsEntry("dashSize", 8).containsEntry("fluid", "HM 32 mineral oil");
        Json.Quantity rating = (Json.Quantity) hyd5.properties().get("pressureRating");
        assertThat(rating.value()).isEqualTo(5076.32);
        assertThat(rating.unit()).isEqualTo("PSI");
        assertThat(rating.mm()).isNull();
        assertThat(rating.bar()).isCloseTo(350.0, within(0.001));
    }

    @Test
    void featureWithoutUnitHasNoNormalisedPosition() {
        Json.Feature hc61 = feature("IF-06", "HC 61");
        assertThat(hc61.source().unit()).isNull();
        assertThat(hc61.source().x()).isEqualTo(608.2677);
        assertThat(hc61.positionMm()).isNull();
    }

    @Test
    void violationDetailCarriesMeasuredValues() {
        Json.Violation position = byId.get("IF-02").violations().get(0);
        assertThat(position.features()).containsExactly("J21", "P21");
        assertThat(position.detail()).containsEntry("axis", "x").containsEntry("toleranceMm", 2.0);
        assertThat((Double) position.detail().get("deltaMm")).isCloseTo(4.3, within(1e-9));

        Json.Violation connector = byId.get("IF-03").violations().get(0);
        assertThat(connector.detail()).containsEntry("pinCount", List.of(37, 55));

        Json.Violation fastener = byId.get("IF-04").violations().get(0);
        assertThat(fastener.features()).containsExactly("RF-4", "CC-4");
        assertThat(fastener.detail()).containsEntry("fastenerStandard", List.of("EN6115", "EN6115"))
                .containsEntry("fastenerCount", List.of(24, 24)).containsEntry("diameterMm", List.of(6.35, 7.94));

        Json.Violation hydraulic = byId.get("IF-06").violations().stream()
                .filter(v -> v.rule().equals("hydraulic")).findFirst().orElseThrow();
        assertThat(hydraulic.features()).containsExactly("HY-6", "HYD 6");
        assertThat(hydraulic.detail()).containsEntry("dashSize", List.of(8, 8)).containsKey("ratingBar");
        @SuppressWarnings("unchecked")
        List<Double> bar = (List<Double>) hydraulic.detail().get("ratingBar");
        assertThat(bar.get(0)).isEqualTo(350.0);
        assertThat(bar.get(1)).isCloseTo(206.843, within(0.001));

        Json.Violation unit = byId.get("IF-06").violations().stream()
                .filter(v -> v.rule().equals("unit") && "x".equals(v.detail().get("axis"))).findFirst().orElseThrow();
        assertThat(unit.detail()).containsEntry("quantity", "positionX").containsEntry("unit", null)
                .containsEntry("storedValue", 608.2677);

        Json.Violation orphan = byId.get("IF-07").violations().get(0);
        assertThat(orphan.features()).containsExactly("P71");
        assertThat(orphan.detail()).containsEntry("interface", "IF-07").containsEntry("candidateMates", List.of());
    }

    @Test
    void aSecondMateFailsTheInterfaceWithDoubleMateNamingEveryMate() {
        Json.Interface if05 = interfaces(RulesTest.withSecondMateOnJ51()).get("IF-05");
        assertThat(if05.status()).isEqualTo("fail");
        assertThat(if05.violations()).hasSize(1);
        Json.Violation doubleMate = if05.violations().get(0);
        assertThat(doubleMate.rule()).isEqualTo("doubleMate");
        assertThat(doubleMate.shape()).isEqualTo(Atelier.SHAPES + "MateCardinalityShape");
        assertThat(doubleMate.message()).isEqualTo("mates with more than one feature");
        assertThat(doubleMate.focusNode()).isEqualTo(RulesTest.FR + "J51");
        assertThat(doubleMate.features()).as("the focus feature, then its mates").containsExactly("J51", "HC 51", "HC 52");
        assertThat(doubleMate.detail()).isEmpty();
        assertThat(byId.get("IF-05").status()).as("with one mate the same interface passes").isEqualTo("pass");
    }

    @Test
    void kindViolationNamesBothKinds() {
        Model data = RulesTest.fixture();
        Resource fs5 = data.createResource(RulesTest.UK_FASTENER);
        data.removeAll(fs5, RDF.type, null);
        fs5.addProperty(RDF.type, data.createResource(Atelier.ONT + "HydraulicCoupling"));

        Json.Violation kind = interfaces(data).get("IF-05").violations().get(0);
        assertThat(kind.rule()).isEqualTo("kind");
        assertThat(kind.features()).containsExactly("RF-5", "FS 5");
        assertThat(kind.detail()).containsEntry("kind", List.of("fastener", "fastener"));
    }

    @Test
    void partsCarryTheirDescriptionFileIndexFileAndTag() {
        assertThat(byId.get("IF-05").parts()).containsExactly(
                new Json.Part("fr-cross-beam-mid", "fr", "Mid cross beam (wing root)", "cad/ornithopter/fr-cross-beam-mid.stp", null,
                        "CAO/fr-cross-beam-mid.CATPart", "EU-DUAL-USE", "EU", "FR", "2026-09-01T08:00:00Z", null, List.of(), null, null, null, null, null, null),
                new Json.Part("uk-left-root-fitting", "uk", "Left wing root fitting", "cad/ornithopter/uk-left-root-fitting.stp", null, "CAD\\uk-left-root-fitting.sldprt",
                        "EU-DUAL-USE", "EU", "UK", "2026-09-01T08:00:00Z", null, List.of(), null, null, null, null, null, null));
        assertThat(byId.get("IF-08").parts()).as("an untagged part carries no tag values").containsExactly(
                new Json.Part("es-tail-boom", "es", "Tail boom", "cad/ornithopter/es-tail-boom.stp", null, "CAD/es-tail-boom.sldprt",
                        "EU-DUAL-USE", "EU", "ES", "2026-09-01T08:00:00Z", null, List.of(), null, null, null, null, null, null),
                new Json.Part("es-tail-plane", "es", "Tail plane", "cad/ornithopter/es-tail-plane.stp", null,
                        "CAD/es-tail-plane.sldprt", null, null, null, null, null, List.of(), null, null, null, null, null, null));
    }

    @Test
    void supplierComesFromTheFileIndexAndIsAbsentOtherwise() {
        assertThat(byId.get("IF-01").parts()).containsExactly(
                new Json.Part("de-right-root-fitting", "de", "Right wing root fitting", "cad/ornithopter/de-right-root-fitting.stp", null,
                        "CAD/de-right-root-fitting.prt", "EU-DUAL-USE", "EU", "DE", "2026-09-01T08:00:00Z", null, List.of(), null, null, null, null, null, null),
                new Json.Part("fr-control-post", "fr", "Control post", "cad/ornithopter/fr-control-post.stp", null, "CAO/fr-control-post.CATPart",
                        "NATIONAL-FR", "FR", "FR", "2026-09-01T08:00:00Z", "Ateliers du Clos Lucé, Amboise", List.of(), null, null, null, null, null, null));
    }

    private Json.Feature feature(String interfaceId, String featureId) {
        return byId.get(interfaceId).features().stream().map(f -> (Json.Feature) f)
                .filter(f -> f.id().equals(featureId)).findFirst().orElseThrow();
    }
}
