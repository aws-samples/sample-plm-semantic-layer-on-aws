# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""The bills of materials of the product files, for data/generate.py.

A product file holds each site's slice of the product as a tree under the site's own kit:
`extended.assemblies` are the non-geometric nodes of the trees (kind SITE_KIT or ASSEMBLY) and
`extended.bomLines` the lines `{parent, child, quantity, findNumber?, placements?}`, parent and child items of the
same site, placements as data/placements.py describes them. No site holds the product root: it exists only in the
semantic layer. `check` enforces the shape; the writers turn the lines into each site's own idiom: a parent column on
the DE part row, the FR nomenclature link table, a JSON document on the ES parent row, the UK indented rows.
"""

import json
from decimal import Decimal

import placements

KINDS = ("SITE_KIT", "ASSEMBLY")
# Every assembly and site kit is released to every profile unless the product file classifies it.
OPEN = {"jurisdiction": "NONE", "releasableTo": "ALL"}


class Bom:
    """One product's trees: the item of each id, each site's kit, each item's lines to its children."""

    def __init__(self, data, fail):
        self.key = data["product"]["key"]
        extended = data.get("extended") or {}
        if not extended.get("assemblies") and not extended.get("bomLines"):
            extended = dict(extended, **flat_tree(data))
        self.assemblies = [assembly_item(a, fail) for a in extended.get("assemblies", [])]
        self.plm_of = {p["id"]: p["plm"] for p in data["parts"]}
        for a in self.assemblies:
            if a["id"] in self.plm_of:
                fail(f"{self.key}: assembly {a['id']} has the id of a part")
            self.plm_of[a["id"]] = a["plm"]
        for item in data.get("items", []):
            if item["id"] in self.plm_of:
                fail(f"{self.key}: non-geometric item {item['id']} has the id of a part or assembly")
            self.plm_of[item["id"]] = item["plm"]
        self.geometric = {p["id"] for p in data["parts"] if (p.get("extended") or {}).get("type", "PART") == "PART"}
        self.kit = {}
        for a in self.assemblies:
            if a["kind"] == "SITE_KIT":
                if a["plm"] in self.kit:
                    fail(f"{self.key}: {a['plm']} has two site kits, {self.kit[a['plm']]} and {a['id']}")
                self.kit[a["plm"]] = a["id"]
        self.lines = [dict(line, quantity=Decimal(str(line["quantity"]))) for line in extended.get("bomLines", [])]
        # A non-geometric item is used once by its site kit.
        self.lines += [{"parent": item["parent"], "child": item["id"], "quantity": Decimal(1)} for item in data.get("items", [])]
        self.children, self.parents = {}, {}
        for line in self.lines:
            parent, child = line["parent"], line["child"]
            if parent not in self.plm_of or child not in self.plm_of:
                fail(f"{self.key}: line {parent} -> {child} names an item the file does not hold")
            if self.plm_of[parent] != self.plm_of[child]:
                fail(f"{self.key}: line {parent} -> {child} crosses from {self.plm_of[parent]} to {self.plm_of[child]}; "
                     "a site's tree holds its own items, a use across sites is an external reference")
            if line["quantity"] <= 0:
                fail(f"{self.key}: line {parent} -> {child} has quantity {line['quantity']}")
            if child in self.children.get(parent, {}):
                fail(f"{self.key}: line {parent} -> {child} is declared twice; one line carries all its occurrences")
            self.children.setdefault(parent, {})[child] = line
            self.parents.setdefault(child, []).append(parent)
        self.check(fail)

    def check(self, fail):
        sites = sorted(set(self.plm_of.values()))
        for plm in sites:
            if plm not in self.kit:
                fail(f"{self.key}: {plm} has no site kit")
        for item, plm in self.plm_of.items():
            parents = self.parents.get(item, [])
            if item == self.kit[plm]:
                if parents:
                    fail(f"{self.key}: site kit {item} has a parent; no site holds the product root")
            elif not parents:
                fail(f"{self.key}: {item} has no parent in the {plm} tree")
            elif plm == "DE" and len(parents) > 1:
                fail(f"{self.key}: DE item {item} has the parents {parents}; the DE tree holds one parent per item")
        for plm in sites:
            seen = set()
            self.walk(self.kit[plm], [], seen, fail)
            unreached = sorted(i for i, p in self.plm_of.items() if p == plm and i not in seen)
            if unreached:
                fail(f"{self.key}: {plm} items not under the site kit (a cycle): {unreached}")

    def walk(self, item, path, seen, fail):
        if item in path:
            fail(f"{self.key}: the tree cycles through {' -> '.join(path + [item])}")
        seen.add(item)
        for child in self.children.get(item, {}):
            self.walk(child, path + [item], seen, fail)

    def occurrences(self):
        """Part occurrences per site: the quantities multiplied down each tree, over the geometric parts."""
        totals = {}
        for plm, kit in self.kit.items():
            total, stack = Decimal(0), [(kit, Decimal(1))]
            while stack:
                item, mult = stack.pop()
                for child, line in self.children.get(item, {}).items():
                    if child in self.geometric:
                        total += mult * line["quantity"]
                    stack.append((child, mult * line["quantity"]))
            totals[plm] = total
        return totals

    def parent_line(self, item):
        """The DE line of an item: (parent, quantity), (None, None) for a site kit."""
        parents = self.parents.get(item)
        if not parents:
            return None, None
        return parents[0], self.children[parents[0]][item]["quantity"]

    def site_lines(self, plm):
        return [line for line in self.lines if self.plm_of[line["parent"]] == plm]

    def document(self, item, version, positions=placements.positions):
        """The ES lista_materiales of a parent item, or None for an item without lines; `positions` gives a line's
        posiciones."""
        children = self.children.get(item)
        if not children:
            return None
        lineas = [{"referencia": child, "cantidad": quantity_json(line["quantity"])} for child, line in children.items()]
        for linea, line in zip(lineas, children.values()):
            if positions(line):
                linea["posiciones"] = positions(line)
        return json.dumps({"version": version, "lineas": lineas}, ensure_ascii=False, separators=(", ", ": "))

    def indented(self, plm):
        """The UK indented rows of a site kit, depth first: (level, parent, child, quantity), the kit itself first.
        A sub-assembly used under several parents is listed under each, its own lines under the first only."""
        rows, expanded = [(0, None, self.kit[plm], Decimal(1))], set()

        def visit(item, level):
            if item in expanded:
                return
            expanded.add(item)
            for child, line in self.children.get(item, {}).items():
                rows.append((level, item, child, line["quantity"]))
                visit(child, level + 1)
        visit(self.kit[plm], 1)
        return rows


