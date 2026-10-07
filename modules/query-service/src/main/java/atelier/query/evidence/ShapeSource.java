// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.evidence;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.graph.GraphFactory;

/**
 * The source of one shape in the loaded shapes graph: its own triples and, through blank nodes,
 * the constraints hanging off it (sh:sparql lists with sh:message, sh:prefixes, sh:select).
 */
public final class ShapeSource {
    private ShapeSource() {}

    public static Graph of(Graph shapes, String shapeIri) {
        Graph source = GraphFactory.createDefaultGraph();
        Deque<Node> pending = new ArrayDeque<>();
        Set<Node> visited = new HashSet<>();
        pending.push(NodeFactory.createURI(shapeIri));
        while (!pending.isEmpty()) {
            Node subject = pending.pop();
            if (!visited.add(subject)) continue;
            shapes.find(subject, Node.ANY, Node.ANY).forEachRemaining(t -> {
                source.add(t);
                if (t.getObject().isBlank()) pending.push(t.getObject());
            });
        }
        return source;
    }
}
