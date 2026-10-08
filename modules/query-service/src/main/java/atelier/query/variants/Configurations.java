// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.variants;

import atelier.query.Atelier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;

/**
 * Splits a variant run's graph, which holds every option of one group, into the graph of one configuration: the base
 * product without what the configuration does not take. Under the default option that is everything only another option
 * holds; under another option it is also what the default option is made of, the lines from and to those items, and
 * then every item that had a parent line and has none left, so the bill-of-materials roll-up and the mass limit count
 * the configuration's own trees. The interfaces taken out leave with the mates only they declared: a feature an option
 * interface mates again keeps the mate of its own configuration only.
 */
public final class Configurations {
    private Configurations() {}

    /** A copy of {@code model} as the product is under {@code option}. */
    public static Model of(Model model, VariantFacts group, VariantFacts.Option option) {
        Model out = ModelFactory.createDefaultModel().add(model);
        Set<String> absent = new HashSet<>();
        for (VariantFacts.Option other : group.options()) {
            if (!other.equals(option)) absent.addAll(group.under(other));
        }
        absent.removeAll(group.under(option));
        Property declares = out.createProperty(Atelier.ONT + "declaresFeature");
        Property matesWith = out.createProperty(Atelier.ONT + "matesWith");
        Set<List<String>> keptPairs = new HashSet<>();
        for (Resource iface : out.listSubjectsWithProperty(declares).toList()) {
            if (absent.contains(iface.getURI())) continue;
            List<Resource> features = out.listObjectsOfProperty(iface, declares).mapWith(RDFNode::asResource).toList();
            for (Resource a : features) for (Resource b : features) keptPairs.add(List.of(a.getURI(), b.getURI()));
        }
        for (String iri : absent) {
            Resource subject = out.createResource(iri);
            if (iri.startsWith(Atelier.INTERFACE)) {
                List<Resource> features = out.listObjectsOfProperty(subject, declares).mapWith(RDFNode::asResource).toList();
                for (Resource a : features) {
                    for (Resource b : features) {
                        if (!keptPairs.contains(List.of(a.getURI(), b.getURI()))) out.remove(a, matesWith, b);
                    }
                }
            }
            if (VariantFacts.isItem(iri)) removeItem(out, subject);
            out.removeAll(subject, null, null);
        }
        Property under = out.createProperty(Atelier.APPLIES_UNDER_OPTION);
        for (Statement s : out.listStatements(null, under, (RDFNode) null).toList()) {
            if (s.getObject().isURIResource() && !s.getResource().getURI().equals(option.iri())
                    && Atelier.kindOf(s.getSubject().getURI()) != null) {
                removeSubject(out, s.getSubject());
            }
        }
        prune(model, out);
        return out;
    }

    /** An item the configuration does not take: its lines, its features, its product membership. */
    private static void removeItem(Model model, Resource item) {
        Property parent = model.createProperty(Atelier.ONT + "parent");
        Property child = model.createProperty(Atelier.ONT + "child");
        Property onPart = model.createProperty(Atelier.ONT + "onPart");
        for (Property end : List.of(parent, child)) {
            for (Resource line : model.listSubjectsWithProperty(end, item).toList()) model.removeAll(line, null, null);
        }
        for (Resource feature : model.listSubjectsWithProperty(onPart, item).toList()) removeSubject(model, feature);
        removeSubject(model, item);
    }

    private static void removeSubject(Model model, Resource subject) {
        String prefix = subject.getURI() + "/";
        for (Statement s : subject.listProperties().toList()) {
            RDFNode o = s.getObject();
            if (o.isURIResource() && o.asResource().getURI().startsWith(prefix)) model.removeAll(o.asResource(), null, null);
        }
        model.removeAll(subject, null, null);
    }

    /** Takes out every item of the product that had a parent line in {@code before} and has none left in {@code after}. */
    private static void prune(Model before, Model after) {
        Property child = after.createProperty(Atelier.ONT + "child");
        Property partOf = after.createProperty(Atelier.PART_OF);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Resource item : after.listSubjectsWithProperty(partOf).toList()) {
                boolean hadParent = before.contains(null, child, before.createResource(item.getURI()));
                if (hadParent && !after.contains(null, child, item) && after.contains(item, RDF.type)) {
                    removeItem(after, item);
                    changed = true;
                }
            }
        }
    }
}
