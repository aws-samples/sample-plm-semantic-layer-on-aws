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
 * A term in English, German, French or Spanish ({@code ?q=}): the concepts of the products' glossary it names and the
 * items of every site whose names hold them. Each site names its parts in its own language only; the layer's labels
 * graph holds the glossary and the English names that let one word find them all.
 */
@RestController
@RequestMapping("/query")
public class TermsController {
    private final QueryService service;
    private final Policy policy;

    public TermsController(QueryService service, Policy policy) {
        this.service = service;
        this.policy = policy;
    }

    @GetMapping("/terms")
    public Json.Terms terms(@RequestParam String q,
                            @RequestHeader(name = QueryController.PROFILE_HEADER, required = false) String profile,
                            @RequestHeader(name = QueryController.ACTOR_HEADER, required = false) String actor) {
        return service.terms(policy.caller(profile, actor), q);
    }
}
