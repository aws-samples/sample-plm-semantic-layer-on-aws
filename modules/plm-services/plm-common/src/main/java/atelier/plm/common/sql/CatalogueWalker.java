// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.sql;

import atelier.plm.common.catalogue.Catalogue;
import atelier.plm.common.catalogue.ColumnEntry;
import atelier.plm.common.catalogue.EntityEntry;
import atelier.plm.common.sql.CatalogueScope.Source;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.AnyComparisonExpression;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.ParenthesedFromItem;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.TableFunction;
import net.sf.jsqlparser.statement.select.Values;
import net.sf.jsqlparser.statement.select.WithItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static atelier.plm.common.sql.CatalogueScope.closest;
import static atelier.plm.common.sql.CatalogueScope.ident;

/**
 * Walks a parsed SELECT once and checks every name against the catalogue of this PLM: each base
 * table must be a catalogue table (a CTE name declared earlier shadows it, as in PostgreSQL), each
 * column must belong to the table its qualifier or scope resolves to ({@code *} expands to the
 * FROM items of its own SELECT), and an unknown name is refused with the closest catalogue names.
 * On the way it records the catalogue columns the statement used and every base-table reference
 * with the slot it sits in, so the releasability rewrite can replace the reference in place.
 * A {@link atelier.plm.common.annotation.ReadThrough} document column is refused wherever it is
 * named (select list, WHERE, ORDER BY, a function argument), the answer naming the view to read
 * instead, and {@code *} does not expand to it.
 * It also refuses what a SELECT may not contain here: system catalogues and other schemas, table
 * functions and VALUES lists, system and introspection functions, casts to the {@code reg*} object
 * identifier types, parameters, non-literal row limits, SELECT INTO, locking and WINDOW clauses,
 * CTEs that are not SELECTs and CTEs named like a catalogue table (they would shadow it and read it
 * unfiltered).
 */
final class CatalogueWalker {

    /** A base-table reference and the setter that replaces it in its FROM or JOIN. */
    record TableSlot(Table table, EntityEntry entry, Consumer<FromItem> replace) {
    }

    private static final Set<String> SYSTEM_FUNCTIONS = Set.of(
            "set_config", "nextval", "setval", "lastval", "currval", "xpath_table", "version",
            "obj_description", "col_description", "shobj_description", "format_type", "row_security_active",
            "query_to_xml", "query_to_xmlschema", "query_to_xml_and_xmlschema", "database_to_xml",
            "database_to_xmlschema", "database_to_xml_and_xmlschema", "table_to_xml", "table_to_xmlschema",
            "table_to_xml_and_xmlschema", "schema_to_xml", "schema_to_xmlschema", "schema_to_xml_and_xmlschema",
            "cursor_to_xml", "cursor_to_xmlschema");
    /** Server, session, privilege and network introspection families, e.g. pg_typeof, has_table_privilege, current_database, inet_server_addr. */
    private static final List<String> SYSTEM_FUNCTION_PREFIXES = List.of(
            "pg_", "has_", "current_", "inet_", "to_reg", "dblink", "lo_", "txid_", "brin_", "gin_");
    /** Object identifier types: a cast to one of them resolves catalogue names the statement may not read. */
    private static final Set<String> OBJECT_IDENTIFIER_TYPES = Set.of(
            "regclass", "regcollation", "regconfig", "regdictionary", "regnamespace", "regoper", "regoperator",
            "regproc", "regprocedure", "regrole", "regtype", "oid");

    private final Map<String, EntityEntry> catalogue = new LinkedHashMap<>();
    private final Map<String, SqlResponse.CatalogueRef> used = new LinkedHashMap<>();
    private final List<TableSlot> baseTables = new ArrayList<>();
    private final Names names = new Names();
    private int anonymousDerived;

    CatalogueWalker(Catalogue plmCatalogue) {
        plmCatalogue.entities().forEach(entity -> catalogue.put(ident(entity.table()), entity));
    }

