// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.validation.RuleValidator;
import atelier.query.validation.RuleViolation;
import java.math.BigDecimal;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

/** The SHACL rules of ontology/shapes.ttl against fixtures/neighbourhood.ttl. */
class RulesTest {
    static final String UK = "https://example.com/atelier/uk/plug/";
    static final String FR = "https://example.com/atelier/fr/plug/";
    static final String FR_FASTENER = "https://example.com/atelier/fr/fastener/RF-5";
    static final String UK_FASTENER = "https://example.com/atelier/uk/fastener/FS%205";
    static final String DE_FASTENER = "https://example.com/atelier/de/fastener/RF-4";
    static final String ES_FASTENER = "https://example.com/atelier/es/fastener/CC-4";
    static final String FR_COUPLING = "https://example.com/atelier/fr/coupling/HY-5";
    static final String DE_COUPLING = "https://example.com/atelier/de/coupling/HY-4";
    static final String ES_COUPLING = "https://example.com/atelier/es/coupling/HZ-4";
    static final String CONTROL_POST = "https://example.com/atelier/fr/part/fr-control-post";
    static final String RIGHT_INNER_SPAR = "https://example.com/atelier/de/part/de-right-inner-spar";
    static final String CAD_MISSING = "no CAD file published in the file index for this part";

    static Model fixture() {
        return FixtureFederator.fixture();
    }

    private final RuleValidator validator = new RuleValidator();