# The site kit a product without a bill of materials gets, named in the site's language.
KIT_WORD = {"DE": "Bausatz", "FR": "Kit", "ES": "Kit", "UK": "Kit"}


def flat_tree(data):
    """A one-level tree for a product file that carries no bill of materials: each site's parts directly under
    one site kit at quantity 1, so a product made only of parts and interfaces loads as it is."""
    key, name = data["product"]["key"], data["product"]["name"]
    sites = sorted({p["plm"] for p in data["parts"]})
    kit = {plm: f"{key.upper()}-{plm}-KIT" for plm in sites}
    return {"assemblies": [{"id": kit[plm], "plm": plm, "name": f"{KIT_WORD.get(plm, 'Kit')} {name}"[:120], "kind": "SITE_KIT"}
                           for plm in sites],
            "bomLines": [{"parent": kit[p["plm"]], "child": p["id"], "quantity": 1} for p in data["parts"]]}


def assembly_item(a, fail):
    """An assembly of the staging shape as a row of its site's part table: no CAD file, part type ASSEMBLY."""
    if a.get("kind") not in KINDS:
        fail(f"assembly {a.get('id')}: kind {a.get('kind')!r} is not one of {', '.join(KINDS)}")
    return {"id": a["id"], "plm": a["plm"], "name": a["name"], "kind": a["kind"], "cadFile": None,
            "classification": a.get("classification", OPEN),
            "extended": {"revision": a.get("revision"), "lifecycle": a.get("lifecycle"), "type": "ASSEMBLY", "nameEn": a.get("nameEn")}}


def quantity_json(value):
    return int(value) if value == value.to_integral_value() else float(value)
