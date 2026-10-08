// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The change log over H2 in PostgreSQL mode with the shipped V5 migration: append the rows of one
 * PLM request (a number, a flag, a text or a null as text), all or none, list oldest first, truncate; the list and the append admit every known profile, the truncate the officer alone. Same
 * context settings as {@link CoreServiceTest}, so the two classes share one application context.
 */
@SpringBootTest(properties = {
        "ATELIER_POLICY_FILE=../../../ontology/policy.json",
        "RELEASED_GRAPHS_DIR=../../../data",
        "spring.datasource.url=jdbc:h2:mem:coredemochange;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"})
@AutoConfigureMockMvc
@Sql(scripts = "classpath:db/migration/V1__schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class DemoChangeControllerTest {

    @Autowired
    MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private static Map<String, Object> change(String plm, String key, String before, String after) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plm", plm);
        body.put("table", "harness_connector");
        body.put("key", key);
        body.put("column", "pos_x");
        body.put("before", before);
        body.put("after", after);
        body.put("actor", "export-officer");
        body.put("purpose", "align with the FR mate");
        return body;
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, String profile) throws Exception {
        if (profile != null) {
            request.header("x-atelier-profile", profile);
        }
        return mvc.perform(request).andReturn();
    }

    private JsonNode expect(MockHttpServletRequestBuilder request, String profile, int status) throws Exception {
        MvcResult result = perform(request, profile);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        String content = result.getResponse().getContentAsString();
        return content.isEmpty() ? null : json.readTree(content);
    }

    private MockHttpServletRequestBuilder postChanges(List<Map<String, Object>> rows) throws Exception {
        return post("/core/changes").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(rows));
    }

    /** POSTs one row and returns the one stored row of the answer. */
    private JsonNode appendOne(Map<String, Object> body, String profile) throws Exception {
        JsonNode stored = expect(postChanges(List.of(body)), profile, 200);
        assertThat(stored.isArray()).isTrue();
        assertThat(stored).hasSize(1);
        return stored.get(0);
    }

    @Test
    void appendsListsOldestFirstAndTruncates() throws Exception {
        JsonNode first = appendOne(change("uk", "PL 6200-01", "18.7008", "19.0000"), "export-officer");
        assertThat(first.get("id").isIntegralNumber()).isTrue();
        assertThat(first.get("plm").asText()).isEqualTo("uk");
        assertThat(first.get("table").asText()).isEqualTo("harness_connector");
        assertThat(first.get("key").asText()).isEqualTo("PL 6200-01");
        assertThat(first.get("column").asText()).isEqualTo("pos_x");
        assertThat(first.get("before").asText()).isEqualTo("18.7008");
        assertThat(first.get("after").asText()).isEqualTo("19.0000");
        assertThat(first.get("actor").asText()).isEqualTo("export-officer");
        assertThat(first.get("purpose").asText()).isEqualTo("align with the FR mate");
        assertThat(OffsetDateTime.parse(first.get("at").asText())).isAfter(OffsetDateTime.now().minusMinutes(1));

        Map<String, Object> second = change("fr", "FR-ORN-NACI-001-J01", "475.000", "470.000");
        second.put("before", 475.0);
        second.put("after", null);
        JsonNode stored = appendOne(second, "export-officer");
        assertThat(stored.get("before").asText()).isEqualTo("475.0");
        assertThat(stored.get("after").isNull()).isTrue();

        JsonNode all = expect(get("/core/changes"), "export-officer", 200);
        assertThat(all).hasSize(2);
        List<String> keys = new ArrayList<>();
        all.forEach(row -> keys.add(row.get("key").asText()));
        assertThat(keys).containsExactly("PL 6200-01", "FR-ORN-NACI-001-J01");
        assertThat(all.get(0).get("id").asLong()).isLessThan(all.get(1).get("id").asLong());

        JsonNode deleted = expect(delete("/core/changes"), "export-officer", 200);
        assertThat(deleted.get("deleted").asInt()).isEqualTo(2);
        assertThat(expect(get("/core/changes"), "export-officer", 200)).isEmpty();
    }

    @Test
    void theListAndTheAppendAreForEveryKnownProfileTheTruncateForTheOfficerAlone() throws Exception {
        String known = "x-atelier-profile must be one of [de-engineer, es-engineer, export-officer, fr-engineer, programme-cleared, uk-engineer]";
        for (String profile : new String[] {"unknown", "nobody", null}) {
            JsonNode appended = expect(postChanges(List.of(change("uk", "PL ALL-01", "1", "2"))), profile, 403);
            assertThat(appended.get("reason").asText()).as(profile).isEqualTo("not-allowed");
            assertThat(appended.get("error").asText()).as(profile).isEqualTo(known);
            assertThat(expect(get("/core/changes"), profile, 403).get("error").asText()).as(profile).isEqualTo(known);
        }
        for (String profile : new String[] {"programme-cleared", "fr-engineer", "export-officer"}) {
            assertThat(expect(get("/core/changes"), profile, 200)).as(profile).isNotNull();
            assertThat(appendOne(change("uk", "PL ALL-01", "1", "2"), profile).get("id").isIntegralNumber()).as(profile).isTrue();
        }
        for (String profile : new String[] {"programme-cleared", "fr-engineer", "unknown", "nobody", null}) {
            JsonNode error = expect(delete("/core/changes"), profile, 403);
            assertThat(error.get("reason").asText()).as(profile).isEqualTo("not-allowed");
            assertThat(error.get("error").asText()).as(profile).isEqualTo("x-atelier-profile must be one of [export-officer]");
        }
        assertThat(expect(get("/core/changes"), "fr-engineer", 200)).as("the refusals deleted nothing").hasSize(3);
        assertThat(expect(delete("/core/changes"), "export-officer", 200).get("deleted").asInt()).isEqualTo(3);
    }

    @Test
    void appendsTheRowsOfOneRequestInOrderAsText() throws Exception {
        Map<String, Object> offer = change("uk", "1", null, null);
        offer.put("table", "supplier_part");
        offer.put("column", "preferred");
        offer.put("before", false);
        offer.put("after", true);
        Map<String, Object> lifecycle = change("uk", "HARN-6200-L", "Released", "Frozen");
        lifecycle.put("table", "component");
        lifecycle.put("column", "lifecycle");
        Map<String, Object> unit = change("uk", "PL 6200-03", null, "IN");
        unit.put("column", "pos_uom");

        JsonNode stored = expect(postChanges(List.of(offer, lifecycle, unit)), "uk-engineer", 200);

        assertThat(stored).hasSize(3);
        assertThat(stored.get(0).get("before").asText()).isEqualTo("false");
        assertThat(stored.get(0).get("after").asText()).isEqualTo("true");
        assertThat(stored.get(1).get("before").asText()).isEqualTo("Released");
        assertThat(stored.get(2).get("before").isNull()).isTrue();
        assertThat(stored.get(2).get("after").asText()).isEqualTo("IN");
        assertThat(stored.get(0).get("id").asLong()).isLessThan(stored.get(1).get("id").asLong());
        assertThat(stored.get(1).get("id").asLong()).isLessThan(stored.get(2).get("id").asLong());
        JsonNode all = expect(get("/core/changes"), "export-officer", 200);
        assertThat(all).isEqualTo(stored);
        expect(delete("/core/changes"), "export-officer", 200);
    }

    @Test
    void requiresAtLeastOneRowAndThePlmTableKeyAndColumnOfEach() throws Exception {
        Map<String, Object> body = change("uk", "PL ALL-01", "1", "2");
        body.remove("column");
        assertThat(expect(postChanges(List.of(change("uk", "PL ALL-01", "1", "2"), body)), "export-officer", 400).get("reason").asText())
                .isEqualTo("missing-field");
        assertThat(expect(postChanges(List.of()), "export-officer", 400).get("reason").asText()).isEqualTo("missing-field");
        assertThat(expect(get("/core/changes"), "export-officer", 200)).as("a refused request appends nothing").isEmpty();
    }

    @Test
    void refusesARowWhoseFieldsAreNotThoseOfAPlmCorrection() throws Exception {
        List<Map<String, Object>> refused = new ArrayList<>();
        for (String plm : new String[] {"../core", "fr/../x", "FR", "core", "fr\n"}) {
            refused.add(change(plm, "PL ALL-01", "1", "2"));
        }
        for (String table : new String[] {"../piece", "Piece", "piece; drop", "piece/x", "9piece"}) {
            Map<String, Object> row = change("uk", "PL ALL-01", "1", "2");
            row.put("table", table);
            refused.add(row);
        }
        for (String column : new String[] {"pos x", "POS_X", "pos_x\n"}) {
            Map<String, Object> row = change("uk", "PL ALL-01", "1", "2");
            row.put("column", column);
            refused.add(row);
        }
        for (String key : new String[] {"PL 6200-01\nINJECTED", "PL\u00006200", "../PL 6200-01", "PL\\6200", "K".repeat(65)}) {
            refused.add(change("uk", key, "1", "2"));
        }
        Map<String, Object> purpose = change("uk", "PL ALL-01", "1", "2");
        purpose.put("purpose", "p".repeat(501));
        refused.add(purpose);
        Map<String, Object> value = change("uk", "PL ALL-01", "v".repeat(1025), "2");
        refused.add(value);

        for (Map<String, Object> row : refused) {
            JsonNode error = expect(postChanges(List.of(change("uk", "PL ALL-01", "1", "2"), row)), "export-officer", 400);
            assertThat(error.get("reason").asText()).as(row.toString()).isEqualTo("bad-field");
        }
        assertThat(expect(get("/core/changes"), "export-officer", 200)).as("a refused request appends nothing").isEmpty();

        for (String plm : new String[] {"fr", "de", "uk", "es"}) {
            assertThat(appendOne(change(plm, "PL 6200-01", "1", "2"), "export-officer").get("plm").asText()).isEqualTo(plm);
        }
        assertThat(expect(delete("/core/changes"), "export-officer", 200).get("deleted").asInt()).isEqualTo(4);
    }
}
