// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.policy;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The export-control tags of this service's parts, read from {@code part_tag} in the Atelier core
 * database over its own read-only connection: a part is visible to a clearance iff its tag's
 * {@code releasable_to} is one of the clearance's tokens, so an untagged part is visible to no one.
 * An unconfigured store (no core connection) sees no part; the {@code /tables} policy then denies
 * every part and feature row and names the cause, {@value #NOT_CONFIGURED}, in the response.
 * The same connection answers whether the core change log ({@code demo_change}) recorded a value
 * as the before of a correction of one of this PLM's cells, which the demo reset may restore.
 * The SQL is parameterised: the PLM code, keys and tokens are bound, never interpolated.
 */
public final class PartTagStore implements Closeable {

    public static final String NOT_CONFIGURED = "core tag store not configured";

    private final String plm;
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    /** A store over the core database's read-only connection, for the parts of the PLM {@code plm} (lower case in the tags). */
    public PartTagStore(String plm, DataSource dataSource) {
        this.plm = plm.toLowerCase(Locale.ROOT);
        this.dataSource = dataSource;
        this.jdbc = dataSource == null ? null : new JdbcTemplate(dataSource);
    }

    public static PartTagStore unconfigured(String plm) {
        return new PartTagStore(plm, null);
    }

    public boolean isConfigured() {
        return jdbc != null;
    }

    /** The native keys among {@code nativeKeys} whose tag releases the part to the clearance; none when unconfigured. */
    public List<String> visible(Collection<String> nativeKeys, Clearance clearance) {
        if (jdbc == null || nativeKeys.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT native_key FROM part_tag WHERE plm = ? AND native_key IN (" + placeholders(nativeKeys.size())
                + ") AND releasable_to IN (" + placeholders(clearance.releasable().size()) + ")";
        List<Object> args = new ArrayList<>();
        args.add(plm);
        args.addAll(nativeKeys);
        args.addAll(clearance.releasable());
        return jdbc.queryForList(sql, String.class, args.toArray());
    }

    /** Every native key of this PLM whose tag releases the part to the clearance, sorted; none when unconfigured. */
    public List<String> visibleKeys(Clearance clearance) {
        if (jdbc == null) {
            return List.of();
        }
        String sql = "SELECT native_key FROM part_tag WHERE plm = ? AND releasable_to IN ("
                + placeholders(clearance.releasable().size()) + ") ORDER BY native_key";
        List<Object> args = new ArrayList<>();
        args.add(plm);
        args.addAll(clearance.releasable());
        return jdbc.queryForList(sql, String.class, args.toArray());
    }

    /**
     * Whether {@code demo_change} holds a correction of this PLM's {@code table}/{@code key}/{@code column}
     * whose before value is {@code value}; false when unconfigured.
     */
    public boolean loggedBefore(String table, String key, String column, String value) {
        if (jdbc == null) {
            return false;
        }
        Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM demo_change WHERE plm = ? AND table_name = ? AND row_key = ?"
                + " AND column_name = ? AND before = ?", Integer.class, plm, table, key, column, value);
        return found != null && found > 0;
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    /** Closes the connection pool when the datasource owns one (Spring calls this on shutdown). */
    @Override
    public void close() {
        if (dataSource instanceof Closeable pool) {
            try {
                pool.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
