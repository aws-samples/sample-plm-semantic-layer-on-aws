// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.api;

import atelier.query.QueryService;
import atelier.query.policy.Policy;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answers about one part or feature, derived from the viewer's federation: where a part is used,
 * what a change touches, and a part's export-control status. A part or feature is named by its
 * native id (URL-encoded; it may contain a space) or its IRI; one no interface names is 404.
 */
@RestController
@RequestMapping("/query")
public class PartsController {
    private final QueryService service;
    private final Policy policy;

    public PartsController(QueryService service, Policy policy) {
        this.service = service;
        this.policy = policy;
    }

    @GetMapping("/parts/{id}/where-used")
    public ResponseEntity<?> whereUsed(@PathVariable String id,
                                       @RequestHeader(name = QueryController.PROFILE_HEADER, required = false) String profile,
                                       @RequestHeader(name = QueryController.ACTOR_HEADER, required = false) String actor) {
        return found(service.whereUsed(id, policy.caller(profile, actor)), "no part " + id);
    }

    /** Impact of a change to {@code part} or to {@code feature}: exactly one of the two. */
    @GetMapping("/impact")
    public ResponseEntity<?> impact(@RequestParam(required = false) String part, @RequestParam(required = false) String feature,
                                    @RequestHeader(name = QueryController.PROFILE_HEADER, required = false) String profile,
                                    @RequestHeader(name = QueryController.ACTOR_HEADER, required = false) String actor) {
        if ((part == null) == (feature == null)) {
            return ResponseEntity.badRequest().body(Map.of("error", "name either part or feature"));
        }
        return found(service.impact(part, feature, policy.caller(profile, actor)),
                part != null ? "no part " + part : "no feature " + feature);
    }

    @GetMapping("/parts/{id}/export-status")
    public ResponseEntity<?> exportStatus(@PathVariable String id,
                                          @RequestHeader(name = QueryController.PROFILE_HEADER, required = false) String profile,
                                          @RequestHeader(name = QueryController.ACTOR_HEADER, required = false) String actor) {
        return found(service.exportStatus(id, policy.caller(profile, actor)), "no part " + id);
    }

    private static ResponseEntity<?> found(Optional<?> answer, String missing) {
        return answer.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", missing)));
    }
}
