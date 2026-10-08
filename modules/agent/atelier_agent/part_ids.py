# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""The part ids the viewer tools name, resolved against the parts of the product on screen.

An id matches exactly, or else by its normalised form (upper case, without hyphens or spaces) when exactly one
part of the product has that form: the model writing UK3501 for UK-3501 still shows UK-3501. The browser
resolves the same way (modules/web/src/viewer/ids.ts); the tool's acknowledgement tells the model which ids it
read otherwise and which match no part, so it can call again with the exact ids.
"""

import re
from collections.abc import Callable
from contextvars import ContextVar

_DASHES = re.compile(r"[\s\-‐-―]")


def normalised(part_id: str) -> str:
    return _DASHES.sub("", part_id).upper()


def resolve(ids: list[str], known: set[str]) -> tuple[list[str], dict[str, str], list[str]]:
    """The ids as parts of `known`, the ids read through their normalised form, and the ids that match no part."""
    by_form: dict[str, list[str]] = {}
    for k in known:
        by_form.setdefault(normalised(k), []).append(k)
    out, read_as, unresolved = [], {}, []
    for part_id in ids:
        if part_id in known:
            out.append(part_id)
        elif len(by_form.get(normalised(part_id), [])) == 1:
            out.append(by_form[normalised(part_id)][0])
            read_as[part_id] = out[-1]
        else:
            unresolved.append(part_id)
    return out, read_as, unresolved


class PartIds:
    """The product a run's viewer shows and its part ids, read once per product through `load`."""

    def __init__(self, load: Callable[[str], set[str] | None], product: str | None) -> None:
        self.load = load
        self.product = product
        self.known: dict[str, set[str] | None] = {}

    def acknowledge(self, ids: list[str], done: str) -> str:
        """The acknowledgement of a viewer call naming `ids`: what the viewer did, and the ids it read otherwise or could not find."""
        if self.product is None:
            return f"{done}."
        if self.product not in self.known:
            self.known[self.product] = self.load(self.product)
        known = self.known[self.product]
        if known is None:
            return f"{done}."
        shown, read_as, unresolved = resolve(ids, known)
        notes = [f"{done}: {len(shown)} of {len(ids)} part ids shown."]
        if read_as:
            notes.append("Read as: " + ", ".join(f"{a} as {b}" for a, b in read_as.items()) + ".")
        if unresolved:
            notes.append(
                f"No part of product `{self.product}` has id " + ", ".join(unresolved)
                + ": call again with the exact ids a tool returned."
            )
        return " ".join(notes)


RUN_PART_IDS: ContextVar[PartIds | None] = ContextVar("run_part_ids", default=None)


def acknowledge(ids: list[str], done: str) -> str:
    """The acknowledgement for the run in progress; outside a run, what the viewer was asked to do."""
    run = RUN_PART_IDS.get()
    return run.acknowledge(ids, done) if run else f"{done}."


def open_product(product: str | None) -> None:
    """The viewer now shows `product`: later viewer calls resolve against its parts."""
    run = RUN_PART_IDS.get()
    if run and product:
        run.product = product
