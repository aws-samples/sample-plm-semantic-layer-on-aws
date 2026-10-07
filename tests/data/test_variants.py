# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""data/variants.py: the shape refusals of a variant group, the configurations an option builds, the ports, and the
groups of the products that carry them, as data/generate.py reads them."""

import copy
import sys
import unittest
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "data"))
import bom as boms  # noqa: E402
import variants  # noqa: E402


class Refused(Exception):
    pass


def fail(message):
    raise Refused(message)


def fastener(fid, part, plm, port=None):
    out = {"id": fid, "part": part, "plm": plm, "kind": "fasteners", "position": {"x": Decimal(0), "y": Decimal(0), "z": Decimal(0)}}
    return out | ({"port": port} if port else {})


# A frame (FR), an arm in a sub-assembly (ES), the arm's pin, and the slot "return": the arm by default, a spring as option.
BASE = {
    "product": {"key": "p"},
    "parts": [{"id": "F", "plm": "FR"}, {"id": "A", "plm": "ES"}, {"id": "PIN", "plm": "ES"}],
    "extended": {"assemblies": [{"id": "FK", "plm": "FR", "name": "Kit", "kind": "SITE_KIT"},
                                {"id": "EK", "plm": "ES", "name": "Kit", "kind": "SITE_KIT"},
                                {"id": "ASM", "plm": "ES", "name": "Arm set", "kind": "ASSEMBLY"}],
                 "bomLines": [{"parent": "FK", "child": "F", "quantity": 1}, {"parent": "EK", "child": "ASM", "quantity": 1},
                              {"parent": "ASM", "child": "A", "quantity": 2}, {"parent": "ASM", "child": "PIN", "quantity": 1}]},
    "interfaces": [{"id": "IF-1", "parts": ["A", "F"], "fasteners": [[fastener("A-1", "A", "ES"), fastener("F-1", "F", "FR", "eye")]]},
                   {"id": "IF-2", "parts": ["PIN", "A"], "fasteners": [[fastener("PIN-1", "PIN", "ES"), fastener("A-2", "A", "ES")]]}],
}
GROUP = {
    "name": "Return", "default": "arm",
    "options": {
        "arm": {"default": True, "assemblies": ["ASM"], "parts": ["A", "PIN"], "interfaces": ["IF-1", "IF-2"]},
        "spring": {"default": False,
                   "parts": [{"id": "S", "plm": "ES"}],
                   "interfaces": [{"id": "IF-9", "parts": ["S", "F"],
                                   "fasteners": [[fastener("S-1", "S", "ES", "eye"), fastener("F-9", "F", "FR", "anchor")]]}],
                   "replaces": {"assemblies": {"ASM": "S"}, "interfaces": {"IF-1": "IF-9"},
                                "removes": {"parts": ["A", "PIN"], "assemblies": [], "interfaces": ["IF-2"]}},
                   "bomLines": [{"parent": "EK", "child": "S", "quantity": 1}]},
    },
}


def product(group=None, **option_changes):
    data = copy.deepcopy(BASE)
    spec = copy.deepcopy(group or GROUP)
    for path, value in option_changes.items():
        option, key = path.split("__")
        spec["options"][option][key] = value
    data["extended"]["variants"] = {"return": spec}
    data["items"] = []
    data["bom"] = boms.Bom(data, fail)
    data["assemblies"] = data["bom"].assemblies
    return data


def checked(data, keys=None):
    groups = variants.groups(data, fail)
    configs = variants.check(data, groups, set() if keys is None else keys, fail)
    variants.check_ports(configs, variants.ports([(data, groups)], fail), fail)
    return groups, configs


class ShapeRefusalTest(unittest.TestCase):
    def test_a_valid_group_builds_both_configurations(self):
        _, configs = checked(product())
        spring = configs["return"]["spring"]
        self.assertEqual([i["id"] for i in spring.interfaces], ["IF-9"])
        self.assertEqual(sorted(spring.items), ["EK", "F", "FK", "S"])
        self.assertEqual([i["id"] for i in configs["return"]["arm"].interfaces], ["IF-1", "IF-2"])

    def test_an_option_replacing_an_id_the_base_does_not_hold_is_refused(self):
        with self.assertRaisesRegex(Refused, "replaces or removes NOPE, which the base does not hold"):
            checked(product(spring__replaces={"assemblies": {"ASM": "S"}, "interfaces": {"IF-1": "IF-9"},
                                              "removes": {"parts": ["A", "PIN", "NOPE"], "interfaces": ["IF-2"]}}))

    def test_an_object_with_a_base_id_is_refused(self):
        with self.assertRaisesRegex(Refused, "object F has the id of a base item"):
            checked(product(spring__parts=[{"id": "F", "plm": "FR"}]))

    def test_a_port_named_twice_on_one_part_is_refused(self):
        spec = copy.deepcopy(GROUP)
        spec["options"]["spring"]["interfaces"][0]["fasteners"][0][1]["port"] = "eye"
        spec["options"]["spring"]["replaces"] = {"assemblies": {"ASM": "S"}, "removes": {"parts": ["A", "PIN"], "interfaces": ["IF-2", "IF-1"]}}
        spec["options"]["spring"]["interfaces"].append(
            {"id": "IF-8", "parts": ["S", "F"], "fasteners": [[fastener("S-2", "S", "ES"), fastener("F-1", "F", "FR", "eye")]]})
        with self.assertRaisesRegex(Refused, "port 'eye' of F is named twice, by F-9 and F-1|port 'eye' of F is named twice, by F-1 and F-9"):
            checked(product(spec))

    def test_a_feature_given_two_ports_is_refused(self):
        spec = copy.deepcopy(GROUP)
        spec["options"]["arm"]["ports"] = {"IF-1": {"F-1": "bore"}}
        with self.assertRaisesRegex(Refused, "feature F-1 has two ports, 'eye' and 'bore'"):
            checked(product(spec))

    def test_more_than_one_default_is_refused(self):
        spec = copy.deepcopy(GROUP)
        spec["options"]["cord"] = {"default": True}
        with self.assertRaisesRegex(Refused, "needs exactly one default option"):
            checked(product(spec))

    def test_an_option_taking_out_what_the_default_does_not_list_is_refused(self):
        spec = copy.deepcopy(GROUP)
        spec["options"]["arm"]["parts"] = ["A"]
        with self.assertRaisesRegex(Refused, "replaces or removes PIN, which the default option arm does not list"):
            checked(product(spec))

    def test_an_option_interface_joining_a_part_the_option_removes_is_refused(self):
        spec = copy.deepcopy(GROUP)
        spec["options"]["spring"]["interfaces"][0]["parts"] = ["S", "A"]
        spec["options"]["spring"]["interfaces"][0]["fasteners"][0][1] = fastener("A-9", "A", "ES")
        with self.assertRaisesRegex(Refused, r"interface IF-9 joins \['A'\]"):
            checked(product(spec))

    def test_an_option_key_used_twice_in_the_dataset_is_refused(self):
        with self.assertRaisesRegex(Refused, "option key spring is used twice in the dataset"):
            checked(product(), keys={"spring"})

    def test_the_default_option_holds_no_objects(self):
        with self.assertRaisesRegex(Refused, "the default option is the base product"):
            checked(product(arm__bomLines=[{"parent": "EK", "child": "A", "quantity": 1}]))

    def test_an_option_item_needs_one_line(self):
        with self.assertRaisesRegex(Refused, "S needs one line of the option's bill of materials, found 0"):
            checked(product(spring__bomLines=[]))


class ConfigurationTest(unittest.TestCase):
    def test_removing_an_assembly_takes_out_what_only_its_lines_reached(self):
        spec = copy.deepcopy(GROUP)
        spec["options"]["arm"].update(parts=[], interfaces=["IF-1", "IF-2"])
        spec["options"]["spring"]["replaces"] = {"assemblies": {"ASM": "S"}, "interfaces": {"IF-1": "IF-9"},
                                                 "removes": {"interfaces": ["IF-2"]}}
        _, configs = checked(product(spec))
        self.assertEqual(sorted(configs["return"]["spring"].unreached), ["A", "PIN"])
        self.assertNotIn("A", configs["return"]["spring"].items)


# The migrated products: what each option's configuration holds, as the generator computes it from the product files.
class ProductGroupsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import generate
        cls.generate = generate
        cls.products = {d["product"]["key"]: d for d in generate.load()}
        cls.parts = generate.check(list(cls.products.values()))

    def figures(self, product, group, option):
        data = self.products[product]
        config = data["configurations"][group][option]
        failing = {i for i, _, _ in self.generate.evaluate({"interfaces": config.interfaces})}
        occurrences, mass = self.generate.configuration_mass(data, config, self.parts)
        return len(config.interfaces), len(failing), int(sum(occurrences.values())), mass

    def test_wind_turbine_offshore_swaps_the_foundation(self):
        self.assertEqual(self.figures("wind-turbine", "foundation", "onshore")[:3], (40, 7, 3738))
        self.assertEqual(self.figures("wind-turbine", "foundation", "offshore")[:3], (41, 7, 3797))

    def test_steam_engine_options_remove_the_parallel_motion_or_the_governor(self):
        self.assertEqual(self.figures("steam-engine", "acting", "single-acting")[:3], (22, 5, 29))
        self.assertEqual(self.figures("steam-engine", "governor", "without-governor")[:3], (21, 4, 28))

    def test_difference_engine_without_output_has_the_occurrences_the_file_recorded(self):
        self.assertEqual(self.figures("difference-engine", "output", "engine-with-output")[:3], (45, 7, 7484))
        self.assertEqual(self.figures("difference-engine", "output", "engine")[:3], (35, 5, 5767))

    def test_ornithopter_spring_return_adds_a_fastener_failure_and_the_one_piece_crank_clears_one(self):
        self.assertEqual(self.figures("ornithopter", "wing-return", "spring-return")[:2], (76, 8))
        self.assertEqual(self.figures("ornithopter", "crank-build", "one-piece")[:3], (72, 6, 86))


if __name__ == "__main__":
    unittest.main()
