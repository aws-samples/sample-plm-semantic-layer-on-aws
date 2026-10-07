// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.web;

import atelier.plm.common.demo.EventPublisher;
import atelier.plm.common.demo.DemoAccess;
import atelier.plm.common.policy.Policy;
import atelier.plm.core.dto.GraphJson;
import atelier.plm.core.dto.LinkRequest;
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
 * A mating link is Atelier's own fact, written by the core service: {@code POST /core/links}, body
 * {@code { from, to }}, for the integration role ({@code programme-cleared}) or the export-control
 * officer (403 otherwise, see {@link DemoAccess}). Appends
 * {@code <from> atelier:matesWith <to>} and its inverse to the links graph (Graph Store {@code POST},
 * never a {@code PUT}), reads the graph's triple count back, then puts one EventBridge event,
 * source {@value #SOURCE}, detail-type {@value #DETAIL_TYPE}, detail {@code { from, to, triples, at }}.
 * The write stands without its announcement: a publisher without a bus leaves {@code eventId} null
 * and says why in {@code eventSkipped}.
 */
@RestController
public class LinkController {

    static final String SOURCE = "atelier.graph";
    static final String DETAIL_TYPE = "interface.link.added";

    private static final Logger log = LoggerFactory.getLogger(LinkController.class);

    private final GraphStore store;
    private final EventPublisher publisher;
    private final ObjectMapper json;

    public LinkController(GraphStore store, EventPublisher publisher, ObjectMapper json) {
        this.store = store;
        this.publisher = publisher;
        this.json = json;
    }

    @PostMapping("/core/links")
    public GraphJson.LinkPublished publish(@RequestBody LinkRequest request,
                                           @RequestHeader(name = Policy.HEADER, required = false) String profile) {
        DemoAccess.integration().require(profile);
        request.requireDistinctFeatures();
        String turtle = "<" + request.from() + "> <" + Graphs.MATES_WITH + "> <" + request.to() + "> .\n"
                + "<" + request.to() + "> <" + Graphs.MATES_WITH + "> <" + request.from() + "> .\n";
        store.append(Graphs.LINKS, turtle.getBytes(StandardCharsets.UTF_8));
        long links = store.count(Graphs.LINKS);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("from", request.from());
        detail.put("to", request.to());
        detail.put("triples", links);
        detail.put("at", OffsetDateTime.now(ZoneOffset.UTC));
        String skipped = publisher.skipped();
        String eventId = skipped == null ? publisher.put(SOURCE, DETAIL_TYPE, toJson(detail)) : null;
        log.info("demo link from={} to={} links={} eventId={} skipped={}", request.from(), request.to(), links, eventId, skipped);
        return new GraphJson.LinkPublished(Map.of("links", links), eventId, skipped);
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return json.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialise the event detail", e);
        }
    }
}
