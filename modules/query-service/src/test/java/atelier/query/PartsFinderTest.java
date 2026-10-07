// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import atelier.query.api.Json;
import atelier.query.mapping.PartsFinder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The parts a query names over a small bill of materials: a two-winged machine whose left wing kit is British and right
 * wing kit German, a French frame kit holding the wing roots, a Spanish wheel kit with a complete wheel inside, one
 * hidden item and one software item.
 */
class PartsFinderTest {
    private static Json.BomNode node(String id, String plm, String name, String nameEn, String type, double occurrences,
                                     Json.BomItem... children) {
        return new Json.BomNode(id, plm, name, nameEn, type, null, null, null, 1, occurrences, null, null,
                children.length == 0 ? null : List.of(children));
    }

    private static Json.BomNode part(String id, String plm, String name, String nameEn) {
        return node(id, plm, name, nameEn, "PART", 1);
    }

    static final Json.BomNode ROOT = node("machine", null, "Flying machine", null, "PRODUCT", 1,
            node("UK-KIT", "uk", "Kit, left wing", "Kit, left wing", "ASSEMBLY", 1,
                    part("UK-SPAR", "uk", "Left wing spar", "Left wing spar"),
                    part("UK-LACE", "uk", "Left lacing cord", "Left lacing cord"),
                    part("UK-SCREW", "uk", "Cap screw M3", "Cap screw M3")),
            node("DE-KIT", "de", "Bausatz rechter Flügel", "Kit, right wing", "ASSEMBLY", 1,
                    part("DE-HOLM", "de", "Rechter Flügelholm", "Right wing spar"),
                    part("DE-MOTOR", "de", "Getriebemotor 12 V", "Gearmotor 12 V"),
                    part("DE-SPINDEL", "de", "Spindel", "Spindle"),
                    new Json.BomHidden(true, "de", 1, 1)),
            node("FR-KIT", "fr", "Kit structure", "Frame kit", "ASSEMBLY", 1,
                    part("FR-ROOT", "fr", "Ferrure d'emplanture d'aile", "Wing root fitting"),
                    part("FR-SCREW", "fr", "Vis CHC M3", "Socket head cap screw M3"),
                    node("FR-SW", "fr", "Programme de vol", "Flight program", "SOFTWARE", 1)),
            node("ES-KIT", "es", "Kit de ruedas y carrocería", "Wheels and covers kit", "ASSEMBLY", 1,
                    node("ES-WHEEL", "es", "Rueda completa", "Complete wheel", "ASSEMBLY", 6,
                            part("ES-RIM", "es", "Llanta", "Rim"),
                            part("ES-SCREW", "es", "Tornillo M3", "Screw M3")),
                    part("ES-COVER", "es", "Tapa", "Cover")));

    private static Json.FoundParts find(String query, Map<String, List<Json.Term>> terms) {
        return new PartsFinder(query, terms).find("machine", query, ROOT, new Json.Provenance(List.of()), "", new Json.Timings(0, 0, 0), null);
    }

    private static Json.FoundParts find(String query) {
        return find(query, Map.of());
    }

    private static List<String> ids(Json.FoundGroup group) {
        return group.parts().stream().map(Json.FoundItem::id).toList();
    }

    @Test
    void anAssemblyWhoseNameHoldsTheWordsStandsForItsPartsAndAPluralFindsTheSingular() {
        Json.FoundParts found = find("both wings");

        assertThat(found.groups()).filteredOn(g -> g.match().equals("assembly"))
                .extracting(g -> g.assembly().id(), PartsFinderTest::ids)
                .containsExactly(tuple("UK-KIT", List.of("UK-SPAR", "UK-LACE", "UK-SCREW")),
                        tuple("DE-KIT", List.of("DE-HOLM", "DE-MOTOR", "DE-SPINDEL")));
        assertThat(found.groups()).filteredOn(g -> g.match().equals("assembly")).extracting(Json.FoundGroup::matched)
                .as("the British kit by its own name, the German kit by its English name").containsExactly(
                        new Json.FoundMatch("name", "wings", "en", null, null), new Json.FoundMatch("nameEn", "wings", "en", null, null));
        assertThat(found.groups()).filteredOn(g -> g.match().equals("parts")).singleElement().satisfies(g -> {
            assertThat(g.assembly().id()).as("a part outside the matching assemblies joins its parent's group").isEqualTo("FR-KIT");
            assertThat(ids(g)).containsExactly("FR-ROOT");
            assertThat(g.matched()).as("the leading quantifier is not part of what is named")
                    .isEqualTo(new Json.FoundMatch("nameEn", "wings", "en", null, null));
        });
        assertThat(found.hidden()).as("the hidden item of the right wing kit").isEqualTo(1);
    }

