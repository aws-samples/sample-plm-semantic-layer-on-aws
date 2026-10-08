// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mapping;

import atelier.query.Atelier;
import atelier.query.api.Json;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

/**
 * The suppliers of a product's visible parts, from the supplier offers each PLM holds for its own parts. Suppliers are
 * grouped by name: each site keeps its own supplier list, and a supplier two sites buy from is listed by both. A part
 * with exactly one offer in its site is single-source; the lead-time conflicts are the parts' {@code conflictingLeadTime}
 * findings, each distinct one once.
 */
public final class SupplierMapper {
    static final String CONFLICTING_LEAD_TIME = "conflictingLeadTime";

    private final Model model;

    public SupplierMapper(Model model) {
        this.model = model;
    }

    private record Row(Resource part, Resource supplier, Json.Offer offer) {}

    /** The suppliers by name, the single-source parts and the lead-time conflicts among {@code parts} (the visible parts with their findings). */
    public Json.Suppliers suppliers(String product, List<Json.Part> parts, Json.Provenance provenance, String sparql,
                                    Json.Timings timings, Json.Policy policy) {
        List<Row> rows = offers();
        Map<String, Map<String, List<Row>>> bySupplier = new TreeMap<>();
        for (Row row : rows) {
            bySupplier.computeIfAbsent(name(row.supplier()), n -> new LinkedHashMap<>())
                    .computeIfAbsent(row.supplier().getURI(), s -> new ArrayList<>()).add(row);
        }
        List<Json.Supplier> suppliers = bySupplier.entrySet().stream()
                .map(e -> new Json.Supplier(e.getKey(), e.getValue().values().stream().map(this::site)
                        .sorted(Comparator.comparing((Json.SupplierSite s) -> Atelier.PLMS.indexOf(s.plm())).thenComparing(Json.SupplierSite::id))
                        .toList()))
                .toList();

        Map<Resource, List<Row>> byPart = new LinkedHashMap<>();
        rows.forEach(row -> byPart.computeIfAbsent(row.part(), p -> new ArrayList<>()).add(row));
        List<Json.SingleSource> single = byPart.entrySet().stream().filter(e -> e.getValue().size() == 1)
                .map(e -> {
                    Row only = e.getValue().get(0);
                    return new Json.SingleSource(Atelier.plmOf(e.getKey().getURI()), Atelier.nativeId(e.getKey().getURI()),
                            label(e.getKey()), name(only.supplier()), only.offer().leadTimeDays());
                })
                .sorted(Comparator.comparing((Json.SingleSource s) -> Atelier.PLMS.indexOf(s.plm())).thenComparing(Json.SingleSource::id))
                .toList();

        List<Json.LeadTimeConflict> conflicts = parts.stream()
                .flatMap(p -> p.findings().stream().filter(f -> CONFLICTING_LEAD_TIME.equals(f.rule()))
                        .map(f -> new Json.LeadTimeConflict(p.plm(), p.id(), p.name(), f.message())))
                .distinct()
                .toList();
        return new Json.Suppliers(product, suppliers, single, conflicts, provenance, sparql, timings, policy);
    }

    /** Every offer whose part a PLM described (a visible part) and whose supplier it names. */
    private List<Row> offers() {
        List<Row> rows = new ArrayList<>();
        for (Resource offer : model.listSubjectsWithProperty(RDF.type, model.createResource(Atelier.SUPPLIER_OFFER)).toList()) {
            RDFNode part = Rdf.object(offer, Rdf.atelier(model, "offersPart"));
            RDFNode supplier = Rdf.object(offer, Rdf.atelier(model, "fromSupplier"));
            if (part == null || supplier == null || !part.asResource().hasProperty(RDF.type)) continue;
            Resource p = part.asResource();
            rows.add(new Row(p, supplier.asResource(), new Json.Offer(Atelier.nativeId(offer.getURI()), Atelier.nativeId(p.getURI()), label(p),
                    Rdf.string(offer, Rdf.atelier(model, "supplierPartNumber")), Rdf.integer(offer, Rdf.atelier(model, "leadTimeDays")),
                    Boolean.parseBoolean(Rdf.string(offer, Rdf.atelier(model, "preferred"))))));
        }
        return rows;
    }

    private Json.SupplierSite site(List<Row> rows) {
        Resource supplier = rows.get(0).supplier();
        return new Json.SupplierSite(Atelier.plmOf(supplier.getURI()), Atelier.nativeId(supplier.getURI()),
                Rdf.string(supplier, Rdf.atelier(model, "location")),
                rows.stream().map(Row::offer).sorted(Comparator.comparing(Json.Offer::partId).thenComparing(o -> String.valueOf(o.supplierPartNumber())))
                        .toList());
    }

    private String name(Resource supplier) {
        String name = label(supplier);
        return name == null ? Atelier.nativeId(supplier.getURI()) : name;
    }

    private String label(Resource subject) {
        return Rdf.string(subject, Rdf.atelier(model, "label"));
    }
}
