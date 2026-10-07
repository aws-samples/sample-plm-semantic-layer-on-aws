// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.preview;

import atelier.query.Atelier;
import atelier.query.QueryService;
import atelier.query.api.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.apache.jena.graph.NodeFactory;

/**
 * The rules before and after a preview, compared per interface, per part and for the product. A result is the same
 * before and after when its rule and what it is about agree (an interface result: its focus feature, the features it
 * names and the quantity or axis; a part finding: the record it names; a product finding: its rule), whatever its message
 * says of the values. Every subject with a fixed or a newly failing result is listed; one with results failing before
 * and after is listed only when the cells concern it: an interface naming a part that holds a rewritten record, a part
 * that holds one or whose finding names one, the product when a part's mass was rewritten.
 */
final class PreviewDiff {
    private PreviewDiff() {}

    record Lists(List<PreviewJson.InterfaceDiff> interfaces, List<PreviewJson.PartDiff> parts, List<PreviewJson.ProductDiff> products,
                 PreviewJson.Tally tally) {}

    static Lists of(QueryService.Rules before, QueryService.Rules after, Rewriter.Touched touched) {
        Set<Json.RowRef> named = new LinkedHashSet<>();
        touched.records().forEach(iri -> named.add(Json.RowRef.of(NodeFactory.createURI(iri))));

        List<PreviewJson.InterfaceDiff> interfaces = new ArrayList<>();
        Map<String, Json.Interface> was = byKey(before.interfaces(), i -> i.product() + "/" + i.id());
        Map<String, Json.Interface> is = byKey(after.interfaces(), i -> i.product() + "/" + i.id());
        for (String key : union(was.keySet(), is.keySet())) {
            Json.Interface b = was.get(key);
            Json.Interface a = is.get(key);
            Json.Interface shown = a == null ? b : a;
            boolean concerned = shown.parts().stream().anyMatch(p -> p instanceof Json.Part q && touched.parts().contains(Atelier.partIri(q.plm(), q.id())));
            PreviewJson.Diff diff = diff(violations(b), violations(a), concerned);
            if (!diff.empty()) {
                interfaces.add(new PreviewJson.InterfaceDiff(shown.id(), shown.product(), shown.label(), b == null ? null : b.status(),
                        a == null ? null : a.status(), diff.fixed(), diff.stillFailing(), diff.newlyFailing()));
            }
        }

        List<PreviewJson.PartDiff> parts = new ArrayList<>();
        Map<String, Json.Part> partsWas = byKey(before.parts(), p -> p.plm() + "/" + p.id());
        Map<String, Json.Part> partsIs = byKey(after.parts(), p -> p.plm() + "/" + p.id());
        for (String key : union(partsWas.keySet(), partsIs.keySet())) {
            Json.Part b = partsWas.get(key);
            Json.Part a = partsIs.get(key);
            Json.Part shown = a == null ? b : a;
            boolean concerned = touched.parts().contains(Atelier.partIri(shown.plm(), shown.id()))
                    || java.util.stream.Stream.concat(findingsOf(b).stream(), findingsOf(a).stream()).anyMatch(f -> named.contains(f.value()));
            PreviewJson.Diff diff = diff(findings(b), findings(a), concerned);
            if (!diff.empty()) {
                parts.add(new PreviewJson.PartDiff(shown.id(), shown.plm(), shown.name(), diff.fixed(), diff.stillFailing(), diff.newlyFailing()));
            }
        }

        List<PreviewJson.ProductDiff> products = new ArrayList<>();
        for (String key : union(before.products().keySet(), after.products().keySet())) {
            PreviewJson.Diff diff = diff(productFindings(before.products().get(key)), productFindings(after.products().get(key)), touched.masses());
            if (!diff.empty()) products.add(new PreviewJson.ProductDiff(key, diff.fixed(), diff.stillFailing(), diff.newlyFailing()));
        }

        int fixed = 0, still = 0, newly = 0;
        for (PreviewJson.InterfaceDiff d : interfaces) { fixed += d.fixed().size(); still += d.stillFailing().size(); newly += d.newlyFailing().size(); }
        for (PreviewJson.PartDiff d : parts) { fixed += d.fixed().size(); still += d.stillFailing().size(); newly += d.newlyFailing().size(); }
        for (PreviewJson.ProductDiff d : products) { fixed += d.fixed().size(); still += d.stillFailing().size(); newly += d.newlyFailing().size(); }
        return new Lists(interfaces, parts, products, new PreviewJson.Tally(fixed, still, newly));
    }

    /** Results by identity, before and after: those only before are fixed, only after newly failing, in both still failing. */
    private static PreviewJson.Diff diff(Map<String, PreviewJson.Result> before, Map<String, PreviewJson.Result> after, boolean concerned) {
        List<PreviewJson.Result> fixed = new ArrayList<>();
        List<PreviewJson.Result> still = new ArrayList<>();
        List<PreviewJson.Result> newly = new ArrayList<>();
        before.forEach((key, result) -> (after.containsKey(key) ? still : fixed).add(after.getOrDefault(key, result)));
        after.forEach((key, result) -> { if (!before.containsKey(key)) newly.add(result); });
        return new PreviewJson.Diff(fixed, concerned || !fixed.isEmpty() || !newly.isEmpty() ? still : List.of(), newly);
    }

    private static Map<String, PreviewJson.Result> violations(Json.Interface iface) {
        Map<String, PreviewJson.Result> out = new LinkedHashMap<>();
        if (iface == null) return out;
        for (Json.Violation v : iface.violations()) {
            String key = String.join("|", v.rule(), v.focusNode(), String.join(",", v.features()),
                    String.valueOf(v.detail().get("quantity")), String.valueOf(v.detail().get("axis")));
            out.putIfAbsent(key, new PreviewJson.Result(v.rule(), v.message(), v.features(), null));
        }
        return out;
    }

    private static List<Json.Finding> findingsOf(Json.Part part) {
        return part == null || part.findings() == null ? List.of() : part.findings();
    }

    private static Map<String, PreviewJson.Result> findings(Json.Part part) {
        Map<String, PreviewJson.Result> out = new LinkedHashMap<>();
        for (Json.Finding f : findingsOf(part)) {
            out.putIfAbsent(f.rule() + "|" + Objects.toString(f.value()), new PreviewJson.Result(f.rule(), f.message(), null, f.value()));
        }
        return out;
    }

    private static Map<String, PreviewJson.Result> productFindings(List<Json.Finding> findings) {
        Map<String, PreviewJson.Result> out = new LinkedHashMap<>();
        if (findings != null) findings.forEach(f -> out.putIfAbsent(f.rule(), new PreviewJson.Result(f.rule(), f.message(), null, null)));
        return out;
    }

    private static <T> Map<String, T> byKey(List<T> items, Function<T, String> key) {
        Map<String, T> out = new LinkedHashMap<>();
        items.forEach(item -> out.putIfAbsent(key.apply(item), item));
        return out;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.addAll(b);
        return out;
    }
}
