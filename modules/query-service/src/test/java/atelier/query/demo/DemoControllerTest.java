// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import atelier.query.Atelier;
import atelier.query.api.ApiErrors;
import atelier.query.federation.Endpoints;
import atelier.query.mcp.PlmApi;
import atelier.query.policy.Policy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * The change list over MockMvc against {@link StubGateway}: the profile gate (every profile but
 * unknown) before any downstream call, the core rows and the live graphs diffed against small
 * released files, a core that does not answer, and the absence of any writing route under
 * {@code /query/demo}: the demo's writes are the PLM and core services' own routes, so a write
 * sent here is 404 or 405 and reaches neither the gateway nor the link store.
 */
class DemoControllerTest {
    static final Policy POLICY = new Policy(Path.of("../../ontology/policy.json"));
    static final ObjectMapper JSON = new ObjectMapper();
    static final String PREFIXES = "@prefix atelier: <https://example.com/atelier/ontology#> .\n";
    static final String LINKS = PREFIXES + """
            <https://example.com/atelier/interface/ornithopter/IF-30> a atelier:Interface ;
                atelier:ofProduct <https://example.com/atelier/product/ornithopter> ;
                atelier:betweenPart <https://example.com/atelier/fr/part/FR-ORN-PCMD-001> , <https://example.com/atelier/es/part/SERV-6120> ;
                atelier:declaresFeature <https://example.com/atelier/fr/plug/FR-ORN-PCMD-001-J01> .
            <https://example.com/atelier/fr/plug/FR-ORN-PCMD-001-J01> atelier:matesWith <https://example.com/atelier/es/plug/SERV-6120-C01> .
            """;
    static final String FILE_INDEX = PREFIXES + """
            <https://example.com/atelier/es/part/SERV-6120> atelier:cadFile "cad/ornithopter/es-tail-servo.stp" .
            <https://example.com/atelier/uk/part/ROOT-6180-L> atelier:builtBy "Forja del Tajo, Toledo" .
            """;
    static final String ROWS = """
            [{"id":1,"plm":"de","table":"stecker","key":"WURZ-R-61080-X01","column":"pos_y_mm","before":644.3,"after":640.0,"actor":"user","purpose":"demo","at":"2026-09-30T10:00:00Z"},
             {"id":2,"plm":"fr","table":"connecteur","key":"FR-ORN-PCMD-001-J03","column":"pos_y_mm","before":95.0,"after":96.0,"actor":"agent","purpose":"demo","at":"2026-09-30T10:01:00Z"}]""";
    static final String ALLOWED = "[de-engineer, es-engineer, export-officer, fr-engineer, programme-cleared, uk-engineer]";
    /** The writes the owning services take directly; nothing maps them here. */
    static final List<String> OWNING_SERVICE_ROUTES = List.of(
            "/query/demo/change", "/query/demo/publish-link", "/query/demo/publish-cad", "/query/demo/reset");

    @TempDir
    Path released;
    Dataset live;
    StubGateway stub;
    MockMvc mvc;

    @BeforeEach
    void start() throws IOException {
        Files.writeString(released.resolve("links.ttl"), LINKS);
        Files.writeString(released.resolve("fileindex.ttl"), FILE_INDEX);
        live = DatasetFactory.create();
        live.addNamedModel(Atelier.LINKS_GRAPH, turtle(LINKS));
        live.addNamedModel(Atelier.FILE_INDEX_GRAPH, turtle(FILE_INDEX));
        stub = new StubGateway(live);
        PlmApi api = new PlmApi(stub.base() + "/api", () -> "s3cret");
        DemoService service = new DemoService(api, new ReleasedGraphs(released), new LiveGraphs(new Endpoints("http://fr/sparql", "http://de/sparql", "http://uk/sparql",
                "http://es/sparql", "http://core/sparql", stub.base() + "/query", Duration.ofSeconds(5), Duration.ofSeconds(30))), JSON);
        mvc = MockMvcBuilders.standaloneSetup(new DemoController(service, POLICY)).setControllerAdvice(new ApiErrors()).build();
    }

    /** Whatever the request and its outcome, the link store received no Graph Store request and both graphs are as released. */
    @AfterEach
    void stop() {
        try {
            assertThat(stub.graphStore()).as("no PUT or POST on the link store from the query service").isEmpty();
            assertThat(live.getNamedModel(Atelier.FILE_INDEX_GRAPH).size()).isEqualTo(2);
        } finally {
            stub.close();
        }
    }

    @Test
    void changesAdmitsEveryKnownProfileAndRefusesTheOthersBeforeAnyDownstreamCall() throws Exception {
        for (String profile : new String[] {"unknown", "nobody", null}) {
            MockHttpServletRequestBuilder request = get("/query/demo/changes");
            if (profile != null) request.header("x-atelier-profile", profile);
            MockHttpServletResponse response = perform(request);
            assertThat(response.getStatus()).as(String.valueOf(profile)).isEqualTo(403);
            assertThat(response.getContentAsString()).as(String.valueOf(profile))
                    .isEqualTo("{\"error\":\"x-atelier-profile must be one of " + ALLOWED + "\"}");
        }
        assertThat(stub.seen).as("a refusal sends nothing downstream").isEmpty();

        stub.route("GET /api/core/changes", 200, "[]");
        for (String profile : List.of("uk-engineer", "programme-cleared", "export-officer")) {
            assertThat(perform(get("/query/demo/changes").header("x-atelier-profile", profile)).getStatus()).as(profile).isEqualTo(200);
        }
        assertThat(stub.gatewayCalls()).extracting(call -> call.headers().get("x-atelier-profile"))
                .as("the caller's own profile is forwarded, so the core service checks the same table")
                .containsExactly("uk-engineer", "programme-cleared", "export-officer");
        assertThat(stub.gatewayCalls()).extracting(StubGateway.Seen::method, StubGateway.Seen::path)
                .containsOnly(tuple("GET", "/api/core/changes"));
    }

