// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mcp;

import atelier.query.Answer;
import atelier.query.api.Json;
import atelier.query.evidence.Turtle;
import atelier.query.policy.Redaction;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryCancelledException;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.sparql.graph.GraphFactory;

/**
 * Runs a query the {@link SparqlGuard} accepted on the merged graph of an {@link Answer} (the
 * profile's redacted federation over every interface, in memory) and reports the result with the
 * time it took. The graph queried is a copy in which every hidden part or feature IRI
 * ({@link Redaction#hidden}) is a blank node: the interface structure stays countable, but a
 * hidden subject is never named, as in every other answer. A query still running after
 * {@link #TIMEOUT} is cancelled and reported as such.
 */
public final class SparqlRunner {
    static final Duration TIMEOUT = Duration.ofSeconds(10);

    private SparqlRunner() {}

    public static Json.SparqlResult run(Query query, Answer answer) {
        long start = System.nanoTime();
        Integer limit = query.isAskType() ? null : (int) query.getLimit();
        try (QueryExecution exec = QueryExecution.model(anonymised(answer.model())).query(query)
                .timeout(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).build()) {
            if (query.isAskType()) {
                boolean result = exec.execAsk();
                return result(query, "ask", null, null, null, result, null, limit, answer, start);
            }
            if (query.isConstructType()) {
                String turtle = Turtle.of(exec.execConstruct().getGraph());
                return result(query, "construct", null, null, null, null, turtle, limit, answer, start);
            }
            ResultSet results = exec.execSelect();
            List<String> columns = results.getResultVars();
            List<List<Object>> rows = new ArrayList<>();
            while (results.hasNext()) {
                QuerySolution row = results.next();
                rows.add(columns.stream().map(c -> value(row.get(c))).toList());
            }
            return result(query, "select", columns, rows, rows.size() == limit, null, null, limit, answer, start);
        } catch (QueryCancelledException e) {
            throw new IllegalStateException("the query did not finish within " + TIMEOUT.toSeconds() + " s");
        }
    }

    private static Json.SparqlResult result(Query query, String form, List<String> columns, List<List<Object>> rows,
                                            Boolean limitReached, Boolean result, String turtle, Integer limit,
                                            Answer answer, long start) {
        return new Json.SparqlResult(query.toString(), form, columns, rows, limitReached, result, turtle, limit,
                answer.model().size(), (System.nanoTime() - start) / 1_000_000, answer.provenance(), answer.timings(),
                answer.policy());
    }

    /** The merged graph with every hidden IRI replaced by one blank node per IRI; the graph itself when nothing is hidden. */
    static Model anonymised(Model merged) {
        Map<String, Node> blanks = new HashMap<>();
        Redaction.hidden(merged).forEach(iri -> blanks.put(iri, NodeFactory.createBlankNode()));
        if (blanks.isEmpty()) return merged;
        Graph copy = GraphFactory.createDefaultGraph();
        merged.getGraph().find().forEachRemaining(t ->
                copy.add(Triple.create(anonymous(t.getSubject(), blanks), t.getPredicate(), anonymous(t.getObject(), blanks))));
        return ModelFactory.createModelForGraph(copy);
    }

    private static Node anonymous(Node node, Map<String, Node> blanks) {
        return node.isURI() ? blanks.getOrDefault(node.getURI(), node) : node;
    }

    /** A binding as JSON: IRI or blank-node label as text, numbers and booleans as such, other literals by lexical form; null when unbound. */
    static Object value(RDFNode node) {
        if (node == null) return null;
        if (node.isURIResource()) return node.asResource().getURI();
        if (node.isAnon()) return "_:" + node.asResource().getId().getLabelString();
        Literal literal = node.asLiteral();
        Object typed = literal.getValue();
        return typed instanceof Number || typed instanceof Boolean ? typed : literal.getLexicalForm();
    }
}
