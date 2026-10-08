// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.FixtureFederator;
import atelier.query.QueryService;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.federation.Federator;
import atelier.query.validation.RuleValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.engine.http.QueryExceptionHTTP;
import org.junit.jupiter.api.Test;

/**
 * The twenty-five tools over fixtures/neighbourhood.ttl served by {@link FixtureFederator}: what each
 * answers, how the profile and the product change it, and how a bad argument or a refused query comes back.
 */
class AtelierToolsTest {
    static final Policy POLICY = new Policy(Path.of("../../ontology/policy.json"));
    static final Caller OFFICER = Caller.user(POLICY.profile("export-officer"));
    static final Caller DE = Caller.user(POLICY.profile("de-engineer"));
    static final ObjectMapper JSON = new ObjectMapper();
    private static final String PREFIX = "PREFIX atelier: <https://example.com/atelier/ontology#> ";

    private final QueryService service = new QueryService(new FixtureFederator(), new RuleValidator(), new NoOntopSql(),
            new CadUrls(null, null));
    private final AtelierTools tools = new AtelierTools(service, POLICY, JSON, new PlmApi("", () -> null), KnownTerms.load());

    @Test
    void offersTheTwentyFiveToolsOfTheContract() {
        assertThat(tools.tools()).extracting(McpSchema.Tool::name).containsExactly("products", "list_interfaces", "parts", "interface_check",
                "where_used", "impact_of_change", "export_status", "bom", "bom_where_used", "path_between", "flow_path", "variant_diff", "external_references", "equivalent_parts", "find_term",
                "find_parts", "suppliers", "parts_between_stations", "section_joints", "evidence", "ontology", "sparql", "preview_correction", "catalogue", "sql");
        for (McpSchema.Tool tool : tools.tools()) {
            assertThat(tool.description()).as(tool.name()).endsWith("Prefer a named tool; use sparql only when no named tool answers.");
            if (!tool.name().equals("ontology")) assertThat(tool.description()).as(tool.name()).contains("Templates:");
            assertThat(tool.inputSchema().type()).isEqualTo("object");
            assertThat(tool.annotations().readOnlyHint()).isTrue();
        }
        assertThat(description("ontology")).startsWith("The ontology the sparql tool is checked against: classes, properties with domain"
                + " and range, the named graphs, the rules, and example patterns. Read it before writing SPARQL. Prefer a named tool");
        assertThat(description("sparql")).as("the guard is stated").contains("SELECT, ASK or CONSTRUCT only").contains("LIMIT 200")
                .contains("call ontology first; every class and predicate must be one it lists");
        assertThat(description("catalogue")).contains("Read this before writing SQL");
        assertThat(description("sql")).contains("one SELECT statement").contains("SQL that actually ran");
        for (String scoped : List.of("list_interfaces", "sparql", "where_used", "impact_of_change", "interface_check", "evidence")) {
            assertThat(description(scoped)).as(scoped).contains("Optional argument: product");
            assertThat(tool(scoped).inputSchema().properties()).as(scoped + " takes the product the agent injects").containsKey("product");
            assertThat(tool(scoped).inputSchema().required()).as(scoped + " answers without it").doesNotContain("product");
        }
        for (String single : List.of("interface_check", "evidence")) {
            assertThat(description(single)).as(single).contains("interface ids are unique within a product");
        }
        for (String unscoped : List.of("products", "export_status", "ontology", "catalogue", "sql")) {
            assertThat(tool(unscoped).inputSchema().properties()).as(unscoped).doesNotContainKey("product");
        }
        for (String bom : List.of("bom", "bom_where_used", "find_parts")) {
            assertThat(tool(bom).inputSchema().required()).as(bom + ": a bill of materials is per product").contains("product");
        }
        assertThat(description("where_used")).as("each where-used names the other").contains("use bom_where_used");
        assertThat(description("bom_where_used")).contains("use where_used");
        assertThat(tools.specifications()).hasSize(25);
    }

