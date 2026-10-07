// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.demo;

import atelier.query.federation.Endpoints;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.graph.GraphFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The live link store as the change feed reads it: one named graph read whole through the SPARQL
 * endpoint ({@code NEPTUNE_SPARQL_URL}) within the endpoints' timeouts, to be diffed against its released file. Read-only: the
 * query service writes to no store, the Atelier core service does. A failure names the link store,
 * never the URL; the detail is logged here.
 */
@Component
public class LiveGraphs {
    private static final Logger log = LoggerFactory.getLogger(LiveGraphs.class);

    private final Endpoints endpoints;

    public LiveGraphs(Endpoints endpoints) {
        this.endpoints = endpoints;
    }

    /** Every triple of the named graph, as the store answers {@code SELECT ?s ?p ?o}. */
    public Model graph(String iri) {
        String query = "SELECT ?s ?p ?o WHERE { GRAPH <" + iri + "> { ?s ?p ?o } }";
        Graph graph = GraphFactory.createDefaultGraph();
        try (QueryExecution exec = endpoints.execution(endpoints.linkStore(), query)) {
            exec.execSelect().forEachRemaining(row -> graph.add(triple(row)));
        } catch (RuntimeException e) {
            log.warn("Link store read of <{}> failed: {}", iri, e.toString());
            throw new IllegalStateException("the link store did not answer");
        }
        return ModelFactory.createModelForGraph(graph);
    }

    private static Triple triple(QuerySolution row) {
        return Triple.create(row.get("s").asNode(), row.get("p").asNode(), row.get("o").asNode());
    }
}
