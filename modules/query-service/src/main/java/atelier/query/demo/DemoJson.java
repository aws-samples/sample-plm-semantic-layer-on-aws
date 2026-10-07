// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.demo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/** Answer of the change list, as defined in docs/contract.md. */
public final class DemoJson {
    private DemoJson() {}

    /**
     * The live named graphs against the released files: triples added, triples removed (each with its IRIs and the
     * subject's PLM beside the shortened terms), live triple count per graph.
     */
    public record Graph(List<GraphDiff.Triple> added, List<GraphDiff.Triple> removed, Map<String, Long> triples) {}

    /** {@code GET /query/demo/changes}: the rows of {@code demo_change} as the core service holds them, and the graph diff. */
    public record Changes(List<JsonNode> values, Graph graph) {}
}
