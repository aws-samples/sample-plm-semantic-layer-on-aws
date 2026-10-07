// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.sql;

import atelier.plm.common.policy.Clearance;
import atelier.plm.common.policy.PartTagStore;
import atelier.plm.common.tables.NativeTable;
import atelier.plm.common.tables.ReleasabilityFilter;
import atelier.plm.common.tables.TablePolicy;
import jakarta.persistence.metamodel.Metamodel;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.Select;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the export-control policy to a validated statement by rewriting, in place, every
 * reference to a part or feature table into the derived table {@code /{plm}/tables/{table}} reads
 * from: {@code (SELECT <projection> FROM t WHERE <key or part column> IN (?, ...)) AS <alias>}, the
 * placeholders bound to the parts the clearance may see, or {@code WHERE 1 = 0} when it may see
 * none (or the tag store is not configured). The projection is the one {@code /tables} reads, so a
 * {@link atelier.plm.common.annotation.ReadThrough} document is absent from the derived table and
 * from anything built on it, a {@code *} or a whole-row value alike; a table holding one is rewritten
 * even without an export-control mapping. The alias is kept, so every column reference of the
 * statement still resolves; any other table is left as written.
 */
final class ReleasabilityRewrite {

    /** The bound values of the rewritten statement, in placeholder order, and the clearance echoed to the caller. */
    record Result(List<Object> args, TablePolicy policy) {
    }

    private ReleasabilityRewrite() {
    }

    static Result apply(List<CatalogueWalker.TableSlot> slots, Metamodel metamodel, Clearance clearance, PartTagStore tags) {
        List<Object> args = new ArrayList<>();
        String error = null;
        for (CatalogueWalker.TableSlot slot : slots) {
            NativeTable table = NativeTable.of(metamodel, slot.entry().table())
                    .orElseThrow(() -> new IllegalStateException("catalogue table without entity: " + slot.entry().table()));
            ReleasabilityFilter.Clause clause = table.filter().forAllRows(clearance, tags);
            if (clause.condition().isEmpty() && "*".equals(table.projection())) {
                continue;
            }
            Alias alias = slot.table().getAlias() != null ? slot.table().getAlias() : new Alias(slot.table().getName(), true);
            slot.replace().accept(derivedTable(table.projection(), table.name(), clause.condition()).withAlias(alias));
            // Every filtered table of one statement is bound to the same visible-key list, so the
            // order the derived tables render in does not matter for the placeholder order.
            args.addAll(clause.args());
            if (error == null) {
                error = clause.error();
            }
        }
        return new Result(args, new TablePolicy(clearance.profile(), clearance.releasable(), error));
    }

    /**
     * {@code (SELECT projection FROM table WHERE condition)}, without the WHERE for an empty condition; projection,
     * table and condition come from the entity classes, never from the request.
     */
    private static ParenthesedSelect derivedTable(String projection, String table, String condition) {
        try {
            Select inner = (Select) CCJSqlParserUtil.parse("SELECT " + projection + " FROM " + table + (condition.isEmpty() ? "" : " WHERE " + condition));
            return new ParenthesedSelect().withSelect(inner);
        } catch (JSQLParserException e) {
            throw new IllegalStateException("cannot build the filtered view of " + table, e);
        }
    }
}
