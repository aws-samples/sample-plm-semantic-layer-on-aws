// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.es;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The Spanish bill of materials under export control, over H2 in PostgreSQL mode with the steam engine's
 * site kit ES-3300, released to ALL, whose {@code lista_materiales} document lists the boiler ES-3301,
 * released to ES alone ({@code es-bom-fixture.sql}, {@code core-tags.sql}). The document column is
 * readable by no profile through {@code /es/tables/pieza} or {@code /es/sql}; the bill of materials is
 * read through {@code linea_lista_materiales}, filtered by its child, and the catalogue names that view.
 */
@SpringBootTest(properties = {
        "ATELIER_POLICY_FILE=../../../ontology/policy.json",
        "spring.datasource.url=jdbc:h2:mem:esplm;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"})
@AutoConfigureMockMvc
@Sql(scripts = "/es-bom-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class EsBillOfMaterialsTest {

    static final String HIDDEN = "ES-3301";
    static final List<String> PROFILES = List.of("fr-engineer", "de-engineer", "uk-engineer", "es-engineer",
            "programme-cleared", "export-officer", "unknown");

    @TestConfiguration
    static class CoreTags {

        /** The core database in place of the one CORE_DB_* would open. */
        @Bean
        @Primary
        PartTagStore testPartTagStore() {
            DriverManagerDataSource core = new DriverManagerDataSource(
                    "jdbc:h2:mem:escoretags;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
            new ResourceDatabasePopulator(new ClassPathResource("core-tags.sql")).execute(core);
            return new PartTagStore("es", core);
        }
    }

    @Autowired
    MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private MvcResult rows(String keys, String profile) throws Exception {
        return mvc.perform(get("/es/tables/pieza").param("keys", keys).header("x-atelier-profile", profile)).andReturn();
    }

    private MvcResult sql(String statement, String profile) throws Exception {
        return mvc.perform(post("/es/sql").contentType(MediaType.APPLICATION_JSON).header("x-atelier-profile", profile)
                .content(json.writeValueAsString(Map.of("sql", statement, "purpose", "bill of materials")))).andReturn();
    }

    private static List<String> columns(JsonNode answer) {
        List<String> names = new ArrayList<>();
        answer.get("columns").forEach(column -> names.add(column.isTextual() ? column.asText() : column.get("name").asText()));
        return names;
    }

    @Test
    void theTableRouteOmitsTheDocumentForEveryProfile() throws Exception {
        for (String profile : PROFILES) {
            MvcResult result = rows("ES-3300,ES-3302", profile);
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            JsonNode answer = json.readTree(result.getResponse().getContentAsString());
            assertThat(columns(answer)).as(profile).doesNotContain("lista_materiales").contains("cod_pieza", "denominacion", "tipo");
        }
        String asFrench = rows("ES-3300,ES-3301", "fr-engineer").getResponse().getContentAsString();
        assertThat(asFrench).contains("ES-3300").doesNotContain(HIDDEN);
    }

    @Test
    void noStatementOverPiezaReturnsTheHiddenChild() throws Exception {
        for (String statement : List.of(
                "SELECT * FROM pieza",
                "SELECT p.* FROM pieza p",
                "SELECT * FROM (SELECT * FROM pieza) d",
                "WITH d AS (SELECT * FROM pieza) SELECT * FROM d",
                "SELECT d.* FROM pieza p JOIN pieza d ON d.cod_pieza = p.cod_pieza")) {
            MvcResult result = sql(statement, "fr-engineer");
            String body = result.getResponse().getContentAsString();
            assertThat(result.getResponse().getStatus()).as(statement + " " + body).isEqualTo(200);
            assertThat(body).as(statement).contains("ES-3300").doesNotContain(HIDDEN).doesNotContain("lineas");
        }
    }

    @Test
    void aStatementNamingTheDocumentIsRefusedForEveryProfileAndNamesTheView() throws Exception {
        for (String profile : PROFILES) {
            for (String statement : List.of(
                    "SELECT lista_materiales FROM pieza",
                    "SELECT p.lista_materiales FROM pieza p",
                    "SELECT cod_pieza FROM pieza WHERE lista_materiales LIKE '%ES-33%'",
                    "SELECT COUNT(*) FROM pieza WHERE CAST(lista_materiales AS VARCHAR) <> ''",
                    "SELECT cod_pieza FROM pieza ORDER BY lista_materiales")) {
                MvcResult result = sql(statement, profile);
                assertThat(result.getResponse().getStatus()).as(profile + ": " + statement).isEqualTo(400);
                JsonNode error = json.readTree(result.getResponse().getContentAsString());
                assertThat(error.get("reason").asText()).as(statement).isEqualTo("read-through-column");
                assertThat(error.get("error").asText()).as(statement).contains("linea_lista_materiales");
            }
        }
    }

    @Test
    void theViewListsTheChildrenTheProfileMaySee() throws Exception {
        String asFrench = sql("SELECT padre, referencia FROM linea_lista_materiales ORDER BY referencia", "fr-engineer")
                .getResponse().getContentAsString();
        assertThat(asFrench).contains("ES-3302", "ES-3306").doesNotContain(HIDDEN);
        String asSpanish = sql("SELECT padre, referencia FROM linea_lista_materiales ORDER BY referencia", "es-engineer")
                .getResponse().getContentAsString();
        assertThat(asSpanish).contains(HIDDEN, "ES-3302");
    }

    @Test
    void theCatalogueDocumentsTheColumnAndNamesTheViewToReadInstead() throws Exception {
        JsonNode catalogue = json.readTree(mvc.perform(get("/es/catalogue")).andReturn().getResponse().getContentAsString());
        JsonNode column = null;
        for (JsonNode entity : catalogue.get("entities")) {
            for (JsonNode candidate : entity.get("columns")) {
                if ("pieza".equals(entity.get("table").asText()) && "lista_materiales".equals(candidate.get("column").asText())) {
                    column = candidate;
                }
            }
        }
        assertThat(column).isNotNull();
        assertThat(column.get("description").asText()).contains("linea_lista_materiales");
        assertThat(column.path("readThrough").asText()).isEqualTo("linea_lista_materiales");
    }
}
