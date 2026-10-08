// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.web;

import atelier.plm.common.demo.DemoAccess;
import atelier.plm.common.demo.EventPublisher;
import atelier.plm.common.policy.Policy;
import atelier.plm.core.dto.EquivalenceRequest;
import atelier.plm.core.dto.GraphJson;
import atelier.plm.core.graph.GraphStore;
import atelier.plm.core.graph.Graphs;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * That several part numbers of different sites are one purchased item is a user's decision, written by the core
 * service like a mating link: {@code POST /core/equivalences}, body {@code { parts }}, for the integration role
 * ({@code programme-cleared}) or the export-control officer (403 otherwise, see {@link DemoAccess}). Appends
 * {@code <a> owl:sameAs <b>} for every ordered pair of the parts to the links graph (Graph Store {@code POST}), reads
 * the graph's triple count back, then puts one EventBridge event, source {@value LinkController#SOURCE}, detail-type
 * {@value #DETAIL_TYPE}, detail {@code { parts, triples, at }}. The released {@code links.ttl} holds no
 * {@code owl:sameAs}, so the demo reset's PUT of it removes every confirmation.
 */
@RestController
public class EquivalenceController {

    static final String DETAIL_TYPE = "equivalence.confirmed";

    private static final Logger log = LoggerFactory.getLogger(EquivalenceController.class);

    private final GraphStore store;
    private final EventPublisher publisher;
    private final ObjectMapper json;

    public EquivalenceController(GraphStore store, EventPublisher publisher, ObjectMapper json) {
        this.store = store;
        this.publisher = publisher;
        this.json = json;
    }

    @PostMapping("/core/equivalences")
    public GraphJson.EquivalenceConfirmed confirm(@RequestBody EquivalenceRequest request,
                                                  @RequestHeader(name = Policy.HEADER, required = false) String profile) {
        DemoAccess.integration().require(profile);
        request.requireDistinctParts();
        StringBuilder turtle = new StringBuilder();
        for (String a : request.parts()) {
            for (String b : request.parts()) {
                if (!a.equals(b)) turtle.append('<').append(a).append("> <").append(Graphs.SAME_AS).append("> <").append(b).append("> .\n");
            }
        }
        store.append(Graphs.LINKS, turtle.toString().getBytes(StandardCharsets.UTF_8));
        long links = store.count(Graphs.LINKS);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("parts", request.parts());
        detail.put("triples", links);
        detail.put("at", OffsetDateTime.now(ZoneOffset.UTC));
        String skipped = publisher.skipped();
        String eventId = skipped == null ? publisher.put(LinkController.SOURCE, DETAIL_TYPE, toJson(detail)) : null;
        log.info("demo equivalence parts={} links={} eventId={} skipped={}", request.parts(), links, eventId, skipped);
        return new GraphJson.EquivalenceConfirmed(request.parts(), Map.of("links", links), eventId, skipped);
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return json.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialise the event detail", e);
        }
    }
}
