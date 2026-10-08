# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""data/stations.py: a part's span is its box along the station axis, a placed occurrence's span is the extent of the
eight box corners moved by its world transform (a rotated occurrence gets its true envelope), British spans are stored
in inches; stations, sections, bounds.json and authored spans are refused when they break the contract."""

import copy
import hashlib
import json
import sys
import tempfile
import unittest
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "data"))
import bom  # noqa: E402
import placements  # noqa: E402
import stations  # noqa: E402


class Refused(Exception):
    pass


def fail(message):
    raise Refused(message)


STATIONS = {"key": "WS", "name": "wing station", "axis": "y", "symmetric": True, "measure": "mm from the centreline",
            "list": [{"id": "WS 0", "at": "0", "basis": "inferred", "source": "the keel"},
                     {"id": "WS 100", "at": "100", "basis": "joint", "source": "the root joint"},
                     {"id": "WS 200", "at": "200", "basis": "measured", "source": "a model", "tolerance": "5"}]}
SECTIONS = [{"id": "S-C", "name": "Centre", "owner": "FR", "side": "both", "stations": ["WS 0", "WS 100"]},
            {"id": "S-R", "name": "Right wing", "owner": "DE", "side": "right", "stations": ["WS 100", "WS 200"]}]
# P is a box 100 to 110 along x, 0 to 20 along y; Q a box 0 to 10 along x, 40 to 60 along y.
BOXES = {"P": ((100.0, 0.0, 0.0), (110.0, 20.0, 1.0)), "Q": ((0.0, 40.0, 0.0), (10.0, 60.0, 1.0))}


def product(plm, lines, extended_extra=None):
    """Kit K > assembly A > parts P and Q, all of one site, with stations and sections."""
    extended = {"assemblies": [{"id": "K", "plm": plm, "name": "Kit", "kind": "SITE_KIT"},
                               {"id": "A", "plm": plm, "name": "A", "kind": "ASSEMBLY"}],
                "bomLines": lines, "stations": copy.deepcopy(STATIONS), "sections": copy.deepcopy(SECTIONS)}
    extended.update(extended_extra or {})
    return {"product": {"key": "kite", "name": "Kite"}, "file": "kite.json",
            "parts": [{"id": "P", "plm": plm, "cadPart": "p"}, {"id": "Q", "plm": plm, "cadPart": "q"}], "extended": extended}


def lines(a=None, p=None, quantities=(1, 1)):
    out = [{"parent": "K", "child": "A", "quantity": quantities[0]}, {"parent": "A", "child": "P", "quantity": quantities[1]},
           {"parent": "K", "child": "Q", "quantity": 1}]
    for line, given in zip(out, (a, p)):
        if given is not None:
            line["placements"] = given
    return out


def build(data):
    tree = bom.Bom(data, fail)
    placements.check(tree, fail)
    return tree, stations.Stations(data, tree, BOXES, fail)


class SpanTest(unittest.TestCase):
    def test_a_part_spans_its_box_along_the_axis(self):
        _, s = build(product("DE", lines()))
        self.assertEqual(s.part_spans, {"P": (0.0, 20.0), "Q": (40.0, 60.0)})
        self.assertEqual(s.part_columns({"id": "P"}, "DE"), [Decimal("0.000"), Decimal("20.000")])

    def test_a_rotated_occurrence_spans_the_eight_corners_moved_by_its_placement(self):
        # A quarter turn about z maps (x, y) to (-y, x): the second occurrence spans the box's x extent along y.
        _, s = build(product("DE", lines(p=[[0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 90]], quantities=(1, 2))))
        self.assertEqual(s.row_spans[("A", "P", 1)], (0.0, 20.0))
        low, high = s.row_spans[("A", "P", 2)]
        self.assertAlmostEqual(low, 100.0, places=9)
        self.assertAlmostEqual(high, 110.0, places=9)
        self.assertEqual(s.row_columns("A", "P", 2, "DE"), [Decimal("100.000"), Decimal("110.000")])

    def test_a_turn_about_the_axis_itself_leaves_the_span(self):
        _, s = build(product("FR", lines(p=[[0, 0, 0, 0, 0, 0], [0, 500, 0, 0, 45, 0]], quantities=(1, 2))))
        low, high = s.row_spans[("A", "P", 2)]
        self.assertAlmostEqual(low, 500.0, places=9)
        self.assertAlmostEqual(high, 520.0, places=9)

    def test_an_occurrence_under_a_translated_parent_is_shifted_by_the_parent(self):
        tree, s = build(product("ES", lines(a=[[0, 0, 0, 0, 0, 0], [0, 300, 0, 0, 0, 0]], quantities=(2, 1))))
        self.assertEqual(s.derived("P", tree, placements.world(tree)), [(0.0, 20.0), (300.0, 320.0)])

    def test_a_child_under_a_turned_parent_spans_its_true_envelope(self):
        # K > A (twice, the second 1000 mm along y and turned 180 degrees about z) > B (twice, 50 mm apart along y) > P:
        # the turn sends y to 1000 - y, so P's last two occurrences lie at 980 to 1000 and 930 to 950.
        data = product("DE", [{"parent": "K", "child": "A", "quantity": 2,
                               "placements": [[0, 0, 0, 0, 0, 0], [0, 1000, 0, 0, 0, 180]]},
                              {"parent": "A", "child": "B", "quantity": 2, "placements": [[0, 0, 0, 0, 0, 0], [0, 50, 0, 0, 0, 0]]},
                              {"parent": "B", "child": "P", "quantity": 1}, {"parent": "K", "child": "Q", "quantity": 1}])
        data["extended"]["assemblies"].append({"id": "B", "plm": "DE", "name": "B", "kind": "ASSEMBLY"})
        tree, s = build(data)
        derived = [tuple(round(v, 6) for v in span) for span in s.derived("P", tree, placements.world(tree))]
        self.assertEqual(derived, [(0, 20), (50, 70), (980, 1000), (930, 950)])
        exact = [stations.extent(w, BOXES["P"], 1) for w in placements.world(tree)["P"]]
        self.assertEqual([tuple(round(v, 6) for v in e) for e in exact], derived)

    def test_a_british_span_is_stored_in_inches_with_its_unit(self):
        _, s = build(product("UK", lines(p=[[0, 0, 0, 0, 0, 0], [0, 10, 0, 0, 0, 0]], quantities=(1, 2))))
        self.assertEqual(s.part_columns({"id": "Q"}, "UK"), [Decimal("1.5748"), Decimal("2.3622"), "IN"])
        self.assertEqual(s.row_columns("A", "P", 2, "UK"), [Decimal("10.0000"), Decimal("10.7874"), "IN"])
        self.assertEqual(s.part_columns({"id": "A"}, "UK"), [None, None, None])

    def test_the_spanish_document_carries_the_span_of_each_position(self):
        tree, s = build(product("ES", lines(p=[[0, 0, 0, 0, 0, 0], [0, 50, 0, 0, 0, 0]], quantities=(1, 2))))
        line = json.loads(tree.document("A", 1, lambda l: stations.positions(l, s, "ES")))["lineas"][0]
        self.assertEqual(line["posiciones"][1]["tramo_desde_mm"], 50)
        self.assertEqual(line["posiciones"][1]["tramo_hasta_mm"], 70)

    def test_a_product_without_stations_stores_no_span(self):
        data = product("FR", lines())
        del data["extended"]["stations"], data["extended"]["sections"]
        _, s = build(data)
        self.assertEqual(s.part_columns({"id": "P"}, "FR"), [None, None])
        self.assertEqual(s.links(None, None, {}, str, str), [])


class RefusalTest(unittest.TestCase):
    def refused(self, pattern, data):
        with self.assertRaisesRegex(Refused, pattern):
            build(data)

    def edited(self, edit):
        data = product("DE", lines())
        edit(data["extended"])
        return data

    def test_a_station_declared_twice(self):
        self.refused("WS 100 declared twice", self.edited(lambda e: e["stations"]["list"].append(dict(e["stations"]["list"][1]))))

    def test_an_unknown_basis(self):
        self.refused("basis 'guessed'", self.edited(lambda e: e["stations"]["list"][0].update(basis="guessed")))

    def test_a_negative_tolerance(self):
        self.refused("negative tolerance", self.edited(lambda e: e["stations"]["list"][2].update(tolerance="-1")))

    def test_a_section_on_an_unknown_station(self):
        self.refused("unknown: \\['WS 300'\\]", self.edited(lambda e: e["sections"][1].update(stations=["WS 100", "WS 300"])))

    def test_a_section_with_its_stations_out_of_order(self):
        self.refused("out of order", self.edited(lambda e: e["sections"][1].update(stations=["WS 200", "WS 100"])))

    def test_a_section_part_the_product_does_not_hold(self):
        self.refused("the part X", self.edited(lambda e: e["sections"][0].update(part="X")))

    def test_an_owner_outside_the_four_sites(self):
        self.refused("owned by 'IT'", self.edited(lambda e: e["sections"][0].update(owner="IT")))

    def test_an_authored_span_anywhere_in_the_file(self):
        data = product("DE", lines())
        data["parts"][0]["extended"] = {"stationSpan": {"from": "WS 0", "to": "WS 100"}}
        self.refused("stationSpan is computed", data)

    def test_an_occurrence_whose_span_the_stored_spans_cannot_give(self):
        # A's second occurrence turns a quarter about z: P under it is no longer A's translation of P's span.
        self.refused("occurrence 2 of P", product("DE", lines(a=[[0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 90]], quantities=(2, 1))))


class BoundsTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        (self.dir / "kite").mkdir()
        for key in ("p", "q"):
            (self.dir / "kite" / f"{key}.stp").write_text(f"STEP {key}\n")
        self.bounds = {key: {"min": [0, 0, 0], "max": [1, 1, 1],
                             "sha256": hashlib.sha256(f"STEP {key}\n".encode()).hexdigest()} for key in ("p", "q")}

    def write(self):
        (self.dir / "kite" / "bounds.json").write_text(json.dumps(self.bounds))

    def test_the_boxes_of_a_current_bounds_file(self):
        self.write()
        self.assertEqual(stations.check_bounds(product("DE", lines()), self.dir, fail)["P"], ((0, 0, 0), (1, 1, 1)))

    def test_a_step_missing_from_bounds_is_refused(self):
        del self.bounds["q"]
        self.write()
        with self.assertRaisesRegex(Refused, "q.stp is not in kite/bounds.json"):
            stations.check_bounds(product("DE", lines()), self.dir, fail)

    def test_a_step_changed_since_its_box_is_refused(self):
        self.write()
        (self.dir / "kite" / "p.stp").write_text("STEP p, moved\n")
        with self.assertRaisesRegex(Refused, "sha256 differs"):
            stations.check_bounds(product("DE", lines()), self.dir, fail)


if __name__ == "__main__":
    unittest.main()
