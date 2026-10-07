// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.variants;

import atelier.query.Atelier;
import atelier.query.api.Json;
import atelier.query.api.VariantJson;
import atelier.query.mapping.Units;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;

/**
 * Compares one option of a variant group with the group's default, port by port. A host is a part both configurations
 * hold (no option of the group lists it); each interface of the group in a configuration gives, for each visible
 * feature on a host, one side of the port the feature serves: the interface and its status, the feature and the part and
 * feature mated to it. Sides are paired by (host, port), or by (host, feature id) for a feature without a port. A port
 * either option lists as not modelled is reported as not modelled whatever the sides hold: present on the host, never
 * compared, so an answer never reads a port the data does not hold as matching.
 */
public final class VariantDiff {
    /** Two lengths in mm, or two pressures in bar, are the same value within this: a UK length in inches to four decimals is within 0.0013 mm. */
    private static final double SAME = 0.01;

    private final VariantFacts group;
    private final VariantFacts.Option option;

    public VariantDiff(VariantFacts group, VariantFacts.Option option) {
        this.group = group;
        this.option = option;
    }

    private record Keyed(Json.PartView host, VariantJson.Side side) {}

    /** The ports either configuration uses through an interface of the group, host by host. */
    public List<VariantJson.Port> ports(List<Json.Interface> against, List<Json.Interface> under) {
        Map<String, Keyed> before = sides(against, group.fallback());
        Map<String, Keyed> after = sides(under, option);
        Map<String, Json.PartView> hosts = new TreeMap<>();
        before.forEach((k, v) -> hosts.put(k, v.host()));
        after.forEach((k, v) -> hosts.putIfAbsent(k, v.host()));
        List<VariantJson.Port> ports = new ArrayList<>();
        for (Map.Entry<String, Json.PartView> entry : hosts.entrySet()) {
            Keyed a = before.get(entry.getKey());
            Keyed b = after.get(entry.getKey());
            String port = entry.getKey().substring(entry.getKey().indexOf('|') + 1);
            String named = port.startsWith("#") ? null : port;
            String change;
            List<String> differences = List.of();
            if (named != null && (option.notModelled().contains(named) || group.fallback().notModelled().contains(named))) {
                change = "not-modelled";
            } else if (a != null && b != null) {
                differences = differences(a.side(), b.side());
                change = differences.isEmpty() ? "same" : "changed";
            } else {
                change = a != null ? "removed" : "added";
            }
            ports.add(new VariantJson.Port(entry.getValue(), named, change, differences,
                    a == null ? null : a.side(), b == null ? null : b.side()));
        }
        return ports;
    }

    /** Per (host IRI | port), the side each host feature of the option's interfaces gives. */
    private Map<String, Keyed> sides(List<Json.Interface> interfaces, VariantFacts.Option of) {
        Set<String> ofOption = Set.copyOf(group.interfaces(of));
        Set<String> listed = group.anyOption();
        Map<String, Keyed> out = new LinkedHashMap<>();
        for (Json.Interface iface : interfaces) {
            if (!ofOption.contains(Atelier.interfaceIri(iface.product(), iface.id()))) continue;
            List<String> rules = iface.violations().stream().map(Json.Violation::rule).distinct().sorted().toList();
            for (Json.FeatureView view : iface.features()) {
                if (!(view instanceof Json.Feature feature)) continue;
                String hostIri = Atelier.partIri(feature.plm(), feature.partId());
                if (listed.contains(hostIri)) continue;
                Json.PartView host = null;
                Json.PartView mate = null;
                for (Json.PartView part : iface.parts()) {
                    if (part instanceof Json.Part p && p.id().equals(feature.partId()) && p.plm().equals(feature.plm())) host = ref(p);
                    else mate = part instanceof Json.Part p ? ref(p) : part;
                }
                Json.FeatureView mateFeature = mateOf(feature, iface);
                String port = feature.properties().get("port") instanceof String p ? p : "#" + feature.id();
                out.put(hostIri + "|" + port, new Keyed(host, new VariantJson.Side(iface.id(), iface.label(), iface.status(), rules,
                        feature, mate, mateFeature)));
            }
        }
        return out;
    }

    private static Json.PartRef ref(Json.Part part) {
        return new Json.PartRef(part.id(), part.plm(), part.supplier());
    }

    private static Json.FeatureView mateOf(Json.Feature feature, Json.Interface iface) {
        for (Object mate : feature.matesWith()) {
            if (mate instanceof Json.Redacted redacted) return redacted;
            for (Json.FeatureView other : iface.features()) {
                if (other instanceof Json.Feature f && f.id().equals(mate) && f != feature) return f;
            }
        }
        return null;
    }

    /** What differs between two sides of a port: the interface, its status, the mate, then each attribute that differs. */
    static List<String> differences(VariantJson.Side a, VariantJson.Side b) {
        List<String> out = new ArrayList<>();
        if (!a.interfaceId().equals(b.interfaceId())) out.add("interface");
        if (!a.status().equals(b.status())) out.add("status");
        if (!Objects.equals(a.mate(), b.mate())) out.add("mate");
        attributes("host", a.feature(), b.feature(), out);
        attributes("mate", a.mateFeature(), b.mateFeature(), out);
        return out;
    }

