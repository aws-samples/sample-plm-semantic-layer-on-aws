// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The values a text column takes: a closed list of words, e.g. a site's lifecycle states, or a
 * form the whole value must match, e.g. {@code @Accepts(pattern = Forms.URN)}. A correction of the
 * column is checked against it, and the catalogue reports it, so a caller proposes a value the
 * owning site would store.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Accepts {

    /** The words the column takes, in the site's own language; empty when the column takes a form instead. */
    String[] value() default {};

    /** A Java regular expression the whole value must match; empty when the column takes a list of words. */
    String pattern() default "";
}
