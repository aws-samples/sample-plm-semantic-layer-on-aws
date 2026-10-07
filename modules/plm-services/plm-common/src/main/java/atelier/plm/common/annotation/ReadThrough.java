// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A structural document column, read as rows through the named view: a document on a parent row
 * (a bill of materials listing its children, say) can name rows the export-control filter hides,
 * and the filter decides per row, never inside a value. The catalogue documents the column and
 * names the view; {@code /tables} and {@code /sql} never return the column or accept a reference to it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ReadThrough {
    /** The view whose rows are the document's lines, filtered row by row. */
    String value();
}
