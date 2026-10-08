// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.tables;

import atelier.plm.common.annotation.OntologyClass;
import atelier.plm.common.catalogue.AnnotationCatalogue;
import atelier.plm.common.policy.Clearance;
import atelier.plm.common.policy.PartTagStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

/**
 * The export-control condition the rows of one table must meet, decided from this service's own
 * entity annotations and evaluated against the part tags of the Atelier core database (the PLM part
 * tables carry no classification): a part table (an entity of class {@code atelier:Part} whose IRI this
 * service mints) keeps the requested keys whose tag releases the part to the clearance; a feature
 * table (an entity with a field mapped {@code atelier:onPart}) keeps the rows whose referenced part is
 * released, as does a bill-of-materials line table (an entity with a field mapped {@code atelier:child}), whose
 * line is visible when its child is, a bill-of-materials closure table (an entity with a field mapped
 * {@code atelier:contains}), whose pair is visible when the contained item is, an external-reference table (an entity with a field mapped
 * {@code atelier:fromPart}), whose reference is visible when the part that makes it is, and a supplier offer table
 * (an entity with a field mapped {@code atelier:offersPart}), whose offer is visible when its part is; the tag table
 * itself (an entity with a field mapped {@code atelier:releasableTo}) keeps
 * the rows whose audience the clearance holds, so the classification of a hidden part is as hidden
 * as the part; any other table is served unfiltered. Table and column names come from the entity
 * classes, never from the request; keys and
 * releasability tokens are bound as parameters. Without a configured tag store no row of a part or
 * feature table is visible.
 */
public sealed interface ReleasabilityFilter {

    String PART = "atelier:Part";
    String ON_PART = "atelier:onPart";
    String CHILD = "atelier:child";
    String FROM_PART = "atelier:fromPart";
    String CONTAINS = "atelier:contains";
    String OFFERS_PART = "atelier:offersPart";
    String RELEASABLE_TO = "atelier:releasableTo";

    /**
     * The SQL condition the rows must meet ({@code condition}, empty when the table is unfiltered),
     * the values bound to its placeholders, and, when the policy could not be evaluated, the reason
     * every filtered row is withheld ({@code null} otherwise).
     */
    public record Clause(String condition, List<String> args, String error) {

        static Clause none() {
            return new Clause("", List.of(), null);
        }

        /** Restricts {@code column} to the visible keys; no visible key matches no row. */
        static Clause on(String column, List<String> visible) {
            if (visible.isEmpty()) {
                return new Clause("1 = 0", List.of(), null);
            }
            return new Clause(column + " IN (" + NativeTable.placeholders(visible.size()) + ")", visible, null);
        }

        static Clause denied(String error) {
            return new Clause("1 = 0", List.of(), error);
        }

        /** The condition as appended to an existing WHERE clause: empty, or {@code " AND <condition>"}. */
        public String sql() {
            return condition.isEmpty() ? "" : " AND " + condition;
        }
    }

    /** The clause for the rows with the requested keys; {@code jdbc} reads this service's own database. */
    Clause clause(JdbcTemplate jdbc, List<String> keys, Clearance clearance, PartTagStore tags);

    /**
     * The clause for every row of the table, with no key list to narrow it: the visible parts are
     * all the parts of this PLM the clearance may see. This is the condition the SQL endpoint wraps
     * a table reference in.
     */
    Clause forAllRows(Clearance clearance, PartTagStore tags);

    static ReleasabilityFilter of(Class<?> type) {
        OntologyClass ontologyClass = type.getAnnotation(OntologyClass.class);
        if (ontologyClass != null && PART.equals(ontologyClass.value()) && ontologyClass.subject().isEmpty()) {
            return new OnOwnKey(AnnotationCatalogue.keyColumn(type));
        }
        Optional<Field> onPart = AnnotationCatalogue.fieldMapped(type, ON_PART).or(() -> AnnotationCatalogue.fieldMapped(type, CHILD))
                .or(() -> AnnotationCatalogue.fieldMapped(type, FROM_PART))
                .or(() -> AnnotationCatalogue.fieldMapped(type, CONTAINS))
                .or(() -> AnnotationCatalogue.fieldMapped(type, OFFERS_PART));
        if (onPart.isPresent()) {
            return new ThroughPart(AnnotationCatalogue.tableName(type), AnnotationCatalogue.keyColumn(type),
                    AnnotationCatalogue.columnName(onPart.get()));
        }
        Optional<Field> releasableTo = AnnotationCatalogue.fieldMapped(type, RELEASABLE_TO);
        if (releasableTo.isPresent()) {
            return new OnAudience(AnnotationCatalogue.columnName(releasableTo.get()));
        }
        return new Unfiltered();
    }

    /** The tag table: rows whose audience is one the clearance holds. */
    record OnAudience(String column) implements ReleasabilityFilter {
        @Override
        public Clause clause(JdbcTemplate jdbc, List<String> keys, Clearance clearance, PartTagStore tags) {
            return Clause.on(column, clearance.releasable());
        }

        @Override
        public Clause forAllRows(Clearance clearance, PartTagStore tags) {
            return Clause.on(column, clearance.releasable());
        }
    }

    /** A part table: the requested keys whose tag releases the part. */
    record OnOwnKey(String keyColumn) implements ReleasabilityFilter {
        @Override
        public Clause clause(JdbcTemplate jdbc, List<String> keys, Clearance clearance, PartTagStore tags) {
            if (!tags.isConfigured()) {
                return Clause.denied(PartTagStore.NOT_CONFIGURED);
            }
            return Clause.on(keyColumn, tags.visible(keys, clearance));
        }

        @Override
        public Clause forAllRows(Clearance clearance, PartTagStore tags) {
            if (!tags.isConfigured()) {
                return Clause.denied(PartTagStore.NOT_CONFIGURED);
            }
            return Clause.on(keyColumn, tags.visibleKeys(clearance));
        }
    }

    /** A feature table: rows whose part, named by the FK column, is released. */
    record ThroughPart(String table, String keyColumn, String fkColumn) implements ReleasabilityFilter {
        @Override
        public Clause clause(JdbcTemplate jdbc, List<String> keys, Clearance clearance, PartTagStore tags) {
            if (!tags.isConfigured()) {
                return Clause.denied(PartTagStore.NOT_CONFIGURED);
            }
            List<String> parts = jdbc.queryForList("SELECT DISTINCT " + fkColumn + " FROM " + table
                    + " WHERE " + keyColumn + " IN (" + NativeTable.placeholders(keys.size()) + ")", String.class, keys.toArray());
            return Clause.on(fkColumn, tags.visible(parts, clearance));
        }

        @Override
        public Clause forAllRows(Clearance clearance, PartTagStore tags) {
            if (!tags.isConfigured()) {
                return Clause.denied(PartTagStore.NOT_CONFIGURED);
            }
            return Clause.on(fkColumn, tags.visibleKeys(clearance));
        }
    }

    /** A table with no export-control mapping. */
    record Unfiltered() implements ReleasabilityFilter {
        @Override
        public Clause clause(JdbcTemplate jdbc, List<String> keys, Clearance clearance, PartTagStore tags) {
            return Clause.none();
        }

        @Override
        public Clause forAllRows(Clearance clearance, PartTagStore tags) {
            return Clause.none();
        }
    }
}
