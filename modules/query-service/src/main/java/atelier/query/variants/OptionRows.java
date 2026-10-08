// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.variants;

import atelier.query.Atelier;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;

/**
 * The rows a site holds only for one option, in a federation run. A run of the base product leaves them out: a feature
 * whose row carries an option code ({@code atelier:appliesUnderOption}) is a feature of another configuration, even when
 * its part is a base part. A variant run names the items only a non-default option holds as parts of the product, so
 * the core graph tags them and their sites describe them.
 */
public final class OptionRows {
    private OptionRows() {}

    /** Removes every feature that applies under an option, with the quantity values minted under its IRI. */
    public static void drop(Model model) {
        Property under = model.createProperty(Atelier.APPLIES_UNDER_OPTION);
        for (Resource feature : model.listSubjectsWithProperty(under).toList()) {
            if (!feature.isURIResource() || Atelier.kindOf(feature.getURI()) == null) continue;
            String prefix = feature.getURI() + "/";
            for (Statement s : feature.listProperties().toList()) {
                RDFNode o = s.getObject();
                if (o.isURIResource() && o.asResource().getURI().startsWith(prefix)) model.removeAll(o.asResource(), null, null);
            }
            model.removeAll(feature, null, null);
        }
    }

    /** States {@code atelier:partOf} the group's product for every part that applies under a non-default option of the group. */
    public static void name(Model model, String variantIri) {
        Resource variant = model.createResource(variantIri);
        Statement product = variant.getProperty(model.createProperty(Atelier.OF_PRODUCT));
        Statement fallback = variant.getProperty(model.createProperty(Atelier.ONT + "defaultOption"));
        if (product == null || fallback == null) return;
        Property under = model.createProperty(Atelier.APPLIES_UNDER_OPTION);
        Property optionOf = model.createProperty(Atelier.ONT + "optionOf");
        Property partOf = model.createProperty(Atelier.PART_OF);
        for (Statement s : model.listStatements(null, under, (RDFNode) null).toList()) {
            if (!s.getObject().isURIResource() || s.getObject().equals(fallback.getObject())) continue;
            if (!s.getResource().hasProperty(optionOf, variant)) continue;
            String iri = s.getSubject().getURI();
            if (iri != null && iri.contains("/part/") && Atelier.plmOf(iri) != null) {
                model.add(s.getSubject(), partOf, product.getObject());
            }
        }
    }
}
