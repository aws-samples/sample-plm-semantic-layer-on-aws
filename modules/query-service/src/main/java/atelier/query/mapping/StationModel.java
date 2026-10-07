// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mapping;

import atelier.query.Atelier;
import atelier.query.api.StationsJson;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

/**
 * A product's stations, sections and the spans of its visible parts, read from a redacted stations federation. A part's
 * span is the one its PLM stores, converted to mm. Its occurrences are those {@link PlacementsMapper} composes; each
 * occurrence's span is the stored span of its line's occurrence (the part's own span for a line without placements)
 * carried by the parent's motion from its first occurrence to this one, the composed transform of its whole path with its
 * rotations, as data/stations.py derives it and checks it against the true envelope of every occurrence. The motion moves
 * the span's ends exactly when it maps the station axis onto itself or its opposite; an occurrence whose parent turns the
 * axis onto another axis has no span (null), which data/generate.py refuses to store.
 */
final class StationModel {
    static final String SKOS_NOTATION = Atelier.SKOS + "notation";
    static final String SOURCE = "http://purl.org/dc/terms/source";
    /** A rotation entry counts as 0 or 1 within this, as data/placements.py SAME. */
    private static final double SAME = 1e-6;
    private static final Map<String, String> BASES = Map.of("PrintedStation", "printed", "MeasuredStation", "measured",
            "JointStation", "joint", "InferredStation", "inferred");

    /** A section as published: id, name, owner, side, stations in order and the part it is (an IRI, or null). */
    record SectionData(String id, String name, String owner, String side, List<String> stations, String part) {}

    /** A part's span as stored ({@code from}, {@code to} in {@code unit}) and in mm, and its occurrence spans in mm. */
    record PartSpan(String iri, String plm, String name, String nameEn, double from, double to, String unit, double fromMm,
                    double toMm, List<double[]> occurrences) {}

    final StationsJson.Axis axis;
    final Map<String, StationsJson.Station> stations = new LinkedHashMap<>();
    final List<SectionData> sections = new ArrayList<>();
    final List<PartSpan> parts = new ArrayList<>();
    private final Model model;
    private final Units units;

    StationModel(Model model, Units units, String product, Collection<String> roots) {
        this.model = model;
        this.units = units;
        Resource p = model.createResource(Atelier.productIri(product));
        String axisName = Rdf.string(p, Rdf.atelier(model, "stationAxis"));
        if (axisName == null) throw new IllegalArgumentException("the product " + product + " has no stations");
        RDFNode symmetric = Rdf.object(p, Rdf.atelier(model, "stationsSymmetric"));
        axis = new StationsJson.Axis(axisName, symmetric != null && symmetric.isLiteral() && symmetric.asLiteral().getBoolean(),
                Rdf.string(p, Rdf.atelier(model, "stationMeasure")));
        readStations(p);
        readSections(p);
        readParts(roots);
    }

    private void readStations(Resource product) {
        Property sectionOf = Rdf.atelier(model, "sectionOf");
        List<StationsJson.Station> found = new ArrayList<>();
        for (Resource s : model.listSubjectsWithProperty(RDF.type, model.createResource(Atelier.ONT + "Station")).toList()) {
            if (!s.hasProperty(sectionOf, product)) continue;
            Double at = mm(Rdf.object(s, Rdf.atelier(model, "stationPosition")));
            String id = Rdf.string(s, model.createProperty(SKOS_NOTATION));
            RDFNode basis = Rdf.object(s, Rdf.atelier(model, "stationBasis"));
            if (at == null || id == null || basis == null || !basis.isURIResource()) continue;
            found.add(new StationsJson.Station(id, at, BASES.getOrDefault(basis.asResource().getLocalName(), "inferred"),
                    Rdf.string(s, model.createProperty(SOURCE)), mm(Rdf.object(s, Rdf.atelier(model, "stationTolerance")))));
        }
        found.stream().sorted(Comparator.comparingDouble(StationsJson.Station::atMm)).forEach(s -> stations.put(s.id(), s));
    }

    private void readSections(Resource product) {
        Property sectionOf = Rdf.atelier(model, "sectionOf");
        for (Resource s : model.listSubjectsWithProperty(RDF.type, model.createResource(Atelier.ONT + "Section")).toList()) {
            if (!s.hasProperty(sectionOf, product)) continue;
            List<String> bounds = Rdf.resources(s, Rdf.atelier(model, "hasStation")).stream()
                    .map(r -> Rdf.string(r, model.createProperty(SKOS_NOTATION))).filter(stations::containsKey)
                    .sorted(Comparator.comparingDouble(id -> stations.get(id).atMm())).toList();
            RDFNode part = Rdf.object(s, Rdf.atelier(model, "sectionPart"));
            sections.add(new SectionData(Rdf.string(s, model.createProperty(SKOS_NOTATION)), Rdf.string(s, RDFS.label),
                    Rdf.string(s, Rdf.atelier(model, "ownedBy")), Rdf.string(s, Rdf.atelier(model, "sectionSide")), bounds,
                    part != null && part.isURIResource() ? part.asResource().getURI() : null));
        }
        sections.sort(Comparator.comparing(SectionData::id));
    }

