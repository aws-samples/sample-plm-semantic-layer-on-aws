// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import atelier.query.mapping.ExternalReferences;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

/**
 * One interface's share of the merged graph of every interface: the interface's own facts (type,
 * label, tolerance, the parts it names, the features it declares), the parts it names (their PLM
 * description and attributes with the mass value node, file-index CAD file and supplier, and core tag) and the features it declares with the value nodes
 * minted under them (positions, diameter, grip length, pressure rating), the external references the parts make with
 * the label, revision and lifecycle word of each visible target, and the bill-of-materials lines from the parts with the
 * same of each visible child. A hidden part or feature
 * keeps only what the link store says about it, as in the merged graph. Nothing of any other
 * interface is included, so SHACL over this graph reports for this interface only.
 */
final class Neighbourhood {
    private Neighbourhood() {}

    static Model of(Model merged, String interfaceIri) {
        Model out = ModelFactory.createDefaultModel();
        Resource iface = merged.createResource(interfaceIri);
        out.add(iface.listProperties());
        Map<String, String> targets = ExternalReferences.targets(merged);
        Property fromPart = merged.createProperty(Atelier.FROM_PART);
        Property parent = merged.createProperty(Atelier.ONT + "parent");
        Property child = merged.createProperty(Atelier.ONT + "child");
        for (RDFNode part : merged.listObjectsOfProperty(iface, merged.createProperty(Atelier.ONT + "betweenPart")).toList()) {
            addWithValues(out, part.asResource());
            for (Resource ref : merged.listSubjectsWithProperty(fromPart, part).toList()) {
                out.add(ref.listProperties());
                String target = targets.get(ref.getURI());
                if (target != null) addTarget(out, merged.createResource(target));
            }
            for (Resource line : merged.listSubjectsWithProperty(parent, part).toList()) {
                out.add(line.listProperties());
                for (RDFNode item : merged.listObjectsOfProperty(line, child).toList()) {
                    if (item.isURIResource()) addTarget(out, item.asResource());
                }
            }
        }
        for (RDFNode declared : merged.listObjectsOfProperty(iface, merged.createProperty(Atelier.ONT + "declaresFeature")).toList()) {
            addWithValues(out, declared.asResource());
        }
        return out;
    }

    /**
     * What the reference and lifecycle shapes read of a dependency (a reference's target, a line's child): its PLM label
     * (the dependency is visible), revision and lifecycle word. Not its type, so the data-quality shapes on parts do not
     * report a part of another interface.
     */
    private static void addTarget(Model out, Resource target) {
        for (String localName : List.of("label", "revision", "lifecycleLabel")) {
            out.add(target.listProperties(target.getModel().createProperty(Atelier.ONT + localName)));
        }
    }

    /** A subject's triples and those of the value nodes minted under its IRI. */
    private static void addWithValues(Model out, Resource subject) {
        out.add(subject.listProperties());
        String prefix = subject.getURI() + "/";
        subject.listProperties().forEachRemaining(s -> {
            if (s.getObject().isURIResource() && s.getResource().getURI().startsWith(prefix)) {
                out.add(s.getResource().listProperties());
            }
        });
    }
}
