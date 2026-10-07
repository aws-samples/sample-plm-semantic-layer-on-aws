// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mapping;

import atelier.query.Atelier;
import atelier.query.api.Json;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

/**
 * The virtual bill of materials of one product from a redacted bill-of-materials federation. No PLM holds the
 * product: the root is minted here, with under it the site kit of every PLM that has an item in the product (the
 * PLM's item no line reaches), then each site's tree from its own lines. Every idiom of the PLMs has been read as
 * {@code atelier:BomLine}, so the trees are walked the same way. Occurrences multiply the quantities down the tree; a
 * part's mass is converted to kg with the units graph. An item the viewer may not see is a {@link Json.BomHidden}
 * marker at its place: what it holds is not expanded, and its occurrences are counted apart in the roll-up.
 */
public final class BomMapper {
    private static final String PART = "PART";
    private static final String ASSEMBLY = "ASSEMBLY";

    private final Model model;
    private final Units units;
    private final Lifecycles lifecycles;
    private final Property parent;
    private final Property child;
    private final Property quantity;
    /** Each visible parent's lines, by child IRI in IRI order. */
    private final Map<String, Map<String, BigDecimal>> children = new LinkedHashMap<>();
    /** Each child's lines, by parent IRI: also the lines from hidden parents, which only the "lines to" request returns. */
    private final Map<String, Map<String, BigDecimal>> parents = new LinkedHashMap<>();

    /** One placement of an item in the expanded tree: the node and its parent node (null for a site kit). */
    private record Placement(Json.BomNode node, Json.BomNode parent) {}

    private final List<Placement> placements = new ArrayList<>();
    /** The own mass in kg of each part node, apart from the items it holds. */
    private final Map<Json.BomNode, BigDecimal> ownMass = new IdentityHashMap<>();

    public BomMapper(Model model, Units units, Lifecycles lifecycles) {
        this.model = model;
        this.units = units;
        this.lifecycles = lifecycles;
        this.parent = Rdf.atelier(model, "parent");
        this.child = Rdf.atelier(model, "child");
        this.quantity = Rdf.atelier(model, "quantity");
        model.listSubjectsWithProperty(RDF.type, model.createResource(Atelier.BOM_LINE)).toList().stream()
                .sorted(Comparator.comparing(Resource::getURI)).forEach(line -> {
                    RDFNode p = Rdf.object(line, parent);
                    RDFNode c = Rdf.object(line, child);
                    BigDecimal q = Rdf.decimal(line, quantity);
                    if (p == null || c == null || q == null || !p.isURIResource() || !c.isURIResource()) return;
                    children.computeIfAbsent(p.asResource().getURI(), k -> new LinkedHashMap<>()).put(c.asResource().getURI(), q);
                    parents.computeIfAbsent(c.asResource().getURI(), k -> new LinkedHashMap<>()).put(p.asResource().getURI(), q);
                });
    }

    /** States {@code parent atelier:assembles child} for every line between two items the viewer may see. */
    public static void deriveAssembles(Model model) {
        Property parent = Rdf.atelier(model, "parent");
        Property child = Rdf.atelier(model, "child");
        Property assembles = Rdf.atelier(model, "assembles");
        for (Resource line : model.listSubjectsWithProperty(RDF.type, model.createResource(Atelier.BOM_LINE)).toList()) {
            RDFNode p = Rdf.object(line, parent);
            RDFNode c = Rdf.object(line, child);
            if (p != null && c != null && p.isURIResource() && c.isURIResource()
                    && p.asResource().hasProperty(RDF.type) && c.asResource().hasProperty(RDF.type)) {
                model.add(p.asResource(), assembles, c);
            }
        }
    }