    /** Validates the whole statement; throws {@link SqlRejectedException} at the first violation. */
    void analyse(Select select) {
        analyse(select, new CatalogueScope(null));
    }

    List<SqlResponse.CatalogueRef> catalogueUsed() {
        return List.copyOf(used.values());
    }

    List<TableSlot> baseTables() {
        return List.copyOf(baseTables);
    }

    /** Validates one SELECT (with its WITH clause) inside {@code outer}; returns its output column names, null when not all are knowable. */
    private List<String> analyse(Select select, CatalogueScope outer) {
        CatalogueScope scope = outer.child();
        if (select.getWithItemsList() != null) {
            // RECURSIVE applies to the whole WITH list in PostgreSQL; the parser records it on the item it follows.
            boolean recursive = select.getWithItemsList().stream().anyMatch(WithItem::isRecursive);
            select.getWithItemsList().forEach(item -> registerCte(item, scope, recursive));
        }
        if (select.getForMode() != null || select.getForUpdate() != null) {
            throw new SqlRejectedException("FOR UPDATE / FOR SHARE locking clauses are not allowed", "locking-clause");
        }
        SqlStatement.checkLimits(select);

        return switch (select) {
            case PlainSelect plain -> analysePlain(plain, scope);
            case SetOperationList ops -> analyseSetOperation(ops, scope);
            case ParenthesedSelect parenthesed -> analyse(parenthesed.getSelect(), scope);
            case Values values -> throw new SqlRejectedException("VALUES lists are not allowed; read the catalogue tables", "values");
            default -> throw new SqlRejectedException(select.getClass().getSimpleName() + " is not supported", "unsupported-select");
        };
    }

    private List<String> analyseSetOperation(SetOperationList ops, CatalogueScope scope) {
        List<String> exposed = null;
        for (int i = 0; i < ops.getSelects().size(); i++) {
            List<String> columns = analyse(ops.getSelects().get(i), scope);
            if (i == 0) {
                exposed = columns;
            }
        }
        if (exposed != null) {
            exposed.forEach(scope::addOutputAlias);
        }
        visitOrderBy(ops.getOrderByElements(), scope);
        return exposed;
    }

    private List<String> analysePlain(PlainSelect plain, CatalogueScope scope) {
        if (plain.getIntoTables() != null && !plain.getIntoTables().isEmpty() || plain.getIntoTempTable() != null) {
            throw new SqlRejectedException("SELECT INTO writes a table and is not allowed", "select-into");
        }
        if (plain.getTop() != null || plain.getSkip() != null || plain.getFirst() != null) {
            throw new SqlRejectedException("TOP, SKIP and FIRST are not PostgreSQL row limits; use LIMIT", "not-postgresql");
        }
        if (plain.getWindowDefinitions() != null && !plain.getWindowDefinitions().isEmpty()) {
            throw new SqlRejectedException("WINDOW clauses are not allowed; write the window inline in OVER (...)", "window-clause");
        }
        registerFrom(plain.getFromItem(), scope, plain::setFromItem);
        if (plain.getJoins() != null) {
            for (Join join : plain.getJoins()) {
                registerFrom(join.getFromItem(), scope, join::setFromItem);
            }
            for (Join join : plain.getJoins()) {
                visitAll(join.getOnExpressions(), scope);
                visitAll(join.getUsingColumns(), scope);
            }
        }
        visit(plain.getWhere(), scope);

        List<String> exposed = new ArrayList<>();
        boolean opaque = false;
        for (SelectItem<?> item : plain.getSelectItems()) {
            Expression expression = item.getExpression();
            expression.accept(names, scope);
            if (item.getAlias() != null) {
                exposed.add(ident(item.getAlias().getName()));
            } else if (expression instanceof AllTableColumns all) {
                Source source = resolveSource(all.getTable(), scope);
                opaque |= source.opaque();
                if (!source.opaque()) {
                    exposed.addAll(readable(source));
                }
            } else if (expression instanceof AllColumns) {
                for (Source source : scope.own()) {
                    opaque |= source.opaque();
                    if (!source.opaque()) {
                        exposed.addAll(readable(source));
                        readable(source).forEach(column -> use(source, column));
                    }
                }
            } else if (expression instanceof Column column) {
                exposed.add(ident(column.getColumnName()));
            } else {
                opaque = true;
            }
        }
        exposed.forEach(scope::addOutputAlias);

        if (plain.getDistinct() != null && plain.getDistinct().getOnSelectItems() != null) {
            plain.getDistinct().getOnSelectItems().forEach(item -> item.getExpression().accept(names, scope));
        }
        if (plain.getGroupBy() != null) {
            visitAll(plain.getGroupBy().getGroupByExpressionList(), scope);
            if (plain.getGroupBy().getGroupingSets() != null) {
                plain.getGroupBy().getGroupingSets().forEach(set -> visitAll(set, scope));
            }
        }
        visit(plain.getHaving(), scope);
        visitOrderBy(plain.getOrderByElements(), scope);
        return opaque ? null : exposed;
    }

