// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mapping;

import atelier.query.Atelier;
import atelier.query.api.Json;
import atelier.query.validation.RuleViolation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

/**
 * Maps the federated model and its SHACL violations to the contract's Interface JSON. A part named
 * by an interface but absent from the model was withheld by export control: it, the declared
 * features of its PLM that no PLM described, and the mates of visible features on it are reported
 * as redacted, and the interface is {@code not-evaluable} with no violations. Of the SHACL results,
 * the sh:Violation ones on the interface's declared features decide its status; the sh:Warning
 * ones are data-quality findings, reported on the part they concern and counted per rule, and
 * never enter a status. An {@code orphan} result carries the candidate mates of the orphan: the
 * unmated features of its kind on the facing part within the interface tolerance of its position,
 * read from {@code candidates} (the whole redacted graph, since a feature no interface declares is
 * outside a single interface's neighbourhood).
 */
public final class InterfaceMapper {
    private final Model model;
    private final Model candidates;
    private final Units units;
    private final Lifecycles lifecycles;
    private final List<RuleViolation> violations;
    private final Map<String, List<Json.Finding>> findings;
    private final Function<String, String> cadUrl;
    private Set<String> context = Set.of();

    /** Over one model that is also where candidate mates are read. */
    public InterfaceMapper(Model model, Units units, Lifecycles lifecycles, List<RuleViolation> results, Function<String, String> cadUrl) {
        this(model, model, units, lifecycles, results, cadUrl);
    }

    /**
     * @param candidates the graph an orphan's candidate mates are read from: the whole redacted graph
     * @param lifecycles the canonical lifecycle state of each PLM's lifecycle word
     * @param results every SHACL result of the model: the sh:Violation ones decide the interfaces' status, the
     *                sh:Warning ones are findings on the parts they name
     * @param cadUrl presigned CAD URL for a part's {@code atelier:cadFile}, or null when CAD is not served
     */
    public InterfaceMapper(Model model, Model candidates, Units units, Lifecycles lifecycles, List<RuleViolation> results,
                           Function<String, String> cadUrl) {
        this.model = model;
        this.candidates = candidates;
        this.units = units;
        this.lifecycles = lifecycles;
        this.violations = results.stream().filter(RuleViolation::failsInterface).toList();
        this.findings = results.stream().filter(r -> !r.failsInterface()).collect(Collectors.groupingBy(
                RuleViolation::subject, LinkedHashMap::new,
                Collectors.mapping(Json.Finding::of, Collectors.toList())));
        this.cadUrl = cadUrl;
    }

    /** Marks the parts of these IRIs as context: the far side of an interface, outside the subtree an answer is about. */
    public InterfaceMapper withContext(Set<String> context) {
        this.context = Set.copyOf(context);
        return this;
    }

    /** The data-quality findings of the model's parts, counted per rule; empty when there are none. */
    public Map<String, Integer> findings() {
        return findings.values().stream().flatMap(List::stream)
                .collect(Collectors.groupingBy(Json.Finding::rule, TreeMap::new, Collectors.summingInt(f -> 1)));
    }

    public List<Json.Interface> interfaces() {
        return model.listSubjectsWithProperty(RDF.type, model.createResource(Atelier.ONT + "Interface"))
                .mapWith(this::toJson).toList().stream()
                .sorted(Comparator.comparing(Json.Interface::id, Atelier.BY_ID).thenComparing(Json.Interface::product)).toList();
    }

    /** Every visible part, then one redaction marker per hidden part an interface names. */
    public List<Json.PartView> parts() {
        List<Json.PartView> parts = new ArrayList<>(model
                .listSubjectsWithProperty(RDF.type, model.createResource(Atelier.ONT + "Part")).mapWith(this::part).toList()
                .stream().sorted(Comparator.comparing((Json.Part p) -> Atelier.PLMS.indexOf(p.plm())).thenComparing(Json.Part::id))
                .toList());
        model.listObjectsOfProperty(Rdf.atelier(model, "betweenPart")).mapWith(RDFNode::asResource).toList().stream()
                .filter(p -> !p.hasProperty(RDF.type)).map(Resource::getURI).sorted().distinct()
                .forEach(iri -> parts.add(new Json.Redacted(true, Atelier.plmOf(iri))));
        return parts;
    }