    @Test
    void productsListsTheProductsWithTheProfilesPartCountsAndScopesListInterfaces() {
        JsonNode officer = ok("products", Map.of(), OFFICER);
        assertThat(officer.fieldNames()).toIterable().containsExactly("products", "provenance", "timings", "policy");
        assertThat(officer.path("products")).extracting(p -> p.path("key").asText()).containsExactly("ornithopter", "tail", "wings");
        assertThat(officer.path("products")).extracting(p -> p.path("partCount").asInt()).containsExactly(9, 3, 3);
        assertThat(officer.path("products").get(0).path("name").asText()).isEqualTo("Ornithopter ground demonstrator (Paris Manuscript B, f. 74v)");
        assertThat(officer.path("products").get(0).path("frame").asText()).startsWith("x aft from the frame nose");
        assertThat(officer.path("products").get(2).path("frame").isNull()).as("a product may state no frame").isTrue();
        JsonNode de = ok("products", Map.of(), DE);
        assertThat(de.path("products")).extracting(p -> p.path("partCount").asInt())
                .as("the control post, the tail drive and the untagged tail plane are not counted for the DE engineer").containsExactly(6, 1, 3);

        JsonNode wings = ok("list_interfaces", Map.of("product", "wings"), DE);
        assertThat(wings.path("interfaces")).extracting(i -> i.path("id").asText()).containsExactly("IF-05", "IF-06");
        assertThat(wings.path("provenance").path("calls")).filteredOn(c -> c.path("endpoint").asText().equals("ontop-de"))
                .extracting(c -> c.path("requests").asInt()).as("no DE part is in the wings: its PLM is not asked").containsExactly(0);
        assertThat(ok("list_interfaces", Map.of("product", "ornithopter"), DE).path("interfaces")).extracting(i -> i.path("id").asText())
                .as("the ornithopter holds every part but only its own interfaces").containsExactly("IF-01", "IF-02", "IF-03", "IF-04");
        assertThat(ok("list_interfaces", Map.of(), DE).path("interfaces")).extracting(i -> i.path("product").asText())
                .containsExactly("ornithopter", "ornithopter", "ornithopter", "ornithopter", "wings", "wings", "tail", "tail");
        assertThat(error("list_interfaces", Map.of("product", "nope"), DE)).isEqualTo("no product nope");
        assertThat(error("list_interfaces", Map.of("product", "tail unit"), DE)).isEqualTo("invalid product key");

        JsonNode count = ok("sparql", Map.of("query", PREFIX + "SELECT (COUNT(?i) AS ?n) WHERE { ?i a atelier:Interface }", "product", "tail"), OFFICER);
        assertThat(count.path("rows").get(0).get(0).asInt()).as("the sparql graph is the product's").isEqualTo(2);
    }

