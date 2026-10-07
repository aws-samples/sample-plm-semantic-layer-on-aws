# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""The dataset the fixture stack runs (data/products/*.json, every product) and the export-control policy
(ontology/policy.json), with the derivations check.py asserts against: the parts, assemblies and interfaces, the
seeded defects per interface and rule, the native table of each concern per PLM, which items and interfaces a
profile may see, and each site's part occurrences in a product's bill of materials.

Interface ids are unique within a product only, so an interface is keyed "<product key>/<id>" (key()); part,
assembly and feature ids are unique across the products."""
import functools
import glob
import statistics
from decimal import Decimal
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "data"))
import references as refs  # noqa: E402  data/references.py, the reference rules as data/generate.py applies them
BASE = "https://example.com/atelier/"
PRODUCTS = {d["product"]["key"]: d for d in (json.load(open(f, encoding="utf-8")) for f in sorted(glob.glob(str(ROOT / "data" / "products" / "*.json"))))}
PROFILES = json.load(open(ROOT / "ontology" / "policy.json", encoding="utf-8"))["profiles"]
PARTS = {p["id"]: p for d in PRODUCTS.values() for p in d["parts"]}


def key(product, iid):
    return f"{product}/{iid}"


def _assembly(a):
    """An assembly or site kit as the PLM part tables and the core hold it: no CAD file, tagged NONE / ALL by its PLM."""
    return {"id": a["id"], "plm": a["plm"], "name": a["name"], "cadFile": None, "classification": {"jurisdiction": "NONE", "releasableTo": "ALL"},
            "extended": {"revision": a.get("revision"), "lifecycle": a.get("lifecycle"), "type": "ASSEMBLY", "nameEn": a.get("nameEn")}}


ASSEMBLIES = {a["id"]: _assembly(a) for d in PRODUCTS.values() for a in d.get("extended", {}).get("assemblies", [])}


def _non_geometric(i):
    """A software or document item as data/items.py writes it: the local id UK/3511 is the row UK-3511, a version is part
    of the name, no CAD file, a line under its site kit."""
    return {"id": i["localId"].replace("/", "-"), "plm": i["plm"], "cadFile": None, "parent": i["siteKit"].replace("/", "-"),
            "name": f"{i['name']} {i['version']}" if i.get("version") else i["name"],
            "classification": i.get("classification", {"jurisdiction": "NONE", "releasableTo": "ALL"}),
            "extended": {"revision": i.get("revision"), "lifecycle": i.get("lifecycle"), "type": i["type"],
                         "nameEn": f"{i['nameEn']} {i['version']}" if i.get("version") and i.get("nameEn") else i.get("nameEn")}}


NON_GEOMETRIC = {n["id"]: n for d in PRODUCTS.values() for n in map(_non_geometric, d.get("extended", {}).get("nonGeometricItems", []))}
# Every row of the PLM part tables: the parts with geometry, the assemblies, the software and documents.
ITEMS = PARTS | ASSEMBLIES | NON_GEOMETRIC


def english_name(item_id):
    """The English name data/labels.ttl gives an item: a UK item's native name, else the product file's nameEn."""
    item = ITEMS[item_id]
    return item["name"] if item["plm"] == "UK" else (item.get("extended") or {}).get("nameEn")
INTERFACES = {key(k, i["id"]): i | {"product": k} for k, d in PRODUCTS.items() for i in d["interfaces"]}
SPARE_PLUGS = [s for d in PRODUCTS.values() for s in d.get("sparePlugs", [])]
DEFECTS = {key(k, defect["interface"]): {} for k, d in PRODUCTS.items() for defect in d.get("seededDefects", [])}
for _k, _d in PRODUCTS.items():
    for _defect in _d.get("seededDefects", []):
        DEFECTS[key(_k, _defect["interface"])][_defect["rule"]] = _defect["features"]
KINDS = {"pairs": "plug", "fasteners": "fastener", "couplings": "coupling"}
# The native relation of each concern per PLM; "bomline" is the relation a bill-of-materials line reads (DE keeps
# parent and quantity on the child's own part row, ES reads a view over the parent's JSONB list).
TABLES = {"fr": {"part": "piece", "plug": "connecteur", "fastener": "fixation", "coupling": "raccord_hydraulique", "ref": "reference_externe", "bomline": "nomenclature"},
          "de": {"part": "bauteil", "plug": "stecker", "fastener": "befestiger", "coupling": "hydraulikkupplung", "ref": "externer_verweis", "bomline": "bauteil"},
          "uk": {"part": "component", "plug": "harness_connector", "fastener": "fastener", "coupling": "hyd_coupling", "ref": "external_ref", "bomline": "bom_line"},
          "es": {"part": "pieza", "plug": "conector", "fastener": "remache", "coupling": "acoplamiento", "ref": "referencia_externa", "bomline": "linea_lista_materiales"}}
# The concerns an interface run asks each PLM about, in request order: its parts, the three feature kinds, the
# external references its parts make and the bill-of-materials lines from them.
INTERFACE_CONCERNS = ("part", "plug", "fastener", "coupling", "ref", "bomline")
ARMS = {"ontop-fr", "ontop-de", "ontop-uk", "ontop-es", "ontop-core", "neptune"}
PART_IRI = re.compile(r"<https://example\.com/atelier/([a-z]+)/part/([^>]+)>")


def plm(item_id):
    return ITEMS[item_id]["plm"].lower()


def part_iri(item_id):
    return f"{BASE}{plm(item_id)}/part/{item_id}"


def geometric(item):
    """A part with geometry (part type PART, the default): what part counts and occurrences count."""
    return item.get("extended", {}).get("type", "PART") == "PART"


def id_order(native_id):
    """The sort key of Atelier.BY_ID: runs of digits by their value, the rest as text, so IF-99 comes before IF-100."""
    runs = re.findall(r"\d+|\D+", native_id)
    return [(0, int(r), "") if r.isdigit() else (1, 0, r) for r in runs], native_id


def cad_missing_parts(products):
    """The parts that carry cadMissing, sorted: every part with geometry the product files release without a file-index
    entry (cadPending), the same rule as tests/seeded.mjs."""
    return sorted(p["id"] for d in products.values() for p in d["parts"] if p.get("cadPending") and geometric(p))


def demo_pending_part(products, product):
    """The part the scripted demo publishes: the first pending part with geometry of `product` (tests/seeded.mjs)."""
    return next((p["id"] for p in products[product]["parts"] if p.get("cadPending") and geometric(p)), None)


def features_of(iface):
    """Every feature the link store declares on the interface: id, part, kind, mate ids."""
    features = []
    for k, kind in KINDS.items():
        for pair in iface.get(k, []):
            for f in pair:
                features.append(dict(id=f["id"], part=f["part"], kind=kind, mates=[g["id"] for g in pair if g is not f]))
    for f in iface.get("unmatedPlugs", []):
        features.append(dict(id=f["id"], part=f["part"], kind="plug", mates=[]))
    return features


def kinds_of(part_id, ikey):
    return {k: sum(1 for f in features_of(INTERFACES[ikey]) if f["part"] == part_id and f["kind"] == k)
            for k in ("plug", "fastener", "coupling")}


def is_hidden(item_id, profile, untagged=()):
    """Untagged items are hidden from every profile but the officer; tagged ones outside the profile's releasabilities from it."""
    if item_id in untagged:
        return profile != "export-officer"
    return ITEMS[item_id]["classification"]["releasableTo"] not in PROFILES[profile]["releasable"]


