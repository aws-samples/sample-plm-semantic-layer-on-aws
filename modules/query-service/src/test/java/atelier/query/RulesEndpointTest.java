// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import atelier.query.api.Json;
import atelier.query.api.QueryController;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.evidence.ShapeSource;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.rules.RuleCatalogue;
import atelier.query.rules.RuleFailures;
import atelier.query.rules.RulesController;
import atelier.query.validation.RuleValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * GET /query/rules over the shipped ontology/shapes.ttl, and GET /query/rules/failures over fixtures/neighbourhood.ttl with
 * the CubeSat product file added (its mass limit is a product rule): every failing record is one the per-product
 * interfaces, parts and products answers report.
 */
class RulesEndpointTest {
    private static final Path WEB_FIXTURE = Path.of("../web/src/dev/fixture-rules.json");
    private static final String SH = "http://www.w3.org/ns/shacl#";
    private static final Policy POLICY = new Policy(Path.of("../../ontology/policy.json"));
    private static final RuleValidator VALIDATOR = new RuleValidator();
    private static final RuleCatalogue CATALOGUE = new RuleCatalogue(VALIDATOR);
    private static final List<RuleCatalogue.Rule> RULES = CATALOGUE.describe().rules();
    private static final String PREFIXES = "PREFIX atelier: <" + Atelier.ONT + "> PREFIX qudt: <" + Atelier.QUDT
            + "> PREFIX skos: <" + Atelier.SKOS + ">\n";

    @Test
    void everyShapeOfTheShapesFileIsDescribedInPlainLanguage() {
        Graph shapes = VALIDATOR.shapesGraph();
        Set<String> declared = Stream.of("NodeShape", "PropertyShape")
                .flatMap(type -> shapes.find(Node.ANY, RDF.type.asNode(), NodeFactory.createURI(SH + type)).toList().stream())
                .map(t -> t.getSubject()).filter(Node::isURI).map(Node::getURI).collect(Collectors.toSet());
        assertThat(declared).hasSizeGreaterThanOrEqualTo(15);
        assertThat(RULES).extracting(RuleCatalogue.Rule::shape).containsExactlyInAnyOrderElementsOf(declared);
        assertThat(RULES).extracting(RuleCatalogue.Rule::name).isSorted().doesNotHaveDuplicates();
        assertThat(RULES).allSatisfy(rule -> {
            assertThat(rule.description()).as(rule.name() + " sh:description").isNotBlank().hasSizeGreaterThan(80);
            assertThat(rule.message()).as(rule.name() + " rdfs:comment").isNotBlank();
            assertThat(rule.focus()).as(rule.name() + " ateliersh:focus").isIn("interface", "part", "reference", "product");
            assertThat(rule.severity()).as(rule.name()).isIn("Violation", "Warning");
            assertThat(rule.terms()).as(rule.name() + " reads ontology terms").isNotEmpty();
        });
        assertThat(rule("position").focus()).isEqualTo("interface");
        assertThat(rule("cadMissing").severity()).isEqualTo("Warning");
        assertThat(rule("danglingReference").focus()).isEqualTo("reference");
        assertThat(rule("massLimit")).extracting(RuleCatalogue.Rule::focus, RuleCatalogue.Rule::severity).containsExactly("product", "Violation");
    }

    @Test
    void theTurtleOfEachShapeParsesBackToTheShapeAsLoaded() {
        for (RuleCatalogue.Rule rule : RULES) {
            Graph parsed = GraphFactory.createDefaultGraph();
            RDFParser.fromString(rule.turtle(), Lang.TURTLE).parse(parsed);
            assertThat(parsed.isIsomorphicWith(ShapeSource.of(VALIDATOR.shapesGraph(), rule.shape()))).as(rule.name()).isTrue();
        }
        assertThat(rule("unit").turtle()).startsWith("PREFIX").contains("ateliersh:UnitShape", "sh:select    \"\"\"\n      SELECT $this",
                "        FILTER NOT EXISTS { ?value qudt:unit ?anyUnit }\n");
        assertThat(rule("unit").turtle().replaceAll("\\s+", " ")).contains("sh:targetClass atelier:InterfaceFeature");
        assertThat(CATALOGUE.describe()).as("the same text on every build").isEqualTo(new RuleCatalogue(new RuleValidator()).describe());
    }

    @Test
    void eachSparqlQueryParsesAndEachTermIsTheOntologys() {
        for (RuleCatalogue.Rule rule : RULES) {
            for (RuleCatalogue.Sparql q : rule.sparql()) QueryFactory.create(PREFIXES + q.query());
        }
        assertThat(rule("unit").sparql()).hasSize(2).allSatisfy(q -> assertThat(q.role()).isEqualTo("constraint"));
        assertThat(rule("cadMissing").sparql()).extracting(RuleCatalogue.Sparql::role).containsExactly("target");
        assertThat(rule("doubleMate").sparql()).as("a core constraint, no SPARQL").isEmpty();
        assertThat(RULES).flatExtracting(RuleCatalogue.Rule::terms)
                .allSatisfy(t -> assertThat(t.term()).startsWith("atelier:"))
                .filteredOn(t -> !"other".equals(t.kind())).allSatisfy(t -> assertThat(t.definition()).as(t.term()).isNotBlank());
        assertThat(rule("position").terms()).extracting(RuleCatalogue.Term::term)
                .contains("atelier:matesWith", "atelier:toleranceMm", "atelier:positionX", "atelier:declaresFeature");
        assertThat(rule("staleRevision").terms()).extracting(RuleCatalogue.Term::term, RuleCatalogue.Term::kind)
                .contains(tuple("atelier:Superseded", "concept"));
    }

