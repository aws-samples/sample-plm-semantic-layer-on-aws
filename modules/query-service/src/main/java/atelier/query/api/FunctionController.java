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
 * Paths through one product: the shortest connectivity paths of parts joined by interfaces between two parts
 * ({@code /paths}), and the walk along its functional edges from a part ({@code /flow}), downstream or, with
 * {@code direction=up}, upstream. Parts are named by native id or IRI; one that names no part of the product is 404.
 */
@RestController
@RequestMapping("/query")
public class FunctionController {
    private final QueryService service;
    private final Policy policy;

    public FunctionController(QueryService service, Policy policy) {
        this.service = service;
        this.policy = policy;
    }

    @GetMapping("/paths")
    public FunctionJson.Paths paths(@RequestParam String product, @RequestParam String from, @RequestParam String to,
                                    @RequestHeader(name = QueryController.PROFILE_HEADER, required = false) String profile,
                                    @RequestHeader(name = QueryController.ACTOR_HEADER, required = false) String actor) {
        return service.paths(policy.caller(profile, actor), product.strip(), item(from), item(to));
    }

    @GetMapping("/flow")
    public FunctionJson.Flow flow(@RequestParam String product, @RequestParam String from, @RequestParam(required = false) String flow,
                                  @RequestParam(required = false) String direction,
                                  @RequestHeader(name = QueryController.PROFILE_HEADER, required = false) String profile,
                                  @RequestHeader(name = QueryController.ACTOR_HEADER, required = false) String actor) {
        return service.flow(policy.caller(profile, actor), product.strip(), item(from), blank(flow), up(direction));
    }

    /** A part id, checked as a subtree root is: plain key characters only. */
    static String item(String id) {
        String checked = QueryController.root(id);
        if (checked == null) throw new IllegalArgumentException("name a part");
        return checked;
    }

    static boolean up(String direction) {
        if (direction == null || direction.isBlank() || direction.strip().equals("down")) return false;
        if (direction.strip().equals("up")) return true;
        throw new IllegalArgumentException("direction is down or up");
    }

    static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
