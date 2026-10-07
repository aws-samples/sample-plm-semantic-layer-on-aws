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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A core service started without {@code NEPTUNE_SPARQL_URL}: the health endpoint says so and both
 * graph writes answer 503 without trying. Same context settings as {@link CoreServiceTest}, so the
 * classes share one application context.
 */
@SpringBootTest(properties = {
        "ATELIER_POLICY_FILE=../../../ontology/policy.json",
        "RELEASED_GRAPHS_DIR=../../../data",
        "spring.datasource.url=jdbc:h2:mem:core;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"})
@AutoConfigureMockMvc
class GraphStoreUnconfiguredTest {

    @Autowired
    MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode officer(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(request.header("x-atelier-profile", "export-officer")).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void healthSaysNoStoreAndStillCountsTheBundledGraphs() throws Exception {
        JsonNode health = json.readTree(mvc.perform(get("/core/graphs/health")).andReturn().getResponse().getContentAsString());
        assertThat(health.get("neptune").asBoolean()).isFalse();
        assertThat(health.get("releasedGraphs").get("links").asLong()).isGreaterThan(300);
        assertThat(health.get("releasedGraphs").get("fileindex").asLong()).isGreaterThan(10);
    }

    @Test
    void bothWritesAnswer503() throws Exception {
        String link = "{\"from\":\"https://example.com/atelier/fr/plug/FR-ORN-EMPL-R-001-J01\","
                + "\"to\":\"https://example.com/atelier/de/plug/HOLM-R-61010-X01\"}";
        assertThat(officer(post("/core/links").contentType(MediaType.APPLICATION_JSON).content(link), 503).get("reason").asText())
                .isEqualTo("link-store-not-configured");
        assertThat(officer(post("/core/graphs/reset"), 503).get("reason").asText()).isEqualTo("link-store-not-configured");
    }
}
