// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.catalogue;

import atelier.plm.common.annotation.Accepts;
import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import atelier.plm.common.annotation.ReadThrough;
import atelier.plm.common.annotation.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads the physical mapping (JPA {@code @Table}/{@code @Column}/{@code @JoinColumn}) and the
 * semantic annotations ({@code @OntologyClass}, {@code @Describe}, {@code @Unit}, {@code @Maps})
 * of entity classes into a {@link Catalogue}. It depends only on the classes, not on a running
 * database, so the same model can drive both the REST catalogue and mapping generators.
 */
public final class AnnotationCatalogue {

    static final String POSITION_TERM_PREFIX = "atelier:position";
    static final String BOM_LINE = "atelier:BomLine";
    static final String CHILD = "atelier:child";

    private AnnotationCatalogue() {
    }

    /** Catalogue of every entity registered in the persistence unit's metamodel. */
    public static Catalogue fromMetamodel(String plm, Metamodel metamodel) {
        List<Class<?>> classes = metamodel.getEntities().stream()
                .map(ManagedType::getJavaType)
                .filter(Objects::nonNull)
                .<Class<?>>map(c -> c)
                .toList();
        return fromClasses(plm, classes);
    }

    /** Catalogue of the given entity classes, sorted by entity name for a stable output. */
    public static Catalogue fromClasses(String plm, Collection<Class<?>> entityClasses) {
        List<EntityEntry> entities = entityClasses.stream()
                .map(AnnotationCatalogue::describeEntity)
                .sorted(Comparator.comparing(EntityEntry::entity))
                .toList();
        return new Catalogue(plm, entities);
    }

