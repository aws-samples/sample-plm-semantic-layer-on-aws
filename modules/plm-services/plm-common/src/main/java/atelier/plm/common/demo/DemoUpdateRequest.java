// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Body of {@code POST /{plm}/demo/update}: one correction, {@code { table, key, column, value,
 * purpose }}, or a batch, {@code { updates: [ { table, key, column, value }, ... ], purpose }}, never
 * both. Each correction names the native table, the key of the row and the column to set; the value
 * is a JSON number, string or boolean as the column takes it, or JSON null to clear the cell.
 * {@code purpose} is what the caller wants the correction for, logged with the change.
 */
public record DemoUpdateRequest(String table, String key, String column, JsonNode value, List<Correction> updates, String purpose) {

    /** At most this many corrections in one request, so one transaction stays well inside the statement timeout. */
    static final int MAX_UPDATES = 500;

    /** One cell to set: a JSON null {@code value} clears it, an absent one is a missing field. */
    public record Correction(String table, String key, String column, JsonNode value) {

        boolean complete() {
            return !isBlank(table) && !isBlank(key) && !isBlank(column) && value != null;
        }
    }

    /** The corrections of the request, in body order; 400 {@code missing-field} unless exactly one shape is complete. */
    List<Correction> corrections() {
        boolean single = table != null || key != null || column != null || value != null;
        if (updates == null) {
            Correction one = new Correction(table, key, column, value);
            if (!one.complete()) {
                throw missing("table, key, column and value are required, or updates");
            }
            return List.of(one);
        }
        if (single) {
            throw missing("send either table, key, column and value, or updates, not both");
        }
        if (updates.isEmpty()) {
            throw missing("updates needs at least one correction");
        }
        if (updates.size() > MAX_UPDATES) {
            throw new DemoRejectedException(HttpStatus.BAD_REQUEST, "updates takes at most " + MAX_UPDATES + " corrections, not " + updates.size(),
                    "too-many-updates");
        }
        for (int i = 0; i < updates.size(); i++) {
            if (updates.get(i) == null || !updates.get(i).complete()) {
                throw missing("updates[" + i + "] needs table, key, column and value");
            }
        }
        return List.copyOf(updates);
    }

    private static DemoRejectedException missing(String message) {
        return new DemoRejectedException(HttpStatus.BAD_REQUEST, message, "missing-field");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