    private void registerCte(WithItem<?> item, CatalogueScope scope, boolean recursive) {
        String name = ident(item.getAlias().getName());
        if (catalogue.containsKey(name)) {
            throw new SqlRejectedException("CTE name shadows a table: WITH " + name + " would hide the catalogue table " + name
                    + " and read it unfiltered; choose another name", "cte-shadows-table");
        }
        if (!(item.getParenthesedStatement() instanceof Select body)) {
            throw new SqlRejectedException("WITH " + name + " is not a SELECT; data-modifying CTEs are not allowed", "write-statement");
        }
        Set<String> declared = null;
        if (item.getWithItemList() != null && !item.getWithItemList().isEmpty()) {
            declared = new LinkedHashSet<>();
            for (SelectItem<?> column : item.getWithItemList()) {
                declared.add(ident(column.getExpression().toString()));
            }
        }
        if (recursive) {
            // A recursive CTE refers to itself in its own body, so the name is known before the body is read.
            scope.add(new Source(name, null, declared, true));
        }
        List<String> exposed = analyse(body, scope);
        scope.add(new Source(name, null, declared != null ? declared : exposed == null ? null : new LinkedHashSet<>(exposed), true));
    }

    private void registerFrom(FromItem item, CatalogueScope scope, Consumer<FromItem> slot) {
        switch (item) {
            case null -> {
            }
            case Table table -> registerTable(table, scope, slot);
            case ParenthesedSelect derived -> registerDerived(derived, scope);
            case ParenthesedFromItem group -> {
                registerFrom(group.getFromItem(), scope, group::setFromItem);
                if (group.getJoins() != null) {
                    for (Join join : group.getJoins()) {
                        registerFrom(join.getFromItem(), scope, join::setFromItem);
                        visitAll(join.getOnExpressions(), scope);
                        visitAll(join.getUsingColumns(), scope);
                    }
                }
            }
            case TableFunction function -> throw new SqlRejectedException(
                    "table functions such as " + function + " are not allowed in FROM; read the catalogue tables", "table-function");
            case Values values -> throw new SqlRejectedException("VALUES lists are not allowed; read the catalogue tables", "values");
            default -> throw new SqlRejectedException(item.getClass().getSimpleName() + " is not supported in FROM", "unsupported-from");
        }
    }

    private void registerTable(Table table, CatalogueScope scope, Consumer<FromItem> slot) {
        String name = ident(table.getName());
        checkSchema(table, name);
        String alias = table.getAlias() == null ? name : ident(table.getAlias().getName());
        Source cte = table.getSchemaName() == null ? scope.findCte(name) : null;
        if (cte != null) {
            scope.add(new Source(alias, null, cte.columns(), false));
            return;
        }
        EntityEntry entry = catalogue.get(name);
        if (entry == null) {
            List<String> candidates = new ArrayList<>(catalogue.keySet());
            candidates.addAll(scope.names());
            throw new SqlRejectedException("unknown table " + name + "; it is not in the catalogue of this PLM", "unknown-table",
                    closest(name, candidates));
        }
        scope.add(new Source(alias, entry, columnsOf(entry), false));
        baseTables.add(new TableSlot(table, entry, slot));
    }

