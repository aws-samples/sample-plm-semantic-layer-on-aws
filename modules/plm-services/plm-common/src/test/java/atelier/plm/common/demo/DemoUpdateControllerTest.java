// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One correction at POST /uk/demo/update: the update of a measure and of a count, the one
 * {@code atelier.plm} / {@code part.value.corrected} event each request puts (naming the caller as
 * actor) and the {@code eventSkipped} answer when the bus is unconfigured or refuses (the update
 * stands), and every refusal the contract names (key, foreign-key and uncorrectable columns, unknown
 * names, profiles other than the UK engineer and the officer, a missing row), none of which puts an
 * event. The other kinds of column are in {@link DemoUpdateKindsTest}, batches in {@link DemoUpdateBatchTest}.
 */
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:demoupdate;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
class DemoUpdateControllerTest extends DemoUpdateTestBase {

    private BigDecimal posX(String connRef) {
        return cellValue("harness_connector", "conn_ref", connRef, "pos_x", BigDecimal.class);
    }

    @Test
    void updatesAMeasureAndReportsBeforeAndAfterAsStored() throws Exception {
        JsonNode body = expect(update("harness_connector", "PL 6200-03", "pos_x", "23.5"), "export-officer", 200);

        List<String> fields = new ArrayList<>();
        body.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("plm", "rows", "at", "eventId");
        assertThat(body.get("plm").asText()).isEqualTo("UK");
        assertThat(body.get("rows")).hasSize(1);
        JsonNode row = body.get("rows").get(0);
        List<String> rowFields = new ArrayList<>();
        row.fieldNames().forEachRemaining(rowFields::add);
        assertThat(rowFields).containsExactly("table", "key", "column", "before", "after");
        assertThat(row.get("table").asText()).isEqualTo("harness_connector");
        assertThat(row.get("key").asText()).isEqualTo("PL 6200-03");
        assertThat(row.get("column").asText()).isEqualTo("pos_x");
        assertThat(row.get("before").isNumber()).isTrue();
        assertThat(row.get("before").decimalValue()).isEqualByComparingTo(new BigDecimal("22.6378"));
        assertThat(row.get("after").decimalValue()).isEqualByComparingTo(new BigDecimal("23.5"));
        assertThat(OffsetDateTime.parse(body.get("at").asText())).isAfter(OffsetDateTime.now().minusMinutes(1));
        assertThat(body.get("eventId").asText()).isEqualTo("evt-1");
        assertThat(body.has("eventSkipped")).isFalse();
        assertThat(posX("PL 6200-03")).isEqualByComparingTo(new BigDecimal("23.5"));
        assertThat(posX("PL 6200-01")).isEqualByComparingTo(new BigDecimal("18.7008"));
    }

    @Test
    void putsOnePartValueCorrectedEventNamingTheCallerAsActor() throws Exception {
        JsonNode body = expect(update("harness_connector", "PL DE-01", "pos_x", "201.5"), "export-officer", 200);

        assertThat(PUTS).hasSize(1);
        Put put = PUTS.get(0);
        assertThat(put.source()).isEqualTo("atelier.plm");
        assertThat(put.detailType()).isEqualTo("part.value.corrected");
        JsonNode detail = json.readTree(put.detail());
        List<String> fields = new ArrayList<>();
        detail.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("plm", "rows", "actor", "purpose", "at");
        assertThat(detail.get("plm").asText()).isEqualTo("UK");
        assertThat(detail.get("rows")).hasSize(1);
        JsonNode row = detail.get("rows").get(0);
        List<String> rowFields = new ArrayList<>();
        row.fieldNames().forEachRemaining(rowFields::add);
        assertThat(rowFields).containsExactly("table", "key", "column", "before", "after");
        assertThat(row.get("table").asText()).isEqualTo("harness_connector");
        assertThat(row.get("key").asText()).isEqualTo("PL DE-01");
        assertThat(row.get("column").asText()).isEqualTo("pos_x");
        assertThat(row.get("before").decimalValue()).isEqualByComparingTo(new BigDecimal("200"));
        assertThat(row.get("after").decimalValue()).isEqualByComparingTo(new BigDecimal("201.5"));
        assertThat(detail.get("actor").asText()).isEqualTo("export-officer");
        assertThat(detail.get("purpose").asText()).isEqualTo("test");
        assertThat(OffsetDateTime.parse(detail.get("at").asText())).isEqualTo(OffsetDateTime.parse(body.get("at").asText()));

        expect(update("harness_connector", "PL DE-01", "pos_x", "200"), "uk-engineer", 200);
        assertThat(PUTS).hasSize(2);
        assertThat(json.readTree(PUTS.get(1).detail()).get("actor").asText()).as("the actor is the caller's profile").isEqualTo("uk-engineer");
    }

