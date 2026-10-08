// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every kind of column a fix of a seeded defect sets, one correction each: an accepted value whose
 * before is in the answer and in the event, and a refused value with its reason, the cell unchanged.
 */
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:demoupdatekinds;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
class DemoUpdateKindsTest extends DemoUpdateTestBase {

    /** Accepts the correction and checks its before and after in the answer and the event. */
    private void corrects(String table, String key, String column, Object value, Object before, Object after) throws Exception {
        JsonNode row = accepted(update(table, key, column, value));
        JsonNode announced = lastEventRows().get(0);
        for (JsonNode reported : new JsonNode[] {row, announced}) {
            assertThat(reported.get("table").asText()).isEqualTo(table);
            assertThat(reported.get("key").asText()).isEqualTo(key);
            assertThat(reported.get("column").asText()).isEqualTo(column);
            assertSame(reported.get("before"), before);
            assertSame(reported.get("after"), after);
        }
    }

    private static void assertSame(JsonNode node, Object expected) {
        if (expected == null) {
            assertThat(node.isNull()).as(String.valueOf(node)).isTrue();
        } else if (expected instanceof BigDecimal number) {
            assertThat(node.decimalValue()).isEqualByComparingTo(number);
        } else if (expected instanceof Integer number) {
            assertThat(node.intValue()).isEqualTo(number);
        } else if (expected instanceof Boolean flag) {
            assertThat(node.isBoolean()).isTrue();
            assertThat(node.booleanValue()).isEqualTo(flag);
        } else {
            assertThat(node.isTextual()).as(String.valueOf(node)).isTrue();
            assertThat(node.asText()).isEqualTo(expected);
        }
    }

    @Test
    void aUnitColumnTakesOneOfTheUnitsTheSiteStores() throws Exception {
        corrects("harness_connector", "PL 6200-03", "pos_uom", "IN", null, "IN");
        JsonNode error = rejected(update("harness_connector", "PL 6200-03", "pos_uom", "MM"), 400, "value-not-in-vocabulary");
        assertThat(error.get("error").asText()).contains("[IN]");
        rejected(update("harness_connector", "PL 6200-03", "pos_uom", 25.4), 400, "value-not-text");
        assertThat(cellValue("harness_connector", "conn_ref", "PL 6200-03", "pos_uom", String.class)).isEqualTo("IN");
    }

    @Test
    void aConnectorTypeTakesAnyTextAndAPinCountANumber() throws Exception {
        corrects("harness_connector", "PL 6190-01", "shell_type", "M12-B", "M12-A", "M12-B");
        rejected(update("harness_connector", "PL 6190-01", "shell_type", "  "), 400, "value-not-text");
        rejected(update("harness_connector", "PL 6190-01", "shell_type", 12), 400, "value-not-text");
        corrects("harness_connector", "PL 6200-01", "pin_qty", "26", 22, 26);
        assertThat(cellValue("harness_connector", "conn_ref", "PL 6190-01", "shell_type", String.class)).isEqualTo("M12-B");
    }

    @Test
    void aFastenerTakesItsStandardDiameterAndCount() throws Exception {
        corrects("fastener", "FS 6190-01", "standard", "NAS1352", "NAS1149", "NAS1352");
        corrects("fastener", "FS 6190-01", "dia", 0.25, new BigDecimal("0.19"), new BigDecimal("0.25"));
        corrects("fastener", "FS 6190-01", "qty", 6, 4, 6);
        corrects("fastener", "FS 6190-01", "dia_uom", "IN", "IN", "IN");
        rejected(update("fastener", "FS 6190-01", "dia_uom", "MilliM"), 400, "value-not-in-vocabulary");
        rejected(update("fastener", "FS 6190-01", "qty", 6.5), 400, "value-not-integer");
    }

    @Test
    void aCouplingTakesItsPressureRatingInTheUnitTheSiteStores() throws Exception {
        corrects("hyd_coupling", "HC 6190-01", "rating", "3045", new BigDecimal("3000"), new BigDecimal("3045"));
        corrects("hyd_coupling", "HC 6190-01", "fluid", "MIL-PRF-83282", "MIL-PRF-5606", "MIL-PRF-83282");
        corrects("hyd_coupling", "HC 6190-01", "standard", "AS5203", "AS5202", "AS5203");
        JsonNode error = rejected(update("hyd_coupling", "HC 6190-01", "rating_uom", "BAR"), 400, "value-not-in-vocabulary");
        assertThat(error.get("error").asText()).contains("[PSI]");
    }

    @Test
    void aRemoteUrnTakesTheUrnForm() throws Exception {
        corrects("external_ref", "XR-1", "remote_urn", "urn:plm:es:part:ES-3101", "urn:plm:es:part:ES-31O1", "urn:plm:es:part:ES-3101");
        JsonNode error = rejected(update("external_ref", "XR-1", "remote_urn", "urn:plm:es:pieza:X"), 400, "value-not-in-form");
        assertThat(error.get("error").asText()).contains("urn:plm:(de|fr|es|uk):part:.+");
        assertThat(cellValue("external_ref", "id", "XR-1", "remote_urn", String.class)).isEqualTo("urn:plm:es:part:ES-3101");
    }

    @Test
    void anExpectedRevisionTakesARevisionForm() throws Exception {
        corrects("external_ref", "XR-1", "expected_revision", "2", "1", "2");
        corrects("external_ref", "XR-1", "expected_revision", "C1", "2", "C1");
        rejected(update("external_ref", "XR-1", "expected_revision", "rev 2"), 400, "value-not-in-form");
        rejected(update("external_ref", "XR-1", "expected_revision", "123"), 400, "value-not-in-form");
    }

    @Test
    void aLifecycleTakesTheSitesOwnWordsAndARevisionItsForm() throws Exception {
        corrects("component", "ACTR-6190-L", "lifecycle", "Released", "Draft", "Released");
        JsonNode error = rejected(update("component", "ACTR-6190-L", "lifecycle", "Freigegeben"), 400, "value-not-in-vocabulary");
        assertThat(error.get("error").asText()).contains("[Draft, Released, Frozen, Superseded]");
        corrects("component", "ACTR-6190-L", "revision", "C1", "P1", "C1");
        rejected(update("component", "ACTR-6190-L", "revision", "01"), 400, "value-not-in-form");
        assertThat(cellValue("component", "comp_id", "ACTR-6190-L", "lifecycle", String.class)).isEqualTo("Released");
    }

    @Test
    void aMassTakesANumber() throws Exception {
        corrects("component", "HARN-6200-L", "mass_lb", "0.45", new BigDecimal("0.4"), new BigDecimal("0.45"));
    }

    @Test
    void anOfferKeyedByAnIntegerTakesALeadTimeAndAPreferredFlag() throws Exception {
        corrects("supplier_part", "1", "lead_time_days", 21, 30, 21);
        corrects("supplier_part", "1", "preferred", true, false, true);
        corrects("supplier_part", "2", "preferred", "false", true, false);
        rejected(update("supplier_part", "1", "preferred", "yes"), 400, "value-not-boolean");
        rejected(update("supplier_part", "1", "preferred", 1), 400, "value-not-boolean");
        rejected(update("supplier_part", "one", "lead_time_days", 21), 400, "bad-key");
        rejected(update("supplier_part", "1.5", "lead_time_days", 21), 400, "bad-key");
        rejected(update("supplier_part", "99", "lead_time_days", 21), 404, "row-not-found");
        assertThat(cellValue("supplier_part", "id", 1, "lead_time_days", Integer.class)).isEqualTo(21);
    }
}
