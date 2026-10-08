// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mapping;

import atelier.query.Atelier;
import atelier.query.api.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Where-used and impact answers derived from the mapped interfaces of an answer: the interfaces a
 * visible part or feature appears on, with what the viewer sees of them. A mate on a hidden part is
 * the redaction marker the interface already carries for it.
 */
public final class UsageMapper {
    private final List<Json.Interface> interfaces;

    public UsageMapper(List<Json.Interface> interfaces) {
        this.interfaces = interfaces;
    }

    /** The interfaces naming the part, each with its other parts and the part's features there per kind. */
    public List<Json.Use> whereUsed(Json.Part part) {
        return interfaces.stream().filter(i -> names(i, part)).map(i -> new Json.Use(i.id(), i.label(), i.status(),
                i.parts().stream().filter(p -> !(p instanceof Json.Part q && q.id().equals(part.id()) && q.plm().equals(part.plm()))).toList(),
                countsByKind(own(i, onPart(part))))).toList();
    }

    /** The part's features over every interface naming it, counted per kind. */
    public Map<String, Integer> featureCounts(Json.Part part) {
        return countsByKind(interfaces.stream().filter(i -> names(i, part)).flatMap(i -> own(i, onPart(part)).stream()).toList());
    }

    /** The interfaces a change to the part touches: each with the part's features there and their mates. */
    public List<Json.Impacted> impactOfPart(Json.Part part) {
        return interfaces.stream().filter(i -> names(i, part)).map(i -> impacted(i, own(i, onPart(part)))).toList();
    }

    /** The interfaces a change to the feature touches: each with the feature and its mates. */
    public List<Json.Impacted> impactOfFeature(Json.Feature feature) {
        Predicate<Json.Feature> same = f -> f.plm().equals(feature.plm()) && f.id().equals(feature.id());
        return interfaces.stream().filter(i -> !own(i, same).isEmpty()).map(i -> impacted(i, own(i, same))).toList();
    }

    private static Json.Impacted impacted(Json.Interface i, List<Json.Feature> own) {
        Set<String> mateIds = own.stream().flatMap(f -> f.matesWith().stream()).filter(String.class::isInstance)
                .map(String.class::cast).collect(Collectors.toSet());
        List<Json.FeatureView> touched = new ArrayList<>(i.features().stream()
                .filter(f -> f instanceof Json.Feature g && (own.contains(g) || mateIds.contains(g.id()))).toList());
        own.stream().flatMap(f -> f.matesWith().stream()).filter(Json.Redacted.class::isInstance)
                .map(Json.Redacted.class::cast).distinct().forEach(touched::add);
        List<String> rules = i.violations().stream().map(Json.Violation::rule).distinct().toList();
        return new Json.Impacted(i.id(), i.label(), i.status(), rules, touched);
    }

    private static boolean names(Json.Interface i, Json.Part part) {
        return i.parts().stream().anyMatch(p -> p instanceof Json.Part q && q.id().equals(part.id()) && q.plm().equals(part.plm()));
    }

    private static Predicate<Json.Feature> onPart(Json.Part part) {
        return f -> f.plm().equals(part.plm()) && f.partId().equals(part.id());
    }

    private static List<Json.Feature> own(Json.Interface i, Predicate<Json.Feature> which) {
        return i.features().stream().filter(Json.Feature.class::isInstance).map(Json.Feature.class::cast).filter(which).toList();
    }

    /** Counts per kind in {@link Atelier#KINDS} order, every kind present. */
    private static Map<String, Integer> countsByKind(List<Json.Feature> features) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Atelier.KINDS.forEach(kind -> counts.put(kind, (int) features.stream().filter(f -> kind.equals(f.kind())).count()));
        return counts;
    }
}
