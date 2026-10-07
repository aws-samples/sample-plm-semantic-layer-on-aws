// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core;

import atelier.plm.core.domain.DemoChangeStore;
import atelier.plm.core.dto.DemoChangeDto;
import atelier.plm.core.graph.Graphs;
import atelier.plm.core.graph.ReleasedGraphs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The demo reset against a {@link StubPlmApi} standing in for the API Gateway and the PLM services
 * and a {@link StubNeptune}: the officer's {@code POST /core/demo/reset} undoes the logged changes
 * newest first through {@code POST /{plm}/demo/update} (origin secret, officer profile, core actor,
 * {@code value} the logged {@code before} as text or JSON null, {@code purpose} naming the row, every
 * row of a batch on its own), then PUTs each released
 * file over its graph, then clears the log; other profiles are refused before anything is touched;
 * a PLM refusal is 502 with the store and the log untouched; a store failure after the undos is 502
 * with the log kept.
 */
@SpringBootTest(properties = {
        "ATELIER_POLICY_FILE=../../../ontology/policy.json",
        "RELEASED_GRAPHS_DIR=../../../data",
        "ORIGIN_SECRET=test-origin-secret",
        "spring.datasource.url=jdbc:h2:mem:coredemoreset;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"})
@AutoConfigureMockMvc
@Sql(scripts = "classpath:db/migration/V1__schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class DemoResetControllerTest {

    static final StubPlmApi PLM = startPlm();
    static final StubNeptune NEPTUNE = startNeptune();
    static final ReleasedGraphs RELEASED = new ReleasedGraphs(Path.of("../../../data"));

    @DynamicPropertySource
    static void endpoints(DynamicPropertyRegistry registry) {
        registry.add("PLM_API_BASE", PLM::base);
        registry.add("NEPTUNE_SPARQL_URL", NEPTUNE::sparqlUrl);
    }

    private static StubPlmApi startPlm() {
        try {
            return new StubPlmApi();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static StubNeptune startNeptune() {
        try {
            return new StubNeptune();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterAll
    static void stop() {
        PLM.close();
        NEPTUNE.close();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    DemoChangeStore changes;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void clear() {
        PLM.clear();
        NEPTUNE.clear();
        changes.clear();
    }

    private JsonNode expect(MockHttpServletRequestBuilder request, String profile, int status) throws Exception {
        if (profile != null) {
            request.header("x-atelier-profile", profile);
        }
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static ReleasedGraphs.Released released(String name) {
        return RELEASED.all().stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
    }

    /** Three corrections as the loader logs them: a British plug moved twice around its French mate, a French coupling once. */
    private List<DemoChangeDto> logThreeChanges() {
        List<DemoChangeDto> logged = new ArrayList<>();
        logged.add(changes.append(new DemoChangeDto(null, "uk", "harness_connector", "PL 6200-01", "pos_x", "18.7008", "19.0000",
                "export-officer", "align with the FR mate", null)));
        logged.add(changes.append(new DemoChangeDto(null, "fr", "raccord_hydraulique", "FR-ORN-GHYD-001-H01", "pos_x_mm", "2300.000", "2295.000",
                "fr-engineer", "align with the UK mate", null)));
        logged.add(changes.append(new DemoChangeDto(null, "uk", "harness_connector", "PL 6200-01", "pos_x", "19.0000", "20.0000",
                "export-officer", "second thought", null)));
        return logged;
    }

    @Test
    void undoesTheChangesNewestFirstThroughThePlmsThenRestoresTheGraphsThenClearsTheLog() throws Exception {
        List<DemoChangeDto> logged = logThreeChanges();
        NEPTUNE.load(Graphs.LINKS, ("<https://example.com/atelier/fr/plug/FR-ORN-EMPL-R-001-J01> <" + Graphs.MATES_WITH
                + "> <https://example.com/atelier/de/plug/HOLM-R-61010-X01> .\n").getBytes(StandardCharsets.UTF_8));

        JsonNode body = expect(post("/core/demo/reset"), "export-officer", 200);

        List<String> fields = new ArrayList<>();
        body.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("undone", "graphs");
        assertThat(body.get("undone").asInt()).isEqualTo(3);
        List<String> graphs = new ArrayList<>();
        body.get("graphs").fieldNames().forEachRemaining(graphs::add);
        assertThat(graphs).containsExactly("links", "fileindex", "labels");
        assertThat(body.get("graphs").get("links").asLong()).isEqualTo(released("links").triples());
        assertThat(body.get("graphs").get("fileindex").asLong()).isEqualTo(released("fileindex").triples());
        assertThat(body.get("graphs").get("labels").asLong()).isEqualTo(released("labels").triples());

        assertThat(PLM.seen).hasSize(3);
        assertThat(PLM.seen).extracting(StubPlmApi.Seen::method).containsOnly("POST");
        assertThat(PLM.seen).extracting(StubPlmApi.Seen::path).containsExactly("/api/uk/demo/update", "/api/fr/demo/update", "/api/uk/demo/update");
        for (StubPlmApi.Seen call : PLM.seen) {
            assertThat(call.headers()).containsEntry("x-origin-verify", "test-origin-secret");
            assertThat(call.headers()).containsEntry("x-atelier-profile", "export-officer");
            assertThat(call.headers()).containsEntry("x-atelier-actor", "atelier-core");
            assertThat(call.headers()).containsEntry("content-type", "application/json");
        }
        List<JsonNode> updates = new ArrayList<>();
        for (StubPlmApi.Seen call : PLM.seen) {
            updates.add(json.readTree(call.body()));
        }
        List<String> updateFields = new ArrayList<>();
        updates.get(0).fieldNames().forEachRemaining(updateFields::add);
        assertThat(updateFields).containsExactly("table", "key", "column", "value", "purpose");
        assertThat(updates).extracting(u -> u.get("table").asText()).containsExactly("harness_connector", "raccord_hydraulique", "harness_connector");
        assertThat(updates).extracting(u -> u.get("key").asText()).containsExactly("PL 6200-01", "FR-ORN-GHYD-001-H01", "PL 6200-01");
        assertThat(updates).extracting(u -> u.get("column").asText()).containsExactly("pos_x", "pos_x_mm", "pos_x");
        assertThat(updates).extracting(u -> u.get("value").asText()).as("each value is the logged before, newest change first")
                .containsExactly("19.0000", "2300.000", "18.7008");
        assertThat(updates).extracting(u -> u.get("purpose").asText()).containsExactly(
                "reset: " + logged.get(2).id(), "reset: " + logged.get(1).id(), "reset: " + logged.get(0).id());

        assertThat(NEPTUNE.seen).extracting(StubNeptune.Seen::method).containsExactly("PUT", "POST", "PUT", "POST", "PUT", "POST");
        assertThat(NEPTUNE.seen).extracting(StubNeptune.Seen::graph).containsExactly(Graphs.LINKS, Graphs.LINKS, Graphs.FILE_INDEX, Graphs.FILE_INDEX, Graphs.LABELS, Graphs.LABELS);
        assertThat(NEPTUNE.seen.get(0).body()).isEqualTo(new String(released("links").turtle(), StandardCharsets.UTF_8));
        assertThat(NEPTUNE.seen.get(2).body()).isEqualTo(new String(released("fileindex").turtle(), StandardCharsets.UTF_8));
        assertThat(NEPTUNE.seen.get(4).body()).isEqualTo(new String(released("labels").turtle(), StandardCharsets.UTF_8));
        assertThat(NEPTUNE.size(Graphs.LINKS)).as("the demo link is gone").isEqualTo(released("links").triples());

        assertThat(changes.all()).as("the log is cleared last").isEmpty();
    }

    @Test
    void replaysTextValuesNullBeforesAndEveryRowOfABatchNewestFirst() throws Exception {
        List<DemoChangeDto> batch = changes.append(List.of(
                new DemoChangeDto(null, "uk", "component", "HARN-6200-L", "lifecycle", "Released", "Frozen", "uk-engineer", "freeze", null),
                new DemoChangeDto(null, "uk", "harness_connector", "PL 6200-03", "pos_uom", null, "IN", "uk-engineer", "freeze", null),
                new DemoChangeDto(null, "uk", "supplier_part", "1", "preferred", "false", "true", "uk-engineer", "freeze", null)));

        JsonNode body = expect(post("/core/demo/reset"), "export-officer", 200);

        assertThat(body.get("undone").asInt()).isEqualTo(3);
        List<JsonNode> updates = new ArrayList<>();
        for (StubPlmApi.Seen call : PLM.seen) {
            updates.add(json.readTree(call.body()));
        }
        assertThat(updates).extracting(u -> u.get("column").asText()).containsExactly("preferred", "pos_uom", "lifecycle");
        assertThat(updates.get(0).get("value").isTextual()).isTrue();
        assertThat(updates.get(0).get("value").asText()).isEqualTo("false");
        assertThat(updates.get(1).has("value")).as("a NULL before is sent as JSON null").isTrue();
        assertThat(updates.get(1).get("value").isNull()).isTrue();
        assertThat(updates.get(2).get("value").asText()).isEqualTo("Released");
        assertThat(updates).extracting(u -> u.get("purpose").asText()).containsExactly(
                "reset: " + batch.get(2).id(), "reset: " + batch.get(1).id(), "reset: " + batch.get(0).id());
        assertThat(changes.all()).isEmpty();
    }

    @Test
    void anEmptyLogStillRestoresTheGraphs() throws Exception {
        JsonNode body = expect(post("/core/demo/reset"), "export-officer", 200);

        assertThat(body.get("undone").asInt()).isZero();
        assertThat(body.get("graphs").get("links").asLong()).isEqualTo(released("links").triples());
        assertThat(PLM.seen).isEmpty();
        assertThat(NEPTUNE.seen).extracting(StubNeptune.Seen::method).containsExactly("PUT", "POST", "PUT", "POST", "PUT", "POST");
    }

    @Test
    void isForTheOfficerAloneAndTouchesNothingForAnyOtherProfile() throws Exception {
        logThreeChanges();
        for (String profile : new String[] {"programme-cleared", "fr-engineer", "uk-engineer", "unknown", "nobody", null}) {
            JsonNode error = expect(post("/core/demo/reset"), profile, 403);
            assertThat(error.get("reason").asText()).as(profile).isEqualTo("not-allowed");
            assertThat(error.get("error").asText()).as(profile).isEqualTo("x-atelier-profile must be one of [export-officer]");
        }
        assertThat(PLM.seen).isEmpty();
        assertThat(NEPTUNE.seen).isEmpty();
        assertThat(changes.all()).hasSize(3);
    }

    @Test
    void aPlmThatRefusesAnUndoStopsTheResetWith502AndLeavesTheStoreAndTheLog() throws Exception {
        List<DemoChangeDto> logged = logThreeChanges();
        PLM.failWith = 404;
        PLM.failAt = 2;
        PLM.failBody = "{\"error\":\"no row of raccord_hydraulique has ref_raccord = FR-ORN-GHYD-001-H01\",\"reason\":\"row-not-found\"}";

        JsonNode error = expect(post("/core/demo/reset"), "export-officer", 502);

        assertThat(error.get("reason").asText()).isEqualTo("plm-update");
        assertThat(error.get("error").asText()).isEqualTo("the FR PLM did not undo change " + logged.get(1).id()
                + " (raccord_hydraulique/FR-ORN-GHYD-001-H01/pos_x_mm): HTTP 404 row-not-found");
        assertThat(error.get("error").asText()).doesNotContain("127.0.0.1");
        assertThat(PLM.seen).as("the reset stops at the refusal").hasSize(2);
        assertThat(NEPTUNE.seen).as("the graphs are not touched").isEmpty();
        assertThat(changes.all()).as("the log stays for the next reset").hasSize(3);
    }

    @Test
    void aStoreFailureAfterTheUndosIs502AndKeepsTheLog() throws Exception {
        logThreeChanges();
        NEPTUNE.failWith = 500;

        JsonNode error = expect(post("/core/demo/reset"), "export-officer", 502);

        assertThat(error.get("reason").asText()).isEqualTo("link-store");
        assertThat(PLM.seen).as("every undo was asked before the store").hasSize(3);
        assertThat(NEPTUNE.seen).hasSize(1);
        assertThat(changes.all()).as("the log is cleared only after the graphs").hasSize(3);
    }
}
