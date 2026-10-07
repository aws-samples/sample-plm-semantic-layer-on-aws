// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core;

import atelier.plm.common.demo.EventPublisher;
import atelier.plm.core.graph.Graphs;
import atelier.plm.core.graph.ReleasedGraphs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The core's graph writes against a {@link StubNeptune} and a recording {@link EventPublisher}: the
 * link write is a Graph Store {@code POST} of both {@code matesWith} triples followed by one
 * {@code atelier.graph} / {@code interface.link.added} event carrying the count read back; the reset is
 * a Graph Store {@code PUT} of each released file; the link is for the integration role or the officer,
 * the reset for the officer alone, and bad feature IRIs are refused before the store or the bus is touched; a store failure is 502 with no event.
 */
@SpringBootTest(properties = {
        "ATELIER_POLICY_FILE=../../../ontology/policy.json",
        "RELEASED_GRAPHS_DIR=../../../data",
        "spring.datasource.url=jdbc:h2:mem:coregraphs;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"})
@AutoConfigureMockMvc
class GraphControllersTest {

    /** One recorded put: what the controller handed the publisher. */
    record Put(String source, String detailType, String detail) {
    }

    static final List<Put> PUTS = new ArrayList<>();
    static final StubNeptune NEPTUNE = start();
    static final ReleasedGraphs RELEASED = new ReleasedGraphs(Path.of("../../../data"));
    static final String FROM = "https://example.com/atelier/fr/plug/FR-ORN-EMPL-R-001-J01";
    static final String TO = "https://example.com/atelier/de/plug/HOLM-R-61010-X01";

    @TestConfiguration
    static class Events {
        @Bean
        @Primary
        EventPublisher recordingPublisher() {
            return (source, detailType, detail) -> {
                PUTS.add(new Put(source, detailType, detail));
                return "evt-" + PUTS.size();
            };
        }
    }

    @DynamicPropertySource
    static void neptune(DynamicPropertyRegistry registry) {
        registry.add("NEPTUNE_SPARQL_URL", NEPTUNE::sparqlUrl);
    }

