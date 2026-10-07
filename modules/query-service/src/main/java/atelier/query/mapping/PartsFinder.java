// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mapping;

import atelier.query.api.Json;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The parts of a bill of materials that a query names, by what they are. The query holds one or more alternatives
 * separated by commas ("screw, nut, washer"); an item matches an alternative when its native name or its English name
 * holds the alternative's words, when the glossary resolves the alternative to a term the item belongs to
 * ({@link TermsMapper}: the word of any of the four languages finds the names of every site), or when its part type is
 * the alternative ("software"). Names compare without case, accents and plural endings; a German name may hold the
 * word of four letters or more inside a compound (Getriebemotoren in Getriebemotor, Flügel in Flügelholm).
 *
 * <p>An assembly that matches stands for its parts: the group is the assembly with every part below it, unless an
 * assembly below it matches too, which is then the group (the complete wheel rather than the wheels and covers kit).
 * A matching part outside such a group joins the group of its parent assembly and its reason, once: a part used under
 * several assemblies is listed under the first the tree reaches. Every group states why its items matched: the field
 * (native name, English name, glossary term, part type), the text or label found and its language, and for a term the
 * term's English label. A leading article or quantifier of an alternative ("the", "both", "les", "die") is not part of
 * what it names.
 */
public final class PartsFinder {
    private static final String ASSEMBLY = "ASSEMBLY";
    private static final String PRODUCT = "PRODUCT";
    private static final Set<String> LEADING = Set.of("the", "a", "an", "all", "both", "every", "each", "der", "die", "das",
            "den", "alle", "beide", "le", "la", "les", "l", "un", "une", "des", "tous", "toutes", "el", "los", "las", "todos", "todas");

    /** One alternative: its text, its words, and per item id the glossary term it resolves to with the label found in the item's name. */
    private record Alternative(String text, List<String> words, Map<String, Json.FoundMatch> termIds) {}

    private final List<Alternative> alternatives = new ArrayList<>();
    private final Map<String, Json.FoundGroup> groups = new LinkedHashMap<>();
    /** The parts listed in a name, term or part-type group: a part used under several assemblies is listed under the first. */
    private final Set<String> listed = new HashSet<>();
    private int hidden;

    /** {@code terms} gives, per alternative as written, the glossary terms it resolves to. */
    public PartsFinder(String query, Map<String, List<Json.Term>> terms) {
        for (String text : alternatives(query)) {
            Map<String, Json.FoundMatch> ids = new LinkedHashMap<>();
            for (Json.Term term : terms.getOrDefault(text, List.of())) {
                term.parts().forEach(p -> ids.putIfAbsent(p.id(),
                        new Json.FoundMatch("term", p.matched().label(), p.matched().lang(), term.labels().get("en"), p.narrower())));
            }
            alternatives.add(new Alternative(text, TermsMapper.words(text), ids));
        }
    }

    /** The alternatives of a query: its comma- or semicolon-separated texts without a leading article or quantifier. */
    public static List<String> alternatives(String query) {
        List<String> out = new ArrayList<>();
        for (String part : query.split("[,;]")) {
            List<String> words = new ArrayList<>(List.of(part.strip().split("\\s+")));
            while (words.size() > 1 && LEADING.contains(TermsMapper.words(words.get(0)).stream().findFirst().orElse(""))) words.remove(0);
            String text = String.join(" ", words).strip();
            if (!text.isEmpty() && !out.contains(text)) out.add(text);
        }
        return out;
    }

    /** The groups of the tree under {@code root} (the product root or a subtree's root) and the hidden items in it. */
    public Json.FoundParts find(String product, String query, Json.BomNode root, Json.Provenance provenance, String sparql,
                                Json.Timings timings, Json.Policy policy) {
        visit(root, null);
        return new Json.FoundParts(product, query, List.copyOf(groups.values()), hidden, provenance, sparql, timings, policy);
    }

    private void visit(Json.BomItem item, Json.BomNode parent) {
        if (!(item instanceof Json.BomNode node)) {
            hidden++;
            return;
        }
        boolean assembly = ASSEMBLY.equals(node.partType()) || PRODUCT.equals(node.partType());
        Json.FoundMatch match = PRODUCT.equals(node.partType()) ? null : match(node);
        if (assembly && match != null && !holdsMatchingAssembly(node)) {
            List<Json.FoundItem> parts = new ArrayList<>();
            collect(node, parts);
            groups.put("assembly|" + node.id(), new Json.FoundGroup("assembly", match, item(node, null), parts));
            return;
        }
        if (!assembly && match != null && listed.add(node.id())) {
            String key = match + "|" + (parent == null ? "" : parent.id());
            groups.computeIfAbsent(key, k -> new Json.FoundGroup("parts", match, parent == null ? null : item(parent, null),
                    new ArrayList<>())).parts().add(item(node, node.occurrences()));
        }
        Json.BomNode next = assembly ? node : parent;
        if (node.children() != null) node.children().forEach(c -> visit(c, next));
    }

