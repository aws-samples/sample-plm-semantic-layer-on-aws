// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Ontology class (CURIE, e.g. "atelier:Part") that rows of the annotated entity instantiate. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface OntologyClass {
    String value();

    /**
     * IRI template of the rows' subjects, over column names of the same table in curly braces, e.g.
     * {@code "https://example.com/atelier/{plm}/part/{native_key}"}, for a table whose rows describe
     * subjects minted by another service. Empty (the default) when the subject is this service's own
     * {@code https://example.com/atelier/<plm>/<segment>/{id column}}. Rows with an explicit subject are
     * statements about someone else's subjects: their mapping carries no {@code atelier:ownedBy}, and the
     * {@code /tables} policy does not treat the table as a part table.
     */
    String subject() default "";
}
