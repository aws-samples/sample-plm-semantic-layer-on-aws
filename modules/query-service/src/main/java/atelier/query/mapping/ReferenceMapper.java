// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mapping;

import atelier.query.Atelier;
import atelier.query.api.Json;
import atelier.query.validation.RuleViolation;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

/**
 * Maps the external references of a redacted model to the contract's Reference JSON. The status is the shapes'
 * verdict: a danglingReference or staleRevision result whose value is the reference. A reference no shape reported
 * is {@code ok} when the viewer sees its target, and {@code not-evaluable} when the target exists but is hidden, since
 * a revision can only be compared on a part the viewer sees. The target is named only when the viewer sees it.
 */
public final class ReferenceMapper {
    public static final String OK = "ok";
    public static final String NOT_EVALUABLE = "not-evaluable";

    private final Model model;
    private final Lifecycles lifecycles;
    private final Map<String, RuleViolation> verdicts;

    /** @param results the SHACL results over the model; only the reference rules' results are read */
    public ReferenceMapper(Model model, Lifecycles lifecycles, List<RuleViolation> results) {
        this.model = model;
        this.lifecycles = lifecycles;
        this.verdicts = results.stream()
                .filter(r -> ExternalReferences.RULES.contains(r.rule()) && r.value() != null && r.value().isURI())
                .collect(Collectors.toMap(r -> r.value().getURI(), Function.identity(), (a, b) -> a));
    }

    /** Every reference of the model, by local part then remote URN. */
    public List<Json.Reference> references() {
        Map<String, String> targets = ExternalReferences.targets(model);
        return model.listSubjectsWithProperty(RDF.type, model.createResource(Atelier.EXTERNAL_REFERENCE)).toList().stream()
                .map(ref -> reference(ref, targets.get(ref.getURI())))
                .sorted(Comparator.comparing(Json.Reference::part).thenComparing(Json.Reference::remoteUrn).thenComparing(Json.Reference::id))
                .toList();
    }

    /** How many references have each status, in status order. */
    public static Map<String, Integer> counts(List<Json.Reference> references) {
        return references.stream().collect(Collectors.groupingBy(Json.Reference::status, TreeMap::new, Collectors.summingInt(r -> 1)));
    }

    private Json.Reference reference(Resource ref, String targetIri) {
        RuleViolation verdict = verdicts.get(ref.getURI());
        Resource target = targetIri == null ? null : model.createResource(targetIri);
        boolean visible = target != null && target.hasProperty(Rdf.atelier(model, "label"));
        String status = verdict != null ? verdict.rule() : visible ? OK : NOT_EVALUABLE;
        String lifecycle = visible ? Rdf.string(target, Rdf.atelier(model, "lifecycleLabel")) : null;
        BigDecimal quantity = Rdf.decimal(ref, Rdf.atelier(model, "quantity"));
        return new Json.Reference(Atelier.nativeId(ref.getURI()), Atelier.plmOf(ref.getURI()),
                Atelier.nativeId(Rdf.string(ref, model.createProperty(Atelier.FROM_PART))),
                Rdf.string(ref, model.createProperty(Atelier.REMOTE_URN)),
                visible ? new Json.PartRef(Atelier.nativeId(targetIri), Atelier.plmOf(targetIri), null) : null,
                quantity == null ? null : quantity.doubleValue(),
                Rdf.string(ref, model.createProperty(Atelier.EXPECTED_REVISION)),
                visible ? Rdf.string(target, Rdf.atelier(model, "revision")) : null,
                lifecycles.state(lifecycle), status, verdict == null ? null : verdict.message(),
                Rdf.string(ref, model.createProperty(RDFS.comment.getURI())));
    }
}
