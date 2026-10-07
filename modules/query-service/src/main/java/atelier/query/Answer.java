// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import atelier.query.api.Json;
import atelier.query.federation.Federator;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.shacl.ValidationReport;

/**
 * What one federated run produced: the mapped interfaces, and the material they were computed
 * from (the federation result after export-control redaction, with every request text it sent;
 * the graph that was validated and mapped; the SHACL report; the viewer policy), so the evidence
 * behind an answer comes from the same run that gave the answer.
 *
 * @param federation the run: the merged graph of every interface and each endpoint's measured share
 * @param model the graph validated and mapped: the merged graph, or one interface's {@link Neighbourhood} of it
 * @param findings the data-quality findings (sh:Warning results) of the model's visible parts, counted per rule
 */
public record Answer(List<Json.Interface> interfaces, Federator.Result federation, Model model,
                     ValidationReport report, Map<String, Integer> findings, Json.Timings timings, Json.Policy policy) {

    /** Every request of the run, in sending order, as one text. */
    public String sparql() {
        return federation.sparql();
    }

    public Json.Provenance provenance() {
        return new Json.Provenance(federation.calls());
    }

    /** Every visible part the interfaces name, once. */
    public List<Json.Part> parts() {
        return interfaces.stream().flatMap(i -> i.parts().stream()).filter(Json.Part.class::isInstance)
                .map(Json.Part.class::cast).distinct().toList();
    }
}