    private Json.Interface toJson(Resource iface) {
        List<Resource> partResources = Rdf.resources(iface, Rdf.atelier(model, "betweenPart")).stream()
                .sorted(Comparator.comparing(Resource::getURI)).toList();
        Set<String> hiddenPlms = partResources.stream().filter(p -> !p.hasProperty(RDF.type))
                .map(p -> Atelier.plmOf(p.getURI())).collect(Collectors.toSet());
        List<Json.PartView> parts = partResources.stream()
                .<Json.PartView>map(p -> p.hasProperty(RDF.type) ? part(p) : new Json.Redacted(true, Atelier.plmOf(p.getURI())))
                .toList();

        Map<String, FeatureFacts> features = Rdf.resources(iface, Rdf.atelier(model, "declaresFeature")).stream()
                .map(f -> FeatureFacts.read(f, units)).sorted(FeatureFacts.ORDER)
                .collect(Collectors.toMap(FeatureFacts::iri, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Function<String, Object> mate = iri -> {
            FeatureFacts m = features.get(iri);
            boolean hidden = (m == null || !m.present()) && hiddenPlms.contains(Atelier.plmOf(iri));
            return hidden ? new Json.Redacted(true, Atelier.plmOf(iri)) : Atelier.nativeId(iri);
        };
        List<Json.FeatureView> shown = features.values().stream()
                .<Json.FeatureView>map(f -> !f.present() && hiddenPlms.contains(f.plm())
                        ? new Json.Redacted(true, f.plm()) : f.toJson(mate))
                .toList();

        BigDecimal tolerance = Rdf.decimal(iface, Rdf.atelier(model, "toleranceMm"));
        boolean evaluable = hiddenPlms.isEmpty();
        // Status: the sh:Violation results whose focus node is a feature the interface declares. Findings
        // (sh:Warning, on parts) are not in violations and never count.
        List<Json.Violation> found = !evaluable ? List.of() : violations.stream()
                .filter(v -> features.containsKey(v.focusNode()))
                .filter(v -> !"orphan".equals(v.rule()) || iface.getURI().equals(v.value().getURI()))
                .map(v -> violation(v, features, partResources, tolerance))
                .toList();
        String status = !evaluable ? "not-evaluable" : found.isEmpty() ? "pass" : "fail";
        return new Json.Interface(Atelier.nativeId(iface.getURI()), Atelier.productOf(iface.getURI()),
                Rdf.string(iface, Rdf.atelier(model, "label")), status,
                tolerance == null ? null : tolerance.doubleValue(), parts, shown, found);
    }

    private Json.Part part(Resource part) {
        Json.Part json = described(part);
        return context.contains(part.getURI()) ? json.asContext() : json;
    }

    private Json.Part described(Resource part) {
        String cadFile = Rdf.string(part, Rdf.atelier(model, "cadFile"));
        String lifecycle = Rdf.string(part, Rdf.atelier(model, "lifecycleLabel"));
        String partType = Rdf.string(part, Rdf.atelier(model, "partType"));
        // Only an item with geometry has a CAD file to sign: an assembly, software or a document has none.
        boolean geometric = partType == null || "PART".equals(partType);
        List<String> builders = model.listObjectsOfProperty(part, Rdf.atelier(model, "builtBy")).filterKeep(RDFNode::isLiteral)
                .mapWith(n -> n.asLiteral().getString()).toList().stream().sorted().distinct().toList();
        return new Json.Part(Atelier.nativeId(part.getURI()), Atelier.plmOf(part.getURI()),
                Rdf.string(part, Rdf.atelier(model, "label")), Rdf.englishName(part), cadFile, cadFile == null || !geometric ? null : cadUrl.apply(cadFile),
                Rdf.string(part, Rdf.atelier(model, "sourceFileRef")), Rdf.string(part, Rdf.atelier(model, "jurisdiction")),
                Rdf.string(part, Rdf.atelier(model, "releasableTo")), Rdf.string(part, Rdf.atelier(model, "taggedBy")),
                Rdf.string(part, Rdf.atelier(model, "taggedAt")), builders.isEmpty() ? null : builders.get(0), suppliers(builders, part),
                findings.getOrDefault(part.getURI(), List.of()), Rdf.string(part, Rdf.atelier(model, "revision")),
                lifecycle, lifecycles.state(lifecycle), mass(part), Rdf.string(part, Rdf.atelier(model, "material")),
                partType, Rdf.integer(part, Rdf.atelier(model, "toothCount")), gearModule(part), null);
    }

    /**
     * The part's builders, then the suppliers with an offer for it (read by the parts answers), by name. An offering
     * supplier is named as the file index names a builder, "name, town", so a supplier that both builds and offers a part
     * is one name in both roles.
     */
    private List<Json.PartSupplier> suppliers(List<String> builders, Resource part) {
        List<Json.PartSupplier> out = new ArrayList<>(builders.stream().map(b -> new Json.PartSupplier(b, Json.PartSupplier.BUILT)).toList());
        model.listSubjectsWithProperty(Rdf.atelier(model, "offersPart"), part)
                .mapWith(offer -> Rdf.object(offer, Rdf.atelier(model, "fromSupplier"))).filterKeep(s -> s != null && s.isResource())
                .mapWith(s -> supplierName(s.asResource())).toList().stream().sorted().distinct()
                .forEach(name -> out.add(new Json.PartSupplier(name, Json.PartSupplier.OFFERED)));
        return out;
    }

    private String supplierName(Resource supplier) {
        String name = Rdf.string(supplier, Rdf.atelier(model, "label"));
        String town = Rdf.string(supplier, Rdf.atelier(model, "location"));
        if (name == null) name = Atelier.nativeId(supplier.getURI());
        return town == null ? name : name + ", " + town;
    }

    /** The gear's module as stored, with its unit and in mm; null for a part that is no gear. */
    private Json.Quantity gearModule(Resource part) {
        RDFNode node = Rdf.object(part, Rdf.atelier(model, "gearModule"));
        if (node == null || !node.isResource()) return null;
        BigDecimal value = Rdf.decimal(node.asResource(), Rdf.qudt(model, "numericValue"));
        if (value == null) return null;
        String unit = Rdf.string(node.asResource(), Rdf.qudt(model, "unit"));
        BigDecimal mm = units.toMm(value, unit);
        return new Json.Quantity(value.doubleValue(), unit == null ? null : Atelier.localName(unit), mm == null ? null : mm.doubleValue(),
                null, null);
    }

    /** The part's mass as stored, with its unit and in kg; null when the PLM states no value. */
    private Json.Quantity mass(Resource part) {
        RDFNode node = Rdf.object(part, Rdf.atelier(model, "mass"));
        if (node == null || !node.isResource()) return null;
        BigDecimal value = Rdf.decimal(node.asResource(), Rdf.qudt(model, "numericValue"));
        if (value == null) return null;
        String unit = Rdf.string(node.asResource(), Rdf.qudt(model, "unit"));
        BigDecimal kg = units.toKg(value, unit);
        return new Json.Quantity(value.doubleValue(), unit == null ? null : Atelier.localName(unit), null, null,
                kg == null ? null : kg.doubleValue());
    }

    private Json.Violation violation(RuleViolation v, Map<String, FeatureFacts> features, List<Resource> parts, BigDecimal tolerance) {
        FeatureFacts focus = features.get(v.focusNode());
        String quantity = v.path() == null ? null : Atelier.localName(v.path());
        String axis = quantity != null && quantity.startsWith("position") ? quantity.substring(8).toLowerCase() : null;
        List<String> ids = new ArrayList<>(List.of(focus.id()));
        Map<String, Object> detail = new LinkedHashMap<>();
        switch (v.rule()) {
            case "unit" -> {
                detail.put("quantity", quantity);
                detail.put("axis", axis);
                String unit = Rdf.string(model.createResource(v.value().getURI()), Rdf.qudt(model, "unit"));
                detail.put("unit", unit == null ? null : Atelier.localName(unit));
                BigDecimal stored = Rdf.decimal(model.createResource(v.value().getURI()), Rdf.qudt(model, "numericValue"));
                detail.put("storedValue", stored == null ? null : stored.doubleValue());
            }
            case "position", "connector", "fastener", "hydraulic", "kind" -> {
                FeatureFacts mate = features.getOrDefault(v.value().getURI(),
                        FeatureFacts.read(model.createResource(v.value().getURI()), units));
                ids.add(mate.id());
                switch (v.rule()) {
                    case "position" -> {
                        BigDecimal delta = focus.deltaMm(mate, axis);
                        detail.put("axis", axis);
                        detail.put("deltaMm", delta == null ? null : delta.doubleValue());
                        detail.put("toleranceMm", tolerance == null ? null : tolerance.doubleValue());
                    }
                    case "connector" -> {
                        detail.put("connectorType", pair(focus, mate, "connectorType"));
                        detail.put("pinCount", pair(focus, mate, "pinCount"));
                    }
                    case "fastener" -> {
                        detail.put("fastenerStandard", pair(focus, mate, "fastenerStandard"));
                        detail.put("fastenerCount", pair(focus, mate, "fastenerCount"));
                        detail.put("diameterMm", Arrays.asList(focus.mm("diameter"), mate.mm("diameter")));
                    }
                    case "hydraulic" -> {
                        detail.put("couplingStandard", pair(focus, mate, "couplingStandard"));
                        detail.put("dashSize", pair(focus, mate, "dashSize"));
                        detail.put("fluid", pair(focus, mate, "fluid"));
                        detail.put("ratingBar", Arrays.asList(focus.bar("pressureRating"), mate.bar("pressureRating")));
                    }
                    default -> detail.put("kind", Arrays.asList(focus.kind(), mate.kind()));
                }
            }
            case "orphan" -> {
                detail.put("interface", Atelier.nativeId(v.value().getURI()));
                detail.put("candidateMates", candidateMates(focus, parts, tolerance));
            }
            // Every mate a source described; a mate on a hidden part stays unnamed (the message carries the count).
            case "doubleMate" -> focus.matesWithIris().stream().filter(m -> model.createResource(m).hasProperty(RDF.type))
                    .map(Atelier::nativeId).forEach(ids::add);
            default -> { }
        }
        return new Json.Violation(v.rule(), v.shape(), v.message(), v.focusNode(), ids, detail);
    }

    /**
     * The unmated features of the orphan's kind on the interface's other visible part(s) within the
     * interface tolerance of the orphan's position on every axis (in mm), by IRI; empty when none.
     */
    private List<Json.CandidateMate> candidateMates(FeatureFacts orphan, List<Resource> parts, BigDecimal tolerance) {
        Property onPart = Rdf.atelier(candidates, "onPart");
        Property matesWith = Rdf.atelier(candidates, "matesWith");
        List<Json.CandidateMate> found = new ArrayList<>();
        for (Resource part : parts) {
            if (!part.hasProperty(RDF.type) || part.getURI().equals(orphan.partIri())) continue;
            for (Resource feature : candidates.listSubjectsWithProperty(onPart, part).toList()) {
                if (feature.hasProperty(matesWith) || !feature.hasProperty(RDF.type)) continue;
                FeatureFacts candidate = FeatureFacts.read(feature, units);
                if (!orphan.kind().equals(candidate.kind()) || !within(orphan, candidate, tolerance)) continue;
                found.add(new Json.CandidateMate(candidate.id(), candidate.partId(), candidate.plm(), candidate.iri(),
                        candidate.positionMm(), (String) candidate.property("connectorType"), (Integer) candidate.property("pinCount")));
            }
        }
        return found.stream().sorted(Comparator.comparing(Json.CandidateMate::iri)).toList();
    }

    private static boolean within(FeatureFacts a, FeatureFacts b, BigDecimal tolerance) {
        if (tolerance == null) return false;
        for (String axis : Atelier.AXES) {
            BigDecimal delta = a.deltaMm(b, axis);
            if (delta == null || delta.compareTo(tolerance) > 0) return false;
        }
        return true;
    }

    private static List<Object> pair(FeatureFacts a, FeatureFacts b, String property) {
        return Arrays.asList(a.property(property), b.property(property));
    }
}
