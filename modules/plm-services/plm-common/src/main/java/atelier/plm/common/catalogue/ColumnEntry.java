// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.catalogue;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Catalogue view of one persistent field and the column it is stored in. {@code unit} is a fixed
 * QUDT unit local name; {@code unitColumn} names the column holding the unit of each row.
 * {@code accepts} is the closed list of values the column takes (a text column's words, or the
 * units a unit column stores) and {@code pattern} the regular expression a whole value must match;
 * both are omitted when the column declares none. {@code readThrough} names the view a structural
 * document column is read through ({@link atelier.plm.common.annotation.ReadThrough}); such a column
 * is documented here and served by neither {@code /tables} nor {@code /sql}.
 */
public record ColumnEntry(
        String field,
        String column,
        String javaType,
        String description,
        String unit,
        String unitColumn,
        String ontologyTerm,
        boolean undescribed,
        boolean unitMissing,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> accepts,
        @JsonInclude(JsonInclude.Include.NON_NULL) String pattern,
        @JsonInclude(JsonInclude.Include.NON_NULL) String readThrough) {
}
