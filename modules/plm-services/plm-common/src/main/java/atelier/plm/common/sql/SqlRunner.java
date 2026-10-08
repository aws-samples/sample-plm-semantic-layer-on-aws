// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.sql;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a rewritten statement over this service's own database inside a read-only transaction with
 * a {@value #TIMEOUT_MS} ms statement timeout, and returns the result as the driver delivers it:
 * column labels with the database's type names, raw JDBC values per row. The transaction is
 * read-only at the JDBC level (the connection is marked read-only before it begins) and, on
 * PostgreSQL, also declared so in SQL ({@code SET TRANSACTION READ ONLY}, {@code SET LOCAL
 * statement_timeout}), so a write slipping past the parser fails in the database too; the JDBC
 * query timeout is the same 5 s and applies on every database. An answer whose cells exceed
 * {@value #MAX_CELL_TEXT} characters of text is refused rather than returned.
 */
final class SqlRunner {

    static final int TIMEOUT_MS = 5000;
    /** Upper bound on the text of all cells of one answer; beyond it the caller is told to narrow or aggregate. */
    static final int MAX_CELL_TEXT = 1_048_576;

    /** Columns with their type names, and the rows in result order. */
    record Result(List<SqlResponse.ColumnInfo> columns, List<List<Object>> rows) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    SqlRunner(DataSource dataSource, PlatformTransactionManager transactionManager) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(TIMEOUT_MS / 1000);
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setReadOnly(true);
    }

    Result run(String sql, List<Object> args) {
        return transaction.execute(status -> {
            jdbc.execute((ConnectionCallback<Void>) SqlRunner::declareReadOnly);
            return jdbc.query(sql, SqlRunner::extract, args.toArray());
        });
    }

    private static Void declareReadOnly(java.sql.Connection connection) throws SQLException {
        if ("PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET TRANSACTION READ ONLY");
                statement.execute("SET LOCAL statement_timeout = " + TIMEOUT_MS);
            }
        }
        return null;
    }

    private static Result extract(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int width = meta.getColumnCount();
        List<SqlResponse.ColumnInfo> columns = new ArrayList<>(width);
        for (int i = 1; i <= width; i++) {
            columns.add(new SqlResponse.ColumnInfo(meta.getColumnLabel(i), meta.getColumnTypeName(i)));
        }
        List<List<Object>> rows = new ArrayList<>();
        long cellText = 0;
        while (rs.next()) {
            List<Object> row = new ArrayList<>(width);
            for (int i = 1; i <= width; i++) {
                Object value = rs.getObject(i);
                cellText += value == null ? 0 : String.valueOf(value).length();
                row.add(value);
            }
            if (cellText > MAX_CELL_TEXT) {
                throw new SqlRejectedException("result too large, add a WHERE or aggregate", "result-too-large");
            }
            rows.add(row);
        }
        return new Result(columns, rows);
    }
}
