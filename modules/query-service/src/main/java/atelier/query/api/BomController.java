// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.api;

import atelier.query.QueryService;
import atelier.query.policy.Policy;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The bill of materials the layer reconstructs, per product ({@code ?product=key}, required): no PLM holds a whole
 * product, so the tree under the product root and the roll-ups exist only here. The where-used over the bill of
 * materials ({@code /used-in}) is distinct from the where-used over interfaces ({@code /where-used}). The placements of the
 * parts ({@code /placements}) follow the same trees.
 */
@RestController
@RequestMapping("/query")
public class BomController {
    private final QueryService service;
    private final Policy policy;

    public BomController(QueryService service, Policy policy) {
        this.service = service;
        this.policy = policy;
    }

    /** The product's bill of materials or, with {@code &root=<item id>}, the tree under that item across the sites. */
    @GetMapping("/bom")
    public Json.Bom bom(@RequestParam String product, @RequestParam(required = false) String root,
                        @RequestHeader(name = QueryController.PROFILE_HEADER, required = false) String profile,
                        @RequestHeader(name = QueryController.ACTOR_HEADER, required = false) String actor) {
        return service.bom(policy.caller(profile, actor), product.strip(), QueryController.root(root));
    }

    /**
     * The world placements of the product's visible geometric parts, every occurrence composed down the bill of materials,
     * or with {@code &root=<item id>} those of the tree under that item, or with {@code &option=<code>} those of the product
     * under that option of one of its variant groups; the viewer draws each part once per occurrence.
     */
    @GetMapping("/placements")
    public Json.Placements placements(@RequestParam String product, @RequestParam(required = false) String root,
                                      @RequestParam(required = false) String option,
                                      @RequestHeader(name = QueryController.PROFILE_HEADER, required = false) String profile,
                                      @RequestHeader(name = QueryController.ACTOR_HEADER, required = false) String actor) {
        return service.placements(policy.caller(profile, actor), product.strip(), QueryController.root(root), QueryController.option(option));
    }

    @GetMapping("/parts/{id}/used-in")
    public ResponseEntity<?> usedIn(@PathVariable String id, @RequestParam String product,
                                    @RequestHeader(name = QueryController.PROFILE_HEADER, required = false) String profile,
                                    @RequestHeader(name = QueryController.ACTOR_HEADER, required = false) String actor) {
        return service.bomWhereUsed(id, policy.caller(profile, actor), product.strip()).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "no item " + id + " in " + product)));
    }
}
