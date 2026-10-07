# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""data/bom.py: a product file made only of parts and interfaces loads with one flat site kit per site; a product
that declares a tree must declare a kit for every site."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "data"))
import bom  # noqa: E402


class Refused(Exception):
    pass


def fail(message):
    raise Refused(message)


def product(**extended):
    return {"product": {"key": "kite", "name": "Kite"},
            "parts": [{"id": "D-1", "plm": "DE"}, {"id": "D-2", "plm": "DE"}, {"id": "FR1", "plm": "FR"}],
            "extended": extended}


class FlatTreeTest(unittest.TestCase):
    def test_a_product_without_a_tree_gets_one_kit_per_site_holding_every_part(self):
        tree = bom.Bom(product(), fail)
        self.assertEqual(tree.kit, {"DE": "KITE-DE-KIT", "FR": "KITE-FR-KIT"})
        self.assertEqual(sorted(tree.children["KITE-DE-KIT"]), ["D-1", "D-2"])
        self.assertEqual(sorted(tree.children["KITE-FR-KIT"]), ["FR1"])

    def test_a_declared_tree_without_a_kit_for_a_site_is_refused(self):
        with self.assertRaises(Refused):
            bom.Bom(product(assemblies=[{"id": "D-0", "plm": "DE", "name": "Bausatz", "kind": "SITE_KIT"}],
                            bomLines=[{"parent": "D-0", "child": "D-1", "quantity": 1},
                                      {"parent": "D-0", "child": "D-2", "quantity": 1}]), fail)


if __name__ == "__main__":
    unittest.main()
