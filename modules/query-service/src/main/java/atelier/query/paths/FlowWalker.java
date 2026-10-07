// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.paths;

import atelier.query.Atelier;
import atelier.query.api.FunctionJson;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

/**
 * Function through a product: the walk along its functional edges ({@code atelier:Drive} in the link store) from one
 * part, breadth first, downstream from driver to driven or upstream for root cause, every flow kind or one. Each edge is
 * a step once, so a loop (a governor feeding back to the throttle) shows its closing edge and stops. A part the viewer
 * may not see is reached as a redaction marker and not walked past. The walk keeps the edge each part was first reached
 * by, the tree a {@link GearTrain} computes the ratio along.
 */
public final class FlowWalker {
    public static final int MAX_STEPS = 60;
    public static final List<String> FLOWS = List.of("mechanical", "electrical", "hydraulic", "steam");

    /** One functional edge: its IRI, driving and driven part, flow kind, interface (or null) and fixed gear (or null). */
    public record Drive(String iri, String driver, String driven, String flow, String via, String reaction) {}

    /** The walk: its edges in walking order, the visible parts in reaching order (the start first), the edge each was reached by. */
    public record Walk(List<Drive> edges, List<String> parts, Map<String, Drive> reachedBy, List<String> notes) {}

    private final Model model;
    private final List<Drive> drives;

    public FlowWalker(Model model) {
        this.model = model;
        this.drives = model.listSubjectsWithProperty(RDF.type, model.createResource(Atelier.ONT + "Drive")).toList().stream()
                .map(this::drive).filter(d -> d.driver() != null && d.driven() != null)
                .sorted(Comparator.comparing(Drive::iri)).toList();
    }

    public Walk walk(String start, String flow, boolean up) {
        if (flow != null && !FLOWS.contains(flow)) {
            throw new IllegalArgumentException("unknown flow " + flow + "; one of " + FLOWS);
        }
        List<Drive> edges = new ArrayList<>();
        List<String> parts = new ArrayList<>(List.of(start));
        Map<String, Drive> reachedBy = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>(List.of(start));
        List<String> notes = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>(List.of(start));
        boolean hidden = false;
        walk:
        while (!queue.isEmpty()) {
            String part = queue.poll();
            for (Drive d : drives) {
                if (!(up ? d.driven() : d.driver()).equals(part) || (flow != null && !flow.equals(d.flow()))) continue;
                if (edges.size() == MAX_STEPS) {
                    notes.add("The walk stops after " + MAX_STEPS + " steps.");
                    break walk;
                }
                edges.add(d);
                String next = up ? d.driver() : d.driven();
                if (!seen.add(next)) continue;
                if (!visible(next)) {
                    hidden = true;
                    continue;
                }
                reachedBy.put(next, d);
                parts.add(next);
                queue.add(next);
            }
        }
        if (hidden) notes.add("The walk reaches a part not visible to your profile and does not go past it.");
        if (edges.isEmpty()) {
            notes.add("No functional edge" + (flow == null ? "" : " of flow " + flow) + (up ? " ends at " : " starts at ")
                    + Atelier.nativeId(start) + ".");
        }
        return new Walk(edges, parts, reachedBy, notes);
    }

    /** A walked edge as a step: both parts as the viewer sees them, the interface's status, the flow and the gear stage. */
    public static FunctionJson.Step step(Drive d, Steps steps, GearTrain train) {
        return new FunctionJson.Step(steps.part(d.driver()), steps.part(d.driven()), steps.joint(d.via()), d.flow(),
                d.reaction() == null ? null : steps.part(d.reaction()), train.stage(d));
    }

    private boolean visible(String iri) {
        return model.createResource(iri).hasProperty(RDF.type);
    }

    private Drive drive(Resource d) {
        return new Drive(d.getURI(), uri(d, "driver"), uri(d, "driven"), literal(d, "flow"), uri(d, "viaInterface"), uri(d, "reactionPart"));
    }

    private String uri(Resource subject, String localName) {
        var s = subject.getProperty(model.createProperty(Atelier.ONT + localName));
        return s == null || !s.getObject().isURIResource() ? null : s.getResource().getURI();
    }

    private String literal(Resource subject, String localName) {
        var s = subject.getProperty(model.createProperty(Atelier.ONT + localName));
        return s == null || !s.getObject().isLiteral() ? null : s.getString();
    }
}