    /** Catalogue entry of one entity class. */
    public static EntityEntry describeEntity(Class<?> type) {
        String tableName = tableName(type);
        OntologyClass ontologyClass = type.getAnnotation(OntologyClass.class);
        Describe describe = type.getAnnotation(Describe.class);

        List<ColumnEntry> columns = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (isPersistent(field)) {
                columns.add(describeField(field));
            }
        }
        return new EntityEntry(
                type.getSimpleName(),
                tableName,
                ontologyClass == null ? null : ontologyClass.value(),
                describe == null ? null : describe.value(),
                List.copyOf(columns));
    }

    static ColumnEntry describeField(Field field) {
        Describe describe = field.getAnnotation(Describe.class);
        Unit unit = field.getAnnotation(Unit.class);
        Maps maps = field.getAnnotation(Maps.class);
        String term = maps == null ? null : maps.value();
        String fixedUnit = unit == null || unit.value().isEmpty() ? null : unit.value();
        String unitColumn = unit == null || unit.column().isEmpty() ? null : unit.column();

        boolean unitMissing = fixedUnit == null && unitColumn == null
                && isNumeric(field.getType())
                && term != null
                && term.startsWith(POSITION_TERM_PREFIX);

        Accepts accepts = field.getAnnotation(Accepts.class);
        List<String> words = accepts == null || accepts.value().length == 0 ? null : List.of(accepts.value());
        List<String> stored = storedUnits(field.getDeclaringClass(), columnName(field));
        if (words == null && !stored.isEmpty()) {
            words = stored;
        }

        return new ColumnEntry(
                field.getName(),
                columnName(field),
                field.getType().getSimpleName(),
                describe == null ? null : describe.value(),
                fixedUnit,
                unitColumn,
                term,
                describe == null,
                unitMissing,
                words,
                accepts == null || accepts.pattern().isEmpty() ? null : accepts.pattern(),
                field.isAnnotationPresent(ReadThrough.class) ? field.getAnnotation(ReadThrough.class).value() : null);
    }

    /**
     * The columns a reader of the entity's table may select, in declaration order: every persistent
     * column but a {@link ReadThrough} document; empty when the entity has no such document, so its
     * rows are read whole.
     */
    public static List<String> readableColumns(Class<?> type) {
        List<Field> persistent = Arrays.stream(type.getDeclaredFields()).filter(AnnotationCatalogue::isPersistent).toList();
        if (persistent.stream().noneMatch(field -> field.isAnnotationPresent(ReadThrough.class))) {
            return List.of();
        }
        return persistent.stream().filter(field -> !field.isAnnotationPresent(ReadThrough.class)).map(AnnotationCatalogue::columnName).toList();
    }

    /**
     * The distinct QUDT unit local names the fields of the entity naming {@code column} in
     * {@code @Unit(column = ...)} declare it stores, in declaration order; empty when no field names
     * the column or none declares what it stores.
     */
    public static List<String> storedUnits(Class<?> type, String column) {
        return Arrays.stream(type.getDeclaredFields())
                .map(field -> field.getAnnotation(Unit.class))
                .filter(unit -> unit != null && !unit.column().isEmpty() && unit.column().equals(column))
                .flatMap(unit -> Arrays.stream(unit.stores()))
                .distinct()
                .toList();
    }

    private static boolean isPersistent(Field field) {
        int modifiers = field.getModifiers();
        return !Modifier.isStatic(modifiers)
                && !Modifier.isTransient(modifiers)
                && !field.isAnnotationPresent(Transient.class)
                && !field.isSynthetic();
    }

    /** The field of the entity mapped to the given ontology term with {@code @Maps}, if any. */
    public static Optional<Field> fieldMapped(Class<?> type, String term) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Maps.class) && field.getAnnotation(Maps.class).value().equals(term))
                .findFirst();
    }

    /** The persistent field of the entity stored in the given column, if any. */
    public static Optional<Field> persistentField(Class<?> type, String column) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> isPersistent(field) && columnName(field).equals(column))
                .findFirst();
    }

    /** Column name of the entity's {@code @Id} field. */
    public static String idColumn(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Id.class))
                .findFirst()
                .map(AnnotationCatalogue::columnName)
                .orElseThrow(() -> new IllegalStateException(type.getName() + " has no @Id field"));
    }

    /** The explicit subject IRI template of {@code @OntologyClass(subject = ...)}, empty when the subject is minted from the {@code @Id} column. */
    public static Optional<String> subjectTemplate(Class<?> type) {
        OntologyClass ontologyClass = type.getAnnotation(OntologyClass.class);
        return ontologyClass == null || ontologyClass.subject().isEmpty() ? Optional.empty() : Optional.of(ontologyClass.subject());
    }

    /**
     * The column a row of the entity is looked up by, which is the column ending its subject IRI:
     * the last {@code {column}} of an explicit subject template, the column mapped to {@code atelier:child}
     * for a bill-of-materials line ({@code .../bomline/{parent}/{child}}), otherwise the {@code @Id} column.
     */
    public static String keyColumn(Class<?> type) {
        OntologyClass ontologyClass = type.getAnnotation(OntologyClass.class);
        if (ontologyClass != null && BOM_LINE.equals(ontologyClass.value()) && ontologyClass.subject().isEmpty()) {
            return fieldMapped(type, CHILD).map(AnnotationCatalogue::columnName)
                    .orElseThrow(() -> new IllegalStateException(type.getSimpleName() + " maps no column to " + CHILD));
        }
        return subjectTemplate(type)
                .map(template -> {
                    List<String> columns = templateColumns(template);
                    if (columns.isEmpty()) {
                        throw new IllegalStateException(type.getSimpleName() + ": subject template names no column: " + template);
                    }
                    return columns.get(columns.size() - 1);
                })
                .orElseGet(() -> idColumn(type));
    }

    /** The column names an IRI template refers to in curly braces, in template order. */
    public static List<String> templateColumns(String template) {
        List<String> columns = new ArrayList<>();
        int open = template.indexOf('{');
        while (open >= 0) {
            int close = template.indexOf('}', open);
            if (close < 0) {
                throw new IllegalStateException("unbalanced brace in IRI template " + template);
            }
            columns.add(template.substring(open + 1, close));
            open = template.indexOf('{', close);
        }
        return columns;
    }

    /** Table name as declared on the class; JPA's default (the simple class name) when none is declared. */
    public static String tableName(Class<?> type) {
        Table table = type.getAnnotation(Table.class);
        return table != null && !table.name().isEmpty() ? table.name() : type.getSimpleName();
    }

    /** Column name as declared on the field; JPA's default (the field name) when none is declared. */
    public static String columnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column != null && !column.name().isEmpty()) {
            return column.name();
        }
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
        if (joinColumn != null && !joinColumn.name().isEmpty()) {
            return joinColumn.name();
        }
        return field.getName();
    }

    /** A numeric primitive or a {@link Number} subclass. */
    public static boolean isNumeric(Class<?> type) {
        if (type.isPrimitive()) {
            return type != boolean.class && type != char.class;
        }
        return Number.class.isAssignableFrom(type);
    }
}
