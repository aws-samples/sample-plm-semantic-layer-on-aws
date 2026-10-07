// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.federation;

import atelier.query.Atelier;
import atelier.query.federation.FederatedQueries.Selection;
import atelier.query.mapping.ExternalReferences;
import atelier.query.policy.Policy;
import atelier.query.policy.Redaction;
import atelier.query.variants.OptionRows;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;

/**
 * Resolves the subtree of one item across the sites, by roots, never by parts. The core graph names the root's site: of the item's IRI under each PLM, the
 * one that belongs to the product. Then in rounds: the core graph returns the tags and memberships of the round's
 * candidate roots, so a root outside the product (a dangling reference) is dropped and a root the viewer may not see
 * is a redacted node its site is never asked about; each site named by the visible roots returns its trees under them
 * ({@link FederatedQueries#structure}: the items each root contains through its own closure table, and the lines
 * between them), the core graph returns the tags and product memberships of the items found, and the layer decides
 * what the viewer may see. A hidden item is a redacted node: the items under it are not expanded and its lines are
 * dropped. Each site is then asked for the facts of its visible items by the roots of the trees with no hidden item and,
 * for a visible item above a hidden one, by that item alone, with the external references they make; the items those
 * references name, not reached yet, are the next round's roots, grouped by site. The resolution stops when a round adds
 * nothing, or after {@link Subtree#MAX_ROUNDS} rounds, when the core graph is asked for the memberships of the items
 * left unresolved so the reference shapes can still tell them from missing ones. An interfaces run then reads, as
 * context, the far side of every interface of the product with a side in the subtree: tags, memberships, then each
 * visible far part's attributes and features alone. The link store is asked last, once per graph, with the final items:
 * the interfaces with a side in the subtree, then the file index of the items and the context parts, so its work
 * follows the subtree as the sites' does; the parts and bill-of-materials answers hold no interface and read the file
 * index alone. A placements run asks each site once more, after the rounds, for the placements of the lines under its
 * visible roots, with the hidden items of the subtree as the only IRIs listed. Everything else it returned leaves the model.
 */
final class Subtrees {
    private final Federator federator;
    private final Endpoints endpoints;
    private final Policy.Profile profile;
    private final Federator.Concern concern;
    private final String product;
    private final String productIri;
    private final CallRecorder recorder;
    private final List<String> sent = new ArrayList<>();
    private final Model model = ModelFactory.createDefaultModel();
    private final Model memberships = ModelFactory.createDefaultModel();
    private final Set<String> described = new HashSet<>();
    private final Property partOf;
    private final Property parent;
    private final Property child;

    Subtrees(Federator federator, Policy.Profile profile, Federator.Concern concern, String product) {
        this.federator = federator;
        this.endpoints = federator.endpoints();
        this.profile = profile;
        this.concern = concern;
        this.product = product;
        this.productIri = Atelier.productIri(product);
        this.recorder = new CallRecorder(endpoints.namesByUrl());
        this.partOf = model.createProperty(Atelier.PART_OF);
        this.parent = model.createProperty(Atelier.ONT + "parent");
        this.child = model.createProperty(Atelier.ONT + "child");
    }

