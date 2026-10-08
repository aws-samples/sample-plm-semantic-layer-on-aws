// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.demo;

import atelier.query.api.QueryController;
import atelier.query.policy.Caller;
import atelier.query.policy.DemoAccess;
import atelier.query.policy.Policy;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /query/demo/changes}: the change list, for every profile but unknown (403 naming the
 * allowed profiles otherwise). It is the demo's only route here, and it reads. A value correction
 * ({@code POST /{plm}/demo/update}), a mating link ({@code POST /core/links}), a CAD publication
 * ({@code POST /{plm}/demo/events/cad}) and the reset ({@code POST /core/demo/reset}) are the
 * owning services' routes, which the browser calls directly through the API gateway.
 */
@RestController
@RequestMapping("/query/demo")
public class DemoController {
    private final DemoService demo;
    private final Policy policy;
    private final DemoAccess known;

    public DemoController(DemoService demo, Policy policy) {
        this.demo = demo;
        this.policy = policy;
        this.known = DemoAccess.anyKnown(policy);
    }

    @GetMapping("/changes")
    public ResponseEntity<?> changes(@RequestHeader(name = QueryController.PROFILE_HEADER, required = false) String profile,
                                     @RequestHeader(name = QueryController.ACTOR_HEADER, required = false) String actor) {
        Caller caller = policy.caller(profile, actor);
        known.require(caller);
        return ResponseEntity.ok(demo.changes(caller));
    }

    /** The caller's profile may not read the change list: 403 naming the profiles that may. */
    @ExceptionHandler(DemoAccess.Refused.class)
    public ResponseEntity<Map<String, String>> refused(DemoAccess.Refused ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    /** A core service or link store failure: 502 with the reason; the detail is in the log. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> unavailable(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
    }
}
