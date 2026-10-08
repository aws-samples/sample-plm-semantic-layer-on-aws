// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.uk;

import atelier.plm.common.policy.PartTagStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The plain read routes of the British service over H2 in PostgreSQL mode: the schema is the shipped V1
 * migration, the rows one component per releasability tier, and the core tags a second in-memory
 * database read through the {@link PartTagStore} as {@code atelier_core} is in production. Every route
 * returns the rows of the parts the profile may see and nothing of the others.
 */
@SpringBootTest(properties = {
        "ATELIER_POLICY_FILE=../../../ontology/policy.json",
        "spring.datasource.url=jdbc:h2:mem:ukplm;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"})
@AutoConfigureMockMvc
@Sql(scripts = {"classpath:db/migration/V1__schema.sql", "classpath:db/migration/V2__part_attributes.sql", "classpath:db/migration/V3__bill_of_materials.sql", "classpath:db/migration/V4__external_references.sql", "classpath:db/migration/V5__purchased_items.sql", "classpath:db/migration/V8__gear_attributes.sql", "classpath:db/migration/V9__purchased_item_classes.sql", "classpath:db/migration/V10__bom_line_placement.sql", "classpath:db/migration/V11__option_codes_and_ports.sql", "classpath:db/migration/V12__span.sql", "/uk-seed.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class UkPlmControllerTest {

    @TestConfiguration
    static class CoreTags {

        /** The core database in place of the one CORE_DB_* would open. */
        @Bean
        @Primary
        PartTagStore testPartTagStore() {
            DriverManagerDataSource core = new DriverManagerDataSource(
                    "jdbc:h2:mem:ukcoretags;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
            new ResourceDatabasePopulator(new ClassPathResource("core-tags.sql")).execute(core);
            return new PartTagStore("uk", core);
        }
    }

    static final List<String> FEATURE_ROUTES = List.of("/uk/plugs", "/uk/fasteners", "/uk/couplings");

    @Autowired
    MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    /** GET as a profile; {@code null} sends no profile header at all. */
    private MockHttpServletRequestBuilder as(String url, String profile) {
        MockHttpServletRequestBuilder request = get(url).accept("application/json");
        return profile == null ? request : request.header("x-atelier-profile", profile);
    }

    private JsonNode getOk(String url, String profile) throws Exception {
        return json.readTree(mvc.perform(as(url, profile)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static List<String> field(JsonNode rows, String name) {
        List<String> values = new ArrayList<>();
        rows.forEach(row -> values.add(row.get(name).asText()));
        return values;
    }

    @Test
    void nationalEngineerDoesNotReceiveAnotherNationsPartNorItsFeatures() throws Exception {
        assertThat(field(getOk("/uk/parts", "de-engineer"), "id")).containsExactly("BRK-DE", "HARN-EU", "PNL-ALL");

        for (String route : FEATURE_ROUTES) {
            assertThat(field(getOk(route, "de-engineer"), "partId")).as(route).containsExactlyInAnyOrder("BRK-DE", "HARN-EU", "PNL-ALL");
        }
        mvc.perform(as("/uk/parts/RIB-FR", "de-engineer")).andExpect(status().isNotFound());
        mvc.perform(as("/uk/parts/RIB-FR", "fr-engineer")).andExpect(status().isOk());
    }

    @Test
    void officerReceivesEveryTaggedPartAndItsFeatures() throws Exception {
        JsonNode parts = getOk("/uk/parts", "export-officer");
        assertThat(field(parts, "id")).containsExactly("ACT-LIC", "BRK-DE", "HARN-EU", "PNL-ALL", "RIB-FR");
        assertThat(parts.get(0).get("plm").asText()).isEqualTo("UK");

        for (String route : FEATURE_ROUTES) {
            assertThat(field(getOk(route, "export-officer"), "partId")).as(route)
                    .containsExactlyInAnyOrder("ACT-LIC", "BRK-DE", "HARN-EU", "PNL-ALL", "RIB-FR");
        }
        assertThat(getOk("/uk/parts/ACT-LIC", "export-officer").get("name").asText()).isEqualTo("Actuator under export licence");
    }

    @Test
    void untaggedPartIsVisibleToNoProfile() throws Exception {
        assertThat(field(getOk("/uk/parts", "export-officer"), "id")).doesNotContain("UNT-UK");
        assertThat(field(getOk("/uk/plugs", "export-officer"), "id")).doesNotContain("PL UNT-01");
        mvc.perform(as("/uk/parts/UNT-UK", "export-officer")).andExpect(status().isNotFound());
    }

    @Test
    void missingOrUnknownProfileReceivesPartsReleasableToAllOnly() throws Exception {
        assertThat(field(getOk("/uk/parts", null), "id")).containsExactly("PNL-ALL");
        assertThat(field(getOk("/uk/parts", "nobody"), "id")).containsExactly("PNL-ALL");
        for (String route : FEATURE_ROUTES) {
            assertThat(field(getOk(route, null), "partId")).as(route).containsExactly("PNL-ALL");
        }
        mvc.perform(as("/uk/parts/HARN-EU", null)).andExpect(status().isNotFound());
    }

    @Test
    void hiddenRowsAreAbsentNotRedacted() throws Exception {
        JsonNode plugs = getOk("/uk/plugs", "de-engineer");
        plugs.forEach(plug -> assertThat(plug.fieldNames()).toIterable()
                .containsExactly("id", "partId", "x", "y", "z", "unit", "connectorType", "pinCount", "plm"));
        assertThat(field(plugs, "id")).containsExactlyInAnyOrder("PL ALL-01", "PL EU-01", "PL DE-01");
    }
}
