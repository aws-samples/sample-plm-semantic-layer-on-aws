// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.tables;

import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import atelier.plm.common.policy.PartTagStore;
import atelier.plm.common.policy.PolicyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the endpoint through Spring MVC against two H2 databases in PostgreSQL mode: the
 * British PLM's own (a part table without classification columns, a feature table referencing it,
 * a table with no export-control mapping) and the Atelier core database holding the part tags, read
 * through the {@link PartTagStore} as {@code atelier_core} is in production. Real PostgreSQL behaviour
 * (types, identifier folding) is checked against the plm-uk image.
 */
@SpringBootTest(properties = {
        "plm.code=uk",
        "ATELIER_POLICY_FILE=src/test/resources/policy.json",
        "spring.datasource.url=jdbc:h2:mem:tables;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc
@Sql(scripts = "/tables/uk-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class TableRowsControllerTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = TableRowsControllerTest.class)
    @Import({TableRowsController.class, PolicyConfiguration.class})
    static class Config {

        /** The core database: a second in-memory H2 seeded with the tags of the fixture's parts. */
        @Bean
        PartTagStore partTagStore() {
            DriverManagerDataSource core = new DriverManagerDataSource(
                    "jdbc:h2:mem:coretags;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
            new ResourceDatabasePopulator(new ClassPathResource("tables/core-fixture.sql")).execute(core);
            return new PartTagStore("uk", core);
        }
    }

    @Entity
    @Table(name = "component")
    @OntologyClass("atelier:Part")
    static class Component {
        @Id
        @Column(name = "comp_id")
        String compId;

        @Column(name = "name")
        String name;

        @Column(name = "cad_file")
        String cadFile;
    }

    @Entity
    @Table(name = "harness_connector")
    @OntologyClass("atelier:Plug")
    static class HarnessConnector {
        @Id
        @Column(name = "conn_ref")
        String connRef;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "comp_id")
        @Maps("atelier:onPart")
        Component component;

        @Column(name = "pos_x", precision = 12, scale = 4)
        BigDecimal posX;

        @Column(name = "pos_uom")
        String posUom;

        @Column(name = "pin_qty")
        Integer pinQty;
    }

    @Entity
    @Table(name = "supplier")
    static class Supplier {
        @Id
        @Column(name = "supplier_id")
        String supplierId;

        @Column(name = "name")
        String name;
    }

    static final String ALL_COMPONENTS = "ACTR-6190-L,HARN-6200-L,PNL-ALL,BRK-DE,RIB-FR,ACT-LIC,UNT-UK";
    static final String ALL_CONNECTORS = "PL%206190-01,PL%206200-01,PL%20ALL-01,PL%20DE-01,PL%20FR-01,PL%20LIC-01,PL%20UNT-01";

    @Autowired
    MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode getOk(String encodedUrl, String profile) throws Exception {
        MockHttpServletRequestBuilder request = get(URI.create(encodedUrl));
        if (profile != null) {
            request.header("x-atelier-profile", profile);
        }
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> keysOf(JsonNode body) {
        List<String> keys = new ArrayList<>();
        body.get("rows").forEach(row -> keys.add(row.get(0).asText()));
        return keys;
    }

    @Test
    void returnsNativeColumnsAndRawValuesInRequestedKeyOrder() throws Exception {
        JsonNode body = getOk("/uk/tables/harness_connector?keys=PL%206200-03,PL%206200-01", "uk-engineer");

        assertThat(body.get("table").asText()).isEqualTo("harness_connector");
        assertThat(body.get("keyColumn").asText()).isEqualTo("conn_ref");
        assertThat(body.get("columns")).extracting(JsonNode::asText)
                .containsExactly("conn_ref", "comp_id", "pos_x", "pos_y", "pos_z", "pos_uom", "shell_type", "pin_qty");
        assertThat(body.get("rows")).hasSize(2);

        JsonNode first = body.get("rows").get(0);
        assertThat(first.get(0).asText()).isEqualTo("PL 6200-03");
        assertThat(first.get(2).isNumber()).isTrue();
        assertThat(first.get(2).decimalValue()).isEqualByComparingTo(new BigDecimal("22.6378"));
        assertThat(first.get(5).isNull()).isTrue();
        assertThat(first.get(7).isInt()).isTrue();
        assertThat(first.get(7).intValue()).isEqualTo(55);
        assertThat(body.get("rows").get(1).get(0).asText()).isEqualTo("PL 6200-01");
    }

    @Test
    void servesEveryEntityTableOfTheService() throws Exception {
        JsonNode body = getOk("/uk/tables/component?keys=HARN-6200-L", "uk-engineer");

        assertThat(body.get("columns")).extracting(JsonNode::asText).containsExactly("comp_id", "name", "cad_file");
        assertThat(body.get("rows").get(0)).extracting(JsonNode::asText)
                .containsExactly("HARN-6200-L", "Left wing sensor harness", "cad/ornithopter/uk-left-sensor-harness.stp");
    }

    @Test
    void partRowsAreFilteredThroughTheirTagInTheCoreDatabase() throws Exception {
        JsonNode body = getOk("/uk/tables/component?keys=" + ALL_COMPONENTS, "de-engineer");

        assertThat(keysOf(body)).containsExactly("ACTR-6190-L", "HARN-6200-L", "PNL-ALL", "BRK-DE");
        assertThat(body.get("policy").get("profile").asText()).isEqualTo("de-engineer");
        assertThat(body.get("policy").get("releasable")).extracting(JsonNode::asText).containsExactly("ALL", "EU", "DE");
        assertThat(body.get("policy").has("error")).isFalse();
    }

    @Test
    void featureRowsOfAHiddenPartAreAbsent() throws Exception {
        JsonNode body = getOk("/uk/tables/harness_connector?keys=" + ALL_CONNECTORS, "de-engineer");

        assertThat(keysOf(body)).containsExactly("PL 6190-01", "PL 6200-01", "PL ALL-01", "PL DE-01");
    }

    @Test
    void untaggedPartIsVisibleToNoProfileEvenWhenAnotherPlmTagsTheSameKey() throws Exception {
        JsonNode parts = getOk("/uk/tables/component?keys=UNT-UK,PNL-ALL", "export-officer");
        assertThat(keysOf(parts)).containsExactly("PNL-ALL");

        JsonNode features = getOk("/uk/tables/harness_connector?keys=PL%20UNT-01", "export-officer");
        assertThat(features.get("rows")).isEmpty();
        assertThat(features.get("policy").has("error")).isFalse();
    }

    @Test
    void missingOrUnknownProfileSeesOnlyRowsReleasableToAll() throws Exception {
        JsonNode noHeader = getOk("/uk/tables/component?keys=" + ALL_COMPONENTS, null);
        assertThat(keysOf(noHeader)).containsExactly("PNL-ALL");
        assertThat(noHeader.get("policy").get("profile").asText()).isEqualTo("unknown");
        assertThat(noHeader.get("policy").get("releasable")).extracting(JsonNode::asText).containsExactly("ALL");

        JsonNode unlisted = getOk("/uk/tables/harness_connector?keys=" + ALL_CONNECTORS, "nobody");
        assertThat(keysOf(unlisted)).containsExactly("PL ALL-01");
        assertThat(unlisted.get("policy").get("profile").asText()).isEqualTo("unknown");
    }

    @Test
    void keyLookupStillAppliesUnderThePolicy() throws Exception {
        JsonNode body = getOk("/uk/tables/component?keys=HARN-6200-L", "de-engineer");
        assertThat(keysOf(body)).containsExactly("HARN-6200-L");

        JsonNode connectors = getOk("/uk/tables/harness_connector?keys=PL%206200-03", "de-engineer");
        assertThat(keysOf(connectors)).containsExactly("PL 6200-03");
    }

    @Test
    void tablesWithoutAnExportControlMappingAreServedUnfiltered() throws Exception {
        JsonNode body = getOk("/uk/tables/supplier?keys=SUP-1,SUP-2", null);

        assertThat(keysOf(body)).containsExactly("SUP-1", "SUP-2");
        assertThat(body.get("policy").get("profile").asText()).isEqualTo("unknown");
    }

    @Test
    void missingKeyYieldsNoRowButColumnsStayKnown() throws Exception {
        JsonNode body = getOk("/uk/tables/harness_connector?keys=PL%206200-03,NO-SUCH-KEY", "uk-engineer");
        assertThat(body.get("rows")).hasSize(1);

        JsonNode none = getOk("/uk/tables/harness_connector?keys=NO-SUCH-KEY", "uk-engineer");
        assertThat(none.get("rows")).isEmpty();
        assertThat(none.get("columns")).hasSize(8);
    }

    @Test
    void bindsKeyValuesInsteadOfInterpolatingThem() throws Exception {
        // "' OR '1'='1" as a key: bound as a literal string, it matches no row.
        JsonNode body = getOk("/uk/tables/harness_connector?keys=%27%20OR%20%271%27%3D%271", "export-officer");
        assertThat(body.get("rows")).isEmpty();
    }

    @Test
    void tablesOutsideTheMetamodelAre404() throws Exception {
        mvc.perform(get(URI.create("/uk/tables/pg_user?keys=postgres"))).andExpect(status().isNotFound());
        mvc.perform(get(URI.create("/uk/tables/HARNESS_CONNECTOR?keys=PL%206200-03"))).andExpect(status().isNotFound());
        mvc.perform(get(URI.create("/uk/tables/harness_connector%20WHERE%201%3D1?keys=x"))).andExpect(status().isNotFound());
    }

    @Test
    void rejectsEmptyAndOversizedKeyLists() throws Exception {
        mvc.perform(get(URI.create("/uk/tables/component?keys="))).andExpect(status().isBadRequest());
        mvc.perform(get(URI.create("/uk/tables/component"))).andExpect(status().isBadRequest());

        String fifty = IntStream.range(0, TableRowsController.MAX_KEYS).mapToObj(i -> "k" + i)
                .collect(Collectors.joining(","));
        mvc.perform(get(URI.create("/uk/tables/component?keys=" + fifty))).andExpect(status().isOk());
        mvc.perform(get(URI.create("/uk/tables/component?keys=" + fifty + ",one-more"))).andExpect(status().isBadRequest());
    }
}
