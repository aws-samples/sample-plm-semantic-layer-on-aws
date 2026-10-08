// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.paths;

import atelier.query.Atelier;
import atelier.query.api.FunctionJson;
import atelier.query.mapping.Units;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;

/**
 * Computing along a flow: the gears of a drivetrain live in several sites, each stating its tooth count and module in
 * its own idiom, and the edges between them are the layer's, so only the layer can multiply the stages out. A
 * mechanical edge between two gears is a mesh, whose ratio (input speed over output speed) is driven over driver teeth;
 * an edge whose driving gear also meshes a fixed gear ({@code atelier:reactionPart}, the ring of a planetary stage
 * driven through its carrier) gives the sun it drives sun over ring plus sun. Any other mechanical edge (a shaft, a key,
 * a coupling) passes the speed unchanged; a non-mechanical edge ends the train. A mesh whose gears the viewer may not see
 * or whose tooth count is not stated (a stepped cluster) leaves the ratio beyond it unknown.
 */
public final class GearTrain {
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private final Model model;
    private final Units units;

    public GearTrain(Model model, Units units) {
        this.model = model;
        this.units = units;
    }

    /** The gear stage an edge is, or null when it is not a mesh of visible gears with stated teeth. */
    public FunctionJson.Stage stage(FlowWalker.Drive d) {
        if (!"mechanical".equals(d.flow())) return null;
        FunctionJson.Gear driver = gear(d.driver());
        FunctionJson.Gear driven = gear(d.driven());
        if (driver == null || driven == null || driver.teeth() == null || driven.teeth() == null) return null;
        FunctionJson.Gear reaction = d.reaction() == null ? null : gear(d.reaction());
        if (d.reaction() != null && (reaction == null || reaction.teeth() == null)) return null;
        double ratio = reaction == null ? (double) driven.teeth() / driver.teeth()
                : (double) driven.teeth() / (reaction.teeth() + driven.teeth());
        return new FunctionJson.Stage(driver, driven, reaction, ratio);
    }

    /** The overall ratio and speed checks of a downstream walk. */
    public record Result(FunctionJson.Ratio ratio, List<FunctionJson.SpeedCheck> checks, List<String> notes) {}

    public Result along(FlowWalker.Walk walk) {
        String start = walk.parts().get(0);
        Map<String, BigDecimal> ratio = new HashMap<>(Map.of(start, BigDecimal.ONE));
        Map<String, List<FunctionJson.Stage>> stages = new HashMap<>(Map.of(start, List.of()));
        boolean unknown = false;
        for (String part : walk.parts().subList(1, walk.parts().size())) {
            FlowWalker.Drive d = walk.reachedBy().get(part);
            BigDecimal before = ratio.get(d.driver());
            if (before == null || !"mechanical".equals(d.flow())) continue;
            FunctionJson.Stage stage = stage(d);
            boolean mesh = isGear(d.driver()) && isGear(d.driven());
            if (mesh && stage == null) {
                unknown = true;
                continue;
            }
            List<FunctionJson.Stage> chain = new ArrayList<>(stages.get(d.driver()));
            BigDecimal factor = BigDecimal.ONE;
            if (stage != null) {
                chain.add(stage);
                factor = stage.reaction() == null
                        ? BigDecimal.valueOf(stage.driven().teeth()).divide(BigDecimal.valueOf(stage.driver().teeth()), MC)
                        : BigDecimal.valueOf(stage.driven().teeth()).divide(BigDecimal.valueOf(stage.reaction().teeth() + stage.driven().teeth()), MC);
            }
            ratio.put(part, before.multiply(factor, MC));
            stages.put(part, chain);
        }
        List<String> notes = new ArrayList<>();
        if (unknown) notes.add("The gear ratio past a mesh whose gears are not visible to your profile, or state no tooth count, is not computed.");
        String end = null;
        for (String part : walk.parts()) {
            List<FunctionJson.Stage> chain = stages.get(part);
            if (chain != null && !chain.isEmpty() && (end == null || chain.size() > stages.get(end).size())) end = part;
        }
        FunctionJson.Ratio overall = end == null ? null : overall(start, end, ratio.get(end), stages.get(end));
        return new Result(overall, checks(walk, ratio), notes);
    }

