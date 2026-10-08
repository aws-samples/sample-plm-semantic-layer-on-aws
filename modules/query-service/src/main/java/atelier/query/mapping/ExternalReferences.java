// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mapping;

import atelier.query.federation.FederatedQueries;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;

/**
 * Where the external references of a model point. The reference rules of ontology/shapes.ttl decide whether a
 * reference is dangling or stale; this class runs the shapes' own URN resolution, verbatim, to name the part a
 * reference points at, so that an answer can show the target and an interface's neighbourhood can carry it.
 */
public final class ExternalReferences {
    /** The rule names of the reference shapes. */
    public static final List<String> RULES = List.of("danglingReference", "staleRevision");

    /**
     * The URN resolution of the reference shapes in ontology/shapes.ttl, character for character: from {@code ?urn} it
     * binds {@code ?target}, the part IRI of a {@code urn:plm:<site>:part:<local id>} key, unbound for any other text.
     */
    public static final String RESOLUTION = """
                    BIND (STR(?urn) AS ?u)
                    BIND (REGEX(?u, "^urn:plm:(de|fr|es|uk):part:.+$") AS ?wellFormed)
                    BIND (REPLACE(?u, "^urn:plm:([a-z]+):part:.*$", "$1") AS ?site)
                    BIND (REPLACE(?u, "^urn:plm:[a-z]+:part:", "") AS ?local)
                    BIND (IF(?site = "uk", REPLACE(?local, "^UK/", "UK-"), ?local) AS ?key)
                    BIND (IF(?wellFormed, IRI(CONCAT("https://example.com/atelier/", ?site, "/part/", ENCODE_FOR_URI(?key))), ?none) AS ?target)
            """;

    private static final String TARGETS = FederatedQueries.PREFIXES + """
            SELECT ?ref ?target WHERE {
              ?ref atelier:fromPart ?part ; atelier:remoteUrn ?urn .
            %s  FILTER (BOUND(?target))
            }
            """.formatted(RESOLUTION);

    private ExternalReferences() {}

    /** The part IRI each reference of the model resolves to, by reference IRI; a reference whose URN is not a key is absent. */
    public static Map<String, String> targets(Model model) {
        Map<String, String> out = new LinkedHashMap<>();
        try (QueryExecution exec = QueryExecutionFactory.create(TARGETS, model)) {
            exec.execSelect().forEachRemaining(row -> out.put(row.getResource("ref").getURI(), row.getResource("target").getURI()));
        }
        return out;
    }
}
