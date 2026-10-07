// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.sql;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.AllValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Fetch;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.TableStatement;
import net.sf.jsqlparser.statement.select.Values;

import java.util.Locale;

/**
 * The statement-level rules of the SQL endpoint: the text must parse (JSqlParser, never a regex) as
 * exactly one SELECT (a WITH clause is part of it), and the outermost SELECT returns at most
 * {@value #MAX_ROWS} rows. Anything that is not a SELECT (UPDATE, DDL, COPY, DO, EXPLAIN, SET...)
 * is refused by its kind, and text the parser does not understand is refused as unparsable.
 */
final class SqlStatement {

    static final int MAX_ROWS = 200;
    static final int MAX_LENGTH = 20_000;

    private SqlStatement() {
    }

    /** The single SELECT the text holds. */
    static Select parse(String sql) {
        if (sql.length() > MAX_LENGTH) {
            throw new SqlRejectedException("statement longer than " + MAX_LENGTH + " characters", "too-long");
        }
        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(sql);
        } catch (JSQLParserException e) {
            throw new SqlRejectedException("cannot parse the statement: " + rootMessage(e), "parse-error");
        }
        if (statements.size() != 1) {
            throw new SqlRejectedException("exactly one statement is expected, got " + statements.size(), "multiple-statements");
        }
        Statement statement = statements.get(0);
        if (statement instanceof Values || statement instanceof TableStatement) {
            throw new SqlRejectedException(kind(statement) + " is not a SELECT statement", "not-a-select");
        }
        if (!(statement instanceof Select select)) {
            throw new SqlRejectedException(kind(statement) + " is not a SELECT statement; only one SELECT (WITH allowed) is accepted",
                    "not-a-select");
        }
        return select;
    }

    /**
     * The row-limit rules of one select at any nesting level: LIMIT, OFFSET and FETCH take integer
     * literals only (a subquery there would count rows the profile may not see), and a row count
     * above {@value #MAX_ROWS} is clamped to it. A select without a limit is left without one here;
     * only the outermost gets one added, by {@link #clampLimit}.
     */
    static void checkLimits(Select select) {
        Limit limit = select.getLimit();
        if (limit != null) {
            requireLiteral(limit.getRowCount());
            requireLiteral(limit.getOffset());
            if (limit.getRowCount() instanceof LongValue count && count.getValue() > MAX_ROWS) {
                count.setValue(MAX_ROWS);
            }
        }
        if (select.getOffset() != null) {
            requireLiteral(select.getOffset().getOffset());
        }
        Fetch fetch = select.getFetch();
        if (fetch != null) {
            requireLiteral(fetch.getExpression());
            if (fetch.getExpression() instanceof LongValue count && count.getValue() > MAX_ROWS) {
                count.setValue(MAX_ROWS);
            } else if (fetch.getExpression() == null && fetch.getRowCount() > MAX_ROWS) {
                fetch.setRowCount(MAX_ROWS);
            }
        }
    }

    /** An integer, or the {@code ALL} / {@code NULL} keywords of a LIMIT (no limit); never a parameter or a subquery. */
    private static void requireLiteral(Expression expression) {
        if (expression != null && !(expression instanceof LongValue || expression instanceof AllValue || expression instanceof NullValue)) {
            throw new SqlRejectedException("LIMIT, OFFSET and FETCH must be integer literals", "non-literal-limit");
        }
    }

    /**
     * Makes the outermost select return at most {@value #MAX_ROWS} rows: a missing, larger or
     * {@code ALL} LIMIT becomes {@code LIMIT 200}; a {@code FETCH FIRST n ROWS} clause becomes the
     * equivalent clamped LIMIT.
     */
    static void clampLimit(Select select) {
        long rows = MAX_ROWS;
        Limit limit = select.getLimit();
        if (limit != null && !limit.isLimitAll() && !limit.isLimitNull()
                && limit.getRowCount() instanceof LongValue count) {
            rows = Math.min(rows, count.getValue());
        }
        Fetch fetch = select.getFetch();
        if (fetch != null) {
            rows = Math.min(rows, fetch.getExpression() instanceof LongValue count ? count.getValue() : fetch.getRowCount());
            select.setFetch(null);
        }
        Limit clamped = limit == null ? new Limit() : limit;
        clamped.setLimitAll(false);
        clamped.setLimitNull(false);
        clamped.setRowCount(new LongValue(Math.max(rows, 0)));
        select.setLimit(clamped);
    }

    /** The statement kind as the caller wrote it, e.g. {@code UPDATE}, {@code DROP}, {@code CREATE TABLE}. */
    private static String kind(Statement statement) {
        String simpleName = statement.getClass().getSimpleName();
        return simpleName.replaceAll("([a-z])([A-Z])", "$1 $2").toUpperCase(Locale.ROOT);
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null) {
            return e.getMessage();
        }
        int newline = message.indexOf('\n');
        return newline > 0 ? message.substring(0, newline) : message;
    }
}
