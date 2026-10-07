// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.web;

import atelier.plm.common.demo.DemoAccess;
import atelier.plm.common.policy.Policy;
import atelier.plm.core.dto.GraphJson;
import atelier.plm.core.graph.GraphStore;
import atelier.plm.core.graph.ReleasedGraphs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Atelier's released graphs at {@code /core/graphs}: {@code POST /reset} (the export-control officer alone,
 * 403 otherwise, see {@link DemoAccess})
 * PUTs the bundled {@code links.ttl}, {@code fileindex.ttl} and {@code labels.ttl} over their named graphs, which
 * drops every link, confirmed equivalence and CAD file the demo added, and answers the counts read back from the store;
 * {@code GET /health} (public, no data) says whether a link store is configured and how many
 * triples each released graph holds as bundled.
 */
@RestController
@RequestMapping("/core/graphs")
public class GraphController {

    private static final Logger log = LoggerFactory.getLogger(GraphController.class);

    private final GraphStore store;
    private final ReleasedGraphs released;

    public GraphController(GraphStore store, ReleasedGraphs released) {
        this.store = store;
        this.released = released;
    }

    @PostMapping("/reset")
    public GraphJson.Reset reset(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        DemoAccess.officer().require(profile);
        Map<String, Long> graphs = new LinkedHashMap<>();
        for (ReleasedGraphs.Released graph : released.all()) {
            store.put(graph.graph(), graph.turtle());
            graphs.put(graph.name(), store.count(graph.graph()));
        }
        log.info("demo graphs reset {}", graphs);
        return new GraphJson.Reset(graphs);
    }

    @GetMapping("/health")
    public GraphJson.Health health() {
        return new GraphJson.Health(store.configured(), released.triples());
    }
}
