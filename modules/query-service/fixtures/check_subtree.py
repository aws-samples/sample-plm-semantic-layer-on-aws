#!/usr/bin/env python3
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Asserts the subtree answers in <out dir> against the wind turbine's product file and prints their characteristics.

Each PLM's closure table returns, for a three-level tree, exactly the pairs the product file's lines give, the pair of
the root with itself included, and every subtree round's root lookup on it is an index access (closure-explain.txt,
EXPLAIN ANALYZE as run.sh ran it). A subtree answer holds the items the rounds reach (the root's tree in its site, then
the trees of the items its references name, a hidden item a redacted node not expanded), every PLM request of the run
names roots (VALUES ?root through atelier:contains) and none lists parts, the interfaces answer holds the product's
interfaces with a side in the subtree with the far sides as context, and the product root is the product-wide answer.
The link store is asked about the subtree's items only: the interfaces with a side in the subtree, and the file index
of the items and the context parts. Then a markdown table: requests per endpoint, request bytes, the largest request,
endpoint time, the closure SQL time (the best of five EXPLAIN ANALYZE of each round's root lookup, summed over the
rounds), triples merged, validation time and the STEP files the viewer loads, for the product root and each subtree
root.
"""
import json
import re
import sys

from dataset import INTERFACES, PRODUCTS, bom_lines, is_hidden, plm, refs

out = sys.argv[1]
PRODUCT = "wind-turbine"
PROFILE = "programme-cleared"
HIDING = "de-engineer"
GEARBOX = "D-37073"  # DE gearbox: its tree references no other site
HUB_CONTROL = "UK-3776"  # UK hub control: its tree references DE and FR items
BLADE_SET = "FR3770"  # FR blade set: holds NATIONAL-FR items hidden from de-engineer
CLOSURE_ROOTS = {"de": GEARBOX, "fr": BLADE_SET, "es": "ES-3774", "uk": "UK-3775"}  # a tree of three levels or more on each site
PLM_ENDPOINTS = ("ontop-fr", "ontop-de", "ontop-uk", "ontop-es")
MAX_ROUNDS = 8


def load(name):
    return json.load(open(f"{out}/{name}", encoding="utf-8"))


CHILDREN = {}
for line in bom_lines(PRODUCT):
    CHILDREN.setdefault(line["parent"], []).append(line["child"])
OF_PRODUCT = {p["id"] for p in PRODUCTS[PRODUCT]["parts"]} | {a["id"] for a in PRODUCTS[PRODUCT]["extended"]["assemblies"]} \
    | {c for children in CHILDREN.values() for c in children}
# Each reference's target as (site, id); a round asks about the targets the product holds, the others are dangling.
TARGETS = {}
for ref in PRODUCTS[PRODUCT]["extended"]["externalRefs"]:
    target = refs.resolve(ref["remoteUrn"])
    if ref["localPart"] in OF_PRODUCT and target:
        TARGETS.setdefault(ref["localPart"], []).append((target[0].lower(), target[1]))


def closure(root):
    seen, todo = set(), [root]
    while todo:
        item = todo.pop()
        if item not in seen:
            seen.add(item)
            todo += CHILDREN.get(item, [])
    return seen


def expected(root, profile):
    """Items reached, hidden items, and each round's roots as (site, id): the trees by site, then the referenced items' trees."""
    items, hidden, rounds, pending = set(), set(), [], [(plm(root), root)]
    while pending and len(rounds) < MAX_ROUNDS:
        pending = [(site, i) for site, i in pending if i in OF_PRODUCT and plm(i) == site]
        if not pending:
            break
        reached, todo = set(), [i for _site, i in pending]
        while todo:
            item = todo.pop()
            if item in items or item in reached:
                continue
            reached.add(item)
            if is_hidden(item, profile):
                hidden.add(item)
            else:
                todo += CHILDREN.get(item, [])
        if not reached:
            break
        items |= reached
        rounds.append(sorted(pending))
        pending = sorted({t for item in reached - hidden for t in TARGETS.get(item, []) if t[1] not in items})
    return items, hidden, rounds


def requests(answer):
    """The request texts of an answer, each starting with the prefix block."""
    return ["PREFIX" + r for r in re.split(r"(?:^|\n\n)PREFIX", answer["sparql"]) if r.strip()]


def plm_requests(answer):
    """The requests a PLM received: neither the link store's (GRAPH) nor the core graph's (tags, memberships)."""
    return [r for r in requests(answer) if "GRAPH <" not in r and "atelier:taggedBy" not in r and "atelier:partOf" not in r]


def calls(answer):
    return {c["endpoint"]: c for c in answer["provenance"]["calls"]}


# Each site's closure table on its own database, as run.sh read it with psql: "ancestor|descendant" per row.
for site, root in CLOSURE_ROOTS.items():
    rows = {tuple(line.split("|")) for line in open(f"{out}/closure-{site}.txt", encoding="utf-8").read().split()}
    pairs = {(root, d) for d in closure(root)}
    assert rows == pairs, (site, sorted(rows ^ pairs)[:6])
    assert (root, root) in rows, f"{site}: the identity pair"
    levels, frontier = 0, {root}
    while frontier:
        frontier = {c for item in frontier for c in CHILDREN.get(item, [])}
        levels += 1 if frontier else 0
    assert levels >= 3, (site, root, levels)
    print(f"  closure table {site}: {root} contains {len(rows)} items, {levels} levels, the identity pair included")

for root, profile in ((GEARBOX, PROFILE), (HUB_CONTROL, PROFILE), (BLADE_SET, HIDING), (HUB_CONTROL, HIDING)):
    tag = f"{root}{'-de' if profile == HIDING else ''}"
    items, hidden, rounds = expected(root, profile)
    parts = load(f"subtree-parts-{tag}.json")
    subtree = parts["subtree"]
    shown = {p["id"] for p in parts["parts"] if not p.get("redacted")}
    assert shown == items - hidden, (tag, sorted(shown ^ (items - hidden))[:6])
    markers = sorted(p["plm"] for p in parts["parts"] if p.get("redacted"))
    assert markers == sorted(plm(h) for h in hidden), (tag, markers, hidden)
    assert [[(r["plm"], r["id"]) for r in rnd["roots"]] for rnd in subtree["rounds"]] == rounds, (tag, subtree["rounds"], rounds)
    assert (subtree["root"], subtree["depth"], subtree["items"], subtree["productRules"]) == (root, len(rounds), len(items - hidden), "not-evaluable"), subtree
    plm_texts = plm_requests(parts)
    assert plm_texts and all("VALUES ?root" in r and "atelier:contains" in r and "VALUES ?part" not in r for r in plm_texts), tag
    assert len(plm_texts) == sum(calls(parts)[e]["requests"] for e in PLM_ENDPOINTS), (tag, len(plm_texts))
    for h in hidden:
        assert all(f"/part/{h}>" not in r for r in plm_texts), f"{tag}: hidden {h} named to a PLM"
    print(f"  subtree {tag} as {profile}: {len(items - hidden)} items, {len(hidden)} hidden, rounds {rounds}, "
          f"{len(plm_texts)} PLM requests, none listing parts")

for root in (GEARBOX, HUB_CONTROL):
    items, hidden, _ = expected(root, PROFILE)
    answer = load(f"subtree-interfaces-{root}.json")
    touching = {k.split("/", 1)[1]: i for k, i in INTERFACES.items() if i["product"] == PRODUCT and set(i["parts"]) & items}
    assert {i["id"] for i in answer["interfaces"]} == set(touching), (root, sorted({i["id"] for i in answer["interfaces"]} ^ set(touching)))
    far = {p for i in touching.values() for p in i["parts"]} - items
    context = {p["id"] for i in answer["interfaces"] for p in i["parts"] if p.get("context")}
    assert context == {p for p in far if not is_hidden(p, PROFILE)}, (root, context ^ far)
    assert answer["subtree"]["context"] == len(far), answer["subtree"]
    assert all("VALUES ?part" not in r for r in plm_requests(answer)), root
    bom = load(f"subtree-bom-{root}.json")
    assert bom["root"]["id"] == root and bom["subtree"]["root"] == root, bom["root"]["id"]
    print(f"  subtree {root} interfaces: {len(touching)} with a side in the subtree, {len(context)} context parts")

for name, root in (("parts-subtree", GEARBOX), ("bom-subtree", HUB_CONTROL)):
    call = load(f"mcp-{name}.json")
    assert not call["isError"] and call["content"]["subtree"]["root"] == root, (name, str(call["content"])[:200])
    assert call["content"]["subtree"]["depth"] == len(expected(root, PROFILE)[2]), call["content"]["subtree"]
print(f"  MCP parts and bom with root: {GEARBOX}, {HUB_CONTROL}")



def plan_nodes(node):
    yield node
    for child in node.get("Plans", []):
        yield from plan_nodes(child)


# Each round's root lookup, five EXPLAIN ANALYZE per lookup: "root|site|sql|<plan JSON>" per line.
LOOKUPS = {}
for line in open(f"{out}/closure-explain.txt", encoding="utf-8").read().splitlines():
    root, site, sql, plan = line.split("|", 3)
    plan = json.loads(plan)[0]
    nodes = [n["Node Type"] for n in plan_nodes(plan["Plan"])]
    assert nodes[0] in ("Index Only Scan", "Index Scan", "Bitmap Heap Scan"), (root, site, nodes)
    assert not {"CTE Scan", "Recursive Union", "Seq Scan"} & set(nodes), (root, site, nodes)
    LOOKUPS.setdefault(root, {}).setdefault((site, sql), []).append(plan["Execution Time"])
for root in (GEARBOX, HUB_CONTROL):
    rounds = load(f"subtree-interfaces-{root}.json")["subtree"]["rounds"]
    assert len(LOOKUPS[root]) == sum(len({r["plm"] for r in rnd["roots"]}) for rnd in rounds), (root, LOOKUPS[root].keys())
    assert all(len(times) == 5 for times in LOOKUPS[root].values()), root
CLOSURE_MS = {root: sum(min(times) for times in lookups.values()) for root, lookups in LOOKUPS.items()}
print(f"  closure lookups: {', '.join(f'{r} {len(l)} by index, {CLOSURE_MS[r]:.3f} ms' for r, l in LOOKUPS.items())}")

for root in (GEARBOX, HUB_CONTROL):
    items, _, _ = expected(root, PROFILE)
    answer = load(f"subtree-interfaces-{root}.json")
    links = [r for r in requests(answer) if "GRAPH <" in r]
    assert len(links) == 2 and "VALUES ?side" in links[0] and "VALUES ?part" in links[1], (root, [r[:120] for r in links])
    named = lambda text: set(re.findall(r"/part/([^>]+)>", text))
    assert named(links[0]) == items, (root, sorted(named(links[0]) ^ items)[:6])
    far = {p for k, i in INTERFACES.items() if i["product"] == PRODUCT and set(i["parts"]) & items for p in i["parts"]} - items
    assert named(links[1]) == items | far, (root, sorted(named(links[1]) ^ (items | far))[:6])
    print(f"  link store for {root}: interfaces of {len(items)} items, file index of {len(named(links[1]))} items and context parts")

whole, rooted = load(f"parts-{PRODUCT}.json"), load(f"subtree-parts-{PRODUCT}.json")
assert "subtree" not in rooted and rooted["parts"] == whole["parts"], "the product root is the product-wide answer"


def step_files(parts_answer, interfaces_answer):
    """STEP files the viewer loads: each visible geometric part's CAD file, then each context part's."""
    own = {p["cadFile"] for p in parts_answer["parts"] if not p.get("redacted") and p.get("cadFile")
           and p.get("partType") in (None, "PART")}
    context = {p["cadFile"] for i in interfaces_answer["interfaces"] for p in i["parts"] if p.get("context") and p.get("cadFile")}
    return len(own), len(context - own)


def row(label, interfaces_answer, parts_answer, closure_ms):
    c = interfaces_answer["provenance"]["calls"]
    used = [x for x in c if x["requests"]]
    per = ", ".join(f"{x['endpoint'].removeprefix('ontop-')} {x['requests']}" for x in used)
    own, context = step_files(parts_answer, interfaces_answer)
    step = f"{own}" + (f" + {context} context" if context else "")
    return (f"| {label} | {per} | {sum(x['requestBytes'] for x in c):,} | {max(x['largestRequestBytes'] for x in c):,} | "
            f"{interfaces_answer['timings']['federationMs']} | {closure_ms} | {sum(x['triples'] for x in c):,} | "
            f"{interfaces_answer['timings']['validationMs']} | {step} |")


print()
print("| Root | Requests per endpoint | Request bytes | Largest request (bytes) | Endpoint time (ms) | Closure SQL (ms) | Triples merged | Validation (ms) | STEP files |")
print("|---|---|---|---|---|---|---|---|---|")
print(row(f"`{PRODUCT}` (product root)", load(f"interfaces-{PRODUCT}.json"), whole, "none"))
for label, root in ((f"`{GEARBOX}` gearbox (DE)", GEARBOX), (f"`{HUB_CONTROL}` hub control (UK, references into DE and FR)", HUB_CONTROL)):
    print(row(label, load(f"subtree-interfaces-{root}.json"), load(f"subtree-parts-{root}.json"), f"{CLOSURE_MS[root]:.2f}"))