def hidden_parts(iface, profile, untagged=()):
    return [p for p in iface["parts"] if is_hidden(p, profile, untagged)]


def visible_ids(profile, untagged=()):
    """Every part and assembly the profile may see, whether or not an interface names it."""
    return {i for i in ITEMS if not is_hidden(i, profile, untagged)}


def hidden_ids(profile, untagged=()):
    return set(ITEMS) - visible_ids(profile, untagged)


def markers(profile, untagged=()):
    """The redaction markers a parts answer ends with: one per hidden part an interface names, in IRI order."""
    named = {p for i in INTERFACES.values() for p in i["parts"]}
    return [{"redacted": True, "plm": plm(p)} for p in sorted(hidden_ids(profile, untagged) & named, key=part_iri)]


def expected_requests(profile, concerns, untagged=(), drives=False, product=None):
    """Requests per arm: the link store twice (its facts, then the file index) and, for the parts and bill-of-materials
    answers (4 concerns) and the placements answer (5: the bill of materials and the placements under the site kit), a
    third time for the names and confirmed equivalences of the visible items, and for a parts answer (drives) a fourth
    for the functional edges the meshModule rule reads; the core four times (the memberships, who tagged each part, the
    tags the profile releases, the products) and, for a run scoped to a product whose visible items reference a part
    outside it, a fifth time for the memberships of those parts; a PLM once per concern when it has a visible item. The
    product-wide counts hold for a run scoped to a product when product is None."""
    visible = visible_ids(profile, untagged)
    plms = {plm(p) for p in visible}
    layer = (2 if drives else 1) if concerns in (4, 5) and plms else 0
    outside = product is not None and references_outside(product, visible)
    return {f"ontop-{p}": (concerns if p in plms else 0) for p in TABLES} | {"ontop-core": 4 + outside, "neptune": 2 + layer}


