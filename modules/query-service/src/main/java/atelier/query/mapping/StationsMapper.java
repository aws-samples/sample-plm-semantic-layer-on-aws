// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mapping;

import atelier.query.Atelier;
import atelier.query.api.Json;
import atelier.query.api.StationsJson;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.jena.rdf.model.Model;

/**
 * The stations and sections answers over a redacted stations federation ({@link StationModel}). On a symmetric axis
 * station n is at +n on the right side and -n on the left; a range or a section on both sides covers both intervals. A
 * part overlaps an interval when one of its occurrences reaches more than {@link #SLACK_MM} into it, and crosses an end
 * or a joint when it reaches more than {@link #SLACK_MM} past it; a part covers a station its span reaches within
 * {@link #SLACK_MM}. Parts the viewer may not see are not federated, so no answer names them.
 */
public final class StationsMapper {
    static final double SLACK_MM = 1.0;
    static final List<String> SIDES = List.of("left", "right", "both");

    private final StationModel data;

    public StationsMapper(Model model, Units units, String product, Collection<String> roots) {
        this.data = new StationModel(model, units, product, roots);
    }

    /** The visible parts between two stations of the product, on one side or both. */
    public StationsJson.Between between(String product, String from, String to, String side, Json.Provenance provenance,
                                        String sparql, Json.Timings timings, Json.Policy policy) {
        StationsJson.Station a = station(from);
        StationsJson.Station b = station(to);
        String s = side(side);
        double lo = Math.min(a.atMm(), b.atMm());
        double hi = Math.max(a.atMm(), b.atMm());
        List<double[]> intervals = intervals(lo, hi, s);
        List<StationsJson.SpanPart> parts = new ArrayList<>();
        for (StationModel.PartSpan p : data.parts) {
            List<StationsJson.Occurrence> in = new ArrayList<>();
            for (int i = 0; i < p.occurrences().size(); i++) {
                double[] o = p.occurrences().get(i);
                String position = o == null ? null : position(o, intervals);
                if (position != null) in.add(new StationsJson.Occurrence(i + 1, round(o[0]), round(o[1]), position));
            }
            if (in.isEmpty()) continue;
            boolean placed = p.occurrences().size() > 1;
            String position = in.stream().anyMatch(o -> o.position().equals("crossing")) ? "crossing" : "inside";
            parts.add(new StationsJson.SpanPart(Atelier.nativeId(p.iri()), p.plm(), p.name(), p.nameEn(), List.of(p.from(), p.to()),
                    p.unit(), round(p.fromMm()), round(p.toMm()), position, covered(in), placed ? p.occurrences().size() : null,
                    placed ? in : null));
        }
        parts.sort(Comparator.comparing((StationsJson.SpanPart p) -> !p.position().equals("inside"))
                .thenComparingDouble(p -> Math.abs(p.fromMm() + p.toMm())).thenComparing(StationsJson.SpanPart::id));
        List<StationsJson.StationRef> within = data.stations.values().stream()
                .filter(st -> st.atMm() >= lo && st.atMm() <= hi).map(st -> data.ref(st.id())).toList();
        Set<String> named = new LinkedHashSet<>(List.of(a.id(), b.id()));
        within.forEach(r -> named.add(r.id()));
        int inside = (int) parts.stream().filter(p -> p.position().equals("inside")).count();
        return new StationsJson.Between(product, data.axis, new StationsJson.Range(data.ref(a.id()), data.ref(b.id()), s, json(intervals)),
                within, parts, inside, parts.size() - inside, notes(named), provenance, sparql, timings, policy);
    }

    /** The product's sections with their parts and foreign parts, and every joint with the parts crossing it. */
    public StationsJson.Sections sections(String product, Json.Provenance provenance, String sparql, Json.Timings timings,
                                          Json.Policy policy) {
        List<StationsJson.Section> sections = new ArrayList<>();
        for (StationModel.SectionData s : data.sections) {
            List<double[]> intervals = s.stations().isEmpty() ? List.of() : intervals(data.stations.get(s.stations().getFirst()).atMm(),
                    data.stations.get(s.stations().getLast()).atMm(), s.side());
            List<StationsJson.SectionPart> parts = new ArrayList<>();
            for (StationModel.PartSpan p : data.parts) {
                List<double[]> in = p.occurrences().stream().filter(o -> o != null && position(o, intervals) != null).toList();
                if (!in.isEmpty()) parts.add(sectionPart(p, in));
            }
            parts.sort(Comparator.comparingDouble((StationsJson.SectionPart p) -> Math.abs(p.fromMm() + p.toMm()))
                    .thenComparing(StationsJson.SectionPart::id));
            Json.PartView part = s.part() == null ? null : data.visible(s.part())
                    ? new Json.PartRef(Atelier.nativeId(s.part()), Atelier.plmOf(s.part()), null)
                    : new Json.Redacted(true, Atelier.plmOf(s.part()));
            sections.add(new StationsJson.Section(s.id(), s.name(), s.owner(), s.side(), part,
                    s.stations().stream().map(data::ref).toList(), json(intervals), parts,
                    parts.stream().filter(p -> !p.plm().equalsIgnoreCase(s.owner())).toList()));
        }
        List<StationsJson.Joint> joints = joints();
        Set<String> named = new LinkedHashSet<>();
        data.sections.forEach(s -> named.addAll(s.stations()));
        return new StationsJson.Sections(product, data.axis, List.copyOf(data.stations.values()), sections, joints, notes(named),
                provenance, sparql, timings, policy);
    }

