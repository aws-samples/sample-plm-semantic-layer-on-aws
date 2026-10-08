# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Function through a product, for data/generate.py: what drives what, and the gears that carry it.

A bill of materials says what contains what; `extended.functionalEdges` says where power or motion goes, as the
layer's own engineering knowledge: `{from, to, flow, via?, reaction?}`, a part of the product driving the next with a
flow kind (mechanical, electrical, hydraulic, steam), `via` the interface of the product joining the two parts when
the joint is one, and `reaction` the gear held fixed that the driving gear also meshes (the ring of a planetary stage
whose carrier drives the planets). The edges are published in the link store (`links`), as the interfaces are.

Gears carry their tooth count and module as part attributes in their site's idiom (`gear_columns`): from the part's
`extended.gear` (`{teeth, module}`, the module in the site's length unit, inches in the UK) or, for the products that
record their gearing as meshes (`extended.meshes`, `extended.gearModelling`), from the mesh list: every part that holds
gears gets the module, and a part holding one gear its tooth count.

`mesh_defects` applies the meshModule rule of ontology/shapes.ttl the way the shape does: a mechanical edge between
two gears whose modules, in millimetres, differ by more than 0.01 mm; `seededMeshDefects` lists the expected
results as `{driver, driven, rule}`. `train` computes a flow's gear ratio as the query service does, so the generator
checks a product's recorded design ratio (`extended.drivetrainChain.ratioFromTeeth`) against its edges and gears.
"""

from decimal import Decimal, ROUND_HALF_EVEN

FLOWS = ("mechanical", "electrical", "hydraulic", "steam")
RULE = "meshModule"
MM_PER_IN = Decimal("25.4")
MODULE_TOLERANCE_MM = Decimal("0.01")
MODULE_SCALE = {"FR": 3, "DE": 3, "ES": 3, "UK": 5}  # NUMERIC(8, 3) millimetres, NUMERIC(8, 5) inches
MODULE_UNIT = {"FR": "MilliM", "DE": "MilliM", "ES": "MilliM", "UK": "IN"}


def edges(data):
    return (data.get("extended") or {}).get("functionalEdges", [])


def gears(data, fail):
    """{part id: (teeth or None, module in the site's unit as Decimal)} of the product's gear parts."""
    plm_of = {p["id"]: p["plm"] for p in data["parts"]}
    out = {}
    for part in data["parts"]:
        gear = (part.get("extended") or {}).get("gear")
        if gear is not None:
            out[part["id"]] = (gear["teeth"], Decimal(gear["module"]))
    ext = data.get("extended") or {}
    if ext.get("meshes"):
        if ext["gearModelling"]["moduleUnit"] != "MilliM":
            fail(f"{data['product']['key']}: mesh modules are recorded in millimetres")
        held = {}
        for mesh in ext["meshes"]:
            for side in ("driver", "driven"):
                held.setdefault(mesh[side + "Part"], {})[mesh[side + "Gear"]] = (mesh[side + "Teeth"], Decimal(str(mesh["module"])))
        for part, by_gear in held.items():
            modules = {m for _, m in by_gear.values()}
            if len(modules) != 1:
                fail(f"{part}: its gears differ in module")
            module = modules.pop()
            if MODULE_UNIT[plm_of[part]] == "IN":
                module = (module / MM_PER_IN).quantize(Decimal(1).scaleb(-MODULE_SCALE["UK"]), ROUND_HALF_EVEN)
            teeth = next(iter(by_gear.values()))[0] if len(by_gear) == 1 else None
            out.setdefault(part, (teeth, module))
    for part, (teeth, module) in out.items():
        if part not in plm_of:
            fail(f"{data['product']['key']}: gear {part} is not a part of the product")
        if teeth is not None and (not isinstance(teeth, int) or isinstance(teeth, bool) or teeth < 1):
            fail(f"{part}: tooth count {teeth!r} must be a positive integer")
        if module <= 0 or -module.as_tuple().exponent > MODULE_SCALE[plm_of[part]]:
            fail(f"{part}: module {module} must be positive with at most {MODULE_SCALE[plm_of[part]]} decimals")
    return out


def gear_columns(part, gear_parts):
    """The part row's gear columns: tooth count and module, NULL for a part that is no gear."""
    return list(gear_parts.get(part["id"], (None, None)))


def module_mm(part, gear_parts, plm_of):
    module = gear_parts[part][1]
    return module * MM_PER_IN if MODULE_UNIT[plm_of[part]] == "IN" else module


def check(data, gear_parts, fail):
    """The product's edges join its own parts, through its own interfaces, with known flows; returns nothing."""
    key = data["product"]["key"]
    own = {p["id"] for p in data["parts"]}
    interfaces = {i["id"]: set(i["parts"]) for i in data["interfaces"]}
    seen = set()
    for edge in edges(data):
        a, b = edge["from"], edge["to"]
        if a not in own or b not in own or a == b:
            fail(f"{key}: functional edge {a} -> {b} must join two parts of the product")
        if (a, b) in seen:
            fail(f"{key}: functional edge {a} -> {b} is declared twice")
        seen.add((a, b))
        if edge["flow"] not in FLOWS:
            fail(f"{key}: functional edge {a} -> {b} has flow {edge['flow']!r}, not one of {', '.join(FLOWS)}")
        if "via" in edge and interfaces.get(edge["via"]) != {a, b}:
            fail(f"{key}: functional edge {a} -> {b} names {edge['via']}, which is not an interface between them")
        reaction = edge.get("reaction")
        if reaction is not None and not (reaction in gear_parts and a in gear_parts and b in gear_parts):
            fail(f"{key}: functional edge {a} -> {b} reacts on {reaction}: the three must be gears")
    for speed in (data.get("extended") or {}).get("ratedSpeeds", []):
        if speed["part"] not in own or Decimal(speed["rpm"]) <= 0:
            fail(f"{key}: rated speed of {speed['part']} needs a part of the product and a positive speed")
    plm_of = {p["id"]: p["plm"] for p in data["parts"]}
    found = mesh_defects(data, gear_parts, plm_of)
    declared = {(d["driver"], d["driven"], d["rule"]) for d in data.get("seededMeshDefects", [])}
    if found != declared:
        fail(f"{key}: the meshModule rule disagrees with seededMeshDefects:\n"
             f"  found only:    {sorted(found - declared)}\n  declared only: {sorted(declared - found)}")
    design = ((data.get("extended") or {}).get("drivetrainChain") or {}).get("gearboxRatio", {}).get("ratioFromTeeth")
    if design is not None:
        speeds = data["extended"]["ratedSpeeds"]
        ratio = train(data, gear_parts, speeds[0]["part"]).get(speeds[-1]["part"])
        if ratio is None or (1 / ratio).quantize(Decimal("0.01")) != Decimal(design):
            fail(f"{key}: the gear train from {speeds[0]['part']} to {speeds[-1]['part']} does not give the design ratio {design}")


def mesh_defects(data, gear_parts, plm_of):
    """{(driver, driven, rule)} of every mechanical edge between two gears whose modules differ by more than 0.01 mm."""
    out = set()
    for edge in edges(data):
        a, b = edge["from"], edge["to"]
        if edge["flow"] != "mechanical" or a not in gear_parts or b not in gear_parts:
            continue
        ma, mb = module_mm(a, gear_parts, plm_of), module_mm(b, gear_parts, plm_of)
        if abs(ma - mb) > MODULE_TOLERANCE_MM:
            out.add((a, b, RULE))
    return out


def train(data, gear_parts, start):
    """{part: ratio} of every part the mechanical edges reach from `start`, breadth first: the product of driven over
    driver teeth along the way (input speed over the part's speed), a planetary stage with a fixed ring counting
    sun over ring plus sun."""
    out, queue = {start: Decimal(1)}, [start]
    while queue:
        part = queue.pop(0)
        for edge in edges(data):
            a, b = edge["from"], edge["to"]
            if a != part or b in out or edge["flow"] != "mechanical":
                continue
            ratio = out[a]
            teeth = {p: gear_parts.get(p, (None, None))[0] for p in (a, b, edge.get("reaction"))}
            if teeth[a] and teeth[b]:
                reaction = edge.get("reaction")
                ratio *= Decimal(teeth[b]) / (teeth[reaction] + teeth[b] if reaction else teeth[a])
            out[b] = ratio
            queue.append(b)
    return out


def links(data, part_iri, interface_iri, product_iri, base):
    """The Turtle of the product's functional edges and rated speeds, for data/links.ttl."""
    key = data["product"]["key"]
    plm_of = {p["id"]: p["plm"] for p in data["parts"]}
    iri = lambda part: f"<{part_iri(plm_of[part], part)}>"
    out = []
    for n, edge in enumerate(edges(data), 1):
        a, b = edge["from"], edge["to"]
        lines = [f"<{base}/drive/{key}/D{n:02d}> a atelier:Drive ;",
                 f"    atelier:ofProduct <{product_iri(key)}> ;",
                 f"    atelier:driver {iri(a)} ;",
                 f"    atelier:driven {iri(b)} ;"]
        if "via" in edge:
            lines.append(f"    atelier:viaInterface <{interface_iri(key, edge['via'])}> ;")
        if "reaction" in edge:
            lines.append(f"    atelier:reactionPart {iri(edge['reaction'])} ;")
        lines.append(f'    atelier:flow "{edge["flow"]}" .')
        out += lines + [f"{iri(a)} atelier:drives {iri(b)} .", ""]
    for speed in (data.get("extended") or {}).get("ratedSpeeds", []):
        out.append(f'{iri(speed["part"])} atelier:ratedSpeedRpm "{speed["rpm"]}"^^xsd:decimal .')
    return out + ([""] if out and out[-1] else [])
