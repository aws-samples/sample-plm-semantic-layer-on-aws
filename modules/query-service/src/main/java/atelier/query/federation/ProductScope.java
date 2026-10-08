// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.federation;

import atelier.query.Atelier;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

/**
 * Restricts a run to one product before any PLM is asked. The link store says which product each
 * interface belongs to ({@code atelier:ofProduct}) and the core graph which products each part
 * belongs to ({@code atelier:partOf}); the product's interfaces and the product's parts stay, so an
 * interface follows its own product even when two products share a part. Everything else leaves
 * the model: the other interfaces with the features they declare, the parts outside the product with
 * their tags and file-index facts, and the other products. The parts left are the product's, so the
 * export-control filter then asks the PLMs about the product's visible parts only, and a row of a
 * part outside the product never leaves its PLM.
 */
final class ProductScope {
    private ProductScope() {}

    /**
     * Cuts {@code model} down to the product; false, with the model untouched, when no named part
     * belongs to it (an unknown product, or one none of whose parts an interface names).
     */
    static boolean restrict(Model model, String productIri) {
        Resource product = model.createResource(productIri);
        Property partOf = model.createProperty(Atelier.PART_OF);
        Set<Resource> inProduct = model.listSubjectsWithProperty(partOf, product).toSet();
        if (inProduct.isEmpty()) return false;

        Property ofProduct = model.createProperty(Atelier.OF_PRODUCT);
        Property betweenPart = model.createProperty(Atelier.ONT + "betweenPart");
        Property declaresFeature = model.createProperty(Atelier.ONT + "declaresFeature");
        Set<Resource> parts = new LinkedHashSet<>(model.listObjectsOfProperty(betweenPart).mapWith(RDFNode::asResource).toList());
        parts.addAll(model.listSubjectsWithProperty(partOf).toList());
        for (Resource iface : model.listSubjectsWithProperty(betweenPart).toList()) {
            if (iface.hasProperty(ofProduct, product)) continue;
            for (RDFNode feature : model.listObjectsOfProperty(iface, declaresFeature).toList()) {
                model.removeAll(feature.asResource(), null, null);
            }
            model.removeAll(iface, null, null);
        }
        for (Resource part : parts) {
            if (!inProduct.contains(part)) model.removeAll(part, null, null);
        }
        for (Resource other : model.listSubjectsWithProperty(RDF.type, model.createResource(Atelier.ONT + "Product")).toList()) {
            if (other.equals(product)) continue;
            model.removeAll(other, null, null);
            model.removeAll(null, partOf, other);
        }
        return true;
    }
}