    /** Each station two sections share, at each signed position where both sections lie, with the parts crossing it. */
    private List<StationsJson.Joint> joints() {
        List<StationsJson.Joint> joints = new ArrayList<>();
        List<StationModel.SectionData> sections = data.sections;
        for (int i = 0; i < sections.size(); i++) {
            for (int j = i + 1; j < sections.size(); j++) {
                StationModel.SectionData a = sections.get(i);
                StationModel.SectionData b = sections.get(j);
                for (String id : a.stations()) {
                    if (!b.stations().contains(id)) continue;
                    double at = data.stations.get(id).atMm();
                    Set<Double> positions = new LinkedHashSet<>(signs(a.side()));
                    positions.retainAll(signs(b.side()));
                    Set<Double> signed = new LinkedHashSet<>();
                    positions.forEach(sign -> signed.add(sign * at + 0.0));
                    for (double pos : signed) {
                        List<StationsJson.SectionPart> crossing = new ArrayList<>();
                        for (StationModel.PartSpan p : data.parts) {
                            List<double[]> across = p.occurrences().stream()
                                    .filter(o -> o != null && o[0] < pos - SLACK_MM && o[1] > pos + SLACK_MM).toList();
                            if (!across.isEmpty()) crossing.add(sectionPart(p, across));
                        }
                        crossing.sort(Comparator.comparing(StationsJson.SectionPart::plm).thenComparing(StationsJson.SectionPart::id));
                        String side = !data.axis.symmetric() ? "both" : pos < 0 ? "left" : pos > 0 ? "right" : "both";
                        joints.add(new StationsJson.Joint(data.ref(id), round(pos), side, List.of(a.id(), b.id()),
                                List.of(a.owner(), b.owner()), crossing));
                    }
                }
            }
        }
        joints.sort(Comparator.comparingDouble((StationsJson.Joint jt) -> Math.abs(jt.atMm())).thenComparing(StationsJson.Joint::side));
        return joints;
    }

    /** The signs of the positions a side covers: +1 right, -1 left, both on a symmetric axis; +1 alone on an axis that is not. */
    private List<Double> signs(String side) {
        if (!data.axis.symmetric()) return List.of(1.0);
        return switch (side) {
            case "left" -> List.of(-1.0);
            case "right" -> List.of(1.0);
            default -> List.of(-1.0, 1.0);
        };
    }

    /** The intervals from {@code lo} to {@code hi} on a side, merged where they touch (a range from the plane of symmetry). */
    private List<double[]> intervals(double lo, double hi, String side) {
        List<double[]> out = new ArrayList<>();
        for (double sign : signs(side)) out.add(sign < 0 ? new double[] {-hi, -lo} : new double[] {lo, hi});
        if (out.size() == 2 && out.get(0)[1] >= out.get(1)[0]) return List.of(new double[] {out.get(0)[0], out.get(1)[1]});
        return out;
    }

    /** inside or crossing for a span reaching more than the slack into one of the intervals; null when it reaches none. */
    private static String position(double[] span, List<double[]> intervals) {
        for (double[] in : intervals) {
            if (Math.min(span[1], in[1]) - Math.max(span[0], in[0]) <= SLACK_MM) continue;
            return span[0] < in[0] - SLACK_MM || span[1] > in[1] + SLACK_MM ? "crossing" : "inside";
        }
        return null;
    }

    /** The stations the occurrences' spans cover, in order along the axis, each with its basis. */
    private List<StationsJson.StationRef> covered(List<StationsJson.Occurrence> occurrences) {
        return data.stations.values().stream().filter(st -> occurrences.stream().anyMatch(o -> signs("both").stream()
                        .anyMatch(sign -> sign * st.atMm() >= o.fromMm() - SLACK_MM && sign * st.atMm() <= o.toMm() + SLACK_MM)))
                .map(st -> data.ref(st.id())).toList();
    }

    private static StationsJson.SectionPart sectionPart(StationModel.PartSpan p, List<double[]> spans) {
        double from = spans.stream().mapToDouble(o -> o[0]).min().orElse(p.fromMm());
        double to = spans.stream().mapToDouble(o -> o[1]).max().orElse(p.toMm());
        return new StationsJson.SectionPart(Atelier.nativeId(p.iri()), p.plm(), p.name(), round(from), round(to));
    }

    /** One note per basis that is not printed, naming the stations of the answer that rest on it. */
    private List<String> notes(Collection<String> named) {
        List<String> notes = new ArrayList<>();
        for (String basis : List.of("inferred", "measured", "joint")) {
            List<String> ids = named.stream().filter(id -> data.stations.containsKey(id) && basis.equals(data.stations.get(id).basis())).toList();
            if (ids.isEmpty()) continue;
            notes.add(String.join(", ", ids) + (ids.size() == 1 ? " is " : " are ") + switch (basis) {
                case "inferred" -> "inferred: no source prints the position, it is read from the geometry or another source.";
                case "measured" -> "measured on the product or a model of it, within its tolerance.";
                default -> "a joint between two sections or parts.";
            });
        }
        return notes;
    }

    private StationsJson.Station station(String id) {
        StationsJson.Station s = data.stations.get(id);
        if (s == null) throw new IllegalArgumentException("unknown station " + id + "; the stations are " + String.join(", ", data.stations.keySet()));
        return s;
    }

    private static String side(String side) {
        if (side == null || side.isBlank()) return "both";
        if (!SIDES.contains(side.strip())) throw new IllegalArgumentException("side is left, right or both");
        return side.strip();
    }

    private static List<List<Double>> json(List<double[]> intervals) {
        return intervals.stream().map(i -> List.of(round(i[0]), round(i[1]))).toList();
    }

    private static double round(double mm) {
        double r = Math.round(mm * 1000) / 1000.0;
        return r == 0 ? 0.0 : r;
    }
}
