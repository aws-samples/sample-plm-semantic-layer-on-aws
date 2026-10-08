// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mapping;

import atelier.query.Atelier;
import java.util.HashMap;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

/**
 * The canonical lifecycle state of a part, read from the {@code atelier:Lifecycle} concept scheme: a
 * PLM's own word (a part's {@code atelier:lifecycleLabel}) resolves to the {@code skos:prefLabel} of the
 * concept that carries the word as a {@code skos:altLabel}. The words are compared without their
 * language tag, since a PLM states its word as a plain literal.
 */
public final class Lifecycles {
    private final Map<String, String> stateByWord = new HashMap<>();

    public Lifecycles(Model scheme) {
        Property inScheme = scheme.createProperty(Atelier.SKOS + "inScheme");
        Property prefLabel = scheme.createProperty(Atelier.SKOS + "prefLabel");
        Property altLabel = scheme.createProperty(Atelier.SKOS + "altLabel");
        for (Resource concept : scheme.listSubjectsWithProperty(inScheme, scheme.createResource(Atelier.LIFECYCLE)).toList()) {
            String state = concept.getProperty(prefLabel).getString();
            concept.listProperties(altLabel).forEachRemaining(s -> stateByWord.put(s.getLiteral().getLexicalForm(), state));
        }
    }

    /** The canonical state (WORKING, RELEASED, BLOCKED, SUPERSEDED) of a PLM's lifecycle word, or null when no concept names it. */
    public String state(String word) {
        return word == null ? null : stateByWord.get(word);
    }
}