def references_outside(product, visible):
    """Whether a visible item of the product references, by a well-formed part URN, a part that is not one of its items."""
    def member(target):
        site, key = target
        return PRODUCT_OF.get(key) == product and key in ITEMS and plm(key) == site.lower()
    return any(PRODUCT_OF.get(local) == product and local in visible and refs.resolve(urn) and not member(refs.resolve(urn))
               for d in PRODUCTS.values() for local, urn in ((r["localPart"], r["remoteUrn"]) for r in d.get("extended", {}).get("externalRefs", [])))


def expected_status(iface, profile, untagged=()):
    ikey = key(iface["product"], iface["id"])
    return "not-evaluable" if hidden_parts(iface, profile, untagged) else "fail" if ikey in DEFECTS else "pass"


def tallies(profile, product=None):
    """Interfaces passing, failing and not evaluable for the profile, every part tagged."""
    statuses = [expected_status(i, profile) for i in INTERFACES.values() if product in (None, i["product"])]
    return statuses.count("pass"), statuses.count("fail"), statuses.count("not-evaluable")


def core_tables_of(ikey, visible_parts):
    """The core rows the core arm attributes to the interface: the tag and the product membership of its visible parts."""
    keys = sorted(set(INTERFACES[ikey]["parts"]) & set(visible_parts))
    return [{"plm": "core", "table": table, "keys": keys} for table in ("part_tag", "product_part")]


def tables_of(ikey, plm_code, visible_parts):
    """The native rows of the interface a PLM's arm attributes: its visible parts and the features on them, the
    external references those parts make and the visible parts the references resolve to, per table."""
    rows = {}
    for p in INTERFACES[ikey]["parts"]:
        if plm(p) == plm_code and p in visible_parts:
            rows.setdefault("part", set()).add(p)
    for f in features_of(INTERFACES[ikey]):
        if plm(f["part"]) == plm_code and f["part"] in visible_parts:
            rows.setdefault(f["kind"], set()).add(f["id"])
    for (local, urn, target, _), ref_id in zip(REFERENCES, REFERENCE_IDS):
        if local not in INTERFACES[ikey]["parts"] or local not in visible_parts:
            continue
        if plm(local) == plm_code:
            rows.setdefault("ref", set()).add(ref_id)
        if target and plm(target) == plm_code and target in visible_parts:
            rows.setdefault("part", set()).add(target)
    return [{"plm": plm_code, "table": TABLES[plm_code][kind], "keys": sorted(rows[kind])}
            for kind in INTERFACE_CONCERNS if kind in rows]


def parts_named(request_text, plm_code):
    """Part keys a PLM (or the core) was asked about, from the request text as sent."""
    return {k for p, k in PART_IRI.findall(request_text) if p == plm_code}


def bom_lines(product, products=None):
    """The product file's lines and one line from its site kit to each software or document item."""
    ext = (products or PRODUCTS)[product].get("extended", {})
    return ext.get("bomLines", []) + [{"parent": n["parent"], "child": n["id"], "quantity": 1}
                                      for n in map(_non_geometric, ext.get("nonGeometricItems", []))]


def _children(product, products=None):
    children = {}
    for line in bom_lines(product, products):
        children.setdefault(line["parent"], []).append(line)
    return children


def site_kits(product, products=None):
    return [a for a in (products or PRODUCTS)[product]["extended"]["assemblies"] if a["kind"] == "SITE_KIT"]


def occurrences_by_site(product, profile="export-officer"):
    """Per site (its site kit's PLM): the part occurrences the profile may see and those of the hidden items, the
    quantities of the lines multiplied down from the site kit, over the parts with geometry. A hidden item is not
    expanded: what it holds is counted neither as seen nor as hidden."""
    children, sites = _children(product), {}
    for kit in site_kits(product):
        counts = [0, 0]

        def walk(item, mult):
            for line in children.get(item, []):
                n, hidden = mult * line["quantity"], is_hidden(line["child"], profile)
                if geometric(ITEMS[line["child"]]):
                    counts[hidden] += n
                if not hidden:
                    walk(line["child"], n)
        walk(kit["id"], 1)
        sites[kit["plm"].lower()] = tuple(counts)
    return sites


