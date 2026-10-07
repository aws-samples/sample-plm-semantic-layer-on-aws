// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.mapping.EquivalentsMapper;
import atelier.query.mapping.ItemClasses;
import atelier.query.mapping.Units;
import atelier.query.policy.Caller;
import atelier.query.validation.RuleValidator;
import java.io.StringReader;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

/**
 * Equivalence by item class over fixtures/item-classes.ttl: O-rings and seat-row placards that three sites buy under
 * their own numbers, units and languages, and one near miss per identifying attribute. The ontology's ItemClasses scheme
 * names the attributes, their tolerances and the schemes their texts resolve in; a class added to the scheme is grouped
 * with no code.
 */
class ItemClassesTest {
    static final String PRODUCT = "cabin-kit";
    static final Caller UNKNOWN = Caller.user(PurchasingTest.POLICY.profile("unknown"));
    static final Caller UK = Caller.user(PurchasingTest.POLICY.profile("uk-engineer"));
    static final String MATERIALS = Atelier.ONT + "Materials";

    private final RuleValidator validator = new RuleValidator();
    private final QueryService service = new QueryService(new FixtureFederator(fixture()), validator, new NoOntopSql(),
            new CadUrls(null, null));

    static Model fixture() {
        Model model = FixtureFederator.fixture();
        RDFDataMgr.read(model, "fixtures/item-classes.ttl");
        return model;
    }

    private Json.EquivalentGroup group(Caller caller, String itemClass) {
        return service.equivalents(caller, PRODUCT).groups().stream().filter(g -> g.itemClass().equals(itemClass)).findFirst().orElseThrow();
    }

    private List<String> grouped() {
        return service.equivalents(UNKNOWN, PRODUCT).groups().stream().flatMap(g -> g.members().stream()).map(Json.EquivalentMember::id).toList();
    }

    @Test
    void anORingNamedByItsDashSizeItsIsoCodeOrItsInchDimensionsIsOneItem() {
        Json.EquivalentGroup ring = group(UNKNOWN, "o-ring");
        assertThat(ring.classLabel()).isEqualTo("O-ring");
        assertThat(ring.members()).extracting(Json.EquivalentMember::plm, Json.EquivalentMember::id, Json.EquivalentMember::standard)
                .containsExactly(tuple("fr", "fr-oring", "AS568-014"), tuple("de", "de-oring", "O-ring-ISO3601-1-014A-12,42x1,78-N"),
                        tuple("uk", "uk-oring", null));
        assertThat(ring.attributes()).extracting(Json.GroupAttribute::attribute, Json.GroupAttribute::mm, Json.GroupAttribute::text)
                .containsExactly(tuple("innerDiameter", 12.42, null), tuple("crossSection", 1.78, null),
                        tuple("compound", null, "EPDM 80 Shore A, phosphate ester resistant"));
        Json.MemberValue fromStandard = ring.members().get(0).values().get(0);
        assertThat(fromStandard).as("the size concept of AS568-014 supplies the dimensions the French site does not state")
                .extracting(Json.MemberValue::stored, Json.MemberValue::unit, Json.MemberValue::fromStandard).containsExactly("12.42", "MilliM", true);
        assertThat(ring.members().get(2).values()).as("the British inches convert to millimetres")
                .extracting(Json.MemberValue::stored, Json.MemberValue::unit, Json.MemberValue::mm)
                .startsWith(tuple("0.4890", "IN", 12.4206), tuple("0.0700", "IN", 1.778));
        assertThat(ring.shelfLifeMonths()).isEqualTo(120);
        assertThat(ring.stocking()).isEqualTo(new Json.Stocking(3, 3, 3, 1));
    }

    @Test
    void anORingDiffersOnItsCompoundItsInnerDiameterOrItsCrossSection() {
        assertThat(grouped()).doesNotContain("es-oring-fkm", "de-oring-214", "uk-oring-thick");
        assertThat(grouped()).as("a compound or a dash size no concept carries cannot be compared").doesNotContain("fr-oring-silicone", "es-oring-dash");
    }