    Federator.SubtreeRun run(String rootId) {
        long start = System.nanoTime();
        String root = root(rootId);

        Set<String> items = new LinkedHashSet<>();
        Set<String> hidden = new LinkedHashSet<>();
        List<Subtree.Round> rounds = new ArrayList<>();
        Set<String> pending = Set.of(root);
        List<String> visibleRoots = new ArrayList<>();
        for (int round = 1; !pending.isEmpty() && round <= Subtree.MAX_ROUNDS; round++) {
            describe(pending);
            List<String> roots = pending.stream().filter(this::inProduct).sorted().toList();
            if (roots.isEmpty()) break;
            Set<String> reached = new LinkedHashSet<>();
            roots.stream().filter(r -> !visible(r)).forEach(r -> {
                reached.add(r);
                hidden.add(r);
            });
            List<String> asked = roots.stream().filter(this::visible).toList();
            visibleRoots.addAll(asked);
            Model structure = bySite(asked, (plm, sited) -> List.of(FederatedQueries.structure(sited)));
            Map<String, Set<String>> lines = lines(structure);
            Set<String> found = new HashSet<>();
            structure.listObjectsOfProperty(model.createProperty(Atelier.ONT + "contains"))
                    .forEachRemaining(o -> found.add(o.asResource().getURI()));
            found.removeAll(items);
            describe(found);

            Deque<String> todo = new ArrayDeque<>(asked.stream().filter(found::contains).toList());
            while (!todo.isEmpty()) {
                String item = todo.pop();
                if (!found.contains(item) || !inProduct(item) || !reached.add(item)) continue;
                if (!visible(item)) {
                    hidden.add(item);
                    continue;
                }
                lines.getOrDefault(item, Set.of()).forEach(todo::push);
            }
            if (reached.isEmpty()) break;
            items.addAll(reached);
            keepLines(structure, reached);

            Model facts = facts(reached, hidden, lines, roots);
            model.add(facts);
            Set<String> next = new TreeSet<>(ExternalReferences.targets(facts).values());
            next.removeAll(items);
            rounds.add(new Subtree.Round(round, roots, reached.size()));
            pending = next;
        }
        Set<String> unresolved = rounds.size() == Subtree.MAX_ROUNDS ? new TreeSet<>(pending) : Set.of();
        if (!unresolved.isEmpty()) {
            copyMemberships(core(FederatedQueries.products(unresolved)));
        }

        if (concern == Federator.Concern.PLACEMENTS) {
            model.add(bySite(visibleRoots, (plm, sited) -> List.of(FederatedQueries.placements(sited,
                    hidden.stream().filter(h -> plm.equals(Atelier.plmOf(h))).toList()))));
        }
        Set<String> context = Set.of();
        if (concern.interfaces()) {
            model.add(link(FederatedQueries.interfaceFacts(items)));
            context = context(items);
        }
        Set<String> listed = new TreeSet<>(items);
        listed.addAll(context);
        model.add(link(FederatedQueries.fileIndex(listed)));
        List<String> shown = items.stream().filter(this::visible).sorted().toList();
        if (concern != Federator.Concern.INTERFACES && !shown.isEmpty()) {
            model.add(link(FederatedQueries.layer(shown)));
            // The meshModule rule reads the functional edges with the gears' modules; a gear outside the subtree has none.
            if (concern == Federator.Concern.PARTS) model.add(link(FederatedQueries.drives(productIri)));
        }
        scope(items, context);
        OptionRows.drop(model);
        long ms = (System.nanoTime() - start) / 1_000_000;
        Federator.Result result = Federator.result(recorder, model, memberships, String.join("\n\n", sent), ms);
        return new Federator.SubtreeRun(result, new Subtree(root, productIri, rounds, items, hidden, context, unresolved));
    }

    /**
     * The IRI of the item {@code id} names in the product: of its IRI under each PLM, the one the core graph places in
     * the product. Their tags are read in the same requests, so round 1 asks the core graph only about the rest of the tree.
     */
    private String root(String id) {
        List<String> candidates = Atelier.PLMS.stream().map(plm -> Atelier.partIri(plm, id)).toList();
        describe(candidates);
        List<String> roots = candidates.stream().filter(this::inProduct).toList();
        if (roots.isEmpty()) throw new UnknownItem(id, product);
        if (roots.size() > 1) {
            throw new IllegalArgumentException("item " + id + " names an item of several sites in " + product);
        }
        return roots.get(0);
    }

    /**
     * The facts of the visible items reached: per site, the trees with no hidden item by their roots (the round's roots
     * and every visible item no such tree contains) and every visible item above a hidden one by itself.
     */
    private Model facts(Set<String> reached, Set<String> hidden, Map<String, Set<String>> lines, List<String> roots) {
        Map<String, Boolean> clean = new HashMap<>();
        Map<String, Set<String>> parents = new HashMap<>();
        lines.forEach((p, children) -> children.forEach(c -> parents.computeIfAbsent(c, k -> new HashSet<>()).add(p)));
        List<String> under = new ArrayList<>();
        List<String> only = new ArrayList<>();
        for (String item : reached) {
            if (hidden.contains(item)) continue;
            if (!clean(item, lines, hidden, clean, new HashSet<>())) {
                only.add(item);
            } else if (roots.contains(item) || parents.getOrDefault(item, Set.of()).stream()
                    .noneMatch(p -> reached.contains(p) && !hidden.contains(p) && clean(p, lines, hidden, clean, new HashSet<>()))) {
                under.add(item);
            }
        }
        Map<String, List<String>> requests = new LinkedHashMap<>();
        bySite(under).forEach((plm, sited) -> requests.computeIfAbsent(plm, k -> new ArrayList<>()).addAll(requests(Selection.under(sited))));
        bySite(only).forEach((plm, sited) -> requests.computeIfAbsent(plm, k -> new ArrayList<>()).addAll(requests(Selection.only(sited))));
        return send(requests);
    }

