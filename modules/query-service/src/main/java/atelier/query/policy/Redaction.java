// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.policy;

import atelier.query.Atelier;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;

/**
 * The export-control policy on the merged graph. A part is visible when its tag's
 * {@code atelier:releasableTo} is one of the viewer's tokens; a part with no tag ({@code atelier:taggedBy}
 * absent) is visible only to a profile that sees untagged parts. {@link #visible} names the parts
 * the PLMs are asked about, from the link-store facts and the core tags; {@link #apply} then
 * enforces the same rule on the merged graph before SHACL: every other part the graph mentions is
 * removed with the features mounted on it and their quantity values, the external references it makes, the supplier
 * offers for it and the occurrences of the lines that use it, and what the link store's labels and links graphs say of it (its names, its confirmed equivalences,
 * and a visible part's equivalence to it). The core request only
 * returns the tags the viewer may see and the PLMs are only asked about visible parts, so on a
 * well-behaved federation this removes nothing but the file-index and tag facts of hidden parts;
 * it guarantees that nothing a source lets through by mistake is validated or shown. Link-store
 * facts naming the hidden features stay, which is how the mapper knows which side of an interface
 * is hidden; {@link #hidden} lists those IRIs for answers that must not name them. Hidden parts are
 * never named: an interface with a hidden part loses its curated label (which spells both parts)
 * for one composed from the parts in IRI order, each visible part by its own name and each hidden
 * part as {@value #HIDDEN_PART}; every answer and the evidence read that label.
 */
public final class Redaction {
    /** What stands for a hidden part wherever an answer would spell its name. */
    public static final String HIDDEN_PART = "a part not visible to your profile";

    private Redaction() {}

    public static void apply(Model model, Collection<String> releasable, boolean untaggedVisible) {
        Property onPart = model.createProperty(Atelier.ONT + "onPart");
        Property fromPart = model.createProperty(Atelier.FROM_PART);
        Property offersPart = model.createProperty(Atelier.ONT + "offersPart");
        for (Resource part : parts(model)) {
            if (visible(part, releasable, untaggedVisible)) continue;
            for (Resource offer : model.listSubjectsWithProperty(offersPart, part).toList()) {
                model.removeAll(offer, null, null);
            }
            for (Resource feature : model.listSubjectsWithProperty(onPart, part).toList()) {
                removeWithValues(model, feature);
            }
            for (Resource reference : model.listSubjectsWithProperty(fromPart, part).toList()) {
                removeWithValues(model, reference);
            }
            removeWithValues(model, part);
        }
        // A line's occurrences follow its child: a hidden item has no placements, and an occurrence whose child is unknown
        // is withheld as well.
        Property ofLine = model.createProperty(Atelier.ONT + "ofLine");
        Property child = model.createProperty(Atelier.ONT + "child");
        for (Resource occurrence : model.listSubjectsWithProperty(ofLine).toList()) {
            RDFNode line = occurrence.getProperty(ofLine).getObject();
            Statement placed = line.isURIResource() ? line.asResource().getProperty(child) : null;
            if (placed == null || !placed.getObject().isURIResource() || !visible(placed.getResource(), releasable, untaggedVisible)) {
                removeWithValues(model, occurrence);
            }
        }
        // The file index answers for every part, also those no interface names and no PLM describes.
        for (String predicate : Atelier.FILE_INDEX_PREDICATES) {
            Property property = model.createProperty(predicate);
            for (Resource subject : model.listSubjectsWithProperty(property).toList()) {
                if (!visible(subject, releasable, untaggedVisible)) model.removeAll(subject, property, null);
            }
        }
        // The layer's names and equivalences of a hidden part, and a visible part's equivalence to one the viewer may not
        // see; the glossary's concepts are not parts and stay.
        for (String predicate : Atelier.LAYER_PREDICATES) {
            for (Statement s : model.listStatements(null, model.createProperty(predicate), (RDFNode) null).toList()) {
                if (!s.getSubject().isURIResource() || Atelier.plmOf(s.getSubject().getURI()) == null) continue;
                boolean hiddenObject = s.getObject().isURIResource() && !visible(s.getObject().asResource(), releasable, untaggedVisible);
                if (!visible(s.getSubject(), releasable, untaggedVisible) || hiddenObject) model.remove(s);
            }
        }
        hideLabels(model);
    }

    /** Replaces the label of every interface naming a hidden part with one that names the visible parts only. */
    private static void hideLabels(Model model) {
        Property betweenPart = model.createProperty(Atelier.ONT + "betweenPart");
        Property label = model.createProperty(Atelier.ONT + "label");
        for (Resource iface : model.listSubjectsWithProperty(betweenPart).toList()) {
            List<Resource> parts = model.listObjectsOfProperty(iface, betweenPart).filterKeep(RDFNode::isURIResource)
                    .mapWith(RDFNode::asResource).toList().stream().sorted(Comparator.comparing(Resource::getURI)).toList();
            if (!iface.hasProperty(label) || parts.stream().allMatch(part -> part.hasProperty(RDF.type))) continue;
            model.removeAll(iface, label, null);
            iface.addProperty(label, parts.stream().map(part -> part.hasProperty(RDF.type) ? name(part) : HIDDEN_PART)
                    .collect(Collectors.joining(" / ")));
        }
    }