    @Test
    void theCompoundResolvesInTheThreeLanguagesWithItsQualifiers() {
        ItemClasses classes = new ItemClasses(validator.ontology());
        String compound = Atelier.ONT + "EPDM80PhosphateEsterResistant";
        assertThat(List.of("EPDM 80 Shore A, phosphatesterbeständig", "EPDM 80 Shore A, résistant aux esters phosphates",
                "EPDM 80 Shore A, phosphate ester resistant", "epdm 80 shore a, PHOSPHATE ESTER RESISTANT"))
                .allMatch(text -> compound.equals(classes.resolve(MATERIALS, text)));
        assertThat(List.of("Polycarbonatfolie", "film polycarbonate", "polycarbonate film"))
                .allMatch(text -> (Atelier.ONT + "PolycarbonateFilm").equals(classes.resolve(MATERIALS, text)));
        assertThat(List.of("Acrylat-Haftklebstoff", "adhésif acrylique", "acrylic pressure-sensitive"))
                .allMatch(text -> (Atelier.ONT + "AcrylicPressureSensitiveAdhesive").equals(classes.resolve(MATERIALS, text)));
        assertThat(classes.resolve(MATERIALS, "EPDM")).as("a compound without its hardness and qualifiers is no concept").isNull();
    }

    @Test
    void aPlacardIsOneItemAcrossLanguagesUnitsAndTheWayItsLegendIsWritten() {
        Json.EquivalentGroup placard = group(UNKNOWN, "placard");
        assertThat(placard.members()).extracting(Json.EquivalentMember::id).containsExactly("fr-placard", "de-placard", "uk-placard");
        assertThat(placard.attributes()).extracting(Json.GroupAttribute::attribute, Json.GroupAttribute::mm, Json.GroupAttribute::text)
                .containsExactly(tuple("legend", null, "14ABC"), tuple("itemWidth", 120.0, null), tuple("itemHeight", 40.0, null),
                        tuple("facestock", 0.25, "polycarbonate film"), tuple("adhesive", null, "acrylic pressure-sensitive adhesive"));
        assertThat(placard.members().get(1).values().get(0).stored()).as("legend case and spaces are ignored").isEqualTo("14 abc");
        assertThat(placard.members().get(2).values()).as("the British inches and the film thickness in inches convert")
                .extracting(Json.MemberValue::attribute, Json.MemberValue::mm)
                .contains(tuple("itemWidth", 119.9896), tuple("itemHeight", 40.005), tuple("facestock", 0.254));
        assertThat(placard.shelfLifeMonths()).isEqualTo(12);
        assertThat(placard.stocking()).isEqualTo(new Json.Stocking(3, 3, 3, 1));
    }

    @Test
    void aPlacardDiffersOnEachIdentifyingAttribute() {
        assertThat(grouped()).as("legend, width beyond 0.5 mm, height beyond 0.5 mm, film thickness")
                .doesNotContain("es-placard-14def", "de-placard-wide", "fr-placard-tall", "uk-placard-thick");
        assertThat(grouped()).as("a face material no concept carries").doesNotContain("es-placard-unknown");
    }

    @Test
    void aContainerIsOneItemAcrossItsTypeNamesUnitsAndLanguages() {
        Json.EquivalentGroup container = group(UNKNOWN, "container");
        assertThat(container.members()).extracting(Json.EquivalentMember::id).containsExactly("fr-container", "de-container", "uk-container");
        assertThat(container.attributes()).extracting(Json.GroupAttribute::attribute, Json.GroupAttribute::text)
                .startsWith(tuple("standard", "AKH")).endsWith(tuple("shellMaterial", "aluminium alloy"));
        assertThat(container.members().get(2).values()).as("the inch dimensions convert within a millimetre")
                .extracting(Json.MemberValue::attribute, Json.MemberValue::mm)
                .contains(tuple("baseWidth", 1562.1), tuple("contourWidth", 2437.892));
        assertThat(grouped()).as("another type, and a height 7 mm off").doesNotContain("es-container-ake", "de-container-tall");
    }