    /** True when no item under {@code item}, itself included, is hidden. */
    private static boolean clean(String item, Map<String, Set<String>> lines, Set<String> hidden, Map<String, Boolean> memo, Set<String> path) {
        Boolean known = memo.get(item);
        if (known != null) return known;
        if (hidden.contains(item)) return false;
        if (!path.add(item)) return true;
        boolean result = lines.getOrDefault(item, Set.of()).stream().allMatch(c -> clean(c, lines, hidden, memo, path));
        path.remove(item);
        memo.put(item, result);
        return result;
    }

    /** The requests of the run's concern about the parts {@code selection} binds. */
    private List<String> requests(Selection selection) {
        List<String> requests = new ArrayList<>(List.of(FederatedQueries.parts(selection)));
        if (concern.interfaces()) {
            Atelier.KINDS.forEach(kind -> requests.add(FederatedQueries.features(kind, selection)));
        }
        requests.add(FederatedQueries.references(selection));
        if (concern.offers()) requests.add(FederatedQueries.offers(selection));
        return requests;
    }

    /**
     * The far side of every interface of the product with a side in the subtree, outside it: their tags and memberships,
     * then each visible one's attributes and features, by itself.
     */
    private Set<String> context(Set<String> items) {
        Property betweenPart = model.createProperty(Atelier.ONT + "betweenPart");
        Set<String> far = new TreeSet<>();
        for (Resource iface : touching(items)) {
            model.listObjectsOfProperty(iface, betweenPart).forEachRemaining(p -> {
                if (p.isURIResource() && !items.contains(p.asResource().getURI())) far.add(p.asResource().getURI());
            });
        }
        if (far.isEmpty()) return far;
        describe(far);
        List<String> shown = far.stream().filter(this::visible).toList();
        Map<String, List<String>> requests = new LinkedHashMap<>();
        bySite(shown).forEach((plm, sited) -> {
            Selection selection = Selection.only(sited);
            List<String> list = new ArrayList<>(List.of(FederatedQueries.parts(selection)));
            Atelier.KINDS.forEach(kind -> list.add(FederatedQueries.features(kind, selection)));
            requests.put(plm, list);
        });
        model.add(send(requests));
        return far;
    }

    /** The product's interfaces that name an item of the subtree; none outside an interfaces run. */
    private List<Resource> touching(Set<String> items) {
        if (!concern.interfaces()) return List.of();
        Property betweenPart = model.createProperty(Atelier.ONT + "betweenPart");
        Resource productNode = model.createResource(productIri);
        return model.listSubjectsWithProperty(betweenPart).toList().stream()
                .filter(i -> i.hasProperty(model.createProperty(Atelier.OF_PRODUCT), productNode))
                .filter(i -> model.listObjectsOfProperty(i, betweenPart).toList().stream()
                        .anyMatch(p -> p.isURIResource() && items.contains(p.asResource().getURI())))
                .toList();
    }

    /**
     * Cuts the model down to the subtree and its context: the interfaces that touch the subtree (none outside an
     * interfaces run) with their declared features, the items reached and the context parts, and the product.
     */
    private void scope(Set<String> items, Set<String> context) {
        Property betweenPart = model.createProperty(Atelier.ONT + "betweenPart");
        Property declaresFeature = model.createProperty(Atelier.ONT + "declaresFeature");
        Set<Resource> kept = new HashSet<>(touching(items));
        for (Resource iface : model.listSubjectsWithProperty(betweenPart).toList()) {
            if (kept.contains(iface)) continue;
            for (RDFNode feature : model.listObjectsOfProperty(iface, declaresFeature).toList()) {
                if (feature.isURIResource()) model.removeAll(feature.asResource(), null, null);
            }
            model.removeAll(iface, null, null);
        }
        Set<String> keep = new HashSet<>(items);
        keep.addAll(context);
        for (Resource subject : model.listSubjects().toList()) {
            if (subject.isURIResource() && isPart(subject.getURI()) && !keep.contains(subject.getURI())) {
                model.removeAll(subject, null, null);
            }
        }
        Resource productNode = model.createResource(productIri);
        for (Resource other : model.listSubjectsWithProperty(RDF.type, model.createResource(Atelier.ONT + "Product")).toList()) {
            if (other.equals(productNode)) continue;
            model.removeAll(other, null, null);
            model.removeAll(null, partOf, other);
        }
    }

