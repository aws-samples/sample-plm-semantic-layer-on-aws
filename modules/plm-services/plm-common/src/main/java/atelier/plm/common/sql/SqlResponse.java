// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.sql;

import atelier.plm.common.tables.TablePolicy;

import java.util.List;

/**
 * Answer of {@code POST /{plm}/sql}: the SQL as requested and as it actually ran (table references
 * rewritten to their releasability-filtered form, {@code LIMIT} clamped), the result with the
 * driver's column type names and raw JDBC values, the elapsed milliseconds, the catalogue entries
 * the statement referenced (so the evidence drawer can show units, descriptions and ontology terms
 * next to the columns), and the clearance the rows were filtered with.
 */
public record SqlResponse(
        String plm,
        String profile,
        String sqlRequested,
        String sqlExecuted,
        List<ColumnInfo> columns,
        List<List<Object>> rows,
        int rowCount,
        long ms,
        List<CatalogueRef> catalogueUsed,
        TablePolicy policy) {

    /** One result column: its label and the database's type name for it. */
    public record ColumnInfo(String name, String type) {
    }

    /** One catalogue column the statement referenced. {@code unit} is the fixed QUDT unit, if any. */
    public record CatalogueRef(String table, String column, String unit, String description, String term) {
    }
}
