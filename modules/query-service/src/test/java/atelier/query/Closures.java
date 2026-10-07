// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

/**
 * What each PLM's closure table answers, derived from a fixture's own lines: {@code atelier:contains} from every item to
 * every item under it in its site's tree, and from every item to itself. A fixture answered in memory has no table to
 * read, so the tests state the pairs the tables hold.
 */
final class Closures {
    private Closures() {}

    static Model with(Model fixture) {
        Property parent = fixture.createProperty(Atelier.ONT + "parent");
        Property child = fixture.createProperty(Atelier.ONT + "child");
        Property contains = fixture.createProperty(Atelier.ONT + "contains");
        Map<String, Set<String>> children = new HashMap<>();
        Set<String> items = new HashSet<>();
        fixture.listSubjectsWithProperty(RDF.type, fixture.createResource(Atelier.ONT + "Part"))
                .forEachRemaining(p -> items.add(p.getURI()));
        for (Resource line : fixture.listSubjectsWithProperty(RDF.type, fixture.createResource(Atelier.BOM_LINE)).toList()) {
            RDFNode p = line.getProperty(parent).getObject();
            RDFNode c = line.getProperty(child).getObject();
            children.computeIfAbsent(p.asResource().getURI(), k -> new HashSet<>()).add(c.asResource().getURI());
            items.add(p.asResource().getURI());
            items.add(c.asResource().getURI());
        }
        for (String item : items) {
            Set<String> seen = new HashSet<>();
            Deque<String> todo = new ArrayDeque<>(Set.of(item));
            while (!todo.isEmpty()) {
                String next = todo.pop();
                if (!seen.add(next)) continue;
                fixture.add(fixture.createResource(item), contains, fixture.createResource(next));
                todo.addAll(children.getOrDefault(next, Set.of()));
            }
        }
        return fixture;
    }
}
