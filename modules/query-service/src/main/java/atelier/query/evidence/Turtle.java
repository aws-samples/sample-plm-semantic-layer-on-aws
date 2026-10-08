// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.evidence;

import atelier.query.Atelier;
import java.io.StringWriter;
import org.apache.jena.graph.Graph;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFWriterRegistry;
import org.apache.jena.riot.RIOT;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.system.PrefixMapFactory;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;

/** Pretty Turtle with the contract prefixes, whatever prefixes the graph itself carries. */
public final class Turtle {
    private static final PrefixMap PREFIXES = PrefixMapFactory.create();

    static {
        PREFIXES.add("atelier", Atelier.ONT);
        PREFIXES.add("qudt", Atelier.QUDT);
        PREFIXES.add("unit", Atelier.UNIT);
        PREFIXES.add("rdfs", RDFS.getURI());
        PREFIXES.add("xsd", XSD.getURI());
        PREFIXES.add("sh", SHACL.getURI());
    }

    private Turtle() {}

    public static String of(Graph graph) {
        StringWriter out = new StringWriter();
        RDFWriterRegistry.getWriterGraphFactory(RDFFormat.TURTLE_PRETTY).create(RDFFormat.TURTLE_PRETTY)
                .write(out, graph, PREFIXES, null, RIOT.getContext());
        return out.toString();
    }
}