    @Test
    void theWebFixtureIsTheAnswer() throws IOException {
        String answer = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(CATALOGUE.describe()) + "\n";
        if (Boolean.getBoolean("fixtures.write")) Files.writeString(WEB_FIXTURE, answer, StandardCharsets.UTF_8);
        assertThat(Files.readString(WEB_FIXTURE, StandardCharsets.UTF_8))
                .as("modules/web/src/dev/fixture-rules.json; regenerate with mvn test -Dtest=RulesEndpointTest -Dfixtures.write=true")
                .isEqualTo(answer);
    }

    @Test
    void theFailuresAreTheRecordsThePerProductAnswersReport() {
        Model fixture = FixtureFederator.fixture().add(ProductFixture.of("cubesat"));
        QueryService service = service(fixture);
        RuleFailures failures = new RuleFailures(service, CATALOGUE);
        Set<String> rules = Set.copyOf(CATALOGUE.names());
        for (String profile : List.of("export-officer", "de-engineer")) {
            Caller caller = Caller.user(POLICY.profile(profile));
            List<RuleFailures.Failure> all = failures.failures(caller).failures();
            assertThat(all).as(profile).isNotEmpty();
            Json.ProductsResponse products = service.products(caller);
            for (Json.Product product : products.products()) {
                String key = product.key();
                Set<List<String>> expected = new HashSet<>();
                service.interfaces(caller, key).interfaces().forEach(i -> i.violations()
                        .forEach(v -> expected.add(List.of(v.rule(), "interface", i.id()))));
                service.parts(caller, key).parts().stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast)
                        .forEach(p -> p.findings().stream().filter(f -> rules.contains(f.rule()))
                                .forEach(f -> expected.add(List.of(f.rule(), "part", p.id()))));
                Set<String> onParts = expected.stream().map(e -> e.get(0)).collect(Collectors.toSet());
                product.findings().stream().filter(f -> !onParts.contains(f.rule())).forEach(f -> expected.add(List.of(f.rule(), "product", key)));
                Set<List<String>> reported = all.stream().filter(f -> f.product().equals(key))
                        .map(f -> List.of(f.rule(), f.kind(), f.id())).collect(Collectors.toSet());
                assertThat(reported).as(profile + " " + key).isEqualTo(expected);
            }
            assertThat(all).extracting(RuleFailures.Failure::product).isSubsetOf(products.products().stream().map(Json.Product::key).toList());
        }
        List<RuleFailures.Failure> officer = failures.failures(Caller.user(POLICY.profile("export-officer"))).failures();
        assertThat(officer).contains(new RuleFailures.Failure("massLimit", "cubesat", "product", "cubesat", null));
        assertThat(officer).extracting(RuleFailures.Failure::kind).contains("interface", "part", "product");
    }

    @Test
    void theWindTurbinesSeededMeshDefectIsALiveFailure() {
        QueryService service = service(FixtureFederator.fixture().add(Closures.with(FunctionFixture.of("wind-turbine"))));
        RuleFailures failures = new RuleFailures(service, CATALOGUE);
        Caller officer = Caller.user(POLICY.profile("export-officer"));
        assertThat(failures.failures(officer).failures()).filteredOn(f -> f.rule().equals("meshModule"))
                .as("the driven gear of the seeded mesh, D-37028 driving D-37031")
                .containsExactly(new RuleFailures.Failure("meshModule", "wind-turbine", "part", "D-37031", "de"));
        assertThat(service.parts(officer, "wind-turbine").parts()).filteredOn(Json.Part.class::isInstance).map(Json.Part.class::cast)
                .filteredOn(p -> p.id().equals("D-37031")).flatExtracting(Json.Part::findings).extracting(Json.Finding::rule)
                .as("the parts answer carries the finding the failures report").contains("meshModule");
        assertThat(service.parts(officer, "wind-turbine", "D-37073").parts()).filteredOn(Json.Part.class::isInstance).map(Json.Part.class::cast)
                .filteredOn(p -> p.id().equals("D-37031")).flatExtracting(Json.Part::findings).extracting(Json.Finding::rule)
                .as("and so does the gearbox subtree's").contains("meshModule");
    }

    @Test
    void theRestApiServesBothAnswers() throws Exception {
        QueryService service = service(FixtureFederator.fixture());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new RulesController(CATALOGUE, new RuleFailures(service, CATALOGUE), POLICY)).build();
        mvc.perform(get("/query/rules")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rules.length()").value(RULES.size()))
                .andExpect(jsonPath("$.rules[0].turtle").isNotEmpty());
        mvc.perform(get("/query/rules/failures").header(QueryController.PROFILE_HEADER, "export-officer")).andExpect(status().isOk())
                .andExpect(jsonPath("$.failures[0].rule").isNotEmpty())
                .andExpect(jsonPath("$.policy.profile").value("export-officer"));
    }

    private static QueryService service(Model fixture) {
        return new QueryService(new FixtureFederator(fixture), VALIDATOR, new NoOntopSql(), new CadUrls(null, null));
    }

    private static RuleCatalogue.Rule rule(String name) {
        return RULES.stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
    }
}
