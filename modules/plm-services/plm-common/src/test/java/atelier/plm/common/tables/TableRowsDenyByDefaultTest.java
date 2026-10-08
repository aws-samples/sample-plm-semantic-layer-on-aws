// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.tables;

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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The production {@link PartTagStoreConfiguration} without any CORE_DB_* setting: the store is
 * unconfigured, so part and feature tables yield no row and say why, while tables outside the
 * export-control policy are still served. Same entities and fixture as {@link TableRowsControllerTest}.
 */
@SpringBootTest(properties = {
        "plm.code=uk",
        "ATELIER_POLICY_FILE=src/test/resources/policy.json",
        "spring.datasource.url=jdbc:h2:mem:tablesdeny;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc
@Sql(scripts = "/tables/uk-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class TableRowsDenyByDefaultTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = TableRowsControllerTest.class)
    @Import({TableRowsController.class, PolicyConfiguration.class, PartTagStoreConfiguration.class})
    static class Config {
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    PartTagStore store;

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode getOk(String encodedUrl) throws Exception {
        MvcResult result = mvc.perform(get(URI.create(encodedUrl)).header("x-atelier-profile", "export-officer"))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void storeIsUnconfiguredWithoutCoreConnectionSettings() {
        assertThat(store.isConfigured()).isFalse();
    }

    @Test
    void partTableYieldsNoRowAndNamesTheCause() throws Exception {
        JsonNode body = getOk("/uk/tables/component?keys=PNL-ALL,HARN-6200-L");

        assertThat(body.get("rows")).isEmpty();
        assertThat(body.get("columns")).extracting(JsonNode::asText).containsExactly("comp_id", "name", "cad_file");
        assertThat(body.get("policy").get("profile").asText()).isEqualTo("export-officer");
        assertThat(body.get("policy").get("error").asText()).isEqualTo(PartTagStore.NOT_CONFIGURED);
    }

    @Test
    void featureTableYieldsNoRowAndNamesTheCause() throws Exception {
        JsonNode body = getOk("/uk/tables/harness_connector?keys=PL%20ALL-01");

        assertThat(body.get("rows")).isEmpty();
        assertThat(body.get("policy").get("error").asText()).isEqualTo("core tag store not configured");
    }

    @Test
    void tablesOutsideThePolicyAreStillServed() throws Exception {
        JsonNode body = getOk("/uk/tables/supplier?keys=SUP-1");

        assertThat(body.get("rows")).hasSize(1);
        assertThat(body.get("policy").has("error")).isFalse();
    }
}
