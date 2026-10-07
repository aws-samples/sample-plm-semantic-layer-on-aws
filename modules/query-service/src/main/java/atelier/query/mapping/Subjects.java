// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mapping;

import atelier.query.Atelier;
import atelier.query.api.Json;
import atelier.query.policy.Redaction;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

/**
 * Resolves a part or feature reference of a request, a native id ({@code FR-ORN-KEEL-001},
 * {@code PL 5710-02}) or an IRI, against the merged graph: the parts the link store's interfaces
 * name and the features they declare. A resolved subject is visible when a source described it
 * ({@code rdf:type} present) and is then returned as the mapped JSON; one the link store names but
 * no source described is hidden by export control and returned as a redaction marker. A reference
 * no interface names resolves to nothing; one that names a subject in several PLMs is refused.
 */
public final class Subjects {
    private final Model model;

    public Subjects(Model model) {
        this.model = model;
    }

    /** The part a reference names, as the viewer sees it, among the parts the sources described. */
    public Optional<Json.PartView> part(String ref, List<Json.Part> described) {
        return one(ref, Redaction.named(model)).map(r -> visible(r)
                ? described.stream().filter(p -> is(r, p.plm(), p.id())).findFirst().<Json.PartView>map(p -> p).orElseThrow()
                : new Json.Redacted(true, Atelier.plmOf(r.getURI())));
    }

    /** The IRI of the part a reference names, among the parts the link store or the core graph names. */
    public Optional<String> partIri(String ref) {
        return one(ref, Redaction.named(model)).map(Resource::getURI);
    }

    /** The feature a reference names, as the viewer sees it, among the features of the mapped interfaces. */
    public Optional<Json.FeatureView> feature(String ref, List<Json.Interface> interfaces) {
        List<String> declared = model.listObjectsOfProperty(model.createProperty(Atelier.ONT + "declaresFeature"))
                .filterKeep(RDFNode::isURIResource).mapWith(f -> f.asResource().getURI()).toList().stream().sorted().distinct().toList();
        return one(ref, declared).map(r -> visible(r)
                ? interfaces.stream().flatMap(i -> i.features().stream()).filter(Json.Feature.class::isInstance)
                        .map(Json.Feature.class::cast).filter(f -> is(r, f.plm(), f.id())).findFirst().<Json.FeatureView>map(f -> f).orElseThrow()
                : new Json.Redacted(true, Atelier.plmOf(r.getURI())));
    }

    private Optional<Resource> one(String ref, List<String> iris) {
        Predicate<String> matches = iri -> iri.equals(ref) || Atelier.nativeId(iri).equals(ref);
        List<String> found = iris.stream().filter(matches).toList();
        if (found.size() > 1) {
            throw new IllegalArgumentException("'" + ref + "' names a subject in several PLMs; use the IRI: " + found);
        }
        return found.stream().findFirst().map(model::createResource);
    }

    private static boolean visible(Resource subject) {
        return subject.hasProperty(RDF.type);
    }

    private static boolean is(Resource subject, String plm, String id) {
        return plm.equals(Atelier.plmOf(subject.getURI())) && id.equals(Atelier.nativeId(subject.getURI()));
    }
}
