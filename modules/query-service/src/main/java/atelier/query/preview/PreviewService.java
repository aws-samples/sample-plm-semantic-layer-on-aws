// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.preview;

import atelier.query.Atelier;
import atelier.query.QueryService;
import atelier.query.api.QueryController;
import atelier.query.federation.EndpointFailure;
import atelier.query.mcp.PlmApi;
import atelier.query.policy.Caller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * What the rules would say if a correction were released: every cell checked against its site's catalogue, then the
 * product's (or subtree's) federation for the caller, the cells written into a copy of the merged graph, and the rules
 * compared before and after ({@link QueryService#preview}, {@link PreviewDiff}). The only requests are the federation's
 * reads and one {@code GET /{plm}/catalogue} per site the cells name: no PLM, link store or change log is written.
 */
@Service
public class PreviewService {
    /** At most this many cells in one preview, as in one release batch. */
    public static final int MAX_CELLS = 500;

    private final QueryService service;
    private final CellMappings mappings;
    private final BiFunction<String, Caller, JsonNode> catalogues;

    @Autowired
    public PreviewService(QueryService service, CellMappings mappings, PlmApi plm) {
        this(service, mappings, (site, caller) -> catalogue(plm, site, caller));
    }

    /** @param catalogues the catalogue of a site (lower-case code) as the caller reads it */
    public PreviewService(QueryService service, CellMappings mappings, BiFunction<String, Caller, JsonNode> catalogues) {
        this.service = service;
        this.mappings = mappings;
        this.catalogues = catalogues;
    }

    public PreviewJson.Answer preview(Caller caller, PreviewJson.Request request) {
        List<PreviewJson.Cell> cells = request.cells();
        if (cells == null || cells.isEmpty()) throw new IllegalArgumentException("cells needs at least one cell");
        if (cells.size() > MAX_CELLS) throw new IllegalArgumentException("cells takes at most " + MAX_CELLS + " cells, not " + cells.size());
        String product = request.product() == null || request.product().isBlank() ? null : request.product().strip();
        String root = QueryController.root(request.root());
        List<Rewriter.Checked> checked = check(caller, cells);
        QueryService.PreviewRun<Rewriter.Outcome> run = service.preview(caller, product, root,
                model -> Rewriter.apply(model, mappings, caller.profile().name(), checked));
        PreviewDiff.Lists diff = PreviewDiff.of(run.before(), run.after(), run.edits().touched());
        return new PreviewJson.Answer(product, root, run.edits().rewrites(), diff.interfaces(), diff.parts(), diff.products(), diff.tally(),
                run.provenance(), run.timings(), run.policy());
    }

    /** Each cell complete, of a site, with a value its site's catalogue accepts, in a column its mapping reads; each catalogue read once. */
    private List<Rewriter.Checked> check(Caller caller, List<PreviewJson.Cell> cells) {
        Map<String, JsonNode> read = new LinkedHashMap<>();
        List<Rewriter.Checked> checked = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            String where = "cells[" + i + "]";
            PreviewJson.Cell cell = cells.get(i);
            if (cell == null || blank(cell.plm()) || blank(cell.table()) || blank(cell.key()) || blank(cell.column()) || cell.value() == null) {
                throw new IllegalArgumentException(where + " needs plm, table, key, column and value");
            }
            String plm = cell.plm().strip().toLowerCase(Locale.ROOT);
            if (!Atelier.PLMS.contains(plm)) {
                throw new IllegalArgumentException(where + ": unknown plm " + cell.plm() + "; one of " + Atelier.PLMS);
            }
            PreviewJson.Cell site = new PreviewJson.Cell(plm, cell.table(), cell.key(), cell.column(), cell.value());
            JsonNode catalogue = read.computeIfAbsent(plm, p -> catalogues.apply(p, caller));
            Rewriter.Checked one = new Rewriter.Checked(where, site, Vocabulary.value(catalogue, site, where));
            Rewriter.mapped(mappings, one);
            checked.add(one);
        }
        return checked;
    }

    private static JsonNode catalogue(PlmApi plm, String site, Caller caller) {
        try {
            PlmApi.Reply reply = plm.catalogue(site, caller);
            if (!reply.ok()) throw new IllegalStateException("GET /" + site + "/catalogue answered " + reply.status());
            return new ObjectMapper().readTree(reply.body());
        } catch (IOException | RuntimeException e) {
            throw new EndpointFailure("plm-" + site, e);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
