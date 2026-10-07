// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mapping;

import atelier.query.Atelier;
import atelier.query.api.Json;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;

/**
 * Resolves a term in any of the four languages against the glossary of the labels graph ({@code atelier:Terms}) and
 * finds the items whose names hold it. Labels and names are compared without case and accents. A concept matches when
 * one of its labels is the text ({@code exact}) or holds its words in order ({@code word}); exact matches come first. An
 * item belongs to a concept when its name in a language holds a label of the concept, or of a narrower concept, in that
 * language: an English label is looked for in the English names of every site, a German, French or Spanish label in
 * the native names of that site. German writes compounds, so a German label may sit inside a word (Zahnrad in
 * Antriebszahnrad); a word of another language must be a whole word, or that word with a plural s or es. A label found
 * only inside a longer label of another concept in the same name does not count (the screw of an aerial screw). A
 * part found through a narrower concept names it ({@code narrower}: an O-ring found under seal).
 */
public final class TermsMapper {
    static final Map<String, String> SITE_LANGUAGE = Map.of("fr", "fr", "de", "de", "uk", "en", "es", "es");
    static final List<String> LANGUAGES = List.of("en", "de", "fr", "es");

    private record Concept(Resource resource, Map<String, String> labels, List<Json.TermLabel> altLabels, String broader) {
        List<Json.TermLabel> all() {
            List<Json.TermLabel> out = new ArrayList<>();
            labels.forEach((lang, label) -> out.add(new Json.TermLabel(lang, label)));
            out.addAll(altLabels);
            return out;
        }
    }

    private record Item(Resource resource, String product, String name, String nameEn, Map<String, String> byLanguage) {}

    private final Map<String, Concept> concepts = new LinkedHashMap<>();
    private final List<Item> items = new ArrayList<>();

    public TermsMapper(Model model) {
        Property inScheme = model.createProperty(Atelier.SKOS + "inScheme");
        Property pref = model.createProperty(Atelier.PREF_LABEL);
        Property alt = model.createProperty(Atelier.ALT_LABEL);
        Property broader = model.createProperty(Atelier.SKOS + "broader");
        Property partOf = model.createProperty(Atelier.PART_OF);
        for (Resource c : model.listSubjectsWithProperty(inScheme, model.createResource(Atelier.TERMS)).toList()) {
            Map<String, String> labels = new LinkedHashMap<>();
            LANGUAGES.forEach(lang -> labels.put(lang, literal(c, pref, lang).stream().findFirst().orElse(null)));
            List<Json.TermLabel> alts = LANGUAGES.stream().flatMap(lang -> literal(c, alt, lang).stream().sorted()
                    .map(label -> new Json.TermLabel(lang, label))).toList();
            Statement b = c.getProperty(broader);
            concepts.put(c.getURI(), new Concept(c, labels, alts, b == null ? null : b.getResource().getURI()));
        }
        for (Resource r : model.listSubjectsWithProperty(pref).toList()) {
            String plm = r.isURIResource() ? Atelier.plmOf(r.getURI()) : null;
            Statement product = r.getProperty(partOf);
            if (plm == null || product == null || !product.getObject().isURIResource()) continue;
            String lang = SITE_LANGUAGE.get(plm);
            String name = literal(r, pref, lang).stream().findFirst().orElse(null);
            if (name == null) continue;
            String english = "en".equals(lang) ? name : literal(r, alt, "en").stream().findFirst().orElse(null);
            Map<String, String> byLanguage = new LinkedHashMap<>();
            byLanguage.put(lang, name);
            if (english != null) byLanguage.put("en", english);
            items.add(new Item(r, Atelier.localName(product.getResource().getURI()), name, english, byLanguage));
        }
        items.sort(Comparator.comparing((Item i) -> Atelier.PLMS.indexOf(Atelier.plmOf(i.resource().getURI())))
                .thenComparing(i -> Atelier.nativeId(i.resource().getURI())));
    }

    /** The concepts {@code q} names, exact matches first, each with the items whose names hold one of its labels. */
    public List<Json.Term> resolve(String q) {
        List<String> words = words(q);
        if (words.isEmpty()) return List.of();
        List<Json.Term> exact = new ArrayList<>();
        List<Json.Term> word = new ArrayList<>();
        for (Concept c : concepts.values()) {
            List<Json.TermLabel> labels = c.all();
            if (labels.stream().anyMatch(l -> words(l.label()).equals(words))) {
                exact.add(term(c, "exact"));
            } else if (labels.stream().anyMatch(l -> contains(words(l.label()), words, false))) {
                word.add(term(c, "word"));
            }
        }
        Comparator<Json.Term> byLabel = Comparator.comparing(t -> t.labels().get("en"));
        exact.sort(byLabel);
        word.sort(byLabel);
        exact.addAll(word);
        return exact;
    }