    @Test
    void reportsExactlyTheSeededViolations() {
        List<RuleViolation> v = validator.validate(fixture());

        assertThat(v).extracting(RuleViolation::rule, RuleViolation::message).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("position", "J21 and P21 are 4.3 mm apart on x; tolerance 2.0 mm"),
                org.assertj.core.groups.Tuple.tuple("connector",
                        "P31 (EN3645, 37 pins) and J31 (EN3645, 55 pins) are mated but do not match"),
                org.assertj.core.groups.Tuple.tuple("orphan",
                        "DE plug P71 is declared on interface IF-07 but mates with no plug"),
                org.assertj.core.groups.Tuple.tuple("unit",
                        "UK plug HC 61 has no unit on its x position (stored value 608.2677), so it cannot be converted"),
                org.assertj.core.groups.Tuple.tuple("unit",
                        "UK plug HC 61 has no unit on its y position (stored value -59.0551), so it cannot be converted"),
                org.assertj.core.groups.Tuple.tuple("unit",
                        "UK plug HC 61 has no unit on its z position (stored value -47.2441), so it cannot be converted"),
                org.assertj.core.groups.Tuple.tuple("fastener", "RF-4 and CC-4 fastener diameters differ: 6.35 mm vs 7.94 mm"),
                org.assertj.core.groups.Tuple.tuple("hydraulic",
                        "HY-6 and HYD 6 pressure ratings differ by more than 2 %: 350.0 bar vs 206.843 bar"));
        assertThat(v).as("every part of the fixture has one CAD file: interface rules only").allMatch(RuleViolation::failsInterface);
    }

    @Test
    void partWithoutOrWithSeveralCadFilesIsAWarningNotAViolation() {
        Model data = fixture();
        Property cadFile = data.createProperty(Atelier.CAD_FILE);
        data.removeAll(data.createResource(RIGHT_INNER_SPAR), cadFile, null);
        data.createResource(CONTROL_POST).addProperty(cadFile, "cad/ornithopter/fr-control-post-v2.stp");

        List<RuleViolation> v = validator.validate(data);
        assertThat(v).filteredOn(x -> x.rule().equals("cadMissing"))
                .extracting(RuleViolation::focusNode, RuleViolation::severity, RuleViolation::message, RuleViolation::path,
                        RuleViolation::shape)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(RIGHT_INNER_SPAR, "Warning", CAD_MISSING, Atelier.CAD_FILE, Atelier.SHAPES + "PartCadShape"),
                        org.assertj.core.groups.Tuple.tuple(CONTROL_POST, "Warning", CAD_MISSING, Atelier.CAD_FILE, Atelier.SHAPES + "PartCadShape"));
        assertThat(v).filteredOn(x -> x.rule().equals("cadMissing")).noneMatch(RuleViolation::failsInterface);
        assertThat(v).filteredOn(RuleViolation::failsInterface).as("the interface rules are unchanged").hasSize(8)
                .allMatch(x -> x.severity().equals("Violation"));
    }

    @Test
    void positionResultCarriesAxisAndMate() {
        RuleViolation position = only(validator.validate(fixture()), "position");
        assertThat(position.focusNode()).isEqualTo("https://example.com/atelier/de/plug/J21");
        assertThat(position.value().getURI()).isEqualTo(FR + "P21");
        assertThat(position.path()).isEqualTo(Atelier.ONT + "positionX");
        assertThat(position.shape()).isEqualTo(Atelier.SHAPES + "PositionShape");
    }

    @Test
    void fastenerAndHydraulicResultsNameTheMate() {
        List<RuleViolation> v = validator.validate(fixture());
        RuleViolation fastener = only(v, "fastener");
        assertThat(fastener.focusNode()).isEqualTo(DE_FASTENER);
        assertThat(fastener.value().getURI()).isEqualTo(ES_FASTENER);
        assertThat(fastener.shape()).isEqualTo(Atelier.SHAPES + "FastenerShape");
        RuleViolation hydraulic = only(v, "hydraulic");
        assertThat(hydraulic.focusNode()).isEqualTo("https://example.com/atelier/fr/coupling/HY-6");
        assertThat(hydraulic.value().getURI()).isEqualTo("https://example.com/atelier/uk/coupling/HYD%206");
    }

    @Test
    void inchAndPsiFeaturesConvertedToMillimetresAndBarPass() {
        assertThat(validator.validate(fixture())).noneMatch(v -> List.of(FR + "J51", UK + "HC%2051", FR_FASTENER,
                UK_FASTENER, FR_COUPLING, "https://example.com/atelier/uk/coupling/HYD%205").contains(v.focusNode()));
    }

    @Test
    void readingInchesAsMillimetresMakesTheAlignedPairsFail() {
        List<RuleViolation> v = new RuleValidator(withMultiplier("IN", "0.001")).validate(fixture());

        assertThat(v).filteredOn(x -> x.rule().equals("position") && x.focusNode().equals(FR + "J51"))
                .extracting(RuleViolation::path)
                .containsExactlyInAnyOrder(Atelier.ONT + "positionX", Atelier.ONT + "positionY", Atelier.ONT + "positionZ");
        assertThat(v).filteredOn(x -> x.rule().equals("fastener")).extracting(RuleViolation::message)
                .contains("RF-5 and FS 5 fastener diameters differ: 6.35 mm vs 0.25 mm");
    }

    @Test
    void readingPsiAsBarMakesTheMatchedCouplingsFail() {
        List<RuleViolation> v = new RuleValidator(withMultiplier("PSI", "100000")).validate(fixture());

        assertThat(v).filteredOn(x -> x.rule().equals("hydraulic") && x.focusNode().equals(FR_COUPLING))
                .extracting(RuleViolation::message)
                .containsExactly("HY-5 and HYD 5 pressure ratings differ by more than 2 %: 350.0 bar vs 5076.32 bar");
    }

    @Test
    void fastenerStandardOrCountMismatchFails() {
        Model data = fixture();
        Resource cc4 = data.createResource(ES_FASTENER);
        Property count = data.createProperty(Atelier.ONT + "fastenerCount");
        data.removeAll(cc4, count, null);
        cc4.addLiteral(count, 20);

        assertThat(validator.validate(data)).filteredOn(v -> v.rule().equals("fastener"))
                .extracting(RuleViolation::message).containsExactlyInAnyOrder(
                        "RF-4 (EN6115, 24 fasteners) and CC-4 (EN6115, 20 fasteners) are mated but do not match",
                        "RF-4 and CC-4 fastener diameters differ: 6.35 mm vs 7.94 mm");
    }

    @Test
    void couplingStandardDashOrFluidMismatchFails() {
        Model data = fixture();
        Resource hz4 = data.createResource(ES_COUPLING);
        Property fluid = data.createProperty(Atelier.ONT + "fluid");
        data.removeAll(hz4, fluid, null);
        hz4.addProperty(fluid, "HM 46 mineral oil");

        assertThat(validator.validate(data)).filteredOn(v -> v.focusNode().equals(DE_COUPLING))
                .extracting(RuleViolation::rule, RuleViolation::message).containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "hydraulic",
                        "HY-4 (AS4395, dash 8, HM 32 mineral oil) and HZ-4 (AS4395, dash 8, HM 46 mineral oil) are mated but do not match"));
    }

    @Test
    void matedFeaturesOfDifferentClassesFail() {
        Model data = fixture();
        Resource fs5 = data.createResource(UK_FASTENER);
        data.removeAll(fs5, RDF.type, null);
        fs5.addProperty(RDF.type, data.createResource(Atelier.ONT + "HydraulicCoupling"));

        assertThat(validator.validate(data)).filteredOn(v -> v.focusNode().equals(FR_FASTENER))
                .extracting(RuleViolation::rule, RuleViolation::message).containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "kind", "RF-5 (Fastener) and FS 5 (HydraulicCoupling) are mated but are not the same kind of feature"));
    }

    @Test
    void unitRuleCoversDiametersAndRatings() {
        Model data = fixture();
        Property unit = data.createProperty(Atelier.QUDT + "unit");
        data.removeAll(data.createResource(DE_FASTENER + "/diameter"), unit, null);
        Resource rating = data.createResource(DE_COUPLING + "/rating");
        data.removeAll(rating, unit, null);
        rating.addProperty(unit, data.createResource(Atelier.UNIT + "ATM"));

        assertThat(validator.validate(data)).filteredOn(v -> v.rule().equals("unit"))
                .extracting(RuleViolation::message, RuleViolation::path).contains(
                        org.assertj.core.groups.Tuple.tuple(
                                "DE fastener RF-4 has no unit on its diameter (stored value 6.350), so it cannot be converted",
                                Atelier.ONT + "diameter"),
                        org.assertj.core.groups.Tuple.tuple(
                                "DE coupling HY-4 states its pressure rating in unit ATM, which has no known conversion multiplier",
                                Atelier.ONT + "pressureRating"));
    }

    @Test
    void aFeatureWithTwoMatesFailsDoubleMateAndOneMatePasses() {
        assertThat(validator.validate(fixture())).as("every seeded feature has one mate at most")
                .noneMatch(v -> v.rule().equals("doubleMate"));

        RuleViolation doubleMate = only(validator.validate(withSecondMateOnJ51()), "doubleMate");
        assertThat(doubleMate.focusNode()).isEqualTo(FR + "J51");
        assertThat(doubleMate.severity()).isEqualTo("Violation");
        assertThat(doubleMate.failsInterface()).isTrue();
        assertThat(doubleMate.path()).isEqualTo(Atelier.ONT + "matesWith");
        assertThat(doubleMate.value()).as("a cardinality result carries no sh:value").isNull();
        assertThat(doubleMate.shape()).isEqualTo(Atelier.SHAPES + "MateCardinalityShape");
        assertThat(doubleMate.message()).isEqualTo("mates with more than one feature");
    }

    /**
     * The fixture with a second mate published for FR J51: UK HC 52, a copy of HC 51 (same part,
     * connector, pins and position) declared on IF-05, so that only the cardinality rule fails.
     */
    static Model withSecondMateOnJ51() {
        Model data = fixture();
        Resource hc51 = data.createResource(UK + "HC%2051");
        Resource hc52 = data.createResource(UK + "HC%2052");
        Resource j51 = data.createResource(FR + "J51");
        Property matesWith = data.createProperty(Atelier.ONT + "matesWith");
        for (Statement s : hc51.listProperties().toList()) {
            if (s.getPredicate().equals(matesWith)) continue;
            RDFNode o = s.getObject();
            if (o.isURIResource() && o.asResource().getURI().startsWith(hc51.getURI() + "/")) {
                Resource copy = data.createResource(o.asResource().getURI().replace(hc51.getURI(), hc52.getURI()));
                for (Statement q : o.asResource().listProperties().toList()) copy.addProperty(q.getPredicate(), q.getObject());
                hc52.addProperty(s.getPredicate(), copy);
            } else {
                hc52.addProperty(s.getPredicate(), o);
            }
        }
        j51.addProperty(matesWith, hc52);
        hc52.addProperty(matesWith, j51);
        data.createResource(Atelier.interfaceIri("wings", "IF-05")).addProperty(data.createProperty(Atelier.ONT + "declaresFeature"), hc52);
        return data;
    }

    @Test
    void emptyModelConforms() {
        assertThat(validator.validate(ModelFactory.createDefaultModel())).isEmpty();
    }

    /** ontology/units.ttl with one unit's multiplier replaced (a wrong conversion table). */
    static Model withMultiplier(String unit, String multiplier) {
        Model mutated = FixtureFederator.units();
        Resource u = mutated.createResource(Atelier.UNIT + unit);
        Property conversion = mutated.createProperty(Atelier.QUDT + "conversionMultiplier");
        mutated.removeAll(u, conversion, null);
        u.addLiteral(conversion, mutated.createTypedLiteral(new BigDecimal(multiplier)));
        return mutated;
    }

    static RuleViolation only(List<RuleViolation> violations, String rule) {
        List<RuleViolation> matching = violations.stream().filter(v -> v.rule().equals(rule)).toList();
        assertThat(matching).hasSize(1);
        return matching.get(0);
    }
}
