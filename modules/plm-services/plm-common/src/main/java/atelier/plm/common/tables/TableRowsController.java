// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.tables;

import atelier.plm.common.policy.PartTagStore;
import atelier.plm.common.policy.Policy;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves the native rows behind an answer at GET /{plm}/tables/{table}?keys=k1,k2, where {plm}
 * is the service's {@code plm.code} property. Only the tables mapped by this service's JPA
 * entities are served; any other name is 404. Rows are looked up by key, read-only, in one
 * parameterised query; keys arrive URL-encoded and may contain spaces. The export-control policy
 * applies to part and feature tables (see {@link ReleasabilityFilter}), evaluated against the part
 * tags of the Atelier core database: rows the profile named in the {@code x-atelier-profile} header may not
 * see are absent, and the response echoes the clearance used, with {@code policy.error} when the
 * tag store is not configured and every filtered row was therefore withheld.
 */
@RestController
public class TableRowsController {

    static final int MAX_KEYS = 50;

    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;
    private final Policy policy;
    private final PartTagStore tags;

    public TableRowsController(EntityManager entityManager, JdbcTemplate jdbc, Policy policy, PartTagStore tags) {
        this.entityManager = entityManager;
        this.jdbc = jdbc;
        this.policy = policy;
        this.tags = tags;
    }

    @GetMapping("/${plm.code}/tables/{table}")
    public TableRows rows(@PathVariable String table, @RequestParam String keys,
                          @RequestHeader(name = Policy.HEADER, required = false) String profile) {
        NativeTable nativeTable = NativeTable.of(entityManager.getMetamodel(), table)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown table " + table));
        List<String> requested = Arrays.stream(keys.split(",")).filter(k -> !k.isEmpty()).distinct().toList();
        if (requested.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keys is required");
        }
        if (requested.size() > MAX_KEYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most " + MAX_KEYS + " keys per request");
        }
        return nativeTable.read(jdbc, requested, policy.clearance(profile), tags);
    }
}
