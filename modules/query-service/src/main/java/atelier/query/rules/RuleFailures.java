// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.rules;

import atelier.query.Atelier;
import atelier.query.QueryService;
import atelier.query.api.Json;
import atelier.query.federation.Federator;
import atelier.query.federation.UnknownProduct;
import atelier.query.policy.Caller;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The answer of {@code GET /query/rules/failures}: every record that fails a rule for the caller's profile, of one product
 * or across every product. The every-product answer comes from three answers the service already gives over every product:
 * the interfaces answer (an interface that a rule fails), the parts answer (a part that carries a rule's finding, the external-reference rules included, under each
 * product the part belongs to) and the products answer (a product rule's finding on a product none of whose parts the
 * rule names). Three federations in all, where the screens' per-product answers would take two per product.
 */
@Service
public class RuleFailures {
    private static final Comparator<Failure> ORDER = Comparator.comparing(Failure::rule).thenComparing(Failure::product)
            .thenComparing(Failure::kind).thenComparing(Failure::id, Atelier.BY_ID);

    private final QueryService service;
    private final Set<String> rules;

    public RuleFailures(QueryService service, RuleCatalogue catalogue) {
        this.service = service;
        this.rules = Set.copyOf(catalogue.names());
    }

    public Response failures(Caller caller) {
        return every(caller).response();
    }

    /** The every-product answer and the keys of the products it read. */
    private record Every(Response response, Set<String> products) {}

    private Every every(Caller caller) {
        long start = System.nanoTime();
        Json.InterfacesResponse interfaces = service.interfaces(caller, null);
        QueryService.PartsByProduct parts = service.partsByProduct(caller);
        Json.ProductsResponse products = service.products(caller);
        TreeSet<Failure> out = new TreeSet<>(ORDER);
        for (Json.Interface i : interfaces.interfaces()) {
            i.violations().forEach(v -> out.add(new Failure(v.rule(), i.product(), "interface", i.id(), null)));
        }
        parts.products().forEach((part, keys) -> keys.forEach(product -> part.findings().stream().filter(f -> rules.contains(f.rule()))
                .forEach(f -> out.add(new Failure(f.rule(), product, "part", part.id(), part.plm())))));
        addProductRecords(out, products);
        return new Every(response(List.copyOf(out), null, start, products.policy(), List.of(run(interfaces), run(parts.answer()), run(products))),
                products.products().stream().map(Json.Product::key).collect(Collectors.toSet()));
    }

    /**
     * The records failing for the caller in one product, scoped as the screens' answers scope it: under {@code root}, the
     * interfaces with a side in the subtree and the subtree's parts, and no product record; under {@code option}, the parts
     * of that configuration, the product records those of the base product. With them, per rule, the number of other products where it fails for the same caller, a count
     * of the products the profile's own every-product answer names, never their records.
     */
    public Response failures(Caller caller, String product, String root, String option) {
        if (product == null) return failures(caller);
        long start = System.nanoTime();
        Every every = every(caller);
        if (!every.products().contains(product)) throw new UnknownProduct(product);
        Response all = every.response();
        Map<String, Set<String>> elsewhere = new TreeMap<>();
        all.failures().stream().filter(f -> !f.product().equals(product))
                .forEach(f -> elsewhere.computeIfAbsent(f.rule(), r -> new TreeSet<>()).add(f.product()));
        Map<String, Integer> otherProducts = new TreeMap<>();
        elsewhere.forEach((rule, keys) -> otherProducts.put(rule, keys.size()));
        boolean subtree = root != null && !root.equals(product);
        if (!subtree && option == null) {
            List<Failure> own = all.failures().stream().filter(f -> f.product().equals(product)).toList();
            return response(own, otherProducts, start, all.policy(), List.of(new Run(all.provenance(), all.sparql(), all.timings())));
        }
        Json.InterfacesResponse interfaces = service.interfaces(caller, product, root);
        Json.PartsResponse parts = service.parts(caller, product, root, option);
        TreeSet<Failure> out = new TreeSet<>(ORDER);
        for (Json.Interface i : interfaces.interfaces()) {
            i.violations().forEach(v -> out.add(new Failure(v.rule(), product, "interface", i.id(), null)));
        }
        parts.parts().stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast).filter(p -> !Boolean.TRUE.equals(p.context()))
                .forEach(p -> p.findings().stream().filter(f -> rules.contains(f.rule()))
                        .forEach(f -> out.add(new Failure(f.rule(), product, "part", p.id(), p.plm()))));
        if (!subtree) all.failures().stream().filter(f -> f.product().equals(product) && f.kind().equals("product")).forEach(out::add);
        Run whole = new Run(all.provenance(), all.sparql(), all.timings());
        return response(List.copyOf(out), otherProducts, start, parts.policy(), List.of(whole, run(interfaces), run(parts)));
    }

    /** A product rule's finding on a product, when the rule names none of the product's parts. */
    private void addProductRecords(TreeSet<Failure> out, Json.ProductsResponse products) {
        Set<List<String>> onParts = new HashSet<>();
        out.forEach(f -> onParts.add(List.of(f.rule(), f.product())));
        for (Json.Product p : products.products()) {
            p.findings().stream().filter(f -> rules.contains(f.rule()) && !onParts.contains(List.of(f.rule(), p.key())))
                    .forEach(f -> out.add(new Failure(f.rule(), p.key(), "product", p.key(), null)));
        }
    }

    /** What one answer contributes to the envelope: its calls, its request texts and its times. */
    private record Run(Json.Provenance provenance, String sparql, Json.Timings timings) {}

    private static Run run(Json.InterfacesResponse r) {
        return new Run(r.provenance(), r.sparql(), r.timings());
    }

    private static Run run(Json.PartsResponse r) {
        return new Run(r.provenance(), r.sparql(), r.timings());
    }

    private static Run run(Json.ProductsResponse r) {
        return new Run(r.provenance(), r.sparql(), r.timings());
    }

    private static Response response(List<Failure> failures, Map<String, Integer> otherProducts, long start, Json.Policy policy, List<Run> runs) {
        List<Federator.Call> calls = runs.stream().flatMap(r -> r.provenance().calls().stream()).toList();
        String sparql = String.join("\n\n", runs.stream().map(Run::sparql).toList());
        long federationMs = runs.stream().mapToLong(r -> r.timings().federationMs()).sum();
        long validationMs = runs.stream().mapToLong(r -> r.timings().validationMs()).sum();
        return new Response(failures, otherProducts, new Json.Provenance(calls), sparql,
                new Json.Timings(msSince(start), federationMs, validationMs), policy);
    }

    private static long msSince(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    /** One failing record: {@code kind} interface, part or product; {@code id} its native key; {@code plm} a part's site, else null. */
    public record Failure(String rule, String product, String kind, String id, String plm) {}

    /**
     * {@code otherProducts}: per rule, the number of products other than the one asked for where it fails for the caller;
     * absent from the every-product answer.
     */
    public record Response(List<Failure> failures, @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Integer> otherProducts,
                           Json.Provenance provenance, String sparql, Json.Timings timings, Json.Policy policy) {}
}
