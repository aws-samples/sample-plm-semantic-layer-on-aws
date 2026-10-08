// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.api;

import atelier.query.InterfaceRef;
import atelier.query.QueryService;
import atelier.query.Warmup;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Query service API; API Gateway maps /api/query/* to /query/*. The viewer's export-control
 * profile is the {@code x-atelier-profile} header (absent or unknown: the {@code unknown} profile);
 * the actor asking on the viewer's behalf is the {@code x-atelier-actor} header (absent: {@code user}).
 */
@RestController
@RequestMapping("/query")
public class QueryController {
    public static final String PROFILE_HEADER = "x-atelier-profile";
    /** Who acts for the viewer ({@code user} when absent); echoed in every answer's policy. */
    public static final String ACTOR_HEADER = "x-atelier-actor";

    /** Item ids are placed into IRIs and SPARQL text (percent-encoded), so only plain key characters are accepted. */
    private static final Pattern ITEM_ID = Pattern.compile("[A-Za-z0-9._ -]{1,64}");

    /** Interface ids are placed into SPARQL text, so only plain key characters are accepted. */
    private static final Pattern INTERFACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final QueryService service;
    private final Policy policy;
    private final Warmup warmup;

    public QueryController(QueryService service, Policy policy, Warmup warmup) {
        this.service = service;
        this.policy = policy;
        this.warmup = warmup;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    /** The warm-up so far: its status and, per profile, {@code done} or {@code failed} with the time in ms. */
    @GetMapping("/health/warmup")
    public Warmup.Summary warmup() {
        return warmup.summary();
    }

    /**
     * The parts of one product ({@code ?product=key}), or of every product; with {@code &root=<item id>}, of that item's
     * subtree across the sites (the product's key as root is the whole product); with {@code &option=<code>}, of the
     * product under that option of one of its variant groups.
     */
    @GetMapping("/parts")
    public Json.PartsResponse parts(@RequestParam(required = false) String product, @RequestParam(required = false) String root,
                                    @RequestParam(required = false) String option,
                                    @RequestHeader(name = PROFILE_HEADER, required = false) String profile,
                                    @RequestHeader(name = ACTOR_HEADER, required = false) String actor) {
        return service.parts(policy.caller(profile, actor), key(product), root(root), option(option));
    }

    /**
     * The interfaces of one product ({@code ?product=key}), or of every product; with {@code &root=<item id>}, those with
     * a side in that item's subtree, the far sides as context.
     */
    @GetMapping("/interfaces")
    public Json.InterfacesResponse interfaces(@RequestParam(required = false) String product, @RequestParam(required = false) String root,
                                              @RequestHeader(name = PROFILE_HEADER, required = false) String profile,
                                              @RequestHeader(name = ACTOR_HEADER, required = false) String actor) {
        return service.interfaces(policy.caller(profile, actor), key(product), root(root));
    }

    /** The products the interfaces' parts belong to, each with the number of its parts the viewer may see. */
    @GetMapping("/products")
    public Json.ProductsResponse products(@RequestHeader(name = PROFILE_HEADER, required = false) String profile,
                                          @RequestHeader(name = ACTOR_HEADER, required = false) String actor) {
        return service.products(policy.caller(profile, actor));
    }

    /** The products as {@code /products} lists them, without the product rules' findings: what the product switch needs first. */
    @GetMapping("/products/list")
    public Json.ProductListResponse productList(@RequestHeader(name = PROFILE_HEADER, required = false) String profile,
                                                @RequestHeader(name = ACTOR_HEADER, required = false) String actor) {
        return service.productList(policy.caller(profile, actor));
    }

    /** An absent or blank root parameter means the whole product; an id outside the key alphabet is refused. */
    public static String root(String root) {
        if (root == null || root.isBlank()) return null;
        String id = root.strip();
        if (!ITEM_ID.matcher(id).matches()) throw new IllegalArgumentException("invalid item id");
        return id;
    }

    /** An absent or blank option parameter means the base product. */
    public static String option(String option) {
        return option == null || option.isBlank() ? null : option.strip();
    }

    /** An absent or blank product parameter means every product. */
    public static String key(String product) {
        return product == null || product.isBlank() ? null : product.strip();
    }

    /**
     * One interface: {@code {product}/{id}} with {@code ?product=key}; without it, the id must belong to
     * exactly one product (several: 400 naming them; none: 404).
     */
    @GetMapping("/interfaces/{id}")
    public ResponseEntity<?> interfaceById(@PathVariable String id, @RequestParam(required = false) String product,
                                           @RequestHeader(name = PROFILE_HEADER, required = false) String profile,
                                           @RequestHeader(name = ACTOR_HEADER, required = false) String actor) {
        return forInterface(new InterfaceRef(id, key(product)), policy.caller(profile, actor), service::interfaceById);
    }

    /** The evidence behind {@link #interfaceById}; the interface is named the same way. */
    @GetMapping("/interfaces/{id}/evidence")
    public ResponseEntity<?> evidence(@PathVariable String id, @RequestParam(required = false) String product,
                                      @RequestHeader(name = PROFILE_HEADER, required = false) String profile,
                                      @RequestHeader(name = ACTOR_HEADER, required = false) String actor) {
        return forInterface(new InterfaceRef(id, key(product)), policy.caller(profile, actor), service::evidence);
    }

    private ResponseEntity<?> forInterface(InterfaceRef ref, Caller caller, BiFunction<InterfaceRef, Caller, Optional<?>> lookup) {
        if (!INTERFACE_ID.matcher(ref.id()).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid interface id"));
        }
        return lookup.apply(ref, caller).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "no interface " + ref)));
    }
}
