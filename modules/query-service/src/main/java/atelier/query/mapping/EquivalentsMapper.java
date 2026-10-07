// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mapping;

import atelier.query.Atelier;
import atelier.query.api.Json;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

/**
 * Entity resolution of purchased parts as a query over the merged graph, driven by the ontology's item classes
 * ({@link ItemClasses}): the visible parts of one class that agree on every identifying attribute of the class are one
 * item. A length agrees within the attribute's tolerance after conversion to millimetres; a text resolved in a scheme
 * agrees when both name the same concept, and the length written inside it (a film's thickness) within the tolerance;
 * another text agrees when it is the same, case and spaces ignored. A value a part does not state agrees only with
 * another missing one, unless the size its standard designates in the class's standard scheme supplies it. A part whose
 * resolved text or standard names no concept is left out. A part joins the first group of its class whose first member
 * it agrees with; groups of one part are not reported. A group is {@code confirmed} when the links graph states
 * {@code owl:sameAs} between every two of its members: a user confirmed it ({@code POST /core/equivalences}); until then
 * it is a proposal.
 */
public final class EquivalentsMapper {
    private static final Pattern THICKNESS = Pattern.compile("^(.*?)[\\s,]*(\\d+(?:[.,]\\d+)?)\\s*(mm|in)\\s*$");
    private static final Map<String, String> UNIT_WORDS = Map.of("mm", Atelier.UNIT + "MilliM", "in", Atelier.UNIT + "IN");

    private final Model model;
    private final Units units;
    private final ItemClasses classes;

    public EquivalentsMapper(Model model, Units units, ItemClasses classes) {
        this.model = model;
        this.units = units;
        this.classes = classes;
    }

    /** One identifying value: a length in mm, a concept with the length inside its text, or a text key; all null when missing. */
    private record Value(BigDecimal mm, String concept, String text, Json.MemberValue json) {
        boolean missing() {
            return mm == null && concept == null && text == null;
        }
    }

    private record Member(ItemClasses.ItemClass itemClass, List<Value> values, Integer shelfLife, Json.EquivalentMember json) {}

    public List<Json.EquivalentGroup> groups() {
        Map<String, List<List<Member>>> byClass = new LinkedHashMap<>();
        model.listSubjectsWithProperty(RDF.type, model.createResource(Atelier.ONT + "Part")).toList().stream()
                .sorted(Comparator.comparing((Resource p) -> Atelier.PLMS.indexOf(Atelier.plmOf(p.getURI())))
                        .thenComparing(p -> Atelier.nativeId(p.getURI())))
                .map(this::member).filter(Objects::nonNull)
                .forEach(m -> {
                    List<List<Member>> groups = byClass.computeIfAbsent(m.itemClass().notation(), c -> new ArrayList<>());
                    groups.stream().filter(g -> agree(m.itemClass(), g.get(0), m)).findFirst()
                            .ifPresentOrElse(g -> g.add(m), () -> groups.add(new ArrayList<>(List.of(m))));
                });
        return byClass.values().stream().flatMap(List::stream).filter(g -> g.size() > 1)
                .sorted(Comparator.comparing((List<Member> g) -> g.get(0).itemClass().notation()).thenComparing(EquivalentsMapper::compare))
                .map(this::group).toList();
    }

    private Json.EquivalentGroup group(List<Member> g) {
        Member first = g.get(0);
        List<ItemClasses.Attribute> spec = first.itemClass().attributes();
        List<Json.GroupAttribute> attributes = new ArrayList<>();
        for (int i = 0; i < spec.size(); i++) {
            Value v = first.values().get(i);
            attributes.add(new Json.GroupAttribute(spec.get(i).name(), spec.get(i).label(), v.mm() == null ? null : v.mm().doubleValue(),
                    v.concept(), v.concept() != null ? classes.label(v.concept()) : v.text() != null ? v.json().stored() : null));
        }
        Integer shelfLife = g.stream().map(Member::shelfLife).filter(Objects::nonNull).min(Integer::compare).orElse(null);
        boolean confirmed = confirmed(g);
        int sites = (int) g.stream().map(m -> m.json().plm()).distinct().count();
        return new Json.EquivalentGroup(first.itemClass().notation(), first.itemClass().label(), attributes, shelfLife,
                new Json.Stocking(g.size(), sites, confirmed ? 1 : g.size(), 1), confirmed, g.stream().map(Member::json).toList());
    }

    /** Groups of a class in the order of their first member's values: concept labels and texts, then lengths, missing first. */
    private static int compare(List<Member> a, List<Member> b) {
        List<Value> x = a.get(0).values(), y = b.get(0).values();
        for (int i = 0; i < x.size(); i++) {
            int c = Comparator.nullsFirst(Comparator.<String>naturalOrder()).compare(sortText(x.get(i)), sortText(y.get(i)));
            if (c == 0) c = Comparator.nullsFirst(Comparator.<BigDecimal>naturalOrder()).compare(x.get(i).mm(), y.get(i).mm());
            if (c != 0) return c;
        }
        return 0;
    }

