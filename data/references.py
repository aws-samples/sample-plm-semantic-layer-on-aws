# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""The external references of the product files, for data/generate.py.

`extended.externalRefs` holds each use of another site's part: `{localPart, remoteUrn, quantity, expectedRevision,
note}`. A site never copies the remote part; it names it by URN, urn:plm:<site>:part:<local id>, with the revision it
expects. A reference whose local part is a part row (a part, an assembly, a site kit, a software or a document item)
is written to its site's reference table; one made by a non-geometric item that is not a row is counted, not written.
A reference to a part of the referring item's own site is refused: an external reference links two sites.

`evaluate` applies the two reference rules of ontology/shapes.ttl the way the shapes do: danglingReference when the
URN is not a urn:plm:<site>:part:<local id> key or resolves to no part of any PLM, staleRevision when the target's
revision differs from the expected one (compared as text) or its lifecycle word is the site's SUPERSEDED word. The
British native form UK/nnnn resolves to the part key UK-nnnn.
"""

import re
from decimal import Decimal

URN = re.compile(r"urn:plm:(de|fr|es|uk):part:(.+)")
RULES = ("danglingReference", "staleRevision")
# Proposed defects whose rule this layer reads as danglingReference: a URN that does not parse resolves to nothing.
DANGLING_ALIASES = {"danglingReference", "malformedUrn"}
QUANTITY_SCALE = 3  # NUMERIC(12, 3) quantity columns
TEXT_LENGTH = {"remoteUrn": 160, "expectedRevision": 8, "note": 400}
FIELDS = {"localPart", "remoteUrn", "quantity", "expectedRevision", "note", "interface", "defect"}


def resolve(urn):
    """(PLM code, part key) the URN names, or None when it is not a urn:plm:<site>:part:<local id> key."""
    m = URN.fullmatch(urn)
    if not m:
        return None
    site, local = m.group(1), m.group(2)
    return site.upper(), re.sub(r"^UK/", "UK-", local) if site == "uk" else local


def native_key(local_id):
    """The part key of a British native id UK/nnnn; other ids unchanged."""
    return re.sub(r"^UK/", "UK-", local_id)


def same_site(key, ref, items, fail):
    """Refuses a reference to a part of the referring item's own site: an external reference links two sites, and a
    site relates its own items in its bill of materials. A URN under the own site's code that names no part of that site
    stays a reference to nothing (the cubesat's wrong site code), which danglingReference reports."""
    target = resolve(ref["remoteUrn"])
    if target and target[0] == ref["plm"] and target[1] in items and items[target[1]]["plm"] == target[0]:
        fail(f"{key}: {ref['localPart']} references {ref['remoteUrn']}, a part of its own site {ref['plm']}; "
             "an external reference links two sites")


class References:
    """One product's external references, checked; `rows` are those a part row makes, `pending` the others."""

    def __init__(self, data, items, fail):
        key = data["product"]["key"]
        extended = data.get("extended") or {}
        others = {native_key(i.get("localId", "")) for i in extended.get("nonGeometricItems", [])}
        own = {p["id"] for p in data["parts"]} | {a["id"] for a in data["assemblies"]} | {i["id"] for i in data.get("items", [])}
        self.rows, self.pending = [], []
        for ref in extended.get("externalRefs", []):
            if set(ref) - FIELDS or not {"localPart", "remoteUrn", "quantity"} <= set(ref):
                fail(f"{key}: external reference {ref} needs localPart, remoteUrn and quantity, and no field outside {sorted(FIELDS)}")
            ref = dict(ref, quantity=Decimal(str(ref["quantity"])),
                       expectedRevision=None if ref.get("expectedRevision") is None else str(ref["expectedRevision"]))
            if ref["quantity"] <= 0 or -ref["quantity"].as_tuple().exponent > QUANTITY_SCALE:
                fail(f"{key}: external reference {ref['localPart']} -> {ref['remoteUrn']} has quantity {ref['quantity']}")
            for field, length in TEXT_LENGTH.items():
                if ref.get(field) is not None and len(ref[field]) > length:
                    fail(f"{key}: external reference {ref['localPart']} -> {ref['remoteUrn']}: {field} exceeds {length} characters")
            if ref["localPart"] in own:
                ref["plm"] = items[ref["localPart"]]["plm"]
                same_site(key, ref, items, fail)
                self.rows.append(ref)
            elif ref["localPart"] in others:
                self.pending.append(ref)
            else:
                fail(f"{key}: external reference from {ref['localPart']}, which is no item of the product")

    def every(self):
        return self.rows + self.pending


def evaluate(refs, items, superseded):
    """{(localPart, remoteUrn, rule)} of every reference that fails a reference rule; `items` are every part row of
    every product by id, `superseded` the SUPERSEDED lifecycle word of each PLM."""
    failures = set()
    for ref in refs:
        target = resolve(ref["remoteUrn"])
        item = items.get(target[1]) if target else None
        if item is None or item["plm"] != target[0]:
            failures.add((ref["localPart"], ref["remoteUrn"], "danglingReference"))
            continue
        revision, lifecycle = item["attributes"][0], item["attributes"][1]
        expected = ref["expectedRevision"]
        if (expected is not None and revision is not None and str(revision) != expected) or lifecycle == superseded[item["plm"]]:
            failures.add((ref["localPart"], ref["remoteUrn"], "staleRevision"))
    return failures


def declared(data, fail):
    """The product's `seededReferenceDefects` as {(localPart, remoteUrn, rule)}."""
    out = set()
    for d in data.get("seededReferenceDefects", []):
        if d.get("rule") not in RULES:
            fail(f"{data['product']['key']}: seeded reference defect {d} has a rule outside {', '.join(RULES)}")
        out.add((d["localPart"], d["remoteUrn"], d["rule"]))
    return out


def check_proposed(data, seeded, fail):
    """Every proposed dangling or stale reference defect (`extended.proposedDefects`) names only URNs seeded under its
    rule, and every seeded defect is named by a proposed defect. A stale revision on a variant's bill-of-materials
    line is not an external reference and is left out."""
    key = data["product"]["key"]
    proposed = (data.get("extended") or {}).get("proposedDefects", [])
    texts = [repr(sorted(p.items())) for p in proposed]
    for p, text in zip(proposed, texts):
        rule = "danglingReference" if p.get("rule") in DANGLING_ALIASES else p.get("rule")
        if rule not in RULES or p.get("scope") == "variant BOM line":
            continue
        urns = set(re.findall(r"urn:plm:[a-z]+:[a-z]+:[A-Za-z0-9/._-]*[A-Za-z0-9]", text))
        named = {urn for _, urn, r in seeded if r == rule}
        if not urns or not urns <= named:
            fail(f"{key}: proposed {p.get('rule')} defect names {sorted(urns)}, seeded under {rule}: {sorted(named)}")
    for local, urn, rule in sorted(seeded):
        if not any(urn in text for text in texts):
            fail(f"{key}: seeded reference defect {local} -> {urn} ({rule}) is named by no proposed defect")
