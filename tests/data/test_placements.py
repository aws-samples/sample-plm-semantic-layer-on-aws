# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""data/placements.py: occurrences compose down a three-level tree, the parent's after the child's; a British placement
in inches is composed in millimetres; a line whose placements disagree with its quantity, and a part whose first
occurrence is not where its STEP sits, are refused; the Spanish document carries the placements of its lines."""

import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "data"))
import bom  # noqa: E402
import placements  # noqa: E402


class Refused(Exception):
    pass


def fail(message):
    raise Refused(message)


def product(plm, lines):
    """A product of one site: kit K > assembly A > assembly B > part P, each line as given."""
    return {"product": {"key": "kite", "name": "Kite"},
            "parts": [{"id": "P", "plm": plm}],
            "extended": {"assemblies": [{"id": "K", "plm": plm, "name": "Kit", "kind": "SITE_KIT"},
                                        {"id": "A", "plm": plm, "name": "A", "kind": "ASSEMBLY"},
                                        {"id": "B", "plm": plm, "name": "B", "kind": "ASSEMBLY"}],
                         "bomLines": lines}}


def tree(plm, a=None, b=None, p=None, quantities=(1, 2, 3)):
    lines = [{"parent": "K", "child": "A", "quantity": quantities[0]}, {"parent": "A", "child": "B", "quantity": quantities[1]},
             {"parent": "B", "child": "P", "quantity": quantities[2]}]
    for line, given in zip(lines, (a, b, p)):
        if given is not None:
            line["placements"] = given
    data = bom.Bom(product(plm, lines), fail)
    placements.check(data, fail)
    return data


def world(data, item):
    return [[round(v, 6) for v in placements.placement(m)] for m in placements.world(data)[item]]


class CompositionTest(unittest.TestCase):
    def test_occurrences_multiply_down_three_levels_the_parents_transform_after_the_childs(self):
        data = tree("DE", b=[[0, 0, 0, 0, 0, 0], [0, 0, 100, 0, 0, 90]], p=[[0, 0, 0, 0, 0, 0], [10, 0, 0, 0, 0, 0], [20, 0, 0, 0, 0, 0]])
        self.assertEqual(world(data, "P"), [
            [0, 0, 0, 0, 0, 0], [10, 0, 0, 0, 0, 0], [20, 0, 0, 0, 0, 0],
            [0, 0, 100, 0, 0, 90], [0, 10, 100, 0, 0, 90], [0, 20, 100, 0, 0, 90]])
        self.assertEqual(placements.placed(data), 6)

    def test_a_line_without_placements_draws_once_per_occurrence_of_its_parent(self):
        data = tree("FR", b=[[0, 0, 0, 0, 0, 0], [5, 0, 0, 0, 0, 0]])
        self.assertEqual(world(data, "P"), [[0, 0, 0, 0, 0, 0], [5, 0, 0, 0, 0, 0]])

    def test_a_british_placement_is_in_inches(self):
        data = tree("UK", p=[[0, 0, 0, 0, 0, 0], [10, 0, 0, 0, 0, 180], [0, 1, 0, 90, 0, 0]])
        self.assertEqual(world(data, "P")[1:3], [[254, 0, 0, 0, 0, 180], [0, 25.4, 0, 90, 0, 0]])
        self.assertEqual(placements.rows(data, "UK")[1], ["B", "P", 2, 10, 0, 0, 0, 0, 180])

    def test_a_count_that_differs_from_the_quantity_is_refused(self):
        with self.assertRaisesRegex(Refused, "2 placements for the quantity 3"):
            tree("ES", p=[[0, 0, 0, 0, 0, 0], [1, 0, 0, 0, 0, 0]])

    def test_a_placement_that_is_not_six_numbers_is_refused(self):
        with self.assertRaisesRegex(Refused, "is not"):
            tree("ES", quantities=(1, 1, 1), p=[[0, 0, 0]])

    def test_a_first_occurrence_away_from_the_step_is_refused(self):
        data = tree("DE", quantities=(1, 1, 2), p=[[1, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0]])
        with self.assertRaisesRegex(Refused, "first occurrence of P"):
            placements.check_reference(data, fail)

    def test_the_spanish_document_lists_the_positions_of_a_line(self):
        data = tree("ES", p=[[0, 0, 0, 0, 0, 0], [1.5, 0, 0, 0, 0, 0], [3, 0, 0, 0, 0, 0]])
        line = json.loads(data.document("B", 1))["lineas"][0]
        self.assertEqual(line["posiciones"][1], {"x_mm": 1.5, "y_mm": 0, "z_mm": 0, "rx_grados": 0, "ry_grados": 0, "rz_grados": 0})
        self.assertNotIn("posiciones", json.loads(data.document("A", 1))["lineas"][0])


if __name__ == "__main__":
    unittest.main()
