// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.web;

import atelier.plm.common.demo.DemoAccess;
import atelier.plm.common.demo.DemoRejectedException;
import atelier.plm.common.policy.Policy;
import atelier.plm.core.domain.DemoChangeStore;
import atelier.plm.core.dto.DemoChangeDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The change log of the demo value corrections at /core/changes (see {@link DemoAccess}): POST
 * appends the rows of one PLM request in one transaction (body {@code [ { plm, table, key, column,
 * before, after, actor, purpose } ]}, at least one, each checked by {@link DemoChangeDto#validate()}
 * before any is stored) and returns them as stored, GET lists every row
 * oldest first, both for every profile but
 * {@code unknown} (403 otherwise); DELETE removes them all and returns {@code { deleted }}, for the
 * export-control officer alone. The rows come from the links loader, which subscribes to the PLMs'
 * {@code part.value.corrected} events; the demo reset ({@link DemoResetController}) undoes and clears them.
 */
@RestController
@RequestMapping("/core/changes")
public class DemoChangeController {

    private final DemoChangeStore changes;
    private final DemoAccess known;

    public DemoChangeController(DemoChangeStore changes, Policy policy) {
        this.changes = changes;
        this.known = DemoAccess.anyKnown(policy);
    }

    @PostMapping
    public List<DemoChangeDto> append(@RequestBody List<DemoChangeDto> rows,
                                      @RequestHeader(name = Policy.HEADER, required = false) String profile) {
        known.require(profile);
        if (rows == null || rows.isEmpty()) {
            throw new DemoRejectedException(HttpStatus.BAD_REQUEST, "at least one change is required", "missing-field");
        }
        for (DemoChangeDto row : rows) {
            if (row == null) {
                throw new DemoRejectedException(HttpStatus.BAD_REQUEST, "a change is null", "missing-field");
            }
            row.validate();
        }
        return changes.append(rows);
    }

    @GetMapping
    public List<DemoChangeDto> all(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        known.require(profile);
        return changes.all();
    }

    @DeleteMapping
    public Map<String, Integer> clear(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        DemoAccess.officer().require(profile);
        return Map.of("deleted", changes.clear());
    }
}
