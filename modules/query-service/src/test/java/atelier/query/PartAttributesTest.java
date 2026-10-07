// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.federation.Federator;
import atelier.query.mapping.Lifecycles;
import atelier.query.mapping.Units;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.policy.Redaction;
import atelier.query.validation.RuleValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

/**
 * The part attributes a PLM states (revision, lifecycle word, mass, material, part type) reach the
 * part records of the answers: the mass converted to kg with the QUDT multiplier of its stored unit,
 * the lifecycle word resolved to its canonical state through the ontology's atelier:Lifecycle scheme.
 * A part a profile may not see contributes none of them.
 */
class PartAttributesTest {
    static final Policy POLICY = EvidenceTest.POLICY;
    static final String DE_FITTING = "https://example.com/atelier/de/part/de-right-root-fitting";
    static final String UK_FITTING = "https://example.com/atelier/uk/part/uk-left-root-fitting";
    static final String CONTROL_POST = "https://example.com/atelier/fr/part/fr-control-post";
    static final String ATTRIBUTES = """
            @prefix atelier:  <https://example.com/atelier/ontology#> .
            @prefix qudt: <http://qudt.org/schema/qudt/> .
            @prefix unit: <http://qudt.org/vocab/unit/> .
            @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .

            <%1$s> atelier:revision "01" ; atelier:lifecycleLabel "Freigegeben" ; atelier:material "Stahl S235" ;
              atelier:partType "PART" ; atelier:mass <%1$s/mass> .
            <%1$s/mass> a qudt:QuantityValue ; qudt:numericValue "2.400"^^xsd:decimal ; qudt:unit unit:KiloGM .
            <%2$s> atelier:revision "C1" ; atelier:lifecycleLabel "Frozen" ; atelier:material "steel" ;
              atelier:partType "PART" ; atelier:mass <%2$s/mass> .
            <%2$s/mass> a qudt:QuantityValue ; qudt:numericValue "5.000"^^xsd:decimal ; qudt:unit unit:LB .
            <%3$s> atelier:revision "B" ; atelier:lifecycleLabel "En cours" ; atelier:material "frêne secret" ;
              atelier:partType "PART" ; atelier:mass <%3$s/mass> .
            <%3$s/mass> a qudt:QuantityValue ; qudt:numericValue "7.250"^^xsd:decimal ; qudt:unit unit:KiloGM .
            """.formatted(DE_FITTING, UK_FITTING, CONTROL_POST);

    private final RuleValidator validator = new RuleValidator();
    private final FixtureFederator federator = new FixtureFederator(fixture());
    private final QueryService service = new QueryService(federator, validator, new NoOntopSql(), new CadUrls(null, null));

    static Model fixture() {
        Model model = FixtureFederator.fixture();
        RDFDataMgr.read(model, new StringReader(ATTRIBUTES), null, Lang.TURTLE);
        return model;
    }

    private static Json.Part part(List<Json.PartView> parts, String id) {
        return parts.stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast)
                .filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void germanPartCarriesItsAttributesWithMassInKgAndItsCanonicalLifecycle() {
        Json.Part fitting = part(service.interfaceById("IF-01", Caller.user(POLICY.profile("export-officer")))
                .orElseThrow()._interface().parts(), "de-right-root-fitting");
        assertThat(fitting.revision()).isEqualTo("01");
        assertThat(fitting.lifecycle()).isEqualTo("Freigegeben");
        assertThat(fitting.lifecycleState()).isEqualTo("RELEASED");
        assertThat(fitting.mass()).isEqualTo(new Json.Quantity(2.4, "KiloGM", null, null, 2.4));
        assertThat(fitting.material()).isEqualTo("Stahl S235");
        assertThat(fitting.partType()).isEqualTo("PART");
    }

    @Test
    void britishMassInPoundsIsConvertedToKgAndFrozenIsBlocked() {
        Json.Part fitting = part(service.parts(Caller.user(POLICY.profile("export-officer"))).parts(), "uk-left-root-fitting");
        assertThat(fitting.mass().value()).isEqualTo(5.0);
        assertThat(fitting.mass().unit()).isEqualTo("LB");
        assertThat(fitting.mass().kg()).isCloseTo(2.26796185, within(1e-9));
        assertThat(fitting.lifecycle()).isEqualTo("Frozen");
        assertThat(fitting.lifecycleState()).isEqualTo("BLOCKED");
    }