    private void readParts(Collection<String> roots) {
        PlacementsMapper placements = new PlacementsMapper(model, units);
        PlacementsMapper.Tree tree = placements.tree(roots);
        int a = Atelier.AXES.indexOf(axis.axis());
        for (String iri : tree.parts()) {
            Resource part = model.createResource(iri);
            double[] own = span(part);
            if (own == null) continue;
            List<double[]> occurrences = new ArrayList<>();
            List<String> parents = placements.parents(iri, tree);
            for (String parent : parents) {
                List<double[]> above = placements.world(parent, tree);
                List<String> rows = placements.occurrences(parent, iri);
                double[] back = inverse(above.get(0));
                for (double[] w : above) {
                    double[] motion = multiply(w, back);
                    if (rows.isEmpty()) occurrences.add(carried(motion, own[2], own[3], a));
                    for (String row : rows) {
                        double[] stored = span(model.createResource(row));
                        occurrences.add(stored == null ? null : carried(motion, stored[2], stored[3], a));
                    }
                }
            }
            if (occurrences.isEmpty()) occurrences.add(new double[] {own[2], own[3]});
            Statement unit = Rdf.object(part, Rdf.atelier(model, "spanFrom")).asResource().getProperty(Rdf.qudt(model, "unit"));
            parts.add(new PartSpan(iri, Atelier.plmOf(iri), Rdf.string(part, Rdf.atelier(model, "label")),
                    Rdf.englishName(part), own[0], own[1], unit == null ? null : unit.getResource().getLocalName(), own[2], own[3],
                    occurrences));
        }
    }

    /** A span along the axis moved by a rigid motion (3 x 4, row-major); null when the motion mixes the axis with another. */
    static double[] carried(double[] motion, double from, double to, int axis) {
        double sign = motion[axis * 4 + axis];
        for (int k = 0; k < 3; k++) {
            if (k != axis && Math.abs(motion[axis * 4 + k]) > SAME) return null;
        }
        if (Math.abs(Math.abs(sign) - 1) > SAME) return null;
        double a = sign * from + motion[axis * 4 + 3];
        double b = sign * to + motion[axis * 4 + 3];
        return new double[] {Math.min(a, b), Math.max(a, b)};
    }

    /** The inverse of a rigid transform: the transposed rotation and the translation brought back through it. */
    static double[] inverse(double[] m) {
        double[] out = new double[12];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) out[i * 4 + j] = m[j * 4 + i];
            out[i * 4 + 3] = -(m[i] * m[3] + m[4 + i] * m[7] + m[8 + i] * m[11]);
        }
        return out;
    }

    static double[] multiply(double[] a, double[] b) {
        double[] out = new double[12];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 4; j++) {
                double v = a[i * 4] * b[j] + a[i * 4 + 1] * b[4 + j] + a[i * 4 + 2] * b[8 + j];
                out[i * 4 + j] = j == 3 ? v + a[i * 4 + 3] : v;
            }
        }
        return out;
    }

    /** {from, to} as stored then {from, to} in mm, of a part's or an occurrence's span; null without a span or a unit. */
    private double[] span(Resource subject) {
        RDFNode from = Rdf.object(subject, Rdf.atelier(model, "spanFrom"));
        RDFNode to = Rdf.object(subject, Rdf.atelier(model, "spanTo"));
        if (from == null || to == null || !from.isResource() || !to.isResource()) return null;
        BigDecimal f = Rdf.decimal(from.asResource(), Rdf.qudt(model, "numericValue"));
        BigDecimal t = Rdf.decimal(to.asResource(), Rdf.qudt(model, "numericValue"));
        Double fm = mm(from);
        Double tm = mm(to);
        if (f == null || t == null || fm == null || tm == null) return null;
        return new double[] {f.doubleValue(), t.doubleValue(), fm, tm};
    }

    /** A quantity value in mm, or null when it has no value or no known length unit. */
    private Double mm(RDFNode node) {
        if (node == null || !node.isResource()) return null;
        BigDecimal value = Rdf.decimal(node.asResource(), Rdf.qudt(model, "numericValue"));
        Statement unit = node.asResource().getProperty(Rdf.qudt(model, "unit"));
        if (value == null || unit == null || !unit.getObject().isURIResource()) return null;
        BigDecimal mm = units.toMm(value, unit.getResource().getURI());
        return mm == null ? null : mm.doubleValue();
    }

    StationsJson.StationRef ref(String id) {
        StationsJson.Station s = stations.get(id);
        return new StationsJson.StationRef(id, s == null ? null : s.basis());
    }

    /** Whether the item is in the model after redaction: the viewer may see it. */
    boolean visible(String iri) {
        return model.createResource(iri).hasProperty(RDF.type);
    }
}
