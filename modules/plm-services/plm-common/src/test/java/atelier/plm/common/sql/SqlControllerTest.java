// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.sql;

import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import atelier.plm.common.annotation.Unit;
import atelier.plm.common.log.PurposeLog;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises POST /uk/sql through Spring MVC against two H2 databases in PostgreSQL mode, the same
 * fixtures as the tables endpoint: the British PLM's own (a part table, a feature table, a table
 * with no export-control mapping) and the Atelier core database holding the part tags. Real PostgreSQL
 * behaviour (SET TRANSACTION, statement_timeout, type names) is checked against the plm-uk image.
 */
@SpringBootTest(properties = {
        "plm.code=uk",
        "ATELIER_POLICY_FILE=src/test/resources/policy.json",
        "spring.datasource.url=jdbc:h2:mem:sqltables;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc
@Sql(scripts = "/tables/uk-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class SqlControllerTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = SqlControllerTest.class)
    @Import({SqlController.class, PolicyConfiguration.class})
    static class Config {

        /** The core database: a second in-memory H2 seeded with the tags of the fixture's parts. */
        @Bean
        PartTagStore partTagStore() {
            DriverManagerDataSource core = new DriverManagerDataSource(
                    "jdbc:h2:mem:sqlcoretags;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
            new ResourceDatabasePopulator(new ClassPathResource("tables/core-fixture.sql")).execute(core);
            return new PartTagStore("uk", core);
        }
    }

    @Entity
    @Table(name = "component")
    @OntologyClass("atelier:Part")
    @Describe("Component: a part managed in the British PLM")
    static class Component {
        @Id
        @Column(name = "comp_id")
        @Describe("Component id")
        @Maps("atelier:identifier")
        String compId;

        @Column(name = "name")
        @Describe("Component name")
        @Maps("atelier:label")
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
        @Describe("Component carrying the connector")
        @Maps("atelier:onPart")
        Component component;

        @Column(name = "pos_x", precision = 12, scale = 4)
        @Describe("Connector position along X")
        @Unit(column = "pos_uom")
        @Maps("atelier:positionX")
        BigDecimal posX;

        @Column(name = "pos_y", precision = 12, scale = 4)
        BigDecimal posY;

        @Column(name = "pos_z", precision = 12, scale = 4)
        BigDecimal posZ;

        @Column(name = "pos_uom")
        String posUom;

        @Column(name = "shell_type")
        String shellType;

        @Column(name = "pin_qty")
        @Describe("Pin count")
        @Maps("atelier:pinCount")
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

    @Autowired
    MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private MvcResult send(String sql, String profile) throws Exception {
        MockHttpServletRequestBuilder request = post("/uk/sql")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("sql", sql, "purpose", "test")));
        if (profile != null) {
            request.header("x-atelier-profile", profile);
        }
        return mvc.perform(request).andReturn();
    }

    private JsonNode ok(String sql, String profile) throws Exception {
        MvcResult result = send(sql, profile);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode rejected(String sql, String profile) throws Exception {
        MvcResult result = send(sql, profile);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(400);
        return json.readTree(result.getResponse().getContentAsString());
    }

    private long count(String table, String profile) throws Exception {
        return ok("SELECT count(*) AS n FROM " + table, profile).get("rows").get(0).get(0).asLong();
    }

    private static JsonNode entry(JsonNode catalogueUsed, String table, String column) {
        for (JsonNode entry : catalogueUsed) {
            if (entry.get("table").asText().equals(table) && entry.get("column").asText().equals(column)) {
                return entry;
            }
        }
        throw new AssertionError("no catalogue entry " + table + "." + column + " in " + catalogueUsed);
    }

    private static List<String> texts(JsonNode array) {
        List<String> texts = new ArrayList<>();
        array.forEach(node -> texts.add(node.asText()));
        return texts;
    }

    @Test
    void acceptsSelectWithCteAndJoinAndReportsTheSqlThatRan() throws Exception {
        JsonNode body = ok("""
                WITH left_wing AS (SELECT comp_id, name FROM component WHERE name LIKE 'Left wing%')
                SELECT w.name, h.conn_ref, h.pin_qty
                FROM left_wing w JOIN harness_connector h ON h.comp_id = w.comp_id
                ORDER BY h.conn_ref""", "export-officer");

        assertThat(body.get("plm").asText()).isEqualTo("UK");
        assertThat(body.get("profile").asText()).isEqualTo("export-officer");
        assertThat(body.get("rowCount").asInt()).isEqualTo(3);
        assertThat(body.get("columns")).extracting(c -> c.get("name").asText()).containsExactly("name", "conn_ref", "pin_qty");
        assertThat(body.get("columns").get(2).get("type").asText()).isNotEmpty();
        assertThat(body.get("rows").get(0)).extracting(JsonNode::asText).containsExactly("Left wing hydraulic actuator", "PL 6190-01", "5");
        assertThat(body.get("rows").get(2)).extracting(JsonNode::asText).containsExactly("Left wing sensor harness", "PL 6200-03", "55");
        assertThat(body.get("ms").isNumber()).isTrue();

        String executed = body.get("sqlExecuted").asText();
        assertThat(body.get("sqlRequested").asText()).startsWith("WITH left_wing AS");
        assertThat(executed).contains("(SELECT * FROM component WHERE comp_id IN (?, ?, ?, ?, ?, ?)) AS component");
        assertThat(executed).contains("JOIN (SELECT * FROM harness_connector WHERE comp_id IN (?, ?, ?, ?, ?, ?)) h ON");
        assertThat(executed).endsWith("LIMIT 200");

        assertThat(body.get("catalogueUsed")).extracting(c -> c.get("table").asText() + "." + c.get("column").asText())
                .containsExactlyInAnyOrder("component.comp_id", "component.name",
                        "harness_connector.conn_ref", "harness_connector.comp_id", "harness_connector.pin_qty");
        JsonNode onPart = entry(body.get("catalogueUsed"), "harness_connector", "comp_id");
        assertThat(onPart.get("term").asText()).isEqualTo("atelier:onPart");
        assertThat(onPart.get("description").asText()).isEqualTo("Component carrying the connector");
        assertThat(body.get("policy").get("profile").asText()).isEqualTo("export-officer");
        assertThat(body.get("policy").has("error")).isFalse();
    }

    @Test
    void correlatedSubqueryResolvesOuterAliasAndIsFilteredToo() throws Exception {
        JsonNode body = ok("""
                SELECT c.comp_id FROM component c
                WHERE EXISTS (SELECT 1 FROM harness_connector h WHERE h.comp_id = c.comp_id AND h.pin_qty > 40)""", "export-officer");

        assertThat(texts(body.get("rows").get(0))).containsExactly("HARN-6200-L");
        assertThat(body.get("sqlExecuted").asText())
                .contains("FROM (SELECT * FROM component WHERE comp_id IN (?, ?, ?, ?, ?, ?)) c WHERE EXISTS")
                .contains("FROM (SELECT * FROM harness_connector WHERE comp_id IN (?, ?, ?, ?, ?, ?)) h WHERE");
    }

    @Test
    void starExpandsToTheCatalogueColumnsOfTheTable() throws Exception {
        JsonNode body = ok("SELECT h.* FROM harness_connector h WHERE h.conn_ref = 'PL 6200-03'", "uk-engineer");

        assertThat(body.get("columns")).extracting(c -> c.get("name").asText())
                .containsExactly("conn_ref", "comp_id", "pos_x", "pos_y", "pos_z", "pos_uom", "shell_type", "pin_qty");
        assertThat(body.get("rows").get(0).get(2).decimalValue()).isEqualByComparingTo(new BigDecimal("22.6378"));
        assertThat(body.get("rows").get(0).get(5).isNull()).isTrue();
        assertThat(body.get("catalogueUsed")).hasSize(8);
        assertThat(body.get("catalogueUsed").get(2).get("term").asText()).isEqualTo("atelier:positionX");
    }

    @Test
    void profileDecidesWhichPartsAndFeaturesTheStatementCanRead() throws Exception {
        assertThat(count("component", "export-officer")).isEqualTo(6);
        assertThat(count("component", "de-engineer")).isEqualTo(4);
        assertThat(count("harness_connector", "export-officer")).isEqualTo(7);
        assertThat(count("harness_connector", "de-engineer")).isEqualTo(5);

        JsonNode unlisted = ok("SELECT comp_id FROM component", "nobody");
        assertThat(unlisted.get("profile").asText()).isEqualTo("unknown");
        assertThat(texts(unlisted.get("rows").get(0))).containsExactly("PNL-ALL");
        assertThat(unlisted.get("policy").get("releasable")).extracting(JsonNode::asText).containsExactly("ALL");
    }

    @Test
    void tablesWithoutAnExportControlMappingRunAsWritten() throws Exception {
        JsonNode body = ok("SELECT * FROM supplier ORDER BY supplier_id", "nobody");

        assertThat(body.get("rowCount").asInt()).isEqualTo(2);
        assertThat(body.get("sqlExecuted").asText()).isEqualTo("SELECT * FROM supplier ORDER BY supplier_id LIMIT 200");
    }

    @Test
    void clampsTheLimitTo200() throws Exception {
        assertThat(ok("SELECT comp_id FROM component LIMIT 5000", "uk-engineer").get("sqlExecuted").asText()).endsWith("LIMIT 200");
        assertThat(ok("SELECT comp_id FROM component", "uk-engineer").get("sqlExecuted").asText()).endsWith("LIMIT 200");
        assertThat(ok("SELECT comp_id FROM component FETCH FIRST 500 ROWS ONLY", "uk-engineer").get("sqlExecuted").asText())
                .endsWith("LIMIT 200");
        assertThat(ok("SELECT comp_id FROM component LIMIT ALL", "uk-engineer").get("sqlExecuted").asText()).endsWith("LIMIT 200");

        JsonNode three = ok("SELECT comp_id FROM component ORDER BY comp_id LIMIT 3", "export-officer");
        assertThat(three.get("rowCount").asInt()).isEqualTo(3);
        assertThat(three.get("sqlExecuted").asText()).endsWith("LIMIT 3");
    }

    @Test
    void rejectsWriteStatementsWithoutRunningThem() throws Exception {
        JsonNode update = rejected("UPDATE component SET name = 'x' WHERE comp_id = 'PNL-ALL'", "export-officer");
        assertThat(update.get("reason").asText()).isEqualTo("not-a-select");
        assertThat(update.get("error").asText()).contains("UPDATE");

        assertThat(rejected("DROP TABLE component", "export-officer").get("error").asText()).contains("DROP");
        assertThat(rejected("SELECT 1; DELETE FROM component", "export-officer").get("reason").asText()).isEqualTo("multiple-statements");
        rejected("WITH gone AS (DELETE FROM component RETURNING *) SELECT * FROM gone", "export-officer");
        rejected("SELECT * INTO component_copy FROM component", "export-officer");
        assertThat(rejected("SELECT * FROM component FOR UPDATE", "export-officer").get("reason").asText()).isEqualTo("locking-clause");

        assertThat(count("component", "export-officer")).isEqualTo(6);
        assertThat(ok("SELECT name FROM component WHERE comp_id = 'PNL-ALL'", "export-officer").get("rows").get(0).get(0).asText())
                .isEqualTo("Linen panel");
    }

    @Test
    void rejectsSystemCataloguesFunctionsAndParameters() throws Exception {
        assertThat(rejected("SELECT * FROM pg_tables", "export-officer").get("reason").asText()).isEqualTo("system-catalogue");
        assertThat(rejected("SELECT * FROM pg_catalog.pg_class", "export-officer").get("reason").asText()).isEqualTo("system-catalogue");
        assertThat(rejected("SELECT table_name FROM information_schema.tables", "export-officer").get("reason").asText())
                .isEqualTo("system-catalogue");
        assertThat(rejected("SELECT pg_sleep(1)", "export-officer").get("reason").asText()).isEqualTo("forbidden-function");
        assertThat(rejected("SELECT comp_id FROM component WHERE comp_id = ?", "export-officer").get("reason").asText()).isEqualTo("parameter");
        assertThat(rejected("SELECT * FROM generate_series(1, 10)", "export-officer").get("reason").asText()).isEqualTo("table-function");
    }

    @Test
    void rejectsUnknownNamesWithTheClosestCatalogueEntries() throws Exception {
        JsonNode column = rejected("SELECT comp_id, nam FROM component", "export-officer");
        assertThat(column.get("reason").asText()).isEqualTo("unknown-column");
        assertThat(column.get("error").asText()).isEqualTo("unknown column nam");
        assertThat(texts(column.get("suggestions"))).startsWith("component.name").hasSizeLessThanOrEqualTo(3);

        JsonNode qualified = rejected("SELECT c.comp_id FROM component c WHERE c.pin_qty > 1", "export-officer");
        assertThat(qualified.get("reason").asText()).isEqualTo("unknown-column");
        assertThat(texts(qualified.get("suggestions"))).hasSize(3).allMatch(s -> s.startsWith("c."));

        JsonNode table = rejected("SELECT * FROM components", "export-officer");
        assertThat(table.get("reason").asText()).isEqualTo("unknown-table");
        assertThat(texts(table.get("suggestions"))).startsWith("component");

        JsonNode cteColumn = rejected("WITH w AS (SELECT comp_id FROM component) SELECT w.name FROM w", "export-officer");
        assertThat(cteColumn.get("reason").asText()).isEqualTo("unknown-column");
        assertThat(texts(cteColumn.get("suggestions"))).containsExactly("w.comp_id");
    }

    @Test
    void rejectsCteNamesThatShadowCatalogueTables() throws Exception {
        JsonNode same = rejected("WITH component AS (SELECT * FROM component) SELECT comp_id, cad_file FROM component", "de-engineer");
        assertThat(same.get("reason").asText()).isEqualTo("cte-shadows-table");
        assertThat(same.get("error").asText()).startsWith("CTE name shadows a table");

        JsonNode qualified = rejected("WITH component AS (SELECT 1 AS x) SELECT * FROM public.component", "de-engineer");
        assertThat(qualified.get("reason").asText()).isEqualTo("cte-shadows-table");

        JsonNode nested = rejected("SELECT * FROM (WITH Harness_Connector AS (SELECT 1 AS x) SELECT x FROM Harness_Connector) q", "de-engineer");
        assertThat(nested.get("reason").asText()).isEqualTo("cte-shadows-table");
    }

    @Test
    void onlyARecursiveCteSeesItsOwnName() throws Exception {
        JsonNode recursive = ok("WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 3) SELECT i FROM n ORDER BY i",
                "export-officer");
        assertThat(recursive.get("rows")).extracting(row -> row.get(0).asInt()).containsExactly(1, 2, 3);

        JsonNode plain = rejected("WITH w AS (SELECT comp_id FROM w) SELECT comp_id FROM w", "export-officer");
        assertThat(plain.get("reason").asText()).isEqualTo("unknown-table");
    }

    @Test
    void schemaQualifiedNamesAreBaseTablesNeverCtes() throws Exception {
        JsonNode body = ok("WITH w AS (SELECT 1 AS x) SELECT comp_id FROM public.component", "de-engineer");
        assertThat(body.get("rowCount").asInt()).isEqualTo(4);
        assertThat(body.get("sqlExecuted").asText()).contains("FROM (SELECT * FROM component WHERE comp_id IN (?, ?, ?, ?)) AS component");

        JsonNode unknown = rejected("WITH w AS (SELECT 1 AS x) SELECT * FROM public.w", "de-engineer");
        assertThat(unknown.get("reason").asText()).isEqualTo("unknown-table");
    }

    @Test
    void limitOffsetAndFetchTakeIntegerLiteralsAtEveryLevelAndInnerLimitsAreClamped() throws Exception {
        JsonNode offset = rejected("SELECT supplier_id FROM supplier OFFSET (SELECT count(*) FROM component WHERE cad_file LIKE 'a%')", "nobody");
        assertThat(offset.get("reason").asText()).isEqualTo("non-literal-limit");
        assertThat(rejected("SELECT supplier_id FROM supplier LIMIT (SELECT 1)", "nobody").get("reason").asText()).isEqualTo("non-literal-limit");
        assertThat(rejected("SELECT supplier_id FROM supplier WHERE supplier_id IN (SELECT supplier_id FROM supplier LIMIT ?)", "nobody")
                .get("reason").asText()).isEqualTo("non-literal-limit");

        JsonNode inner = ok("SELECT comp_id FROM component WHERE comp_id IN (SELECT comp_id FROM component ORDER BY comp_id LIMIT 5000)",
                "export-officer");
        assertThat(inner.get("sqlExecuted").asText()).contains("ORDER BY comp_id LIMIT 200)").endsWith("LIMIT 200").doesNotContain("5000");
        assertThat(inner.get("rowCount").asInt()).isEqualTo(6);

        JsonNode fetch = ok("SELECT comp_id FROM component WHERE comp_id IN "
                + "(SELECT comp_id FROM component ORDER BY comp_id FETCH FIRST 5000 ROWS ONLY) LIMIT 2 OFFSET 1", "export-officer");
        assertThat(fetch.get("sqlExecuted").asText()).contains("FETCH FIRST 200 ROWS ONLY").doesNotContain("5000").endsWith("LIMIT 2 OFFSET 1");
        assertThat(fetch.get("rowCount").asInt()).isEqualTo(2);
    }

    @Test
    void rejectsWindowClausesButKeepsInlineWindows() throws Exception {
        JsonNode window = rejected("SELECT rank() OVER w FROM supplier s WINDOW w AS "
                + "(ORDER BY (SELECT c.cad_file FROM component c WHERE c.name = s.name))", "nobody");
        assertThat(window.get("reason").asText()).isEqualTo("window-clause");

        JsonNode inline = ok("SELECT supplier_id, rank() OVER (ORDER BY supplier_id) AS r FROM supplier", "nobody");
        assertThat(inline.get("rowCount").asInt()).isEqualTo(2);
    }

    @Test
    void rejectsIntrospectionFunctionsAndObjectIdentifierCasts() throws Exception {
        for (String sql : List.of("SELECT current_database()", "SELECT current_schema()", "SELECT current_setting('search_path')",
                "SELECT has_table_privilege('component', 'SELECT')", "SELECT obj_description(1)", "SELECT pg_typeof(1)",
                "SELECT version()", "SELECT inet_server_addr()", "SELECT to_regclass('component')")) {
            assertThat(rejected(sql, "export-officer").get("reason").asText()).as(sql).isEqualTo("forbidden-function");
        }
        assertThat(rejected("SELECT 'component'::regclass", "export-officer").get("reason").asText()).isEqualTo("forbidden-cast");
        assertThat(rejected("SELECT CAST('component' AS regclass)", "export-officer").get("reason").asText()).isEqualTo("forbidden-cast");
        assertThat(rejected("SELECT comp_id FROM component WHERE cad_file::regclass IS NOT NULL", "export-officer").get("reason").asText())
                .isEqualTo("forbidden-cast");

        assertThat(ok("SELECT CAST(pin_qty AS text) AS t FROM harness_connector", "export-officer").get("rowCount").asInt()).isEqualTo(7);
    }

    @Test
    void refusesAnswersBeyondOneMegabyteOfCellText() throws Exception {
        JsonNode body = rejected("SELECT repeat('x', 200000) AS big FROM component", "export-officer");
        assertThat(body.get("reason").asText()).isEqualTo("result-too-large");
        assertThat(body.get("error").asText()).isEqualTo("result too large, add a WHERE or aggregate");

        assertThat(ok("SELECT repeat('x', 100000) AS big FROM component", "export-officer").get("rowCount").asInt()).isEqualTo(6);
    }

    @Test
    void databaseErrorsAreReportedBySqlStateOnly() throws Exception {
        JsonNode body = rejected("SELECT 1 / 0 FROM supplier", "nobody");
        assertThat(body.get("reason").asText()).isEqualTo("execution-error");
        assertThat(body.get("error").asText()).isEqualTo("the database rejected the statement (SQLSTATE 22012)");
    }

    @Test
    void purposeIsLoggedFlatAndShort() {
        assertThat(PurposeLog.safe(null)).isEmpty();
        assertThat(PurposeLog.safe("line one\nline two\ttab")).isEqualTo("line one line two tab");
        assertThat(PurposeLog.safe("p".repeat(500))).hasSize(200);
    }

    @Test
    void requiresTheProfileHeaderAndASqlText() throws Exception {
        JsonNode noProfile = rejected("SELECT comp_id FROM component", null);
        assertThat(noProfile.get("reason").asText()).isEqualTo("missing-profile");

        MvcResult noSql = mvc.perform(post("/uk/sql").contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\": \"nothing\"}").header("x-atelier-profile", "export-officer")).andReturn();
        assertThat(noSql.getResponse().getStatus()).isEqualTo(400);
        assertThat(json.readTree(noSql.getResponse().getContentAsString()).get("reason").asText()).isEqualTo("missing-sql");

        MvcResult unparsable = mvc.perform(post("/uk/sql").contentType(MediaType.APPLICATION_JSON)
                .content("not json").header("x-atelier-profile", "export-officer")).andReturn();
        assertThat(unparsable.getResponse().getStatus()).isEqualTo(400);
    }
}
