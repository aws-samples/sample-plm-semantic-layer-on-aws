// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.sql;

import atelier.plm.common.catalogue.EntityEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The names a column reference can resolve to at one point of a statement: the FROM items of the
 * enclosing SELECT (base tables under their alias, CTEs and derived tables under theirs), the
 * output aliases of its select list, and, through {@code parent}, the same for every enclosing
 * SELECT (a correlated subquery sees its outer tables). Names are compared as PostgreSQL folds
 * them: unquoted identifiers lower case, quoted ones as written.
 */
final class CatalogueScope {

    /**
     * One resolvable name. {@code entry} is the catalogue entity behind a base table (null for a
     * CTE or derived table); {@code columns} are the exposed column names, null when they cannot
     * be known (a select list with an unaliased expression), in which case any column is accepted
     * and left to the database to check; {@code cte} marks a name declared in a WITH clause, which
     * a later FROM resolves before the catalogue.
     */
    record Source(String name, EntityEntry entry, Set<String> columns, boolean cte) {

        boolean opaque() {
            return columns == null;
        }
    }

    private final CatalogueScope parent;
    private final Map<String, Source> sources = new LinkedHashMap<>();
    private final Set<String> outputAliases = new HashSet<>();

    CatalogueScope(CatalogueScope parent) {
        this.parent = parent;
    }

    CatalogueScope child() {
        return new CatalogueScope(this);
    }

    void add(Source source) {
        sources.put(source.name(), source);
    }

    void addOutputAlias(String alias) {
        outputAliases.add(alias);
    }

    /** The FROM items of this SELECT alone, in FROM order (what {@code *} expands to). */
    Collection<Source> own() {
        return sources.values();
    }

    boolean hasOutputAlias(String name) {
        return outputAliases.contains(name);
    }

    /** The source of that name in this SELECT or the nearest enclosing one. */
    Source find(String name) {
        for (CatalogueScope scope = this; scope != null; scope = scope.parent) {
            Source source = scope.sources.get(name);
            if (source != null) {
                return source;
            }
        }
        return null;
    }

    /** The CTE of that name declared in this SELECT or an enclosing one. */
    Source findCte(String name) {
        for (CatalogueScope scope = this; scope != null; scope = scope.parent) {
            Source source = scope.sources.get(name);
            if (source != null && source.cte()) {
                return source;
            }
        }
        return null;
    }

    /** The scopes from this one outwards. */
    List<CatalogueScope> chain() {
        List<CatalogueScope> chain = new ArrayList<>();
        for (CatalogueScope scope = this; scope != null; scope = scope.parent) {
            chain.add(scope);
        }
        return chain;
    }

    /** Every name resolvable here, innermost first. */
    List<String> names() {
        List<String> names = new ArrayList<>();
        chain().forEach(scope -> names.addAll(scope.sources.keySet()));
        return names;
    }

    /** The up to three columns resolvable here closest to {@code name}, as {@code source.column}. */
    List<String> closestColumns(String name) {
        record Candidate(String qualified, String column) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (CatalogueScope scope : chain()) {
            for (Source source : scope.sources.values()) {
                if (!source.opaque()) {
                    source.columns().forEach(column -> candidates.add(new Candidate(source.name() + "." + column, column)));
                }
            }
        }
        return candidates.stream()
                .sorted(Comparator.comparingInt((Candidate c) -> levenshtein(name, c.column())).thenComparing(Candidate::qualified))
                .map(Candidate::qualified)
                .distinct()
                .limit(3)
                .toList();
    }

    /** An identifier as PostgreSQL resolves it: quoted as written, otherwise folded to lower case. */
    static String ident(String raw) {
        if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            return raw.substring(1, raw.length() - 1).replace("\"\"", "\"");
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    /** The up to three candidates closest to {@code name} by Levenshtein distance, ties alphabetically. */
    static List<String> closest(String name, Collection<String> candidates) {
        return candidates.stream()
                .distinct()
                .sorted(Comparator.comparingInt((String c) -> levenshtein(name, c)).thenComparing(c -> c))
                .limit(3)
                .toList();
    }

    static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