    @Test
    void answers200WithEventSkippedWhenTheBusIsUnconfigured() throws Exception {
        PUBLISHER = EventPublisher.unconfigured();

        JsonNode body = expect(update("harness_connector", "PL FR-01", "pos_x", "301"), "export-officer", 200);

        assertThat(body.get("rows").get(0).get("after").decimalValue()).isEqualByComparingTo(new BigDecimal("301"));
        assertThat(body.get("eventId").isNull()).isTrue();
        assertThat(body.get("eventSkipped").asText()).isEqualTo("event bus not configured: AWS_REGION is not set");
        assertThat(posX("PL FR-01")).as("the update stands").isEqualByComparingTo(new BigDecimal("301"));
        assertThat(PUTS).isEmpty();
    }

    @Test
    void aRefusedEventLeavesTheUpdateAndAnswers200WithEventSkipped() throws Exception {
        PUBLISHER = (source, detailType, detail) -> {
            throw new DemoRejectedException(HttpStatus.BAD_GATEWAY, "EventBridge refused the event: InternalFailure", "event-refused");
        };
        JsonNode body = expect(update("harness_connector", "PL LIC-01", "pos_x", "401"), "export-officer", 200);
        assertThat(body.get("eventId").isNull()).isTrue();
        assertThat(body.get("eventSkipped").asText()).isEqualTo("EventBridge refused the event: InternalFailure");
        assertThat(posX("PL LIC-01")).as("the update stands").isEqualByComparingTo(new BigDecimal("401"));

        PUBLISHER = (source, detailType, detail) -> {
            throw new IllegalStateException("arn:aws:iam::111111111111:role/task is not authorized");
        };
        body = expect(update("harness_connector", "PL UNT-01", "pos_x", "501"), "export-officer", 200);
        assertThat(body.get("eventId").isNull()).isTrue();
        assertThat(body.get("eventSkipped").asText()).as("the SDK's detail stays in the log").isEqualTo("the event bus did not accept the event");
        assertThat(posX("PL UNT-01")).isEqualByComparingTo(new BigDecimal("501"));
    }

    @Test
    void updatesACountFromAJsonNumberAndRefusesAFractionOrAWord() throws Exception {
        JsonNode row = accepted(update("harness_connector", "PL 6190-01", "pin_qty", 4));
        assertThat(row.get("before").intValue()).isEqualTo(5);
        assertThat(row.get("after").intValue()).isEqualTo(4);
        assertThat(lastEventRows().get(0).get("before").intValue()).isEqualTo(5);
        assertThat(cellValue("harness_connector", "conn_ref", "PL 6190-01", "pin_qty", Integer.class)).isEqualTo(4);

        rejected(update("harness_connector", "PL 6190-01", "pin_qty", 4.5), 400, "value-not-integer");
        rejected(update("harness_connector", "PL 6190-01", "pin_qty", "four"), 400, "value-not-a-number");
        rejected(update("harness_connector", "PL 6190-01", "pin_qty", true), 400, "value-not-a-number");
        assertThat(cellValue("harness_connector", "conn_ref", "PL 6190-01", "pin_qty", Integer.class)).isEqualTo(4);
    }

