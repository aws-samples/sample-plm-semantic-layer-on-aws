// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.FixtureFederator;
import atelier.query.federation.Federator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * The MCP endpoint over the real Spring WebMVC streamable-HTTP transport, called with the SDK's own
 * client: tools/list, and tools/call under different {@code x-atelier-profile} / {@code x-atelier-actor}
 * headers, the federation answered by {@link FixtureFederator}. The REST answers the tools reuse are
 * called on the same server, as is the plain-REST tool listing at {@code /query/mcp/tools}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ONTOP_FR_URL=http://fr/sparql", "ONTOP_DE_URL=http://de/sparql", "ONTOP_UK_URL=http://uk/sparql",
        "ONTOP_ES_URL=http://es/sparql", "ONTOP_CORE_URL=http://core/sparql", "NEPTUNE_SPARQL_URL=http://links/query",
        "atelier.warmup.enabled=false"})
class McpEndpointTest {
    @TestConfiguration
    static class Fixture {
        @Bean
        @Primary
        Federator federator() {
            return new FixtureFederator();
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    int port;

    @Test
    void listsTheTwentyFiveToolsAndAnswersUnderTheCallersHeaders() {
        try (McpSyncClient de = client(Map.of("x-atelier-profile", "de-engineer", "x-atelier-actor", "agent"))) {
            McpSchema.InitializeResult init = de.initialize();
            assertThat(init.serverInfo().name()).isEqualTo("atelier-semantic-layer");
            assertThat(de.listTools().tools()).extracting(McpSchema.Tool::name).containsExactly("products", "list_interfaces",
                    "parts", "interface_check", "where_used", "impact_of_change", "export_status", "bom", "bom_where_used", "path_between", "flow_path", "variant_diff", "external_references",
                    "equivalent_parts", "find_term", "find_parts", "suppliers", "parts_between_stations", "section_joints", "evidence", "ontology", "sparql",
                    "preview_correction", "catalogue", "sql");

            JsonNode products = call(de, "products", Map.of());
            assertThat(products.path("products")).extracting(p -> p.path("key").asText()).containsExactly("ornithopter", "tail", "wings");
            JsonNode scoped = call(de, "list_interfaces", Map.of("product", "wings"));
            assertThat(scoped.path("interfaces")).extracting(i -> i.path("id").asText()).containsExactly("IF-05", "IF-06");

            JsonNode drive = call(de, "export_status", Map.of("part", "de-tail-drive"));
            assertThat(drive.path("visible").asBoolean()).as("the EXPORT-LICENCE tail drive is not visible to the DE engineer").isFalse();
            assertThat(drive.path("part").path("redacted").asBoolean()).isTrue();
            assertThat(drive.path("policy").path("profile").asText()).isEqualTo("de-engineer");
            assertThat(drive.path("policy").path("actor").asText()).as("the acting agent is echoed").isEqualTo("agent");

            JsonNode list = call(de, "list_interfaces", Map.of());
            assertThat(list.path("interfaces")).extracting(i -> i.path("status").asText())
                    .containsExactly("not-evaluable", "fail", "fail", "fail", "pass", "fail", "not-evaluable", "not-evaluable");
        }
        try (McpSyncClient officer = client(Map.of("x-atelier-profile", "export-officer"))) {
            officer.initialize();
            JsonNode drive = call(officer, "export_status", Map.of("part", "de-tail-drive"));
            assertThat(drive.path("visible").asBoolean()).isTrue();
            assertThat(drive.path("part").path("jurisdiction").asText()).isEqualTo("EXPORT-LICENCE");
            assertThat(drive.path("policy").path("actor").asText()).as("no actor header: the user").isEqualTo("user");

            McpSchema.CallToolResult rejected = officer.callTool(new McpSchema.CallToolRequest("sparql",
                    Map.of("query", "PREFIX atelier: <https://example.com/atelier/ontology#> SELECT * WHERE { ?s atelier:weight ?o }")));
            assertThat(rejected.isError()).isTrue();
            assertThat(((McpSchema.TextContent) rejected.content().get(0)).text()).startsWith("unknown terms in predicate or class position: [atelier:weight]");
        }
        try (McpSyncClient nobody = client(Map.of())) {
            nobody.initialize();
            JsonNode list = call(nobody, "list_interfaces", Map.of());
            assertThat(list.path("policy").path("profile").asText()).isEqualTo("unknown");
            assertThat(list.path("interfaces")).extracting(i -> i.path("status").asText()).containsOnly("not-evaluable");
        }
    }

    @Test
    void restAnswersCarryTheSameShapesAndTheActor() throws IOException {
        TestRestTemplate rest = new TestRestTemplate();
        Function<String, ResponseEntity<String>> get = path -> {
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-atelier-profile", "export-officer");
            headers.set("x-atelier-actor", "agent");
            return rest.exchange(URI.create("http://localhost:" + port + "/query" + path), HttpMethod.GET,
                    new org.springframework.http.HttpEntity<>(headers), String.class);
        };

        ResponseEntity<String> whereUsed = get.apply("/parts/fr-cross-beam-mid/where-used");
        assertThat(whereUsed.getStatusCode().value()).isEqualTo(200);
        JsonNode used = JSON.readTree(whereUsed.getBody());
        assertThat(used.path("interfaces")).extracting(i -> i.path("id").asText()).containsExactly("IF-02", "IF-03", "IF-05", "IF-06");
        assertThat(used.path("policy").path("actor").asText()).isEqualTo("agent");

        JsonNode impact = JSON.readTree(get.apply("/impact?feature=HC%2061").getBody());
        assertThat(impact.path("interfaces").get(0).path("id").asText()).isEqualTo("IF-06");
        assertThat(get.apply("/impact").getStatusCode().value()).isEqualTo(400);
        assertThat(get.apply("/impact?part=x&feature=y").getStatusCode().value()).isEqualTo(400);

        JsonNode status = JSON.readTree(get.apply("/parts/de-tail-drive/export-status").getBody());
        assertThat(status.path("visible").asBoolean()).isTrue();
        assertThat(status.path("part").path("releasableTo").asText()).isEqualTo("LICENSED");
        assertThat(get.apply("/parts/nope/export-status").getStatusCode().value()).isEqualTo(404);
        assertThat(JSON.readTree(get.apply("/interfaces").getBody()).path("policy").path("actor").asText()).isEqualTo("agent");

        JsonNode products = JSON.readTree(get.apply("/products").getBody());
        assertThat(products.path("products")).extracting(p -> p.path("key").asText() + ":" + p.path("partCount").asInt())
                .containsExactly("ornithopter:9", "tail:3", "wings:3");
        assertThat(products.path("sparql").asText()).contains("atelier:partOf");
        JsonNode wings = JSON.readTree(get.apply("/interfaces?product=wings").getBody());
        assertThat(wings.path("interfaces")).extracting(i -> i.path("id").asText()).containsExactly("IF-05", "IF-06");
        assertThat(JSON.readTree(get.apply("/parts?product=wings").getBody()).path("parts")).hasSize(3);
        assertThat(get.apply("/interfaces?product=").getStatusCode().value()).as("a blank product means every product").isEqualTo(200);
        assertThat(get.apply("/interfaces?product=nope").getStatusCode().value()).isEqualTo(404);
        assertThat(get.apply("/parts?product=wing%20box").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void restToolListingMatchesTheMcpToolsList() throws IOException {
        McpSchema.InitializeResult init;
        List<String> mcpNames;
        try (McpSyncClient client = client(Map.of())) {
            init = client.initialize();
            mcpNames = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
        }

        ResponseEntity<String> response = new TestRestTemplate()
                .getForEntity(URI.create("http://localhost:" + port + McpServerConfig.ENDPOINT + "/tools"), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/json");
        JsonNode listing = JSON.readTree(response.getBody());
        assertThat(listing.path("server").path("name").asText()).isEqualTo(init.serverInfo().name()).isEqualTo("atelier-semantic-layer");
        assertThat(listing.path("server").path("version").asText()).isEqualTo(init.serverInfo().version());
        assertThat(listing.path("tools")).extracting(t -> t.path("name").asText()).hasSize(25).containsExactlyElementsOf(mcpNames);
        assertThat(listing.path("tools")).allSatisfy(t -> assertThat(t.path("description").asText()).as(t.path("name").asText()).isNotBlank());
        assertThat(listing.path("count").asInt()).isEqualTo(25);
    }

    @Test
    void ontologyAnswersTheSameOverRestAndMcp() throws IOException {
        ResponseEntity<String> rest = new TestRestTemplate().getForEntity(URI.create("http://localhost:" + port + "/query/ontology"), String.class);
        assertThat(rest.getStatusCode().value()).as("public, no profile header").isEqualTo(200);
        assertThat(rest.getHeaders().getContentType().toString()).startsWith("application/json");
        assertThat(rest.getBody().getBytes(StandardCharsets.UTF_8).length).isLessThan(32 * 1024);
        JsonNode overRest = JSON.readTree(rest.getBody());
        assertThat(overRest.path("version").asText()).isNotBlank();
        assertThat(overRest.path("classes")).isNotEmpty();
        assertThat(overRest.path("properties")).isNotEmpty();
        assertThat(overRest.path("rules")).isNotEmpty();
        assertThat(overRest.path("examples")).hasSize(3);
        try (McpSyncClient client = client(Map.of("x-atelier-profile", "de-engineer", "x-atelier-actor", "agent"))) {
            client.initialize();
            assertThat(call(client, "ontology", Map.of())).isEqualTo(overRest);
        }
    }

    private McpSyncClient client(Map<String, String> headers) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint(McpServerConfig.ENDPOINT)
                .customizeRequest(request -> headers.forEach(request::header))
                .build();
        return McpClient.sync(transport).requestTimeout(Duration.ofSeconds(60)).build();
    }

    private static JsonNode call(McpSyncClient client, String tool, Map<String, Object> args) {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(tool, args));
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(result.isError()).as(tool + ": " + text).isNotEqualTo(Boolean.TRUE);
        try {
            return JSON.readTree(text);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
