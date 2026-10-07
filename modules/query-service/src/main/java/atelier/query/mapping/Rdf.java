// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mapping;

import atelier.query.Atelier;
import java.math.BigDecimal;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;

/** Single-valued reads from the federated model. */
final class Rdf {
    private Rdf() {}

    static Property atelier(Model model, String localName) {
        return model.createProperty(Atelier.ONT + localName);
    }

    static Property qudt(Model model, String localName) {
        return model.createProperty(Atelier.QUDT + localName);
    }

    static RDFNode object(Resource subject, Property property) {
        Statement s = subject.getProperty(property);
        return s == null ? null : s.getObject();
    }

    static String string(Resource subject, Property property) {
        RDFNode o = object(subject, property);
        return o == null ? null : o.isLiteral() ? o.asLiteral().getLexicalForm() : o.asResource().getURI();
    }

    static BigDecimal decimal(Resource subject, Property property) {
        String lexical = string(subject, property);
        return lexical == null ? null : new BigDecimal(lexical);
    }

    static Integer integer(Resource subject, Property property) {
        String lexical = string(subject, property);
        return lexical == null ? null : Integer.valueOf(lexical);
    }

    /** The item's English name: its skos:altLabel in en, or its skos:prefLabel when that is English (a UK item); null when none. */
    static String englishName(Resource item) {
        Model model = item.getModel();
        for (String predicate : List.of(Atelier.ALT_LABEL, Atelier.PREF_LABEL)) {
            for (Statement s : item.listProperties(model.createProperty(predicate)).toList()) {
                if (s.getObject().isLiteral() && "en".equals(s.getLiteral().getLanguage())) return s.getString();
            }
        }
        return null;
    }

    static List<Resource> resources(Resource subject, Property property) {
        return subject.listProperties(property).mapWith(s -> s.getObject().asResource()).toList();
    }
}
