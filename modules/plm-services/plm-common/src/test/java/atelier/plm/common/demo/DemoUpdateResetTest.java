// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The officer's reset of a cell seeded outside its column's form, the case of the French external
 * reference XR-0002 ({@code urn:plm:es:pieza:ES-3203}): a correction to a well-formed URN is logged
 * in the core {@code demo_change}, and the reset's request ({@code purpose "reset: <id>"}) restores
 * the logged before value although the form refuses it. Every other request keeps the form.
 */
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:demoupdatereset;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
class DemoUpdateResetTest extends DemoUpdateTestBase {

    static final String SEEDED = "urn:plm:es:pieza:ES-3203";
    static final String CORRECTED = "urn:plm:es:part:ES-3203";
    static final String OTHER_MALFORMED = "urn:plm:es:pieza:ES-9999";

    private final JdbcTemplate core = new JdbcTemplate(CORE);

    @AfterEach
    void clearLog() {
        core.update("DELETE FROM demo_change");
    }

    private static Map<String, Object> reset(Object value, long id) {
        Map<String, Object> body = update("external_ref", "XR-0002", "remote_urn", value);
        body.put("purpose", "reset: " + id);
        return body;
    }

    private String urn() {
        return cellValue("external_ref", "id", "XR-0002", "remote_urn", String.class);
    }

    @Test
    void theResetRestoresTheLoggedBeforeValueOutsideTheFormAndNothingElseDoes() throws Exception {
        jdbc.update("INSERT INTO external_ref (id, part_no, remote_urn, expected_revision) VALUES ('XR-0002', 'HARN-6200-L', ?, '1')", SEEDED);

        rejected(update("external_ref", "XR-0002", "remote_urn", OTHER_MALFORMED), 400, "value-not-in-form");
        assertThat(accepted(update("external_ref", "XR-0002", "remote_urn", CORRECTED)).get("after").asText()).isEqualTo(CORRECTED);
        core.update("INSERT INTO demo_change (plm, table_name, row_key, column_name, before, after, actor, purpose) "
                + "VALUES ('uk', 'external_ref', 'XR-0002', 'remote_urn', ?, ?, 'uk-engineer', 'point at the ES part')", SEEDED, CORRECTED);
        long id = core.queryForObject("SELECT id FROM demo_change", Long.class);

        rejected(reset(OTHER_MALFORMED, id), 400, "value-not-in-form");
        assertThat(expect(reset(SEEDED, id), "uk-engineer", 400).get("reason").asText())
                .as("the reset is the officer's").isEqualTo("value-not-in-form");
        rejected(update("external_ref", "XR-0002", "remote_urn", SEEDED), 400, "value-not-in-form");
        Map<String, Object> otherCell = update("external_ref", "XR-1", "remote_urn", SEEDED);
        otherCell.put("purpose", "reset: " + id);
        rejected(otherCell, 400, "value-not-in-form");
        assertThat(urn()).as("every refusal left the correction").isEqualTo(CORRECTED);

        JsonNode restored = accepted(reset(SEEDED, id));
        assertThat(restored.get("before").asText()).isEqualTo(CORRECTED);
        assertThat(restored.get("after").asText()).isEqualTo(SEEDED);
        assertThat(urn()).isEqualTo(SEEDED);
    }
}
