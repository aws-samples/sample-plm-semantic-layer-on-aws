// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.sql;

import atelier.plm.common.policy.PartTagStore;
import atelier.plm.common.policy.PartTagStoreConfiguration;
import atelier.plm.common.policy.PolicyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The production {@link PartTagStoreConfiguration} without any CORE_DB_* setting: the tag store is
 * unconfigured, so a statement over a part or feature table reads no row and the answer says why,
 * while a table outside the export-control policy is still read. Same entities and fixture as
 * {@link SqlControllerTest}.
 */
@SpringBootTest(properties = {
        "plm.code=uk",
        "ATELIER_POLICY_FILE=src/test/resources/policy.json",
        "spring.datasource.url=jdbc:h2:mem:sqldeny;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc
@Sql(scripts = "/tables/uk-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class SqlDenyByDefaultTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = SqlControllerTest.class)
    @Import({SqlController.class, PolicyConfiguration.class, PartTagStoreConfiguration.class})
    static class Config {
    }

    @Autowired
    MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode ok(String sql) throws Exception {
        MvcResult result = mvc.perform(post("/uk/sql").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("sql", sql, "purpose", "test")))
                .header("x-atelier-profile", "export-officer")).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void partAndFeatureTablesYieldNoRowAndNameTheCause() throws Exception {
        JsonNode parts = ok("SELECT comp_id FROM component");
        assertThat(parts.get("rows")).isEmpty();
        assertThat(parts.get("sqlExecuted").asText()).isEqualTo("SELECT comp_id FROM (SELECT * FROM component WHERE 1 = 0) AS component LIMIT 200");
        assertThat(parts.get("policy").get("error").asText()).isEqualTo(PartTagStore.NOT_CONFIGURED);

        JsonNode features = ok("SELECT count(*) FROM harness_connector h");
        assertThat(features.get("rows").get(0).get(0).asLong()).isZero();
        assertThat(features.get("policy").get("error").asText()).isEqualTo(PartTagStore.NOT_CONFIGURED);
    }

    @Test
    void tablesOutsideThePolicyAreStillRead() throws Exception {
        JsonNode body = ok("SELECT supplier_id FROM supplier");
        assertThat(body.get("rowCount").asInt()).isEqualTo(2);
        assertThat(body.get("policy").has("error")).isFalse();
    }
}
