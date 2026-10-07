// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import java.util.List;
import java.util.Optional;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

/**
 * A request's reference to an interface: its id (the last segment of its IRI) and, when the request
 * gives one, the key of its product. Interface ids are unique within a product only, so an id alone
 * names an interface when exactly one product of the merged graph has an interface of that id.
 */
public record InterfaceRef(String id, String product) {

    /**
     * The IRI of the interface the reference names among the interfaces of {@code merged}; empty when
     * none does. An id without a product that several products have is refused with their keys.
     */
    Optional<String> resolve(Model merged) {
        Resource type = merged.createResource(Atelier.ONT + "Interface");
        if (product != null) {
            Resource iface = merged.createResource(Atelier.interfaceIri(product, id));
            return iface.hasProperty(RDF.type, type) ? Optional.of(iface.getURI()) : Optional.empty();
        }
        List<String> found = merged.listSubjectsWithProperty(RDF.type, type).mapWith(Resource::getURI)
                .filterKeep(iri -> iri.startsWith(Atelier.INTERFACE) && Atelier.nativeId(iri).equals(id))
                .toList().stream().sorted().toList();
        if (found.size() > 1) {
            throw new AmbiguousInterface(id, found.stream().map(Atelier::productOf).toList());
        }
        return found.stream().findFirst();
    }

    /** {@code IF-07}, or {@code wings/IF-07} when the product is named: how answers spell what was asked. */
    @Override
    public String toString() {
        return product == null ? id : product + "/" + id;
    }
}