    @Test
    void theInnermostMatchingAssemblyIsTheGroupWithTheOccurrencesOfItsParts() {
        Json.FoundParts found = find("wheels");

        assertThat(found.groups()).singleElement().satisfies(g -> {
            assertThat(g.assembly().id()).as("the complete wheel, not the wheels and covers kit").isEqualTo("ES-WHEEL");
            assertThat(ids(g)).containsExactly("ES-RIM", "ES-SCREW");
        });
    }

    @Test
    void alternativesFindAFamilyAndEachPartIsListedOnce() {
        Json.FoundParts found = find("screw, nut");

        assertThat(found.groups()).extracting(g -> g.assembly().id()).containsExactly("UK-KIT", "FR-KIT", "ES-WHEEL");
        assertThat(found.groups()).flatExtracting(PartsFinderTest::ids).containsExactly("UK-SCREW", "FR-SCREW", "ES-SCREW");
        assertThat(found.groups()).extracting(Json.FoundGroup::matched).containsExactly(new Json.FoundMatch("name", "screw", "en", null, null),
                new Json.FoundMatch("nameEn", "screw", "en", null, null), new Json.FoundMatch("nameEn", "screw", "en", null, null));
        assertThat(found.groups().get(0).parts().get(0).nameEn()).as("a British name is its English name").isNull();
        assertThat(found.groups().get(1).parts().get(0).nameEn()).isEqualTo("Socket head cap screw M3");
    }

    @Test
    void aGermanWordFindsAGermanCompoundAndAShortWordMustStandAlone() {
        assertThat(find("Getriebemotoren").groups()).singleElement().satisfies(g -> {
            assertThat(ids(g)).containsExactly("DE-MOTOR");
            assertThat(g.matched()).as("found in the German site's own name").isEqualTo(new Json.FoundMatch("name", "Getriebemotoren", "de", null, null));
        });
        assertThat(find("Flügel").groups()).filteredOn(g -> g.match().equals("assembly")).extracting(g -> g.assembly().id())
                .containsExactly("DE-KIT");
        assertThat(find("pin").groups()).as("pin is not the syllable of Spindel").isEmpty();
    }

    @Test
    void aGlossaryTermFindsTheItemsOfEverySiteAndAPartTypeFindsItsItems() {
        Json.Term spar = new Json.Term("https://example.com/atelier/term/spar", "exact", Map.of("en", "spar", "de", "Holm"), List.of(), null,
                List.of(new Json.TermPart("UK-SPAR", "uk", "machine", "Left wing spar", null, new Json.TermLabel("en", "spar"), null),
                        new Json.TermPart("DE-HOLM", "de", "machine", "Rechter Flügelholm", "Right wing spar", new Json.TermLabel("de", "Holm"), null)));
        Json.FoundParts found = find("longeron", Map.of("longeron", List.of(spar)));

        assertThat(found.groups()).extracting(g -> g.assembly().id(), Json.FoundGroup::matched).as("each site's label, in its language")
                .containsExactly(tuple("UK-KIT", new Json.FoundMatch("term", "spar", "en", "spar", null)),
                        tuple("DE-KIT", new Json.FoundMatch("term", "Holm", "de", "spar", null)));
        assertThat(found.groups()).flatExtracting(PartsFinderTest::ids).containsExactly("UK-SPAR", "DE-HOLM");
        assertThat(find("software").groups()).singleElement().satisfies(g -> {
            assertThat(g.matched()).isEqualTo(new Json.FoundMatch("partType", "software", null, null, null));
            assertThat(ids(g)).containsExactly("FR-SW");
        });
    }

    @Test
    void aPartFoundThroughANarrowerTermNamesIt() {
        Json.Term fastener = new Json.Term("https://example.com/atelier/term/fastener", "exact", Map.of("en", "fastener"), List.of(), null,
                List.of(new Json.TermPart("FR-SCREW", "fr", "machine", "Vis CHC M3", "Socket head cap screw M3", new Json.TermLabel("fr", "vis"),
                        "screw")));

        assertThat(find("fasteners", Map.of("fasteners", List.of(fastener))).groups()).singleElement()
                .satisfies(g -> assertThat(g.matched()).isEqualTo(new Json.FoundMatch("term", "vis", "fr", "fastener", "screw")));
    }

    @Test
    void aQueryIsSplitIntoAlternativesWithoutLeadingArticles() {
        assertThat(PartsFinder.alternatives("the screws, les écrous; die Scheiben, ,both")).containsExactly("screws", "écrous", "Scheiben", "both");
        assertThat(PartsFinder.singular("roues dentées")).isEqualTo("roue dentee");
    }
}