    private McpSchema.Tool tool(String name) {
        return tools.tools().stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void ontologyIsTheCatalogueForEveryProfileAndItsExamplesRun() {
        JsonNode ontology = ok("ontology", Map.of(), DE);
        assertThat(ontology).isEqualTo(JSON.valueToTree(tools.ontology().describe()));
        assertThat(ontology.fieldNames()).toIterable().containsExactly("version", "prefixes", "classes", "properties", "graphs", "rules", "examples");
        assertThat(text(tools.call("ontology", Map.of(), DE)).length()).isLessThan(32 * 1024);
        assertThat(ok("ontology", Map.of(), OFFICER)).as("the ontology does not depend on the profile").isEqualTo(ontology);
        for (JsonNode example : ontology.path("examples")) {
            JsonNode answer = ok("sparql", Map.of("query", example.path("query").asText()), OFFICER);
            assertThat(answer.path("form").asText()).as(example.path("question").asText()).isEqualTo("select");
        }
        JsonNode withoutUnit = ok("sparql", Map.of("query", ontology.path("examples").get(1).path("query").asText()), OFFICER);
        assertThat(withoutUnit.path("columns")).extracting(JsonNode::asText).containsExactly("part", "interfaces", "featuresWithoutUnit");
        assertThat(withoutUnit.path("rows")).as("the UK plug HC 61 of IF-06 has positions without a unit").hasSize(1);
        assertThat(withoutUnit.path("rows").get(0).get(0).asText()).isEqualTo("https://example.com/atelier/uk/part/uk-left-wing-actuator");
        assertThat(withoutUnit.path("rows").get(0).get(1).asInt()).isEqualTo(1);
        assertThat(withoutUnit.path("rows").get(0).get(2).asInt()).isEqualTo(1);
    }

    @Test
    void whereUsedListsTheInterfacesMatesAndFeatureCounts() {
        JsonNode answer = ok("where_used", Map.of("part", "fr-cross-beam-mid"), OFFICER);
        assertThat(answer.path("part").path("id").asText()).isEqualTo("fr-cross-beam-mid");
        assertThat(answer.path("interfaces")).extracting(i -> i.path("id").asText()).containsExactly("IF-02", "IF-03", "IF-05", "IF-06");
        assertThat(answer.path("interfaces")).extracting(i -> i.path("status").asText()).containsExactly("fail", "fail", "pass", "fail");
        assertThat(answer.path("interfaces")).extracting(i -> i.path("mates").get(0).path("id").asText())
                .containsExactly("de-right-root-fitting", "de-right-inner-spar", "uk-left-root-fitting", "uk-left-wing-actuator");
        assertThat(JSON.convertValue(answer.path("interfaces").get(2).path("features"), Map.class))
                .isEqualTo(Map.of("plug", 1, "fastener", 1, "coupling", 1));
        assertThat(JSON.convertValue(answer.path("features"), Map.class)).isEqualTo(Map.of("plug", 4, "fastener", 1, "coupling", 2));
        assertThat(answer.path("provenance").path("calls")).hasSize(6);
        assertThat(answer.has("sparql")).as("no request texts in a tool answer").isFalse();
        assertThat(answer.path("policy").path("actor").asText()).isEqualTo("user");

        JsonNode byIri = ok("where_used", Map.of("part", "https://example.com/atelier/fr/part/fr-cross-beam-mid"), OFFICER);
        assertThat(byIri.path("interfaces")).hasSize(4);
    }

    @Test
    void hiddenPartsAreRedactedAndUnknownIdsAreErrors() {
        JsonNode hidden = ok("where_used", Map.of("part", "fr-control-post"), DE);
        assertThat(hidden.path("part").path("redacted").asBoolean()).isTrue();
        assertThat(hidden.path("part").path("plm").asText()).isEqualTo("fr");
        assertThat(hidden.path("interfaces")).isEmpty();
        assertThat(error("where_used", Map.of("part", "nope"), OFFICER)).isEqualTo("no interface names part nope");
        assertThat(error("where_used", Map.of(), OFFICER)).isEqualTo("argument 'part' is required");
        assertThat(error("interface_check", Map.of("interface", "IF-99"), OFFICER)).isEqualTo("no interface IF-99");
        assertThat(error("nothing", Map.of(), OFFICER)).isEqualTo("unknown tool nothing");
    }

    @Test
    void impactOfChangeNamesTheInterfaceAndTheMatedFeatures() {
        JsonNode plug = ok("impact_of_change", Map.of("feature", "HC 61"), OFFICER);
        assertThat(plug.path("feature").path("id").asText()).isEqualTo("HC 61");
        assertThat(plug.path("interfaces")).hasSize(1);
        JsonNode if06 = plug.path("interfaces").get(0);
        assertThat(if06.path("id").asText()).isEqualTo("IF-06");
        assertThat(if06.path("status").asText()).isEqualTo("fail");
        assertThat(if06.path("rulesFailing")).extracting(JsonNode::asText).containsExactlyInAnyOrder("unit", "hydraulic");
        assertThat(if06.path("features")).extracting(f -> f.path("id").asText()).containsExactlyInAnyOrder("J61", "HC 61");

        JsonNode drive = ok("impact_of_change", Map.of("part", "de-tail-drive"), OFFICER);
        assertThat(drive.path("interfaces")).extracting(i -> i.path("id").asText()).containsExactly("IF-07");
        assertThat(drive.path("interfaces").get(0).path("features")).extracting(f -> f.path("id").asText())
                .containsExactlyInAnyOrder("P71", "P72", "J72");

        JsonNode driveForDe = ok("impact_of_change", Map.of("part", "de-tail-drive"), DE);
        assertThat(driveForDe.path("part").path("redacted").asBoolean()).isTrue();
        assertThat(driveForDe.path("interfaces")).isEmpty();
        JsonNode mateOfHidden = ok("impact_of_change", Map.of("feature", "J72"), DE);
        assertThat(mateOfHidden.path("interfaces").get(0).path("features")).extracting(f -> f.path("id").asText(""))
                .as("the hidden mate is a redaction marker").containsExactly("J72", "");
        assertThat(error("impact_of_change", Map.of("part", "x", "feature", "y"), OFFICER)).isEqualTo("name either part or feature");
    }

    @Test
    void exportStatusFollowsTheProfile() {
        JsonNode de = ok("export_status", Map.of("part", "de-tail-drive"), DE);
        assertThat(de.path("visible").asBoolean()).isFalse();
        assertThat(de.path("cadAvailable").asBoolean()).isFalse();
        assertThat(de.path("part").path("redacted").asBoolean()).isTrue();
        assertThat(de.path("part").has("jurisdiction")).isFalse();

        JsonNode officer = ok("export_status", Map.of("part", "de-tail-drive"), OFFICER);
        assertThat(officer.path("visible").asBoolean()).isTrue();
        assertThat(officer.path("part").path("jurisdiction").asText()).isEqualTo("EXPORT-LICENCE");
        assertThat(officer.path("part").path("releasableTo").asText()).isEqualTo("LICENSED");
        assertThat(officer.path("part").path("taggedBy").asText()).isEqualTo("DE");
        assertThat(officer.path("cadAvailable").asBoolean()).as("no CAD bucket here").isFalse();
        assertThat(officer.path("policy").path("untagged")).extracting(JsonNode::asText).containsExactly("https://example.com/atelier/es/part/es-tail-plane");
        assertThat(officer.path("part").has("supplier")).as("the tail drive is built by the PLM's own plant").isFalse();

        JsonNode controlPost = ok("export_status", Map.of("part", "fr-control-post"), OFFICER);
        assertThat(controlPost.path("part").path("supplier").asText()).isEqualTo("Ateliers du Clos Lucé, Amboise");
        JsonNode controlPostForDe = ok("export_status", Map.of("part", "fr-control-post"), DE);
        assertThat(controlPostForDe.path("part").path("redacted").asBoolean()).isTrue();
        assertThat(controlPostForDe.toString()).as("a redacted part never exposes its supplier").doesNotContain("supplier");
    }

    @Test
    void toolAnswersAreCompact() throws IOException {
        McpSchema.CallToolResult result = tools.call("list_interfaces", Map.of(), OFFICER);
        String text = text(result);
        assertThat(text.length()).as("compact JSON").isLessThan(6 * 1024);
        JsonNode list = JSON.readTree(text);
        assertThat(list.fieldNames()).toIterable().containsExactly("interfaces", "provenance", "timings", "policy");
        assertThat(list.path("provenance").path("calls").get(0).fieldNames()).toIterable()
                .containsExactly("endpoint", "kind", "requests", "triples", "ms", "requestBytes", "largestRequestBytes");
        for (JsonNode i : list.path("interfaces")) {
            boolean failing = i.path("status").asText().equals("fail");
            assertThat(i.fieldNames()).toIterable().as(i.path("id").asText()).containsExactly(failing
                    ? new String[] {"id", "product", "label", "status", "parts", "rulesFailing", "messages"}
                    : new String[] {"id", "product", "label", "status", "parts", "rulesFailing"});
            assertThat(i.path("parts")).allSatisfy(p -> assertThat(p.fieldNames()).toIterable()
                    .startsWith("id", "plm").isSubsetOf("id", "plm", "supplier"));
            if (failing) assertThat(i.path("messages")).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        }
        assertThat(list.path("interfaces").get(0).path("parts").toString()).as("supplier only on the supplier-built control post")
                .isEqualTo("[{\"id\":\"de-right-root-fitting\",\"plm\":\"de\"},{\"id\":\"fr-control-post\",\"plm\":\"fr\",\"supplier\":\"Ateliers du Clos Lucé, Amboise\"}]");
        JsonNode if06 = list.path("interfaces").get(5);
        assertThat(if06.path("rulesFailing")).extracting(JsonNode::asText).containsExactly("unit", "hydraulic");
        assertThat(if06.path("messages")).as("3 unit messages and 1 hydraulic: cut to 3").hasSize(3);

        for (String tool : List.of("interface_check", "where_used", "impact_of_change", "export_status", "sparql")) {
            Map<String, Object> args = switch (tool) {
                case "interface_check" -> Map.of("interface", "IF-02");
                case "where_used", "export_status" -> Map.of("part", "fr-cross-beam-mid");
                case "impact_of_change" -> Map.of("feature", "HC 61");
                default -> Map.of("query", PREFIX + "ASK { ?i a atelier:Interface }");
            };
            JsonNode answer = ok(tool, args, OFFICER);
            assertThat(answer.has("sparql")).as(tool).isFalse();
            assertThat(answer.path("provenance").path("calls")).as(tool).hasSize(6);
            assertThat(answer.path("policy").path("profile").asText()).as(tool).isEqualTo("export-officer");
        }
        JsonNode arm = ok("evidence", Map.of("interface", "IF-02", "endpoint", "ontop-fr"), OFFICER).path("arm");
        assertThat(arm.path("sparql").asText()).as("the evidence tool is where the request texts are").contains("CONSTRUCT");
        assertThat(arm.has("sql")).isTrue();
    }

    @Test
    void listInterfacesInterfaceCheckAndEvidenceMirrorTheRestAnswers() {
        JsonNode list = ok("list_interfaces", Map.of(), DE);
        assertThat(list.path("interfaces")).hasSize(8);
        JsonNode if01 = list.path("interfaces").get(0);
        assertThat(if01.path("id").asText()).isEqualTo("IF-01");
        assertThat(if01.path("status").asText()).isEqualTo("not-evaluable");
        assertThat(if01.has("messages")).isFalse();
        assertThat(if01.path("parts").toString()).isEqualTo("[{\"id\":\"de-right-root-fitting\",\"plm\":\"de\"},{\"redacted\":true,\"plm\":\"fr\"}]");
        assertThat(list.path("interfaces").get(1).path("rulesFailing")).extracting(JsonNode::asText).containsExactly("position");
        assertThat(list.path("interfaces").get(1).path("messages").get(0).asText()).contains("4.3 mm");

        JsonNode check = ok("interface_check", Map.of("interface", "IF-02"), DE);
        assertThat(check.path("interface").path("violations").get(0).path("detail").path("deltaMm").asDouble()).isEqualTo(4.3);
        assertThat(check.path("interface").path("product").asText()).isEqualTo("ornithopter");
        assertThat(ok("interface_check", Map.of("interface", "https://example.com/atelier/interface/ornithopter/IF-02"), DE).path("interface"))
                .as("an IRI names the same interface").isEqualTo(check.path("interface"));
        assertThat(ok("interface_check", Map.of("interface", "IF-02", "product", "ornithopter"), DE).path("interface"))
                .as("so does the id with its product").isEqualTo(check.path("interface"));
        assertThat(error("interface_check", Map.of("interface", "IF-02", "product", "wings"), DE)).isEqualTo("no interface wings/IF-02");

        JsonNode evidence = ok("evidence", Map.of("interface", "IF-02", "endpoint", "ontop-de"), DE);
        assertThat(evidence.path("interfaceId").asText()).isEqualTo("IF-02");
        assertThat(evidence.path("product").asText()).isEqualTo("ornithopter");
        assertThat(evidence.path("arm").path("endpoint").asText()).isEqualTo("ontop-de");
        assertThat(evidence.path("arm").path("requests").asInt()).isEqualTo(6);
        assertThat(evidence.path("arm").path("sparql").asText()).contains("CONSTRUCT");
        assertThat(evidence.path("arm").path("tables").get(0).path("table").asText()).isEqualTo("bauteil");
        assertThat(error("evidence", Map.of("interface", "IF-02", "endpoint", "ontop-xx"), DE)).startsWith("unknown endpoint ontop-xx");
    }

    @Test
    void sparqlIsGuardedLimitedAndRunsOnTheProfilesGraph() {
        assertThat(error("sparql", Map.of("query", PREFIX + "SELECT * WHERE { ?s atelier:weight ?o }"), OFFICER))
                .startsWith("unknown terms in predicate or class position: [atelier:weight]; known classes:");
        assertThat(error("sparql", Map.of("query", PREFIX + "INSERT DATA { <urn:a> atelier:label \"x\" }"), OFFICER))
                .startsWith("update forms are not accepted");

        JsonNode all = ok("sparql", Map.of("query", "SELECT * WHERE { ?s ?p ?o }"), OFFICER);
        assertThat(all.path("form").asText()).isEqualTo("select");
        assertThat(all.path("query").asText()).contains("LIMIT   200");
        assertThat(all.path("limit").asInt()).isEqualTo(200);
        assertThat(all.path("rows")).hasSize(200);
        assertThat(all.path("limitReached").asBoolean()).isTrue();
        assertThat(all.path("graphTriples").asLong()).isGreaterThan(200);
        assertThat(all.path("ms").isNumber()).isTrue();
        assertThat(all.path("provenance").path("calls")).hasSize(6);
        assertThat(all.has("sparql")).isFalse();

        String parts = PREFIX + "SELECT ?p ?label WHERE { ?p a atelier:Part ; atelier:label ?label } ORDER BY ?p";
        JsonNode officer = ok("sparql", Map.of("query", parts), OFFICER);
        assertThat(officer.path("columns")).extracting(JsonNode::asText).containsExactly("p", "label");
        assertThat(officer.path("rows")).hasSize(9);
        assertThat(officer.path("limitReached").asBoolean()).isFalse();
        JsonNode de = ok("sparql", Map.of("query", parts), DE);
        assertThat(de.path("rows")).as("the control post, the tail drive and the untagged tail plane are not in the DE engineer's graph").hasSize(6);
        assertThat(de.toString()).doesNotContain("Control post").doesNotContain("Tail trim hydraulic drive").doesNotContain("NATIONAL-FR");

        JsonNode count = ok("sparql", Map.of("query", PREFIX + "SELECT (COUNT(?f) AS ?n) WHERE { ?f a atelier:Plug }"), OFFICER);
        assertThat(count.path("rows").get(0).get(0).asInt()).isEqualTo(15);
        JsonNode ask = ok("sparql", Map.of("query", PREFIX + "ASK { ?i a atelier:Interface }"), OFFICER);
        assertThat(ask.path("form").asText()).isEqualTo("ask");
        assertThat(ask.path("result").asBoolean()).isTrue();
        assertThat(ask.has("limit")).isFalse();
        JsonNode construct = ok("sparql", Map.of("query", PREFIX + "CONSTRUCT { ?i atelier:label ?l } WHERE { ?i a atelier:Interface ; atelier:label ?l }"), OFFICER);
        assertThat(construct.path("form").asText()).isEqualTo("construct");
        assertThat(construct.path("turtle").asText()).contains("atelier:label");
    }

    @Test
    void sparqlNeverNamesAHiddenPartOrFeature() {
        String parts = PREFIX + "SELECT ?i ?p WHERE { ?i atelier:betweenPart ?p } ORDER BY ?i ?p";
        JsonNode officer = ok("sparql", Map.of("query", parts), OFFICER);
        JsonNode de = ok("sparql", Map.of("query", parts), DE);
        assertThat(officer.path("rows").toString()).contains("https://example.com/atelier/fr/part/fr-control-post").contains("de/part/de-tail-drive");
        assertThat(de.path("rows")).as("the interface structure stays countable").hasSameSizeAs(officer.path("rows"));
        assertThat(de.path("rows").toString()).as("hidden parts are blank nodes, never the IRI the named tools redact")
                .doesNotContain("fr-control-post").doesNotContain("de-tail-drive").doesNotContain("es-tail-plane").contains("\"_:");
        assertThat(de.path("graphTriples").asLong()).isEqualTo(service.answer(DE).model().size());

        String features = PREFIX + "SELECT ?f WHERE { ?i atelier:declaresFeature ?f }";
        JsonNode deFeatures = ok("sparql", Map.of("query", features), DE);
        assertThat(deFeatures.path("rows").toString()).doesNotContain("fr/plug/J11").doesNotContain("de/plug/P71")
                .doesNotContain("de/plug/P72").doesNotContain("es/plug/P82").contains("de/plug/P11").contains("es/plug/J82");
        assertThat(ok("sparql", Map.of("query", features), OFFICER).path("rows").toString()).contains("fr/plug/J11").contains("de/plug/P71");
        String mates = PREFIX + "SELECT ?m WHERE { <https://example.com/atelier/es/plug/J72> atelier:matesWith ?m }";
        assertThat(ok("sparql", Map.of("query", mates), DE).path("rows").get(0).get(0).asText()).startsWith("_:");
    }

    @Test
    void aFailingEndpointIsReportedByItsProvenanceNameOnly() {
        Federator failing = new FixtureFederator() {
            @Override
            protected Model answer(String url, String query) {
                if (url.equals("http://uk/sparql")) {
                    throw new QueryExceptionHTTP(503, "HTTP 503 error making the query: http://ontop-uk.atelier.internal:8080/sparql");
                }
                return super.answer(url, query);
            }
        };
        AtelierTools brokenUk = new AtelierTools(new QueryService(failing, new RuleValidator(), new NoOntopSql(), new CadUrls(null, null)),
                POLICY, JSON, new PlmApi("", () -> null), KnownTerms.load());
        McpSchema.CallToolResult result = brokenUk.call("list_interfaces", Map.of(), OFFICER);
        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo("a federated endpoint failed: ontop-uk");
    }

    @Test
    void catalogueAndSqlReportThemselvesUnavailableWithoutThePlmApi() {
        assertThat(error("catalogue", Map.of("plm", "uk"), OFFICER)).isEqualTo("PLM_API_BASE is not set: the PLM and core services are not reachable here");
        assertThat(error("sql", Map.of("plm", "xx", "query", "SELECT 1", "purpose", "t"), OFFICER)).startsWith("unknown plm xx");
        assertThat(error("sql", Map.of("plm", "uk", "query", "SELECT 1"), OFFICER)).isEqualTo("argument 'purpose' is required");
    }

    private String description(String tool) {
        return tools.tools().stream().filter(t -> t.name().equals(tool)).findFirst().orElseThrow().description();
    }

    private JsonNode ok(String tool, Map<String, Object> args, Caller caller) {
        McpSchema.CallToolResult result = tools.call(tool, args, caller);
        assertThat(result.isError()).as(tool + " " + args + ": " + text(result)).isNotEqualTo(Boolean.TRUE);
        try {
            return JSON.readTree(text(result));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String error(String tool, Map<String, Object> args, Caller caller) {
        McpSchema.CallToolResult result = tools.call(tool, args, caller);
        assertThat(result.isError()).as(tool + " " + args).isTrue();
        return text(result);
    }

    private static String text(McpSchema.CallToolResult result) {
        List<McpSchema.Content> content = result.content();
        assertThat(content).hasSize(1);
        return ((McpSchema.TextContent) content.get(0)).text();
    }
}
