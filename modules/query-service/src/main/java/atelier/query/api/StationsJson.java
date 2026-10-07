// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Response shapes of the stations and sections answers, as defined in docs/contract.md. */
public final class StationsJson {
    private StationsJson() {}

    /** The product's station axis: x, y or z, whether stations are measured from a plane of symmetry, and how, in words. */
    public record Axis(String axis, boolean symmetric, String measure) {}

    /**
     * A station: its id, position in mm along the axis (on a symmetric axis, the distance from the plane of symmetry), its
     * basis (printed, measured, joint or inferred), its source and, when stated, its tolerance in mm.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Station(String id, double atMm, String basis, String source, Double toleranceMm) {}

    /** A station named in an answer, always with its basis, so an inferred station reads as inferred wherever it appears. */
    public record StationRef(String id, String basis) {}

    /** The range asked: its two stations, the side, and the intervals along the axis it covers, in mm. */
    public record Range(StationRef from, StationRef to, String side, List<List<Double>> intervalsMm) {}

    /** One occurrence of a placed part in the range: its index among the part's occurrences, its span in mm and position. */
    public record Occurrence(int index, double fromMm, double toMm, String position) {}

    /**
     * A part whose span overlaps the range by more than 1 mm: its span as its PLM stores it ({@code span}, in {@code unit})
     * and in mm, its position (inside the range, or crossing one of its ends by more than 1 mm), the stations its span
     * covers, and for a part placed more than once its occurrence count and the occurrences in the range.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SpanPart(String id, String plm, String name, String nameEn, List<Double> span, String unit, double fromMm,
                           double toMm, String position, List<StationRef> stations, Integer occurrenceCount,
                           List<Occurrence> occurrences) {}

    /**
     * GET /query/stations: the visible parts between two stations, inside first then crossing, each in order along the
     * axis, the stations within the range, and notes naming the inferred stations the answer rests on.
     */
    public record Between(String product, Axis axis, Range range, List<StationRef> stations, List<SpanPart> parts, int inside,
                          int crossing, List<String> notes, Json.Provenance provenance, String sparql, Json.Timings timings,
                          Json.Policy policy) {}

    /** A part in a section or crossing a joint: id, site, native name and the hull of its spans there, in mm. */
    public record SectionPart(String id, String plm, String name, double fromMm, double toMm) {}

    /**
     * A section: its owner, side, stations in order, the intervals it covers, the part it is ({@link Json.PartView}, absent
     * when no part is the section), the visible parts overlapping it by more than 1 mm and among them the foreign ones,
     * owned by another site.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Section(String id, String name, String owner, String side, Json.PartView part, List<StationRef> stations,
                          List<List<Double>> intervalsMm, List<SectionPart> parts, List<SectionPart> foreign) {}

    /**
     * A joint: a station two sections share, at its signed position in mm on one side, the two sections and their owners,
     * and the visible parts crossing it (more than 1 mm on both sides).
     */
    public record Joint(StationRef station, double atMm, String side, List<String> sections, List<String> owners,
                        List<SectionPart> crossing) {}

    /** GET /query/sections: the product's stations, its sections with their parts, and every joint with its crossing parts. */
    public record Sections(String product, Axis axis, List<Station> stations, List<Section> sections, List<Joint> joints,
                           List<String> notes, Json.Provenance provenance, String sparql, Json.Timings timings, Json.Policy policy) {}
}