    private void registerDerived(ParenthesedSelect derived, CatalogueScope scope) {
        List<String> exposed = analyse(derived.getSelect(), scope);
        Alias alias = derived.getAlias();
        String name = alias == null ? "$derived" + (++anonymousDerived) : ident(alias.getName());
        Set<String> columns = exposed == null ? null : new LinkedHashSet<>(exposed);
        if (alias != null && alias.getAliasColumns() != null && !alias.getAliasColumns().isEmpty()) {
            columns = new LinkedHashSet<>();
            for (Alias.AliasColumn column : alias.getAliasColumns()) {
                columns.add(ident(column.name));
            }
        }
        scope.add(new Source(name, null, columns, false));
    }

    private static void checkSchema(Table table, String name) {
        String schema = table.getSchemaName() == null ? null : ident(table.getSchemaName());
        if ("pg_catalog".equals(schema) || "information_schema".equals(schema) || name.startsWith("pg_")) {
            throw new SqlRejectedException(table.getFullyQualifiedName() + " is a system catalogue, not a PLM table", "system-catalogue");
        }
        if (schema != null && !"public".equals(schema) || table.getDatabaseName() != null) {
            throw new SqlRejectedException(table.getFullyQualifiedName() + " names a schema other than the PLM's own", "unknown-schema");
        }
    }

    private Source resolveSource(Table qualifier, CatalogueScope scope) {
        String name = ident(qualifier.getName());
        checkSchema(qualifier, name);
        Source source = scope.find(name);
        if (source == null) {
            List<String> candidates = new ArrayList<>(scope.names());
            candidates.addAll(catalogue.keySet());
            throw new SqlRejectedException("unknown table or alias " + name, "unknown-table", closest(name, candidates));
        }
        return source;
    }

    private void resolveColumn(Column column, CatalogueScope scope) {
        String name = ident(column.getColumnName());
        if (column.getTable() != null && column.getTable().getName() != null) {
            Source source = resolveSource(column.getTable(), scope);
            if (!source.opaque() && !source.columns().contains(name)) {
                throw new SqlRejectedException("unknown column " + source.name() + "." + name, "unknown-column",
                        closest(name, source.columns()).stream().map(c -> source.name() + "." + c).toList());
            }
            use(source, name);
            return;
        }
        for (CatalogueScope level : scope.chain()) {
            List<Source> hits = level.own().stream().filter(s -> !s.opaque() && s.columns().contains(name)).toList();
            if (!hits.isEmpty()) {
                hits.forEach(source -> use(source, name));
                return;
            }
            if (level.hasOutputAlias(name) || level.own().stream().anyMatch(Source::opaque)) {
                return;
            }
        }
        throw new SqlRejectedException("unknown column " + name, "unknown-column", scope.closestColumns(name));
    }

    private void use(Source source, String column) {
        if (source.entry() == null) {
            return;
        }
        for (ColumnEntry entry : source.entry().columns()) {
            if (ident(entry.column()).equals(column) && entry.readThrough() != null) {
                throw new SqlRejectedException(source.entry().table() + "." + entry.column() + " is a structural document and is not readable"
                        + " here; read its rows through the view " + entry.readThrough(), "read-through-column", List.of(entry.readThrough()));
            }
            if (ident(entry.column()).equals(column)) {
                used.putIfAbsent(source.entry().table() + "." + entry.column(), new SqlResponse.CatalogueRef(
                        source.entry().table(), entry.column(), entry.unit(), entry.description(), entry.ontologyTerm()));
            }
        }
    }

