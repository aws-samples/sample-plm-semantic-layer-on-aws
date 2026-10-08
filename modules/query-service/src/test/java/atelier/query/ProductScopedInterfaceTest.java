// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import atelier.query.api.ApiErrors;
import atelier.query.api.Json;
import atelier.query.api.QueryController;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.mcp.AtelierTools;
import atelier.query.mcp.PlmApi;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.validation.RuleValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Interface ids are unique within a product. Over fixtures/neighbourhood.ttl with fixtures/same-id.ttl
 * added (the wings' IF-02 beside the ornithopter's): how the REST API, the service and the MCP tools
 * name an interface by id and product, and how a product scope attributes an interface to its own
 * product when two products share its parts.
 */
class ProductScopedInterfaceTest {
    static final Policy POLICY = EvidenceTest.POLICY;
    static final Caller OFFICER = Caller.user(POLICY.profile("export-officer"));
    static final ObjectMapper JSON = new ObjectMapper();
    static final String AMBIGUOUS = "interface IF-02 exists in several products (ornithopter, wings): pass product to name one";

    private final QueryService service = new QueryService(new FixtureFederator(fixture()), new RuleValidator(), new NoOntopSql(),
            new CadUrls(null, null));
    private final AtelierTools tools = new AtelierTools(service, POLICY, JSON, new PlmApi("", () -> null));
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new QueryController(service, POLICY,
            new Warmup(service, POLICY, FixtureFederator.ENDPOINTS, false))).setControllerAdvice(new ApiErrors()).build();

    /** neighbourhood.ttl with the wings' IF-02 added. */
    static Model fixture() {
        return FixtureFederator.fixture().add(RDFDataMgr.loadModel("fixtures/same-id.ttl"));
    }

    @Test
    void anIdTwoProductsHaveResolvesWithTheProductAndIsRefusedWithoutIt() {
        Json.Interface wings = service.interfaceById(new InterfaceRef("IF-02", "wings"), OFFICER).orElseThrow()._interface();
        Json.Interface ornithopter = service.interfaceById(new InterfaceRef("IF-02", "ornithopter"), OFFICER).orElseThrow()._interface();
        assertThat(wings.id()).isEqualTo(ornithopter.id()).isEqualTo("IF-02");
        assertThat(wings.product()).isEqualTo("wings");
        assertThat(wings.status()).isEqualTo("pass");
        assertThat(wings.parts()).extracting(p -> ((Json.Part) p).id()).containsExactly("fr-cross-beam-mid", "uk-left-root-fitting");
        assertThat(ornithopter.product()).isEqualTo("ornithopter");
        assertThat(ornithopter.status()).isEqualTo("fail");
        assertThat(ornithopter.violations()).extracting(Json.Violation::rule).containsExactly("position");

        assertThatThrownBy(() -> service.interfaceById("IF-02", OFFICER)).isInstanceOf(AmbiguousInterface.class).hasMessage(AMBIGUOUS);
        assertThatThrownBy(() -> service.evidence("IF-02", OFFICER)).isInstanceOf(AmbiguousInterface.class).hasMessage(AMBIGUOUS);
        assertThat(service.interfaceById("IF-05", OFFICER).orElseThrow()._interface().product())
                .as("an id one product has needs no product").isEqualTo("wings");
        assertThat(service.evidence("IF-05", OFFICER).orElseThrow().product()).isEqualTo("wings");
        assertThat(service.interfaceById(new InterfaceRef("IF-05", "ornithopter"), OFFICER)).as("the ornithopter has no IF-05").isEmpty();
        assertThat(service.evidence(new InterfaceRef("IF-99", "wings"), OFFICER)).isEmpty();
        assertThatThrownBy(() -> service.interfaceById(new InterfaceRef("IF-02", "wing box"), OFFICER))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("invalid product key");
    }

    @Test
    void theRestApiAnswers400NamingTheProductsWithoutProductAndEachInterfaceWithIt() throws Exception {
        MockHttpServletResponse ambiguous = request("/query/interfaces/IF-02");
        assertThat(ambiguous.getStatus()).isEqualTo(400);
        assertThat(JSON.readTree(ambiguous.getContentAsString())).isEqualTo(JSON.readTree(
                "{\"error\":\"" + AMBIGUOUS + "\",\"id\":\"IF-02\",\"products\":[\"ornithopter\",\"wings\"]}"));
        MockHttpServletResponse ambiguousEvidence = request("/query/interfaces/IF-02/evidence");
        assertThat(ambiguousEvidence.getStatus()).isEqualTo(400);
        assertThat(JSON.readTree(ambiguousEvidence.getContentAsString()).path("products")).extracting(JsonNode::asText)
                .containsExactly("ornithopter", "wings");

        JsonNode wings = JSON.readTree(request("/query/interfaces/IF-02?product=wings").getContentAsString());
        assertThat(wings.path("interface").path("id").asText()).isEqualTo("IF-02");
        assertThat(wings.path("interface").path("product").asText()).isEqualTo("wings");
        assertThat(wings.path("interface").path("status").asText()).isEqualTo("pass");
        JsonNode ornithopter = JSON.readTree(request("/query/interfaces/IF-02?product=ornithopter").getContentAsString());
        assertThat(ornithopter.path("interface").path("product").asText()).isEqualTo("ornithopter");
        assertThat(ornithopter.path("interface").path("status").asText()).isEqualTo("fail");
        JsonNode evidence = JSON.readTree(request("/query/interfaces/IF-02/evidence?product=wings").getContentAsString());
        assertThat(evidence.path("interfaceId").asText()).as("the bare id").isEqualTo("IF-02");
        assertThat(evidence.path("product").asText()).isEqualTo("wings");
        assertThat(evidence.path("merged").path("turtle").asText()).contains("interface/wings/IF-02").doesNotContain("ornithopter/IF-02");

        MockHttpServletResponse missing = request("/query/interfaces/IF-02?product=tail");
        assertThat(missing.getStatus()).isEqualTo(404);
        assertThat(missing.getContentAsString()).isEqualTo("{\"error\":\"no interface tail/IF-02\"}");
        assertThat(request("/query/interfaces/IF-99").getStatus()).isEqualTo(404);
        assertThat(request("/query/interfaces/IF-02?product=tail%20unit").getStatus()).as("a key outside the alphabet").isEqualTo(400);
        assertThat(request("/query/interfaces/IF-05?product=").getStatus()).as("a blank product means no product").isEqualTo(200);
    }

    @Test
    void theToolsTakeTheProductAndNameTheCandidatesWithoutIt() {
        assertThat(error("interface_check", Map.of("interface", "IF-02"))).isEqualTo(AMBIGUOUS);
        assertThat(error("evidence", Map.of("interface", "IF-02", "endpoint", "ontop-uk"))).isEqualTo(AMBIGUOUS);
        JsonNode wings = ok("interface_check", Map.of("interface", "IF-02", "product", "wings")).path("interface");
        assertThat(wings.path("product").asText()).isEqualTo("wings");
        assertThat(wings.path("status").asText()).isEqualTo("pass");
        assertThat(ok("interface_check", Map.of("interface", "IF-02", "product", "ornithopter")).path("interface").path("status").asText())
                .isEqualTo("fail");
        assertThat(ok("interface_check", Map.of("interface", "https://example.com/atelier/interface/wings/IF-02")).path("interface"))
                .as("an IRI names its product itself").isEqualTo(wings);
        assertThat(error("interface_check", Map.of("interface", "IF-02", "product", "tail"))).isEqualTo("no interface tail/IF-02");

        JsonNode arm = ok("evidence", Map.of("interface", "IF-02", "endpoint", "ontop-uk", "product", "wings"));
        assertThat(arm.path("interfaceId").asText()).isEqualTo("IF-02");
        assertThat(arm.path("product").asText()).isEqualTo("wings");
        assertThat(arm.path("arm").path("tables").toString()).contains("HC 22");

        assertThat(ok("list_interfaces", Map.of()).path("interfaces")).extracting(i -> i.path("id").asText() + " " + i.path("product").asText())
                .startsWith("IF-01 ornithopter", "IF-02 ornithopter", "IF-02 wings", "IF-03 ornithopter");
    }

    @Test
    void aProductScopeKeepsAnInterfaceOfItsOwnProductOnlyEvenWhenAnotherProductHoldsItsParts() {
        assertThat(service.interfaces(OFFICER, "ornithopter").interfaces()).extracting(Json.Interface::id, Json.Interface::product)
                .as("both parts of the wings' IF-02 belong to the ornithopter too")
                .containsExactly(tuple("IF-01", "ornithopter"), tuple("IF-02", "ornithopter"), tuple("IF-03", "ornithopter"), tuple("IF-04", "ornithopter"));
        assertThat(service.interfaces(OFFICER, "wings").interfaces()).extracting(Json.Interface::id, Json.Interface::product)
                .containsExactly(tuple("IF-02", "wings"), tuple("IF-05", "wings"), tuple("IF-06", "wings"));

        assertThat(service.whereUsed("fr-cross-beam-mid", OFFICER).orElseThrow().interfaces()).extracting(Json.Use::id)
                .containsExactly("IF-02", "IF-02", "IF-03", "IF-05", "IF-06");
        assertThat(service.whereUsed("fr-cross-beam-mid", OFFICER, "wings").orElseThrow().interfaces()).extracting(Json.Use::id)
                .containsExactly("IF-02", "IF-05", "IF-06");
        assertThat(ok("where_used", Map.of("part", "fr-cross-beam-mid", "product", "ornithopter")).path("interfaces"))
                .extracting(i -> i.path("id").asText()).containsExactly("IF-02", "IF-03");
        assertThat(ok("impact_of_change", Map.of("feature", "J22", "product", "wings")).path("interfaces"))
                .extracting(i -> i.path("id").asText()).containsExactly("IF-02");
        assertThat(error("impact_of_change", Map.of("feature", "J22", "product", "ornithopter"))).isEqualTo("no interface declares feature J22");
        assertThat(error("where_used", Map.of("part", "fr-cross-beam-mid", "product", "nope"))).isEqualTo("no product nope");
    }

    private MockHttpServletResponse request(String path) throws Exception {
        return mvc.perform(get(path).header(QueryController.PROFILE_HEADER, "export-officer")).andReturn().getResponse();
    }

    private JsonNode ok(String tool, Map<String, Object> args) {
        McpSchema.CallToolResult result = tools.call(tool, args, OFFICER);
        assertThat(result.isError()).as(tool + " " + args + ": " + text(result)).isNotEqualTo(Boolean.TRUE);
        try {
            return JSON.readTree(text(result));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String error(String tool, Map<String, Object> args) {
        McpSchema.CallToolResult result = tools.call(tool, args, OFFICER);
        assertThat(result.isError()).as(tool + " " + args).isTrue();
        return text(result);
    }

    private static String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().get(0)).text();
    }
}
