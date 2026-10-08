// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import atelier.query.api.FunctionJson;
import atelier.query.api.Json;
import atelier.query.api.StationsJson;
import atelier.query.api.VariantJson;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.EvidenceBuilder;
import atelier.query.evidence.OntopSql;
import atelier.query.federation.Federator;
import atelier.query.federation.Subtree;
import atelier.query.federation.UnknownItem;
import atelier.query.mapping.BomMapper;
import atelier.query.mapping.EquivalentsMapper;
import atelier.query.mapping.ItemClasses;
import atelier.query.mapping.ExternalReferences;
import atelier.query.mapping.InterfaceMapper;
import atelier.query.mapping.Lifecycles;
import atelier.query.mapping.PartsFinder;
import atelier.query.mapping.PlacementsMapper;
import atelier.query.mapping.StationsMapper;
import atelier.query.mapping.ReferenceMapper;
import atelier.query.mapping.Subjects;
import atelier.query.mapping.SupplierMapper;
import atelier.query.mapping.Units;
import atelier.query.mapping.UsageMapper;
import atelier.query.paths.FlowWalker;
import atelier.query.paths.GearTrain;
import atelier.query.paths.PathFinder;
import atelier.query.paths.Steps;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.policy.Redaction;
import atelier.query.validation.RuleValidator;
import atelier.query.validation.RuleViolation;
import atelier.query.variants.Configurations;
import atelier.query.variants.UnknownVariant;
import atelier.query.variants.VariantDiff;
import atelier.query.variants.VariantFacts;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.vocabulary.RDF;
import org.springframework.stereotype.Service;

/**
 * Per request: the profile's federation over every interface, or over one product's ({@link Federator}: the link store,
 * the core tags with the viewer's export-control FILTER and the core product membership, then each PLM about the parts the viewer
 * may see), redaction of the merged graph against the same policy, SHACL validation in-process
 * (the interface rules and the data-quality shapes over the parts, with every product membership so the reference
 * shapes can tell a hidden target from a missing one), mapping to the contract JSON.
 * A single-interface answer runs the same federation as the full answer of the profile, so each
 * Ontop endpoint receives request texts the warm-up already had it translate, then validates and
 * maps that interface's {@link Neighbourhood} of the merged graph. The interface is named by an
 * {@link InterfaceRef}: its id and, since ids are unique within a product only, the key of its
 * product when the id alone would be ambiguous. The parts, interfaces, bill-of-materials and references answers can be
 * scoped to the subtree of one item of a product (a root other than the product's own key): the federation resolves
 * the subtree across the sites by roots ({@link Federator#partsUnder}), the shapes run on the subtree (with, for the
 * interfaces, the far sides as context), and the product rules are not evaluated.
 */
@Service
public class QueryService {
    /** The rule whose findings the products answer counts per product. */
    private static final String LIFECYCLE_CONFLICT = "lifecycleConflict";

    private final Federator federator;
    private final RuleValidator validator;
    private final Units units;
    private final Lifecycles lifecycles;
    private final ItemClasses itemClasses;
    private final EvidenceBuilder evidence;
    private final CadUrls cadUrls;
    private final TermsService terms;

    /**
     * A federation result after redaction, the caller it was redacted for, the named parts that carry no tag, and per
     * product IRI the number of its items the caller may not see.
     */
    private record Federated(Federator.Result result, Caller caller, List<String> untagged, Map<String, Integer> hiddenByProduct) {
        /** The policy of an answer over {@code model} (the whole result or one interface's neighbourhood): its untagged parts are those the model names. */
        Json.Policy policy(Model model) {
            return caller.json(untagged.stream().filter(iri -> model.containsResource(model.createResource(iri))).toList());
        }

        /**
         * The results a product rule may state for this caller: a product with an item the caller may not see is not
         * evaluable, so its product-wide results (focus the product: massLimit, massScale) are dropped. A hidden item could
         * carry the mass that breaks the limit, or shift a site's median either way.
         */
        List<RuleViolation> evaluable(List<RuleViolation> results) {
            return results.stream().filter(r -> r.focusNode() == null || !r.focusNode().startsWith(Atelier.PRODUCT)
                    || hiddenByProduct.getOrDefault(r.focusNode(), 0) == 0).toList();
        }
    }

    public QueryService(Federator federator, RuleValidator validator, OntopSql ontopSql, CadUrls cadUrls) {
        this.federator = federator;
        this.validator = validator;
        this.units = new Units(validator.units());
        this.lifecycles = new Lifecycles(validator.lifecycle());
        this.itemClasses = new ItemClasses(validator.ontology());
        this.evidence = new EvidenceBuilder(validator.shapesGraph(), ontopSql);
        this.cadUrls = cadUrls;
        this.terms = new TermsService(federator);
    }

    /** The concepts of the products' glossary {@code q} names, in any of the four languages, with the visible items whose names hold them. */
    public Json.Terms terms(Caller caller, String q) {
        return terms.terms(caller, q);
    }

    /**
     * The parts of one product (or of the subtree of {@code root}) that {@code query} names by what they are, grouped by
     * the assembly they belong to ({@link PartsFinder}), over its bill of materials and the glossary, both redacted for the
     * caller.
     */
    public Json.FoundParts findParts(Caller caller, String product, String query, String root) {
        long start = System.nanoTime();
        List<String> alternatives = PartsFinder.alternatives(query == null ? "" : query);
        if (alternatives.isEmpty()) throw new IllegalArgumentException("query must name the parts: words, alternatives separated by commas");
        Json.Bom bom = bom(caller, product, root);
        TermsService.Resolved terms = this.terms.resolve(caller, alternatives);
        List<Federator.Call> calls = new ArrayList<>(bom.provenance().calls());
        calls.addAll(terms.result().calls());
        return new PartsFinder(query, terms.terms()).find(product, query.strip(), bom.root(), new Json.Provenance(calls),
                bom.sparql() + "\n\n" + terms.result().sparql(),
                new Json.Timings(msSince(start), bom.timings().federationMs() + terms.result().ms(), 0), bom.policy());
    }

    public Json.PartsResponse parts(Caller caller) {
        return parts(caller, null);
    }

    /** The visible parts of one product, or of every product when {@code product} is null. */
    public Json.PartsResponse parts(Caller caller, String product) {
        long start = System.nanoTime();
        Federated fed = redacted(federator.parts(caller.profile(), product), caller);
        Described described = describe(fed);
        return new Json.PartsResponse(described.parts(), new Json.Provenance(fed.result().calls()), fed.result().sparql(),
                new Json.Timings(msSince(start), fed.result().ms(), described.validationMs()), fed.policy(fed.result().model()));
    }

