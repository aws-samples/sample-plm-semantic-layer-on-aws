// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Exercises POST /uk/demo/events/cad through Spring MVC against H2 with the British fixture (the
 * {@code component} part table; the entities are those declared in {@link DemoUpdateControllerTest},
 * scanned from this package) and a recording {@link EventPublisher} in place of EventBridge: the
 * event's source, detail-type and detail, the 403 for other profiles, the 404 for an unknown part
 * and the 400 for a bad file key. The real adapter is covered by {@link EventBridgePublisherTest}.
 */
@SpringBootTest(properties = {
        "plm.code=uk",
        "spring.datasource.url=jdbc:h2:mem:democad;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc
@Sql(scripts = "/tables/uk-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class DemoCadControllerTest {

    /** One recorded put: what the controller handed the publisher. */
    record Put(String source, String detailType, String detail) {
    }

    static final List<Put> PUTS = new ArrayList<>();

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = DemoCadControllerTest.class)
    @Import({DemoCadController.class, DemoErrorAdvice.class})
    static class Config {
        @Bean
        EventPublisher eventPublisher() {
            return (source, detailType, detail) -> {
                PUTS.add(new Put(source, detailType, detail));
                return "evt-" + PUTS.size();
            };
        }
    }

    static final String PART = "HARN-6200-L";
    static final String CAD_FILE = "cad/ornithopter/uk-left-sensor-harness-v2.stp";

    @Autowired
    MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void clear() {
        PUTS.clear();
    }

    private MvcResult send(String part, String cadFile, String profile) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("part", part);
        body.put("cadFile", cadFile);
        MockHttpServletRequestBuilder request = post("/uk/demo/events/cad")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
        if (profile != null) {
            request.header("x-atelier-profile", profile);
        }
        return mvc.perform(request).andReturn();
    }

    private JsonNode expect(String part, String cadFile, String profile, int status) throws Exception {
        MvcResult result = send(part, cadFile, profile);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String rejected(String part, String cadFile, int status) throws Exception {
        return expect(part, cadFile, "export-officer", status).get("reason").asText();
    }

    @Test
    void putsOneEventWithTheAgreedShapeAndReturnsItsIdWithTheDetail() throws Exception {
        JsonNode body = expect(PART, CAD_FILE, "export-officer", 200);

        assertThat(body.get("eventId").asText()).isEqualTo("evt-1");
        assertThat(body.get("plm").asText()).isEqualTo("UK");
        assertThat(body.get("part").asText()).isEqualTo(PART);
        assertThat(body.get("partIri").asText()).isEqualTo("https://example.com/atelier/uk/part/HARN-6200-L");
        assertThat(body.get("cadFile").asText()).isEqualTo(CAD_FILE);
        OffsetDateTime at = OffsetDateTime.parse(body.get("at").asText());
        assertThat(at).isAfter(OffsetDateTime.now().minusMinutes(1));

        assertThat(PUTS).hasSize(1);
        Put put = PUTS.get(0);
        assertThat(put.source()).isEqualTo("atelier.plm");
        assertThat(put.detailType()).isEqualTo("part.cad.published");
        JsonNode detail = json.readTree(put.detail());
        List<String> fields = new ArrayList<>();
        detail.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("plm", "part", "partIri", "cadFile", "at");
        assertThat(detail.get("plm").asText()).isEqualTo("UK");
        assertThat(detail.get("part").asText()).isEqualTo(PART);
        assertThat(detail.get("partIri").asText()).isEqualTo("https://example.com/atelier/uk/part/HARN-6200-L");
        assertThat(detail.get("cadFile").asText()).isEqualTo(CAD_FILE);
        assertThat(OffsetDateTime.parse(detail.get("at").asText())).isEqualTo(at);
    }

    @Test
    void isForTheUkEngineerOrTheExportOfficerAndPutsNothingForAnyOtherProfile() throws Exception {
        for (String profile : new String[] {"fr-engineer", "programme-cleared", "unknown", "nobody", null}) {
            JsonNode error = expect(PART, CAD_FILE, profile, 403);
            assertThat(error.get("reason").asText()).as(profile).isEqualTo("not-allowed");
            assertThat(error.get("error").asText()).as(profile).isEqualTo("x-atelier-profile must be one of [uk-engineer, export-officer]");
        }
        assertThat(PUTS).isEmpty();

        JsonNode body = expect(PART, CAD_FILE, "uk-engineer", 200);
        assertThat(body.get("eventId").asText()).isEqualTo("evt-1");
        assertThat(PUTS).as("the PLM's own engineer publishes its CAD files").hasSize(1);
    }

    @Test
    void answers404WhenThePartIsNotInThePartTable() throws Exception {
        JsonNode error = expect("NO-SUCH-PART", CAD_FILE, "export-officer", 404);
        assertThat(error.get("reason").asText()).isEqualTo("part-not-found");
        assertThat(error.get("error").asText()).isEqualTo("no row of component has comp_id = NO-SUCH-PART");
        assertThat(rejected("PL 6200-01", CAD_FILE, 404)).as("a plug is not a part").isEqualTo("part-not-found");
        assertThat(PUTS).isEmpty();
    }

    @Test
    void rejectsAFileKeyOutsideTheProductFolderShape() throws Exception {
        for (String accepted : new String[] {"cad/ornithopter/fr-keel-beam.stp", "cad/aerial-screw/de-hydraulic-motor.stp"}) {
            assertThat(DemoCadController.CAD_FILE.matcher(accepted).matches()).as(accepted).isTrue();
        }
        for (String cadFile : new String[] {"uk-left-sensor-harness.stp", "cad/uk-left-sensor-harness.stp",
                "cad/ornithopter/UK-Left-Sensor-Harness.stp", "cad/ornithopter/left sensor harness.stp", "cad/ornithopter/../secret.stp",
                "cad/../ornithopter/secret.stp", "cad/ornithopter/left/sensor-harness.stp", "cad/ornithopter/uk-left-sensor-harness.step",
                "cad/ornithopter/uk-left-sensor-harness.stp\n", "s3://bucket/cad/ornithopter/uk-left-sensor-harness.stp"}) {
            assertThat(rejected(PART, cadFile, 400)).as(cadFile).isEqualTo("bad-cad-file");
        }
        assertThat(rejected(PART, null, 400)).isEqualTo("missing-field");
        assertThat(rejected("", CAD_FILE, 400)).isEqualTo("missing-field");
        assertThat(PUTS).isEmpty();
    }

    @Test
    void mintsThePartIriWithTheIdPercentEncodedAsR2rmlDoes() {
        assertThat(PartIri.of("uk", "PL 6200-01")).isEqualTo("https://example.com/atelier/uk/part/PL%206200-01");
        assertThat(PartIri.of("fr", "FR-ORN-CERC-001")).isEqualTo("https://example.com/atelier/fr/part/FR-ORN-CERC-001");
        assertThat(PartIri.encode("a/b?c#d~e*f.g_h")).isEqualTo("a%2Fb%3Fc%23d~e%2Af.g_h");
    }

    @Test
    void unconfiguredPublisherRefusesWith503() {
        assertThatThrownBy(() -> EventPublisher.unconfigured().put("atelier.plm", "part.cad.published", "{}"))
                .isInstanceOf(DemoRejectedException.class)
                .satisfies(e -> assertThat(((DemoRejectedException) e).status().value()).isEqualTo(503));
        assertThat(EventPublisher.unconfigured().skipped()).isEqualTo("event bus not configured: AWS_REGION is not set");
    }
}
