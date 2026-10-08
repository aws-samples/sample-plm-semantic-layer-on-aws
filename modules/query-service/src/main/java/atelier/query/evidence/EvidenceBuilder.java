// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.evidence;

import atelier.query.Answer;
import atelier.query.Atelier;
import atelier.query.api.Json;
import atelier.query.federation.Endpoints;
import atelier.query.federation.Federator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.graph.GraphFactory;

/**
 * Serialises the material behind an {@link Answer} into the contract's Evidence: one arm per
 * endpoint of the run, the graph that was validated (the interface's neighbourhood of the merged
 * graph, after export-control redaction), the SHACL report with the shapes that reported for the
 * interface (those of its violations and of the findings on its parts), and the policy the answer
 * was computed for. An arm carries the requests the run sent to its endpoint and the endpoint's
 * measured share of the run (requests, triples, time: the provenance figures), with the triples of
 * the validated graph it supplied and the native rows behind them; the triples are attributed by
 * {@link Federator#sourceOf}, as in provenance.
 */
public final class EvidenceBuilder {
    private static final Node RULE = NodeFactory.createURI(Atelier.SHAPES + "rule");

    private final Graph shapes;
    private final Map<String, String> shapeByRule = new HashMap<>();
    private final OntopSql ontopSql;

    public EvidenceBuilder(Graph shapes, OntopSql ontopSql) {
        this.shapes = shapes;
        shapes.find(Node.ANY, RULE, Node.ANY).forEachRemaining(t ->
                shapeByRule.put(t.getObject().getLiteralLexicalForm(), t.getSubject().getURI()));
        this.ontopSql = ontopSql;
    }

    public Json.Evidence build(Json.Interface iface, Answer answer) {
        Federator.Result fed = answer.federation();
        Map<String, Graph> byEndpoint = split(answer.model());
        List<Json.Arm> arms = fed.calls().stream()
                .map(call -> arm(call, byEndpoint.getOrDefault(call.endpoint(), GraphFactory.createDefaultGraph()),
                        fed.queries().getOrDefault(call.endpoint(), List.of())))
                .toList();
        Json.Merged merged = new Json.Merged((int) answer.model().size(), Turtle.of(answer.model().getGraph()));
        Json.Shacl shacl = new Json.Shacl(shapesReporting(iface), Turtle.of(answer.report().getGraph()));
        return new Json.Evidence(iface.id(), iface.product(), answer.sparql(), arms, merged, shacl, answer.timings(), answer.policy());
    }

    private Json.Arm arm(Federator.Call call, Graph triples, List<String> queries) {
        String source = Endpoints.sourceOf(call.endpoint());
        String sql = source == null ? null : sql(source, queries);
        List<Json.Table> tables = source == null ? List.of() : NativeTables.of(source, triples);
        return new Json.Arm(call.endpoint(), call.kind(), String.join("\n\n", queries), Turtle.of(triples),
                (int) call.triples(), call.requests(), call.ms(), sql, tables);
    }

    /** One SQL per request sent to the Ontop source, joined like the requests; null when none was sent or any is not obtainable. */
    private String sql(String source, List<String> queries) {
        if (queries.isEmpty()) return null;
        List<String> sqls = new ArrayList<>();
        for (String query : queries) {
            String sql = ontopSql.reformulate(source, query);
            if (sql == null) return null;
            sqls.add(sql);
        }
        return String.join("\n\n", sqls);
    }

    private List<Json.Shape> shapesReporting(Json.Interface iface) {
        Map<String, String> ruleByShape = new LinkedHashMap<>();
        iface.violations().forEach(v -> ruleByShape.putIfAbsent(v.shape(), v.rule()));
        iface.parts().stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast)
                .flatMap(p -> p.findings().stream()).forEach(f -> ruleByShape.putIfAbsent(shapeByRule.get(f.rule()), f.rule()));
        return ruleByShape.entrySet().stream()
                .map(e -> new Json.Shape(e.getValue(), e.getKey(), Turtle.of(ShapeSource.of(shapes, e.getKey()))))
                .toList();
    }

    private static Map<String, Graph> split(Model merged) {
        Map<String, Graph> graphs = new LinkedHashMap<>();
        merged.getGraph().find().forEachRemaining(t ->
                graphs.computeIfAbsent(Federator.sourceOf(t), k -> GraphFactory.createDefaultGraph()).add(t));
        return graphs;
    }
}