    /** A visible part's own name as its PLM states it, or its native id when the PLM gives none. */
    private static String name(Resource part) {
        Statement label = part.getProperty(part.getModel().createProperty(Atelier.ONT + "label"));
        return label != null && label.getObject().isLiteral() ? label.getString() : Atelier.nativeId(part.getURI());
    }

    /**
     * IRIs the redacted graph mentions only because the link store names them: the hidden parts
     * (named by an interface, no {@code rdf:type}) and the features an interface declares on a
     * hidden side (no {@code rdf:type}, owned by a PLM hidden on that interface). Answers show them
     * as redaction markers, never by IRI or id.
     */
    public static Set<String> hidden(Model model) {
        Property betweenPart = model.createProperty(Atelier.ONT + "betweenPart");
        Property declaresFeature = model.createProperty(Atelier.ONT + "declaresFeature");
        Set<String> hidden = new LinkedHashSet<>();
        for (Resource iface : model.listSubjectsWithProperty(betweenPart).toList()) {
            Set<String> hiddenPlms = new HashSet<>();
            for (Resource part : undescribed(model.listObjectsOfProperty(iface, betweenPart).toList())) {
                hidden.add(part.getURI());
                hiddenPlms.add(Atelier.plmOf(part.getURI()));
            }
            for (Resource feature : undescribed(model.listObjectsOfProperty(iface, declaresFeature).toList())) {
                if (hiddenPlms.contains(Atelier.plmOf(feature.getURI()))) hidden.add(feature.getURI());
            }
        }
        return hidden;
    }

    private static List<Resource> undescribed(List<RDFNode> nodes) {
        return nodes.stream().filter(RDFNode::isURIResource).map(RDFNode::asResource)
                .filter(r -> !r.hasProperty(RDF.type)).toList();
    }

    /** IRIs of the parts the link store's interfaces name or the core graph places in a product, sorted. */
    public static List<String> named(Model model) {
        return named(model, part -> true);
    }

    /** IRIs of the named parts that carry no tag, sorted. */
    public static List<String> untagged(Model model) {
        Property taggedBy = model.createProperty(Atelier.ONT + "taggedBy");
        return named(model, part -> !part.hasProperty(taggedBy));
    }

    /** IRIs of the named parts the viewer may see (see {@link #apply}), sorted: what the PLMs are asked about. */
    public static List<String> visible(Model model, Collection<String> releasable, boolean untaggedVisible) {
        return named(model, part -> visible(part, releasable, untaggedVisible));
    }

    private static List<String> named(Model model, Predicate<Resource> keep) {
        Property betweenPart = model.createProperty(Atelier.ONT + "betweenPart");
        Property partOf = model.createProperty(Atelier.ONT + "partOf");
        Stream<Resource> onInterfaces = model.listObjectsOfProperty(betweenPart).filterKeep(RDFNode::isURIResource)
                .mapWith(RDFNode::asResource).toList().stream();
        Stream<Resource> inProducts = model.listSubjectsWithProperty(partOf).toList().stream();
        return Stream.concat(onInterfaces, inProducts).filter(keep).map(Resource::getURI).sorted().distinct().toList();
    }

    /** Every part the merged graph mentions: named by an interface, carrying a feature or a supplier offer, or described by a PLM. */
    private static Set<Resource> parts(Model model) {
        Set<Resource> parts = new LinkedHashSet<>();
        for (String predicate : List.of("betweenPart", "onPart", "offersPart")) {
            model.listObjectsOfProperty(model.createProperty(Atelier.ONT + predicate))
                    .filterKeep(RDFNode::isURIResource).mapWith(RDFNode::asResource).forEachRemaining(parts::add);
        }
        model.listSubjectsWithProperty(RDF.type, model.createResource(Atelier.ONT + "Part")).forEachRemaining(parts::add);
        return parts;
    }

    /** True when the viewer may see the part: its tag releases it to one of the viewer's tokens, or it carries none and the viewer sees untagged parts. */
    public static boolean visible(Resource part, Collection<String> releasable, boolean untaggedVisible) {
        if (!part.hasProperty(part.getModel().createProperty(Atelier.ONT + "taggedBy"))) return untaggedVisible;
        Statement token = part.getProperty(part.getModel().createProperty(Atelier.ONT + "releasableTo"));
        return token != null && token.getObject().isLiteral() && releasable.contains(token.getLiteral().getLexicalForm());
    }

    /**
     * Removes what the sources said about a subject (every triple but the link-store predicates,
     * so a hidden feature keeps its atelier:matesWith) and the value nodes minted under its IRI
     * (.../position/x, .../diameter).
     */
    private static void removeWithValues(Model model, Resource subject) {
        String prefix = subject.getURI() + "/";
        for (Statement s : subject.listProperties().toList()) {
            RDFNode o = s.getObject();
            if (o.isURIResource() && o.asResource().getURI().startsWith(prefix)) {
                model.removeAll(o.asResource(), null, null);
            }
            if (!Atelier.LINK_PREDICATES.contains(s.getPredicate().getURI()) && !Atelier.OPTION_PREDICATES.contains(s.getPredicate().getURI())) {
                model.remove(s);
            }
        }
    }
}
