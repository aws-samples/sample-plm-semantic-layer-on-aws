// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import atelier.plm.common.annotation.Accepts;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.Unit;
import atelier.plm.common.catalogue.AnnotationCatalogue;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;

/**
 * The one column of one native table that {@code POST /{plm}/demo/update} may change, decided from
 * this service's entity annotations: the table is an entity's {@code @Table}, the column one of its
 * persistent fields, neither the row's key ({@code @Id}, or the column ending the subject IRI) nor a
 * foreign key ({@code @ManyToOne} / {@code @OneToOne} / {@code @JoinColumn}, or mapped to a term
 * linking rows, see {@link #FOREIGN_KEY_TERMS}), and of one of four kinds:
 * <ul>
 *   <li>a measure: a numeric Java type carrying an {@code @Maps} term, so the corrected value is visible through Ontop;</li>
 *   <li>a unit column, named by some field's {@code @Unit(column = ...)}: the value is one of the units those fields
 *       declare the site {@code stores}, and a unit column whose fields declare none is not correctable;</li>
 *   <li>a text column: a String mapped to one of {@link #TEXT_TERMS}; one mapped to {@link #CONSTRAINED_TERMS} is
 *       correctable only with an {@code @Accepts}, whose words or form the value must take, the others take any
 *       non-blank text;</li>
 *   <li>a flag: a Boolean mapped to one of {@link #FLAG_TERMS}.</li>
 * </ul>
 * Every name held here comes from the entity class, never from the request, so the SQL built on it
 * contains nothing the caller wrote. JSON null clears the cell whatever the kind; a NOT NULL column
 * refuses it in the database.
 */
