// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The catalogue and sql forwarders against a stub standing in for the API Gateway and a PLM service:
 * what they send (path, method, body, the origin secret, the caller's profile and actor) and how the
 * PLM's answer, success or rejection, reaches the tool caller unchanged.
 */
class PlmApiTest {
    /** One request the stub received. */
    record Seen(String method, String path, Map<String, String> headers, String body) {}

    private static final String CATALOGUE = "{\"plm\":\"UK\",\"entities\":[{\"table\":\"harness_connector\"}]}";
    private static final String REJECTION = "{\"error\":\"unknown column pos_uon\",\"suggestions\":[\"pos_uom\"]}";
    private static final String RAN = "{\"sql\":\"SELECT ... LIMIT 200\",\"rowCount\":2,\"rows\":[[1],[2]],\"ms\":3}";

    private final List<Seen> seen = new ArrayList<>();
    private HttpServer stub;
    private PlmApi api;

    @BeforeEach
    void start() throws IOException {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/api/", this::answer);
        stub.start();
        api = new PlmApi("http://127.0.0.1:" + stub.getAddress().getPort() + "/api/", () -> "s3cret");
    }

    @AfterEach
    void stop() {
        stub.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : List.of("x-origin-verify", "x-atelier-profile", "x-atelier-actor")) {
            headers.put(name, exchange.getRequestHeaders().getFirst(name));
        }
        seen.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers, body));
        String reply = switch (exchange.getRequestURI().getPath()) {
            case "/api/uk/catalogue" -> CATALOGUE;
            case "/api/uk/sql" -> body.contains("pos_uon") ? REJECTION : RAN;
            default -> "{\"error\":\"not found\"}";
        };
        int status = reply == REJECTION ? 400 : reply == CATALOGUE || reply == RAN ? 200 : 404;
        byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void catalogueForwardsTheHeadersAndReturnsTheJsonAsIs() {
        Caller agent = new Caller(profile("uk-engineer"), "agent");
        PlmApi.Reply reply = api.catalogue("uk", agent);

        assertThat(reply.ok()).isTrue();
        assertThat(reply.body()).isEqualTo(CATALOGUE);
        assertThat(seen).containsExactly(new Seen("GET", "/api/uk/catalogue",
                Map.of("x-origin-verify", "s3cret", "x-atelier-profile", "uk-engineer", "x-atelier-actor", "agent"), ""));
    }

    @Test
    void sqlPostsTheStatementAndPurposeAndPassesRejectionsThrough() {
        Caller user = Caller.user(profile("uk-engineer"));
        PlmApi.Reply ran = api.sql("uk", "SELECT conn_ref FROM harness_connector WHERE pos_uom IS NULL", "unit-less plugs", user);
        assertThat(ran.ok()).isTrue();
        assertThat(ran.body()).isEqualTo(RAN);
        assertThat(seen.get(0).method()).isEqualTo("POST");
        assertThat(seen.get(0).path()).isEqualTo("/api/uk/sql");
        assertThat(seen.get(0).headers()).containsEntry("x-atelier-profile", "uk-engineer").containsEntry("x-atelier-actor", "user")
                .containsEntry("x-origin-verify", "s3cret");
        assertThat(seen.get(0).body()).isEqualTo("{\"sql\":\"SELECT conn_ref FROM harness_connector WHERE pos_uom IS NULL\",\"purpose\":\"unit-less plugs\"}");

        PlmApi.Reply rejected = api.sql("uk", "SELECT pos_uon FROM harness_connector", "typo", user);
        assertThat(rejected.ok()).isFalse();
        assertThat(rejected.status()).isEqualTo(400);
        assertThat(rejected.body()).isEqualTo(REJECTION);
    }

    @Test
    void toolsHandThePlmAnswerToTheCallerAndItsRejectionAsAnError() {
        AtelierTools tools = new AtelierTools(null, POLICY, new com.fasterxml.jackson.databind.ObjectMapper(), api, KnownTerms.load());
        Caller user = Caller.user(profile("uk-engineer"));

        io.modelcontextprotocol.spec.McpSchema.CallToolResult catalogue = tools.call("catalogue", Map.of("plm", "UK"), user);
        assertThat(catalogue.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(((io.modelcontextprotocol.spec.McpSchema.TextContent) catalogue.content().get(0)).text()).isEqualTo(CATALOGUE);

        io.modelcontextprotocol.spec.McpSchema.CallToolResult rejected = tools.call("sql",
                Map.of("plm", "uk", "query", "SELECT pos_uon FROM harness_connector", "purpose", "typo"), user);
        assertThat(rejected.isError()).isTrue();
        assertThat(((io.modelcontextprotocol.spec.McpSchema.TextContent) rejected.content().get(0)).text())
                .isEqualTo("the PLM service answered 400: " + REJECTION);
    }

    @Test
    void withoutAnOriginSecretNoHeaderIsSent() {
        PlmApi open = new PlmApi("http://127.0.0.1:" + stub.getAddress().getPort() + "/api", () -> null);
        open.catalogue("uk", Caller.user(profile("fr-engineer")));
        assertThat(seen.get(0).headers().get("x-origin-verify")).isNull();
    }

    static final Policy POLICY = new Policy(java.nio.file.Path.of("../../ontology/policy.json"));

    private static Policy.Profile profile(String name) {
        return POLICY.profile(name);
    }
}