    private static StubNeptune start() {
        try {
            return new StubNeptune();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterAll
    static void stop() {
        NEPTUNE.close();
    }

    @Autowired
    MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void clear() {
        PUTS.clear();
        NEPTUNE.clear();
    }

    private JsonNode expect(MockHttpServletRequestBuilder request, String profile, int status) throws Exception {
        if (profile != null) {
            request.header("x-atelier-profile", profile);
        }
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        String content = result.getResponse().getContentAsString();
        return content.isEmpty() ? null : json.readTree(content);
    }

    private MockHttpServletRequestBuilder link(String from, String to) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("to", to);
        return post("/core/links").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private static ReleasedGraphs.Released released(String name) {
        return RELEASED.all().stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void linkAppendsBothTriplesWithAPostReadsTheCountBackAndPutsOneEvent() throws Exception {
        NEPTUNE.load(Graphs.LINKS, released("links").turtle());
        long before = NEPTUNE.size(Graphs.LINKS);

        JsonNode body = expect(link(FROM, TO), "export-officer", 200);

        assertThat(body.get("triples").get("links").asLong()).isEqualTo(before + 2);
        assertThat(body.get("eventId").asText()).isEqualTo("evt-1");
        assertThat(body.has("eventSkipped")).isFalse();

        assertThat(NEPTUNE.seen).extracting(StubNeptune.Seen::method).containsExactly("POST", "POST");
        StubNeptune.Seen write = NEPTUNE.seen.get(0);
        assertThat(write.path()).isEqualTo("/sparql/gsp/");
        assertThat(write.graph()).isEqualTo(Graphs.LINKS);
        assertThat(write.contentType()).isEqualTo("text/turtle");
        assertThat(write.body()).isEqualTo("<" + FROM + "> <" + Graphs.MATES_WITH + "> <" + TO + "> .\n"
                + "<" + TO + "> <" + Graphs.MATES_WITH + "> <" + FROM + "> .\n");
        StubNeptune.Seen count = NEPTUNE.seen.get(1);
        assertThat(count.path()).isEqualTo("/sparql");
        assertThat(count.graph()).isEqualTo(Graphs.LINKS);
        assertThat(count.body()).startsWith("SELECT (COUNT(*) AS ?n)");
        Model links = NEPTUNE.graphs.get(Graphs.LINKS);
        assertThat(links.contains(ResourceFactory.createResource(FROM), ResourceFactory.createProperty(Graphs.MATES_WITH),
                ResourceFactory.createResource(TO))).isTrue();
        assertThat(links.contains(ResourceFactory.createResource(TO), ResourceFactory.createProperty(Graphs.MATES_WITH),
                ResourceFactory.createResource(FROM))).isTrue();
        assertThat(links.size()).as("the released links are still there").isEqualTo(before + 2);

        assertThat(PUTS).hasSize(1);
        Put put = PUTS.get(0);
        assertThat(put.source()).isEqualTo("atelier.graph");
        assertThat(put.detailType()).isEqualTo("interface.link.added");
        JsonNode detail = json.readTree(put.detail());
        List<String> fields = new ArrayList<>();
        detail.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("from", "to", "triples", "at");
        assertThat(detail.get("from").asText()).isEqualTo(FROM);
        assertThat(detail.get("to").asText()).isEqualTo(TO);
        assertThat(detail.get("triples").asLong()).isEqualTo(before + 2);
        assertThat(OffsetDateTime.parse(detail.get("at").asText())).isAfter(OffsetDateTime.now().minusMinutes(1));
    }

    @Test
    void resetPutsEachReleasedFileOverItsGraphAndReadsTheCountsBack() throws Exception {
        NEPTUNE.load(Graphs.LINKS, ("<" + FROM + "> <" + Graphs.MATES_WITH + "> <" + TO + "> .\n").getBytes(StandardCharsets.UTF_8));
        NEPTUNE.load(Graphs.FILE_INDEX, ("<https://example.com/atelier/fr/part/FR-ORN-CERC-001> <" + Graphs.ONT
                + "cadFile> \"cad/ornithopter/fr-head-hoop.stp\" .\n").getBytes(StandardCharsets.UTF_8));

        JsonNode body = expect(post("/core/graphs/reset"), "export-officer", 200);

        List<String> names = new ArrayList<>();
        body.get("graphs").fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactly("links", "fileindex", "labels");
        assertThat(body.get("graphs").get("links").asLong()).isEqualTo(released("links").triples());
        assertThat(body.get("graphs").get("fileindex").asLong()).isEqualTo(released("fileindex").triples());
        assertThat(body.get("graphs").get("labels").asLong()).isEqualTo(released("labels").triples());

        assertThat(NEPTUNE.seen).extracting(StubNeptune.Seen::method).containsExactly("PUT", "POST", "PUT", "POST", "PUT", "POST");
        assertThat(NEPTUNE.seen).extracting(StubNeptune.Seen::graph).containsExactly(Graphs.LINKS, Graphs.LINKS, Graphs.FILE_INDEX, Graphs.FILE_INDEX, Graphs.LABELS, Graphs.LABELS);
        assertThat(NEPTUNE.seen.get(0).contentType()).isEqualTo("text/turtle");
        assertThat(NEPTUNE.seen.get(0).body()).isEqualTo(new String(released("links").turtle(), StandardCharsets.UTF_8));
        assertThat(NEPTUNE.seen.get(2).body()).isEqualTo(new String(released("fileindex").turtle(), StandardCharsets.UTF_8));
        assertThat(NEPTUNE.seen.get(4).body()).isEqualTo(new String(released("labels").turtle(), StandardCharsets.UTF_8));
        assertThat(NEPTUNE.graphs.get(Graphs.LINKS).contains(ResourceFactory.createResource(FROM), ResourceFactory.createProperty(Graphs.MATES_WITH),
                ResourceFactory.createResource(TO))).as("the demo link is gone").isFalse();
        assertThat(NEPTUNE.graphs.get(Graphs.LINKS).contains(ResourceFactory.createResource(FROM), ResourceFactory.createProperty(Graphs.MATES_WITH)))
                .as("the released mate of the same plug is back").isTrue();
        assertThat(NEPTUNE.graphs.get(Graphs.FILE_INDEX).contains(ResourceFactory.createResource("https://example.com/atelier/fr/part/FR-ORN-CERC-001"),
                ResourceFactory.createProperty(Graphs.ONT + "cadFile"))).as("the head hoop lost its CAD file").isFalse();
        assertThat(PUTS).isEmpty();
    }

    @Test
    void rejectsAnythingButTwoDistinctFeatureIrisBeforeTouchingTheStoreOrTheBus() throws Exception {
        String[][] bad = {
                {"https://example.com/atelier/fr/part/FR-ORN-EMPL-R-001", TO},
                {FROM, "https://example.com/atelier/xx/plug/P-1"},
                {FROM, "https://example.com/atelier/de/bolt/B-1"},
                {"http://evil.example/atelier/fr/plug/P-1", TO},
                {FROM, "https://example.com/atelier/de/plug/a/b"},
                {FROM, "https://example.com/atelier/de/plug/P 1"},
                {FROM, "https://example.com/atelier/de/plug/P>1"},
                {FROM, null},
                {"", TO},
        };
        for (String[] pair : bad) {
            assertThat(expect(link(pair[0], pair[1]), "export-officer", 400).get("reason").asText()).as(pair[0] + " -> " + pair[1])
                    .isEqualTo("bad-feature-iri");
        }
        assertThat(expect(link(FROM, FROM), "export-officer", 400).get("reason").asText()).isEqualTo("same-feature");
        assertThat(NEPTUNE.seen).isEmpty();
        assertThat(PUTS).isEmpty();
    }

    @Test
    void theLinkIsForTheIntegrationRoleOrTheOfficerAndTheResetForTheOfficerAlone() throws Exception {
        for (String profile : new String[] {"fr-engineer", "unknown", "nobody", null}) {
            JsonNode error = expect(link(FROM, TO), profile, 403);
            assertThat(error.get("reason").asText()).as(profile).isEqualTo("not-allowed");
            assertThat(error.get("error").asText()).as(profile).isEqualTo("x-atelier-profile must be one of [programme-cleared, export-officer]");
        }
        for (String profile : new String[] {"programme-cleared", "fr-engineer", "unknown", "nobody", null}) {
            JsonNode error = expect(post("/core/graphs/reset"), profile, 403);
            assertThat(error.get("reason").asText()).as(profile).isEqualTo("not-allowed");
            assertThat(error.get("error").asText()).as(profile).isEqualTo("x-atelier-profile must be one of [export-officer]");
        }
        assertThat(NEPTUNE.seen).isEmpty();
        assertThat(PUTS).isEmpty();

        NEPTUNE.load(Graphs.LINKS, released("links").turtle());
        JsonNode body = expect(link(FROM, TO), "programme-cleared", 200);
        assertThat(body.get("eventId").asText()).as("the integration role publishes a link").isEqualTo("evt-1");
        assertThat(PUTS).hasSize(1);
    }

    @Test
    void aStoreFailureIs502AndNoEventIsPut() throws Exception {
        NEPTUNE.failWith = 500;
        JsonNode error = expect(link(FROM, TO), "export-officer", 502);
        assertThat(error.get("reason").asText()).isEqualTo("link-store");
        assertThat(error.get("error").asText()).doesNotContain("127.0.0.1");
        assertThat(PUTS).isEmpty();
    }

    @Test
    void healthIsPublicAndReportsTheStoreAndTheBundledCounts() throws Exception {
        JsonNode health = expect(get("/core/graphs/health"), null, 200);
        assertThat(health.get("neptune").asBoolean()).isTrue();
        assertThat(health.get("releasedGraphs").get("links").asLong()).isEqualTo(released("links").triples()).isGreaterThan(300);
        assertThat(health.get("releasedGraphs").get("fileindex").asLong()).isEqualTo(released("fileindex").triples()).isGreaterThan(10);
        assertThat(NEPTUNE.seen).as("configuration only, no store call").isEmpty();
    }
}
