# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""data/purchasing.py: a purchased block is written into its site's columns, and the items a product buys under several
part numbers are found from the identifying attributes ontology/atelier.ttl names per class, across the sites' units and
languages. The placard texts are those the sites write, on fictional part ids."""

import sys
import unittest
from decimal import Decimal
from pathlib import Path

DATA = Path(__file__).resolve().parents[2] / "data"
sys.path.insert(0, str(DATA))
import purchasing  # noqa: E402
import ttl  # noqa: E402


class Refused(Exception):
    pass


def fail(message):
    raise Refused(message)


def part(pid, plm, purchased):
    return {"id": pid, "plm": plm, "name": pid, "extended": {"partType": "PURCHASED", "purchased": purchased}}


def placard(pid, plm, legend="14ABC", width="120", height="40", material="film polycarbonate 0,25 mm",
            adhesive="adhésif acrylique", **block):
    return part(pid, plm, {"class": "placard", "attributes": {"legend": legend, "width": width, "height": height,
                                                              "material": material, "adhesive": adhesive}, **block})


def ids(found, item_class):
    return [[p["id"] for p in g] for c, g in found if c == item_class and len(g) > 1]


CLASSES = purchasing.item_classes()
PLACARDS = [
    placard("FR-P-1", "FR", shelfLifeMonths=12),
    placard("DE-P-1", "DE", legend="14 abc", material="Polycarbonatfolie 0,25 mm", adhesive="Acrylat-Haftklebstoff", shelfLifeMonths=12),
    placard("UK-P-1", "UK", width="4.724", height="1.575", material="polycarbonate film 0.010 in", adhesive="acrylic pressure-sensitive"),
    placard("ES-P-1", "ES", legend="14DEF", material="película de policarbonato 0,25 mm", adhesive="adhesivo acrílico"),
    placard("DE-P-2", "DE", width="121", material="Polycarbonatfolie 0,25 mm", adhesive="Acrylat-Haftklebstoff"),
    placard("FR-P-2", "FR", height="40.6"),
    placard("UK-P-2", "UK", width="4.724", height="1.575", material="polycarbonate film 0.020 in", adhesive="acrylic pressure-sensitive"),
    placard("ES-P-2", "ES", material="vinilo 0,10 mm", adhesive="adhesivo acrílico"),
]


def written(p, classes=None, columns=None):
    """The non-empty columns `description` writes for a part, by column name."""
    columns = columns or purchasing.site_columns(p["plm"])
    values = purchasing.description(p, fail, classes, columns)
    return {name: v for name, v in zip(columns.names().split(", "), values) if v is not None}


class DescriptionTest(unittest.TestCase):
    def test_each_class_writes_its_attributes_into_the_columns_the_site_maps(self):
        uk = part("UK-R-1", "UK", {"class": "o-ring", "attributes": {"innerDiameter": "0.489", "crossSection": "0.070",
                                                                      "compound": "EPDM 80 Shore A, phosphate ester resistant"},
                                   "shelfLifeMonths": 120})
        self.assertEqual(written(uk), {"item_class": "o-ring", "inside_dia": Decimal("0.489"), "cross_section": Decimal("0.070"),
                                       "compound": "EPDM 80 Shore A, phosphate ester resistant", "shelf_life_months": 120,
                                       "size_uom": "IN"})
        self.assertEqual(written(placard("FR-P-1", "FR", shelfLifeMonths=12)),
                         {"classe_article": "placard", "legende": "14ABC", "largeur_mm": Decimal("120"), "hauteur_mm": Decimal("40"),
                          "support": "film polycarbonate 0,25 mm", "adhesif": "adhésif acrylique", "duree_stockage_mois": 12})
        screw = part("ES-S-1", "ES", {"class": "fastener", "standard": "UNE-EN ISO 4762", "unit": "IN",
                                      "attributes": {"diameter": "0.118", "length": "0.394"}})
        self.assertEqual(written(screw), {"norma": "UNE-EN ISO 4762", "diametro_nominal": Decimal("0.118"),
                                          "longitud_nominal": Decimal("0.394"), "clase_articulo": "fastener", "unidad_medida": "IN"})
        self.assertEqual(written({"id": "D-1", "plm": "DE", "extended": {}}), {})

    def test_a_class_added_only_to_the_vocabulary_is_written_and_grouped_with_no_code(self):
        extra = ttl.parse("""
            @prefix atelier: <https://example.com/atelier/ontology#> .
            @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
            atelier:CordItem a skos:Concept ; skos:inScheme atelier:ItemClasses ; skos:notation "cord" ;
              atelier:identifiedBy atelier:CordDiameter, atelier:CordMaterial .
            atelier:CordDiameter atelier:onAttribute atelier:crossSection ; atelier:attributeOrder 1 ;
              atelier:stagingKey "diameter" ; atelier:matchToleranceMm 0.1 .
            atelier:CordMaterial atelier:onAttribute atelier:compound ; atelier:attributeOrder 2 ; atelier:stagingKey "fibre" .
        """)
        classes = purchasing.ItemClasses(ttl.parse(purchasing.ONTOLOGY.read_text(encoding="utf-8")) + extra)
        cords = [part("FR-K-1", "FR", {"class": "cord", "attributes": {"diameter": "3.2", "fibre": "soie"}, "shelfLifeMonths": 36}),
                 part("UK-K-1", "UK", {"class": "cord", "attributes": {"diameter": "0.126", "fibre": "SOIE"}}),
                 part("DE-K-1", "DE", {"class": "cord", "attributes": {"diameter": "4", "fibre": "Seide"}})]
        fr_columns = purchasing.SiteColumns("FR", classes)
        self.assertEqual(written(cords[0], classes, fr_columns),
                         {"classe_article": "cord", "section_mm": Decimal("3.2"), "melange": "soie", "duree_stockage_mois": 36})
        found = purchasing.groups({"parts": cords}, classes, fail)
        self.assertEqual(ids(found, "cord"), [["FR-K-1", "UK-K-1"]], "0.126 in is 3.2 mm; the text compares case-blind")

    def test_a_block_outside_the_staging_shape_is_refused(self):
        with self.assertRaisesRegex(Refused, "not one of brake, canister, container, cord, fastener, o-ring, placard, tyre, wheel"):
            purchasing.description(part("D-1", "DE", {"class": "gasket"}), fail)
        with self.assertRaisesRegex(Refused, "unknown keys attributes.colour"):
            purchasing.description(part("D-1", "DE", {"class": "placard", "attributes": {"colour": "red"}}), fail)
        with self.assertRaisesRegex(Refused, "DE stores atelier:innerDiameter in MilliM, not IN"):
            purchasing.description(part("D-1", "DE", {"class": "o-ring", "unit": "IN", "attributes": {"innerDiameter": "0.489"}}), fail)
        with self.assertRaisesRegex(Refused, "needs partType PURCHASED"):
            purchasing.description({"id": "D-1", "plm": "DE", "extended": {"purchased": {"class": "o-ring"}}}, fail)
        with self.assertRaisesRegex(Refused, "positive number of months"):
            purchasing.description(part("D-1", "DE", {"class": "o-ring", "shelfLifeMonths": 0}), fail)


class GroupsTest(unittest.TestCase):
    def test_a_placard_is_one_item_across_languages_units_and_legend_spelling(self):
        found = purchasing.groups({"parts": PLACARDS}, CLASSES, fail)
        self.assertEqual(ids(found, "placard"), [["FR-P-1", "DE-P-1", "UK-P-1"]])

    def test_a_placard_differs_on_each_identifying_attribute(self):
        grouped = {pid for g in ids(purchasing.groups({"parts": PLACARDS}, CLASSES, fail), "placard") for pid in g}
        self.assertFalse(grouped & {"ES-P-1", "DE-P-2", "FR-P-2", "UK-P-2"}, "legend, width, height, film thickness")
        self.assertNotIn("ES-P-2", grouped, "a face material no concept carries")

    def test_an_o_ring_is_one_item_by_dash_size_iso_code_or_inch_dimensions(self):
        rings = [
            part("FR-R-1", "FR", {"class": "o-ring", "standard": "AS568-014",
                                  "attributes": {"compound": "EPDM 80 Shore A, résistant aux esters phosphates"}}),
            part("DE-R-1", "DE", {"class": "o-ring", "standard": "O-ring-ISO3601-1-014A-12,42x1,78-N",
                                  "attributes": {"compound": "EPDM 80 Shore A, phosphatesterbeständig"}}),
            part("UK-R-1", "UK", {"class": "o-ring", "attributes": {"innerDiameter": "0.489", "crossSection": "0.070",
                                                                     "compound": "EPDM 80 Shore A, phosphate ester resistant"}}),
            part("ES-R-1", "ES", {"class": "o-ring", "attributes": {"innerDiameter": "12.42", "crossSection": "1.78",
                                                                     "compound": "FKM 75 Shore A"}}),
            part("UK-R-2", "UK", {"class": "o-ring", "attributes": {"innerDiameter": "0.489", "crossSection": "0.103",
                                                                     "compound": "EPDM 80 Shore A, phosphate ester resistant"}}),
        ]
        self.assertEqual(ids(purchasing.groups({"parts": rings}, CLASSES, fail), "o-ring"), [["FR-R-1", "DE-R-1", "UK-R-1"]])

    def test_a_container_is_one_item_across_its_type_names_and_inches(self):
        def container(pid, plm, standard, height="1143", material="Aluminiumlegierung", **dims):
            attributes = {"baseWidth": "1562", "depth": "1534", "height": height, "contourWidth": "2438", "material": material}
            return part(pid, plm, {"class": "container", "standard": standard, "attributes": {**attributes, **dims}})
        found = purchasing.groups({"parts": [
            container("FR-C-1", "FR", "AKH", material="alliage d'aluminium"),
            container("DE-C-1", "DE", "LD3-45W"),
            container("UK-C-1", "UK", "IATA AKH", baseWidth="61.50", depth="60.39", height="45.00", contourWidth="95.98",
                      material="aluminium alloy"),
            container("ES-C-1", "ES", "AKE", material="aleación de aluminio"),
            container("DE-C-2", "DE", "LD3-45W", height="1150"),
        ]}, CLASSES, fail)
        self.assertEqual(ids(found, "container"), [["FR-C-1", "DE-C-1", "UK-C-1"]])

    def test_a_cord_compares_the_part_s_own_material_as_the_query_service_does(self):
        def cord(pid, plm, diameter, material):
            p = part(pid, plm, {"class": "cord", "attributes": {"diameter": diameter}, "shelfLifeMonths": 36})
            p["extended"]["material"] = material
            return p
        found = purchasing.groups({"parts": [cord("ES-K-1", "ES", "10", "seda cruda"),
                                             cord("UK-K-1", "UK", "0.394", "raw silk"),
                                             cord("DE-K-1", "DE", "10", "Rohseide"),
                                             cord("DE-K-2", "DE", "10", "Polyethylen"),
                                             cord("FR-K-1", "FR", "10", "chanvre")]}, CLASSES, fail)
        self.assertEqual(ids(found, "cord"), [["ES-K-1", "UK-K-1", "DE-K-1"]],
                         "one raw silk item in three languages; polyethylene is another item")
        self.assertNotIn("FR-K-1", {p["id"] for _, g in found for p in g},
                         "a material the vocabulary does not name leaves the part out, as the query service does")

    def test_a_tyre_is_one_item_in_millimetres_and_inches_and_differs_on_its_ply_rating(self):
        def tyre(pid, plm, outer, section, rim, plies):
            return part(pid, plm, {"class": "tyre", "attributes": {"outerDiameter": outer, "sectionWidth": section,
                                                                   "rimDiameter": rim, "plyRating": plies}, "shelfLifeMonths": 60})
        found = purchasing.groups({"parts": [tyre("FR-T-1", "FR", "1168.4", "431.8", "508", "30"),
                                             tyre("UK-T-1", "UK", "46.00", "17.00", "20.00", "30"),
                                             tyre("DE-T-1", "DE", "1168.4", "431.8", "508", "28")]}, CLASSES, fail)
        self.assertEqual(ids(found, "tyre"), [["FR-T-1", "UK-T-1"]])
        with self.assertRaisesRegex(Refused, "not a positive whole number"):
            purchasing.description(tyre("FR-T-2", "FR", "1168.4", "431.8", "508", "30.5"), fail)

    def test_a_wheel_and_a_brake_are_one_item_across_units(self):
        found = purchasing.groups({"parts": [
            part("FR-W-1", "FR", {"class": "wheel", "attributes": {"rimDiameter": "508", "width": "381"}}),
            part("UK-W-1", "UK", {"class": "wheel", "attributes": {"rimDiameter": "20.00", "width": "15.00"}}),
            part("FR-B-1", "FR", {"class": "brake", "attributes": {"heatStackDiameter": "439.42", "rotors": "4"}}),
            part("UK-B-1", "UK", {"class": "brake", "attributes": {"heatStackDiameter": "17.30", "rotors": "4"}}),
            part("ES-B-1", "ES", {"class": "brake", "attributes": {"heatStackDiameter": "439.42", "rotors": "5"}}),
        ]}, CLASSES, fail)
        self.assertEqual((ids(found, "wheel"), ids(found, "brake")), ([["FR-W-1", "UK-W-1"]], [["FR-B-1", "UK-B-1"]]))

    def test_the_vocabulary_resolves_the_three_languages(self):
        materials = purchasing.ONT + "Materials"
        for words, concept in ((("Polycarbonatfolie", "film polycarbonate", "polycarbonate film"), "PolycarbonateFilm"),
                               (("Acrylat-Haftklebstoff", "adhésif acrylique", "acrylic pressure-sensitive"), "AcrylicPressureSensitiveAdhesive"),
                               (("EPDM 80 Shore A, phosphatesterbeständig", "EPDM 80 Shore A, résistant aux esters phosphates",
                                 "EPDM 80 Shore A, phosphate ester resistant"), "EPDM80PhosphateEsterResistant")):
            for word in words:
                self.assertEqual(CLASSES.resolve(materials, word), purchasing.ONT + concept, word)

    def test_a_class_added_to_the_scheme_is_grouped_with_no_code(self):
        extra = ttl.parse("""
            @prefix atelier: <https://example.com/atelier/ontology#> .
            @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
            atelier:LegendOnly a skos:Concept ; skos:inScheme atelier:ItemClasses ; skos:notation "placard" ;
              atelier:identifiedBy atelier:LegendOnlyLegend .
            atelier:LegendOnlyLegend atelier:onAttribute atelier:legend ; atelier:attributeOrder 1 ; atelier:stagingKey "legend" .
        """)
        triples = [t for t in ttl.parse(purchasing.ONTOLOGY.read_text(encoding="utf-8")) if t[0] != purchasing.ONT + "PlacardItem"]
        classes = purchasing.ItemClasses(triples + extra)
        legends = [part(p["id"], p["plm"], {"class": "placard", "attributes": {"legend": p["extended"]["purchased"]["attributes"]["legend"]}})
                   for p in PLACARDS]
        found = purchasing.groups({"parts": legends}, classes, fail)
        self.assertEqual(ids(found, "placard"), [["FR-P-1", "DE-P-1", "UK-P-1", "DE-P-2", "FR-P-2", "UK-P-2", "ES-P-2"]],
                         "a class identified by its legend alone groups every 14ABC placard")

    def test_the_figure_states_the_stocking_line_and_the_shortest_shelf_life(self):
        lines = purchasing.figure({"parts": PLACARDS}, CLASSES, fail)
        self.assertIn("    placard FR-P-1, DE-P-1, UK-P-1: 3 part numbers in 3 sites (DE, FR, UK), shelf life 12 months; "
                      "one stock line once confirmed", lines)


if __name__ == "__main__":
    unittest.main()