    @Test
    void rejectsTheKeyColumn() throws Exception {
        JsonNode error = rejected(update("harness_connector", "PL ALL-01", "conn_ref", "PL X"), 400, "key-column");
        assertThat(error.get("error").asText()).isEqualTo("conn_ref is the key column of harness_connector");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM harness_connector WHERE conn_ref = 'PL ALL-01'", Integer.class)).isEqualTo(1);
        rejected(update("supplier_part", "1", "id", 3), 400, "key-column");
    }

    @Test
    void rejectsForeignKeysAndColumnsThatAreNotCorrectable() throws Exception {
        rejected(update("harness_connector", "PL ALL-01", "comp_id", "PNL-ALL"), 400, "foreign-key");
        rejected(update("fastener", "FS 6190-01", "comp_id", "PNL-ALL"), 400, "foreign-key");
        rejected(update("external_ref", "XR-1", "part_no", "PNL-ALL"), 400, "foreign-key");
        rejected(update("supplier_part", "1", "comp_id", "PNL-ALL"), 400, "foreign-key");
        rejected(update("supplier_part", "1", "supplier_id", "SUP-2"), 400, "foreign-key");
        rejected(update("harness_connector", "PL ALL-01", "pos_z", 1), 400, "not-correctable");
        rejected(update("component", "PNL-ALL", "name", "Linen"), 400, "not-correctable");
        rejected(update("component", "PNL-ALL", "cad_file", "cad/x.stp"), 400, "not-correctable");
        assertThat(PUTS).isEmpty();
    }

    @Test
    void rejectsUnknownTablesAndColumns() throws Exception {
        rejected(update("supplier", "SUP-1", "name", 1), 400, "unknown-table");
        rejected(update("harness_connector; DROP TABLE component", "PL ALL-01", "pos_x", 1), 400, "unknown-table");
        rejected(update("harness_connector", "PL ALL-01", "pos_w", 1), 400, "unknown-column");
        rejected(update("harness_connector", "PL ALL-01", "pos_x = 0, pos_y", 1), 400, "unknown-column");
    }

    @Test
    void isForTheUkEngineerOrTheExportOfficerAndNamesThemToEveryOtherProfile() throws Exception {
        Map<String, Object> body = update("harness_connector", "PL ALL-01", "pos_x", 1);
        for (String profile : new String[] {"fr-engineer", "programme-cleared", "unknown", "nobody", null}) {
            JsonNode error = expect(body, profile, 403);
            assertThat(error.get("reason").asText()).as(profile).isEqualTo("not-allowed");
            assertThat(error.get("error").asText()).as(profile).isEqualTo("x-atelier-profile must be one of [uk-engineer, export-officer]");
        }
        assertThat(posX("PL ALL-01")).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(PUTS).as("a refusal puts no event").isEmpty();

        expect(update("harness_connector", "PL ALL-01", "pos_x", 101), "uk-engineer", 200);
        assertThat(posX("PL ALL-01")).as("the PLM's own engineer corrects its rows").isEqualByComparingTo(new BigDecimal("101"));
        expect(update("harness_connector", "PL ALL-01", "pos_x", 100), "export-officer", 200);
        assertThat(posX("PL ALL-01")).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void answers404WhenTheRowDoesNotExist() throws Exception {
        JsonNode error = rejected(update("harness_connector", "NO-SUCH-PLUG", "pos_x", 1), 404, "row-not-found");
        assertThat(error.get("error").asText()).isEqualTo("no row of harness_connector has conn_ref = NO-SUCH-PLUG");
        assertThat(PUTS).as("nothing was written, nothing is announced").isEmpty();
    }

    @Test
    void requiresEveryField() throws Exception {
        rejected(update("harness_connector", "PL ALL-01", "pos_x", ABSENT), 400, "missing-field");
        rejected(update("harness_connector", "", "pos_x", 1), 400, "missing-field");
        rejected(update(null, "PL ALL-01", "pos_x", 1), 400, "missing-field");
    }
}
