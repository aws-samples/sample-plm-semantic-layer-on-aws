// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.tables;

import atelier.plm.common.catalogue.AnnotationCatalogue;
import atelier.plm.common.policy.Clearance;
import atelier.plm.common.policy.PartTagStore;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A table this service maps with a JPA entity, identified by its {@code @Table} name, the column its
 * rows are looked up by (the one ending the subject IRI: the {@code @Id} column, or the last column
 * of an explicit subject template), the export-control filter its annotations declare and the
 * select list a reader gets ({@code projection}: {@code *}, or the readable columns when the table
 * holds a {@link atelier.plm.common.annotation.ReadThrough} document). All names come from the entity
 * class, so the SQL text they appear in never contains anything taken from a request.
 */
public record NativeTable(String name, String keyColumn, ReleasabilityFilter filter, String projection) {

    /** The entity table named {@code table}, or empty when no entity of the metamodel maps it. */
    public static Optional<NativeTable> of(Metamodel metamodel, String table) {
        return metamodel.getEntities().stream()
                .map(ManagedType::getJavaType)
                .filter(type -> AnnotationCatalogue.tableName(type).equals(table))
                .findFirst()
                .map(type -> new NativeTable(table, AnnotationCatalogue.keyColumn(type), ReleasabilityFilter.of(type), projection(type)));
    }

    private static String projection(Class<?> type) {
        List<String> readable = AnnotationCatalogue.readableColumns(type);
        return readable.isEmpty() ? "*" : String.join(", ", readable);
    }

    static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    /**
     * Reads the rows whose key is one of {@code keys} and that the clearance may see, with a single
     * parameterised query over this service's database once the filter has decided the visible
     * parts from the tag store; the keys are bound as strings (every key column is a VARCHAR).
     * Rows come back in the order the keys were requested; a key without a visible row yields
     * nothing.
     */
    TableRows read(JdbcTemplate jdbc, List<String> keys, Clearance clearance, PartTagStore tags) {
        ReleasabilityFilter.Clause clause = filter.clause(jdbc, keys, clearance, tags);
        String sql = "SELECT " + projection + " FROM " + name + " WHERE " + keyColumn + " IN (" + placeholders(keys.size()) + ")"
                + clause.sql();
        List<Object> args = new ArrayList<>(keys);
        args.addAll(clause.args());
        TablePolicy policy = TablePolicy.of(clearance, clause.error());
        ResultSetExtractor<TableRows> extractor = rs -> extract(rs, keys, policy);
        return jdbc.query(sql, extractor, args.toArray());
    }

    private TableRows extract(ResultSet rs, List<String> keys, TablePolicy policy) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int width = meta.getColumnCount();
        List<String> columns = new ArrayList<>(width);
        for (int i = 1; i <= width; i++) {
            columns.add(meta.getColumnLabel(i));
        }
        int keyIndex = indexOfKeyColumn(columns);

        // Several rows may share a key when the lookup column is not the whole primary key (the
        // tags of two PLMs' parts with the same native key), so each key maps to all its rows.
        Map<String, List<List<Object>>> byKey = new LinkedHashMap<>();
        while (rs.next()) {
            List<Object> row = new ArrayList<>(width);
            for (int i = 1; i <= width; i++) {
                row.add(rs.getObject(i));
            }
            byKey.computeIfAbsent(String.valueOf(row.get(keyIndex)), k -> new ArrayList<>()).add(row);
        }

        List<List<Object>> rows = new ArrayList<>();
        for (String key : keys) {
            List<List<Object>> matching = byKey.remove(key);
            if (matching != null) {
                rows.addAll(matching);
            }
        }
        // Rows whose key the driver renders differently from the request stay in database order.
        byKey.values().forEach(rows::addAll);
        return new TableRows(name, keyColumn, columns, rows, policy);
    }

    private int indexOfKeyColumn(List<String> columns) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).equalsIgnoreCase(keyColumn)) {
                return i;
            }
        }
        throw new IllegalStateException(name + " has no column " + keyColumn);
    }
}
