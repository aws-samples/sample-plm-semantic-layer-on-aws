// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.rules;

import atelier.query.api.QueryController;
import atelier.query.policy.Policy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /query/rules}: what each shape checks and how, the same for every profile since it describes the shapes, not
 * rows. {@code GET /query/rules/failures}: the records each rule fails for the caller's profile, of one product
 * ({@code ?product=key}, with {@code &root=<item id>} and {@code &option=<code>} as the parts answer takes them) with a count
 * of the other products where each rule fails, or across every product.
 */
@RestController
public class RulesController {
    private final RuleCatalogue catalogue;
    private final RuleFailures failures;
    private final Policy policy;

    public RulesController(RuleCatalogue catalogue, RuleFailures failures, Policy policy) {
        this.catalogue = catalogue;
        this.failures = failures;
        this.policy = policy;
    }

    @GetMapping("/query/rules")
    public RuleCatalogue.Rules rules() {
        return catalogue.describe();
    }

    @GetMapping("/query/rules/failures")
    public RuleFailures.Response failures(@RequestParam(required = false) String product, @RequestParam(required = false) String root,
                                          @RequestParam(required = false) String option,
                                          @RequestHeader(name = QueryController.PROFILE_HEADER, required = false) String profile,
                                          @RequestHeader(name = QueryController.ACTOR_HEADER, required = false) String actor) {
        String key = QueryController.key(product);
        if (key == null && (root != null || option != null)) throw new IllegalArgumentException("a root or an option is of one product: name the product");
        return failures.failures(policy.caller(profile, actor), key, QueryController.root(root), QueryController.option(option));
    }
}
