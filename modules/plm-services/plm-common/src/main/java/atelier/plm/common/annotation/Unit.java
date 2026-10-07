// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * QUDT unit (http://qudt.org/vocab/unit/) of a numeric column. Declare either a fixed unit local
 * name, e.g. {@code @Unit("MilliM")}, or the column that holds the unit local name per row, e.g.
 * {@code @Unit(column = "pos_uom")}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Unit {

    /** Fixed QUDT unit local name, e.g. "MilliM" or "IN"; empty when the unit is read per row. */
    String value() default "";

    /** Column holding the QUDT unit local name of each row; empty when the unit is fixed. */
    String column() default "";

    /**
     * For a per-row unit, the QUDT unit local names the site stores in {@link #column()}: the values
     * a correction of that column may set.
     */
    String[] stores() default {};
}