    @Test
    void changesListsTheCoreRowsAndDiffsEachLiveGraphAgainstItsReleasedFile() throws Exception {
        stub.route("GET /api/core/changes", 200, ROWS);
        Model links = live.getNamedModel(Atelier.LINKS_GRAPH);
        links.add(turtle(PREFIXES + """
                <https://example.com/atelier/es/plug/SERV-6120-C03> atelier:matesWith <https://example.com/atelier/fr/plug/FR-ORN-PCMD-001-J03> .
                <https://example.com/atelier/fr/plug/FR-ORN-PCMD-001-J03> atelier:matesWith <https://example.com/atelier/es/plug/SERV-6120-C03> .
                """));
        links.remove(turtle(PREFIXES + "<https://example.com/atelier/fr/plug/FR-ORN-PCMD-001-J01> atelier:matesWith <https://example.com/atelier/es/plug/SERV-6120-C01> ."));

        MockHttpServletResponse response = officer(get("/query/demo/changes"));

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode answer = JSON.readTree(response.getContentAsString());
        assertThat(answer.path("values")).isEqualTo(JSON.readTree(ROWS));
        assertThat(answer.path("graph")).isEqualTo(JSON.readTree("""
                {"added":[{"s":"FR-ORN-PCMD-001-J03","p":"atelier:matesWith","o":"SERV-6120-C03",
                           "sIri":"https://example.com/atelier/fr/plug/FR-ORN-PCMD-001-J03","oIri":"https://example.com/atelier/es/plug/SERV-6120-C03","plm":"fr"},
                          {"s":"SERV-6120-C03","p":"atelier:matesWith","o":"FR-ORN-PCMD-001-J03",
                           "sIri":"https://example.com/atelier/es/plug/SERV-6120-C03","oIri":"https://example.com/atelier/fr/plug/FR-ORN-PCMD-001-J03","plm":"es"}],
                 "removed":[{"s":"FR-ORN-PCMD-001-J01","p":"atelier:matesWith","o":"SERV-6120-C01",
                             "sIri":"https://example.com/atelier/fr/plug/FR-ORN-PCMD-001-J01","oIri":"https://example.com/atelier/es/plug/SERV-6120-C01","plm":"fr"}],
                 "triples":{"links":7,"fileindex":2}}"""));
        assertThat(stub.seen).filteredOn(seen -> seen.path().equals("/query")).as("one SELECT per graph, nothing else on the link store").hasSize(2);
    }

    @Test
    void aCoreThatDoesNotListTheChangesIs502NamingIt() throws Exception {
        stub.route("GET /api/core/changes", 500, "{\"error\":\"boom\"}");

        MockHttpServletResponse response = officer(get("/query/demo/changes"));

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"the core service did not list the changes: HTTP 500\"}");
        assertThat(stub.seen).as("the link store is not read when the core does not answer").allMatch(seen -> seen.path().startsWith("/api/"));
    }

    /** No demo route writes: none maps a writing verb, and a write sent here is refused before anything goes downstream. */
    @Test
    void noDemoRouteWrites() throws Exception {
        for (Class<?> controller : List.of(DemoController.class, DemoHealthController.class)) {
            for (Method method : controller.getDeclaredMethods()) {
                String name = controller.getSimpleName() + "." + method.getName();
                assertThat(method.isAnnotationPresent(PostMapping.class) || method.isAnnotationPresent(PutMapping.class)
                        || method.isAnnotationPresent(DeleteMapping.class) || method.isAnnotationPresent(PatchMapping.class))
                        .as(name + " maps a writing verb").isFalse();
                RequestMapping mapping = method.getAnnotation(RequestMapping.class);
                if (mapping != null) assertThat(mapping.method()).as(name).containsOnly(RequestMethod.GET);
            }
        }

        String change = "{\"plm\":\"DE\",\"table\":\"stecker\",\"key\":\"WURZ-R-61080-X01\",\"column\":\"pos_y_mm\",\"value\":640.0,\"purpose\":\"demo\"}";
        for (String route : OWNING_SERVICE_ROUTES) {
            MockHttpServletResponse response = officer(post(route).contentType(MediaType.APPLICATION_JSON).content(change));
            assertThat(response.getStatus()).as(route).isIn(404, 405);
        }
        for (HttpMethod verb : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)) {
            MockHttpServletResponse response = officer(request(verb, "/query/demo/changes").contentType(MediaType.APPLICATION_JSON).content("{}"));
            assertThat(response.getStatus()).as(verb.name() + " /query/demo/changes").isEqualTo(405);
        }
        assertThat(stub.seen).as("nothing reaches the gateway or the link store").isEmpty();
    }

    private MockHttpServletResponse officer(MockHttpServletRequestBuilder request) throws Exception {
        return perform(request.header("x-atelier-profile", "export-officer"));
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    static Model turtle(String body) {
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, new StringReader(body), null, Lang.TURTLE);
        return model;
    }
}