def occurrences(product, products=None):
    """Every item of the product's bill of materials with its occurrences in the product, all sites together."""
    children, total = _children(product, products), {}

    def walk(item, mult):
        for line in children.get(item, []):
            total[line["child"]] = total.get(line["child"], 0) + mult * line["quantity"]
            walk(line["child"], mult * line["quantity"])
    for kit in site_kits(product, products):
        total[kit["id"]] = 1
        walk(kit["id"], 1)
    return total


def turtle_body(turtle):
    return [l for l in turtle.splitlines() if l.strip() and not l.lower().startswith(("@prefix", "prefix"))]


def _reference(ref):
    """(local part, remote URN, target item id or None, failing rule or None) of one reference a part row makes."""
    target = refs.resolve(ref["remoteUrn"])
    item = target[1] if target and target[1] in ITEMS and ITEMS[target[1]]["plm"] == target[0] else None
    return ref["localPart"], ref["remoteUrn"], item


# Every reference a part row makes, with its target, and the rule it fails (the product's seededReferenceDefects).
# REFERENCE_IDS holds each one's row key: data/generate.py numbers each site's rows XR-nnnn across the products in
# file-name order, the order PRODUCTS is read in.
REFERENCES, REFERENCE_IDS, _ref_no = [], [], {}
for _d in PRODUCTS.values():
    _failing = {(f["localPart"], f["remoteUrn"]): f["rule"] for f in _d.get("seededReferenceDefects", [])}
    for _ref in _d.get("extended", {}).get("externalRefs", []):
        if _ref["localPart"] in ITEMS:
            REFERENCES.append(_reference(_ref) + (_failing.get((_ref["localPart"], _ref["remoteUrn"])),))
            _ref_no[plm(_ref["localPart"])] = _ref_no.get(plm(_ref["localPart"]), 0) + 1
            REFERENCE_IDS.append(f"XR-{_ref_no[plm(_ref['localPart'])]:04d}")


def reference_findings(item_id, profile, untagged=()):
    """The reference rules a visible item's references fail for the profile, sorted: a dangling reference whatever the
    viewer sees (the target does not exist), a stale one only when the profile also sees the target."""
    return sorted(rule for local, _, target, rule in REFERENCES if local == item_id and rule
                  and (rule == "danglingReference" or not is_hidden(target, profile, untagged)))


# Every (item, dependency) the lifecycle rule reports: the products' seededLifecycleDefects, which data/generate.py
# checks are exactly the released items over a working or blocked bill-of-materials child or reference target.
LIFECYCLE_CONFLICTS = [(d["item"], d["dependency"]) for _d in PRODUCTS.values() for d in _d.get("seededLifecycleDefects", [])]


def lifecycle_findings(item_id, profile, untagged=()):
    """One lifecycleConflict per seeded dependency of a visible item that the profile also sees; a hidden dependency
    raises nothing."""
    return ["lifecycleConflict" for item, dependency in LIFECYCLE_CONFLICTS
            if item == item_id and not is_hidden(dependency, profile, untagged)]


def lifecycle_conflicts(product, profile, untagged=()):
    """The seeded (item, dependency) pairs of a product whose two ends the profile sees: what its products answer counts."""
    return [(i, dep) for i, dep in ((d["item"], d["dependency"]) for d in PRODUCTS[product].get("seededLifecycleDefects", []))
            if not is_hidden(i, profile, untagged) and not is_hidden(dep, profile, untagged)]


def reference_status(target, rule, profile, untagged=()):
    """The status the references answer gives a reference: dangling whatever the viewer sees, not evaluable when the
    target exists but is hidden, otherwise stale or ok."""
    if rule == "danglingReference":
        return rule
    if is_hidden(target, profile, untagged):
        return "not-evaluable"
    return rule or "ok"


_OFFER_NO = {}


def _offers(data):
    """The product's supplier offers as {id, part, plm, supplier, days, preferred}, the supplier by name; staging columns
    per site. An offer's id is its key in the site's offer table: data/generate.py numbers each site's offers across the
    products in file-name order, the order PRODUCTS is read in."""
    local = {(p["plm"], (p.get("extended") or {}).get("localId", p["id"])): p["id"] for p in data["parts"]}
    local.update({(p["plm"], p["id"]): p["id"] for p in data["parts"]})
    extended = data.get("extended") or {}
    names = {(plm, list(row.values())[0]): list(row.values())[1] for plm, block in (extended.get("suppliers") or {}).items() for row in block["rows"]}
    out = []
    for plm, block in (extended.get("supplierParts") or {}).items():
        for row in block["rows"]:
            part, supplier, _, days = list(row.values())[:4]
            _OFFER_NO[plm] = _OFFER_NO.get(plm, 0) + 1
            out.append({"id": str(_OFFER_NO[plm]), "part": local[(plm, part)], "plm": plm, "supplier": names[(plm, supplier)], "days": days,
                        "preferred": [v for k, v in row.items() if k in ("bevorzugt", "prefere", "preferido", "preferred")][0]})
    return out