    /** True when an assembly below {@code node} matches. */
    private boolean holdsMatchingAssembly(Json.BomNode node) {
        if (node.children() == null) return false;
        for (Json.BomItem c : node.children()) {
            if (c instanceof Json.BomNode n && ASSEMBLY.equals(n.partType()) && (match(n) != null || holdsMatchingAssembly(n))) return true;
        }
        return false;
    }

    /** Every part below {@code node}, once each, counting the hidden ones. */
    private void collect(Json.BomNode node, List<Json.FoundItem> parts) {
        if (node.children() == null) return;
        for (Json.BomItem c : node.children()) {
            if (!(c instanceof Json.BomNode n)) {
                hidden++;
                continue;
            }
            if (!ASSEMBLY.equals(n.partType()) && parts.stream().noneMatch(p -> p.id().equals(n.id()))) parts.add(item(n, n.occurrences()));
            collect(n, parts);
        }
    }

    /**
     * Why the node matches, or null: its native name in its site's language ({@code name}), its English name
     * ({@code nameEn}), a glossary term with the label found in its name ({@code term}), or its part type.
     */
    private Json.FoundMatch match(Json.BomNode node) {
        for (Alternative a : alternatives) {
            if (holds(node.name(), a.words(), "de".equals(node.plm()))) {
                return new Json.FoundMatch("name", a.text(), TermsMapper.SITE_LANGUAGE.get(node.plm()), null, null);
            }
            if (holds(node.nameEn(), a.words(), false)) return new Json.FoundMatch("nameEn", a.text(), "en", null, null);
            if (a.termIds().containsKey(node.id())) return a.termIds().get(node.id());
            if (node.partType() != null && a.words().size() == 1 && stem(node.partType().toLowerCase()).equals(stem(a.words().get(0)))) {
                return new Json.FoundMatch("partType", a.text(), null, null, null);
            }
        }
        return null;
    }

    private static Json.FoundItem item(Json.BomNode node, Double occurrences) {
        String nameEn = node.nameEn() == null || node.nameEn().equals(node.name()) ? null : node.nameEn();
        return new Json.FoundItem(node.id(), node.plm(), node.name(), nameEn, occurrences == null || occurrences == 1 ? null : occurrences);
    }

    /** True when {@code name} holds {@code words}: side by side and in order, or for a German name inside its compounds. */
    static boolean holds(String name, List<String> words, boolean german) {
        if (name == null || words.isEmpty()) return false;
        List<String> text = TermsMapper.words(name);
        if (german) {
            // A short word inside a compound is too often another word's syllable (pin in Spindel): it must stand alone.
            String joined = String.join(" ", text);
            return words.stream().allMatch(w -> w.length() >= 4 ? joined.contains(germanStem(w))
                    : text.stream().anyMatch(t -> stem(t).equals(stem(w))));
        }
        for (int i = 0; i + words.size() <= text.size(); i++) {
            boolean all = true;
            for (int j = 0; j < words.size() && all; j++) all = stem(text.get(i + j)).equals(stem(words.get(j)));
            if (all) return true;
        }
        return false;
    }

    /** The text with every word without its plural ending ("roues dentées" is looked up as "roue dentee"). */
    public static String singular(String text) {
        return String.join(" ", TermsMapper.words(text).stream().map(PartsFinder::stem).toList());
    }

    /** An English, French or Spanish word without its plural ending. */
    static String stem(String word) {
        if (word.length() > 4 && word.endsWith("ies")) return word.substring(0, word.length() - 3) + "y";
        if (word.length() > 4 && word.endsWith("es") && word.substring(0, word.length() - 2).matches(".*(s|x|z|ch|sh)")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")) return word.substring(0, word.length() - 1);
        return word;
    }

    /** A German word without its plural ending (Getriebemotoren, Zahnräder, Flügel stay findable inside compounds). */
    static String germanStem(String word) {
        for (String ending : List.of("en", "er", "e", "n", "s")) {
            if (word.length() > ending.length() + 4 && word.endsWith(ending)) return word.substring(0, word.length() - ending.length());
        }
        return word;
    }
}
