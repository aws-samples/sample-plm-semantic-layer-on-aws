// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import atelier.plm.common.policy.PartTagStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * POST /uk/demo/update through Spring MVC against H2 in PostgreSQL mode with the British fixture
 * ({@link UkDemoEntities}), a recording {@link EventPublisher} in place of EventBridge and, as the
 * Atelier core database, a second H2 holding an empty {@code demo_change} ({@link #CORE}). Each
 * subclass names its own in-memory database in {@code @TestPropertySource}, so each gets its own
 * context and a fresh fixture. Real PostgreSQL behaviour (SET LOCAL statement_timeout, FOR UPDATE)
 * is checked against the plm-uk image.
 */
@SpringBootTest(classes = DemoUpdateTestBase.Config.class, properties = {"plm.code=uk", "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc
@Sql(scripts = {"/tables/uk-fixture.sql", "/demo/uk-update-fixture.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
abstract class DemoUpdateTestBase {

    /** One recorded put: what the controller handed the publisher. */
    record Put(String source, String detailType, String detail) {
    }

    static final List<Put> PUTS = new ArrayList<>();

    /** The core database the store reads the change log from, shared by every context of the JVM. */
    static final DriverManagerDataSource CORE = new DriverManagerDataSource(
            "jdbc:h2:mem:democorelog;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");

    /** The publisher behind the bean, swapped by the tests of the unconfigured and refused cases. */
    static volatile EventPublisher PUBLISHER = recording();

    static EventPublisher recording() {
        return (source, detailType, detail) -> {
            PUTS.add(new Put(source, detailType, detail));
            return "evt-" + PUTS.size();
        };
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = DemoUpdateTestBase.class)
    @Import({DemoUpdateController.class, DemoErrorAdvice.class})
    static class Config {
        @Bean
        PartTagStore partTagStore() {
            new ResourceDatabasePopulator(new ClassPathResource("demo/core-change-log.sql")).execute(CORE);
            return new PartTagStore("uk", CORE);
        }

        @Bean
        EventPublisher eventPublisher() {
            return new EventPublisher() {
                @Override
                public String put(String source, String detailType, String detailJson) {
                    return PUBLISHER.put(source, detailType, detailJson);
                }

                @Override
                public String skipped() {
                    return PUBLISHER.skipped();
                }
            };
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void clearPuts() {
        PUTS.clear();
        PUBLISHER = recording();
    }

    MvcResult send(Map<String, Object> body, String profile) throws Exception {
        MockHttpServletRequestBuilder request = post("/uk/demo/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
        if (profile != null) {
            request.header("x-atelier-profile", profile);
        }
        return mvc.perform(request).andReturn();
    }

    /** The {@code value} that leaves the field out of a body. */
    static final Object ABSENT = new Object();

    /** A single correction body; a {@code value} of {@link #ABSENT} leaves the field out. */
    static Map<String, Object> update(String table, String key, String column, Object value) {
        Map<String, Object> body = cell(table, key, column, value);
        body.put("purpose", "test");
        return body;
    }

    /** One cell of a batch. */
    static Map<String, Object> cell(String table, String key, String column, Object value) {
        Map<String, Object> cell = new LinkedHashMap<>();
        cell.put("table", table);
        cell.put("key", key);
        cell.put("column", column);
        if (value != ABSENT) {
            cell.put("value", value);
        }
        return cell;
    }

    /** A batch body over the given cells. */
    static Map<String, Object> batch(List<Map<String, Object>> cells) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("updates", cells);
        body.put("purpose", "test batch");
        return body;
    }

    JsonNode expect(Map<String, Object> body, String profile, int status) throws Exception {
        MvcResult result = send(body, profile);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        return json.readTree(result.getResponse().getContentAsString());
    }

    /** The one row of a single correction's answer, after checking it is the only one. */
    JsonNode accepted(Map<String, Object> body) throws Exception {
        JsonNode answer = expect(body, "export-officer", 200);
        assertThat(answer.get("rows")).hasSize(1);
        return answer.get("rows").get(0);
    }

    JsonNode rejected(Map<String, Object> body, int status, String reason) throws Exception {
        JsonNode error = expect(body, "export-officer", status);
        assertThat(error.get("reason").asText()).as(error.toString()).isEqualTo(reason);
        return error;
    }

    /** The rows of the last event put. */
    JsonNode lastEventRows() throws Exception {
        assertThat(PUTS).isNotEmpty();
        return json.readTree(PUTS.get(PUTS.size() - 1).detail()).get("rows");
    }

    <T> T cellValue(String table, String keyColumn, Object key, String column, Class<T> type) {
        return jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE " + keyColumn + " = ?", type, key);
    }
}
