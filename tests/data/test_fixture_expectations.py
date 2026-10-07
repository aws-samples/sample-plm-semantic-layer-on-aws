# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""modules/query-service/fixtures/dataset.py, the expectations of the fixture check read from the product files, as
tests/seeded.mjs reads them for the smoke test: every part released without a file-index entry carries cadMissing,
the scripted demo's pending part is its own product's, and a product's lead-time conflicts are its own offers'."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "modules" / "query-service" / "fixtures"))
import dataset  # noqa: E402


def product(key, pending):
    """A fixture product with fictional ids: two parts with geometry, `pending` of them released without a CAD file."""
    parts = [{"id": f"{key}-P{i}", "plm": "DE", "cadFile": f"cad/{key}/p{i}.stp", **({"cadPending": True} if i in pending else {})}
             for i in range(2)]
    parts.append({"id": f"{key}-DOC", "plm": "UK", "cadFile": None, "extended": {"type": "DOCUMENT"}})
    return {"product": {"key": key}, "parts": parts}


class CadMissing(unittest.TestCase):
    def test_every_pending_part_with_geometry_of_every_product_carries_cad_missing(self):
        products = {"ornithopter": product("ornithopter", {0}), "second": product("second", {1})}
        self.assertEqual(dataset.cad_missing_parts(products), ["ornithopter-P0", "second-P1"])

    def test_the_demo_publishes_its_own_products_pending_part(self):
        products = {"first": product("first", {0}), "ornithopter": product("ornithopter", {1})}
        self.assertEqual(dataset.demo_pending_part(products, "ornithopter"), "ornithopter-P1")

    def test_the_product_files_pending_parts(self):
        expected = sorted(p["id"] for d in dataset.PRODUCTS.values() for p in d["parts"] if p.get("cadPending"))
        self.assertEqual(dataset.cad_missing_parts(dataset.PRODUCTS), expected)


def weighed(key, limit, mass):
    """A fixture product with a mass limit: one site kit holding two parts of `mass` kg, one of them twice."""
    return {"product": {"key": key}, "parts": [{"id": f"{key}-P{i}", "plm": "DE", "cadFile": f"cad/{key}/p{i}.stp", "extended": {"mass": mass}}
                                               for i in range(2)],
            "extended": {"massLimitKg": limit, "assemblies": [{"id": f"{key}-KIT", "plm": "DE", "kind": "SITE_KIT"}],
                         "bomLines": [{"parent": f"{key}-KIT", "child": f"{key}-P0", "quantity": 2},
                                      {"parent": f"{key}-KIT", "child": f"{key}-P1", "quantity": 1}]}}


class MassLimit(unittest.TestCase):
    def test_every_product_with_a_limit_is_weighed_from_its_file(self):
        # 2 x 1.5 kg + 1.5 kg = 4.5 kg.
        products = {"over": weighed("over", "4.4", "1.5"), "within": weighed("within", "4.5", "1.5"), "second": product("second", set())}
        self.assertEqual(dataset.mass_limit(products=products),
                         {"over": {"status": "fail", "limitKg": 4.4}, "within": {"status": "pass", "limitKg": 4.5}})

    def test_a_third_product_over_its_limit_joins_the_product_files_answer(self):
        products = dict(dataset.PRODUCTS, third=weighed("third", "1", "1"))
        expected = dataset.mass_limit()
        self.assertEqual(dataset.mass_limit(products=products), dict(expected, third={"status": "fail", "limitKg": 1.0}))



def offer(no, part, days, preferred=True, plm="DE"):
    return {"id": str(no), "part": part, "plm": plm, "supplier": f"Supplier {no}", "days": days, "preferred": preferred}


class LeadTimeConflicts(unittest.TestCase):
    def test_a_products_conflicts_are_those_of_its_own_offers(self):
        rover = [offer(1, "R-1", 10), offer(2, "R-1", 20), offer(3, "R-2", 5), offer(4, "R-2", 9, preferred=False)]
        second = [offer(5, "S-1", 7, plm="UK"), offer(6, "S-1", 14, plm="UK")]
        self.assertEqual({p: [f["value"]["id"] for f in fs] for p, fs in dataset.lead_time_conflicts(rover).items()}, {"R-1": ["2"]})
        self.assertEqual(list(dataset.lead_time_conflicts(second)), ["S-1"])

    def test_a_second_product_with_a_conflict_leaves_the_rover_expectation_alone(self):
        second = [offer(5, "S-1", 7, plm="UK"), offer(6, "S-1", 14, plm="UK")]
        offers = dict(dataset.OFFERS, second=second)
        self.assertEqual(dataset.lead_time_conflicts(offers["rover"]), dataset.lead_time_conflicts(dataset.OFFERS["rover"]))
        self.assertNotIn("S-1", dataset.lead_time_conflicts(offers["rover"]))
        self.assertTrue(dataset.lead_time_conflicts(dataset.OFFERS["rover"]), "the rover's file seeds a lead-time conflict")

if __name__ == "__main__":
    unittest.main()