    private FunctionJson.Ratio overall(String start, String end, BigDecimal ratio, List<FunctionJson.Stage> chain) {
        double speedUp = BigDecimal.ONE.divide(ratio, MC).doubleValue();
        TreeSet<String> sites = new TreeSet<>();
        chain.forEach(s -> { sites.add(s.driver().plm().toUpperCase()); sites.add(s.driven().plm().toUpperCase()); });
        String text = String.format(Locale.ROOT, "Gear ratio %s from %s to %s over %d stage%s of %s gears: %s turns %s times per turn of %s.",
                round(ratio, 5), Atelier.nativeId(start), Atelier.nativeId(end), chain.size(), chain.size() == 1 ? "" : "s",
                String.join(" and ", sites), Atelier.nativeId(end), round(BigDecimal.valueOf(speedUp), 4), Atelier.nativeId(start));
        return new FunctionJson.Ratio(Atelier.nativeId(end), Atelier.plmOf(end), ratio.doubleValue(), speedUp, chain, text);
    }

    /** Each rated speed downstream of the first one the walk reaches, against that one carried through the train. */
    private List<FunctionJson.SpeedCheck> checks(FlowWalker.Walk walk, Map<String, BigDecimal> ratio) {
        List<String> rated = walk.parts().stream().filter(p -> rpm(p) != null && ratio.containsKey(p)).toList();
        List<FunctionJson.SpeedCheck> out = new ArrayList<>();
        if (rated.size() < 2) return out;
        String reference = rated.get(0);
        BigDecimal from = rpm(reference);
        for (String part : rated.subList(1, rated.size())) {
            if (!downstreamOf(part, reference, walk)) continue;
            BigDecimal predicted = from.multiply(ratio.get(reference), MC).divide(ratio.get(part), MC);
            BigDecimal ratedRpm = rpm(part);
            BigDecimal off = predicted.subtract(ratedRpm).abs().divide(ratedRpm, MC);
            boolean holds = off.compareTo(TOLERANCE) <= 0;
            String text = String.format(Locale.ROOT, "%s at %s rpm through the gear train turns %s at %s rpm; rated %s rpm: %s.",
                    Atelier.nativeId(reference), from.toPlainString(), Atelier.nativeId(part), round(predicted, 1), ratedRpm.toPlainString(),
                    holds ? "within 1 %" : "off by " + round(off.multiply(BigDecimal.valueOf(100)), 1) + " %");
            out.add(new FunctionJson.SpeedCheck(Atelier.nativeId(reference), Atelier.plmOf(reference), from.doubleValue(),
                    Atelier.nativeId(part), Atelier.plmOf(part), ratedRpm.doubleValue(), predicted.doubleValue(), holds, text));
        }
        return out;
    }

    private static boolean downstreamOf(String part, String reference, FlowWalker.Walk walk) {
        for (String p = part; p != null; p = walk.reachedBy().containsKey(p) ? walk.reachedBy().get(p).driver() : null) {
            if (p.equals(reference)) return true;
        }
        return false;
    }

    private boolean isGear(String iri) {
        Resource part = model.createResource(iri);
        return !part.hasProperty(RDF.type) || part.hasProperty(model.createProperty(Atelier.ONT + "toothCount"))
                || part.hasProperty(model.createProperty(Atelier.ONT + "gearModule"));
    }

    /** A visible gear: a part stating a tooth count or a module; null otherwise. */
    private FunctionJson.Gear gear(String iri) {
        Resource part = model.createResource(iri);
        if (!part.hasProperty(RDF.type)) return null;
        Statement teeth = part.getProperty(model.createProperty(Atelier.ONT + "toothCount"));
        Statement module = part.getProperty(model.createProperty(Atelier.ONT + "gearModule"));
        if (teeth == null && module == null) return null;
        Double mm = null;
        if (module != null && module.getObject().isResource()) {
            Resource q = module.getResource();
            Statement value = q.getProperty(model.createProperty(Atelier.QUDT + "numericValue"));
            Statement unit = q.getProperty(model.createProperty(Atelier.QUDT + "unit"));
            BigDecimal converted = value == null || unit == null ? null
                    : units.toMm(new BigDecimal(value.getLiteral().getLexicalForm()), unit.getResource().getURI());
            mm = converted == null ? null : converted.doubleValue();
        }
        return new FunctionJson.Gear(Atelier.nativeId(iri), Atelier.plmOf(iri),
                teeth == null || !teeth.getObject().isLiteral() ? null : teeth.getInt(), mm);
    }

    private BigDecimal rpm(String iri) {
        Statement s = model.createResource(iri).getProperty(model.createProperty(Atelier.ONT + "ratedSpeedRpm"));
        RDFNode o = s == null ? null : s.getObject();
        return o == null || !o.isLiteral() ? null : new BigDecimal(o.asLiteral().getLexicalForm());
    }

    private static String round(BigDecimal value, int decimals) {
        return value.setScale(decimals, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString();
    }
}