    /** Keeps the lines from the visible items reached, which place their children, hidden ones included. */
    private void keepLines(Model structure, Set<String> reached) {
        for (Resource line : structure.listSubjectsWithProperty(RDF.type, structure.createResource(Atelier.BOM_LINE)).toList()) {
            Statement p = line.getProperty(parent);
            if (p == null || !p.getObject().isURIResource()) continue;
            String from = p.getResource().getURI();
            if (reached.contains(from) && visible(from)) model.add(line.listProperties());
        }
    }

    /** The structure's lines as parent to children. */
    private Map<String, Set<String>> lines(Model structure) {
        Map<String, Set<String>> lines = new HashMap<>();
        for (Resource line : structure.listSubjectsWithProperty(RDF.type, structure.createResource(Atelier.BOM_LINE)).toList()) {
            Statement p = line.getProperty(parent);
            Statement c = line.getProperty(child);
            if (p == null || c == null || !p.getObject().isURIResource() || !c.getObject().isURIResource()) continue;
            lines.computeIfAbsent(p.getResource().getURI(), k -> new LinkedHashSet<>()).add(c.getResource().getURI());
        }
        return lines;
    }

    /** The tags and product memberships of the items not asked about yet, from the core graph. */
    private void describe(Collection<String> items) {
        List<String> ask = items.stream().filter(i -> !described.contains(i)).sorted().toList();
        if (ask.isEmpty()) return;
        described.addAll(ask);
        FederatedQueries.tags(profile, ask).forEach(q -> model.add(core(q)));
        Model products = core(FederatedQueries.products(ask));
        model.add(products);
        copyMemberships(products);
    }

    private void copyMemberships(Model products) {
        memberships.add(products.listStatements(null, partOf, (RDFNode) null).toList());
    }

    private boolean inProduct(String item) {
        return model.contains(model.createResource(item), partOf, model.createResource(productIri));
    }

    private boolean visible(String item) {
        return Redaction.visible(model.createResource(item), profile.releasable(), profile.seesUntagged());
    }

    private static boolean isPart(String iri) {
        String plm = Atelier.plmOf(iri);
        return plm != null && iri.startsWith(Atelier.DATA + plm + "/part/");
    }

    /** The IRIs grouped by the site their IRI names, in IRI order. */
    private static Map<String, List<String>> bySite(Collection<String> iris) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String plm : Atelier.PLMS) {
            List<String> sited = iris.stream().filter(i -> plm.equals(Atelier.plmOf(i))).sorted().toList();
            if (!sited.isEmpty()) out.put(plm, sited);
        }
        return out;
    }

    private Model bySite(Collection<String> iris, java.util.function.BiFunction<String, List<String>, List<String>> requests) {
        Map<String, List<String>> bySite = new LinkedHashMap<>();
        bySite(iris).forEach((plm, sited) -> bySite.put(plm, requests.apply(plm, sited)));
        return send(bySite);
    }

    /** Each site's requests in order, the sites concurrently, merged. */
    private Model send(Map<String, List<String>> requestsBySite) {
        Map<String, List<String>> byUrl = new LinkedHashMap<>();
        for (String plm : Atelier.PLMS) {
            List<String> requests = requestsBySite.get(plm);
            if (requests == null || requests.isEmpty()) continue;
            byUrl.put(endpoints.ontop(plm), requests);
            sent.addAll(requests);
        }
        Model out = ModelFactory.createDefaultModel();
        if (!byUrl.isEmpty()) federator.sendConcurrently(recorder, byUrl).forEach(out::add);
        return out;
    }

    private Model core(String query) {
        sent.add(query);
        return federator.send(recorder, endpoints.ontop(Endpoints.CORE), query);
    }

    private Model link(String query) {
        sent.add(query);
        return federator.send(recorder, endpoints.linkStore(), query);
    }
}
