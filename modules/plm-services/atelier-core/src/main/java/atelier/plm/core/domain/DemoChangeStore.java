// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.domain;

import atelier.plm.core.dto.DemoChangeDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code demo_change} table over plain JDBC: appended one PLM request's rows at a time in one
 * transaction, read oldest first by the change list and newest first by the reset, which clears it. The table is not a
 * JPA entity on purpose, so it stays out of the catalogue and of the generated R2RML mapping.
 */
@Component
public class DemoChangeStore {

    private static final String COLUMNS = "id, plm, table_name, row_key, column_name, before, after, actor, purpose, at";

    private static final RowMapper<DemoChangeDto> ROW = (rs, i) -> new DemoChangeDto(
            rs.getLong("id"), rs.getString("plm"), rs.getString("table_name"), rs.getString("row_key"), rs.getString("column_name"),
            rs.getString("before"), rs.getString("after"), rs.getString("actor"), rs.getString("purpose"),
            rs.getObject("at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public DemoChangeStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Appends the changes in order, all or none, and returns them as stored. */
    @Transactional
    public List<DemoChangeDto> append(List<DemoChangeDto> changes) {
        List<DemoChangeDto> stored = new ArrayList<>();
        for (DemoChangeDto change : changes) {
            stored.add(append(change));
        }
        return stored;
    }

    /** Appends the change and returns it as stored, with its id and timestamp. */
    public DemoChangeDto append(DemoChangeDto change) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO demo_change (plm, table_name, row_key, column_name, before, after, actor, purpose) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[] {"id"});
            statement.setString(1, change.plm());
            statement.setString(2, change.table());
            statement.setString(3, change.key());
            statement.setString(4, change.column());
            statement.setString(5, change.before());
            statement.setString(6, change.after());
            statement.setString(7, change.actor());
            statement.setString(8, change.purpose());
            return statement;
        }, key);
        long id = key.getKey().longValue();
        return jdbc.queryForObject("SELECT " + COLUMNS + " FROM demo_change WHERE id = ?", ROW, id);
    }

    /** Every logged change, oldest first. */
    public List<DemoChangeDto> all() {
        return jdbc.query("SELECT " + COLUMNS + " FROM demo_change ORDER BY id", ROW);
    }

    /** Every logged change, newest first: the order the reset undoes them in. */
    public List<DemoChangeDto> newestFirst() {
        return jdbc.query("SELECT " + COLUMNS + " FROM demo_change ORDER BY id DESC", ROW);
    }

    /** Deletes every logged change and returns how many there were. */
    public int clear() {
        return jdbc.update("DELETE FROM demo_change");
    }
}