    @Test
    void aTyreAWheelAndABrakeAreOneItemInMillimetresAndInches() {
        Json.EquivalentGroup tyre = group(UNKNOWN, "tyre");
        assertThat(tyre.members()).extracting(Json.EquivalentMember::id).containsExactly("fr-tyre", "uk-tyre");
        assertThat(tyre.members().get(1).values()).as("46 x 17.0 R20 in is 1168.4 x 431.8 mm on a 508 mm rim")
                .extracting(Json.MemberValue::attribute, Json.MemberValue::mm)
                .startsWith(tuple("outerDiameter", 1168.4), tuple("sectionWidth", 431.8), tuple("rimDiameter", 508.0));
        assertThat(tyre.shelfLifeMonths()).isEqualTo(60);
        assertThat(group(UNKNOWN, "wheel").members()).extracting(Json.EquivalentMember::id).containsExactly("fr-wheel", "uk-wheel");
        assertThat(group(UNKNOWN, "brake").members()).extracting(Json.EquivalentMember::id).containsExactly("fr-brake", "uk-brake");
        assertThat(grouped()).as("another ply rating, a nose tyre, a narrower wheel, another rotor count")
                .doesNotContain("de-tyre-plies", "es-tyre-nose", "de-wheel-narrow", "es-brake-five");
    }

    @Test
    void theStockingLineCountsTheVisiblePartNumbersAndTheShortestShelfLife() {
        Json.EquivalentGroup placard = group(UK, "placard");
        assertThat(placard.members()).extracting(Json.EquivalentMember::id).contains("uk-placard-spare");
        assertThat(placard.stocking()).as("four part numbers in three sites").isEqualTo(new Json.Stocking(4, 3, 4, 1));
        assertThat(placard.shelfLifeMonths()).as("the spares' six months").isEqualTo(6);
    }

    @Test
    void aClassAddedToTheSchemeIsGroupedWithNoCode() {
        Model ontology = ModelFactory.createDefaultModel().add(validator.ontology());
        ontology.read(new StringReader("""
                @prefix atelier: <https://example.com/atelier/ontology#> .
                @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
                atelier:BearingItem a skos:Concept ; skos:inScheme atelier:ItemClasses ; skos:notation "bearing" ;
                  skos:prefLabel "Bearing"@en ; atelier:identifiedBy atelier:BearingBore, atelier:BearingSeal .
                atelier:BearingBore atelier:onAttribute atelier:innerDiameter ; atelier:attributeOrder 1 ; atelier:matchToleranceMm 0.01 .
                atelier:BearingSeal atelier:onAttribute atelier:compound ; atelier:attributeOrder 2 ; atelier:resolvedIn atelier:Materials .
                """), null, "TURTLE");
        Model parts = ModelFactory.createDefaultModel().read(new StringReader("""
                @prefix atelier: <https://example.com/atelier/ontology#> .
                @prefix qudt: <http://qudt.org/schema/qudt/> .
                @prefix unit: <http://qudt.org/vocab/unit/> .
                <https://example.com/atelier/de/part/de-bearing> a atelier:Part ; atelier:label "Rillenkugellager 6001-2RS" ;
                  atelier:itemClass "bearing" ; atelier:innerDiameter <https://example.com/atelier/de/part/de-bearing/inner-diameter> ;
                  atelier:compound "NBR 70 Shore A, mineralölbeständig" .
                <https://example.com/atelier/de/part/de-bearing/inner-diameter> qudt:numericValue 12.000 ; qudt:unit unit:MilliM .
                <https://example.com/atelier/uk/part/uk-bearing> a atelier:Part ; atelier:label "Ball bearing 6001-2RS" ;
                  atelier:itemClass "bearing" ; atelier:innerDiameter <https://example.com/atelier/uk/part/uk-bearing/inner-diameter> ;
                  atelier:compound "NBR 70 Shore A, mineral oil resistant" .
                <https://example.com/atelier/uk/part/uk-bearing/inner-diameter> qudt:numericValue 0.4724 ; qudt:unit unit:IN .
                """), null, "TURTLE");
        List<Json.EquivalentGroup> groups = new EquivalentsMapper(parts, new Units(validator.units()), new ItemClasses(ontology)).groups();
        assertThat(groups).extracting(Json.EquivalentGroup::itemClass, Json.EquivalentGroup::classLabel, g -> g.members().size())
                .containsExactly(tuple("bearing", "Bearing", 2));
        assertThat(groups.get(0).attributes()).extracting(Json.GroupAttribute::attribute).containsExactly("innerDiameter", "compound");
    }
}
