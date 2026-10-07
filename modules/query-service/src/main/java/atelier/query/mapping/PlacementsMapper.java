// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mapping;

import atelier.query.Atelier;
import atelier.query.api.Json;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;

/**
 * The world placements of the parts from a redacted placements federation. An occurrence of a line is a translation then
 * a rotation about the fixed x, y and z axes of the product frame, in that order, relative to the child as its CAD file
 * draws it; a line without occurrences is used once, as drawn. Occurrences compose down the tree, the parent's after the
 * child's, from the identity at each root (a site kit, or the roots of a subtree), so a part's occurrences are every
 * path's: its parents in id order, each parent's occurrences in order and, under each, the line's in index order. The
 * first is the composition of the reference occurrences, the identity. A path through an item the viewer may not see
 * contributes nothing: its own lines are not federated. Translations are converted to millimetres with the units graph.
 */
public final class PlacementsMapper {
    private static final double[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};
    private static final String[] AXES = {"X", "Y", "Z"};

    private final Model model;
    private final Units units;
    private final Map<String, Set<String>> children = new HashMap<>();
    private final Map<String, Set<String>> parents = new HashMap<>();
    /** Each line's occurrences by index, as transforms. */
    private final Map<String, Map<Integer, double[]>> occurrences = new HashMap<>();
    /** Each line's occurrence IRIs by index. */
    private final Map<String, Map<Integer, String>> occurrenceIris = new HashMap<>();
    private final Map<String, String> lineOf = new HashMap<>();

    public PlacementsMapper(Model model, Units units) {
        this.model = model;
        this.units = units;
        Property parent = Rdf.atelier(model, "parent");
        Property child = Rdf.atelier(model, "child");
        for (Resource line : model.listSubjectsWithProperty(parent).toList()) {
            RDFNode p = Rdf.object(line, parent);
            RDFNode c = Rdf.object(line, child);
            if (!line.isURIResource() || p == null || c == null || !p.isURIResource() || !c.isURIResource()) continue;
            String from = p.asResource().getURI();
            String to = c.asResource().getURI();
            children.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
            parents.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(from);
            lineOf.put(from + " " + to, line.getURI());
        }
        Property ofLine = Rdf.atelier(model, "ofLine");
        Property index = Rdf.atelier(model, "index");
        for (Resource occurrence : model.listSubjectsWithProperty(RDF.type, model.createResource(Atelier.OCCURRENCE)).toList()) {
            RDFNode line = Rdf.object(occurrence, ofLine);
            Integer i = Rdf.integer(occurrence, index);
            double[] m = transform(occurrence);
            if (line == null || !line.isURIResource() || i == null || m == null) continue;
            occurrences.computeIfAbsent(line.asResource().getURI(), k -> new HashMap<>()).put(i, m);
            occurrenceIris.computeIfAbsent(line.asResource().getURI(), k -> new HashMap<>()).put(i, occurrence.getURI());
        }
    }

    /** The visible geometric parts under the roots with their world placements, reference occurrence first, in id order. */
    public Json.Placements placements(String product, Collection<String> roots, Json.Provenance provenance, String sparql,
                                      Json.Timings timings, Json.Policy policy, Json.Subtree subtree) {
        Tree tree = tree(roots);
        List<Json.PartPlacements> parts = new ArrayList<>();
        int total = 0;
        for (String item : tree.parts()) {
            List<double[]> world = world(item, tree);
            if (world.isEmpty()) continue;
            parts.add(new Json.PartPlacements(Atelier.nativeId(item), Atelier.plmOf(item),
                    world.stream().map(PlacementsMapper::placement).toList()));
            total += world.size();
        }
        return new Json.Placements(product, parts, total, provenance, sparql, timings, policy, subtree);
    }

    /** The visible items under the roots, with the world transforms composed so far. */
    public record Tree(Set<String> reached, Map<String, List<double[]>> memo, List<String> parts) {}

    /** The items the visible roots reach through visible items, and the geometric parts among them in id order. */
    public Tree tree(Collection<String> roots) {
        Set<String> reached = new HashSet<>();
        roots.stream().filter(r -> visible(model.createResource(r))).forEach(r -> reach(r, reached));
        Map<String, List<double[]>> memo = new HashMap<>();
        roots.forEach(r -> memo.put(r, List.of(IDENTITY)));
        List<String> parts = reached.stream().sorted(Comparator.comparing(Atelier::nativeId).thenComparing(Atelier::plmOf))
                .filter(item -> {
                    String type = Rdf.string(model.createResource(item), Rdf.atelier(model, "partType"));
                    return type == null || "PART".equals(type);
                }).toList();
        return new Tree(reached, memo, parts);
    }

    /** The world transforms of an item's occurrences (3 x 4, row-major), in the order of the class comment. */
    public List<double[]> world(String item, Tree tree) {
        return world(item, tree.reached(), tree.memo());
    }

