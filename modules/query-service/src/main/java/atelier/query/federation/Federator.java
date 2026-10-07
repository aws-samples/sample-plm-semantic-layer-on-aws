// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.federation;

import atelier.query.Atelier;
import atelier.query.mapping.ExternalReferences;
import atelier.query.policy.Policy;
import atelier.query.policy.Redaction;
import atelier.query.variants.OptionRows;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs a profile's federation and measures what each endpoint contributed. A run reads the link
 * store once, then the core graph: every membership, or the memberships of the product a run is scoped to; then the tags of
 * every part, keeping those of the parts the interfaces and memberships name, or of the product's parts, in two requests
 * (who tagged each part, then the tags the profile FILTER releases); then the products they belong to, a run of every
 * product reading each product's facts once. After the PLMs, a scoped run asks for the memberships of the parts its
 * external references name outside its product. A run scoped to a product keeps only the
 * interfaces whose parts all belong to it ({@link ProductScope}). It then decides in Java which of
 * the remaining parts the viewer may see (a tag came back with its releasability, or the part
 * carries no tag and the profile sees untagged parts), and sends each PLM with a visible part one
 * request per concern about exactly those parts: its parts and, for the interfaces answer and the preview, its
 * plugs, fasteners and couplings, for the interfaces and parts answers the external references those parts make,
 * for the parts answer and the preview the supplier offers for those parts, for the bill of materials the lines from and to those
 * parts; the parts and bill-of-materials answers ask the link store, alongside, for the names and confirmed
 * equivalences of the visible parts ({@link FederatedQueries#layer}), the parts answer also for the product's functional
 * edges (the meshModule rule reads them with the gears' modules), and a flow run asks it for the functional edges before
 * the PLMs. The PLMs are asked concurrently,
 * each one's requests in order; a PLM with no visible part receives nothing. Every request is a
 * CONSTRUCT from {@link FederatedQueries}, sent verbatim and exactly once, so what an endpoint
 * receives is the text reported for it. An endpoint that fails is reported by its provenance name
 * ({@link EndpointFailure}); the failure detail is logged here. A run scoped to one item's subtree names roots instead
 * of parts and is resolved across the sites in rounds by {@link Subtrees}.
 */
@Component
public class Federator {
    /** One endpoint's share of a run: HTTP requests, triples in the merged model, time, and the bytes of the request texts. */
    public record Call(String endpoint, String kind, int requests, long triples, long ms, long requestBytes, long largestRequestBytes) {}

    /**
     * The merged model, each endpoint's share, the request texts sent to each endpoint, every
     * request of the run in sending order as one text ({@code sparql}), and the wall time.
     */
    /**
     * {@code memberships} is the core graph's product memberships as read, before the run was scoped to a product
     * or redacted: whether a part exists, whatever the viewer may see, which the reference shapes read. A run of every
     * product reads every membership; a scoped run its product's and those of the parts its references name.
     */
    public record Result(Model model, Model memberships, List<Call> calls, Map<String, List<String>> queries, String sparql, long ms) {
        /** This result after {@code edit} has changed the model in place, with each endpoint's triple count recomputed. */
        public Result edited(Consumer<Model> edit) {
            edit.accept(model);
            Map<String, Long> triples = triplesByEndpoint(model);
            return new Result(model, memberships, calls.stream()
                    .map(c -> new Call(c.endpoint(), c.kind(), c.requests(), triples.getOrDefault(c.endpoint(), 0L), c.ms(),
                            c.requestBytes(), c.largestRequestBytes()))
                    .toList(), queries, sparql, ms);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(Federator.class);

    private final Endpoints endpoints;

    public Federator(Endpoints endpoints) {
        this.endpoints = endpoints;
    }

    /** Every interface, with the parts the viewer may see, their features and their tags. */
    public Result interfaces(Policy.Profile profile) {
        return interfaces(profile, null);
    }

    /** The interfaces of one product (every product when {@code product} is null), as {@link #interfaces(Policy.Profile)}. */
    public Result interfaces(Policy.Profile profile, String product) {
        return run(profile, Concern.INTERFACES, product);
    }

    /** The parts the interfaces name that the viewer may see, with their tags, products, CAD files and PLM descriptions. */
    public Result parts(Policy.Profile profile) {
        return parts(profile, null);
    }

    /** The parts of one product (every product when {@code product} is null), as {@link #parts(Policy.Profile)}. */
    public Result parts(Policy.Profile profile, String product) {
        return run(profile, Concern.PARTS, product);
    }

    /** The parts of one product as {@link #parts(Policy.Profile, String)}, with the attributes of each purchased item's class. */
    public Result purchased(Policy.Profile profile, String product) {
        return run(profile, Concern.PURCHASED, product);
    }

    /** Every product's parts the viewer may see, with each PLM's description of them and nothing else: what the product list counts. */
    public Result listing(Policy.Profile profile) {
        return run(profile, Concern.LISTING, null);
    }

    /** The parts of one product as {@link #parts(Policy.Profile, String)}, with the bill-of-materials lines from and to them. */
    public Result bom(Policy.Profile profile, String product) {
        return run(profile, Concern.BOM, product);
    }

    /**
     * The bill of materials of one product as {@link #bom(Policy.Profile, String)} reads it, then per site one request for the
     * placements of the lines under its site kit ({@link FederatedQueries#placements}): the kit is the site's visible item of
     * the product that no line reaches, and the product's items the viewer may not see are the only IRIs the request lists.
     */
    public Result placements(Policy.Profile profile, String product) {
        if (product == null) throw new IllegalArgumentException("placements are per product: name the product");
        return run(profile, Concern.PLACEMENTS, product);
    }

    /**
     * The placements run of one product ({@link #placements}) with the stored spans of the visible parts and of their
     * occurrences along the product's station axis, and the product's stations and sections from the link store: what
     * the stations and sections answers read.
     */
    public Result stations(Policy.Profile profile, String product) {
        if (product == null) throw new IllegalArgumentException("stations are per product: name the product");
        return run(profile, Concern.STATIONS, product);
    }

    /**
     * The parts of one product as {@link #interfaces(Policy.Profile, String)} reads them, with the supplier offers for them: what
     * every rule reads, so a preview validates the interface, part and product rules on one merged graph.
     */
    public Result preview(Policy.Profile profile, String product) {
        return run(profile, Concern.PREVIEW, product);
    }

    /**
     * The interfaces of one product as {@link #interfaces(Policy.Profile, String)}, with the product's functional edges and
     * rated speeds from the link store: what the path and flow answers walk.
     */
    public Result flow(Policy.Profile profile, String product) {
        if (product == null) throw new IllegalArgumentException("paths and flows are per product: name the product");
        return run(profile, Concern.FLOW, product);
    }

    /**
     * One variant group of a product as {@link #preview(Policy.Profile, String)} reads the product, with the group and what
     * applies under its options from the options graph: the option interfaces join the base ones, and the items only an
     * option holds are named as the product's parts, so the core graph tags them and their PLMs describe them. The
     * federated graph holds both configurations; {@link atelier.query.variants.Configurations} splits it.
     */
    public Result variant(Policy.Profile profile, String product, String variantIri) {
        return run(profile, Concern.VARIANT, product, variantIri);
    }

    /**
     * The parts run ({@link #parts(Policy.Profile, String)}) or the placements run ({@link #placements}) of one product with one
     * variant group's options from the options graph, as {@link #variant} reads them: the items only an option holds are
     * named as the product's parts and their lines join the trees, so the graph holds every configuration of the group.
     */
    public Result parts(Policy.Profile profile, String product, String variantIri) {
        return run(profile, Concern.PARTS, product, variantIri);
    }

    /** As {@link #parts(Policy.Profile, String, String)}, the placements run. */
    public Result placements(Policy.Profile profile, String product, String variantIri) {
        if (product == null) throw new IllegalArgumentException("placements are per product: name the product");
        return run(profile, Concern.PLACEMENTS, product, variantIri);
    }

    /** The variant groups of one product and their options, from the options graph: what a variant diff can be asked about. */
    public Model groups(String product) {
        CallRecorder recorder = new CallRecorder(endpoints.namesByUrl());
        return send(recorder, endpoints.linkStore(), FederatedQueries.groups(Atelier.productIri(product)));
    }

    /**
     * The glossary and every item's names from the link store's labels graph, then the core graph's product memberships
     * and the tags of the items, with the profile FILTER, so the redaction drops the names of the items the viewer may
     * not see. No PLM is asked: the labels graph holds the names.
     */
    public Result terms(Policy.Profile profile) {
        long start = System.nanoTime();
        CallRecorder recorder = new CallRecorder(endpoints.namesByUrl());
        Model model = send(recorder, endpoints.linkStore(), FederatedQueries.labels());
        Model memberships = send(recorder, endpoints.ontop(Endpoints.CORE), FederatedQueries.members());
        model.add(memberships);
        List<String> tags = FederatedQueries.tags(profile, FederatedQueries.Selection.everyPart());
        model.add(namedOnly(sendAll(recorder, endpoints.ontop(Endpoints.CORE), tags), Redaction.named(model)));
        String sparql = String.join("\n\n", Stream.concat(Stream.of(FederatedQueries.labels(), FederatedQueries.members()), tags.stream()).toList());
        return result(recorder, model, memberships, sparql, (System.nanoTime() - start) / 1_000_000);
    }

    /** What a run asks each PLM about its visible parts, after their description. */
    enum Concern {
        INTERFACES, PARTS, PURCHASED, BOM, PREVIEW, FLOW, PLACEMENTS, STATIONS, VARIANT, LISTING;

        /** Whether the run reads the interfaces with their features; a flow run is an interfaces run with the drives. */
        boolean interfaces() {
            return this == INTERFACES || this == PREVIEW || this == FLOW || this == VARIANT;
        }

        /** Whether the run reads the supplier offers for the visible parts. */
        boolean offers() {
            return this == PARTS || this == PURCHASED || this == PREVIEW || this == VARIANT;
        }

        /** Whether the run reads the lines to the visible items too, which tell a site kit from an item under a hidden parent. */
        boolean linesTo() {
            return this == BOM || this == PLACEMENTS || this == STATIONS;
        }

        /** Whether the run reads the placements under the site kits. */
        boolean placements() {
            return this == PLACEMENTS || this == STATIONS;
        }
    }

    /** A subtree run: the federation result and how the subtree was resolved. */
    public record SubtreeRun(Result result, Subtree subtree) {}

    private Result run(Policy.Profile profile, Concern concern, String product) {
        return run(profile, concern, product, null);
    }

    private Result run(Policy.Profile profile, Concern concern, String product, String variantIri) {
        boolean features = concern.interfaces();
        long start = System.nanoTime();
        CallRecorder recorder = new CallRecorder(endpoints.namesByUrl());
        String facts = features ? FederatedQueries.interfaceFacts() : FederatedQueries.partFacts();
        Model model = send(recorder, endpoints.linkStore(), facts);
        model.add(send(recorder, endpoints.linkStore(), FederatedQueries.fileIndex()));
        List<String> options = variantIri == null ? List.of()
                : List.of(FederatedQueries.variant(variantIri), FederatedQueries.underOptions(variantIri));
        options.forEach(q -> model.add(send(recorder, endpoints.linkStore(), q)));
        // A run scoped to a product keeps the product's parts only, so the core is asked about them by the product key.
        String members = product == null ? FederatedQueries.members() : FederatedQueries.members(Atelier.productIri(product));
        Model memberships = send(recorder, endpoints.ontop(Endpoints.CORE), members);
        model.add(memberships);
        if (variantIri != null) OptionRows.name(model, variantIri);
        // A run of every product asks for every tag, a text that never changes, and keeps the named parts' tags; a variant run
        // also names the option's own rows, which no product membership holds, so it asks by the parts.
        FederatedQueries.Selection asked = product == null ? FederatedQueries.Selection.everyPart()
                : variantIri != null ? FederatedQueries.Selection.parts(Redaction.named(model))
                : FederatedQueries.Selection.ofProduct(Atelier.productIri(product));
        List<String> tags = FederatedQueries.tags(profile, asked);
        // The memberships name every member of the run, so the products' facts are read once per product: every product's,
        // or the scoped product's alone; a variant run's option rows belong to no product, so it asks by the parts.
        String products = product == null ? FederatedQueries.productFacts()
                : variantIri == null ? FederatedQueries.productFacts(Atelier.productIri(product)) : FederatedQueries.products(asked);
        Model tagModel = sendAll(recorder, endpoints.ontop(Endpoints.CORE), tags);
        model.add(product == null ? namedOnly(tagModel, Redaction.named(model)) : tagModel);
        Model productModel = send(recorder, endpoints.ontop(Endpoints.CORE), products);
        if (product == null) dropProductsWithoutParts(productModel, memberships);
        model.add(productModel);
        if (product != null && !ProductScope.restrict(model, Atelier.productIri(product))) {
            throw new UnknownProduct(product);
        }
        String drives = concern == Concern.FLOW ? FederatedQueries.drives(Atelier.productIri(product)) : null;
        if (drives != null) model.add(send(recorder, endpoints.linkStore(), drives));

        List<String> visible = Redaction.visible(model, profile.releasable(), profile.seesUntagged());
        Map<String, List<String>> plmRequests = new LinkedHashMap<>();
        for (String plm : Atelier.PLMS) {
            List<String> parts = visible.stream().filter(iri -> iri.startsWith(Atelier.DATA + plm + "/")).toList();
            if (parts.isEmpty()) continue;
            List<String> requests = new ArrayList<>(List.of(concern == Concern.PURCHASED ? FederatedQueries.purchased(parts)
                    : FederatedQueries.parts(parts)));
            if (concern == Concern.LISTING) {
                plmRequests.put(endpoints.ontop(plm), requests);
                continue;
            }
            if (features) Atelier.KINDS.forEach(kind -> requests.add(FederatedQueries.features(kind, parts)));
            requests.add(FederatedQueries.references(parts));
            if (concern.offers()) requests.add(FederatedQueries.offers(parts));
            // The lines from a visible item and its references are its dependencies, which the lifecycle rule reads
            // in every answer that reports a part's findings.
            requests.add(FederatedQueries.bomLinesFrom(parts));
            if (concern.linesTo()) requests.add(FederatedQueries.bomLinesTo(parts));
            if (concern == Concern.STATIONS) requests.add(FederatedQueries.spans(parts));
            plmRequests.put(endpoints.ontop(plm), requests);
        }
        // The parts and bill-of-materials answers name items; the link store gives their English names and the
        // equivalences users confirmed, asked about the visible items alongside the PLMs.
        if (concern != Concern.INTERFACES && concern != Concern.LISTING && !visible.isEmpty()) {
            plmRequests.put(endpoints.linkStore(), switch (concern) {
                case PARTS -> List.of(FederatedQueries.layer(visible), FederatedQueries.drives(product == null ? null : Atelier.productIri(product)));
                case STATIONS -> List.of(FederatedQueries.layer(visible), FederatedQueries.stations(Atelier.productIri(product)));
                default -> List.of(FederatedQueries.layer(visible));
            });
        }
        sendConcurrently(recorder, plmRequests).forEach(model::add);
        // A scoped run read its product's memberships only; the reference shapes tell a reference to a part of another product
        // from one to no part by the memberships of the parts the references name.
        String targets = product == null ? null : targetMemberships(model, memberships);
        if (targets != null) memberships.add(send(recorder, endpoints.ontop(Endpoints.CORE), targets));
        if (variantIri == null) OptionRows.drop(model);
        Map<String, List<String>> placements = concern.placements() ? placementRequests(model, visible, concern == Concern.STATIONS) : Map.of();
        sendConcurrently(recorder, placements).forEach(model::add);
        long ms = (System.nanoTime() - start) / 1_000_000;

        Stream<String> head = Stream.of(Stream.of(facts, FederatedQueries.fileIndex()), options.stream(), Stream.of(members), tags.stream(),
                Stream.of(products, drives)).flatMap(q -> q).filter(q -> q != null);
        Stream<String> after = Stream.concat(Stream.concat(plmRequests.values().stream().flatMap(List::stream), Stream.ofNullable(targets)),
                placements.values().stream().flatMap(List::stream));
        String sparql = Stream.concat(head, after).collect(Collectors.joining("\n\n"));
        return result(recorder, model, memberships, sparql, ms);
    }

    /**
     * Per site, the placements request under its site kit: the site's visible item of the product no line reaches (the
     * lines to every visible item have been read), with the site's items the viewer may not see as the hidden children;
     * with {@code spans}, the stored spans of those occurrences too.
     */
    private Map<String, List<String>> placementRequests(Model model, List<String> visible, boolean spans) {
        Property child = model.createProperty(Atelier.ONT + "child");
        Set<String> reached = new HashSet<>();
        model.listObjectsOfProperty(child).forEachRemaining(c -> {
            if (c.isURIResource()) reached.add(c.asResource().getURI());
        });
        List<String> hidden = Redaction.named(model).stream().filter(iri -> !visible.contains(iri)).toList();
        Map<String, List<String>> requests = new LinkedHashMap<>();
        for (String plm : Atelier.PLMS) {
            List<String> kits = visible.stream().filter(iri -> plm.equals(Atelier.plmOf(iri)) && !reached.contains(iri)).toList();
            if (kits.isEmpty()) continue;
            List<String> sited = hidden.stream().filter(iri -> plm.equals(Atelier.plmOf(iri))).toList();
            requests.put(endpoints.ontop(plm), spans
                    ? List.of(FederatedQueries.placements(kits, sited), FederatedQueries.occurrenceSpans(kits, sited))
                    : List.of(FederatedQueries.placements(kits, sited)));
        }
        return requests;
    }

    /** Each request's answer, sent in order to one endpoint, in one model. */
    private Model sendAll(CallRecorder recorder, String url, List<String> queries) {
        Model model = ModelFactory.createDefaultModel();
        for (String query : queries) model.add(send(recorder, url, query));
        return model;
    }

    /** The statements of {@code model} about the parts of {@code named}: what a request per named part would have read. */
    private static Model namedOnly(Model model, List<String> named) {
        Set<String> keep = new HashSet<>(named);
        Model out = ModelFactory.createDefaultModel();
        model.listStatements().filterKeep(st -> st.getSubject().isURIResource() && keep.contains(st.getSubject().getURI()))
                .forEachRemaining(out::add);
        return out;
    }

    /** The request for the memberships of the parts the references of {@code model} name that {@code memberships} lacks; null for none. */
    private static String targetMemberships(Model model, Model memberships) {
        Property partOf = memberships.createProperty(Atelier.PART_OF);
        List<String> unknown = ExternalReferences.targets(model).values().stream().distinct()
                .filter(iri -> !memberships.contains(memberships.createResource(iri), partOf, (RDFNode) null)).sorted().toList();
        return unknown.isEmpty() ? null : FederatedQueries.membersOf(unknown);
    }

    /** Removes the products no membership names, with their mass-limit values, as a request per member never reads them. */
    private static void dropProductsWithoutParts(Model products, Model memberships) {
        Property partOf = memberships.createProperty(Atelier.PART_OF);
        Property massLimit = products.createProperty(Atelier.ONT + "massLimit");
        for (Resource p : products.listSubjectsWithProperty(RDF.type, products.createResource(Atelier.ONT + "Product")).toList()) {
            if (memberships.contains(null, partOf, p)) continue;
            products.listObjectsOfProperty(p, massLimit).toList().forEach(q -> products.removeAll(q.asResource(), null, null));
            products.removeAll(p, null, null);
        }
    }

    /** A run's result: each endpoint's requests, triples of the merged model, time and request bytes. */
    static Result result(CallRecorder recorder, Model model, Model memberships, String sparql, long ms) {
        Map<String, Long> triples = triplesByEndpoint(model);
        Map<String, List<String>> queries = recorder.queries();
        List<Call> calls = recorder.counts().entrySet().stream()
                .map(e -> {
                    List<Long> bytes = queries.getOrDefault(e.getKey(), List.of()).stream()
                            .map(q -> (long) q.getBytes(StandardCharsets.UTF_8).length).toList();
                    return new Call(e.getKey(), kindOf(e.getKey()), e.getValue().requests(), triples.getOrDefault(e.getKey(), 0L),
                            e.getValue().nanos() / 1_000_000, bytes.stream().mapToLong(Long::longValue).sum(),
                            bytes.stream().mapToLong(Long::longValue).max().orElse(0));
                })
                .toList();
        return new Result(model, memberships, calls, queries, sparql, ms);
    }

    /**
     * The interfaces with at least one side in the subtree of {@code root} (a native id of an item of {@code product}),
     * resolved across the sites ({@link Subtrees}), with the far side of each as context.
     */
    public SubtreeRun interfacesUnder(Policy.Profile profile, String product, String root) {
        return new Subtrees(this, profile, Concern.INTERFACES, product).run(root);
    }

    /** The parts of the subtree of {@code root}, with their tags, CAD files, PLM descriptions, references and offers. */
    public SubtreeRun partsUnder(Policy.Profile profile, String product, String root) {
        return new Subtrees(this, profile, Concern.PARTS, product).run(root);
    }

    /** The subtree of {@code root} as {@link #interfacesUnder} resolves it, with the supplier offers for its visible parts. */
    public SubtreeRun previewUnder(Policy.Profile profile, String product, String root) {
        return new Subtrees(this, profile, Concern.PREVIEW, product).run(root);
    }

    /** The subtree of {@code root} as {@link #bomUnder} resolves it, with the placements of the lines under its roots. */
    public SubtreeRun placementsUnder(Policy.Profile profile, String product, String root) {
        return new Subtrees(this, profile, Concern.PLACEMENTS, product).run(root);
    }

    /** The parts of the subtree of {@code root} with the lines between them and the references between the sites. */
    public SubtreeRun bomUnder(Policy.Profile profile, String product, String root) {
        return new Subtrees(this, profile, Concern.BOM, product).run(root);
    }

    Endpoints endpoints() {
        return endpoints;
    }

    /** Sends each endpoint's requests in order, the endpoints concurrently; one model per endpoint, in map order. */
    List<Model> sendConcurrently(CallRecorder recorder, Map<String, List<String>> requestsByUrl) {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Model>> futures = requestsByUrl.entrySet().stream()
                    .map(e -> pool.submit(() -> {
                        Model model = ModelFactory.createDefaultModel();
                        for (String query : e.getValue()) model.add(send(recorder, e.getKey(), query));
                        return model;
                    }))
                    .toList();
            List<Model> models = new ArrayList<>();
            for (Future<Model> future : futures) models.add(future.get());
            return models;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException failure) throw failure;
            throw new IllegalStateException(e.getCause());
        }
    }

    Model send(CallRecorder recorder, String url, String query) {
        recorder.recordQuery(url, query);
        long start = System.nanoTime();
        try {
            Model model = answer(url, query);
            recorder.record(url, System.nanoTime() - start);
            return model;
        } catch (RuntimeException e) {
            String endpoint = endpoints.namesByUrl().get(url);
            log.warn("Endpoint {} failed: {}", endpoint, e.toString());
            throw new EndpointFailure(endpoint, e);
        }
    }

    /** The graph a SPARQL endpoint answers to a CONSTRUCT. */
    protected Model answer(String url, String query) {
        try (QueryExecution exec = endpoints.execution(url, query)) {
            return exec.execConstruct();
        }
    }

    public static String kindOf(String endpoint) {
        return Endpoints.LINK_STORE.equals(endpoint) ? "materialized" : "virtual";
    }

    /** Number of triples of the merged model each endpoint supplied, by {@link #sourceOf}. */
    public static Map<String, Long> triplesByEndpoint(Model model) {
        Map<String, Long> counts = new LinkedHashMap<>();
        model.getGraph().find().forEachRemaining(t -> counts.merge(sourceOf(t), 1L, Long::sum));
        return counts;
    }

    /**
     * Name of the endpoint that supplied a triple of the merged model: link-store predicates,
     * interface subjects, the functional edges and rated speeds ({@link Atelier#FUNCTION_PREDICATES}, drive subjects), the
     * stations and sections ({@link Atelier#STATION_PREDICATES}, station and section subjects), the
     * file-index predicates (atelier:cadFile, atelier:builtBy) and the layer's (labels, owl:sameAs) come from the link store, a part's
     * tag and product predicates and the product subjects from the core graph; a part's PLM predicates
     * ({@link Atelier#PART_PREDICATES}: its description and attributes), the features and the quantity
     * values minted under a part or feature IRI (positions, diameter, grip, rating, mass) come from the
     * Ontop endpoint of the PLM named in the subject IRI.
     */
    public static String sourceOf(Triple t) {
        String predicate = t.getPredicate().getURI();
        String subject = t.getSubject().isURI() ? t.getSubject().getURI() : "";
        if (Atelier.LINK_PREDICATES.contains(predicate) || Atelier.FILE_INDEX_PREDICATES.contains(predicate)
                || Atelier.OPTION_PREDICATES.contains(predicate) || subject.startsWith(Atelier.OPTION) || subject.startsWith(Atelier.VARIANT)
                || Atelier.LAYER_PREDICATES.contains(predicate) || subject.startsWith(Atelier.INTERFACE)
                || Atelier.FUNCTION_PREDICATES.contains(predicate) || subject.startsWith(Atelier.DRIVE)
                || Atelier.STATION_PREDICATES.contains(predicate) || subject.startsWith(Atelier.STATION)
                || subject.startsWith(Atelier.SECTION)) {
            return Endpoints.LINK_STORE;
        }
        if (Atelier.CORE_PREDICATES.contains(predicate) || subject.startsWith(Atelier.PRODUCT)) {
            return Endpoints.ontopName(Endpoints.CORE);
        }
        String plm = Atelier.plmOf(subject);
        if (plm != null && Atelier.PART_PREDICATES.contains(predicate)) {
            return Endpoints.ontopName(plm);
        }
        String rest = subject.startsWith(Atelier.DATA) ? subject.substring(Atelier.DATA.length()) : "";
        int slash = rest.indexOf('/');
        return slash > 0 ? Endpoints.ontopName(rest.substring(0, slash)) : "unknown";
    }
}
