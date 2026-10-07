// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mapping;

import atelier.query.Atelier;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDFS;

/**
 * The item classes of the ontology's {@code atelier:ItemClasses} scheme: per class (its {@code skos:notation}, the value
 * of a part's {@code atelier:itemClass}), the identifying attributes in their {@code atelier:attributeOrder}, each with
 * the part property it compares, its tolerance in millimetres and the scheme its text resolves in, and the scheme the
 * class's standards designate sizes in. Texts resolve to the concept of the scheme that carries them as a
 * {@code skos:prefLabel} or {@code skos:altLabel} in any language, case, spaces and hyphens ignored.
 */
public final class ItemClasses {
    /** An identifying attribute: a length when it has a tolerance and no scheme, a resolved text with a scheme, a text otherwise. */
    public record Attribute(String property, String label, BigDecimal toleranceMm, String scheme) {
        public String name() {
            return Atelier.localName(property);
        }
    }

    public record ItemClass(String concept, String notation, String label, List<Attribute> attributes, String standardScheme) {}

    private final Model ontology;
    private final Map<String, ItemClass> byNotation = new TreeMap<>();
    private final Map<String, Map<String, String>> conceptByKey = new HashMap<>();

    public ItemClasses(Model ontology) {
        this.ontology = ontology;
        Property inScheme = ontology.createProperty(Atelier.SKOS + "inScheme");
        for (Resource concept : ontology.listSubjectsWithProperty(inScheme, ontology.createResource(Atelier.ITEM_CLASSES)).toList()) {
            List<Attribute> attributes = concept.listProperties(ontology.createProperty(Atelier.ONT + "identifiedBy"))
                    .mapWith(s -> s.getObject().asResource()).toList().stream()
                    .sorted(Comparator.comparing(a -> Rdf.integer(a, Rdf.atelier(ontology, "attributeOrder"))))
                    .map(this::attribute).toList();
            String notation = Rdf.string(concept, ontology.createProperty(Atelier.SKOS + "notation"));
            byNotation.put(notation, new ItemClass(concept.getURI(), notation, label(concept.getURI()), attributes,
                    Rdf.string(concept, Rdf.atelier(ontology, "standardScheme"))));
        }
        for (Statement s : ontology.listStatements(null, inScheme, (RDFNode) null).toList()) {
            Map<String, String> labels = conceptByKey.computeIfAbsent(s.getResource().getURI(), k -> new HashMap<>());
            for (String p : List.of(Atelier.PREF_LABEL, Atelier.ALT_LABEL)) {
                s.getSubject().listProperties(ontology.createProperty(p))
                        .forEachRemaining(l -> labels.put(key(l.getLiteral().getLexicalForm()), s.getSubject().getURI()));
            }
        }
    }

    private Attribute attribute(Resource a) {
        String property = Rdf.string(a, Rdf.atelier(ontology, "onAttribute"));
        String label = Rdf.string(ontology.createResource(property), RDFS.label);
        return new Attribute(property, label == null ? Atelier.localName(property) : label,
                Rdf.decimal(a, Rdf.atelier(ontology, "matchToleranceMm")), Rdf.string(a, Rdf.atelier(ontology, "resolvedIn")));
    }

    /** The class whose notation a part's atelier:itemClass states, or null. */
    public ItemClass of(String notation) {
        return notation == null ? null : byNotation.get(notation.strip());
    }

    /** The concept of {@code scheme} that names {@code text}, or null. */
    public String resolve(String scheme, String text) {
        Map<String, String> labels = conceptByKey.get(scheme);
        return labels == null || text == null ? null : labels.get(key(text));
    }

    /** A concept's English or untagged preferred label, else any preferred label, else its local name. */
    public String label(String concept) {
        List<Statement> labels = ontology.createResource(concept).listProperties(ontology.createProperty(Atelier.PREF_LABEL)).toList();
        return labels.stream().filter(s -> List.of("", "en").contains(s.getLanguage())).findFirst()
                .or(() -> labels.stream().findFirst()).map(Statement::getString).orElse(Atelier.localName(concept));
    }

    /** The value node a size concept states for a property ({@code qudt:numericValue}, {@code qudt:unit}), or null. */
    public Resource sizeValue(String concept, String property) {
        RDFNode node = Rdf.object(ontology.createResource(concept), ontology.createProperty(property));
        return node != null && node.isResource() ? node.asResource() : null;
    }

    /** A text as the schemes compare it: Unicode-normalised, lower case, without spaces and hyphens. */
    static String key(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC).replaceAll("[\\s\\-\\u2010-\\u2015]+", "").toLowerCase(Locale.ROOT);
    }

    /** A legend as two sites' placards compare: Unicode-normalised, lower case, without spaces. */
    static String legendKey(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