    /** The reached parents of an item, in id order: the order its occurrences are composed in. */
    public List<String> parents(String item, Tree tree) {
        return parents.getOrDefault(item, Set.of()).stream().filter(tree.reached()::contains)
                .sorted(Comparator.comparing(Atelier::nativeId)).toList();
    }

    /** The occurrence IRIs of the line from {@code parent} to {@code child} in index order; empty for a line used once, as drawn. */
    public List<String> occurrences(String parent, String child) {
        Map<Integer, String> byIndex = occurrenceIris.get(lineOf.get(parent + " " + child));
        if (byIndex == null) return List.of();
        return byIndex.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
    }

    private void reach(String item, Set<String> reached) {
        if (!reached.add(item)) return;
        for (String c : children.getOrDefault(item, Set.of())) {
            if (visible(model.createResource(c))) reach(c, reached);
        }
    }

    private List<double[]> world(String item, Set<String> reached, Map<String, List<double[]>> memo) {
        List<double[]> known = memo.get(item);
        if (known != null) return known;
        memo.put(item, List.of());
        List<double[]> found = new ArrayList<>();
        List<String> above = parents.getOrDefault(item, Set.of()).stream().filter(reached::contains)
                .sorted(Comparator.comparing(Atelier::nativeId)).toList();
        for (String parent : above) {
            List<double[]> local = local(lineOf.get(parent + " " + item));
            for (double[] p : world(parent, reached, memo)) {
                for (double[] m : local) found.add(multiply(p, m));
            }
        }
        memo.put(item, found);
        return found;
    }

    private List<double[]> local(String line) {
        Map<Integer, double[]> byIndex = occurrences.get(line);
        if (byIndex == null || byIndex.isEmpty()) return List.of(IDENTITY);
        return byIndex.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
    }

    /** The transform of one occurrence (3 x 4, row-major), or null when a value or a known length unit is missing. */
    private double[] transform(Resource occurrence) {
        double[] t = new double[3];
        double[] r = new double[3];
        for (int a = 0; a < 3; a++) {
            RDFNode node = Rdf.object(occurrence, Rdf.atelier(model, "translation" + AXES[a]));
            if (node == null || !node.isResource()) return null;
            BigDecimal value = Rdf.decimal(node.asResource(), Rdf.qudt(model, "numericValue"));
            Statement unit = node.asResource().getProperty(Rdf.qudt(model, "unit"));
            BigDecimal mm = unit == null || !unit.getObject().isURIResource() ? null : units.toMm(value, unit.getResource().getURI());
            BigDecimal degrees = Rdf.decimal(occurrence, Rdf.atelier(model, "rotation" + AXES[a]));
            if (mm == null || degrees == null) return null;
            t[a] = mm.doubleValue();
            r[a] = Math.toRadians(degrees.doubleValue());
        }
        double cx = Math.cos(r[0]), sx = Math.sin(r[0]), cy = Math.cos(r[1]), sy = Math.sin(r[1]), cz = Math.cos(r[2]), sz = Math.sin(r[2]);
        // Rz Ry Rx: about x first, then y, then z, the fixed axes of the frame.
        return new double[] {
                cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx, t[0],
                sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx, t[1],
                -sy, cy * sx, cy * cx, t[2]};
    }

    private static double[] multiply(double[] a, double[] b) {
        double[] out = new double[12];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 4; j++) {
                double v = a[i * 4] * b[j] + a[i * 4 + 1] * b[4 + j] + a[i * 4 + 2] * b[8 + j];
                out[i * 4 + j] = j == 3 ? v + a[i * 4 + 3] : v;
            }
        }
        return out;
    }

    /** A transform as [x, y, z, rx, ry, rz]: millimetres to 3 decimals, degrees to 4, in the convention of the occurrences. */
    static List<Double> placement(double[] m) {
        double ry = Math.asin(Math.max(-1, Math.min(1, -m[8])));
        double rx;
        double rz;
        if (Math.abs(m[8]) < 1 - 1e-9) {
            rx = Math.atan2(m[9], m[10]);
            rz = Math.atan2(m[4], m[0]);
        } else {
            // x and z turn about the same axis: the whole turn goes on z.
            rx = 0;
            rz = Math.atan2(-m[1], m[5]);
        }
        return List.of(round(m[3], 3), round(m[7], 3), round(m[11], 3),
                round(Math.toDegrees(rx), 4), round(Math.toDegrees(ry), 4), round(Math.toDegrees(rz), 4));
    }

    private static double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        double rounded = Math.round(value * scale) / scale;
        return rounded == 0 ? 0.0 : rounded;
    }

    private static boolean visible(Resource item) {
        return item.hasProperty(RDF.type);
    }
}
