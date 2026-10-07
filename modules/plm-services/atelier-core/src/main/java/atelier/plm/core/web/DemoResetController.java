// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.web;

import atelier.plm.common.demo.DemoAccess;
import atelier.plm.common.demo.DemoRejectedException;
import atelier.plm.common.policy.Policy;
import atelier.plm.core.demo.PlmApi;
import atelier.plm.core.domain.DemoChangeStore;
import atelier.plm.core.dto.DemoChangeDto;
import atelier.plm.core.dto.GraphJson;
import atelier.plm.core.graph.GraphStore;
import atelier.plm.core.graph.ReleasedGraphs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The demo reset is deterministic and Atelier's: {@code POST /core/demo/reset}, for the export-control
 * officer alone (403 otherwise, see {@link DemoAccess}). It undoes every {@code demo_change} row
 * newest first by asking the owning PLM to set the value back ({@code POST /{plm}/demo/update},
 * body {@code { table, key, column, value: before, purpose: "reset: <id>" }}, through the API
 * Gateway as the officer, see {@link PlmApi}; the PLM announces each undo as a
 * {@code part.value.corrected} event the loader ignores by its purpose), then PUTs the bundled
 * {@code links.ttl}, {@code fileindex.ttl} and {@code labels.ttl} over their named graphs (every link,
 * confirmed equivalence and CAD file the demo added is gone), then clears the log. The answer is
 * {@code { undone, graphs: { links, fileindex, labels } }} with the counts read back from the store. A PLM that refuses an undo stops the
 * reset with 502 and the log stays, so the next reset replays it.
 */
@RestController
public class DemoResetController {

    private static final Logger log = LoggerFactory.getLogger(DemoResetController.class);

    private final DemoChangeStore changes;
    private final PlmApi plm;
    private final GraphStore store;
    private final ReleasedGraphs released;
    private final ObjectMapper json;

    public DemoResetController(DemoChangeStore changes, PlmApi plm, GraphStore store, ReleasedGraphs released, ObjectMapper json) {
        this.changes = changes;
        this.plm = plm;
        this.store = store;
        this.released = released;
        this.json = json;
    }

    @PostMapping("/core/demo/reset")
    public GraphJson.DemoReset reset(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        DemoAccess.officer().require(profile);
        List<DemoChangeDto> rows = changes.newestFirst();
        for (DemoChangeDto row : rows) {
            undo(row);
        }
        Map<String, Long> graphs = new LinkedHashMap<>();
        for (ReleasedGraphs.Released graph : released.all()) {
            store.put(graph.graph(), graph.turtle());
            graphs.put(graph.name(), store.count(graph.graph()));
        }
        int cleared = changes.clear();
        log.info("demo reset undone={} cleared={} graphs={}", rows.size(), cleared, graphs);
        return new GraphJson.DemoReset(rows.size(), graphs);
    }

    private void undo(DemoChangeDto row) {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("table", row.table());
        update.put("key", row.key());
        update.put("column", row.column());
        update.put("value", row.before());
        update.put("purpose", "reset: " + row.id());
        PlmApi.Reply reply = plm.update(row.plm(), update);
        if (!reply.ok()) {
            String cell = row.table() + "/" + row.key() + "/" + row.column();
            log.warn("demo reset: the {} PLM did not undo change {} ({}): HTTP {}{}", row.plm(), row.id(), cell, reply.status(), reason(reply.body()));
            throw new DemoRejectedException(HttpStatus.BAD_GATEWAY, "the " + row.plm().toUpperCase(Locale.ROOT) + " PLM did not undo change "
                    + row.id() + " (" + cell + "): HTTP " + reply.status() + reason(reply.body()), "plm-update");
        }
    }

    /** The PLM's {@code reason} category when its answer carries one, as {@code " <reason>"}; empty otherwise. */
    private String reason(String body) {
        try {
            JsonNode reason = json.readTree(body).path("reason");
            return reason.isTextual() ? " " + reason.asText() : "";
        } catch (IOException e) {
            return "";
        }
    }
}
