// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.variants;

import atelier.query.Atelier;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;

/**
 * A variant group as the options graph states it, read from a run's graph before redaction: the group, its options with
 * the default first, and per option the IRIs of everything that applies under it (items, interfaces, lines, functional
 * edges). Read before redaction because a hidden item keeps only its link-store facts, and an answer still says which
 * option a hidden item belongs to.
 */
public record VariantFacts(String iri, String key, String name, String selects, Option fallback, List<Option> options,
                           Map<String, Set<String>> under) {

    /** One option: its IRI and code, whether it is the default, and what the options graph says of it. */
    public record Option(String iri, String key, boolean isDefault, String source, String applicability, List<String> notModelled) {}

    private static final String DCTERMS_SOURCE = "http://purl.org/dc/terms/source";
    private static final String RDFS = "http://www.w3.org/2000/01/rdf-schema#";

    /** The group of that IRI in {@code model}, or null when the options graph has none. */
    public static VariantFacts read(Model model, String variantIri) {
        Resource variant = model.createResource(variantIri);
        Property optionOf = model.createProperty(Atelier.ONT + "optionOf");
        Statement fallback = variant.getProperty(model.createProperty(Atelier.ONT + "defaultOption"));
        if (fallback == null || !fallback.getObject().isURIResource()) return null;
        String defaultIri = fallback.getResource().getURI();
        List<Option> options = model.listSubjectsWithProperty(optionOf, variant).toList().stream()
                .map(o -> option(o, defaultIri))
                .sorted(Comparator.comparing((Option o) -> !o.isDefault()).thenComparing(Option::key))
                .toList();
        Property under = model.createProperty(Atelier.APPLIES_UNDER_OPTION);
        Map<String, Set<String>> applies = new LinkedHashMap<>();
        for (Option option : options) {
            Set<String> subjects = new TreeSet<>();
            model.listSubjectsWithProperty(under, model.createResource(option.iri())).forEachRemaining(s -> {
                if (s.isURIResource()) subjects.add(s.getURI());
            });
            applies.put(option.iri(), subjects);
        }
        return new VariantFacts(variantIri, Atelier.nativeId(variantIri), literal(variant, RDFS + "label"),
                literal(variant, RDFS + "comment"), options.get(0), options, applies);
    }

    private static Option option(Resource option, String defaultIri) {
        Model m = option.getModel();
        List<String> notModelled = m.listObjectsOfProperty(option, m.createProperty(Atelier.ONT + "portNotModelled"))
                .filterKeep(RDFNode::isLiteral).mapWith(n -> n.asLiteral().getLexicalForm()).toList().stream().sorted().toList();
        return new Option(option.getURI(), Atelier.nativeId(option.getURI()), option.getURI().equals(defaultIri),
                literal(option, DCTERMS_SOURCE), literal(option, Atelier.ONT + "applicability"), notModelled);
    }

    private static String literal(Resource subject, String predicate) {
        Statement s = subject.getProperty(subject.getModel().createProperty(predicate));
        return s == null || !s.getObject().isLiteral() ? null : s.getLiteral().getLexicalForm();
    }

    /** The option of that code, or null. */
    public Option option(String code) {
        return options.stream().filter(o -> o.key().equals(code)).findFirst().orElse(null);
    }

    /** What applies under the option, by IRI. */
    public Set<String> under(Option option) {
        return under.getOrDefault(option.iri(), Set.of());
    }

    /** Every IRI that applies under any option of the group. */
    public Set<String> anyOption() {
        Set<String> all = new TreeSet<>();
        under.values().forEach(all::addAll);
        return all;
    }

    /** The parts and assemblies that apply under the option, IRIs sorted. */
    public List<String> items(Option option) {
        return under(option).stream().filter(VariantFacts::isItem).toList();
    }

    /** The interfaces that apply under the option, IRIs sorted. */
    public List<String> interfaces(Option option) {
        return under(option).stream().filter(iri -> iri.startsWith(Atelier.INTERFACE)).toList();
    }

    static boolean isItem(String iri) {
        return iri.contains("/part/") && Atelier.plmOf(iri) != null;
    }
}
