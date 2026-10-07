// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.api;

import atelier.query.QueryService;
import atelier.query.policy.Policy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stations and sections of one product, which no PLM holds: the parts between two stations ({@code /stations}, on a
 * side or both), and the sections with their owners, parts and the parts crossing each joint ({@code /sections}). A
 * station is named by its id (WS 1500); an unknown station or side, or a product without stations, is 400.
 */
@RestController
@RequestMapping("/query")
public class StationsController {
    private final QueryService service;
    private final Policy policy;

    public StationsController(QueryService service, Policy policy) {
        this.service = service;
        this.policy = policy;
    }

    @GetMapping("/stations")
    public StationsJson.Between stations(@RequestParam String product, @RequestParam String from, @RequestParam String to,
                                         @RequestParam(required = false) String side,
                                         @RequestHeader(name = QueryController.PROFILE_HEADER, required = false) String profile,
                                         @RequestHeader(name = QueryController.ACTOR_HEADER, required = false) String actor) {
        return service.stations(policy.caller(profile, actor), product.strip(), from.strip(), to.strip(), side);
    }

    @GetMapping("/sections")
    public StationsJson.Sections sections(@RequestParam String product,
                                          @RequestHeader(name = QueryController.PROFILE_HEADER, required = false) String profile,
                                          @RequestHeader(name = QueryController.ACTOR_HEADER, required = false) String actor) {
        return service.sections(policy.caller(profile, actor), product.strip());
    }
}
