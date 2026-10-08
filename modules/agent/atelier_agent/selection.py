# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""What the person has selected on screen, so "this part", "this interface" and "here" resolve.

The browser sends it under `forwardedProps.selection` of the AG-UI run input as
`{"product": ..., "root": ..., "part": {"id", "plm", "name"} | null, "interface": {"id"} | null}`,
`root` present in subtree mode only. Every value is checked before it reaches the prompt: ids and
keys against the forms the PLMs and the core database use, the part name stripped of anything
that could read as prompt markup.
"""

import re
from dataclasses import dataclass

from ag_ui.core import RunAgentInput

from .product import KEY_PATTERN

# Native part, assembly and interface ids as the PLMs and the query service spell them.
ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._/-]{0,63}$")
PLM_PATTERN = re.compile(r"^[A-Za-z]{2,8}$")
NAME_MAX = 120


@dataclass(frozen=True)
class SelectedPart:
    id: str
    plm: str
    name: str


@dataclass(frozen=True)
class Selection:
    product: str | None
    root: str | None
    part: SelectedPart | None
    interface: str | None

    def context(self) -> str:
        """The sentences the prompt carries about the selection."""
        where = f" in the subtree of assembly `{self.root}`" if self.root else ""
        shown = f"Product `{self.product}` is on screen{where}." if self.product else ""
        picked = []
        if self.part:
            picked.append(f"part `{self.part.id}` ({self.part.name}, {self.part.plm.upper()} PLM)")
        if self.interface:
            picked.append(f"interface `{self.interface}`")
        if not picked:
            return f"{shown} Nothing is selected.".strip()
        return f"{shown} The person has selected {' and '.join(picked)}.".strip()


def _id(value: object) -> str | None:
    return value if isinstance(value, str) and ID_PATTERN.match(value) else None


def _name(value: object, fallback: str) -> str:
    if not isinstance(value, str):
        return fallback
    text = re.sub(r"[`*_<>{}\[\]\\]", "", " ".join(value.split()))[:NAME_MAX].strip()
    return text or fallback


def _part(value: object) -> SelectedPart | None:
    if not isinstance(value, dict):
        return None
    part_id = _id(value.get("id"))
    plm = value.get("plm")
    if part_id is None or not isinstance(plm, str) or not PLM_PATTERN.match(plm):
        return None
    return SelectedPart(id=part_id, plm=plm, name=_name(value.get("name"), part_id))


def selection_of(run_input: RunAgentInput) -> Selection | None:
    """The selection of a run, or None when the body carries none; malformed fields are dropped."""
    props = run_input.forwarded_props
    value = props.get("selection") if isinstance(props, dict) else None
    if not isinstance(value, dict):
        return None
    product = value.get("product")
    interface = value.get("interface")
    return Selection(
        product=product if isinstance(product, str) and KEY_PATTERN.match(product) else None,
        root=_id(value.get("root")),
        part=_part(value.get("part")),
        interface=_id(interface.get("id")) if isinstance(interface, dict) else None,
    )
