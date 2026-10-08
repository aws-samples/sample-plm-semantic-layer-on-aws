// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Answer of {@code POST /{plm}/demo/update}: the PLM code, one row per correction in request order,
 * when the corrections ran (UTC), and EventBridge's id of the one {@code part.value.corrected} event
 * announcing them; {@code eventId} is null and {@code eventSkipped} says why when no event was put
 * (the corrections stand either way).
 */
public record DemoUpdateResponse(String plm, List<Row> rows, OffsetDateTime at, String eventId,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) String eventSkipped) {

    /**
     * One corrected cell: the table and column as the catalogue names them, the key as requested, the
     * value before the correction and the value the database holds after it, as the driver returns them.
     */
    public record Row(String table, String key, String column, Object before, Object after) {
    }
}
