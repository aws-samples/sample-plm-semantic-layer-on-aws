# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""data/references.py: an external reference links two sites, so a reference to a part of the referring item's own
site is refused; a URN under the own site's code that names no part of it stays a dangling reference."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "data"))
import references as refs  # noqa: E402

ITEMS = {"D-1": {"plm": "DE"}, "D-2": {"plm": "DE"}, "FR1": {"plm": "FR"}}


class Refused(Exception):
    pass


def fail(message):
    raise Refused(message)


def product(*external):
    return {"product": {"key": "p"}, "parts": [{"id": i} for i in ITEMS], "assemblies": [],
            "extended": {"externalRefs": [{"localPart": local, "remoteUrn": urn, "quantity": 1} for local, urn in external]}}


class SameSiteReferenceTest(unittest.TestCase):
    def test_a_reference_to_a_part_of_the_own_site_is_refused(self):
        with self.assertRaisesRegex(Refused, "D-1 references urn:plm:de:part:D-2, a part of its own site DE"):
            refs.References(product(("D-1", "urn:plm:de:part:D-2")), ITEMS, fail)

    def test_a_reference_to_another_sites_part_is_written(self):
        written = refs.References(product(("D-1", "urn:plm:fr:part:FR1")), ITEMS, fail)
        self.assertEqual([r["remoteUrn"] for r in written.rows], ["urn:plm:fr:part:FR1"])

    def test_a_wrong_site_code_naming_no_part_stays_dangling(self):
        written = refs.References(product(("FR1", "urn:plm:fr:part:D-1")), ITEMS, fail)
        self.assertEqual(refs.evaluate(written.rows, ITEMS | {k: v | {"attributes": [None, None]} for k, v in ITEMS.items()}, {}),
                         {("FR1", "urn:plm:fr:part:D-1", "danglingReference")})


if __name__ == "__main__":
    unittest.main()