    /** The parts answer of every product with the product keys of each visible part, which the parts listing does not carry. */
    public record PartsByProduct(Json.PartsResponse answer, Map<Json.Part, List<String>> products) {}

    /**
     * {@link #parts(Caller, String)} over every product, with the products the core graph names each visible part in:
     * the failing records of the part rules are counted per product from it.
     */
    public PartsByProduct partsByProduct(Caller caller) {
        long start = System.nanoTime();
        Federated fed = redacted(federator.parts(caller.profile(), null), caller);
        Described described = describe(fed);
        Map<List<String>, List<String>> memberships = new HashMap<>();
        fed.result().memberships().listStatements(null, fed.result().memberships().createProperty(Atelier.PART_OF), (RDFNode) null)
                .forEachRemaining(s -> {
                    if (!s.getObject().isURIResource()) return;
                    String iri = s.getSubject().getURI();
                    memberships.computeIfAbsent(List.of(String.valueOf(Atelier.plmOf(iri)), Atelier.nativeId(iri)), k -> new ArrayList<>())
                            .add(Atelier.nativeId(s.getResource().getURI()));
                });
        Map<Json.Part, List<String>> products = new HashMap<>();
        described.parts().stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast)
                .forEach(p -> products.put(p, memberships.getOrDefault(List.of(p.plm(), p.id()), List.of()).stream().sorted().toList()));
        Json.PartsResponse answer = new Json.PartsResponse(described.parts(), new Json.Provenance(fed.result().calls()), fed.result().sparql(),
                new Json.Timings(msSince(start), fed.result().ms(), described.validationMs()), fed.policy(fed.result().model()));
        return new PartsByProduct(answer, products);
    }

    /**
     * The products the parts belong to, each with the number of its parts (items with geometry) the viewer may see, as
     * {@link #products(Caller)} lists them, without the product rules: a federation of every product's parts with each
     * PLM's description of them, and no validation, so the list answers before the findings.
     */
    public Json.ProductListResponse productList(Caller caller) {
        long start = System.nanoTime();
        Federated fed = redacted(federator.listing(caller.profile()), caller);
        Model model = fed.result().model();
        List<Json.ProductListing> products = productsOf(model).stream()
                .map(p -> new Json.ProductListing(Atelier.nativeId(p.getURI()), string(p, "label"), string(p, "frame"), partCount(p)))
                .toList();
        return new Json.ProductListResponse(products, new Json.Provenance(fed.result().calls()), fed.result().sparql(),
                new Json.Timings(msSince(start), fed.result().ms(), 0), fed.policy(model));
    }

    /**
     * The products the parts belong to, each with the number of its parts (items with geometry) the viewer may see and the
     * findings of the product rules, each once (massLimit; massScale, whose results also name each part of the site), from
     * the bill-of-materials federation of every product: the core graph names the product of each item and its mass limit,
     * the redaction leaves the visible items with their membership, and each item's occurrences are derived from the lines
     * before the shapes run. The lines and the references the federation reads are also the dependencies of the lifecycle
     * rule, whose findings on each product's items are counted.
     */
    public Json.ProductsResponse products(Caller caller) {
        long start = System.nanoTime();
        Federated fed = redacted(federator.bom(caller.profile(), null), caller);
        Model model = fed.result().model();
        BomMapper.deriveOccurrences(model);
        long validationStart = System.nanoTime();
        List<RuleViolation> results = fed.evaluable(validator.validate(model));
        Map<String, Set<Json.Finding>> findings = results.stream()
                .filter(r -> r.focusNode() != null && r.focusNode().startsWith(Atelier.PRODUCT))
                .collect(Collectors.groupingBy(RuleViolation::focusNode,
                        Collectors.mapping(r -> new Json.Finding(r.rule(), r.message(), null), Collectors.toCollection(LinkedHashSet::new))));
        long validationMs = msSince(validationStart);
        Property partOf = model.createProperty(Atelier.PART_OF);
        Map<String, Long> conflicts = results.stream().filter(r -> LIFECYCLE_CONFLICT.equals(r.rule()) && r.value() != null)
                .map(r -> List.of(r.focusNode(), r.value().getURI())).distinct()
                .flatMap(pair -> model.listObjectsOfProperty(model.createResource(pair.get(0)), partOf).toList().stream())
                .filter(RDFNode::isURIResource)
                .collect(Collectors.groupingBy(product -> product.asResource().getURI(), Collectors.counting()));
        List<Json.Product> products = productsOf(model).stream()
                .map(p -> new Json.Product(Atelier.nativeId(p.getURI()), string(p, "label"), string(p, "frame"), partCount(p),
                        List.copyOf(findings.getOrDefault(p.getURI(), Set.of())), massLimit(p, fed, results),
                        conflicts.getOrDefault(p.getURI(), 0L).intValue()))
                .toList();
        return new Json.ProductsResponse(products, new Json.Provenance(fed.result().calls()), fed.result().sparql(),
                new Json.Timings(msSince(start), fed.result().ms(), validationMs), fed.policy(model));
    }

    /** The products of the merged graph, by key. */
    private static List<Resource> productsOf(Model model) {
        return model.listSubjectsWithProperty(RDF.type, model.createResource(Atelier.ONT + "Product")).toList().stream()
                .sorted(Comparator.comparing(p -> Atelier.nativeId(p.getURI()))).toList();
    }

    /** The product's parts with geometry the viewer may see: the visible items the product holds that a PLM describes as parts. */
    private static int partCount(Resource product) {
        Model model = product.getModel();
        return model.listSubjectsWithProperty(model.createProperty(Atelier.PART_OF), product).filterKeep(QueryService::geometric).toList().size();
    }

    /**
     * The product's mass-limit status for the caller, null when the product states no limit: not-evaluable with the number
     * of hidden items when the caller may not see every item, as an interface with a redacted side; else fail when the
     * massLimit shape reports the product, pass otherwise.
     */
    private Json.MassLimit massLimit(Resource product, Federated fed, List<RuleViolation> results) {
        Statement limit = product.getProperty(product.getModel().createProperty(Atelier.ONT + "massLimit"));
        if (limit == null || !limit.getObject().isResource()) return null;
        Resource quantity = limit.getResource();
        Statement value = quantity.getProperty(quantity.getModel().createProperty(Atelier.QUDT + "numericValue"));
        Statement unit = quantity.getProperty(quantity.getModel().createProperty(Atelier.QUDT + "unit"));
        if (value == null || unit == null) return null;
        double kg = units.toKg(new BigDecimal(value.getLiteral().getLexicalForm()), unit.getResource().getURI()).doubleValue();
        int hidden = fed.hiddenByProduct().getOrDefault(product.getURI(), 0);
        if (hidden > 0) return new Json.MassLimit("not-evaluable", kg, hidden);
        boolean fails = results.stream().anyMatch(r -> "massLimit".equals(r.rule()) && product.getURI().equals(r.focusNode()));
        return new Json.MassLimit(fails ? "fail" : "pass", kg, null);
    }

    /** A visible part with geometry: an assembly, a site kit or a software or document item is not counted as a part. */
    private static boolean geometric(Resource part) {
        String type = string(part, "partType");
        return part.hasProperty(RDF.type) && (type == null || "PART".equals(type));
    }

    private static String string(Resource subject, String localName) {
        Statement statement = subject.getProperty(subject.getModel().createProperty(Atelier.ONT + localName));
        return statement == null || !statement.getObject().isLiteral() ? null : statement.getString();
    }

    public Json.InterfacesResponse interfaces(Caller caller) {
        return interfaces(caller, null);
    }

    /** The interfaces of one product, or of every product when {@code product} is null. */
    public Json.InterfacesResponse interfaces(Caller caller, String product) {
        Answer answer = answer(caller, product);
        return new Json.InterfacesResponse(answer.interfaces(), answer.provenance(), answer.sparql(),
                answer.timings(), answer.policy(), answer.findings());
    }

    /** The interface of the id, when exactly one product has it; empty when none does. */
    public Optional<Json.InterfaceResponse> interfaceById(String id, Caller caller) {
        return interfaceById(new InterfaceRef(id, null), caller);
    }

    /** The interface the reference names, as the viewer sees it; empty when no interface matches. */
    public Optional<Json.InterfaceResponse> interfaceById(InterfaceRef ref, Caller caller) {
        return answer(ref, caller).map(a -> new Json.InterfaceResponse(a.interfaces().get(0), a.provenance(), a.sparql(),
                a.timings(), a.policy()));
    }

    /** Evidence behind {@link #interfaceById(String, Caller)}: the same pipeline, with its intermediate material serialised. */
    public Optional<Json.Evidence> evidence(String id, Caller caller) {
        return evidence(new InterfaceRef(id, null), caller);
    }

    /** Evidence behind {@link #interfaceById(InterfaceRef, Caller)}. */
    public Optional<Json.Evidence> evidence(InterfaceRef ref, Caller caller) {
        return answer(ref, caller).map(a -> evidence.build(a.interfaces().get(0), a));
    }

    /** Where a part (native id or IRI) is used, from the profile's full answer; empty when no interface names it. */
    public Optional<Json.WhereUsed> whereUsed(String part, Caller caller) {
        return whereUsed(part, caller, null);
    }

    /** As {@link #whereUsed(String, Caller)}, among the interfaces of one product when {@code product} is non-null. */
    public Optional<Json.WhereUsed> whereUsed(String part, Caller caller, String product) {
        Answer answer = answer(caller, product);
        UsageMapper usage = new UsageMapper(answer.interfaces());
        return new Subjects(answer.model()).part(part, answer.parts()).map(view -> view instanceof Json.Part p
                ? new Json.WhereUsed(p, usage.whereUsed(p), usage.featureCounts(p), answer.provenance(), answer.sparql(),
                        answer.timings(), answer.policy())
                : new Json.WhereUsed(view, List.of(), Map.of(), answer.provenance(), answer.sparql(), answer.timings(),
                        answer.policy()));
    }

    /** The impact of a change to a part or, when {@code part} is null, to a feature; empty when no interface names the subject. */
    public Optional<Json.Impact> impact(String part, String feature, Caller caller) {
        return impact(part, feature, caller, null);
    }

    /** As {@link #impact(String, String, Caller)}, among the interfaces of one product when {@code product} is non-null. */
    public Optional<Json.Impact> impact(String part, String feature, Caller caller, String product) {
        Answer answer = answer(caller, product);
        UsageMapper usage = new UsageMapper(answer.interfaces());
        Subjects subjects = new Subjects(answer.model());
        if (part != null) {
            return subjects.part(part, answer.parts()).map(view -> new Json.Impact(view, null,
                    view instanceof Json.Part p ? usage.impactOfPart(p) : List.of(),
                    answer.provenance(), answer.sparql(), answer.timings(), answer.policy()));
        }
        return subjects.feature(feature, answer.interfaces()).map(view -> new Json.Impact(null, view,
                view instanceof Json.Feature f ? usage.impactOfFeature(f) : List.of(),
                answer.provenance(), answer.sparql(), answer.timings(), answer.policy()));
    }

    /** Export-control status of a part for the profile, from the parts federation; empty when no interface names it. */
    public Optional<Json.ExportStatus> exportStatus(String part, Caller caller) {
        long start = System.nanoTime();
        Federated fed = redacted(federator.parts(caller.profile()), caller);
        Described described = describe(fed);
        List<Json.Part> parts = described.parts().stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast).toList();
        return new Subjects(fed.result().model()).part(part, parts).map(view -> new Json.ExportStatus(view,
                view instanceof Json.Part, view instanceof Json.Part p && p.cadUrl() != null,
                new Json.Provenance(fed.result().calls()), fed.result().sparql(),
                new Json.Timings(msSince(start), fed.result().ms(), described.validationMs()), fed.policy(fed.result().model())));
    }

    /**
     * The virtual bill of materials of one product: the bill-of-materials federation (the product's parts and the lines
     * from and to each PLM's visible items), redacted for the caller, then the product root, the site kits and their
     * trees with the roll-ups ({@link BomMapper}).
     */
    public Json.Bom bom(Caller caller, String product) {
        long start = System.nanoTime();
        Federated fed = bomFederation(caller, product);
        return new BomMapper(fed.result().model(), units, lifecycles).bom(Atelier.productIri(product),
                new Json.Provenance(fed.result().calls()), fed.result().sparql(),
                new Json.Timings(msSince(start), fed.result().ms(), 0), fed.policy(fed.result().model()));
    }

    /**
     * The world placements of the visible geometric parts of one product, or of the subtree of {@code root}: the
     * bill-of-materials federation with one placements request per site by its roots, redacted for the caller, composed
     * down the trees from the site kits or the subtree's roots ({@link PlacementsMapper}).
     */
    public Json.Placements placements(Caller caller, String product, String root) {
        long start = System.nanoTime();
        if (isSubtree(product, root)) {
            Federator.SubtreeRun run = federator.placementsUnder(caller.profile(), product, root);
            Federated fed = redacted(run.result(), caller);
            Model model = fed.result().model();
            List<String> roots = run.subtree().rounds().stream().flatMap(r -> r.roots().stream()).toList();
            return new PlacementsMapper(model, units).placements(product, roots, new Json.Provenance(fed.result().calls()),
                    fed.result().sparql(), new Json.Timings(msSince(start), fed.result().ms(), 0), fed.policy(model),
                    subtree(run.subtree(), model));
        }
        if (product == null) throw new IllegalArgumentException("placements are per product: name the product");
        Federated fed = redacted(federator.placements(caller.profile(), product), caller);
        Model model = fed.result().model();
        return new PlacementsMapper(model, units).placements(product, siteKits(model, product), new Json.Provenance(fed.result().calls()),
                fed.result().sparql(), new Json.Timings(msSince(start), fed.result().ms(), 0), fed.policy(model), null);
    }

    /**
     * The visible parts of one product whose span overlaps the range between two stations, on one side or both, inside it
     * or crossing an end, with the occurrences of a placed part in the range ({@link StationsMapper}): the stations run of
     * the product, redacted for the caller, composed down the trees from the site kits.
     */
    public StationsJson.Between stations(Caller caller, String product, String from, String to, String side) {
        long start = System.nanoTime();
        Federated fed = redacted(federator.stations(caller.profile(), product), caller);
        Model model = fed.result().model();
        return new StationsMapper(model, units, product, siteKits(model, product)).between(product, from, to, side,
                new Json.Provenance(fed.result().calls()), fed.result().sparql(), new Json.Timings(msSince(start), fed.result().ms(), 0),
                fed.policy(model));
    }

    /** The sections of one product with their owners, parts and foreign parts, and the parts crossing each joint. */
    public StationsJson.Sections sections(Caller caller, String product) {
        long start = System.nanoTime();
        Federated fed = redacted(federator.stations(caller.profile(), product), caller);
        Model model = fed.result().model();
        return new StationsMapper(model, units, product, siteKits(model, product)).sections(product,
                new Json.Provenance(fed.result().calls()), fed.result().sparql(), new Json.Timings(msSince(start), fed.result().ms(), 0),
                fed.policy(model));
    }

    /** The product's items no line reaches: each site's kit. */
    private static List<String> siteKits(Model model, String product) {
        Property child = model.createProperty(Atelier.ONT + "child");
        Set<String> reached = new HashSet<>();
        model.listObjectsOfProperty(child).forEachRemaining(c -> {
            if (c.isURIResource()) reached.add(c.asResource().getURI());
        });
        return model.listSubjectsWithProperty(model.createProperty(Atelier.PART_OF), model.createResource(Atelier.productIri(product)))
                .toList().stream().map(Resource::getURI).filter(iri -> !reached.contains(iri)).sorted().toList();
    }

    /** Where a part is used in a product's bill of materials; empty when the part is not an item of the product. */
    public Optional<Json.BomWhereUsed> bomWhereUsed(String part, Caller caller, String product) {
        long start = System.nanoTime();
        Federated fed = bomFederation(caller, product);
        Model model = fed.result().model();
        BomMapper mapper = new BomMapper(model, units, lifecycles);
        mapper.bom(Atelier.productIri(product), null, null, null, null);
        List<Json.Part> described = new InterfaceMapper(model, units, lifecycles, List.of(), cadUrls::presign).parts().stream()
                .filter(Json.Part.class::isInstance).map(Json.Part.class::cast).toList();
        Json.Timings timings = new Json.Timings(msSince(start), fed.result().ms(), 0);
        Subjects subjects = new Subjects(model);
        return subjects.part(part, described).map(view -> {
            String iri = view instanceof Json.Part ? subjects.partIri(part).orElseThrow() : null;
            return new Json.BomWhereUsed(product, view, iri == null ? List.of() : mapper.usedIn(iri),
                    iri == null ? 0 : mapper.occurrences(iri), new Json.Provenance(fed.result().calls()), fed.result().sparql(),
                    timings, fed.policy(model));
        });
    }

    private Federated bomFederation(Caller caller, String product) {
        if (product == null) throw new IllegalArgumentException("a bill of materials is per product: name the product");
        Federated fed = redacted(federator.bom(caller.profile(), product), caller);
        BomMapper.deriveAssembles(fed.result().model());
        return fed;
    }

    /**
     * The purchased items one product's parts share across the sites: the parts federation of the product, redacted for
     * the caller, grouped by item class and by the attributes the class's identifying attributes name ({@link EquivalentsMapper}).
     */
    public Json.Equivalents equivalents(Caller caller, String product) {
        long start = System.nanoTime();
        if (product == null) throw new IllegalArgumentException("purchased items and suppliers are per product: name the product");
        Federated fed = redacted(federator.purchased(caller.profile(), product), caller);
        List<Json.EquivalentGroup> groups = new EquivalentsMapper(fed.result().model(), units, itemClasses).groups();
        return new Json.Equivalents(product, groups, new Json.Provenance(fed.result().calls()), fed.result().sparql(),
                new Json.Timings(msSince(start), fed.result().ms(), 0), fed.policy(fed.result().model()));
    }

    /**
     * The suppliers of one product's parts: the parts federation of the product with each PLM's supplier offers for its
     * visible parts, redacted for the caller and validated, so the lead-time conflicts are the parts' findings
     * ({@link SupplierMapper}).
     */
    public Json.Suppliers suppliers(Caller caller, String product) {
        long start = System.nanoTime();
        Federated fed = productParts(caller, product);
        Described described = describe(fed);
        List<Json.Part> parts = described.parts().stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast).toList();
        return new SupplierMapper(fed.result().model()).suppliers(product, parts, new Json.Provenance(fed.result().calls()),
                fed.result().sparql(), new Json.Timings(msSince(start), fed.result().ms(), described.validationMs()),
                fed.policy(fed.result().model()));
    }

    private Federated productParts(Caller caller, String product) {
        if (product == null) throw new IllegalArgumentException("purchased items and suppliers are per product: name the product");
        return redacted(federator.parts(caller.profile(), product), caller);
    }

    /** True when {@code root} scopes an answer to one item's subtree: given, and not the product root (the product's own key). */
    public static boolean isSubtree(String product, String root) {
        if (root == null) return false;
        if (product == null) throw new IllegalArgumentException("a root names an item of a product: name the product");
        return !root.equals(product);
    }

    /** The visible parts of the subtree of {@code root}, then a redaction marker per hidden item of it; the product's parts for the product root. */
    public Json.PartsResponse parts(Caller caller, String product, String root) {
        if (!isSubtree(product, root)) return parts(caller, product);
        long start = System.nanoTime();
        Federator.SubtreeRun run = federator.partsUnder(caller.profile(), product, root);
        Federated fed = redacted(run.result(), caller);
        Model model = fed.result().model();
        long validationStart = System.nanoTime();
        List<RuleViolation> results = scoped(validator.validate(withMemberships(fed, model)), run.subtree());
        long validationMs = msSince(validationStart);
        List<Json.PartView> parts = new ArrayList<>(new InterfaceMapper(model, units, lifecycles, results, cadUrls::presign).parts());
        run.subtree().hidden().stream().sorted().forEach(iri -> parts.add(new Json.Redacted(true, Atelier.plmOf(iri))));
        return new Json.PartsResponse(parts, new Json.Provenance(fed.result().calls()), fed.result().sparql(),
                new Json.Timings(msSince(start), fed.result().ms(), validationMs), fed.policy(model), subtree(run.subtree(), model));
    }

    /**
     * The interfaces of the product with at least one side in the subtree of {@code root}, validated on the subtree and
     * the far sides, which come as context; the product's interfaces for the product root.
     */
    public Json.InterfacesResponse interfaces(Caller caller, String product, String root) {
        if (!isSubtree(product, root)) return interfaces(caller, product);
        long start = System.nanoTime();
        Federator.SubtreeRun run = federator.interfacesUnder(caller.profile(), product, root);
        Federated fed = redacted(run.result(), caller);
        Model model = fed.result().model();
        long validationStart = System.nanoTime();
        List<RuleViolation> results = scoped(validator.violations(validator.report(withMemberships(fed, model))), run.subtree());
        long validationMs = msSince(validationStart);
        InterfaceMapper mapper = new InterfaceMapper(model, units, lifecycles, results, cadUrls::presign).withContext(run.subtree().context());
        return new Json.InterfacesResponse(mapper.interfaces(), new Json.Provenance(fed.result().calls()), fed.result().sparql(),
                new Json.Timings(msSince(start), fed.result().ms(), validationMs), fed.policy(model), mapper.findings(),
                subtree(run.subtree(), model));
    }

    /**
     * The bill of materials under {@code root}: its site's tree and, through the external references of its items, the
     * trees of the items they name on the other sites; the product's bill of materials for the product root.
     */
    public Json.Bom bom(Caller caller, String product, String root) {
        if (!isSubtree(product, root)) return bom(caller, product);
        long start = System.nanoTime();
        Federator.SubtreeRun run = federator.bomUnder(caller.profile(), product, root);
        Federated fed = redacted(run.result(), caller);
        Model model = fed.result().model();
        BomMapper.deriveAssembles(model);
        return new BomMapper(model, units, lifecycles).subtree(product, run.subtree().root(), crossings(model, run.subtree()),
                new Json.Provenance(fed.result().calls()), fed.result().sparql(), new Json.Timings(msSince(start), fed.result().ms(), 0),
                fed.policy(model), subtree(run.subtree(), model));
    }

    /** The external references the visible items of the subtree of {@code root} make; the product's for the product root. */
    public Json.References references(Caller caller, String product, String root) {
        if (!isSubtree(product, root)) return references(caller, product);
        long start = System.nanoTime();
        Federator.SubtreeRun run = federator.partsUnder(caller.profile(), product, root);
        Federated fed = redacted(run.result(), caller);
        Model model = fed.result().model();
        long validationStart = System.nanoTime();
        List<RuleViolation> results = scoped(validator.validate(withMemberships(fed, model)), run.subtree());
        long validationMs = msSince(validationStart);
        List<Json.Reference> references = new ReferenceMapper(model, lifecycles, results).references();
        return new Json.References(product, references, ReferenceMapper.counts(references), new Json.Provenance(fed.result().calls()),
                fed.result().sparql(), new Json.Timings(msSince(start), fed.result().ms(), validationMs), fed.policy(model),
                subtree(run.subtree(), model));
    }

    /**
     * The results a subtree answer states: none whose focus is the product (the product rules need every item of the
     * product) and none on a context part (outside the subtree, it is shown for its interface only).
     */
    private static List<RuleViolation> scoped(List<RuleViolation> results, Subtree subtree) {
        return results.stream().filter(r -> r.focusNode() == null
                || !r.focusNode().startsWith(Atelier.PRODUCT) && !subtree.context().contains(r.focusNode())).toList();
    }

    /** Per visible item of the subtree, the items of another site its references name, with each reference's quantity. */
    private static Map<String, Map<String, BigDecimal>> crossings(Model model, Subtree subtree) {
        Map<String, Map<String, BigDecimal>> out = new HashMap<>();
        Property fromPart = model.createProperty(Atelier.FROM_PART);
        Property quantity = model.createProperty(Atelier.ONT + "quantity");
        ExternalReferences.targets(model).forEach((ref, target) -> {
            Resource reference = model.createResource(ref);
            Statement from = reference.getProperty(fromPart);
            if (from == null || !from.getObject().isURIResource() || !subtree.items().contains(target)) return;
            Statement q = reference.getProperty(quantity);
            BigDecimal n = q == null || !q.getObject().isLiteral() ? BigDecimal.ONE : new BigDecimal(q.getLiteral().getLexicalForm());
            out.computeIfAbsent(from.getResource().getURI(), k -> new java.util.LinkedHashMap<>()).merge(target, n, BigDecimal::add);
        });
        return out;
    }

    /** How the subtree was resolved, as the answer reports it; the root's name only when the viewer may see the root. */
    private static Json.Subtree subtree(Subtree subtree, Model model) {
        Resource root = model.createResource(subtree.root());
        return new Json.Subtree(Atelier.nativeId(subtree.root()), Atelier.plmOf(subtree.root()), Atelier.nativeId(subtree.product()),
                subtree.redacted() ? null : string(root, "label"), subtree.redacted(), subtree.depth(),
                subtree.rounds().stream().map(r -> new Json.SubtreeRound(r.round(), roots(r.roots()), r.items())).toList(),
                subtree.items().size() - subtree.hidden().size(), subtree.context().size(), "not-evaluable", roots(subtree.unresolved()));
    }

    private static List<Json.SubtreeRoot> roots(java.util.Collection<String> iris) {
        return iris.stream().sorted().map(iri -> new Json.SubtreeRoot(Atelier.nativeId(iri), Atelier.plmOf(iri))).toList();
    }

    /** The visible parts of a parts federation with their data-quality findings, and the time the shapes took. */
    private record Described(List<Json.PartView> parts, long validationMs) {}

    private Described describe(Federated fed) {
        return describe(fed, fed.result().model());
    }

    private Described describe(Federated fed, Model model) {
        long start = System.nanoTime();
        List<RuleViolation> results = fed.evaluable(validator.validate(withMemberships(fed, model)));
        long validationMs = msSince(start);
        return new Described(new InterfaceMapper(model, units, lifecycles, results, cadUrls::presign).parts(), validationMs);
    }

    /** The profile's federation over every interface, redacted, then the whole merged graph validated and mapped. */
    public Answer answer(Caller caller) {
        return answer(caller, null);
    }

    /** As {@link #answer(Caller)}, over the interfaces of one product when {@code product} is non-null. */
    public Answer answer(Caller caller, String product) {
        long start = System.nanoTime();
        Federated fed = redacted(federator.interfaces(caller.profile(), product), caller);
        return answer(fed, fed.result().model(), start);
    }

    /**
     * The profile's federation over every interface, redacted (the run of {@link #answer(Caller)}), then
     * the {@link Neighbourhood} of the interface the reference names, validated and mapped; empty when
     * no interface matches the reference. The mapper reads an orphan's candidate mates from the whole
     * redacted graph: the features on the facing part that no interface declares are outside the
     * neighbourhood.
     */
    public Optional<Answer> answer(InterfaceRef ref, Caller caller) {
        long start = System.nanoTime();
        Federated fed = redacted(federator.interfaces(caller.profile(), null), caller);
        Model merged = fed.result().model();
        return ref.resolve(merged).map(iri -> answer(fed, Neighbourhood.of(merged, iri), start));
    }

    /** {@code model} (the merged graph or one neighbourhood of it) validated and mapped, timed from {@code start}. */
    private Answer answer(Federated fed, Model model, long start) {
        long validationStart = System.nanoTime();
        ValidationReport report = validator.report(withMemberships(fed, model));
        List<RuleViolation> results = fed.evaluable(validator.violations(report));
        long validationMs = msSince(validationStart);
        InterfaceMapper mapper = new InterfaceMapper(model, fed.result().model(), units, lifecycles, results, cadUrls::presign);
        return new Answer(mapper.interfaces(), fed.result(), model, report, mapper.findings(),
                new Json.Timings(msSince(start), fed.result().ms(), validationMs), fed.policy(model));
    }

    /**
     * {@code model} with every product membership the core graph returned, whatever the viewer may see or the product
     * the run is scoped to: the reference shapes read from it whether a reference's target exists.
     */
    private static Model withMemberships(Federated fed, Model model) {
        return ModelFactory.createUnion(model, fed.result().memberships());
    }

    /** A flow run of one product, redacted, validated and mapped: its interfaces, every part it names, and the times. */
    private record Flowed(Federated fed, Model model, List<Json.Interface> interfaces, List<Json.PartView> parts, long start, long validationMs) {
        Json.Timings timings() {
            return new Json.Timings(msSince(start), fed.result().ms(), validationMs);
        }

        /** The part a request names, visible or redacted; an id that names no part of the product is refused. */
        Json.PartView part(String ref, String product) {
            List<Json.Part> described = parts.stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast).toList();
            return new Subjects(model).part(ref, described).orElseThrow(() -> new UnknownItem(ref, product));
        }

        String iri(String ref) {
            return new Subjects(model).partIri(ref).orElseThrow();
        }

        /** The described parts of the IRIs, in their order. */
        List<Json.Part> described(List<String> iris) {
            return iris.stream().flatMap(iri -> parts.stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast)
                    .filter(p -> p.plm().equals(Atelier.plmOf(iri)) && p.id().equals(Atelier.nativeId(iri))).limit(1)).toList();
        }
    }

    private Flowed flowed(Caller caller, String product) {
        long start = System.nanoTime();
        Federated fed = redacted(federator.flow(caller.profile(), product), caller);
        Model model = fed.result().model();
        long validationStart = System.nanoTime();
        List<RuleViolation> results = fed.evaluable(validator.violations(validator.report(withMemberships(fed, model))));
        long validationMs = msSince(validationStart);
        InterfaceMapper mapper = new InterfaceMapper(model, units, lifecycles, results, cadUrls::presign);
        return new Flowed(fed, model, mapper.interfaces(), mapper.parts(), start, validationMs);
    }

    /**
     * The shortest paths of parts joined by interfaces between two parts of a product ({@link PathFinder}): the flow run
     * of the product, redacted and validated, so each step carries its interface's status for the viewer's profile. A
     * hidden end part answers with its redaction marker and no path.
     */
    public FunctionJson.Paths paths(Caller caller, String product, String from, String to) {
        Flowed f = flowed(caller, product);
        Json.PartView a = f.part(from, product);
        Json.PartView b = f.part(to, product);
        Json.Provenance provenance = new Json.Provenance(f.fed().result().calls());
        if (!(a instanceof Json.Part) || !(b instanceof Json.Part)) {
            return new FunctionJson.Paths(product, a, b, PathFinder.MAX_LENGTH, PathFinder.MAX_PATHS, List.of(),
                    List.of("An end of the path is a part not visible to your profile."), List.of(), provenance, f.fed().result().sparql(),
                    f.timings(), f.fed().policy(f.model()));
        }
        PathFinder.Found found = new PathFinder(f.model(), new Steps(f.model(), f.interfaces())).between(f.iri(from), f.iri(to));
        return new FunctionJson.Paths(product, a, b, PathFinder.MAX_LENGTH, PathFinder.MAX_PATHS, found.paths(), found.notes(),
                f.described(found.parts().isEmpty() ? List.of(f.iri(from), f.iri(to)) : found.parts()), provenance,
                f.fed().result().sparql(), f.timings(), f.fed().policy(f.model()));
    }

    /**
     * The walk along the product's functional edges from a part ({@link FlowWalker}), downstream or, with {@code up},
     * upstream, of one flow kind or every kind; downstream, the gear ratio and the rated-speed checks along it
     * ({@link GearTrain}). The flow run of the product, redacted and validated, so each step's interface carries its
     * status and each part its findings (meshModule on a gear driven by a gear of another module).
     */
    public FunctionJson.Flow flow(Caller caller, String product, String from, String flow, boolean up) {
        Flowed f = flowed(caller, product);
        Json.PartView start = f.part(from, product);
        Json.Provenance provenance = new Json.Provenance(f.fed().result().calls());
        String direction = up ? "up" : "down";
        if (!(start instanceof Json.Part)) {
            return new FunctionJson.Flow(product, start, flow, direction, FlowWalker.MAX_STEPS, List.of(), null, List.of(),
                    List.of("The start is a part not visible to your profile."), List.of(), provenance, f.fed().result().sparql(),
                    f.timings(), f.fed().policy(f.model()));
        }
        FlowWalker.Walk walk = new FlowWalker(f.model()).walk(f.iri(from), flow, up);
        GearTrain train = new GearTrain(f.model(), units);
        Steps steps = new Steps(f.model(), f.interfaces());
        GearTrain.Result computed = up ? new GearTrain.Result(null, List.of(), List.of()) : train.along(walk);
        List<String> notes = new ArrayList<>(walk.notes());
        notes.addAll(computed.notes());
        return new FunctionJson.Flow(product, start, flow, direction, FlowWalker.MAX_STEPS,
                walk.edges().stream().map(d -> FlowWalker.step(d, steps, train)).toList(), computed.ratio(), computed.checks(), notes,
                f.described(java.util.stream.Stream.concat(walk.parts().stream(), walk.edges().stream().map(FlowWalker.Drive::reaction)
                        .filter(java.util.Objects::nonNull)).distinct().toList()), provenance, f.fed().result().sparql(), f.timings(), f.fed().policy(f.model()));
    }

    /**
     * The external references of one product's visible parts: the parts federation (which asks each PLM for the
     * references its visible parts make), redacted, validated with the product memberships, then each reference with
     * its target and the shapes' verdict ({@link ReferenceMapper}).
     */
    public Json.References references(Caller caller, String product) {
        if (product == null) throw new IllegalArgumentException("references are per product: name the product");
        long start = System.nanoTime();
        Federated fed = redacted(federator.parts(caller.profile(), product), caller);
        Model model = fed.result().model();
        long validationStart = System.nanoTime();
        List<RuleViolation> results = validator.validate(withMemberships(fed, model));
        long validationMs = msSince(validationStart);
        List<Json.Reference> references = new ReferenceMapper(model, lifecycles, results).references();
        return new Json.References(product, references, ReferenceMapper.counts(references), new Json.Provenance(fed.result().calls()),
                fed.result().sparql(), new Json.Timings(msSince(start), fed.result().ms(), validationMs), fed.policy(model));
    }

    /** One side of a preview: the interfaces, the visible parts with their findings, and per product key its own findings. */
    public record Rules(List<Json.Interface> interfaces, List<Json.Part> parts, Map<String, List<Json.Finding>> products) {}

    /** A preview run: the rules before and after the edit, what the edit returned, and the run's provenance, timings and policy. */
    public record PreviewRun<T>(Rules before, Rules after, T edits, Json.Provenance provenance, Json.Timings timings, Json.Policy policy) {}

    /**
     * The rules of one product, or of the subtree of {@code root}, on the merged graph as the caller may see it and on a copy
     * that {@code edit} has rewritten: the federation of the interfaces answer with the supplier offers (every rule's
     * input), redacted once, then each graph with its occurrences derived, validated with every product membership and
     * mapped as the answers map it. Nothing is written anywhere: the copy lives for this call.
     */
    public <T> PreviewRun<T> preview(Caller caller, String product, String root, Function<Model, T> edit) {
        if (product == null) throw new IllegalArgumentException("a preview is per product: name the product");
        long start = System.nanoTime();
        Subtree subtree = null;
        Federated fed;
        if (isSubtree(product, root)) {
            Federator.SubtreeRun run = federator.previewUnder(caller.profile(), product, root);
            fed = redacted(run.result(), caller);
            subtree = run.subtree();
        } else {
            fed = redacted(federator.preview(caller.profile(), product), caller);
        }
        Model before = fed.result().model();
        Model after = ModelFactory.createDefaultModel().add(before);
        T edits = edit.apply(after);
        long validationStart = System.nanoTime();
        Rules was = rules(fed, before, subtree);
        Rules is = rules(fed, after, subtree);
        return new PreviewRun<>(was, is, edits, new Json.Provenance(fed.result().calls()),
                new Json.Timings(msSince(start), fed.result().ms(), msSince(validationStart)), fed.policy(before));
    }

    /**
     * One option of a variant group of a product against the group's default: the variant federation (the product as the
     * preview reads it, with the group's options from the options graph), the group read before redaction, the redacted
     * graph split into the two configurations, each validated with its occurrences derived as the preview validates, then
     * the ports compared ({@link VariantDiff}). The default option is the base product and has no diff of its own.
     */
    public VariantJson.Diff variantDiff(Caller caller, String product, String group, String code) {
        long start = System.nanoTime();
        String variantIri = Atelier.variantIri(product, group);
        if (!Atelier.isKey(code)) throw new IllegalArgumentException("invalid option code");
        Federator.Result raw = federator.variant(caller.profile(), product, variantIri);
        VariantFacts facts = VariantFacts.read(raw.model(), variantIri);
        if (facts == null || facts.option(code) == null) throw new UnknownVariant(product, group, code, variantGroups(product));
        VariantFacts.Option option = facts.option(code);
        if (option.isDefault()) {
            throw new IllegalArgumentException(code + " is the default option of " + group + ", the base product; name another option");
        }
        Federated fed = redacted(raw, caller);
        Model base = Configurations.of(fed.result().model(), facts, facts.fallback());
        Model taken = Configurations.of(fed.result().model(), facts, option);
        long validationStart = System.nanoTime();
        Rules against = rules(fed, base, null);
        Rules under = rules(fed, taken, null);
        long validationMs = msSince(validationStart);
        VariantDiff diff = new VariantDiff(facts, option);
        String productIri = Atelier.productIri(product);
        Set<String> own = Set.copyOf(facts.interfaces(option));
        List<Json.Interface> interfaces = under.interfaces().stream()
                .filter(i -> own.contains(Atelier.interfaceIri(i.product(), i.id()))).toList();
        return new VariantJson.Diff(product,
                new VariantJson.Group(facts.key(), facts.name(), facts.selects(), facts.fallback().key(),
                        facts.options().stream().map(VariantFacts.Option::key).toList()),
                json(option), json(facts.fallback()), diff.ports(against.interfaces(), under.interfaces()),
                diff.items(facts.fallback(), base), diff.items(option, taken),
                VariantDiff.configuration(option, under.interfaces(), taken, productIri, units, under.products().getOrDefault(product, List.of())),
                VariantDiff.configuration(facts.fallback(), against.interfaces(), base, productIri, units,
                        against.products().getOrDefault(product, List.of())),
                interfaces, new Json.Provenance(fed.result().calls()), fed.result().sparql(),
                new Json.Timings(msSince(start), fed.result().ms(), validationMs), fed.policy(fed.result().model()));
    }

    /**
     * The visible parts of one product as it is under one option of a variant group: the parts run with the group's options,
     * redacted, then split into the option's configuration as the variant diff splits it ({@link Configurations}). The
     * default option's configuration is the base product. Without {@code option}, {@link #parts(Caller, String, String)}.
     */
    public Json.PartsResponse parts(Caller caller, String product, String root, String option) {
        if (option == null) return parts(caller, product, root);
        long start = System.nanoTime();
        Configured c = configured(caller, product, root, option, federator::parts);
        Described described = describe(c.fed(), c.model());
        return new Json.PartsResponse(described.parts(), new Json.Provenance(c.fed().result().calls()), c.fed().result().sparql(),
                new Json.Timings(msSince(start), c.fed().result().ms(), described.validationMs()), c.fed().policy(c.model()));
    }

    /** The world placements of the visible geometric parts of the option's configuration, composed down its own trees. */
    public Json.Placements placements(Caller caller, String product, String root, String option) {
        if (option == null) return placements(caller, product, root);
        long start = System.nanoTime();
        Configured c = configured(caller, product, root, option, federator::placements);
        Model model = c.model();
        return new PlacementsMapper(model, units).placements(product, siteKits(model, product), new Json.Provenance(c.fed().result().calls()),
                c.fed().result().sparql(), new Json.Timings(msSince(start), c.fed().result().ms(), 0), c.fed().policy(model), null);
    }

    /** A federation run of one product with a variant group's options: the product, the group's IRI. */
    private interface VariantRun {
        Federator.Result run(Policy.Profile profile, String product, String variantIri);
    }

    /** A redacted variant run and the graph of one configuration split from it. */
    private record Configured(Federated fed, Model model) {}

    /** The run of the group the option code belongs to, redacted and split into that option's configuration. */
    private Configured configured(Caller caller, String product, String root, String code, VariantRun run) {
        if (product == null) throw new IllegalArgumentException("an option is of one product: name the product");
        if (isSubtree(product, root)) throw new IllegalArgumentException("an option and a subtree root are not answered together");
        if (!Atelier.isKey(code)) throw new IllegalArgumentException("invalid option code");
        VariantJson.Group group = variants(product).groups().stream().filter(g -> g.options().contains(code)).findFirst()
                .orElseThrow(() -> new UnknownVariant(product, code, variantGroups(product)));
        String variantIri = Atelier.variantIri(product, group.key());
        Federator.Result raw = run.run(caller.profile(), product, variantIri);
        VariantFacts facts = VariantFacts.read(raw.model(), variantIri);
        Federated fed = redacted(raw, caller);
        return new Configured(fed, Configurations.of(fed.result().model(), facts, facts.option(code)));
    }

    /** Each variant group of the product with its option codes, the default first: {@code group (default, other, ...)}. */
    private List<String> variantGroups(String product) {
        return variants(product).groups().stream()
                .map(g -> g.key() + " (" + String.join(", ", g.options()) + ")").toList();
    }

    /** The variant groups of one product, each with its name, what it selects and its options, the default first. */
    public VariantJson.Groups variants(String product) {
        Model groups = federator.groups(product);
        Property optionOf = groups.createProperty(Atelier.ONT + "optionOf");
        Property fallback = groups.createProperty(Atelier.ONT + "defaultOption");
        Property label = groups.createProperty("http://www.w3.org/2000/01/rdf-schema#label");
        Property comment = groups.createProperty("http://www.w3.org/2000/01/rdf-schema#comment");
        List<VariantJson.Group> out = groups.listSubjectsWithProperty(fallback).toList().stream().map(variant -> {
            String first = Atelier.nativeId(variant.getPropertyResourceValue(fallback).getURI());
            List<String> codes = new ArrayList<>(List.of(first));
            groups.listSubjectsWithProperty(optionOf, variant).toList().stream().map(o -> Atelier.nativeId(o.getURI()))
                    .filter(c -> !c.equals(first)).sorted().forEach(codes::add);
            return new VariantJson.Group(Atelier.nativeId(variant.getURI()), literal(variant, label), literal(variant, comment), first, codes);
        }).sorted(Comparator.comparing(VariantJson.Group::key)).toList();
        return new VariantJson.Groups(product, out);
    }

    private static String literal(Resource subject, Property property) {
        Statement s = subject.getProperty(property);
        return s == null || !s.getObject().isLiteral() ? null : s.getString();
    }

    private static VariantJson.Option json(VariantFacts.Option option) {
        return new VariantJson.Option(option.key(), option.isDefault(), option.source(), option.applicability(), option.notModelled());
    }

    private Rules rules(Federated fed, Model model, Subtree subtree) {
        BomMapper.deriveOccurrences(model);
        List<RuleViolation> results = fed.evaluable(validator.validate(withMemberships(fed, model)));
        if (subtree != null) results = scoped(results, subtree);
        InterfaceMapper mapper = new InterfaceMapper(model, units, lifecycles, results, cadFile -> null);
        if (subtree != null) mapper.withContext(subtree.context());
        Map<String, List<Json.Finding>> products = results.stream()
                .filter(r -> r.focusNode() != null && r.focusNode().startsWith(Atelier.PRODUCT))
                .collect(Collectors.groupingBy(r -> Atelier.nativeId(r.focusNode()), TreeMap::new,
                        Collectors.mapping(Json.Finding::of, Collectors.toList())));
        return new Rules(mapper.interfaces(), mapper.parts().stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast).toList(),
                products);
    }

    /**
     * Notes which named parts carry no tag and the product of every item (the core membership names hidden items too),
     * then redacts the merged graph for the caller's profile and counts per product the items left undescribed.
     */
    private static Federated redacted(Federator.Result raw, Caller caller) {
        List<String> untagged = Redaction.untagged(raw.model());
        Property partOf = raw.model().createProperty(Atelier.PART_OF);
        Map<Resource, Resource> productOf = new HashMap<>();
        raw.model().listStatements(null, partOf, (RDFNode) null)
                .forEachRemaining(s -> { if (s.getObject().isURIResource()) productOf.put(s.getSubject(), s.getResource()); });
        Policy.Profile profile = caller.profile();
        Federator.Result redacted = raw.edited(m -> Redaction.apply(m, profile.releasable(), profile.seesUntagged()));
        Map<String, Integer> hidden = new HashMap<>();
        productOf.forEach((item, product) -> {
            if (!redacted.model().contains(item, RDF.type)) hidden.merge(product.getURI(), 1, Integer::sum);
        });
        return new Federated(redacted, caller, untagged, hidden);
    }

    private static long msSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
