# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""data/functional.py: the functional edges join the product's own parts through its own interfaces, the meshModule rule
fails on exactly the meshes `seededMeshDefects` lists, a planetary stage counts sun over ring plus sun, and a product's
recorded design ratio is checked against its gear train."""

import json
import sys
import unittest
from decimal import Decimal
from pathlib import Path

DATA = Path(__file__).resolve().parents[2] / "data"
sys.path.insert(0, str(DATA))
import functional  # noqa: E402


class Refused(Exception):
    pass


def fail(message):
    raise Refused(message)


def gear(part_id, plm, teeth, module):
    return {"id": part_id, "plm": plm, "extended": {"gear": {"teeth": teeth, "module": module}}}


def product(parts, edges, interfaces=(), **extra):
    return {"product": {"key": "p"}, "parts": parts, "interfaces": [{"id": i, "parts": list(p)} for i, p in interfaces],
            "extended": {"functionalEdges": edges, **extra.pop("extended", {})}, **extra}


TWO_STAGE = [gear("D-1", "DE", 60, "2"), {"id": "D-2", "plm": "DE"}, gear("D-3", "DE", 20, "2"), gear("D-4", "DE", 45, "2"),
             gear("UK-5", "UK", 15, "0.07874")]
TWO_STAGE_EDGES = [{"from": "D-1", "to": "D-3", "flow": "mechanical"}, {"from": "D-3", "to": "D-2", "flow": "mechanical"},
                   {"from": "D-2", "to": "D-4", "flow": "mechanical"}, {"from": "D-4", "to": "UK-5", "flow": "mechanical"}]


class FunctionalEdgesTest(unittest.TestCase):
    def test_an_edge_must_join_two_parts_of_the_product_through_its_own_interface(self):
        parts = [{"id": "D-1", "plm": "DE"}, {"id": "D-2", "plm": "DE"}]
        with self.assertRaisesRegex(Refused, "must join two parts"):
            functional.check(product(parts, [{"from": "D-1", "to": "X-9", "flow": "mechanical"}]), {}, fail)
        with self.assertRaisesRegex(Refused, "not an interface between them"):
            functional.check(product(parts, [{"from": "D-1", "to": "D-2", "flow": "mechanical", "via": "IF-1"}],
                                     [("IF-1", ("D-1", "D-9"))]), {}, fail)
        with self.assertRaisesRegex(Refused, "has flow 'pneumatic'"):
            functional.check(product(parts, [{"from": "D-1", "to": "D-2", "flow": "pneumatic"}]), {}, fail)
        functional.check(product(parts, [{"from": "D-1", "to": "D-2", "flow": "mechanical", "via": "IF-1"}],
                                 [("IF-1", ("D-2", "D-1"))]), {}, fail)

    def test_the_ratio_of_a_two_stage_train_is_the_product_of_driven_over_driver(self):
        data = product(TWO_STAGE, TWO_STAGE_EDGES)
        ratios = functional.train(data, functional.gears(data, fail), "D-1")
        self.assertEqual(ratios["D-3"], Decimal(20) / Decimal(60))
        self.assertEqual(ratios["D-2"], ratios["D-3"], "a shaft passes the speed unchanged")
        self.assertEqual(ratios["UK-5"], Decimal(20) / Decimal(60) * Decimal(15) / Decimal(45))

    def test_a_planetary_stage_with_a_fixed_ring_counts_sun_over_ring_plus_sun(self):
        parts = [{"id": "D-1", "plm": "DE"}, gear("D-2", "DE", 38, "20"), gear("D-3", "DE", 22, "20"), gear("D-4", "DE", 99, "20")]
        edges = [{"from": "D-1", "to": "D-2", "flow": "mechanical"}, {"from": "D-2", "to": "D-3", "flow": "mechanical", "reaction": "D-4"}]
        data = product(parts, edges)
        self.assertEqual(functional.train(data, functional.gears(data, fail), "D-1")["D-3"], Decimal(22) / Decimal(121))

    def test_mesh_module_compares_millimetres_and_must_match_the_seeded_list(self):
        data = product(TWO_STAGE, TWO_STAGE_EDGES)
        gears = functional.gears(data, fail)
        # 0.07874 in is 2.0000 mm: the UK pinion meshes the DE wheel
        self.assertEqual(functional.mesh_defects(data, gears, {p["id"]: p["plm"] for p in TWO_STAGE}), set())
        TWO_STAGE[4]["extended"]["gear"]["module"] = "0.0625"
        try:
            gears = functional.gears(data, fail)
            self.assertEqual(functional.mesh_defects(data, gears, {p["id"]: p["plm"] for p in TWO_STAGE}), {("D-4", "UK-5", "meshModule")})
            with self.assertRaisesRegex(Refused, "disagrees with seededMeshDefects"):
                functional.check(data, gears, fail)
            functional.check(dict(data, seededMeshDefects=[{"driver": "D-4", "driven": "UK-5", "rule": "meshModule"}]), gears, fail)
        finally:
            TWO_STAGE[4]["extended"]["gear"]["module"] = "0.07874"

    def test_mesh_module_tolerance_is_a_hundredth_of_a_millimetre_on_both_sides(self):
        cases = [("2.5", "UK", "0.0984", False),   # 2.49936 mm
                 ("2.5", "UK", "0.1", True),       # 2.54 mm
                 ("2.5", "DE", "2.509", False),
                 ("2.5", "DE", "2.511", True)]
        for driver, plm, driven, flagged in cases:
            parts = [gear("D-1", "DE", 20, driver), gear(f"{plm}-2", plm, 40, driven)]
            data = product(parts, [{"from": "D-1", "to": f"{plm}-2", "flow": "mechanical"}])
            found = functional.mesh_defects(data, functional.gears(data, fail), {p["id"]: p["plm"] for p in parts})
            self.assertEqual(found == {("D-1", f"{plm}-2", "meshModule")}, flagged, (driver, plm, driven))
            self.assertEqual(bool(found), flagged)

    def test_the_wind_turbine_gives_its_design_ratio_and_a_wrong_tooth_count_is_refused(self):
        data = json.loads((DATA / "products" / "wind-turbine.json").read_text(encoding="utf-8"))
        gears = functional.gears(data, fail)
        functional.check(data, gears, fail)
        gears["D-37027"] = (26, gears["D-37027"][1])
        with self.assertRaisesRegex(Refused, "does not give the design ratio 97.02"):
            functional.check(data, gears, fail)


if __name__ == "__main__":
    unittest.main()
