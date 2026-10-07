// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/** Response shape of the variant diff, as defined in docs/contract.md. */
public final class VariantJson {
    private VariantJson() {}

    /** A variant group: one installation slot, the options it can take and the default the base product is built with. */
    public record Group(String key, String name, String selects, String defaultOption, List<String> options) {}

    /** The variant groups of a product: what the variant diff can be asked about. */
    public record Groups(String product, List<Group> groups) {}

    /** An option: its code, whether it is the default, its source and applicability, the ports it has that the data does not model. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Option(String key, boolean isDefault, String source, String applicability, List<String> portsNotModelled) {}

    /**
     * One side of a port: the interface that uses it in the configuration, its status for the viewer and the rules it
     * fails, the host's feature at the port, the part mated to it and that part's feature.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Side(String interfaceId, String label, String status, List<String> rules, Json.FeatureView feature,
                       Json.PartView mate, Json.FeatureView mateFeature) {}

    /**
     * A port of a host part (a part both configurations hold) that either configuration uses through an interface of the
     * group: {@code change} is added, removed, changed, same or not-modelled (a port one option has and the data does not
     * model, never compared), {@code differences} what changed (the interface, its status, the mate, and each attribute
     * of the host's or the mate's feature), {@code against} the default configuration's side and {@code option} the
     * option's. {@code port} is null for a feature the data gives no port: it is then paired by its id.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Port(Json.PartView host, String port, String change, List<String> differences, Side against, Side option) {}

    public record Tally(int pass, int fail, int notEvaluable) {}

    /**
     * One configuration of the product: the option it is built with, its interfaces and their tally for the viewer, the part
     * occurrences per site and in total (the quantities multiplied down its trees), its mass in kilograms (as the
     * massLimit rule counts it) and the findings of the product rules.
     */
    public record Configuration(String option, int interfaces, Tally tally, Map<String, Double> occurrences, double occurrencesTotal,
                                double massKg, List<Json.Finding> productFindings) {}

    /** The items and interfaces one configuration holds and the other does not. */
    public record Items(List<Json.PartView> items, List<String> interfaces) {}

    /**
     * The variant diff: the option against the group's default option, port by port, the items and interfaces each side
     * holds alone, both configurations with the interface rules run on each, and the option's own interfaces as the
     * interfaces answer reports them.
     */
    public record Diff(String product, Group group, Option option, Option against, List<Port> ports, Items removed, Items added,
                       Configuration configuration, Configuration baseline, List<Json.Interface> interfaces,
                       Json.Provenance provenance, String sparql, Json.Timings timings, Json.Policy policy) {}
}
