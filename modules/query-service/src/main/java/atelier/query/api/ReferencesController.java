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
 * The external references of a product's visible parts ({@code ?product=key}, required): a site names another site's
 * part by URN, and only the layer sees both ends, so only it can say that a reference points at nothing or at a
 * revision that has moved on.
 */
@RestController
@RequestMapping("/query")
public class ReferencesController {
    private final QueryService service;
    private final Policy policy;

    public ReferencesController(QueryService service, Policy policy) {
        this.service = service;
        this.policy = policy;
    }

    /** The references of the product's visible parts or, with {@code &root=<item id>}, of that item's subtree. */
    @GetMapping("/references")
    public Json.References references(@RequestParam String product, @RequestParam(required = false) String root,
                                      @RequestHeader(name = QueryController.PROFILE_HEADER, required = false) String profile,
                                      @RequestHeader(name = QueryController.ACTOR_HEADER, required = false) String actor) {
        return service.references(policy.caller(profile, actor), product.strip(), QueryController.root(root));
    }
}
