# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Part ids the viewer tools name: exact first, then a unique normalised form; the rest reported to the model."""

from atelier_agent import part_ids
from atelier_agent.frontend_tools import isolate_parts, open_product, zoom_to_part
from atelier_agent.part_ids import RUN_PART_IDS, PartIds, resolve

CUBESAT = {"UK-3501", "UK-3503", "D-35001", "ES-3503"}


def test_an_id_matches_exactly_or_by_a_unique_normalised_form():
    shown, read_as, unresolved = resolve(["UK-3501", "UK3503", "uk-35-01", "d35001", "UK-9999"], CUBESAT)
    assert shown == ["UK-3501", "UK-3503", "UK-3501", "D-35001"]
    assert read_as == {"UK3503": "UK-3503", "uk-35-01": "UK-3501", "d35001": "D-35001"}
    assert unresolved == ["UK-9999"]


def test_a_normalised_form_two_parts_share_resolves_to_neither():
    shown, read_as, unresolved = resolve(["AB12"], {"AB-12", "A-B12"})
    assert shown == [] and read_as == {} and unresolved == ["AB12"]


def test_the_acknowledgement_names_what_was_read_otherwise_and_what_matches_no_part():
    loads = []

    def load(product):
        loads.append(product)
        return CUBESAT if product == "cubesat" else {"FR3801"}

    token = RUN_PART_IDS.set(PartIds(load, "cubesat"))
    try:
        ack = isolate_parts(ids=["UK3501", "UK-3503", "UK-9999"], caption="power")
        assert ack == ("Isolated: 2 of 3 part ids shown. Read as: UK3501 as UK-3501. No part of product `cubesat` has id UK-9999:"
                       " call again with the exact ids a tool returned.")
        assert zoom_to_part(id="UK-3503") == "Zoomed: 1 of 1 part ids shown."
        open_product(product="rover")
        assert zoom_to_part(id="FR-3801") == "Zoomed: 1 of 1 part ids shown. Read as: FR-3801 as FR3801."
        assert loads == ["cubesat", "rover"], "each product's ids are read once"
    finally:
        RUN_PART_IDS.reset(token)
    assert part_ids.acknowledge(["X"], "Outlined") == "Outlined."