    /** The columns {@code *} expands to: all of a CTE or derived table, those of a base table but its read-through documents. */
    private static List<String> readable(Source source) {
        if (source.entry() == null) {
            return List.copyOf(source.columns());
        }
        Set<String> documents = new LinkedHashSet<>();
        source.entry().columns().stream().filter(column -> column.readThrough() != null).forEach(column -> documents.add(ident(column.column())));
        return source.columns().stream().filter(column -> !documents.contains(column)).toList();
    }

    private static Set<String> columnsOf(EntityEntry entry) {
        Set<String> columns = new LinkedHashSet<>();
        entry.columns().forEach(column -> columns.add(ident(column.column())));
        return columns;
    }

    private static void checkFunction(Function function) {
        if (function.getMultipartName() != null && function.getMultipartName().size() > 1) {
            throw new SqlRejectedException("schema-qualified function " + function.getName() + " is not allowed", "forbidden-function");
        }
        String name = ident(function.getName());
        if (SYSTEM_FUNCTIONS.contains(name) || SYSTEM_FUNCTION_PREFIXES.stream().anyMatch(name::startsWith)) {
            throw new SqlRejectedException("function " + name + " is a system function and is not allowed", "forbidden-function");
        }
    }

    private static void rejectParameter(Expression expression) {
        if (expression instanceof JdbcParameter || expression instanceof JdbcNamedParameter) {
            throw new SqlRejectedException("parameters are not allowed; write literal values", "parameter");
        }
    }

    private void visit(Expression expression, CatalogueScope scope) {
        if (expression != null) {
            expression.accept(names, scope);
        }
    }

    private void visitAll(Collection<? extends Expression> expressions, CatalogueScope scope) {
        if (expressions != null) {
            expressions.forEach(expression -> visit(expression, scope));
        }
    }

    private void visitOrderBy(List<OrderByElement> elements, CatalogueScope scope) {
        if (elements != null) {
            elements.forEach(element -> visit(element.getExpression(), scope));
        }
    }

    /** Expression walk: resolves columns, refuses parameters and system functions, and analyses subselects in their own scope. */
    private final class Names extends net.sf.jsqlparser.expression.ExpressionVisitorAdapter<Void> {

        @Override
        public <S> Void visit(Column column, S context) {
            resolveColumn(column, (CatalogueScope) context);
            return null;
        }

        @Override
        public <S> Void visit(AllTableColumns all, S context) {
            Source source = resolveSource(all.getTable(), (CatalogueScope) context);
            if (!source.opaque()) {
                readable(source).forEach(column -> use(source, column));
            }
            return null;
        }

        @Override
        public <S> Void visit(Function function, S context) {
            checkFunction(function);
            return super.visit(function, context);
        }

        @Override
        public <S> Void visit(CastExpression cast, S context) {
            String type = cast.getColDataType() == null ? "" : ident(cast.getColDataType().getDataType());
            if (OBJECT_IDENTIFIER_TYPES.contains(type)) {
                throw new SqlRejectedException("casts to " + type + " resolve catalogue objects and are not allowed", "forbidden-cast");
            }
            return super.visit(cast, context);
        }

        @Override
        public <S> Void visit(JdbcParameter parameter, S context) {
            rejectParameter(parameter);
            return null;
        }

        @Override
        public <S> Void visit(JdbcNamedParameter parameter, S context) {
            rejectParameter(parameter);
            return null;
        }

        @Override
        public <S> Void visit(ParenthesedSelect select, S context) {
            analyse(select, (CatalogueScope) context);
            return null;
        }

        @Override
        public <S> Void visit(Select select, S context) {
            analyse(select, (CatalogueScope) context);
            return null;
        }

        @Override
        public <S> Void visit(AnyComparisonExpression any, S context) {
            analyse(any.getSelect(), (CatalogueScope) context);
            return null;
        }

        @Override
        public <S> Void visit(ExpressionList<? extends Expression> list, S context) {
            for (Expression expression : list) {
                expression.accept(this, context);
            }
            return null;
        }
    }
}