    private static void attributes(String side, Json.FeatureView a, Json.FeatureView b, List<String> out) {
        if (!(a instanceof Json.Feature x) || !(b instanceof Json.Feature y)) {
            if (!Objects.equals(a, b)) out.add(side);
            return;
        }
        if (!x.id().equals(y.id())) out.add(side + ".feature");
        if (!x.kind().equals(y.kind())) out.add(side + ".kind");
        if (!samePosition(x.positionMm(), y.positionMm())) out.add(side + ".position");
        Set<String> names = new java.util.TreeSet<>(x.properties().keySet());
        names.addAll(y.properties().keySet());
        names.remove("port");
        for (String name : names) {
            if (!sameValue(x.properties().get(name), y.properties().get(name))) out.add(side + "." + name);
        }
    }

    private static boolean samePosition(Json.Mm a, Json.Mm b) {
        if (a == null || b == null) return a == b;
        return Math.abs(a.x() - b.x()) < SAME && Math.abs(a.y() - b.y()) < SAME && Math.abs(a.z() - b.z()) < SAME;
    }

    private static boolean sameValue(Object a, Object b) {
        if (a instanceof Json.Quantity x && b instanceof Json.Quantity y) {
            Double p = x.mm() != null ? x.mm() : x.bar() != null ? x.bar() : x.value();
            Double q = y.mm() != null ? y.mm() : y.bar() != null ? y.bar() : y.value();
            return p != null && q != null ? Math.abs(p - q) < SAME : Objects.equals(p, q);
        }
        return Objects.equals(a, b);
    }

    /** The items and interfaces that apply under {@code of}, as the viewer may see them in {@code model}. */
    public VariantJson.Items items(VariantFacts.Option of, Model model) {
        List<Json.PartView> items = group.items(of).stream().map(iri -> {
            Resource item = model.createResource(iri);
            return item.hasProperty(RDF.type) ? (Json.PartView) new Json.PartRef(Atelier.nativeId(iri), Atelier.plmOf(iri), null)
                    : new Json.Redacted(true, Atelier.plmOf(iri));
        }).toList();
        return new VariantJson.Items(items, group.interfaces(of).stream().map(Atelier::nativeId).toList());
    }

    /**
     * One configuration's figures, read from its graph after the occurrences were derived: the tally of its interfaces,
     * the part occurrences per site and in total, and the mass of every item of the product that states one.
     */
    public static VariantJson.Configuration configuration(VariantFacts.Option of, List<Json.Interface> interfaces, Model model,
                                                          String productIri, Units units, List<Json.Finding> productFindings) {
        int pass = 0, fail = 0, notEvaluable = 0;
        for (Json.Interface iface : interfaces) {
            switch (iface.status()) {
                case "pass" -> pass++;
                case "fail" -> fail++;
                default -> notEvaluable++;
            }
        }
        Map<String, Double> occurrences = new TreeMap<>();
        double total = 0;
        BigDecimal mass = BigDecimal.ZERO;
        Resource product = model.createResource(productIri);
        for (Resource item : model.listSubjectsWithProperty(model.createProperty(Atelier.PART_OF), product).toList()) {
            BigDecimal n = decimal(item, Atelier.ONT + "occurrences");
            if (n == null) continue;
            String type = string(item, Atelier.ONT + "partType");
            if (type == null || "PART".equals(type)) {
                occurrences.merge(Atelier.plmOf(item.getURI()), n.doubleValue(), Double::sum);
                total += n.doubleValue();
            }
            Statement q = item.getProperty(model.createProperty(Atelier.MASS));
            if (q != null && q.getObject().isResource()) {
                BigDecimal value = decimal(q.getResource(), Atelier.QUDT + "numericValue");
                String unit = string(q.getResource(), Atelier.QUDT + "unit");
                BigDecimal kg = units.toKg(value, unit);
                if (kg != null) mass = mass.add(kg.multiply(n));
            }
        }
        return new VariantJson.Configuration(of.key(), interfaces.size(), new VariantJson.Tally(pass, fail, notEvaluable), occurrences,
                total, mass.setScale(3, java.math.RoundingMode.HALF_EVEN).doubleValue(), productFindings);
    }

    private static BigDecimal decimal(Resource subject, String predicate) {
        Statement s = subject.getProperty(subject.getModel().createProperty(predicate));
        if (s == null || !s.getObject().isLiteral()) return null;
        try {
            return new BigDecimal(s.getLiteral().getLexicalForm());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String string(Resource subject, String predicate) {
        Statement s = subject.getProperty(subject.getModel().createProperty(predicate));
        if (s == null) return null;
        RDFNode o = s.getObject();
        return o.isLiteral() ? o.asLiteral().getLexicalForm() : o.isURIResource() ? o.asResource().getURI() : null;
    }
}
