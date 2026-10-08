// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.demo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /query/demo/health}: how the change feed is wired, for any caller (no officer check:
 * the answer is configuration and released triple counts, no data). The business events published
 * (by the PLM services and the Atelier core service) and subscribed, the service that writes Atelier's
 * graphs ({@code writer}), the triple count of each released graph as bundled in the image, the
 * table the links loader logs value corrections in, and the route that resets the demo data (the
 * core service's, for the officer). {@code eventBus} is null: this service publishes nothing, so it
 * names no bus. The Architecture tab's "Events" row reads it.
 */
@RestController
public class DemoHealthController {
    static final String CHANGE_LOG = "atelier_core.demo_change";
    static final String WRITER = "atelier-core";
    static final String RESET = "POST /core/demo/reset (export-officer)";
    static final List<String> PUBLISHES = List.of(
            "atelier.plm/part.value.corrected (PLM services, logged by the links loader)",
            "atelier.plm/part.cad.published (PLM services)",
            "atelier.graph/interface.link.added (Atelier core service)",
            "atelier.graph/equivalence.confirmed (Atelier core service)");
    static final List<String> SUBSCRIPTIONS = List.of(
            "atelier.plm/part.value.corrected -> links loader -> " + CHANGE_LOG,
            "atelier.plm/part.cad.published -> links loader -> file index");

    private final ReleasedGraphs released;

    public DemoHealthController(ReleasedGraphs released) {
        this.released = released;
    }

    @GetMapping("/query/demo/health")
    public Health health() {
        Map<String, Long> graphs = new LinkedHashMap<>();
        released.all().forEach(graph -> graphs.put(graph.name(), graph.triples()));
        return new Health(null, WRITER, PUBLISHES, SUBSCRIPTIONS, graphs, CHANGE_LOG, RESET);
    }

    public record Health(String eventBus, String writer, List<String> publishes, List<String> subscriptions,
                         Map<String, Long> releasedGraphs, String changeLog, String reset) {}
}
