// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.paths;

import atelier.query.Atelier;
import atelier.query.api.FunctionJson;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

/**
 * Connectivity through a product: its parts are the nodes and its interfaces ({@code atelier:betweenPart}, two parts
 * each) the edges, so a path names the interface of every step. SPARQL property paths match a path but cannot return
 * it, so the shortest paths are found here by a breadth-first search over the merged graph. A part the viewer may not
 * see (named by an interface, described by no source) is not passed through: it breaks the path for that viewer, and
 * the search over every part tells whether a hidden part breaks or shortens the paths.
 */
public final class PathFinder {
    public static final int MAX_LENGTH = 12;
    public static final int MAX_PATHS = 5;

    /** An interface between two parts, as an edge walked from {@code from} to {@code to}. */
    private record Edge(String iface, String from, String to) {}

    private final Model model;
    private final Map<String, List<Edge>> adjacent = new HashMap<>();
    private final Steps steps;

    public PathFinder(Model model, Steps steps) {
        this.model = model;
        this.steps = steps;
        Property betweenPart = model.createProperty(Atelier.ONT + "betweenPart");
        for (Resource iface : model.listSubjectsWithProperty(betweenPart).toList()) {
            List<String> parts = model.listObjectsOfProperty(iface, betweenPart).filterKeep(RDFNode::isURIResource)
                    .mapWith(n -> n.asResource().getURI()).toList();
            if (parts.size() != 2) continue;
            adjacent.computeIfAbsent(parts.get(0), k -> new ArrayList<>()).add(new Edge(iface.getURI(), parts.get(0), parts.get(1)));
            adjacent.computeIfAbsent(parts.get(1), k -> new ArrayList<>()).add(new Edge(iface.getURI(), parts.get(1), parts.get(0)));
        }
        adjacent.values().forEach(edges -> edges.sort(Comparator.comparing(Edge::iface).thenComparing(Edge::to)));
    }

    /** The shortest paths from one part IRI to another, with the notes on hidden parts; both parts visible. */
    public Found between(String from, String to) {
        List<List<Edge>> visible = shortest(from, to, true);
        List<List<Edge>> any = shortest(from, to, false);
        List<String> notes = new ArrayList<>();
        if (visible.isEmpty() && !any.isEmpty()) {
            notes.add("Every path of at most " + MAX_LENGTH + " steps between them runs through a part not visible to your profile.");
        } else if (!any.isEmpty() && any.get(0).size() < visible.get(0).size()) {
            notes.add("A shorter path of " + any.get(0).size() + " steps runs through a part not visible to your profile.");
        } else if (any.isEmpty()) {
            notes.add("No path of at most " + MAX_LENGTH + " steps joins them through the product's interfaces.");
        }
        List<FunctionJson.Path> paths = visible.stream()
                .map(p -> new FunctionJson.Path(p.size(), p.stream().map(e -> steps.joint(e.from(), e.to(), e.iface())).toList())).toList();
        Set<String> on = new LinkedHashSet<>();
        visible.forEach(p -> p.forEach(e -> { on.add(e.from()); on.add(e.to()); }));
        return new Found(paths, notes, List.copyOf(on));
    }

    /** The paths, the notes, and the IRIs of the parts on them in walking order. */
    public record Found(List<FunctionJson.Path> paths, List<String> notes, List<String> parts) {}

    /**
     * Every shortest path of at most {@link #MAX_LENGTH} steps, at most {@link #MAX_PATHS} of them in interface order;
     * with {@code visibleOnly}, through visible parts only.
     */
    private List<List<Edge>> shortest(String from, String to, boolean visibleOnly) {
        Map<String, Integer> depth = new HashMap<>(Map.of(from, 0));
        Map<String, List<Edge>> into = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>(List.of(from));
        while (!queue.isEmpty()) {
            String part = queue.poll();
            int d = depth.get(part);
            if (d == MAX_LENGTH || part.equals(to) || (visibleOnly && !part.equals(from) && !visible(part))) continue;
            for (Edge edge : adjacent.getOrDefault(part, List.of())) {
                Integer seen = depth.get(edge.to());
                if (seen == null) {
                    depth.put(edge.to(), d + 1);
                    queue.add(edge.to());
                }
                if (seen == null || seen == d + 1) into.computeIfAbsent(edge.to(), k -> new ArrayList<>()).add(edge);
            }
        }
        List<List<Edge>> out = new ArrayList<>();
        if (depth.containsKey(to)) collect(to, from, into, new ArrayDeque<>(), out);
        return out;
    }

    /** Walks back from {@code part} to {@code from} along the edges into each part, collecting paths in order. */
    private static void collect(String part, String from, Map<String, List<Edge>> into, Deque<Edge> tail, List<List<Edge>> out) {
        if (out.size() == MAX_PATHS) return;
        if (part.equals(from)) {
            out.add(List.copyOf(tail));
            return;
        }
        for (Edge edge : into.getOrDefault(part, List.of())) {
            tail.push(edge);
            collect(edge.from(), from, into, tail, out);
            tail.pop();
            if (out.size() == MAX_PATHS) return;
        }
    }

    private boolean visible(String part) {
        return model.createResource(part).hasProperty(RDF.type);
    }
}