    @Test
    void everySiteWordResolvesThroughTheSchemeAndAnUnknownWordToNothing() {
        Lifecycles lifecycles = new Lifecycles(validator.lifecycle());
        Map<String, List<String>> words = Map.of(
                "WORKING", List.of("In Arbeit", "En cours", "Borrador", "Draft"),
                "RELEASED", List.of("Freigegeben", "Publié", "Liberado", "Released"),
                "BLOCKED", List.of("Gesperrt", "Bloqué", "Bloqueado", "Frozen"),
                "SUPERSEDED", List.of("Ersetzt", "Remplacé", "Sustituido", "Superseded"));
        words.forEach((state, list) -> list.forEach(word -> assertThat(lifecycles.state(word)).as(word).isEqualTo(state)));
        assertThat(lifecycles.state("Obsolete")).isNull();
        assertThat(lifecycles.state(null)).isNull();
    }

    @Test
    void theSchemeNotTheCodeDecidesTheCanonicalState() {
        Model scheme = ModelFactory.createDefaultModel().add(validator.lifecycle());
        Resource blocked = scheme.createResource(Atelier.ONT + "Blocked");
        Resource superseded = scheme.createResource(Atelier.ONT + "Superseded");
        scheme.remove(blocked, scheme.createProperty(Atelier.SKOS + "altLabel"), scheme.createLiteral("Frozen", "en"));
        superseded.addProperty(scheme.createProperty(Atelier.SKOS + "altLabel"), scheme.createLiteral("Frozen", "en"));
        assertThat(new Lifecycles(scheme).state("Frozen")).isEqualTo("SUPERSEDED");
    }

    @Test
    void cartMassUnitDefectIsStoredAsWrittenAndReadAsPounds() throws IOException {
        String seed = Files.readString(Path.of("../plm-services/plm-uk/src/main/resources/db/migration/R__products_seed.sql"));
        Matcher row = Pattern.compile("\\('UK-3103', '[^']*', '[^']*', '[^']*', '[^']*', ([0-9.]+), ").matcher(seed);
        assertThat(row.find()).as("the trip counter row of the British seed").isTrue();
        assertThat(row.group(1)).as("0.6 kg in the product file, written unconverted into mass_lb").isEqualTo("0.6");
        BigDecimal kg = new Units(validator.units()).toKg(new BigDecimal(row.group(1)), Atelier.UNIT + "LB");
        assertThat(kg.doubleValue()).as("the layer reads the column's unit: the counter weighs less than half its mass")
                .isCloseTo(0.272155422, within(1e-9));
    }

    @Test
    void hiddenPartContributesNoAttributeToAProfileThatCannotSeeIt() throws IOException {
        Caller de = Caller.user(POLICY.profile("de-engineer"));
        Answer answer = service.answer(de);
        assertThat(answer.model().listStatements(answer.model().createResource(CONTROL_POST), null, (String) null).toList())
                .as("only link-store facts remain on the hidden control post")
                .allMatch(s -> Atelier.LINK_PREDICATES.contains(s.getPredicate().getURI()));
        assertThat(answer.model().containsResource(answer.model().createResource(CONTROL_POST + "/mass"))).isFalse();
        String json = new ObjectMapper().writeValueAsString(service.interfaces(de));
        assertThat(json).doesNotContain("frêne secret").doesNotContain("En cours").doesNotContain("7.25");
    }

    @Test
    void redactionStripsTheAttributesAndTheMassNodeOfAHiddenPartASourceLetThrough() {
        Model model = fixture();
        Redaction.apply(model, POLICY.profile("de-engineer").releasable(), false);
        Resource post = model.createResource(CONTROL_POST);
        for (String predicate : List.of("revision", "lifecycleLabel", "material", "partType", "mass")) {
            assertThat(post.hasProperty(model.createProperty(Atelier.ONT + predicate))).as(predicate).isFalse();
        }
        assertThat(model.listStatements(model.createResource(CONTROL_POST + "/mass"), null, (String) null).toList()).isEmpty();
        assertThat(model.createResource(DE_FITTING).hasProperty(model.createProperty(Atelier.MASS))).as("a visible part keeps its mass").isTrue();
    }

    @Test
    void attributesAndTheMassNodeAreAttributedToThePlmThatStatesThem() {
        assertThat(Federator.sourceOf(Triple.create(NodeFactory.createURI(DE_FITTING), NodeFactory.createURI(Atelier.ONT + "revision"),
                NodeFactory.createLiteralString("01")))).isEqualTo("ontop-de");
        assertThat(Federator.sourceOf(Triple.create(NodeFactory.createURI(UK_FITTING + "/mass"), RDF.type.asNode(),
                NodeFactory.createURI(Atelier.QUDT + "QuantityValue")))).isEqualTo("ontop-uk");
        Federator.Result run = federator.parts(POLICY.profile("export-officer"));
        assertThat(Federator.triplesByEndpoint(run.model())).doesNotContainKey("unknown");
    }
}
