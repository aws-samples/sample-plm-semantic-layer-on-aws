// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Response shapes of the path and flow answers, as defined in docs/contract.md. */
public final class FunctionJson {
    private FunctionJson() {}

    /** The interface of a step, as the viewer's profile sees it: its status and the rules it fails. */
    public record Joint(String id, String label, String status, List<String> rules) {}

    /**
     * One step of a path or a flow, from a part to the next: each side a {@link Json.PartRef} or a {@link Json.Redacted}
     * marker, the interface joining them ({@code joint}, absent for a flow edge no interface joins), and for a flow
     * step its {@code flow} kind, the fixed gear it reacts on ({@code reaction}) and the gear stage it is ({@code stage}).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Step(Json.PartView from, Json.PartView to, Joint joint, String flow, Json.PartView reaction, Stage stage) {}

    public record Path(int length, List<Step> steps) {}

    /**
     * The shortest paths of parts joined by interfaces between two parts of a product: at most {@code maxPaths} of at most
     * {@code maxLength} steps, through parts the viewer may see; {@code notes} say when a part the viewer may not see
     * breaks or shortens a path. {@code parts} describes every visible part on the paths.
     */
    public record Paths(String product, Json.PartView from, Json.PartView to, int maxLength, int maxPaths, List<Path> paths,
                        List<String> notes, List<Json.Part> parts, Json.Provenance provenance, String sparql, Json.Timings timings,
                        Json.Policy policy) {}

    /** A gear of a stage: its part, site, tooth count and module in mm. */
    public record Gear(String id, String plm, Integer teeth, Double moduleMm) {}

    /**
     * A gear stage: the driving and driven gear, the fixed gear of a planetary stage, and the stage's ratio, input speed
     * over output speed: driven over driver teeth, or sun over ring plus sun for a planetary stage with a fixed ring.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Stage(Gear driver, Gear driven, Gear reaction, double ratio) {}

    /** The overall ratio of a flow's gear train, from the flow's start to {@code to}: the product of its stages, and the speed-up it gives. */
    public record Ratio(String to, String toPlm, double ratio, double speedUp, List<Stage> stages, String text) {}

    /** A rated speed carried through the gear train and compared with the rated speed of a part downstream, within 1 %. */
    public record SpeedCheck(String from, String fromPlm, double fromRpm, String to, String toPlm, double ratedRpm, double predictedRpm,
                             boolean holds, String text) {}

    /**
     * The walk along the drives relation from a part: downstream ({@code direction} down) or upstream for root cause
     * ({@code up}), every flow kind or only {@code flow}; each edge once, at most {@code maxSteps}. A downstream walk through
     * meshing gears states the gear {@code ratio} and the {@code checks} of the rated speeds the product records.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Flow(String product, Json.PartView from, String flow, String direction, int maxSteps, List<Step> steps, Ratio ratio,
                       List<SpeedCheck> checks, List<String> notes, List<Json.Part> parts, Json.Provenance provenance, String sparql,
                       Json.Timings timings, Json.Policy policy) {}
}