def lead_time_conflicts(offers):
    """The conflictingLeadTime findings of one product's offers, by part: one per pair of preferred offers of a part with
    different lead times, the shorter first, each naming the longer offer as its value."""
    out = {}
    for a in offers:
        for b in offers:
            if a["part"] == b["part"] and a["preferred"] and b["preferred"] and a["days"] < b["days"]:
                out.setdefault(a["part"], []).append({"rule": "conflictingLeadTime", "message":
                    f"preferred offers disagree on the lead time: {a['days']} days from {a['supplier']}, {b['days']} days from {b['supplier']}",
                    "value": {"plm": b["plm"].lower(), "kind": "offer", "id": b["id"]}})
    return out


# Per product: its supplier offers and the names of the suppliers its file lists; then the conflictingLeadTime findings
# of every product, by part.
OFFERS = {key: _offers(data) for key, data in PRODUCTS.items()}
SUPPLIER_NAMES = {key: {list(row.values())[1] for block in ((data.get("extended") or {}).get("suppliers") or {}).values() for row in block["rows"]}
                  for key, data in PRODUCTS.items()}
LEAD_TIME_CONFLICTS = {part: fs for offers in OFFERS.values() for part, fs in lead_time_conflicts(offers).items()}


def kg(part):
    """A part's mass as the layer reads it: the stored number in its column's unit (pounds in the UK PLM), in kg."""
    stored = Decimal(str(part["extended"]["mass"]).replace(",", "."))
    return stored * (Decimal("0.45359237") if part["plm"] == "UK" else 1)


# The product of every item: parts, assemblies, software and documents.
PRODUCT_OF = {i: k for k, d in PRODUCTS.items() for i in [p["id"] for p in d["parts"]]
              + [a["id"] for a in d.get("extended", {}).get("assemblies", [])]
              + [n["id"] for n in map(_non_geometric, d.get("extended", {}).get("nonGeometricItems", []))]}


@functools.lru_cache(maxsize=None)
def hidden_items(product, profile, untagged=()):
    """The items of the product the profile may not see; a product rule is not evaluable while there is one."""
    return frozenset(i for i, k in PRODUCT_OF.items() if k == product and is_hidden(i, profile, untagged))


@functools.lru_cache(maxsize=None)
def mass_scale(profile="export-officer", untagged=()):
    """{part id: product key} of every part of a site whose lower median part mass is more than 100 times the lower
    median of the other sites' medians, sites of at least five parts with a mass (the massScale rule), over the products
    the profile sees whole."""
    out = {}
    for key, d in PRODUCTS.items():
        if hidden_items(key, profile, untagged):
            continue
        sites = {}
        for p in d["parts"]:
            if p.get("extended", {}).get("mass") is not None and p["extended"].get("type", "PART") == "PART":
                sites.setdefault(p["plm"], []).append(p)
        medians = {s: statistics.median_low([kg(p) for p in ps]) for s, ps in sites.items() if len(ps) >= 5}
        for s, m in medians.items():
            others = [v for t, v in medians.items() if t != s]
            if others and m > 100 * statistics.median_low(others):
                out.update({p["id"]: key for p in sites[s]})
    return out


def mass_limit(profile="export-officer", untagged=(), products=None):
    """{product key: status entry} of every product with extended.massLimitKg as the products answer states it for the
    profile: not-evaluable with the number of hidden items, else fail or pass on occurrences times unit mass in kg.
    `products` replaces the product files (a test's fixture products, which no profile's policy hides)."""
    out = {}
    for key, d in (products or PRODUCTS).items():
        limit = d.get("extended", {}).get("massLimitKg")
        if limit is None:
            continue
        hidden = hidden_items(key, profile, untagged) if products is None else ()
        if hidden:
            out[key] = {"status": "not-evaluable", "limitKg": float(limit), "hiddenItems": len(hidden)}
            continue
        occ = occurrences(key, products)
        total = sum(kg(p) * occ.get(p["id"], 0) for p in d["parts"] if p.get("extended", {}).get("mass") is not None)
        out[key] = {"status": "fail" if total > Decimal(limit) else "pass", "limitKg": float(limit)}
    return out
