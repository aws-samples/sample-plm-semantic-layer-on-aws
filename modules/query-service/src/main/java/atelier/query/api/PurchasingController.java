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
 * What the layer knows about purchased items, per product ({@code ?product=key}, required): the parts the sites buy
 * under different numbers that are one item ({@code /equivalents}), and the suppliers each site depends on with the
 * single-source parts and the lead-time conflicts ({@code /suppliers}). No site can answer either: each holds its own
 * numbers, units and supplier list.
 */
@RestController
@RequestMapping("/query")
public class PurchasingController {
    private final QueryService service;
    private final Policy policy;

    public PurchasingController(QueryService service, Policy policy) {
        this.service = service;
        this.policy = policy;
    }

    @GetMapping("/equivalents")
    public Json.Equivalents equivalents(@RequestParam String product,
                                        @RequestHeader(name = QueryController.PROFILE_HEADER, required = false) String profile,
                                        @RequestHeader(name = QueryController.ACTOR_HEADER, required = false) String actor) {
        return service.equivalents(policy.caller(profile, actor), product.strip());
    }

    @GetMapping("/suppliers")
    public Json.Suppliers suppliers(@RequestParam String product,
                                    @RequestHeader(name = QueryController.PROFILE_HEADER, required = false) String profile,
                                    @RequestHeader(name = QueryController.ACTOR_HEADER, required = false) String actor) {
        return service.suppliers(policy.caller(profile, actor), product.strip());
    }
}
