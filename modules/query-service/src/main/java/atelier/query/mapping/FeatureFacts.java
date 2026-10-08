// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mapping;

import atelier.query.Atelier;
import atelier.query.api.Json;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.function.Function;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

/**
 * An interface feature as read from the federated model: its kind (from the IRI path), each
 * position axis both as stored and in mm, and the class-specific properties of the contract with
 * quantities normalised (lengths to mm, pressures to bar). {@code present} is false for a feature
 * the link store declares but no PLM described.
 */
record FeatureFacts(String iri, String id, String plm, String kind, String partIri, String partId, boolean present,
                    Map<String, BigDecimal> stored, String unitIri, Map<String, BigDecimal> mm,
                    Map<String, Object> properties, List<String> matesWithIris) {

    private enum Type { STRING, INTEGER, LENGTH, PRESSURE }

    private record Prop(String name, Type type) {}

    /** The class-specific properties of each feature kind, in the order they are reported. */
    private static final Map<String, List<Prop>> PROPERTIES = Map.of(
            "plug", List.of(new Prop("connectorType", Type.STRING), new Prop("pinCount", Type.INTEGER)),
            "fastener", List.of(new Prop("fastenerStandard", Type.STRING), new Prop("diameter", Type.LENGTH),
                    new Prop("fastenerCount", Type.INTEGER), new Prop("gripLength", Type.LENGTH)),
            "coupling", List.of(new Prop("couplingStandard", Type.STRING), new Prop("dashSize", Type.INTEGER),
                    new Prop("pressureRating", Type.PRESSURE), new Prop("fluid", Type.STRING)));

    static FeatureFacts read(Resource feature, Units units) {
        Model m = feature.getModel();
        String kind = Atelier.kindOf(feature.getURI());
        Map<String, BigDecimal> stored = new HashMap<>();
        Map<String, BigDecimal> mm = new HashMap<>();
        String unitIri = null;
        for (String axis : Atelier.AXES) {
            Resource q = (Resource) Rdf.object(feature, m.createProperty(Atelier.positionProperty(axis)));
            if (q == null) continue;
            BigDecimal value = Rdf.decimal(q, Rdf.qudt(m, "numericValue"));
            String unit = Rdf.string(q, Rdf.qudt(m, "unit"));
            if (unit != null) unitIri = unit;
            if (value != null) stored.put(axis, value);
            BigDecimal inMm = units.toMm(value, unit);
            if (inMm != null) mm.put(axis, inMm);
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Prop prop : PROPERTIES.getOrDefault(kind, List.of())) {
            properties.put(prop.name(), value(feature, prop, units));
        }
        // The port is stated only where a site names one: a feature that serves a variant's slot.
        String port = Rdf.string(feature, Rdf.atelier(m, "port"));
        if (port != null) properties.put("port", port);
        String part = Rdf.string(feature, Rdf.atelier(m, "onPart"));
        List<String> mates = Rdf.resources(feature, Rdf.atelier(m, "matesWith")).stream()
                .map(Resource::getURI).sorted().toList();
        return new FeatureFacts(feature.getURI(), Atelier.nativeId(feature.getURI()), Atelier.plmOf(feature.getURI()), kind,
                part, part == null ? null : Atelier.nativeId(part), feature.hasProperty(RDF.type), stored, unitIri, mm,
                properties, mates);
    }

    private static Object value(Resource feature, Prop prop, Units units) {
        Model m = feature.getModel();
        return switch (prop.type()) {
            case STRING -> Rdf.string(feature, Rdf.atelier(m, prop.name()));
            case INTEGER -> Rdf.integer(feature, Rdf.atelier(m, prop.name()));
            case LENGTH, PRESSURE -> {
                Resource q = (Resource) Rdf.object(feature, Rdf.atelier(m, prop.name()));
                if (q == null) yield null;
                BigDecimal value = Rdf.decimal(q, Rdf.qudt(m, "numericValue"));
                String unit = Rdf.string(q, Rdf.qudt(m, "unit"));
                yield prop.type() == Type.LENGTH
                        ? new Json.Quantity(dbl(value), Atelier.localName(unit), dbl(units.toMm(value, unit)), null, null)
                        : new Json.Quantity(dbl(value), Atelier.localName(unit), null, dbl(units.toBar(value, unit)), null);
            }
        };
    }

    /** Distance in mm between this feature and another on one axis, or null if either lacks a mm value. */
    BigDecimal deltaMm(FeatureFacts other, String axis) {
        BigDecimal a = mm.get(axis);
        BigDecimal b = other.mm.get(axis);
        return a == null || b == null ? null : a.subtract(b).abs();
    }

    /** A property's plain value (string or integer), or null. */
    Object property(String name) {
        return properties.get(name);
    }

    /** A length property in mm, or null when absent or not convertible. */
    Double mm(String name) {
        return properties.get(name) instanceof Json.Quantity q ? q.mm() : null;
    }

    /** A pressure property in bar, or null when absent or not convertible. */
    Double bar(String name) {
        return properties.get(name) instanceof Json.Quantity q ? q.bar() : null;
    }

    /** The position in mm, or null unless all three axes converted. */
    Json.Mm positionMm() {
        return mm.size() == 3 ? new Json.Mm(mm.get("x").doubleValue(), mm.get("y").doubleValue(), mm.get("z").doubleValue()) : null;
    }

    /** @param mate how a mate IRI is shown: its id, or a redaction marker when the mate is hidden. */
    Json.Feature toJson(Function<String, Object> mate) {
        Json.Source source = new Json.Source(dbl(stored.get("x")), dbl(stored.get("y")), dbl(stored.get("z")),
                unitIri == null ? null : Atelier.localName(unitIri));
        return new Json.Feature(id, plm, partId, kind, properties, source, positionMm(),
                matesWithIris.stream().map(mate).toList());
    }

    static final Comparator<FeatureFacts> ORDER = Comparator.comparing((FeatureFacts f) -> Atelier.PLMS.indexOf(f.plm()))
            .thenComparing(f -> Atelier.KINDS.indexOf(f.kind())).thenComparing(FeatureFacts::id);

    private static Double dbl(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
