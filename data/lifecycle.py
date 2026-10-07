# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""The lifecycle propagation rule of the product files, for data/generate.py.

Each site releases its items in its own words; the canonical state of a word is its place in the site's lifecycle
vocabulary (WORKING, RELEASED, BLOCKED, SUPERSEDED). `evaluate` applies the lifecycleConflict rule of
ontology/shapes.ttl the way the shape does, over the rows generate.py writes: an item whose state is RELEASED depends on
an item whose state is WORKING or BLOCKED, a dependency being a child of one of its bill-of-materials lines or the part
one of its external references resolves to (the reference rows only; a reference an item that is no row makes is not
written). `seededLifecycleDefects` lists the expected results as `{item, dependency, rule}`.
"""

import references as refs

RULE = "lifecycleConflict"
STATES = ("WORKING", "RELEASED", "BLOCKED", "SUPERSEDED")
UNRELEASED = {"WORKING", "BLOCKED"}


def state(item, vocabularies):
    """The canonical lifecycle state of a part row, or None when its site states no lifecycle."""
    word = item["attributes"][1]
    return None if word is None else STATES[vocabularies[item["plm"]].index(word)]


def evaluate(lines, references, items, vocabularies):
    """{(item, dependency, rule)} of every released item that depends on a working or blocked one; `lines` are the
    product's bill-of-materials lines, `references` the reference rows, `items` every part row by id, `vocabularies`
    the lifecycle words of each PLM in the order of STATES."""
    edges = {(line["parent"], line["child"]) for line in lines}
    for ref in references:
        target = refs.resolve(ref["remoteUrn"])
        if target and target[1] in items and items[target[1]]["plm"] == target[0]:
            edges.add((ref["localPart"], target[1]))
    return {(item, dependency, RULE) for item, dependency in edges
            if state(items[item], vocabularies) == "RELEASED" and state(items[dependency], vocabularies) in UNRELEASED}


def declared(data, fail):
    """The product's `seededLifecycleDefects` as {(item, dependency, rule)}."""
    out = set()
    for d in data.get("seededLifecycleDefects", []):
        if d.get("rule") != RULE or set(d) != {"item", "dependency", "rule"}:
            fail(f"{data['product']['key']}: seeded lifecycle defect {d} needs item, dependency and the rule {RULE}")
        out.add((d["item"], d["dependency"], d["rule"]))
    return out
