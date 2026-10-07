// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.preview;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A cell's value checked against its site's catalogue (GET /{plm}/catalogue) as the site's own update route checks a
 * correction: the table and column must be the site's; a column listing the words or units it takes takes one of them,
 * one stating a form takes a text of that form; a numeric column takes a number (an integer column no fraction), a
 * flag true or false, any other column a non-blank text. JSON null clears the cell. A refusal names the cell.
 */
final class Vocabulary {
    private static final Set<String> INTEGERS = Set.of("Integer", "int", "Long", "long", "Short", "short", "BigInteger");
    private static final Set<String> DECIMALS = Set.of("BigDecimal", "Double", "double", "Float", "float");
    private static final Set<String> FLAGS = Set.of("Boolean", "boolean");

    private Vocabulary() {}

    /** The value as the column takes it: a BigDecimal, a Boolean, a String, or null to clear the cell. */
    static Object value(JsonNode catalogue, PreviewJson.Cell cell, String where) {
        JsonNode entity = find(catalogue.path("entities"), "table", cell.table());
        if (entity == null) throw refused(where, "the " + cell.plm().toUpperCase() + " catalogue has no table " + cell.table());
        JsonNode column = find(entity.path("columns"), "column", cell.column());
        if (column == null) throw refused(where, "unknown column " + cell.column() + " of " + cell.table());
        JsonNode value = cell.value();
        if (value == null || value.isNull()) return null;
        String name = cell.column() + " of " + cell.table();
        List<String> accepts = words(column.path("accepts"));
        String javaType = column.path("javaType").asText();
        if (accepts != null) {
            if (!value.isTextual() || !accepts.contains(value.asText())) {
                throw refused(where, name + " takes one of " + accepts + ", not " + value);
            }
            return value.asText();
        }
        if (INTEGERS.contains(javaType) || DECIMALS.contains(javaType)) {
            BigDecimal number = number(value);
            if (number == null) throw refused(where, name + " takes a number, not " + value);
            if (INTEGERS.contains(javaType) && number.stripTrailingZeros().scale() > 0) {
                throw refused(where, name + " takes an integer, not " + number.toPlainString());
            }
            return number;
        }
        if (FLAGS.contains(javaType)) {
            if (value.isBoolean()) return value.booleanValue();
            if (value.isTextual() && ("true".equals(value.asText()) || "false".equals(value.asText()))) return Boolean.valueOf(value.asText());
            throw refused(where, name + " takes true or false, not " + value);
        }
        if (!value.isTextual() || value.asText().isBlank()) throw refused(where, name + " takes a non-blank text, not " + value);
        String pattern = column.path("pattern").isTextual() ? column.path("pattern").asText() : null;
        if (pattern != null && !value.asText().matches(pattern)) {
            throw refused(where, name + " takes a value of the form " + pattern + ", not " + value.asText());
        }
        return value.asText();
    }

    static IllegalArgumentException refused(String where, String why) {
        return new IllegalArgumentException(where + ": " + why);
    }

    private static BigDecimal number(JsonNode value) {
        if (value.isNumber()) return value.decimalValue();
        if (!value.isTextual()) return null;
        try {
            return new BigDecimal(value.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static JsonNode find(JsonNode list, String field, String name) {
        for (JsonNode item : list) {
            if (name.equals(item.path(field).asText(null))) return item;
        }
        return null;
    }

    private static List<String> words(JsonNode accepts) {
        if (!accepts.isArray()) return null;
        List<String> words = new ArrayList<>();
        accepts.forEach(w -> words.add(w.asText()));
        return words;
    }
}