    /**
     * States {@code item atelier:occurrences n} for every item the viewer may see: how many times it occurs in its product,
     * the quantities multiplied down the lines from its site kit (the item no line reaches, which occurs once) and summed
     * over every path, as the roll-up counts them. A path through a parent the viewer may not see contributes nothing,
     * since that parent's own lines are not federated. Only over a bill-of-materials federation: without lines every
     * item would be a site kit.
     */
    public static void deriveOccurrences(Model model) {
        BomMapper lines = new BomMapper(model, null, null);
        Property occurrences = Rdf.atelier(model, "occurrences");
        Map<String, BigDecimal> memo = new HashMap<>();
        for (Resource item : model.listSubjectsWithProperty(RDF.type, model.createResource(Atelier.ONT + "Part")).toList()) {
            if (item.isURIResource() && item.hasProperty(Rdf.atelier(model, "partOf"))) {
                model.addLiteral(item, occurrences, model.createTypedLiteral(lines.occurrencesOf(item.getURI(), memo, new HashSet<>())));
            }
        }
    }

    private BigDecimal occurrencesOf(String item, Map<String, BigDecimal> memo, Set<String> path) {
        BigDecimal known = memo.get(item);
        if (known != null) return known;
        Map<String, BigDecimal> uses = parents.get(item);
        BigDecimal total = uses == null ? BigDecimal.ONE : BigDecimal.ZERO;
        if (uses != null && path.add(item)) {
            for (Map.Entry<String, BigDecimal> use : uses.entrySet()) {
                if (path.contains(use.getKey()) || !visible(model.createResource(use.getKey()))) continue;
                total = total.add(occurrencesOf(use.getKey(), memo, path).multiply(use.getValue()));
            }
            path.remove(item);
        }
        memo.put(item, total);
        return total;
    }

    /** The root, the site kits and their trees, and the roll-ups, for the product of that IRI. */
    public Json.Bom bom(String productIri, Json.Provenance provenance, String sparql, Json.Timings timings, Json.Policy policy) {
        Resource product = model.createResource(productIri);
        List<Json.BomItem> kits = new ArrayList<>();
        for (String plm : Atelier.PLMS) {
            List<Resource> members = members(product, plm);
            if (members.isEmpty()) continue;
            kits.add(members.stream().filter(m -> visible(m) && ASSEMBLY.equals(partType(m)) && !parents.containsKey(m.getURI()))
                    .findFirst().<Json.BomItem>map(kit -> node(kit, BigDecimal.ONE, BigDecimal.ONE, new HashSet<>()))
                    .orElse(new Json.BomHidden(true, plm, 1, 1)));
        }
        Rollup total = new Rollup(null);
        List<Json.BomRollup> sites = new ArrayList<>();
        for (Json.BomItem kit : kits) {
            Rollup site = new Rollup(kit instanceof Json.BomNode n ? n.plm() : ((Json.BomHidden) kit).plm());
            site.add(kit);
            total.add(kit);
            sites.add(site.json());
        }
        Double rootMass = kits.stream().map(BomMapper::unitMass).filter(m -> m != null).reduce(Double::sum).orElse(null);
        Json.BomNode root = new Json.BomNode(Atelier.nativeId(productIri), null, Rdf.string(product, Rdf.atelier(model, "label")), null,
                "PRODUCT", null, null, null, 1, 1, rootMass, rootMass, kits);
        return new Json.Bom(root.id(), root, sites, total.json(), provenance, sparql, timings, policy);
    }