    /** A label to look for in the items' names, and the concept it labels. */
    private record Search(Json.TermLabel label, Concept concept) {}

    private Json.Term term(Concept c, String match) {
        List<Concept> family = withNarrower(c);
        List<Search> search = new ArrayList<>();
        for (Concept k : family) k.all().forEach(l -> search.add(new Search(l, k)));
        List<Json.TermPart> parts = new ArrayList<>();
        for (Item item : items) {
            // The site's own word first: the label an engineer of that site would recognise.
            String own = SITE_LANGUAGE.get(Atelier.plmOf(item.resource().getURI()));
            search.stream().sorted(Comparator.comparing((Search s) -> !s.label().lang().equals(own)))
                    .filter(s -> holds(item.byLanguage().get(s.label().lang()), s.label())
                            && !shadowed(item.byLanguage().get(s.label().lang()), s.label(), family)).findFirst()
                    .ifPresent(s -> parts.add(new Json.TermPart(Atelier.nativeId(item.resource().getURI()),
                            Atelier.plmOf(item.resource().getURI()), item.product(), item.name(),
                            item.nameEn() == null || item.nameEn().equals(item.name()) ? null : item.nameEn(), s.label(),
                            s.concept() == c ? null : s.concept().labels().get("en"))));
        }
        return new Json.Term(c.resource().getURI(), match, c.labels(), c.altLabels(), c.broader(), parts);
    }

    /**
     * True when {@code name} holds {@code label} only as part of a longer label of a concept outside {@code family}: the
     * screw of "Aerial screw kit" is the aerial screw, not a fastener.
     */
    private boolean shadowed(String name, Json.TermLabel label, List<Concept> family) {
        List<String> inner = words(label.label());
        for (Concept other : concepts.values()) {
            if (family.contains(other)) continue;
            for (Json.TermLabel longer : other.all()) {
                if (!longer.lang().equals(label.lang())) continue;
                List<String> outer = words(longer.label());
                boolean within = "de".equals(label.lang()) ? String.join(" ", outer).contains(String.join(" ", inner))
                        : contains(outer, inner, false);
                if (!outer.equals(inner) && within && holds(name, longer)) return true;
            }
        }
        return false;
    }

    /** The concept and every concept below it, by skos:broader. */
    private List<Concept> withNarrower(Concept top) {
        Set<Concept> out = new LinkedHashSet<>(List.of(top));
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Concept c : concepts.values()) {
                if (c.broader() != null && out.stream().anyMatch(o -> o.resource().getURI().equals(c.broader()))) grew |= out.add(c);
            }
        }
        return List.copyOf(out);
    }

    private static boolean holds(String name, Json.TermLabel label) {
        if (name == null) return false;
        if ("de".equals(label.lang())) return String.join(" ", words(name)).contains(String.join(" ", words(label.label())));
        return contains(words(name), words(label.label()), true);
    }

    /** True when {@code words} appear in {@code text} in order and side by side; with {@code plural}, each may end in s or es. */
    private static boolean contains(List<String> text, List<String> words, boolean plural) {
        for (int i = 0; i + words.size() <= text.size(); i++) {
            boolean all = true;
            for (int j = 0; j < words.size() && all; j++) {
                String t = text.get(i + j);
                String w = words.get(j);
                all = t.equals(w) || plural && (t.equals(w + "s") || t.equals(w + "es"));
            }
            if (all) return true;
        }
        return false;
    }

    /** Lower-case words without accents. */
    static List<String> words(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return Arrays.stream(plain.split("[^\\p{L}\\p{N}]+")).filter(w -> !w.isEmpty()).toList();
    }

    private static List<String> literal(Resource subject, Property property, String lang) {
        return subject.listProperties(property).toList().stream().map(Statement::getObject)
                .filter(RDFNode::isLiteral).map(RDFNode::asLiteral).filter(l -> lang.equals(l.getLanguage()))
                .map(l -> l.getString()).toList();
    }
}
