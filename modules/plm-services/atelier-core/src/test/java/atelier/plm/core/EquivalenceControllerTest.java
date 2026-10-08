// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core;

import atelier.plm.common.demo.EventPublisher;
import atelier.plm.core.graph.Graphs;
import atelier.plm.core.graph.ReleasedGraphs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A confirmed equivalence against a {@link StubNeptune} and a recording {@link EventPublisher}: the rover's four socket
 * head cap screws become twelve {@code owl:sameAs} triples in the links graph, appended with one Graph Store
 * {@code POST}, and one {@code atelier.graph} / {@code equivalence.confirmed} event; the graph reset puts the released
 * links back, which hold none. The integration role and the officer confirm; the engineers, the unknown profile and a
 * request without a profile are refused, as are bodies that do not name two to sixteen distinct part IRIs, before the
 * store or the bus is touched.
 */
@SpringBootTest(properties = {
        "ATELIER_POLICY_FILE=../../../ontology/policy.json",
        "RELEASED_GRAPHS_DIR=../../../data",
        "spring.datasource.url=jdbc:h2:mem:coreequivalences;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"})
@AutoConfigureMockMvc
class EquivalenceControllerTest {

    record Put(String source, String detailType, String detail) {
    }

    static final List<Put> PUTS = new ArrayList<>();
    static final StubNeptune NEPTUNE = start();
    static final ReleasedGraphs RELEASED = new ReleasedGraphs(Path.of("../../../data"));
    static final List<String> SCREWS = List.of(
            "https://example.com/atelier/de/part/D-38014", "https://example.com/atelier/fr/part/FR3811",
            "https://example.com/atelier/uk/part/UK-3823", "https://example.com/atelier/es/part/ES-3826");
    static final Property SAME_AS = ResourceFactory.createProperty(Graphs.SAME_AS);

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
        NEPTUNE.load(Graphs.LINKS, links().turtle());
    }

    private static ReleasedGraphs.Released links() {
        return RELEASED.all().stream().filter(r -> r.name().equals("links")).findFirst().orElseThrow();
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

    private MockHttpServletRequestBuilder confirm(List<String> parts) throws Exception {
        return post("/core/equivalences").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("parts", parts)));
    }

    private long sameAs() {
        return NEPTUNE.graphs.get(Graphs.LINKS).listStatements(null, SAME_AS, (org.apache.jena.rdf.model.RDFNode) null).toList().size();
    }

    @Test
    void confirmingAppendsEveryOrderedPairAsSameAsAndPutsOneEventThenTheResetRemovesThem() throws Exception {
        long before = NEPTUNE.size(Graphs.LINKS);
        assertThat(sameAs()).as("the released links confirm nothing").isZero();

        JsonNode body = expect(confirm(SCREWS), "programme-cleared", 200);

        assertThat(body.get("parts")).hasSize(4);
        assertThat(body.get("triples").get("links").asLong()).isEqualTo(before + 12);
        assertThat(body.get("eventId").asText()).isEqualTo("evt-1");
        assertThat(NEPTUNE.seen).extracting(StubNeptune.Seen::method).containsExactly("POST", "POST");
        assertThat(NEPTUNE.seen.get(0).graph()).isEqualTo(Graphs.LINKS);
        Model links = NEPTUNE.graphs.get(Graphs.LINKS);
        for (String a : SCREWS) {
            for (String b : SCREWS) {
                assertThat(links.contains(ResourceFactory.createResource(a), SAME_AS, ResourceFactory.createResource(b)))
                        .as(a + " sameAs " + b).isEqualTo(!a.equals(b));
            }
        }

        assertThat(PUTS).hasSize(1);
        assertThat(PUTS.get(0).source()).isEqualTo("atelier.graph");
        assertThat(PUTS.get(0).detailType()).isEqualTo("equivalence.confirmed");
        JsonNode detail = json.readTree(PUTS.get(0).detail());
        List<String> fields = new ArrayList<>();
        detail.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("parts", "triples", "at");
        assertThat(detail.get("triples").asLong()).isEqualTo(before + 12);

        expect(post("/core/graphs/reset"), "export-officer", 200);
        assertThat(sameAs()).as("the reset puts the released links back").isZero();
        assertThat(NEPTUNE.size(Graphs.LINKS)).isEqualTo(before);
    }

    @Test
    void theOfficerConfirmsToo() throws Exception {
        expect(confirm(SCREWS.subList(0, 2)), "export-officer", 200);
        assertThat(sameAs()).isEqualTo(2);
    }

    @Test
    void theEngineersTheUnknownProfileAndNoProfileAreRefusedBeforeTheStoreIsTouched() throws Exception {
        for (String profile : new String[] {"fr-engineer", "de-engineer", "uk-engineer", "es-engineer", "unknown", null}) {
            JsonNode body = expect(confirm(SCREWS), profile, 403);
            assertThat(body.get("reason").asText()).as(profile).isEqualTo("not-allowed");
        }
        assertThat(NEPTUNE.seen).isEmpty();
        assertThat(PUTS).isEmpty();
    }

    @Test
    void aBodyThatDoesNotNameTwoDistinctPartIrisIsRefused() throws Exception {
        String feature = "https://example.com/atelier/fr/plug/FR-ORN-EMPL-R-001-J01";
        String injected = "https://example.com/atelier/fr/part/X> <" + Graphs.SAME_AS + "> <https://example.com/atelier/de/part/Y";
        Map<List<String>, String> refused = Map.of(
                List.of(SCREWS.get(0)), "bad-parts",
                List.of(SCREWS.get(0), feature), "bad-part-iri",
                List.of(SCREWS.get(0), injected), "bad-part-iri",
                List.of(SCREWS.get(0), SCREWS.get(0)), "same-part");
        for (Map.Entry<List<String>, String> entry : refused.entrySet()) {
            JsonNode body = expect(confirm(entry.getKey()), "programme-cleared", 400);
            assertThat(body.get("reason").asText()).as(entry.getKey().toString()).isEqualTo(entry.getValue());
        }
        assertThat(NEPTUNE.seen).isEmpty();
        assertThat(PUTS).isEmpty();
    }
}