    private static String sortText(Value v) {
        return v.json() == null ? null : v.json().conceptLabel() != null ? v.json().conceptLabel() : v.text() != null ? v.json().stored() : null;
    }

    private boolean confirmed(List<Member> group) {
        Property sameAs = model.createProperty(Atelier.SAME_AS);
        for (Member a : group) {
            for (Member b : group) {
                if (a != b && !model.contains(model.createResource(a.json().iri()), sameAs, model.createResource(b.json().iri()))) return false;
            }
        }
        return true;
    }

    private static boolean agree(ItemClasses.ItemClass itemClass, Member a, Member b) {
        for (int i = 0; i < itemClass.attributes().size(); i++) {
            BigDecimal tolerance = itemClass.attributes().get(i).toleranceMm();
            Value x = a.values().get(i), y = b.values().get(i);
            if (x.missing() || y.missing()) {
                if (x.missing() != y.missing()) return false;
                continue;
            }
            if (!Objects.equals(x.concept(), y.concept()) || !Objects.equals(x.text(), y.text())) return false;
            if ((x.mm() == null) != (y.mm() == null)) return false;
            if (x.mm() != null && x.mm().subtract(y.mm()).abs().compareTo(tolerance) > 0) return false;
        }
        return true;
    }

    /** The part as a candidate member of its class, or null when it has no known class, states nothing, or names no concept. */
    private Member member(Resource part) {
        ItemClasses.ItemClass itemClass = classes.of(Rdf.string(part, Rdf.atelier(model, "itemClass")));
        if (itemClass == null) return null;
        String standard = Rdf.string(part, Rdf.atelier(model, "standard"));
        String size = null;
        if (itemClass.standardScheme() != null && standard != null) {
            size = classes.resolve(itemClass.standardScheme(), standard);
            if (size == null) return null;
        }
        List<Value> values = new ArrayList<>();
        for (ItemClasses.Attribute attribute : itemClass.attributes()) {
            Value v = value(part, attribute, size);
            if (v == null) return null;
            values.add(v);
        }
        if (values.stream().allMatch(Value::missing)) return null;
        RDFNode shelf = Rdf.object(part, Rdf.atelier(model, "shelfLifeMonths"));
        Integer shelfLife = shelf == null ? null : shelf.asLiteral().getInt();
        return new Member(itemClass, values, shelfLife, new Json.EquivalentMember(Atelier.plmOf(part.getURI()), Atelier.nativeId(part.getURI()),
                part.getURI(), Rdf.string(part, Rdf.atelier(model, "label")), standard,
                values.stream().map(Value::json).filter(Objects::nonNull).toList(), shelfLife));
    }

    /** The part's value of one identifying attribute, a missing value when it states none, null when it cannot be compared. */
    private Value value(Resource part, ItemClasses.Attribute attribute, String size) {
        RDFNode node = Rdf.object(part, model.createProperty(attribute.property()));
        if (node == null && size != null) {
            Resource fromSize = classes.sizeValue(size, attribute.property());
            if (fromSize != null) return length(attribute, fromSize, true);
        }
        if (node == null) return new Value(null, null, null, null);
        if (node.isResource()) return length(attribute, node.asResource(), false);
        String text = node.asLiteral().getLexicalForm();
        if (attribute.scheme() == null) {
            return new Value(null, null, ItemClasses.legendKey(text), new Json.MemberValue(attribute.name(), text, null, null, null, null, null));
        }
        String named = text;
        BigDecimal thickness = null;
        Matcher m = attribute.toleranceMm() == null ? null : THICKNESS.matcher(text);
        if (m != null && m.matches()) {
            named = m.group(1);
            thickness = units.toMm(new BigDecimal(m.group(2).replace(',', '.')), UNIT_WORDS.get(m.group(3)));
        }
        String concept = classes.resolve(attribute.scheme(), named);
        if (concept == null) return null;
        return new Value(thickness, concept, null, new Json.MemberValue(attribute.name(), text, null,
                thickness == null ? null : thickness.stripTrailingZeros().doubleValue(), concept, classes.label(concept), null));
    }

    /** A length as stored, with its unit and in mm; null when its unit is unknown. */
    private Value length(ItemClasses.Attribute attribute, Resource node, boolean fromStandard) {
        BigDecimal value = Rdf.decimal(node, node.getModel().createProperty(Atelier.QUDT + "numericValue"));
        String unit = Rdf.string(node, node.getModel().createProperty(Atelier.QUDT + "unit"));
        BigDecimal mm = value == null ? null : units.toMm(value, unit);
        if (mm == null) return null;
        return new Value(mm, null, null, new Json.MemberValue(attribute.name(), value.toPlainString(), Atelier.localName(unit),
                mm.stripTrailingZeros().doubleValue(), null, null, fromStandard ? true : null));
    }
}