    /**
     * The tree under one item, as the viewer sees it: its own site's lines and, under an item whose external reference
     * names an item the subtree reached ({@code crossings}: from the referencing item's IRI to each target's IRI with the
     * reference's quantity), the target's tree. Roll-ups per site and in total over the subtree. A root the viewer may not
     * see is a node with its id and site only, holding nothing.
     */
    public Json.Bom subtree(String productKey, String rootIri, Map<String, Map<String, BigDecimal>> crossings, Json.Provenance provenance,
                            String sparql, Json.Timings timings, Json.Policy policy, Json.Subtree subtree) {
        crossings.forEach((from, targets) -> targets.forEach((to, q) -> {
            children.computeIfAbsent(from, k -> new LinkedHashMap<>()).merge(to, q, BigDecimal::add);
            parents.computeIfAbsent(to, k -> new LinkedHashMap<>()).merge(from, q, BigDecimal::add);
        }));
        Resource item = model.createResource(rootIri);
        Json.BomNode root = visible(item) ? node(item, BigDecimal.ONE, BigDecimal.ONE, new HashSet<>())
                : new Json.BomNode(Atelier.nativeId(rootIri), Atelier.plmOf(rootIri), null, null, null, null, null, null, 1, 1, null, null, List.of());
        Rollup total = new Rollup(null);
        total.add(root);
        List<Json.BomRollup> sites = new ArrayList<>();
        for (String plm : Atelier.PLMS) {
            Rollup site = new Rollup(plm);
            site.add(root);
            if (site.counted()) sites.add(site.json());
        }
        return new Json.Bom(productKey, root, sites, total.json(), provenance, sparql, timings, policy, subtree);
    }

    /**
     * The parents of the part (IRI) in the product's tree as the viewer sees it, each once with the quantity it uses and
     * the part's occurrences under it; a hidden parent is a marker with its quantity. Call after {@link #bom}.
     */
    public List<Json.BomUse> usedIn(String partIri) {
        String plm = Atelier.plmOf(partIri);
        String id = Atelier.nativeId(partIri);
        Map<String, Json.BomUse> byParent = new LinkedHashMap<>();
        for (Placement p : placements) {
            if (p.parent() == null || !id.equals(p.node().id()) || !plm.equals(p.node().plm())) continue;
            Json.BomNode up = p.parent();
            String key = up.plm() + "/" + up.id();
            Json.BomUse seen = byParent.get(key);
            double occurrences = p.node().occurrences() + (seen == null ? 0 : seen.occurrences());
            byParent.put(key, new Json.BomUse(up.id(), up.plm(), up.name(), up.partType(), null, p.node().quantity(), occurrences));
        }
        parents.getOrDefault(partIri, Map.of()).forEach((parentIri, q) -> {
            if (!visible(model.createResource(parentIri))) {
                byParent.put(parentIri, new Json.BomUse(null, Atelier.plmOf(parentIri), null, null, true, q.doubleValue(), null));
            }
        });
        return List.copyOf(byParent.values());
    }

    /** The part's occurrences over every visible placement. */
    public double occurrences(String partIri) {
        return usedIn(partIri).stream().filter(u -> u.occurrences() != null).mapToDouble(Json.BomUse::occurrences).sum();
    }

