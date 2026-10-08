// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batches and null: several cells in one request are checked, then applied in one transaction and
 * announced by one event listing every row with its before; one refused or missing row leaves every
 * cell as it was. JSON null clears a nullable cell and a NOT NULL column refuses it.
 */
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:demoupdatebatch;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
class DemoUpdateBatchTest extends DemoUpdateTestBase {

    private BigDecimal posX(String connRef) {
        return cellValue("harness_connector", "conn_ref", connRef, "pos_x", BigDecimal.class);
    }

    @Test
    void appliesABatchInOneRequestAndAnnouncesItInOneEvent() throws Exception {
        JsonNode body = expect(batch(List.of(
                cell("harness_connector", "PL 6200-03", "pos_x", "22.7"),
                cell("harness_connector", "PL 6200-03", "pos_uom", "IN"),
                cell("component", "HARN-6200-L", "lifecycle", "Frozen"),
                cell("supplier_part", "2", "lead_time_days", 40))), "uk-engineer", 200);

        JsonNode rows = body.get("rows");
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0).get("before").decimalValue()).isEqualByComparingTo(new BigDecimal("22.6378"));
        assertThat(rows.get(0).get("after").decimalValue()).isEqualByComparingTo(new BigDecimal("22.7"));
        assertThat(rows.get(1).get("before").isNull()).isTrue();
        assertThat(rows.get(1).get("after").asText()).isEqualTo("IN");
        assertThat(rows.get(2).get("before").asText()).isEqualTo("Released");
        assertThat(rows.get(2).get("after").asText()).isEqualTo("Frozen");
        assertThat(rows.get(3).get("key").asText()).isEqualTo("2");
        assertThat(rows.get(3).get("before").intValue()).isEqualTo(45);
        assertThat(rows.get(3).get("after").intValue()).isEqualTo(40);
        assertThat(body.get("eventId").asText()).isEqualTo("evt-1");

        assertThat(PUTS).as("one event for the request").hasSize(1);
        JsonNode detail = json.readTree(PUTS.get(0).detail());
        assertThat(detail.get("rows")).isEqualTo(rows);
        assertThat(detail.get("actor").asText()).isEqualTo("uk-engineer");
        assertThat(detail.get("purpose").asText()).isEqualTo("test batch");
        assertThat(cellValue("component", "comp_id", "HARN-6200-L", "lifecycle", String.class)).isEqualTo("Frozen");
    }

    @Test
    void aMissingRowAnywhereRollsBackTheWholeBatch() throws Exception {
        JsonNode error = rejected(batch(List.of(
                cell("harness_connector", "PL DE-01", "pos_x", 250),
                cell("harness_connector", "NO-SUCH-PLUG", "pos_x", 1))), 404, "row-not-found");
        assertThat(error.get("error").asText()).isEqualTo("no row of harness_connector has conn_ref = NO-SUCH-PLUG");
        assertThat(posX("PL DE-01")).as("the first cell is rolled back").isEqualByComparingTo(new BigDecimal("200"));
        assertThat(PUTS).isEmpty();
    }

    @Test
    void aRefusedCellAnywhereLeavesTheWholeBatchUnapplied() throws Exception {
        rejected(batch(List.of(
                cell("harness_connector", "PL FR-01", "pos_x", 350),
                cell("component", "RIB-FR", "lifecycle", "Publié"))), 400, "value-not-in-vocabulary");
        assertThat(posX("PL FR-01")).isEqualByComparingTo(new BigDecimal("300"));
        assertThat(PUTS).isEmpty();
    }

    @Test
    void nullClearsANullableCellAndANotNullColumnRefusesIt() throws Exception {
        JsonNode row = accepted(update("harness_connector", "PL LIC-01", "pin_qty", null));
        assertThat(row.get("before").intValue()).isEqualTo(37);
        assertThat(row.get("after").isNull()).isTrue();
        assertThat(lastEventRows().get(0).get("after").isNull()).isTrue();
        assertThat(cellValue("harness_connector", "conn_ref", "PL LIC-01", "pin_qty", Integer.class)).isNull();

        row = accepted(update("harness_connector", "PL LIC-01", "pos_uom", null));
        assertThat(row.get("before").asText()).isEqualTo("IN");
        assertThat(row.get("after").isNull()).isTrue();

        rejected(update("harness_connector", "PL LIC-01", "pos_x", null), 400, "execution-error");
        assertThat(posX("PL LIC-01")).isEqualByComparingTo(new BigDecimal("400"));
    }

    @Test
    void takesOneShapeAndBetweenOneAndFiveHundredCells() throws Exception {
        Map<String, Object> both = batch(List.of(cell("harness_connector", "PL ALL-01", "pos_x", 1)));
        both.put("table", "harness_connector");
        rejected(both, 400, "missing-field");
        rejected(Map.of("purpose", "nothing"), 400, "missing-field");
        rejected(batch(List.of()), 400, "missing-field");
        rejected(batch(List.of(cell("harness_connector", "PL ALL-01", "pos_x", ABSENT))), 400, "missing-field");

        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            many.add(cell("harness_connector", "PL ALL-01", "pin_qty", i));
        }
        rejected(batch(many), 400, "too-many-updates");
        assertThat(expect(batch(many.subList(0, 500)), "export-officer", 200).get("rows")).hasSize(500);
        assertThat(cellValue("harness_connector", "conn_ref", "PL ALL-01", "pin_qty", Integer.class)).isEqualTo(499);
        assertThat(PUTS).hasSize(1);
    }
}
