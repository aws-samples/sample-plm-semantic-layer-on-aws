// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies the value corrections of one request over this service's own database, as the PLM's
 * application role, in one transaction: for each, in order, reads the current value of the row
 * (locked), runs the single parameterised {@code UPDATE ... WHERE <key column> = ?}, and reads the
 * stored value back so the answer reports what the database holds (the column's scale applied). The
 * JDBC query timeout is {@value #TIMEOUT_MS} ms on every database and, on PostgreSQL, the
 * transaction also declares {@code SET LOCAL statement_timeout}. A key matching no row is 404 and
 * rolls back every correction of the request.
 */
final class ValueUpdate {

    static final int TIMEOUT_MS = 5000;

    /** One checked correction: the column, the key as requested and as the key column's type, and the coerced value. */
    record Change(UpdatableColumn column, String key, Object keyValue, Object value) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    ValueUpdate(DataSource dataSource, PlatformTransactionManager transactionManager) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(TIMEOUT_MS / 1000);
        this.transaction = new TransactionTemplate(transactionManager);
    }

    List<DemoUpdateResponse.Row> apply(List<Change> changes) {
        return transaction.execute(status -> {
            jdbc.execute((ConnectionCallback<Void>) ValueUpdate::declareTimeout);
            List<DemoUpdateResponse.Row> rows = new ArrayList<>();
            for (Change change : changes) {
                rows.add(apply(change));
            }
            return rows;
        });
    }

    private DemoUpdateResponse.Row apply(Change change) {
        UpdatableColumn column = change.column();
        String select = "SELECT " + column.column() + " FROM " + column.table() + " WHERE " + column.keyColumn() + " = ?";
        List<Object> before = jdbc.queryForList(select + " FOR UPDATE", Object.class, change.keyValue());
        if (before.isEmpty()) {
            throw new DemoRejectedException(HttpStatus.NOT_FOUND,
                    "no row of " + column.table() + " has " + column.keyColumn() + " = " + change.key(), "row-not-found");
        }
        if (before.size() > 1) {
            throw new DemoRejectedException(HttpStatus.BAD_REQUEST,
                    column.keyColumn() + " = " + change.key() + " matches " + before.size() + " rows of " + column.table(), "ambiguous-key");
        }
        jdbc.update("UPDATE " + column.table() + " SET " + column.column() + " = ? WHERE " + column.keyColumn() + " = ?",
                change.value(), change.keyValue());
        Object after = jdbc.queryForObject(select, Object.class, change.keyValue());
        return new DemoUpdateResponse.Row(column.table(), change.key(), column.column(), before.get(0), after);
    }

    private static Void declareTimeout(Connection connection) throws SQLException {
        if ("PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL statement_timeout = " + TIMEOUT_MS);
            }
        }
        return null;
    }
}
