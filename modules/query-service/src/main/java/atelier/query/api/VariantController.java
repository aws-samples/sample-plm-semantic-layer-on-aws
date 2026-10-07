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
 * The variant groups of one product ({@code /variants}: each group with its options, the default first, which the policy
 * does not filter) and the variant diff ({@code /variant-diff}: an option of a group against the group's default option,
 * port by port, with the interface rules run on each configuration). A group or option the product does not have is 404;
 * the default option itself, a key outside the key alphabet, is 400.
 */
@RestController
@RequestMapping("/query")
public class VariantController {
    private final QueryService service;
    private final Policy policy;

    public VariantController(QueryService service, Policy policy) {
        this.service = service;
        this.policy = policy;
    }

    @GetMapping("/variants")
    public VariantJson.Groups variants(@RequestParam String product) {
        return service.variants(product.strip());
    }

    @GetMapping("/variant-diff")
    public VariantJson.Diff variantDiff(@RequestParam String product, @RequestParam String group, @RequestParam String option,
                                        @RequestHeader(name = QueryController.PROFILE_HEADER, required = false) String profile,
                                        @RequestHeader(name = QueryController.ACTOR_HEADER, required = false) String actor) {
        return service.variantDiff(policy.caller(profile, actor), product.strip(), group.strip(), option.strip());
    }
}