public record UpdatableColumn(String table, String keyColumn, Class<?> keyType, String column, Kind kind, Class<?> javaType,
                              List<String> vocabulary, String form) {

    /** What a column holds, which decides the values a correction may set. */
    public enum Kind { MEASURE, UNIT, TEXT, FLAG }

    /** Terms whose column holds another row's key. */
    static final Set<String> FOREIGN_KEY_TERMS = Set.of("atelier:onPart", "atelier:fromPart", "atelier:offersPart", "atelier:fromSupplier",
            "atelier:parent", "atelier:child", "atelier:contains");

    /** Terms of the text columns a correction may set. */
    static final Set<String> TEXT_TERMS = Set.of("atelier:connectorType", "atelier:fastenerStandard", "atelier:couplingStandard",
            "atelier:fluid", "atelier:lifecycleLabel", "atelier:revision", "atelier:expectedRevision", "atelier:remoteUrn");

    /** Text terms whose values the rules compare against a site's words or a form, so an unconstrained correction is refused. */
    static final Set<String> CONSTRAINED_TERMS = Set.of("atelier:lifecycleLabel", "atelier:revision", "atelier:expectedRevision",
            "atelier:remoteUrn");

    /** Terms of the flags a correction may set. */
    static final Set<String> FLAG_TERMS = Set.of("atelier:preferred");

    public static UpdatableColumn of(Metamodel metamodel, String table, String column) {
        Class<?> type = metamodel.getEntities().stream()
                .map(ManagedType::getJavaType)
                .filter(t -> AnnotationCatalogue.tableName(t).equals(table))
                .findFirst()
                .orElseThrow(() -> reject("unknown table " + table, "unknown-table"));
        Field field = AnnotationCatalogue.persistentField(type, column)
                .orElseThrow(() -> reject("unknown column " + column + " of " + table, "unknown-column"));
        String tableName = AnnotationCatalogue.tableName(type);
        String keyColumn = AnnotationCatalogue.keyColumn(type);
        Class<?> keyType = AnnotationCatalogue.persistentField(type, keyColumn).<Class<?>>map(Field::getType).orElse(String.class);
        String columnName = AnnotationCatalogue.columnName(field);
        Maps maps = field.getAnnotation(Maps.class);
        String term = maps == null ? "" : maps.value();
        Class<?> javaType = field.getType();

        if (field.isAnnotationPresent(Id.class) || column.equals(keyColumn)) {
            throw reject(column + " is the key column of " + table, "key-column");
        }
        if (field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class)
                || field.isAnnotationPresent(JoinColumn.class) || FOREIGN_KEY_TERMS.contains(term)) {
            throw reject(column + " is a foreign key of " + table, "foreign-key");
        }
        if (isUnitColumn(type, columnName)) {
            List<String> units = AnnotationCatalogue.storedUnits(type, columnName);
            if (units.isEmpty()) {
                throw reject(column + " of " + table + " is a unit column that declares no stored unit", "not-correctable");
            }
            return new UpdatableColumn(tableName, keyColumn, keyType, columnName, Kind.UNIT, javaType, units, null);
        }
        if (maps != null && AnnotationCatalogue.isNumeric(javaType)) {
            return new UpdatableColumn(tableName, keyColumn, keyType, columnName, Kind.MEASURE, javaType, null, null);
        }
        if (javaType == String.class && TEXT_TERMS.contains(term)) {
            Accepts accepts = field.getAnnotation(Accepts.class);
            if (accepts == null && CONSTRAINED_TERMS.contains(term)) {
                throw reject(column + " of " + table + " declares no accepted values", "not-correctable");
            }
            List<String> words = accepts == null || accepts.value().length == 0 ? null : List.of(accepts.value());
            String form = accepts == null || accepts.pattern().isEmpty() ? null : accepts.pattern();
            return new UpdatableColumn(tableName, keyColumn, keyType, columnName, Kind.TEXT, javaType, words, form);
        }
        if ((javaType == Boolean.class || javaType == boolean.class) && FLAG_TERMS.contains(term)) {
            return new UpdatableColumn(tableName, keyColumn, keyType, columnName, Kind.FLAG, javaType, null, null);
        }
        throw reject(column + " of " + table + " is not correctable", "not-correctable");
    }

    private static boolean isUnitColumn(Class<?> type, String column) {
        for (Field field : type.getDeclaredFields()) {
            Unit unit = field.getAnnotation(Unit.class);
            if (unit != null && unit.column().equals(column)) {
                return true;
            }
        }
        return false;
    }

    /** The row key as the key field's Java type: a text key as given, a numeric key converted (400 {@code bad-key} when it does not convert). */
    public Object key(String key) {
        if (!AnnotationCatalogue.isNumeric(keyType)) {
            return key;
        }
        try {
            return number(new BigDecimal(key.trim()), keyType);
        } catch (NumberFormatException | ArithmeticException e) {
            throw reject(keyColumn + " of " + table + " takes a " + keyType.getSimpleName() + " key, not " + key, "bad-key");
        }
    }

    /**
     * The requested value as the column's Java type, null for JSON null: a measure takes a number or a
     * number in a string (an integer column refuses a fraction), a flag a boolean or "true"/"false", a
     * text or unit column a string, checked against the column's words or form.
     */
    public Object coerce(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return switch (kind) {
            case MEASURE -> measure(value);
            case FLAG -> flag(value);
            case TEXT, UNIT -> text(value);
        };
    }

    /**
     * The requested value as {@link #coerce(JsonNode)} gives it, except that a text or unit column
     * takes any non-blank text, inside its words and form or not: the value the change log recorded
     * as the cell's before, which the officer's reset restores.
     */
    public Object restore(JsonNode value) {
        if (value == null || value.isNull() || kind == Kind.MEASURE || kind == Kind.FLAG) {
            return coerce(value);
        }
        if (!value.isTextual() || value.asText().isBlank()) {
            throw reject(column + " takes a non-blank text, not " + value, "value-not-text");
        }
        return value.asText();
    }

    private Object measure(JsonNode value) {
        BigDecimal number;
        if (value.isNumber()) {
            number = value.decimalValue();
        } else if (value.isTextual()) {
            try {
                number = new BigDecimal(value.asText().trim());
            } catch (NumberFormatException e) {
                throw reject(column + " takes a number, not " + value.asText(), "value-not-a-number");
            }
        } else {
            throw reject(column + " takes a number, not " + value, "value-not-a-number");
        }
        try {
            return number(number, javaType);
        } catch (ArithmeticException e) {
            throw reject(column + " takes an integer, not " + number, "value-not-integer");
        }
    }

    private static Object number(BigDecimal value, Class<?> type) {
        if (type == BigDecimal.class) {
            return value;
        }
        if (type == Integer.class || type == int.class) {
            return value.intValueExact();
        }
        if (type == Long.class || type == long.class) {
            return value.longValueExact();
        }
        if (type == Short.class || type == short.class) {
            return value.shortValueExact();
        }
        if (type == BigInteger.class) {
            return value.toBigIntegerExact();
        }
        if (type == Double.class || type == double.class) {
            return value.doubleValue();
        }
        if (type == Float.class || type == float.class) {
            return value.floatValue();
        }
        throw reject("unsupported numeric type " + type.getSimpleName(), "not-correctable");
    }

    private Object flag(JsonNode value) {
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isTextual() && ("true".equals(value.asText()) || "false".equals(value.asText()))) {
            return Boolean.valueOf(value.asText());
        }
        throw reject(column + " takes true or false, not " + value, "value-not-boolean");
    }

    private Object text(JsonNode value) {
        if (!value.isTextual() || value.asText().isBlank()) {
            throw reject(column + " takes a non-blank text, not " + value, "value-not-text");
        }
        String text = value.asText();
        if (vocabulary != null && !vocabulary.contains(text)) {
            String what = kind == Kind.UNIT ? " takes one of the units " : " takes one of ";
            throw reject(column + " of " + table + what + vocabulary + ", not " + text, "value-not-in-vocabulary");
        }
        if (form != null && !text.matches(form)) {
            throw reject(column + " of " + table + " takes a value of the form " + form + ", not " + text, "value-not-in-form");
        }
        return text;
    }

    private static DemoRejectedException reject(String message, String reason) {
        return new DemoRejectedException(HttpStatus.BAD_REQUEST, message, reason);
    }
}
