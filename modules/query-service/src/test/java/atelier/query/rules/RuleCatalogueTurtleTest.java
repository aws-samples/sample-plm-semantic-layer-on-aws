// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.Test;

/**
 * The Turtle of a shape writes each SPARQL string as a long string, whatever its length: the conversion costs no stack
 * per character, so a long query cannot overflow the thread that describes the rules.
 */
class RuleCatalogueTurtleTest {
    private static final String SH = "http://www.w3.org/ns/shacl#";

    private static Graph shapeWith(String query) {
        Graph graph = GraphFactory.createDefaultGraph();
        graph.add(NodeFactory.createURI("https://example.com/atelier/shapes#Long"), NodeFactory.createURI(SH + "select"),
                NodeFactory.createLiteralString(query));
        graph.add(NodeFactory.createURI("https://example.com/atelier/shapes#Long"), NodeFactory.createURI(SH + "message"),
                NodeFactory.createLiteralString("one \"quoted\" line, a back\\slash"));
        return graph;
    }

    @Test
    void aQueryOfAHundredThousandCharactersIsWrittenAsALongString() {
        String line = "  ?part atelier:label ?name ; atelier:gearModule ?q . FILTER (?name != \"x\")\n";
        String query = line.repeat(100_000 / line.length() + 1);
        String turtle = RuleCatalogue.turtle(shapeWith(query));
        assertThat(turtle).contains("\"\"\"" + query + "\"\"\"");
    }

    @Test
    void aStringWithoutALineBreakStaysAShortStringWithItsEscapes() {
        String turtle = RuleCatalogue.turtle(shapeWith("SELECT $this WHERE { }\n"));
        assertThat(turtle).contains("\"one \\\"quoted\\\" line, a back\\\\slash\"").contains("\"\"\"SELECT $this WHERE { }\n\"\"\"");
    }
}