    private Json.BomNode node(Resource item, BigDecimal qty, BigDecimal occurrences, Set<String> path) {
        List<Json.BomItem> items = new ArrayList<>();
        Set<String> below = new HashSet<>(path);
        below.add(item.getURI());
        BigDecimal childMass = null;
        List<Json.BomNode> placed = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> line : children.getOrDefault(item.getURI(), Map.of()).entrySet()) {
            if (below.contains(line.getKey())) continue;
            Resource c = model.createResource(line.getKey());
            BigDecimal occ = occurrences.multiply(line.getValue());
            if (!visible(c)) {
                items.add(new Json.BomHidden(true, Atelier.plmOf(c.getURI()), line.getValue().doubleValue(), occ.doubleValue()));
                continue;
            }
            Json.BomNode n = node(c, line.getValue(), occ, below);
            items.add(n);
            placed.add(n);
            if (n.unitMassKg() != null) {
                BigDecimal m = BigDecimal.valueOf(n.unitMassKg()).multiply(line.getValue());
                childMass = childMass == null ? m : childMass.add(m);
            }
        }
        items.sort(ORDER);
        BigDecimal own = ownMassKg(item);
        BigDecimal unit = own == null ? childMass : childMass == null ? own : own.add(childMass);
        String lifecycle = Rdf.string(item, Rdf.atelier(model, "lifecycleLabel"));
        Json.BomNode node = new Json.BomNode(Atelier.nativeId(item.getURI()), Atelier.plmOf(item.getURI()),
                Rdf.string(item, Rdf.atelier(model, "label")), Rdf.englishName(item), partType(item), Rdf.string(item, Rdf.atelier(model, "revision")),
                lifecycle, lifecycles.state(lifecycle), qty.doubleValue(), occurrences.doubleValue(),
                unit == null ? null : unit.doubleValue(), unit == null ? null : unit.multiply(occurrences).doubleValue(), items);
        if (own != null) ownMass.put(node, own);
        placed.forEach(n -> placements.add(new Placement(n, node)));
        if (path.isEmpty()) placements.add(new Placement(node, null));
        return node;
    }

    /** Assemblies before parts, each group by id. */
    private static final Comparator<Json.BomItem> ORDER = Comparator
            .comparing((Json.BomItem i) -> i instanceof Json.BomNode n && ASSEMBLY.equals(n.partType()) ? 0 : i instanceof Json.BomNode ? 1 : 2)
            .thenComparing(i -> i instanceof Json.BomNode n ? n.id() : "");

    /** Part occurrences, mass, parts without a mass and hidden occurrences of a subtree. */
    private final class Rollup {
        private final String plm;
        private BigDecimal occurrences = BigDecimal.ZERO;
        private BigDecimal mass = BigDecimal.ZERO;
        private int withoutMass;
        private BigDecimal hidden = BigDecimal.ZERO;
        private boolean seen;

        Rollup(String plm) {
            this.plm = plm;
        }

        /** Adds the item and what it holds; a site's roll-up counts its own site's items only, a subtree crossing sites. */
        void add(Json.BomItem item) {
            if (item instanceof Json.BomHidden h) {
                if (plm == null || plm.equals(h.plm())) {
                    hidden = hidden.add(BigDecimal.valueOf(h.occurrences()));
                    seen = true;
                }
                return;
            }
            Json.BomNode n = (Json.BomNode) item;
            boolean ofSite = plm == null || plm.equals(n.plm());
            if (ofSite) seen = true;
            if (ofSite && (n.partType() == null || PART.equals(n.partType()))) {
                BigDecimal occ = BigDecimal.valueOf(n.occurrences());
                occurrences = occurrences.add(occ);
                BigDecimal own = ownMass.get(n);
                if (own == null) withoutMass++;
                else mass = mass.add(own.multiply(occ));
            }
            n.children().forEach(this::add);
        }

        /** True when the roll-up met an item of its site. */
        boolean counted() {
            return seen;
        }

        Json.BomRollup json() {
            return new Json.BomRollup(plm, occurrences.doubleValue(), mass.doubleValue(), withoutMass, hidden.doubleValue());
        }
    }

    private static Double unitMass(Json.BomItem item) {
        return item instanceof Json.BomNode n ? n.unitMassKg() : null;
    }

    private List<Resource> members(Resource product, String plm) {
        return model.listSubjectsWithProperty(Rdf.atelier(model, "partOf"), product).toList().stream()
                .filter(r -> plm.equals(Atelier.plmOf(r.getURI()))).sorted(Comparator.comparing(Resource::getURI)).toList();
    }

    private String partType(Resource item) {
        return Rdf.string(item, Rdf.atelier(model, "partType"));
    }

    private static boolean visible(Resource item) {
        return item.hasProperty(RDF.type);
    }

    private BigDecimal ownMassKg(Resource item) {
        RDFNode node = Rdf.object(item, Rdf.atelier(model, "mass"));
        if (node == null || !node.isResource()) return null;
        BigDecimal value = Rdf.decimal(node.asResource(), Rdf.qudt(model, "numericValue"));
        String unit = Rdf.string(node.asResource(), Rdf.qudt(model, "unit"));
        return value == null ? null : units.toKg(value, unit);
    }
}
