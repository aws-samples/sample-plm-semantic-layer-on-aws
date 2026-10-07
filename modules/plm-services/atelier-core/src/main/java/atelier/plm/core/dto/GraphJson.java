// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** Answers of the core service's graph endpoints, as defined in docs/contract.md ("Freshness and the change feed"). */
public final class GraphJson {

    private GraphJson() {
    }

    /**
     * {@code POST /core/links}: the links graph's triple count after the write and EventBridge's id of the event
     * announcing it; {@code eventId} is null and {@code eventSkipped} says why when no event was put.
     */
    public record LinkPublished(Map<String, Long> triples, String eventId, @JsonInclude(JsonInclude.Include.NON_NULL) String eventSkipped) {
    }

    /**
     * {@code POST /core/equivalences}: the parts confirmed as one item, the links graph's triple count after the write
     * and EventBridge's id of the event announcing it, as {@link LinkPublished}.
     */
    public record EquivalenceConfirmed(java.util.List<String> parts, Map<String, Long> triples, String eventId,
                                       @JsonInclude(JsonInclude.Include.NON_NULL) String eventSkipped) {
    }

    /** {@code POST /core/graphs/reset}: the triple count of each released graph read back from the store. */
    public record Reset(Map<String, Long> graphs) {
    }

    /** {@code POST /core/demo/reset}: how many value corrections were undone, and the triple count of each released graph read back from the store. */
    public record DemoReset(int undone, Map<String, Long> graphs) {
    }

    /** {@code GET /core/graphs/health}: whether a link store is configured, and the triple count of each released graph as bundled. */
    public record Health(boolean neptune, Map<String, Long> releasedGraphs) {
    }
}
