#!/usr/bin/env python3
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Asserts the query service responses in <out dir> against the dataset the fixture stack runs
(data/products/*.json, every product) and the export-control policy (ontology/policy.json): the status of every
interface per profile, keyed by product and id, the redaction of hidden parts (their names replaced in the interface
labels), the parts and assemblies listed, the untagged-part report, the data-quality finding of the part released
without a file-index entry (the ornithopter's head hoop), the orphan's candidate mates, the requests each source
received (one per concern), the Ontop query logs (cache hits, no reformulation failure, the native relations read),
the evidence arms, the wind turbine's bill of materials and a part's used-in, the placements, and the MCP tools. The checks naming
one interface or part concern the ornithopter (or the aerial screw's licensed motor) and are scoped to its product."""
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

from dataset import (references_outside, LEAD_TIME_CONFLICTS, lead_time_conflicts, id_order, cad_missing_parts, demo_pending_part, OFFERS, SUPPLIER_NAMES, ARMS, ASSEMBLIES, BASE, DEFECTS, INTERFACE_CONCERNS, INTERFACES, ITEMS, PARTS, PART_IRI, PRODUCTS, PROFILES,
                     REFERENCES, SPARE_PLUGS, TABLES, lifecycle_conflicts, lifecycle_findings, bom_lines, core_tables_of, english_name, expected_requests, expected_status, features_of, geometric,
                     hidden_ids, hidden_parts, is_hidden, key, kinds_of, markers, mass_limit, mass_scale, NON_GEOMETRIC, occurrences,
                     occurrences_by_site, part_iri, parts_named, plm, reference_findings, reference_status, site_kits, tables_of, tallies,
                     turtle_body, visible_ids)

out = sys.argv[1]
CLEARED_FILTER = 'IN ("ALL", "EU", "FR", "DE", "UK", "ES")'
ORN = "ornithopter"  # the product the scripted interface and part checks name
ORN_KEY = lambda iid: key(ORN, iid)
UNTAGGED = "POLE-L-6050"  # the ES left pulley block of the ornithopter, on IF-27 with the FR middle cross beam; its tag row is deleted by run.sh
# Parts released without a file-index entry (cadPending): no cadFile, and the sh:Warning shape reports the finding on them.
CAD_PENDING = set(cad_missing_parts(PRODUCTS))
# The scripted demo's pending part: the ornithopter's head hoop, on IF-31 with the ES tail control cord and IF-47 with
# the FR forward cross beam, both passing. Any other product's pending part carries its own cadMissing finding.
HEAD_HOOP = demo_pending_part(PRODUCTS, "ornithopter")
CAD_MISSING = {"rule": "cadMissing", "message": "no CAD file published in the file index for this part"}
HIDDEN_PART = "a part not visible to your profile"  # stands for a hidden part in an interface label
SPARE_PLUG = PRODUCTS[ORN]["sparePlugs"][0]  # FR-ORN-PCMD-001-J03, unmated on the FR control post at the orphan SERV-6120-C03's position: IF-30's one candidate mate
# Interfaces (pass, fail, not evaluable) per profile with every part tagged, over every product: the talk-track figures.
TALLIES = {profile: tallies(profile) for profile in PROFILES}
# Parts releasable to the officer alone: what hides interfaces from programme-cleared.
OFFICER_ONLY = {p for p in PARTS if PARTS[p]["classification"]["releasableTo"] not in PROFILES["programme-cleared"]["releasable"]}
LICENSED = "HMOT-70090"  # the DE hydraulic motor of the aerial screw: EXPORT-LICENCE, releasable to LICENSED, the officer's alone
SUPPLIER_BUILT = "KNKL-6130-L"  # the UK left knuckle hinge of the ornithopter: built by Forja del Tajo for the UK PLM, releasable to ALL
STRUCTURAL = "FR-ORN-KEEL-001"  # the keel beam: built by its owner, no supplier
EVIDENCE = ("IF-13", "IF-25", "IF-05", "IF-02")  # the ornithopter's position; fastener; unit; hydraulic
INCHES = ("IF-01", "IF-02")  # UK sides stored in inches (and psi), mated to FR millimetre features
BOM_PRODUCT = "wind-turbine"
BOM_HIDING = "de-engineer"  # the wind turbine's NATIONAL-FR and NATIONAL-ES parts and its licensed one are hidden from it
USED_IN = "D-37007"  # the main bearing shaft seal, four per front main bearing unit
SITES = ("fr", "de", "uk", "es")  # the order of the root's site kits
# Ontop's reformulation failure and its sticky aftermath (every later answer a 500), as they appear in the endpoint log.
ONTOP_FAILURES = ("value already present", "OntopResultConversionException", "NullPointerException")


def load(name):
    return json.load(open(f"{out}/{name}", encoding="utf-8"))


def by_id(response):
    return {key(i["product"], i["id"]): i for i in response["interfaces"]}


def redacted(entries):
    return [x for x in entries if x.get("redacted")]


def visible(entries):
    return [x for x in entries if not x.get("redacted")]


def show(title, ifaces, product=ORN):
    print(f"  {title}, {product}:")
    for iid, iface in sorted((v["id"], v) for v in ifaces.values() if v["product"] == product):
        rules = sorted({v["rule"] for v in iface["violations"]})
        kinds = [f["kind"] for f in visible(iface["features"])]
        hidden = [p["plm"] for p in redacted(iface["parts"])]
        print(f"    {iid} {iface['status']:13} {len(kinds):2} features ({kinds.count('plug')} plug, {kinds.count('fastener')} "
              f"fastener, {kinds.count('coupling')} coupling), {len(redacted(iface['features']))} redacted"
              f"{', hidden part ' + ' '.join(hidden) if hidden else ''}  {rules}")


def expected_findings(profile, untagged=()):
    """The data-quality findings of the profile's visible parts, counted per rule: the cadPending parts it may see, the
    reference rules their references fail, the lifecycle conflicts over the dependencies it sees and the parts of a site
    whose masses are in another unit (massScale, over the whole product)."""
    visible_items = visible_ids(profile, untagged)
    counts = {"cadMissing": len(CAD_PENDING & visible_items), "massScale": len(mass_scale(profile, untagged))}
    for item in visible_items:
        for rule in reference_findings(item, profile, untagged) + lifecycle_findings(item, profile, untagged):
            counts[rule] = counts.get(rule, 0) + 1
    return {rule: n for rule, n in sorted(counts.items()) if n}


def finding_rules(item_id, profile, untagged=()):
    """The rules of an item's findings, sorted: cadMissing for a cadPending part, massScale for a part of a mis-scaled
    site, then its failing references and its lifecycle conflicts; none in an answer that runs no shapes (profile None, the bill of materials)."""
    if profile is None:
        return []
    return sorted((["cadMissing"] if item_id in CAD_PENDING else []) + (["massScale"] if item_id in mass_scale(profile, untagged) else [])
                  + reference_findings(item_id, profile, untagged) + lifecycle_findings(item_id, profile, untagged))


# The storage unit of each PLM's mass column, the kg per unit of ontology/units.ttl, and the canonical state of every
# lifecycle word as the atelier:Lifecycle scheme of ontology/atelier.ttl states it.
MASS_UNIT = {"FR": "KiloGM", "DE": "KiloGM", "ES": "KiloGM", "UK": "LB"}
KG_PER = {"KiloGM": 1.0, "LB": 0.45359237}
STATE = {word: state for state, words in {
    "WORKING": ("In Arbeit", "En cours", "Borrador", "Draft"), "RELEASED": ("Freigegeben", "Publié", "Liberado", "Released"),
    "BLOCKED": ("Gesperrt", "Bloqué", "Bloqueado", "Frozen"), "SUPERSEDED": ("Ersetzt", "Remplacé", "Sustituido", "Superseded"),
}.items() for word in words}


def check_attributes(p, source):
    """The item attributes as the product file states them: revision as text, the lifecycle word with its canonical
    state, the mass as stored in the PLM's unit (a part whose file gives another unit is stored unconverted, as the
    cart's trip counter UK-3103 is: 0.6 kg in mass_lb) and in kg, the material, the part type (PART when absent,
    ASSEMBLY for an assembly or site kit, which has no mass and no material)."""
    ext = source.get("extended", {})
    assert p.get("revision") == (None if ext.get("revision") is None else str(ext["revision"])), (p["id"], p.get("revision"))
    assert p.get("lifecycle") == ext.get("lifecycle") and p.get("lifecycleState") == STATE.get(ext.get("lifecycle")), p
    assert p.get("material") == ext.get("material") and p.get("partType") == ext.get("type", "PART"), p
    if ext.get("mass") is None:
        assert "mass" not in p, p
        return
    stored, unit = float(str(ext["mass"]).replace(",", ".")), MASS_UNIT[source["plm"]]
    assert p["mass"]["value"] == stored and p["mass"]["unit"] == unit, (p["id"], p["mass"])
    assert abs(p["mass"]["kg"] - stored * KG_PER[unit]) < 1e-9, (p["id"], p["mass"])


# The seeded meshes of the product files: the parts answer reads the functional edges, so the driven gear of a mesh
# whose two gears the profile sees carries the meshModule finding.
MESHES = [m for data in PRODUCTS.values() for m in data.get("seededMeshDefects", [])]


def mesh_findings(item_id, profile, untagged=()):
    shown = visible_ids(profile, untagged)
    return [m["rule"] for m in MESHES if m["driven"] == item_id and item_id in shown and m["driver"] in shown]


def check_part(p, untagged=(), profile="programme-cleared", offers=False):
    """A visible part, assembly, software or document item: the PLM description and attributes, the file-index CAD file (no
    bucket: cadUrl null; none and a cadMissing finding for a cadPending part; none, and no finding, for an assembly, software
    or a document), its findings (the CAD file, the massScale finding of a part of a mis-scaled site and the reference
    rules, as the profile sees the targets; none when the answer runs no shapes, profile None), and its core tag (none for
    an untagged part). A parts answer (offers) reads the supplier offers and the functional edges too, so a part whose
    preferred offers disagree on the lead time also carries the conflictingLeadTime finding and the driven gear of a
    seeded mesh the meshModule finding."""
    source = ITEMS[p["id"]]
    pending = p["id"] in CAD_PENDING
    conflicts = LEAD_TIME_CONFLICTS.get(p["id"], []) if offers and profile else []
    meshes = mesh_findings(p["id"], profile, untagged) if offers and profile else []
    expected = sorted(finding_rules(p["id"], profile, untagged) + [f["rule"] for f in conflicts] + meshes)
    assert p["plm"] == plm(p["id"]) and p["name"] == source["name"], p
    assert p["cadFile"] == (None if pending else source["cadFile"]) and p["sourceFileRef"] == source["cadFile"], p
    assert "cadUrl" in p and p["cadUrl"] is None, p
    assert ("findings" in p) == bool(expected) and sorted(f["rule"] for f in p.get("findings", [])) == expected, (p["id"], p.get("findings"))
    assert CAD_MISSING in p.get("findings", []) if pending else True, (p["id"], p.get("findings"))
    assert all(f in p.get("findings", []) for f in conflicts), (p["id"], p.get("findings"))
    assert ("supplier" in p) == ("supplier" in source) and p.get("supplier") == source.get("supplier"), (p["id"], p.get("supplier"))
    if offers:
        assert p.get("nameEn") == english_name(p["id"]), (p["id"], p.get("nameEn"), "the labels graph's English name")
    check_attributes(p, source)
    if p["id"] in untagged:
        assert (p["jurisdiction"], p["releasableTo"], p["taggedBy"], p["taggedAt"]) == (None, None, None, None), p
        return
    assert p["jurisdiction"] == source["classification"]["jurisdiction"], p
    assert p["releasableTo"] == source["classification"]["releasableTo"], p
    assert p["taggedBy"] == source["plm"] and p["taggedAt"].startswith("2026-09-01T08:00:00"), p


def check_interface(iface, profile, untagged=()):
    ikey = key(iface["product"], iface["id"])
    source = INTERFACES[ikey]
    hidden = hidden_parts(source, profile, untagged)
    features = features_of(source)
    parts_in_iri_order = sorted(source["parts"], key=part_iri)
    if hidden:
        assert iface["status"] == "not-evaluable" and iface["violations"] == [], ikey
        assert [p.get("id") or ("redacted", p["plm"]) for p in iface["parts"]] == \
               [("redacted", plm(p)) if p in hidden else p for p in parts_in_iri_order], (ikey, iface["parts"])
        assert all(set(x) == {"redacted", "plm"} for x in redacted(iface["features"])), ikey
        assert len(redacted(iface["features"])) == sum(f["part"] in hidden for f in features), ikey
        assert {f["id"] for f in visible(iface["features"])} == {f["id"] for f in features if f["part"] not in hidden}, ikey
        for f in visible(iface["features"]):
            mates = next(x for x in features if x["id"] == f["id"])["mates"]
            assert f["matesWith"] == [{"redacted": True, "plm": plm(next(x for x in features if x["id"] == m)["part"])} for m in mates], (ikey, f["id"], f["matesWith"])
        # The label never spells a hidden part: the visible parts by their own PLM names, the hidden ones as the marker.
        names = {p["id"]: p["name"] for p in visible(iface["parts"])}
        assert iface["label"] == " / ".join(HIDDEN_PART if p in hidden else names[p] for p in parts_in_iri_order), (ikey, iface["label"])
        assert iface["label"] != source["label"], (ikey, iface["label"])
    else:
        rules = sorted({v["rule"] for v in iface["violations"]})
        assert rules == sorted(DEFECTS.get(ikey, {})), (ikey, rules)
        assert iface["status"] == ("fail" if rules else "pass"), ikey
        assert iface["label"] == source["label"], (ikey, iface["label"])
        assert [p["id"] for p in iface["parts"]] == parts_in_iri_order and not redacted(iface["features"]), ikey
        for p in iface["parts"]:
            check_part(p, untagged, profile)
            assert "nameEn" not in p, (ikey, p["id"], "the interfaces answer asks the labels graph for no name")
        assert {f["id"] for f in iface["features"]} == {f["id"] for f in features}, ikey
        for f in iface["features"]:
            source_feature = next(x for x in features if x["id"] == f["id"])
            assert f["kind"] == source_feature["kind"] and f["partId"] == source_feature["part"], (ikey, f["id"])
            assert f["matesWith"] == source_feature["mates"], (ikey, f["id"], f["matesWith"])
        for v in iface["violations"]:
            assert sorted(v["features"]) == sorted(DEFECTS[ikey][v["rule"]]), (ikey, v)
    for p in visible(iface["parts"]):
        assert p["id"] not in hidden


def check_answer(response, profile, untagged=()):
    ifaces = by_id(response)
    assert sorted(ifaces) == sorted(INTERFACES), sorted(ifaces)
    for iface in ifaces.values():
        check_interface(iface, profile, untagged)
    statuses = [i["status"] for i in ifaces.values()]
    expected = [expected_status(i, profile, untagged) for i in INTERFACES.values()]
    assert sorted(statuses) == sorted(expected), (profile, statuses)
    if not untagged:
        assert (statuses.count("pass"), statuses.count("fail"), statuses.count("not-evaluable")) == TALLIES[profile], (profile, statuses)
    assert response["policy"]["profile"] == profile and response["policy"]["releasable"] == PROFILES[profile]["releasable"]
    assert response["policy"]["filter"] == "FILTER (?rel IN (" + ", ".join(f'"{r}"' for r in PROFILES[profile]["releasable"]) + "))"
    assert response["policy"]["untagged"] == [part_iri(p) for p in untagged], response["policy"]
    assert response["findings"] == expected_findings(profile, untagged), (profile, response["findings"])
    return ifaces


def check_requests(response, profile, concerns, untagged=(), drives=False, product=None):
    """The requests each arm received; concerns is what the run asks a PLM with a visible item: 6 for interfaces (its
    parts, the three feature kinds, the references its parts make and the lines from them), 4 for parts (its parts,
    their references, the supplier offers for them and the lines from them), 4 for the bill of materials (its parts,
    their references, the lines from and to them), 5 for the placements (the bill of materials and the placements under
    the site kit); drives for a parts answer, which also reads the functional edges; product for a run scoped to it."""
    calls = {c["endpoint"]: c for c in response["provenance"]["calls"]}
    assert set(calls) == ARMS, set(calls)
    expected = expected_requests(profile, concerns, untagged, drives, product)
    for name, c in calls.items():
        assert c["requests"] == expected[name], (name, c, expected[name])
        assert c["kind"] == ("materialized" if name == "neptune" else "virtual"), c
        if c["requests"] == 0:
            assert c["triples"] == 0, (name, c)
    return calls


def releasabilities(profile):
    return {ITEMS[p]["classification"]["releasableTo"] for p in visible_ids(profile)}


# --- programme-cleared: every tagged national part visible, the parts releasable to the officer alone hidden ------
cleared = load("interfaces.json")
interfaces = check_answer(cleared, "programme-cleared")
show("programme-cleared", interfaces)
assert [i["status"] for i in interfaces.values()].count("fail") == TALLIES["programme-cleared"][1]
officer_only_ifaces = sorted(k for k, i in INTERFACES.items() if OFFICER_ONLY & set(i["parts"]))
assert sorted(k for k, v in interfaces.items() if v["status"] == "not-evaluable") == officer_only_ifaces, "the officer-only parts alone hide interfaces"
licensed_ifaces = sorted(k for k, i in INTERFACES.items() if LICENSED in i["parts"])
assert licensed_ifaces == [key("aerial-screw", i) for i in ("IF-89", "IF-90", "IF-99")], licensed_ifaces
assert set(licensed_ifaces) <= set(officer_only_ifaces)

position = interfaces[ORN_KEY("IF-13")]["violations"]
assert len(position) == 1 and position[0]["detail"]["axis"] == "y" and abs(position[0]["detail"]["deltaMm"] - 4.3) < 1e-9, position
assert "4.3 mm apart on y; tolerance 2.0 mm" in position[0]["message"], position[0]["message"]
connector = interfaces[ORN_KEY("IF-03")]["violations"]
assert len(connector) == 1 and sorted(connector[0]["detail"]["connectorType"]) == ["EN3645", "EN4165"], connector
assert connector[0]["detail"]["pinCount"] == [22, 22], connector  # same pin count, different connector family
fastener = interfaces[ORN_KEY("IF-25")]["violations"]
assert len(fastener) == 1 and sorted(fastener[0]["detail"]["diameterMm"]) == [10.0, 12.0], fastener
unit = interfaces[ORN_KEY("IF-05")]["violations"]
assert {v["rule"] for v in unit} == {"unit"} and {v["features"][0] for v in unit} == {"HL 6180-02"}, unit
assert sorted(v["detail"]["axis"] for v in unit) == ["x", "y", "z"], unit
hydraulic = interfaces[ORN_KEY("IF-02")]["violations"]
assert len(hydraulic) == 1 and hydraulic[0]["rule"] == "hydraulic", hydraulic
bars = sorted(hydraulic[0]["detail"]["ratingBar"])
assert abs(bars[0] - 206.8) < 1e-3 and abs(bars[1] - 344.738) < 1e-3, bars  # FR 206.8 bar vs UK 5000 psi
orphan = interfaces[ORN_KEY("IF-30")]["violations"]
assert len(orphan) == 1 and orphan[0]["detail"]["interface"] == "IF-30", orphan
# The orphan's candidate mates: the unmated FR plug at SERV-6120-C03's position, and nothing else
# (FR-ORN-PCMD-001-J01 is mated; the fasteners there are not plugs).
candidates = orphan[0]["detail"]["candidateMates"]
assert [c["id"] for c in candidates] == [SPARE_PLUG["id"]] == ["FR-ORN-PCMD-001-J03"], candidates
candidate = candidates[0]
assert (candidate["part"], candidate["plm"], candidate["iri"]) == (SPARE_PLUG["part"], "fr", BASE + "fr/plug/FR-ORN-PCMD-001-J03"), candidate
assert (candidate["connectorType"], candidate["pinCount"]) == (SPARE_PLUG["connectorType"], SPARE_PLUG["pinCount"]) == ("M12-A", 4), candidate
c03 = next(u for u in INTERFACES[ORN_KEY("IF-30")]["unmatedPlugs"] if u["id"] == "SERV-6120-C03")
assert all(abs(candidate["position"][a] - float(c03["position"][a])) <= float(INTERFACES[ORN_KEY("IF-30")]["toleranceMm"]) for a in "xyz"), candidate["position"]
assert set(candidate) == {"id", "part", "plm", "iri", "position", "connectorType", "pinCount"}, candidate
print(f"    candidate mate of {orphan[0]['features'][0]}: {candidate['id']} on {candidate['part']} at {candidate['position']} mm ({candidate['connectorType']}, {candidate['pinCount']} pins)")
for v in (position[0], connector[0], fastener[0], hydraulic[0], orphan[0]):
    print("   ", v["message"])

# The head hoop is released without a file-index entry: a cadMissing finding on the part, counted at the top of
# the answer, and its interfaces IF-31 (with the tail control cord) and IF-47 (with the forward cross beam) pass all the same.
# Every other part the product files do not release without a CAD file, on an interface or not, has its file-index
# CAD file and no finding.
assert HEAD_HOOP == "FR-ORN-CERC-001" and HEAD_HOOP in CAD_PENDING, (HEAD_HOOP, CAD_PENDING)
if31, if47 = interfaces[ORN_KEY("IF-31")], interfaces[ORN_KEY("IF-47")]
hoop = next(p for p in if31["parts"] if p["id"] == HEAD_HOOP)
assert if31["status"] == "pass" and if31["violations"] == [] and if47["status"] == "pass", (if31, if47["status"])
assert hoop["cadFile"] is None and hoop["findings"] == [CAD_MISSING], hoop
assert cleared["findings"] == expected_findings("programme-cleared") and cleared["findings"]["cadMissing"] >= 1, cleared["findings"]
assert all(sorted(f["rule"] for f in p.get("findings", [])) == finding_rules(p["id"], "programme-cleared")
           for i in interfaces.values() for p in visible(i["parts"]))
print(f"    finding: {HEAD_HOOP} {hoop['findings'][0]['rule']} ({hoop['findings'][0]['message']}); IF-31 {if31['status']}")
ev31 = load("evidence-IF-31.json")
assert [(s["rule"], s["shape"]) for s in ev31["shacl"]["shapes"]] == [("cadMissing", "https://example.com/atelier/shapes#PartCadShape")], ev31["shacl"]["shapes"]
assert ev31["shacl"]["report"].count("sh:ValidationResult") == 1 and "sh:Warning" in ev31["shacl"]["report"], ev31["shacl"]["report"]
assert "sh:Violation" not in ev31["shacl"]["report"], ev31["shacl"]["report"]

# UK values are stored in inches / psi and pass only because they are converted.
for iid in INCHES:
    features = {f["id"]: f for f in interfaces[ORN_KEY(iid)]["features"]}
    for f in features.values():
        if f["plm"] != "uk" or f["source"]["unit"] != "IN":
            continue
        mate = features[f["matesWith"][0]]
        raw_gap = abs(f["source"]["x"] - mate["positionMm"]["x"])
        mm_gap = max(abs(f["positionMm"][a] - mate["positionMm"][a]) for a in "xyz")
        assert raw_gap > 1000 and mm_gap < 0.002, (f["id"], raw_gap, mm_gap)
hl = next(f for f in interfaces[ORN_KEY("IF-01")]["features"] if f["kind"] == "fastener" and f["plm"] == "uk")
hl_source = next(f for pair in INTERFACES[ORN_KEY("IF-01")]["fasteners"] for f in pair if f["id"] == hl["id"])
for name, value, unit_key in (("diameter", "diameter", "diameterUnit"), ("gripLength", "gripLength", "gripUnit")):
    q = hl["properties"][name]
    assert hl_source[unit_key] == "IN" and q["unit"] == "IN" and q["value"] == float(hl_source[value]), (hl["id"], name, q)
    assert abs(q["mm"] - float(hl_source[value]) * 25.4) < 1e-3, (hl["id"], name, q)
hc = next(f for f in interfaces[ORN_KEY("IF-02")]["features"] if f["id"] == "HC 6190-02")["properties"]["pressureRating"]
assert hc["unit"] == "PSI" and abs(hc["bar"] - 206.843) < 1e-3 and "mm" not in hc, hc  # 3000 psi, the system pressure
nul = next(f for f in interfaces[ORN_KEY("IF-05")]["features"] if f["id"] == "HL 6180-02")
assert nul["source"]["unit"] is None and nul["positionMm"] is None, nul

# One request per concern per source: the link store twice (its facts, then the file index), the core graph three
# times (every product membership, then all named items' tags, then their products), each PLM six times (its
# parts, plugs, fasteners, couplings, the references its parts make and the lines from them) about its visible items.
calls = check_requests(cleared, "programme-cleared", 6)
assert all(c["triples"] > 0 for c in calls.values()), calls
# The parts answer lists every part and assembly the profile may see, whether or not an interface names it, then a
# marker per hidden part an interface names.
parts = load("parts.json")["parts"]
assert {p["id"] for p in visible(parts)} == visible_ids("programme-cleared") and len(visible(parts)) == len(ITEMS) - len(hidden_ids("programme-cleared")), parts
assert hidden_ids("programme-cleared") == OFFICER_ONLY and redacted(parts) == markers("programme-cleared"), redacted(parts)
assert {"redacted": True, "plm": "de"} in redacted(parts), "the licensed motor of the aerial screw is a marker"
for p in visible(parts):
    check_part(p, offers=True)
assert sorted(p["id"] for p in visible(parts) if "findings" in p) == \
    sorted(i for i in visible_ids("programme-cleared") if finding_rules(i, "programme-cleared") or i in LEAD_TIME_CONFLICTS
           or mesh_findings(i, "programme-cleared")), \
    "the head hoop, the parts whose references fail, the lifecycle conflicts, the conflicting lead times and the seeded meshes carry a finding"
assert set(mass_scale().values()) == {"difference-engine"} and not mass_scale("programme-cleared"), \
    "the officer sees the difference engine whole; programme-cleared does not, so massScale is not evaluated for it"
assert sorted((p["id"], p["partType"], p["cadFile"]) for p in visible(parts) if p["id"] in NON_GEOMETRIC) == sorted(
    (i, n["extended"]["type"], None) for i, n in NON_GEOMETRIC.items() if i in visible_ids("programme-cleared")), \
    "every visible software and document item is listed with its part type and no CAD file"
assert sorted(p["id"] for p in visible(parts) if p["partType"] == "ASSEMBLY") == sorted(ASSEMBLIES), "every assembly and site kit is listed"
assert {p["releasableTo"] for p in visible(parts)} == releasabilities("programme-cleared") and "LICENSED" not in releasabilities("programme-cleared"), parts
check_requests(load("parts.json"), "programme-cleared", 4, drives=True)
single = load("interface-IF-13.json")
assert single["interface"] == interfaces[ORN_KEY("IF-13")] and single["sparql"] == cleared["sparql"]
print("    provenance:", {n: (c["requests"], c["triples"], c["ms"]) for n, c in calls.items()})
print(f"    parts answer: {len(visible(parts))} items ({sum(p['partType'] == 'ASSEMBLY' for p in visible(parts))} assemblies), {len(redacted(parts))} markers")

# The warm-up answered all interfaces once per profile before the calls above; the first client call is warm.
# A product list asked while the warm-up was answering its profiles was answered while it still ran, in seconds, not
# after the warm-up's minutes.
during = open(f"{out}/during-warmup-status.txt", encoding="utf-8").read().split()
during_s = float(open(f"{out}/during-warmup-time.txt", encoding="utf-8").read())
if during and during[0] in ("running", "warming") and during[-1] in ("running", "warming"):
    assert load("during-warmup-list.json")["products"] and during_s < 10, (during, during_s)
    print(f"    product list during the warm-up ({during[0]}, then {during[-1]}): answered in {during_s:.2f} s")
else:
    print(f"    product list during the warm-up: the warm-up had ended ({during}); not measured")
warmup = load("warmup.json")
assert warmup["status"] == "done", warmup
assert list(warmup["profiles"]) == list(PROFILES), warmup
assert all(o["status"] == "done" and o["ms"] > 0 for o in warmup["profiles"].values()), warmup
print("    warm-up (ms per profile):", {p: o["ms"] for p, o in warmup["profiles"].items()},
      "total", sum(o["ms"] for o in warmup["profiles"].values()))

def product_wide_findings_dropped(iface):
    """The interface as a single-interface answer states it: its graph holds the interface's two parts, so the findings that
    need a whole product (massScale) are in the full answer only."""
    parts = []
    for p in iface["parts"]:
        kept = [f for f in p.get("findings", []) if f["rule"] != "massScale"]
        parts.append({k: v for k, v in p.items() if k != "findings"} | ({"findings": kept} if kept else {}))
    return iface | {"parts": parts}


# The first call to each interface after the warm-up sends the profile's request texts, which the warm-up
# had every Ontop endpoint translate: the click answers from the reformulation caches.
FIRSTS = {profile: {k: load(f"first-{profile}-{i['product']}-{i['id']}.json") for k, i in sorted(INTERFACES.items())}
          for profile in ("programme-cleared", "de-engineer")}
print("    first call to each interface after warm-up, totalMs (federationMs), slowest five:")
for profile, full_name in (("programme-cleared", "interfaces.json"), ("de-engineer", "interfaces-de.json")):
    full = load(full_name)
    firsts = FIRSTS[profile]
    slowest = sorted(firsts.items(), key=lambda kv: -kv[1]["timings"]["totalMs"])[:5]
    print(f"      {profile}: " + ", ".join(f"{k} {r['timings']['totalMs']} ({r['timings']['federationMs']})" for k, r in slowest))
    for k, r in firsts.items():
        assert r["interface"] == product_wide_findings_dropped(by_id(full)[k]), (profile, k)
        assert r["sparql"] == full["sparql"], (profile, k)


def ontop_log(name):
    """Ontop's query log per source: per request, in log order, its reformulationCacheHit value and the tables it read
    (reported when the request is reformulated, not on a cache hit)."""
    hits, tables = {src: [] for src in TABLES} | {"core": []}, {src: set() for src in TABLES} | {"core": set()}
    for line in open(f"{out}/{name}", encoding="utf-8"):
        src, _, entry = line.partition(" ")
        hits[src].append(re.search(r'"reformulationCacheHit"\s*:\s*(true|false)', entry).group(1) == "true")
        read = re.search(r'"tables":\[([^]]*)\]', entry)
        tables[src] |= set(re.findall(r'\\"([a-z_]+)\\"', read.group(1))) if read else set()
    return hits, tables


(warm, _), (screen, _), (first, _), (products_log, _), (final, final_tables) = (
    ontop_log("ontop-log-warm.txt"), ontop_log("ontop-log-screen.txt"), ontop_log("ontop-log-first.txt"),
    ontop_log("ontop-log-products.txt"), ontop_log("ontop-log-final.txt"))
# Each profile's first screen after the warm-up (the product list, then the first product's parts, placements and
# interfaces) translates nothing: every request it sends to every arm is a cache hit.
for src in screen:
    assert screen[src][:len(warm[src])] == warm[src], src
    window = screen[src][len(warm[src]):]
    assert window and all(window), (src, "every request of a profile's first screen is a cache hit", len(window), window.count(False))
print("    Ontop reformulation cache, every profile's first screen:",
      {src: f"{sum(screen[src][len(warm[src]):])} hits / {len(screen[src]) - len(warm[src])} requests" for src in screen})
singles = 2 * len(INTERFACES)
for src in first:
    assert first[src][:len(screen[src])] == screen[src], src
    window = first[src][len(screen[src]):]
    assert len(window) == singles * (4 if src == "core" else 6) and all(window), (src, "every request of a single-interface call is a cache hit", len(window), window.count(False))
print(f"    Ontop reformulation cache, {singles} single-interface calls:",
      {src: f"{sum(first[src][len(screen[src]):])} hits / {len(first[src]) - len(screen[src])} requests" for src in first})
# A PLM's texts for a product name that site's parts the profile may see: each profile's warm-up answers every product's
# first screen, so the first visit of any profile to any product is answered from the reformulation caches.
for src in products_log:
    assert products_log[src][:len(first[src])] == first[src], src
    window = products_log[src][len(first[src]):]
    assert window and all(window), (src, "every request of every product's first screen is a cache hit", len(window), window.count(False))
print("    Ontop reformulation cache, every product's first screen as every profile:",
      {src: f"{sum(products_log[src][len(first[src]):])} hits / {len(products_log[src]) - len(first[src])} requests" for src in products_log})

# Click latency. The cache-hit assertion above is the exact guard that a click triggers no Ontop translation; these pins
# catch a click that got slower across the board, and measure the click, not the machine. Each profile's single-interface
# calls are measured against the median of the run's twenty full answers, taken under the same load: their median under
# 0.6 of it and their 95th percentile under 1.0; the maximum of 738 single samples is no pin, since one stack-wide stall
# moves it. One absolute backstop, every call under 10 s, which no machine load reaches, fails a regression that slows
# full answers and clicks alike, which a ratio cannot see. The absolute numbers and the load average when the calls
# ended are printed beside them.
CLICK_MEDIAN, CLICK_P95, CLICK_BACKSTOP_MS = 0.6, 1.0, 10_000
quantile = lambda values, q: sorted(values)[min(len(values) - 1, int(q * len(values)))]
full_median = quantile([load(f"interfaces-run{n}.json")["timings"]["totalMs"] for n in range(1, 21)], 0.5)
print(f"    single-interface calls, {open(f'{out}/load-first.txt', encoding='utf-8').read().strip()}; twenty full answers, median {full_median} ms:")
pins = []
for profile, firsts in FIRSTS.items():
    ms = [r["timings"]["totalMs"] for r in firsts.values()]
    median_ms, p95_ms, max_ms = quantile(ms, 0.5), quantile(ms, 0.95), max(ms)
    print(f"      {profile}: median {median_ms}, p95 {p95_ms}, p99 {quantile(ms, 0.99)}, max {max_ms} ms; "
          f"of the full-answer median: median {median_ms / full_median:.2f}, p95 {p95_ms / full_median:.2f}, max {max_ms / full_median:.2f}")
    pins += [(f"{profile} median {median_ms / full_median:.2f} < {CLICK_MEDIAN} x full", median_ms < CLICK_MEDIAN * full_median),
             (f"{profile} p95 {p95_ms / full_median:.2f} < {CLICK_P95} x full", p95_ms < CLICK_P95 * full_median),
             (f"{profile} max {max_ms} < {CLICK_BACKSTOP_MS} ms", max_ms < CLICK_BACKSTOP_MS)]
assert all(ok for _, ok in pins), [name for name, ok in pins if not ok]
print("    Ontop reformulation cache, whole run:",
      {src: f"{sum(final[src])} hits / {len(final[src])} requests" for src in final})
for src in final:
    log = open(f"{out}/ontop-{src}.log", encoding="utf-8", errors="replace").read()
    for failure in ONTOP_FAILURES:
        assert failure not in log, (src, failure)
print("    Ontop endpoint logs: no reformulation failure, no result-conversion failure")
# Every native relation of each PLM is read over the run: its part, feature, external-reference and bill-of-materials line relations.
for src, relations in TABLES.items():
    assert set(relations.values()) <= final_tables[src], (src, sorted(set(relations.values()) - final_tables[src]))
assert {"part_tag", "product_part", "product"} <= final_tables["core"], final_tables["core"]
print("    Ontop relations read:", {src: sorted(t) for src, t in final_tables.items()})

# Twenty back-to-back full answers: per-arm requests and ms.
runs = [load(f"interfaces-run{n}.json") for n in range(1, 21)]
print("    20 full answers, per arm ms (requests):")
for n, r in enumerate(runs, start=1):
    check_requests(r, "programme-cleared", 6)
    rc = {c["endpoint"]: c for c in r["provenance"]["calls"]}
    print(f"      run {n:2}: total {r['timings']['totalMs']:5} ms, federation {r['timings']['federationMs']:5} ms, "
          + ", ".join(f"{name.removeprefix('ontop-')} {c['ms']} ({c['requests']})" for name, c in rc.items()))
mean = lambda values: sum(values) / len(values)
print(f"    mean of 20: total {mean([r['timings']['totalMs'] for r in runs]):.0f} ms, federation "
      f"{mean([r['timings']['federationMs'] for r in runs]):.0f} ms, per arm "
      + ", ".join(f"{name.removeprefix('ontop-')} {mean([c['ms'] for r in runs for c in r['provenance']['calls'] if c['endpoint'] == name]):.1f}"
                  for name in ("ontop-fr", "ontop-de", "ontop-uk", "ontop-es", "ontop-core", "neptune")))

# --- evidence: the same run as /interfaces, cut to the interface; the core arm carries the FILTER, the PLM
# arms name only visible items, the triples and tables are the interface's neighbourhood -----------------
single_calls = {c["endpoint"]: c for c in single["provenance"]["calls"]}
assert {n: (c["requests"], c["triples"]) for n, c in single_calls.items()} == {n: (c["requests"], c["triples"]) for n, c in calls.items()}
all_items = set(ITEMS)
cleared_visible = visible_ids("programme-cleared")
plm_arms = defaultdict(list)


def check_core_requests(arm, release_filter):
    """The core arm's four requests, none naming a part: every product membership (no VALUES, no FILTER), then who tagged
    every part (no FILTER), then the tags the profile releases (the FILTER on the whole pattern, one condition in its SQL),
    then each product's facts once (no VALUES, no FILTER), the memberships having named every member."""
    members_request, tagged_request, releases_request, products_request = arm["sparql"].split("\n\n")
    assert "atelier:partOf" in members_request and "VALUES" not in members_request and "FILTER" not in members_request, members_request
    assert "atelier:taggedBy" in tagged_request and "FILTER" not in tagged_request and "releasableTo" not in tagged_request, tagged_request[:300]
    assert release_filter in releases_request and "atelier:releasableTo ?rel" in releases_request and "OPTIONAL" not in releases_request, releases_request[:300]
    assert "atelier:Product" in products_request and "?product atelier:label" in products_request, products_request[:300]
    assert all("VALUES" not in r and not PART_IRI.findall(r) for r in (members_request, tagged_request, releases_request, products_request))
    members_sql, tagged_sql, releases_sql, products_sql = arm["sql"].split("\n\n")
    assert members_sql.startswith("SELECT") and '"product_part"' in members_sql and "releasable" not in members_sql, members_sql[:400]
    assert tagged_sql.startswith("SELECT") and '"part_tag"' in tagged_sql and '"releasable_to"' not in tagged_sql, tagged_sql[:400]
    assert releases_sql.startswith("SELECT") and '"part_tag"' in releases_sql and len(re.findall(r'"releasable_to" = ', releases_sql)) == len(re.findall(r'"[A-Z]+"', release_filter)), releases_sql[:600]
    assert products_sql.startswith("SELECT") and 'FROM "product"' in products_sql and '"product_part"' not in products_sql, products_sql[:400]
    return releases_sql


for iid in EVIDENCE:
    ev = load(f"evidence-{iid}.json")
    assert (ev["product"], ev["interfaceId"]) == (ORN, iid) and ev["sparql"] == cleared["sparql"] and ev["sparql"].startswith("PREFIX"), iid
    assert ev["policy"] == cleared["policy"], ev["policy"]
    arms = {a["endpoint"]: a for a in ev["arms"]}
    assert set(arms) == ARMS, (iid, set(arms))
    for name, arm in arms.items():
        assert (arm["requests"], arm["tripleCount"]) == (calls[name]["requests"], calls[name]["triples"]), (iid, name, arm["requests"], arm["tripleCount"])
        assert arm["sparql"].count("CONSTRUCT") == arm["requests"], (iid, name, "one CONSTRUCT per request, joined by a blank line")
        if name == "neptune":
            assert arm["sql"] is None and "IN (" not in arm["sparql"] and "VALUES" not in arm["sparql"], (iid, name)
            assert "graph/fileindex" in arm["sparql"] and "atelier:cadFile" in arm["triples"] and arm["tables"] == [], (iid, name)
            continue
        assert arm["kind"] == "virtual", (iid, name)
        for keyword in ("UNION", "MINUS", "BIND("):
            assert keyword not in arm["sparql"], (iid, name, keyword)
        if name == "ontop-core":
            # The rows attributed to the interface are its visible parts' in the tag and membership tables.
            tags_sql = check_core_requests(arm, CLEARED_FILTER)
            assert "'EU'" in tags_sql and "'LICENSED'" not in tags_sql, (iid, tags_sql[:400])
            assert arm["tables"] == core_tables_of(ORN_KEY(iid), cleared_visible), arm["tables"]
            assert "atelier:taggedBy" in arm["triples"] and "atelier:partOf" in arm["triples"], (iid, name)
            continue
        plm_code = name.removeprefix("ontop-")
        # The six requests to a PLM name its visible items (of every product) and carry no FILTER; their
        # SQL (Ontop's, never executed by the service) reads one table each and no classification column.
        assert "IN (" not in arm["sparql"], (iid, name)
        assert parts_named(arm["sparql"], plm_code) == {p for p in cleared_visible if plm(p) == plm_code}, (iid, name)
        sqls = arm["sql"].split("\n\n")
        assert len(sqls) == 6 and all(s.startswith("SELECT") for s in sqls), (iid, name, arm["sql"][:200])
        for s in sqls:
            assert "UNION" not in s and "releasable" not in s and "export_class" not in s, (iid, name, s[:300])
        read = [TABLES[plm_code][c] for c in INTERFACE_CONCERNS]
        assert [t for t in read if any(f'"{t}"' in s for s in sqls)] == read, (iid, name)
        plm_arms[name].append((arm["sparql"], arm["sql"]))
        expected_tables = tables_of(ORN_KEY(iid), plm_code, cleared_visible)
        assert arm["tables"] == expected_tables, (iid, name, arm["tables"], expected_tables)
        body = turtle_body(arm["triples"])
        assert bool(body) == bool(expected_tables), (iid, name, body[:3])
        assert "releasableTo" not in arm["triples"] and "cadFile" not in arm["triples"], (iid, name)
    assert sum(a["tripleCount"] for a in ev["arms"]) == sum(c["triples"] for c in calls.values()), iid
    assert ev["merged"]["triples"] < sum(c["triples"] for c in calls.values()), iid
    assert set(re.findall(r"interface/([^/>\s]+)/(IF-\d+)", ev["merged"]["turtle"])) == {(ORN, iid)}, (iid, "the merged graph is the interface's neighbourhood")
    assert sorted(s["rule"] for s in ev["shacl"]["shapes"]) == sorted(DEFECTS.get(ORN_KEY(iid), {})), ev["shacl"]["shapes"]
    assert all("sh:select" in s["turtle"] for s in ev["shacl"]["shapes"]), iid
    assert ev["shacl"]["report"].count("sh:ValidationResult") == len(interfaces[ORN_KEY(iid)]["violations"]), iid
    print(f"    evidence {iid}: arms", {n: (a["requests"], a["tripleCount"], len(a["tables"])) for n, a in arms.items()},
          "merged", ev["merged"]["triples"], "shapes", sorted(s["rule"] for s in ev["shacl"]["shapes"]))
# Every single-interface run of a profile sends each PLM the same requests, whatever the interface.
for name, sent in plm_arms.items():
    assert len(sent) == len(EVIDENCE) and len(set(sent)) == 1, (name, "one set of request texts and one SQL per profile", len(set(sent)))
core = next(a for a in load(f"evidence-{EVIDENCE[0]}.json")["arms"] if a["endpoint"] == "ontop-core")
print("    core SQL excerpt:", re.search(r'"releasable_to"[^\n]{0,160}', core["sql"]).group(0))
uk = next(a for a in load("evidence-IF-05.json")["arms"] if a["endpoint"] == "ontop-uk")
print(f"    uk: {uk['requests']} requests, sparql {len(uk['sparql'])} B, sql {len(uk['sql'])} B, {uk['sql'].count('UNION ALL')} UNION ALL")

# --- de-engineer: the ornithopter's NATIONAL-FR parts (control post, instrumentation pod) and the licensed hydraulic
# motor are hidden, with the other products' parts outside ALL, EU and DE
de = load("interfaces-de.json")
de_ifaces = check_answer(de, "de-engineer")
show("de-engineer", de_ifaces)
assert {p for p in hidden_ids("de-engineer") if p in {x["id"] for x in PRODUCTS[ORN]["parts"]}} == {"FR-ORN-PCMD-001", "FR-ORN-NACI-001"}
assert LICENSED in hidden_ids("de-engineer"), hidden_ids("de-engineer")
assert {de_ifaces[k]["status"] for k in [ORN_KEY(i) for i in ("IF-03", "IF-15", "IF-30", "IF-43", "IF-44")] + licensed_ifaces} == {"not-evaluable"}
# Hidden parts are never named: IF-44 (control post / instrumentation pod, both NATIONAL-FR) shows two markers, IF-43
# the FR keel beam by its own name and the control post as the marker.
if44_de, if43_de = de_ifaces[ORN_KEY("IF-44")], de_ifaces[ORN_KEY("IF-43")]
assert if44_de["label"] == f"{HIDDEN_PART} / {HIDDEN_PART}", if44_de["label"]
assert not any(word in if44_de["label"].lower() for word in ("poteau", "commande", "nacelle", "instrumentation")), if44_de["label"]
keel = next(p for p in visible(if43_de["parts"]) if p["id"] == STRUCTURAL)
assert if43_de["label"] == f"{keel['name']} / {HIDDEN_PART}" and "Poteau de commande" not in if43_de["label"], if43_de["label"]
assert "Poteau de commande" not in json.dumps(if43_de) and "FR-ORN-PCMD-001" not in json.dumps(if43_de), "the control post leaked into IF-43"
print(f"    hidden labels: IF-44 '{if44_de['label']}'; IF-43 '{if43_de['label']}'")
check_requests(de, "de-engineer", 6)
for name in ("interface-IF-44-de.json", "interface-IF-43-de.json"):
    one = load(name)["interface"]
    assert one["product"] == ORN and one == de_ifaces[ORN_KEY(one["id"])], name
ev = load("evidence-IF-03-de.json")
de_visible = visible_ids("de-engineer")
assert ev["policy"] == de["policy"], ev["policy"]
pod = PARTS["FR-ORN-NACI-001"]
assert "NATIONAL-FR" not in ev["merged"]["turtle"] and pod["cadFile"] not in ev["merged"]["turtle"], "hidden part leaked"
assert "FR-ORN-NACI-001-J01/position" not in ev["merged"]["turtle"] and "fr/plug/FR-ORN-NACI-001-J01>" in ev["merged"]["turtle"], "hidden plug"
# The hidden pod leaves IF-03 not evaluable, so no interface rule reports; the visible harness's own references
# still answer the reference rules (its dangling URN names a pod part number that does not exist).
assert set(re.findall(r"shapes#(\w+)>", ev["shacl"]["report"])) == \
    {f"{r[0].upper()}{r[1:]}Shape" for p in INTERFACES[ORN_KEY("IF-03")]["parts"] if p in de_visible
     for r in reference_findings(p, "de-engineer")}, ev["shacl"]["report"]
arms = {a["endpoint"]: a for a in ev["arms"]}
de_tags_sql = check_core_requests(arms["ontop-core"], 'IN ("ALL", "EU", "DE")')
assert "'DE'" in de_tags_sql and "'FR'" not in de_tags_sql and "'LICENSED'" not in de_tags_sql, de_tags_sql[:400]
assert arms["ontop-core"]["tables"] == core_tables_of(ORN_KEY("IF-03"), de_visible) and arms["ontop-core"]["tables"][0]["keys"] == ["HARN-6200-L"], arms["ontop-core"]["tables"]
parts_de = load("parts-de.json")["parts"]
# The hidden FR pod is never asked of the FR PLM: the run asks FR about its other, visible items.
assert arms["ontop-fr"]["requests"] == 6 and "FR-ORN-NACI-001" not in arms["ontop-fr"]["sparql"] and "FR-ORN-NACI-001" not in arms["ontop-fr"]["sql"]
assert parts_named(arms["ontop-fr"]["sparql"], "fr") == {p["id"] for p in visible(parts_de) if p["plm"] == "fr"} == {p for p in de_visible if plm(p) == "fr"}
assert arms["ontop-fr"]["tables"] == tables_of(ORN_KEY("IF-03"), "fr", de_visible) == [], arms["ontop-fr"]["tables"]
assert arms["ontop-uk"]["tables"] == tables_of(ORN_KEY("IF-03"), "uk", de_visible), arms["ontop-uk"]["tables"]
assert parts_named(arms["ontop-de"]["sparql"], "de") == {p["id"] for p in visible(parts_de) if p["plm"] == "de"}, arms["ontop-de"]["sparql"][:300]
assert LICENSED not in arms["ontop-de"]["sparql"]
assert {p["id"] for p in visible(parts_de)} == de_visible and len(visible(parts_de)) == len(ITEMS) - len(hidden_ids("de-engineer"))
assert redacted(parts_de) == markers("de-engineer"), redacted(parts_de)
assert de["findings"] == expected_findings("de-engineer"), de["findings"]
assert sorted(p["id"] for p in visible(parts_de) if "findings" in p) == \
    sorted(i for i in de_visible if finding_rules(i, "de-engineer") or i in LEAD_TIME_CONFLICTS or mesh_findings(i, "de-engineer")), \
    "the head hoop and the bearing are releasable to all; a reference to a part hidden from DE is neither dangling nor stale"

# --- export-officer sees everything (every seeded defect fails); unknown sees the items releasable to ALL ---------
officer = check_answer(load("interfaces-officer.json"), "export-officer")
show("export-officer", officer)
assert [i["status"] for i in officer.values()].count("fail") == len(DEFECTS) and "not-evaluable" not in {i["status"] for i in officer.values()}
assert officer[ORN_KEY("IF-30")]["violations"][0]["features"] == ["SERV-6120-C03"], officer[ORN_KEY("IF-30")]["violations"]
assert {officer[k]["status"] for k in licensed_ifaces} == {"pass"}, "the officer evaluates the licensed motor's interfaces"
parts_officer = load("parts-officer.json")["parts"]
assert len(parts_officer) == len(ITEMS) and not redacted(parts_officer), parts_officer
assert {p["releasableTo"] for p in parts_officer} == releasabilities("export-officer") and "LICENSED" in releasabilities("export-officer"), parts_officer
for p in parts_officer:
    check_part(p, profile="export-officer", offers=True)
assert load("interfaces-officer.json")["findings"] == expected_findings("export-officer")
# The officer sees every item, so the products answer weighs the cubesat and the ornithopter whole: the cubesat's sites
# exceed the 1U limit, the ornithopter's the rated load of its test stand.
officer_rules = {p["key"]: p.get("findings", []) for p in load("products-officer.json")["products"]}
assert officer_rules["cubesat"] == [{"rule": "massLimit", "message": "cubesat weighs 2.01 kg over every site's parts, more than its limit of 2.0 kg"}], officer_rules["cubesat"]
assert {k for k, f in officer_rules.items() if f} == {k for k, v in mass_limit().items() if v["status"] == "fail"} | set(mass_scale().values()), officer_rules
# Every product file with a mass limit is weighed; the cubesat and the ornithopter are over theirs.
officer_limits = mass_limit()
assert {p["key"]: p["massLimit"] for p in load("products-officer.json")["products"] if "massLimit" in p} == officer_limits, officer_limits
assert {k: officer_limits[k] for k in ("cubesat", "ornithopter")} == \
    {"cubesat": {"status": "fail", "limitKg": 2.0}, "ornithopter": {"status": "fail", "limitKg": 260.5}}, officer_limits
# The products answer counts the lifecycle conflicts on each product's visible items: every seeded one for the officer.
officer_conflicts = {p["key"]: p["lifecycleConflicts"] for p in load("products-officer.json")["products"]}
assert officer_conflicts == {k: len(lifecycle_conflicts(k, "export-officer")) for k in PRODUCTS} == \
    {k: len(d.get("seededLifecycleDefects", [])) for k, d in PRODUCTS.items()}, officer_conflicts
print("  products as export-officer:", {k: [x["rule"] for x in f] for k, f in officer_rules.items() if f}, "lifecycle conflicts", officer_conflicts)
# The product list the screens wait for is the products answer without the product rules, from one request per PLM and no
# validation, for a profile that sees every item and one that does not.
for listing, ruled in (("products-list-officer.json", "products-officer.json"), ("products-list-de.json", "products-de.json")):
    plain = [{k: p[k] for k in ("key", "name", "frame", "partCount")} for p in load(ruled)["products"]]
    answer = load(listing)
    assert answer["products"] == plain, (listing, answer["products"], plain)
    assert answer["timings"]["validationMs"] == 0, answer["timings"]
    assert all(c["requests"] <= 1 for c in answer["provenance"]["calls"] if c["endpoint"] in ("ontop-fr", "ontop-de", "ontop-uk", "ontop-es")), answer["provenance"]["calls"]
    print(f"  {listing}: {len(plain)} products, {answer['timings']['totalMs']} ms against {load(ruled)['timings']['totalMs']} ms for the products answer")
motor = next(p for p in parts_officer if p["id"] == LICENSED)
assert (motor["jurisdiction"], motor["releasableTo"], motor["taggedBy"]) == ("EXPORT-LICENCE", "LICENSED", "DE"), motor
assert sum("supplier" in p for p in parts_officer) == sum("supplier" in p for p in PARTS.values()) > 0, "supplier-built parts"
hinge = next(p for p in parts_officer if p["id"] == SUPPLIER_BUILT)
assert hinge["supplier"] == PARTS[SUPPLIER_BUILT]["supplier"] == "Forja del Tajo, Toledo", hinge
assert "supplier" not in next(p for p in parts_officer if p["id"] == STRUCTURAL), STRUCTURAL
print(f"  supplier: {sum('supplier' in p for p in parts_officer)} supplier-built parts carry it ({SUPPLIER_BUILT}: {hinge['supplier']}); {STRUCTURAL} has none")
unknown = load("interfaces-unknown.json")
check_answer(unknown, "unknown")
check_requests(unknown, "unknown", 6)
parts_unknown = load("parts-unknown.json")["parts"]
assert {p["id"] for p in visible(parts_unknown)} == visible_ids("unknown") and redacted(parts_unknown) == markers("unknown"), parts_unknown
assert {p["releasableTo"] for p in visible(parts_unknown)} == releasabilities("unknown") == {"ALL"}, parts_unknown
print(f"  unknown: {TALLIES['unknown'][2]} not-evaluable, {len(visible(parts_unknown))} visible items (releasable to ALL), {len(redacted(parts_unknown))} redacted")
print("  tallies (pass, fail, not evaluable):", TALLIES)

# --- where-used, impact and export status (REST), then the MCP tools: tools/list names the twenty-five tools and each
# tools/call answer is the REST answer (times aside) under the session's own profile and actor ----------------
used = load(f"where-used-{STRUCTURAL}.json")
assert (used["part"]["id"], used["part"]["plm"]) == (STRUCTURAL, "fr"), used["part"]
used_ifaces = sorted((i["id"] for k, i in INTERFACES.items() if STRUCTURAL in i["parts"]), key=id_order)
assert used_ifaces == ["IF-29", "IF-40", "IF-43", "IF-45", "IF-152", "IF-176"] and [u["id"] for u in used["interfaces"]] == used_ifaces, used["interfaces"]
for u in used["interfaces"]:
    assert u["status"] == interfaces[ORN_KEY(u["id"])]["status"], u
    assert [m["id"] for m in u["mates"]] == [p for p in INTERFACES[ORN_KEY(u["id"])]["parts"] if p != STRUCTURAL], u
    assert u["features"] == kinds_of(STRUCTURAL, ORN_KEY(u["id"])), u
assert used["features"] == {k: sum(kinds_of(STRUCTURAL, ORN_KEY(i))[k] for i in used_ifaces) for k in ("plug", "fastener", "coupling")}
assert used["policy"] == cleared["policy"] and used["sparql"] == cleared["sparql"]
check_requests(used, "programme-cleared", 6)

impact = load("impact-HL-6180-02.json")
assert (impact["feature"]["id"], impact["feature"]["plm"]) == ("HL 6180-02", "uk") and "part" not in impact, impact["feature"]
assert [i["id"] for i in impact["interfaces"]] == ["IF-05"] and impact["interfaces"][0]["status"] == "fail", impact["interfaces"]
assert sorted(impact["interfaces"][0]["rulesFailing"]) == sorted(DEFECTS[ORN_KEY("IF-05")]) == ["unit"], impact["interfaces"][0]
mates = next(f for f in features_of(INTERFACES[ORN_KEY("IF-05")]) if f["id"] == "HL 6180-02")["mates"]
assert sorted(f["id"] for f in impact["interfaces"][0]["features"]) == sorted(["HL 6180-02"] + mates) == ["HL 6110-01", "HL 6180-02"], impact["interfaces"][0]["features"]
impact_part = load("impact-WURZ-R-61080.json")
assert impact_part["part"]["id"] == "WURZ-R-61080" and "feature" not in impact_part
assert [i["id"] for i in impact_part["interfaces"]] == sorted((i["id"] for i in INTERFACES.values() if "WURZ-R-61080" in i["parts"]), key=id_order) == ["IF-13", "IF-17", "IF-158"]

de_status = load(f"export-status-{LICENSED}-de.json")
assert de_status["visible"] is False and de_status["cadAvailable"] is False, de_status
assert de_status["part"] == {"redacted": True, "plm": "de"}, de_status["part"]
assert (de_status["policy"]["profile"], de_status["policy"]["actor"]) == ("de-engineer", "user"), de_status["policy"]
officer_status = load(f"export-status-{LICENSED}-officer.json")
motor = officer_status["part"]
assert officer_status["visible"] is True and officer_status["cadAvailable"] is False, officer_status
assert (motor["id"], motor["jurisdiction"], motor["releasableTo"], motor["taggedBy"]) == (LICENSED, "EXPORT-LICENCE", "LICENSED", "DE"), motor
check_requests(officer_status, "export-officer", 4, drives=True)
assert "supplier" not in motor, "the motor is built by its owner"
hinge_status = load(f"export-status-{SUPPLIER_BUILT}.json")
assert hinge_status["visible"] is True and hinge_status["part"]["supplier"] == "Forja del Tajo, Toledo" and hinge_status["part"]["jurisdiction"] == "NONE", hinge_status["part"]
hinge_de = load(f"export-status-{SUPPLIER_BUILT}-de.json")  # releasable to ALL: de-engineer sees it, supplier included
assert hinge_de["visible"] is True and hinge_de["part"]["supplier"] == "Forja del Tajo, Toledo" and hinge_de["policy"]["profile"] == "de-engineer", hinge_de["part"]
print(f"  where-used {STRUCTURAL}:", [(u["id"], u["status"], [m["id"] for m in u["mates"]], u["features"]) for u in used["interfaces"]])
print("  impact HL 6180-02:", [(i["id"], i["status"], i["rulesFailing"], [f["id"] for f in i["features"]]) for i in impact["interfaces"]])
print(f"  export status {LICENSED}: de-engineer visible", de_status["visible"], "| export-officer", motor["jurisdiction"], motor["releasableTo"])
print(f"  export status {SUPPLIER_BUILT}: programme-cleared supplier {hinge_status['part']['supplier']} | de-engineer visible, supplier {hinge_de['part']['supplier']}")

# --- bill of materials: the wind turbine's tree, which no PLM holds, rolled up per site --------------------------


def nodes(node):
    yield node
    for child in node.get("children", []):
        yield from nodes(child)


def check_bom(bom, profile):
    """The root and its four site kits, the per-site part occurrences of the product file (those the profile may see,
    and those of the hidden items), the total, and the requests: each PLM three times (its items, the lines from and to them)."""
    expected = occurrences_by_site(BOM_PRODUCT, profile)
    root = bom["root"]
    assert bom["product"] == BOM_PRODUCT and (root["id"], root.get("plm"), root["partType"]) == (BOM_PRODUCT, None, "PRODUCT"), {k: root.get(k) for k in ("id", "plm", "partType")}
    kits = {a["plm"].lower(): a["id"] for a in site_kits(BOM_PRODUCT)}
    assert [(k["id"], k["plm"], k["partType"]) for k in root["children"]] == [(kits[s], s, "ASSEMBLY") for s in SITES], root["children"]
    sites = {r["plm"]: (r["occurrences"], r["hiddenOccurrences"]) for r in bom["sites"]}
    assert [r["plm"] for r in bom["sites"]] == list(SITES) and sites == expected, (profile, sites, expected)
    total = bom["total"]
    assert total["plm"] is None and (total["occurrences"], total["hiddenOccurrences"]) == tuple(sum(v[i] for v in expected.values()) for i in (0, 1)), total
    assert abs(total["massKg"] - sum(r["massKg"] for r in bom["sites"])) < 1e-6, total
    assert bom["policy"]["profile"] == profile, bom["policy"]
    check_requests(bom, profile, 4, product=BOM_PRODUCT)
    return sites


bom_officer = load(f"bom-{BOM_PRODUCT}-officer.json")
officer_sites = check_bom(bom_officer, "export-officer")
officer_nodes = list(nodes(bom_officer["root"]))
assert not redacted(officer_nodes) and bom_officer["total"]["hiddenOccurrences"] == 0, "the officer sees every node"
assert {n["id"] for n in officer_nodes} - {BOM_PRODUCT} == set(occurrences(BOM_PRODUCT)), "every item of the product's bill of materials is a node"
assert (bom_officer["root"]["quantity"], bom_officer["root"]["occurrences"]) == (1, 1), bom_officer["root"]
by_item = defaultdict(float)
for n in officer_nodes[1:]:
    by_item[n["id"]] += n["occurrences"]
assert by_item == occurrences(BOM_PRODUCT), "each node's occurrences are its line's quantity multiplied down from its site kit"
bom_hiding = load(f"bom-{BOM_PRODUCT}-de.json")
hiding_sites = check_bom(bom_hiding, BOM_HIDING)
hidden_items = sorted(i for i in occurrences(BOM_PRODUCT) if is_hidden(i, BOM_HIDING))
markers_seen = redacted(list(nodes(bom_hiding["root"])))
# One marker per line to a hidden item under a visible parent, carrying only its PLM, quantity and occurrences;
# the tree never names a hidden item, by id or by name (the core tag request of the run's sparql names every item,
# the FILTER deciding which tags come back, as in every answer).
hidden_lines = [l for l in bom_lines(BOM_PRODUCT) if is_hidden(l["child"], BOM_HIDING) and not is_hidden(l["parent"], BOM_HIDING)]
assert hidden_items and len(markers_seen) == len(hidden_lines) and bom_hiding["total"]["hiddenOccurrences"] > 0, (hidden_items, markers_seen)
assert all(set(m) == {"redacted", "plm", "quantity", "occurrences"} and m["redacted"] is True for m in markers_seen), markers_seen
assert sorted((m["plm"], m["quantity"]) for m in markers_seen) == sorted((plm(l["child"]), l["quantity"]) for l in hidden_lines), markers_seen
tree = json.dumps([bom_hiding["root"], bom_hiding["sites"], bom_hiding["total"]], ensure_ascii=False)
for item in hidden_items:
    assert f'"{item}"' not in tree and f"/{item}>" not in tree and ITEMS[item]["name"] not in tree, (item, "a hidden item is named")
print(f"  bill of materials {BOM_PRODUCT}, export-officer (occurrences, massKg per site):",
      {r["plm"]: (r["occurrences"], round(r["massKg"], 1)) for r in bom_officer["sites"]},
      "total", (bom_officer["total"]["occurrences"], round(bom_officer["total"]["massKg"], 1)))
print(f"  bill of materials {BOM_PRODUCT}, {BOM_HIDING} (occurrences, hidden):", hiding_sites,
      "total hidden", bom_hiding["total"]["hiddenOccurrences"], f"; {len(markers_seen)} markers for {', '.join(hidden_items)}")

used_in = load(f"used-in-{USED_IN}.json")
parents = [l for l in bom_lines(BOM_PRODUCT) if l["child"] == USED_IN]
assert len(parents) == 1 and parents[0]["quantity"] > 1, parents
assert used_in["product"] == BOM_PRODUCT and used_in["part"]["id"] == USED_IN, used_in["part"]
check_part(used_in["part"], profile=None)
parent = parents[0]["parent"]
assert used_in["usedIn"] == [{"id": parent, "plm": plm(parent), "name": ITEMS[parent]["name"], "partType": "ASSEMBLY",
                               "quantity": parents[0]["quantity"], "occurrences": occurrences(BOM_PRODUCT)[parent] * parents[0]["quantity"]}], used_in["usedIn"]
assert used_in["occurrences"] == occurrences(BOM_PRODUCT)[USED_IN], used_in["occurrences"]
check_requests(used_in, "programme-cleared", 4, product=BOM_PRODUCT)
print(f"  used-in {USED_IN}: {[(u['id'], u['quantity']) for u in used_in['usedIn']]}, {used_in['occurrences']} occurrences")

# --- placements: each part's occurrences composed down its site's tree, as data/placements.py composes the staging
# placements, one placements request per site; a hidden item has none, and is never named ------------------------------
import bom as staging_bom  # noqa: E402  data/bom.py and data/placements.py, on the path dataset.py sets
import placements as staging  # noqa: E402
import stations as staging_stations  # noqa: E402  data/stations.py: a box's extent along the station axis


def placement_matrix(p, mm_per_unit=1.0):
    return staging.matrix(p, mm_per_unit)


def check_placements(answer, product, profile):
    tree = staging_bom.Bom(PRODUCTS[product], lambda message: (_ for _ in ()).throw(AssertionError(message)))
    world = staging.world(tree)
    hidden = hidden_ids(profile)
    listed = {p["id"]: p["occurrences"] for p in answer["parts"]}
    assert answer["product"] == product and answer["occurrences"] == sum(len(o) for o in listed.values()), answer["occurrences"]
    assert not set(listed) & hidden and set(listed) <= {i for i in tree.geometric}, sorted(set(listed) & hidden)
    text = json.dumps(answer["parts"])
    for item in hidden:
        assert f'"{item}"' not in text, (item, "a hidden item is named")
    clean = [p for p in listed if not hidden & ancestors(tree, p)]
    for part in clean:
        expected, got = world[part], [placement_matrix(o) for o in listed[part]]
        assert len(expected) == len(got), (part, len(expected), len(got))
        for e, g in zip(expected, got):
            assert all(abs(e[i][j] - g[i][j]) < (2e-3 if j == 3 else 1e-4) for i in range(3) for j in range(4)), (part, e, g)
        assert staging.is_identity(got[0]), (part, "the reference occurrence comes first")
    check_requests(answer, profile, 5, product=product)
    return clean


def ancestors(tree, item, seen=None):
    seen = set() if seen is None else seen
    for parent in tree.parents.get(item, []):
        if parent not in seen:
            seen.add(parent)
            ancestors(tree, parent, seen)
    return seen


for product in ("rover", "wind-turbine", "difference-engine"):
    answer = load(f"placements-{product}.json")
    checked = check_placements(answer, product, "export-officer")
    print(f"  placements {product}: {len(answer['parts'])} parts, {answer['occurrences']} occurrences, {len(checked)} compared with the staging composition")
# The core is asked about every part, or about a product's parts by the product key, never part by part: the core's
# request bytes do not grow with the parts; a product's answer adds only the memberships of the few parts its references
# name outside it.
core_bytes = lambda name: next(c["requestBytes"] for c in load(name)["provenance"]["calls"] if c["endpoint"] == "ontop-core")
scoped_core = {name: core_bytes(name) for name in ("placements-rover.json", "placements-wind-turbine.json",
                                                   "placements-difference-engine.json", "parts-wind-turbine.json", "interfaces-wind-turbine.json")}
print(f"  ontop-core request bytes: product-wide {core_bytes('interfaces.json')}, scoped to a product {sorted(set(scoped_core.values()))}")
assert core_bytes("interfaces.json") < 2048 and all(n < 3072 for n in scoped_core.values()), (core_bytes("interfaces.json"), scoped_core)
rover_wheels = [p for p in load("placements-rover.json")["parts"] if p["id"] == "FR3801"]
assert len(rover_wheels) == 1 and len(rover_wheels[0]["occurrences"]) == 6, rover_wheels
blade_tips = [p for p in load("placements-wind-turbine.json")["parts"] if p["id"] == "FR3717"]
assert len(blade_tips) == 1 and len(blade_tips[0]["occurrences"]) == 3, blade_tips
turbine_de = load("placements-wind-turbine-de.json")
check_placements(turbine_de, "wind-turbine", BOM_HIDING)
assert turbine_de["occurrences"] < load("placements-wind-turbine.json")["occurrences"], turbine_de["occurrences"]
blade_set = load("placements-subtree-FR3770.json")
assert blade_set["subtree"]["root"] == "FR3770" and blade_set["parts"], blade_set.get("subtree")
subtree_tips = [p["occurrences"] for p in blade_set["parts"] if p["id"] == "FR3717"]
assert subtree_tips == [blade_tips[0]["occurrences"]], ("the blade set sits at its reference occurrence", subtree_tips)
print(f"  placements wind-turbine as {BOM_HIDING}: {turbine_de['occurrences']} occurrences; blade set FR3770: "
      f"{len(blade_set['parts'])} parts, {blade_set['occurrences']} occurrences")

# --- external references: every reference of a product's visible parts with the shapes' verdict, the failing ones
# for programme-cleared (who sees every target) exactly the product's seededReferenceDefects ---------------------------
for product, data in PRODUCTS.items():
    own = {p["id"] for p in data["parts"]} | {a["id"] for a in data.get("extended", {}).get("assemblies", [])} \
        | {i["localId"].replace("/", "-") for i in data.get("extended", {}).get("nonGeometricItems", [])}
    seeded = {(d["localPart"], d["remoteUrn"], d["rule"]) for d in data.get("seededReferenceDefects", []) if d["localPart"] in ITEMS}
    for profile, name in (("programme-cleared", f"references-{product}.json"), ("de-engineer", f"references-{product}-de.json")):
        answer = load(name)
        got = sorted((r["part"], r["remoteUrn"], r["status"]) for r in answer["references"])
        want = sorted((local, urn, reference_status(target, rule, profile)) for local, urn, target, rule in REFERENCES
                      if local in own and not is_hidden(local, profile))
        assert got == want, (product, profile, sorted(set(got) ^ set(want)))
        statuses = [r["status"] for r in answer["references"]]
        assert answer["counts"] == {status: statuses.count(status) for status in sorted(set(statuses))}, answer["counts"]
        if profile == "programme-cleared":
            failing = {r for r in got if r[2] in ("danglingReference", "staleRevision")}
            assert failing == seeded, (product, sorted(failing ^ seeded))
    print(f"  references {product}: {load(f'references-{product}.json')['counts']} (de-engineer {load(f'references-{product}-de.json')['counts']})")

# The rover's purchased items and suppliers. Its M3 socket head cap screw is bought by all four sites under four part
# numbers, DE and FR in millimetres, ES and UK in inches: one equivalence group. Every supplier its file lists is named,
# each single-source part the profile may see has one offer in the file, and the conflicting lead times are the file's.
PURCHASING = "rover"
SCREW = {"D-38014", "FR3811", "ES-3826", "UK-3823"}
equivalents = load("equivalents-rover.json")
screw = [g for g in equivalents["groups"] if SCREW <= {m["id"] for m in g["members"]}]
assert len(screw) == 1 and {m["id"] for m in screw[0]["members"]} == SCREW, equivalents["groups"]
value = lambda m, attribute: next(v for v in m["values"] if v["attribute"] == attribute)
assert screw[0]["itemClass"] == "fastener" and [(a["attribute"], a.get("text"), a.get("mm")) for a in screw[0]["attributes"]] \
    == [("standard", "ISO 4762", None), ("nominalDiameter", None, 3.0), ("nominalLength", None, 10.0)], screw[0]
assert {m["id"]: value(m, "nominalDiameter")["unit"] for m in screw[0]["members"]} == {"D-38014": "MilliM", "FR3811": "MilliM", "ES-3826": "IN", "UK-3823": "IN"}
assert all(abs(value(m, "nominalDiameter")["mm"] - 3.0) <= 0.01 and abs(value(m, "nominalLength")["mm"] - 10.0) <= 0.01
           for m in screw[0]["members"]), screw[0]
assert screw[0]["stocking"] == {"partNumbers": 4, "sites": 4, "stockLines": 4, "stockLinesOnceConfirmed": 1}, screw[0]["stocking"]
assert not any(g["confirmed"] for g in equivalents["groups"]), "the released links graph confirms no group: each is a proposal"
assert all(m["iri"] == part_iri(m["id"]) for g in equivalents["groups"] for m in g["members"]), equivalents["groups"]
check_requests(equivalents, "programme-cleared", 4, product=PURCHASING)
# The O-rings: DE names the size by its ISO 3601-1 code, which the size concept resolves to millimetres; UK and ES state
# the dimensions, UK in inches; each writes the compound in its language. FKM on the same size stays out.
RINGS = {"wind-turbine": [["D-37056", "UK-3741"], ["D-37057", "ES-3742"]], "steam-engine": [["D-33009", "UK-3306", "ES-3308"]]}
for product, expected in RINGS.items():
    groups = [g for g in load(f"equivalents-{product}.json")["groups"] if g["itemClass"] == "o-ring"]
    assert sorted(sorted(m["id"] for m in g["members"]) for g in groups) == sorted(sorted(e) for e in expected), groups
    assert all(g["shelfLifeMonths"] and g["stocking"]["stockLinesOnceConfirmed"] == 1 for g in groups), groups
    assert all(v.get("fromStandard") for g in groups for m in g["members"] if m["plm"] == "de" for v in m["values"] if "mm" in v and v["attribute"] != "compound"), groups
    print(f"  equivalents {product}: o-ring {[[m['id'] for m in g['members']] for g in groups]}")
# The ornithopter's raw silk cord and ballast can: three sites, three part numbers each, UK in inches, each writing the
# material in its language; the cord's material is the part's own atelier:material, resolved in atelier:Materials.
ORN_ITEMS = {"cord": ["CPED-6040", "LACE-6165-L", "SCHN-R-61065"], "canister": ["CAN-6270", "FR-ORN-BIDN-001", "KANI-61140"]}
orn_groups = {g["itemClass"]: sorted(m["id"] for m in g["members"]) for g in load("equivalents-ornithopter.json")["groups"]}
assert orn_groups == ORN_ITEMS, orn_groups
print(f"  equivalents ornithopter: {orn_groups}")
suppliers = load("suppliers-rover.json")
assert {s["name"] for s in suppliers["suppliers"]} == SUPPLIER_NAMES[PURCHASING], suppliers["suppliers"]
offered = defaultdict(list)
for o in OFFERS[PURCHASING]:
    offered[o["part"]].append(o)
assert [(s["plm"], s["id"]) for s in suppliers["singleSource"]] == sorted(((ITEMS[p]["plm"].lower(), p) for p, os in offered.items()
                                                                           if len(os) == 1 and p in visible_ids("programme-cleared")),
                                                                          key=lambda k: (SITES.index(k[0]), k[1])), suppliers["singleSource"]
assert [(c["id"], c["message"]) for c in suppliers["conflicts"]] == [(p, f["message"]) for p, fs in lead_time_conflicts(OFFERS[PURCHASING]).items() for f in fs], suppliers["conflicts"]
# Each offer is named by its key in the site's offer table: the row the file numbers it as, with that row's part, lead time and flag.
by_key = {(o["plm"].lower(), o["id"]): (o["part"], o["days"], o["preferred"]) for o in OFFERS[PURCHASING]}
assert all(by_key.get((site["plm"], o["id"])) == (o["partId"], o["leadTimeDays"], o["preferred"])
           for s in suppliers["suppliers"] for site in s["sites"] for o in site["offers"]), suppliers["suppliers"]
check_requests(suppliers, "programme-cleared", 4, drives=True, product=PURCHASING)
print(f"  equivalents {PURCHASING}: {[(g['itemClass'], g['attributes'][0].get('text'), len(g['members'])) for g in equivalents['groups']]}; suppliers "
      f"{len(suppliers['suppliers'])}, single-source {len(suppliers['singleSource'])}, conflicts {[c['id'] for c in suppliers['conflicts']]}")

# Terms over the labels graph: Zahnrad, roue (in the gear's roue dentée) and gear name term/gear, whose items are at the
# German, French and British sites, each matched in its own language; the run asks the labels graph once, the core twice
# and no PLM.
GEAR = "https://example.com/atelier/term/gear"
gear = {}
for word in ("Zahnrad", "roue", "gear"):
    answer = load(f"terms-{word}.json")
    terms = {t["iri"]: t for t in answer["terms"]}
    assert GEAR in terms, (word, list(terms))
    gear[word] = terms[GEAR]
    assert {n: c["requests"] for n, c in ((c["endpoint"], c) for c in answer["provenance"]["calls"]) if c["requests"]} == {"neptune": 1, "ontop-core": 3}, answer["provenance"]
assert gear["Zahnrad"]["match"] == "exact" and gear["roue"]["match"] == "word" and gear["gear"]["match"] == "exact"
assert gear["Zahnrad"]["parts"] == gear["roue"]["parts"] == gear["gear"]["parts"], "one concept, one list of items"
found = {(p["plm"], p["id"]): p for p in gear["gear"]["parts"]}
assert {plm for plm, _ in found} >= {"de", "fr", "uk"}, sorted(found)
assert found[("de", "D-31001")]["matched"] == {"lang": "de", "label": "Zahnrad"} and found[("de", "D-31001")]["name"] == ITEMS["D-31001"]["name"]
assert found[("fr", "FR3650")]["matched"] == {"lang": "fr", "label": "pignon"} and found[("fr", "FR3650")]["nameEn"] == english_name("FR3650")
assert all(not is_hidden(i, "programme-cleared") for _, i in found), "programme-cleared is answered no item it may not see"
print(f"  terms: Zahnrad, roue and gear name {GEAR}: {len(found)} items, sites {sorted({p for p, _ in found})}")

TOOLS = ["products", "list_interfaces", "parts", "interface_check", "where_used", "impact_of_change", "export_status", "bom", "bom_where_used",
         "path_between", "flow_path", "variant_diff", "external_references", "equivalent_parts", "find_term", "find_parts", "suppliers", "parts_between_stations",
         "section_joints", "evidence", "ontology", "sparql", "preview_correction", "catalogue", "sql"]
tools = load("mcp-tools.json")
assert [t["name"] for t in tools] == TOOLS, [t["name"] for t in tools]
assert all(t["description"].endswith("Prefer a named tool; use sparql only when no named tool answers.") for t in tools)
assert all(t["inputSchema"]["type"] == "object" for t in tools)


def mcp(name):
    r = load(f"mcp-{name}.json")
    return r["isError"], r["content"]


err, onto = mcp("ontology")
assert not err and list(onto) == ["version", "prefixes", "classes", "properties", "graphs", "rules", "examples"], onto
assert onto["version"] and len(onto["examples"]) == 3, onto
assert {g["iri"] for g in onto["graphs"]} == {BASE + "graph/links", BASE + "graph/fileindex"}, onto["graphs"]
assert {p["iri"] for p in onto["properties"]} >= {"atelier:ownedBy", "atelier:connectorType", "atelier:matesWith", "qudt:unit"}, onto["properties"]
assert {r["name"] for r in onto["rules"]} >= {"unit", "position", "connector", "cadMissing", "massLimit", "massScale"}, onto["rules"]
assert load("mcp-ontology.json")["bytes"] < 32 * 1024, load("mcp-ontology.json")["bytes"]
print(f"  ontology: version {onto['version']}, {len(onto['classes'])} classes, {len(onto['properties'])} properties, {len(onto['rules'])} rules, {load('mcp-ontology.json')['bytes']} B")


# Variant diffs: each option against its group's default, figures as data/generate.py computes them for each configuration.
import generate as gen  # noqa: E402  data/generate.py: the configurations, their rules and their roll-ups

gen_products = gen.load()
gen_parts = gen.check(gen_products)
variant_count = 0
for data in gen_products:
    product = data["product"]["key"]
    for group in data["groups"]:
        configs = data["configurations"][group.key]
        for option in group.options[1:]:
            answer = load(f"variant-{product}-{option.key}.json")
            assert answer["group"]["defaultOption"] == group.default.key and answer["option"]["key"] == option.key, answer["group"]
            for side, config in (("configuration", configs[option.key]), ("baseline", configs[group.default.key])):
                failing = len({i for i, _, _ in gen.evaluate({"interfaces": config.interfaces})})
                got = answer[side]
                assert got["interfaces"] == len(config.interfaces), (product, option.key, side, got["interfaces"])
                assert got["tally"] == {"pass": len(config.interfaces) - failing, "fail": failing, "notEvaluable": 0}, (product, side, got["tally"])
                occurrences, mass = gen.configuration_mass(data, config, gen_parts)
                assert got["occurrences"] == {plm.lower(): float(n) for plm, n in occurrences.items() if n}, (product, side, got["occurrences"])
                assert abs(got["massKg"] - float(mass)) < 0.01, (product, option.key, side, got["massKg"], mass)
            assert answer["removed"]["interfaces"] == sorted(group.default.ids["interfaces"]), answer["removed"]
            assert answer["added"]["interfaces"] == sorted(option.object_ids("interfaces")), answer["added"]
            assert {p["change"] for p in answer["ports"]} <= {"added", "removed", "changed", "same", "not-modelled"}
            assert all(p.get("port") not in option.not_modelled + group.default.not_modelled or p["change"] == "not-modelled"
                       for p in answer["ports"]), answer["ports"]
            # A part the DE engineer may not see is a marker in the diff, never an id.
            de = load(f"variant-{product}-{option.key}-de.json")
            de.pop("sparql")
            hidden_options = [o["id"] for o in option.objects["parts"] if o["classification"]["releasableTo"] not in PROFILES["de-engineer"]["releasable"]]
            text = json.dumps(de)
            assert not [i for i in hidden_options + sorted(hidden_ids("de-engineer")) if f'"{i}"' in text], (product, option.key)
            variant_count += 1
            print(f"  variant {product} {group.key}/{option.key}: {answer['configuration']['tally']} against {answer['baseline']['tally']}; "
                  f"{len(answer['ports'])} ports ({', '.join(sorted({p['change'] for p in answer['ports']}))}); "
                  f"occurrences {answer['configuration']['occurrencesTotal']:.0f} against {answer['baseline']['occurrencesTotal']:.0f}; "
                  f"{answer['configuration']['massKg']:.1f} kg against {answer['baseline']['massKg']:.1f} kg; DE hides {len(hidden_options)} option parts")
assert variant_count >= 4, variant_count


# Configured answers: the parts and placements of each option's configuration, every occurrence composed down its own lines.
def configured_counts(data, config):
    """Per item, its occurrences down the configuration's lines from the site kits: each line's placements, or one."""
    children = {}
    for line in config.lines:
        children.setdefault(line["parent"], []).append(line)
    counts = {}
    for kit in data["bom"].kit.values():
        stack = [(kit, 1)]
        while stack:
            item, n = stack.pop()
            for line in children.get(item, []):
                m = n * len(line.get("placements") or [None])
                counts[line["child"]] = counts.get(line["child"], 0) + m
                stack.append((line["child"], m))
    return counts


for data in gen_products:
    product = data["product"]["key"]
    for group in data["groups"]:
        for option in group.options[1:]:
            config = data["configurations"][group.key][option.key]
            counts = configured_counts(data, config)
            placed = {p["id"]: len(p["occurrences"]) for p in load(f"option-placements-{product}-{option.key}.json")["parts"]}
            assert set(placed) <= config.items, (product, option.key, sorted(set(placed) - config.items))
            assert not set(placed) & set(group.default.ids["parts"]), (product, option.key, "a part of the default option is placed")
            own = [o["id"] for o in option.objects["parts"] if o.get("cadFile")]
            assert set(own) <= set(placed), (product, option.key, sorted(set(own) - set(placed)))
            wrong = {i: (n, counts.get(i)) for i, n in placed.items() if n != counts.get(i)}
            assert not wrong, (product, option.key, wrong)
            listed = {p["id"] for p in load(f"option-parts-{product}-{option.key}.json")["parts"] if "id" in p}
            assert set(o["id"] for o in option.objects["parts"]) <= listed and not listed & set(group.default.ids["parts"]), (product, option.key)
            hidden = [o["id"] for o in option.objects["parts"] if o["classification"]["releasableTo"] not in PROFILES["de-engineer"]["releasable"]]
            de_listed = {p["id"] for p in load(f"option-parts-{product}-{option.key}-de.json")["parts"] if "id" in p}
            assert not de_listed & set(hidden), (product, option.key, sorted(de_listed & set(hidden)))
            print(f"  configured {product} {option.key}: {len(listed)} parts listed, {len(placed)} placed at {sum(placed.values())} occurrences, "
                  f"each as its configuration's lines give it; DE hides {len(hidden)} option parts")
onshore = load("option-placements-wind-turbine-onshore.json")
base_turbine = load("placements-wind-turbine.json")
assert onshore["parts"] == base_turbine["parts"], "the default option's placements are the base product's"
offshore_kit = [p for p in load("option-placements-wind-turbine-offshore.json")["parts"] if p["id"] == "ES-3741"]
assert len(offshore_kit) == 1 and len(offshore_kit[0]["occurrences"]) == 48, offshore_kit
assert "option triple-expansion" in load("option-unknown.json")["error"], load("option-unknown.json")
listed_groups = load("variants-ornithopter.json")["groups"]
assert [(g["key"], g["defaultOption"], g["options"]) for g in listed_groups] == sorted(
    (g.key, g.default.key, [o.key for o in g.options]) for d in gen_products if d["product"]["key"] == "ornithopter" for g in d["groups"]), listed_groups
unknown_option = load("variant-unknown.json")
assert "acting (double-acting, single-acting)" in unknown_option["error"], unknown_option
err, mcp_variant = mcp("variant-diff")
assert not err and "sparql" not in mcp_variant and mcp_variant["option"]["key"] == "single-acting", mcp_variant

# find_parts: an assembly whose name holds the words stands for its parts; a German word finds a German compound.
err, wings = mcp("find-parts")
wing_kits = {g["assembly"]["id"]: [p["id"] for p in g["parts"]] for g in wings["groups"] if g["match"] == "assembly"} if not err else {}
assert not err and "sparql" not in wings and set(wing_kits) == {"BAUS-61000", "KIT-6100-L"}, wings
assert {"HOLM-R-61010", "SPAR-6110-L"} <= set(wing_kits["BAUS-61000"]) | set(wing_kits["KIT-6100-L"]), wing_kits
assert "SPAR-6110-L" in wing_kits["KIT-6100-L"] and "HOLM-R-61010" in wing_kits["BAUS-61000"], wing_kits
err, motors = mcp("find-parts-de")
assert not err and sorted(p["id"] for g in motors["groups"] for p in g["parts"]) == ["D-38001", "D-38002"], motors
assert all(g["matched"]["via"] in ("name", "nameEn", "term", "partType") for g in wings["groups"] + motors["groups"]), wings
assert {g["matched"]["via"]: g["matched"].get("lang") for g in motors["groups"]} == {"name": "de"}, motors
# Redacted at query time: the blade parts the DE engineer may not see are counted, never named.
err, blades = mcp("find-parts-hidden")
found_blades = [p["id"] for g in blades["groups"] for p in g["parts"]] if not err else []
assert not err and found_blades and not [i for i in found_blades if is_hidden(i, BOM_HIDING)] and blades["hidden"] > 0, blades
print(f"  find_parts: both wings are {sorted(wing_kits)}; Getriebemotoren finds D-38001 and D-38002 by the DE name;"
      f" as {BOM_HIDING}, {len(found_blades)} blade parts named and {blades['hidden']} hidden")


def timeless(answer):
    """The answer without its measured times, its presigned URLs and its Turtle text."""
    return json.loads(json.dumps(answer), object_hook=lambda d: {k: v for k, v in d.items() if k not in ("ms", "timings", "cadUrl", "triples")})


err, prods = mcp("products")
expected_products = [(k, sum(p["id"] in cleared_visible and geometric(p) for p in d["parts"])) for k, d in sorted(PRODUCTS.items())]
assert not err and [(p["key"], p["partCount"]) for p in prods["products"]] == expected_products, (prods, expected_products)
assert dict(expected_products)["aerial-screw"] == len(PRODUCTS["aerial-screw"]["parts"]) - 1, "the licensed motor is not counted"
# The product rules reach the product: the cubesat and the ornithopter are over their limits, the difference engine's
# French site stores grams.
product_rules = {p["key"]: sorted(f["rule"] for f in p.get("findings", [])) for p in prods["products"]}
# Programme-cleared may not see the cubesat's LICENSED board: its mass limit is not evaluable and raises no violation.
cleared_limits = mass_limit("programme-cleared")
assert product_rules == {k: sorted(["massLimit"] * (cleared_limits.get(k, {}).get("status") == "fail") + ["massScale"] * (k in set(mass_scale("programme-cleared").values())))
                         for k in PRODUCTS}, product_rules
assert {p["key"]: p["massLimit"] for p in prods["products"] if "massLimit" in p} == cleared_limits, prods
assert {p["key"]: p["lifecycleConflicts"] for p in prods["products"]} == {k: len(lifecycle_conflicts(k, "programme-cleared")) for k in PRODUCTS}, prods
assert {k: cleared_limits[k] for k in ("cubesat", "ornithopter")} == {"cubesat": {"status": "not-evaluable", "limitKg": 2.0, "hiddenItems": 1},
                                                                    "ornithopter": {"status": "fail", "limitKg": 260.5}}, cleared_limits
assert all(p["name"] == PRODUCTS[p["key"]]["product"]["name"] and p["frame"] == PRODUCTS[p["key"]]["frame"] for p in prods["products"]), prods
err, listed = mcp("list-interfaces")
assert not err and sorted(key(i["product"], i["id"]) for i in listed["interfaces"]) == sorted(INTERFACES), listed
assert (listed["policy"]["profile"], listed["policy"]["actor"]) == ("programme-cleared", "fixture"), listed["policy"]
assert {key(i["product"], i["id"]): i["status"] for i in listed["interfaces"]} == {k: v["status"] for k, v in interfaces.items()}
assert {key(i["product"], i["id"]): sorted(i["rulesFailing"]) for i in listed["interfaces"]} == {k: sorted(DEFECTS.get(k, {})) if v["status"] == "fail" else [] for k, v in interfaces.items()}
err, check = mcp("interface-check")
assert not err and check["interface"] == interfaces[ORN_KEY("IF-05")], "interface_check IF-05 differs from GET /interfaces/IF-05"
err, wu = mcp("where-used")
assert not err and timeless(wu["interfaces"]) == timeless(used["interfaces"]) and wu["features"] == used["features"]
err, im = mcp("impact")
assert not err and timeless(im["interfaces"]) == timeless(impact["interfaces"])
err, ev = mcp("evidence")
uk_arm = next(a for a in load("evidence-IF-05.json")["arms"] if a["endpoint"] == "ontop-uk")
assert not err and ev["interfaceId"] == "IF-05" and timeless(ev["arm"]) == timeless(uk_arm), "evidence arm differs"
assert sorted(turtle_body(ev["arm"]["triples"])) == sorted(turtle_body(uk_arm["triples"]))
err, st = mcp("export-status-de")
assert not err and st["visible"] is False and st["part"] == {"redacted": True, "plm": "de"} and st["policy"]["profile"] == "de-engineer", st
err, st = mcp("export-status-officer")
assert not err and st["visible"] is True and st["part"]["jurisdiction"] == "EXPORT-LICENCE" and "supplier" not in st["part"], st
err, st = mcp("export-status-supplier")
assert not err and st["visible"] is True and st["part"]["supplier"] == "Forja del Tajo, Toledo" and st["policy"]["profile"] == "programme-cleared", st
err, sel = mcp("sparql-select")
assert not err and sel["form"] == "select" and sel["columns"] == ["owner", "plugs"] and sel["limit"] == 200, sel
plugs_by_owner = {row[0]: row[1] for row in sel["rows"]}
expected_plugs = {}
for i in INTERFACES.values():
    for f in features_of(i):
        if f["kind"] == "plug" and f["part"] in cleared_visible:
            expected_plugs[PARTS[f["part"]]["plm"]] = expected_plugs.get(PARTS[f["part"]]["plm"], 0) + 1
for sp in SPARE_PLUGS:  # unmated and declared by no interface, still a plug of a visible part in the graph
    if sp["part"] in cleared_visible:
        expected_plugs[PARTS[sp["part"]]["plm"]] += 1
assert plugs_by_owner == expected_plugs, (plugs_by_owner, expected_plugs)
err, lim = mcp("sparql-limit")
assert not err and len(lim["rows"]) == 200 and lim["limitReached"] is True and "LIMIT" in lim["query"], lim["query"]
err, unk = mcp("sparql-unknown")
assert err and unk.startswith("unknown terms in predicate or class position: [atelier:weight]") and "known classes:" in unk, unk
err, upd = mcp("sparql-update")
assert err and upd.startswith("update forms are not accepted"), upd
err, cat = mcp("catalogue")
assert err and cat == "PLM_API_BASE is not set: the PLM and core services are not reachable here", cat
err, mcp_bom = mcp("bom")
assert not err and mcp_bom["product"] == BOM_PRODUCT and mcp_bom["policy"]["profile"] == "programme-cleared", mcp_bom.get("policy")
expected_cleared = occurrences_by_site(BOM_PRODUCT, "programme-cleared")
assert {r["plm"]: (r["occurrences"], r["hiddenOccurrences"]) for r in mcp_bom["sites"]} == expected_cleared, (mcp_bom["sites"], expected_cleared)
assert (mcp_bom["total"]["occurrences"], mcp_bom["total"]["hiddenOccurrences"]) == tuple(sum(v[i] for v in expected_cleared.values()) for i in (0, 1)), mcp_bom["total"]
assert [k["id"] for k in mcp_bom["root"]["children"]] == [k["id"] for k in bom_officer["root"]["children"]], "the root's four site kits"
# Tool answers are compact: no federation request texts (the evidence arm carries them), list_interfaces in six
# fields plus up to three messages when failing, under 330 B an interface for the officer's list.
for name in ("list-interfaces", "interface-check", "where-used", "impact", "sparql-select", "export-status-de", "products", "bom"):
    assert "sparql" not in load(f"mcp-{name}.json")["content"], name
assert "sparql" in ev["arm"] and "sql" in ev["arm"]
officer_list = load("mcp-list-interfaces-officer.json")
assert not officer_list["isError"] and officer_list["bytes"] < 330 * len(INTERFACES), officer_list["bytes"]
assert set(officer_list["content"]) == {"interfaces", "provenance", "timings", "policy"}, set(officer_list["content"])
for i in officer_list["content"]["interfaces"]:
    failing = i["status"] == "fail"
    assert set(i) == ({"id", "product", "label", "status", "parts", "rulesFailing"} | ({"messages"} if failing else set())), i
    assert all({"id", "plm"} <= set(p) <= {"id", "plm", "supplier"} for p in i["parts"]) and (not failing or 1 <= len(i["messages"]) <= 3), i
    assert all(("supplier" in p) == ("supplier" in PARTS[p["id"]]) and p.get("supplier") == PARTS[p["id"]].get("supplier") for p in i["parts"]), i["parts"]
    assert sorted(i["rulesFailing"]) == sorted(DEFECTS.get(key(i["product"], i["id"]), {})), i
assert all(set(c) == {"endpoint", "kind", "requests", "triples", "ms", "requestBytes", "largestRequestBytes"}
           for c in officer_list["content"]["provenance"]["calls"])
print(f"  MCP list_interfaces for the officer: {officer_list['bytes']} B compact JSON, {len(officer_list['content']['interfaces'])} interfaces")
print("  MCP tools/list:", TOOLS)
print("  MCP tools/call: products", [(p["key"], p["partCount"]) for p in prods["products"]], "for programme-cleared;")
print(f"    bom {BOM_PRODUCT}: (occurrences, hidden) per site", {r["plm"]: (r["occurrences"], r["hiddenOccurrences"]) for r in mcp_bom["sites"]}, f"{load('mcp-bom.json')['bytes']} B;")
print("    where_used, impact_of_change, interface_check and evidence equal their REST answers; export_status per profile;")
print(f"    sparql: plugs per owner {plugs_by_owner}; LIMIT {lim['limit']} applied ({len(lim['rows'])} rows, {lim['ms']} ms on"
      f" {lim['graphTriples']} triples); unknown term and update rejected; catalogue unavailable without PLM_API_BASE")

# --- untagged: with its tag row gone, the ES left pulley block is hidden from programme-cleared, reported, and
# visible (without a tag) to the export officer; its rows are never requested from the ES PLM ----------------
untagged = load("interfaces-untagged.json")
un_ifaces = check_answer(untagged, "programme-cleared", (UNTAGGED,))
show(f"programme-cleared, {UNTAGGED} untagged", un_ifaces)
untagged_ifaces = sorted(k for k, i in INTERFACES.items() if UNTAGGED in i["parts"])
assert untagged_ifaces == [ORN_KEY("IF-27")], untagged_ifaces
if27 = un_ifaces[ORN_KEY("IF-27")]
assert if27["status"] == "not-evaluable" and redacted(if27["parts"]) == [{"redacted": True, "plm": "es"}]
assert [i["status"] for i in un_ifaces.values()].count("not-evaluable") == TALLIES["programme-cleared"][2] + len(untagged_ifaces)
check_requests(untagged, "programme-cleared", 6, (UNTAGGED,))
assert load("interface-IF-27-untagged.json")["interface"] == if27
ev = load("evidence-IF-27-untagged.json")
assert ev["policy"]["untagged"] == [part_iri(UNTAGGED)], ev["policy"]
arms = {a["endpoint"]: a for a in ev["arms"]}
parts_untagged = load("parts-untagged.json")["parts"]
assert parts_named(arms["ontop-es"]["sparql"], "es") == {p["id"] for p in visible(parts_untagged) if p["plm"] == "es"}
assert UNTAGGED not in arms["ontop-es"]["sparql"] and UNTAGGED not in arms["ontop-es"]["sql"]
assert PARTS[UNTAGGED]["cadFile"] not in ev["merged"]["turtle"], "hidden part's CAD file leaked"
assert arms["ontop-core"]["tables"] == core_tables_of(ORN_KEY("IF-27"), visible_ids("programme-cleared", (UNTAGGED,))), arms["ontop-core"]["tables"]
assert arms["ontop-core"]["tables"][0]["keys"] == ["FR-ORN-TRAV-MIL-001"], arms["ontop-core"]["tables"]
assert len(visible(parts_untagged)) == len(ITEMS) - len(hidden_ids("programme-cleared", (UNTAGGED,)))
assert redacted(parts_untagged) == markers("programme-cleared", (UNTAGGED,)), redacted(parts_untagged)
officer_untagged = load("interfaces-untagged-officer.json")
check_answer(officer_untagged, "export-officer", (UNTAGGED,))
assert officer_untagged["policy"]["untagged"] == [part_iri(UNTAGGED)], officer_untagged["policy"]
if27 = by_id(officer_untagged)[ORN_KEY("IF-27")]
assert if27["status"] == "pass" and not redacted(if27["parts"]), if27
pulley = next(p for p in if27["parts"] if p["id"] == UNTAGGED)
assert pulley["cadFile"] == PARTS[UNTAGGED]["cadFile"] and pulley["sourceFileRef"] == pulley["cadFile"], pulley
assert (pulley["jurisdiction"], pulley["releasableTo"], pulley["taggedBy"], pulley["taggedAt"]) == (None, None, None, None), pulley
print(f"  export-officer, {UNTAGGED} untagged: IF-27", if27["status"], "with the untagged part shown, policy.untagged",
      officer_untagged["policy"]["untagged"])

# --- stations: the ornithopter's parts between two wing stations, each part's span from its STEP box in bounds.json (in
# inches for the UK parts), inside or crossing by the 1 mm rule; a hidden part is never named; every station carries its
# basis and the inferred ones are named in the notes; the right wing root joint crossed by the parts of three sites ----
BOUNDS = json.loads((Path(__file__).resolve().parents[3] / "modules" / "cad" / "stp" / "ornithopter" / "bounds.json").read_text())
ORNITHOPTER = PRODUCTS["ornithopter"]
STATION_AT = {st["id"]: float(st["at"]) for st in ORNITHOPTER["extended"]["stations"]["list"]}
STATION_BASIS = {st["id"]: st["basis"] for st in ORNITHOPTER["extended"]["stations"]["list"]}


def box_span(part):
    box = BOUNDS[part["cadPart"]]
    return box["min"][1], box["max"][1]


# Every occurrence of a part, as the placements give it down the trees, spans its box moved by the occurrence's world
# transform (data/placements.py, data/stations.py): a part placed several times is between two stations when one of its
# occurrences is, and crossing when one of those crosses.
WORLD = staging.world(staging_bom.Bom(ORNITHOPTER, lambda message: sys.exit(message)))


def occurrence_spans(part):
    box = BOUNDS[part["cadPart"]]
    return [staging_stations.extent(w, (box["min"], box["max"]), 1) for w in WORLD.get(part["id"], [staging.IDENTITY])]


def expected_between(lo, hi, side, profile):
    intervals = {"right": [(lo, hi)], "left": [(-hi, -lo)], "both": [(-hi, -lo), (lo, hi)]}[side]
    if side == "both" and lo <= 0:
        intervals = [(-hi, hi)]
    out = {}
    for part in ORNITHOPTER["parts"]:
        if is_hidden(part["id"], profile):
            continue
        positions = []
        for f, t in occurrence_spans(part):
            for a, b in intervals:
                if min(t, b) - max(f, a) > 1:
                    positions.append("crossing" if f < a - 1 or t > b + 1 else "inside")
                    break
        if positions:
            out[part["id"]] = "crossing" if "crossing" in positions else "inside"
    return out


def check_between(answer, lo, hi, side, profile):
    got = {p["id"]: p["position"] for p in answer["parts"]}
    assert got == expected_between(lo, hi, side, profile), sorted(set(got.items()) ^ set(expected_between(lo, hi, side, profile).items()))
    for p in answer["parts"]:
        f, t = box_span(PARTS[p["id"]])
        if p["plm"] == "uk":
            assert p["unit"] == "IN" and abs(p["span"][0] - f / 25.4) < 6e-5 and abs(p["fromMm"] - f) < 2e-3, p
        else:
            assert p["unit"] == "MilliM" and abs(p["span"][0] - f) < 6e-4 and abs(p["toMm"] - t) < 6e-4, p
        assert all(s["basis"] == STATION_BASIS[s["id"]] for s in p["stations"]), p["stations"]
    text = json.dumps(answer["parts"])
    for item in hidden_ids(profile):
        assert f'"{item}"' not in text, (item, "a hidden part is named")
    for name, c in {c["endpoint"]: c for c in answer["provenance"]["calls"]}.items():
        assert c["requests"] == {"neptune": 4, "ontop-core": 4 + references_outside(ORN, visible_ids(profile))}.get(name, 7), (name, c)
    return got


between = load("stations-ornithopter.json")
assert between["range"]["from"] == {"id": "WS 1500", "basis": "inferred"} and between["range"]["side"] == "both", between["range"]
assert between["range"]["intervalsMm"] == [[-3600.0, -1500.0], [1500.0, 3600.0]], between["range"]
assert any(n.startswith("WS 1500, ") and "inferred" in n for n in between["notes"]), between["notes"]
cleared_between = check_between(between, 1500, 3600, "both", "programme-cleared")
unknown_between = check_between(load("stations-ornithopter-unknown.json"), 1500, 3600, "both", "unknown")
assert set(unknown_between) < set(cleared_between), "the unknown profile is answered fewer parts"
right = check_between(load("stations-ornithopter-right.json"), 3000, 4100, "right", "programme-cleared")
# The right outer wing: the spars, the knuckle, the ribs, the covering, the loom and the drive cord, the access flap at
# WS 3600, the rib shoe whose sixth occurrence sits at WS 3600 and the lacing cord along the outer ribs.
assert set(right) == {"HOLM-R-61010", "HOLM-R-61020", "GELK-R-61030", "RIPP-R-61050", "LEIN-R-61070", "KABL-R-61100", "CABL-R-6080",
                      "KLAP-R-61130", "RSCH-R-61045", "SCHN-R-61065"}, sorted(right)
sections = load("sections-ornithopter.json")
assert [s["id"] for s in sections["sections"]] == ["S-CTR", "S-L-IN", "S-L-OUT", "S-R-IN", "S-R-OUT"], sections["sections"]
root_joint = next(j for j in sections["joints"] if j["atMm"] == 700.0)
assert root_joint["sections"] == ["S-CTR", "S-R-IN"] and root_joint["owners"] == ["FR", "DE"], root_joint
assert root_joint["station"] == {"id": "WS 700", "basis": "joint"}, root_joint["station"]
assert {p["id"] for p in root_joint["crossing"]} == {"FR-ORN-EMPL-R-001", "WURZ-R-61080", "HOLM-R-61010", "AKTR-R-61090", "KABL-R-61100",
                                                     "CABL-R-6080", "CRET-R-6085"}, root_joint["crossing"]
assert sorted(j["atMm"] for j in sections["joints"]) == [-3000.0, -700.0, 700.0, 3000.0], [j["atMm"] for j in sections["joints"]]
mcp_joints = load("mcp-section-joints.json")["content"]
assert [j["atMm"] for j in mcp_joints["joints"]] == [-700.0, 700.0] and "sparql" not in mcp_joints, mcp_joints["joints"]
assert {p["id"]: p["position"] for p in load("mcp-stations.json")["content"]["parts"]} == cleared_between
print(f"  stations WS 1500 to WS 3600: {sum(v == 'inside' for v in cleared_between.values())} inside, "
      f"{sum(v == 'crossing' for v in cleared_between.values())} crossing for programme-cleared, {len(unknown_between)} parts for unknown; "
      f"right wing WS 3000 to WS 4100: {len(right)} crossing; root joint WS 700 right crossed by {len(root_joint['crossing'])} parts")
