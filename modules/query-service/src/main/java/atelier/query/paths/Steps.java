// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.paths;

import atelier.query.Atelier;
import atelier.query.api.FunctionJson;
import atelier.query.api.Json;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.RDF;

/**
 * The parts and interfaces a path or flow step names, as the viewer sees them: a visible part by its site and id, a
 * hidden one as a redaction marker, and an interface with the status the rules gave it for the viewer's profile.
 */
public final class Steps {
    private final Model model;
    private final Map<String, Json.Interface> interfaces;

    /** {@code interfaces}: the product's mapped interfaces, keyed here by IRI. */
    public Steps(Model model, List<Json.Interface> interfaces) {
        this.model = model;
        this.interfaces = interfaces.stream().collect(Collectors.toMap(i -> Atelier.interfaceIri(i.product(), i.id()), Function.identity(),
                (a, b) -> a));
    }

    /** A part as a step names it. */
    public Json.PartView part(String iri) {
        return visible(iri) ? new Json.PartRef(Atelier.nativeId(iri), Atelier.plmOf(iri), null) : new Json.Redacted(true, Atelier.plmOf(iri));
    }

    public boolean visible(String iri) {
        return model.createResource(iri).hasProperty(RDF.type);
    }

    /** The interface of an IRI as the viewer's profile sees it; null for no interface. */
    public FunctionJson.Joint joint(String iri) {
        if (iri == null) return null;
        Json.Interface iface = interfaces.get(iri);
        if (iface == null) return new FunctionJson.Joint(Atelier.nativeId(iri), null, "not-evaluable", List.of());
        return new FunctionJson.Joint(iface.id(), iface.label(), iface.status(),
                iface.violations().stream().map(Json.Violation::rule).distinct().toList());
    }

    /** A connectivity step from one part to the next through an interface. */
    public FunctionJson.Step joint(String from, String to, String iface) {
        return new FunctionJson.Step(part(from), part(to), joint(iface), null, null, null);
    }
}
