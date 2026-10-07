// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.demo;

import atelier.query.mcp.PlmApi;
import atelier.query.policy.Caller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.springframework.stereotype.Service;

/**
 * The change list of docs/contract.md ("Freshness and the change feed"), derived rather than
 * declared: the rows of {@code atelier_core.demo_change} as the core service lists them ({@code GET
 * /core/changes}; the links loader records them from the PLM services' {@code part.value.corrected}
 * events), and the live named graphs diffed against the released files bundled here. The query
 * service reads and writes to no store. It keeps no cache of the link store, so every answer sees
 * the current graphs and, once the core service has reset the demo data, the restored ones.
 */
@Service
public class DemoService {
    private final PlmApi api;
    private final ReleasedGraphs released;
    private final LiveGraphs live;
    private final ObjectMapper json;

    public DemoService(PlmApi api, ReleasedGraphs released, LiveGraphs live, ObjectMapper json) {
        this.api = api;
        this.released = released;
        this.live = live;
        this.json = json;
    }

    /** The core's change rows, and each live graph diffed against its released file. */
    public DemoJson.Changes changes(Caller caller) {
        List<JsonNode> values = rows(caller);
        List<GraphDiff.Triple> added = new ArrayList<>();
        List<GraphDiff.Triple> removed = new ArrayList<>();
        Map<String, Long> triples = new LinkedHashMap<>();
        for (ReleasedGraphs.Released graph : released.all()) {
            Model current = live.graph(graph.graph());
            GraphDiff.Diff diff = GraphDiff.of(graph.model(), current);
            added.addAll(diff.added());
            removed.addAll(diff.removed());
            triples.put(graph.name(), current.size());
        }
        return new DemoJson.Changes(values, new DemoJson.Graph(added, removed, triples));
    }

    /** The rows of {@code GET /core/changes}. */
    private List<JsonNode> rows(Caller caller) {
        PlmApi.Reply reply = api.get("core/changes", caller);
        if (!reply.ok()) throw new IllegalStateException("the core service did not list the changes: HTTP " + reply.status());
        JsonNode body = read(reply.body());
        if (!body.isArray()) throw new IllegalStateException("the core service's change list is not a JSON array");
        List<JsonNode> rows = new ArrayList<>();
        body.forEach(rows::add);
        return rows;
    }

    private JsonNode read(String body) {
        try {
            return json.readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException("the core service answered with invalid JSON");
        }
    }
}
