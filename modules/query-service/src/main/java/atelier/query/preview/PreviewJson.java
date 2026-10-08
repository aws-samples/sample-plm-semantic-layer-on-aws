// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.preview;

import atelier.query.api.Json;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** The request and answer of {@code POST /query/preview} and of the {@code preview_correction} tool. */
public final class PreviewJson {
    private PreviewJson() {}

    /** One cell as the release form builds it: the site, its native table, the row key, the column and the value (JSON null clears it). */
    public record Cell(String plm, String table, String key, String column, JsonNode value) {}

    /** The cells of one product (or of the subtree of {@code root}) to preview. */
    public record Request(String product, String root, List<Cell> cells) {}

    /** One triple of the merged graph a cell rewrites: its value before and after, as lexical forms or IRIs, null when absent. */
    public record Triple(String subject, String predicate, String before, String after) {}

    /** A cell as previewed: the cell and the triples it rewrote. */
    public record Rewrite(String plm, String table, String key, String column, JsonNode value, List<Triple> triples) {}

    /** One rule result: the rule, its message, the features an interface result names, the record a part finding names. */
    public record Result(String rule, String message,
                         @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> features,
                         @JsonInclude(JsonInclude.Include.NON_NULL) Json.RowRef value) {}

    /** The results of one subject that the cells fix, leave failing or make fail. */
    public record Diff(List<Result> fixed, List<Result> stillFailing, List<Result> newlyFailing) {
        boolean empty() {
            return fixed.isEmpty() && stillFailing.isEmpty() && newlyFailing.isEmpty();
        }
    }

    /** An interface whose results change, or that names a rewritten record and still fails: its status before and after. */
    public record InterfaceDiff(String id, String product, String label, String before, String after, List<Result> fixed,
                                List<Result> stillFailing, List<Result> newlyFailing) {}

    /** A part whose findings change, or that holds or is named by a rewritten record and still carries one. */
    public record PartDiff(String id, String plm, String name, List<Result> fixed, List<Result> stillFailing, List<Result> newlyFailing) {}

    /** The product, when its own findings change or a rewritten mass leaves one standing. */
    public record ProductDiff(String key, List<Result> fixed, List<Result> stillFailing, List<Result> newlyFailing) {}

    public record Tally(int fixed, int stillFailing, int newlyFailing) {}

    /**
     * The answer: the cells with the triples they rewrote, the interfaces, parts and product the diff concerns, the
     * tally, and the provenance, timings and policy of the federation the rules ran on.
     */
    public record Answer(String product, @JsonInclude(JsonInclude.Include.NON_NULL) String root, List<Rewrite> cells,
                         List<InterfaceDiff> interfaces, List<PartDiff> parts, List<ProductDiff> products, Tally tally,
                         Json.Provenance provenance, Json.Timings timings, Json.Policy policy) {}
}
