# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Stations, sections and the station span of every part, for data/generate.py.

A product may name its stations and sections in `extended`:

    "stations": {"key", "name", "axis": "x" | "y" | "z", "symmetric": bool, "measure",
                 "list": [{"id", "at", "basis", "source", "tolerance"?}, ...]},
    "sections": [{"id", "name", "owner", "side": "left" | "right" | "both", "stations": [id, ...], "part"?}, ...]

`at` is in millimetres along the axis; on a symmetric axis station n is at +n on the right side and -n on the left.
`basis` is printed, measured, joint or inferred; `tolerance`, in millimetres, is the accuracy of a station read from a
drawing or a model. A section lists its stations in order along the axis, is owned by one site and may name the part it
is. Stations and sections are product data: `links` writes them to the link store.

A part's span is computed, never authored: it is the extent along the station axis of the part's box in
modules/cad/stp/<product key>/bounds.json (written beside the STEP files by modules/cad/generate.py, with the sha256 of
each STEP), at its reference occurrence. `check_bounds` refuses a part whose STEP is missing from bounds.json or whose
hash differs, for every product. Each placement row carries the span of its occurrence with the parent at the parent's
first occurrence: the extent of the eight box corners moved by that world transform, so a rotated occurrence gets its
true envelope. The layer derives every other world occurrence's span from these: the stored span of its line's
occurrence (the part's own span for a line without placements) carried by the parent's motion from its first occurrence
to this one, D = M_parent M_parent,1^-1, the composed transform of its whole path, rotations included. D moves the
interval's ends along the axis exactly when it maps the station axis onto itself or its opposite (a parent turned 180
degrees about another axis, or turned about the station axis itself); a parent turned so that the station axis mixes
with another axis (a yaw of 30 degrees) moves the span by extents along the other axes, which no span stores, and the
generator refuses that one case. Every derived span is checked against the true envelope within DERIVED_MM. Spans are
stored in the site's length unit: millimetres to 3 decimals, UK inches to 4 with the unit in span_uom.
"""

import hashlib
import json
from decimal import Decimal
from pathlib import Path

import placements as places

BASES = ("printed", "measured", "joint", "inferred")
SIDES = ("left", "right", "both")
AXES = "xyz"
PLMS = ("FR", "DE", "UK", "ES")
DERIVED_MM = 0.001
# Per site: the span columns of its part table and of its placement table, in insert order.
COLUMNS = {"FR": "travee_debut_mm, travee_fin_mm", "DE": "spanne_von_mm, spanne_bis_mm",
           "UK": "span_from, span_to, span_uom", "ES": "tramo_desde_mm, tramo_hasta_mm"}
# ES keeps the placements in the lista_materiales document: the keys of a posicion.
POSITION_KEYS = ("tramo_desde_mm", "tramo_hasta_mm")
FORBIDDEN = "stationSpan"
BASE = "https://example.com/atelier"
SKOS, QUDT, UNIT = "http://www.w3.org/2004/02/skos/core#", "http://qudt.org/schema/qudt/", "http://qudt.org/vocab/unit/"
DCTERMS = "http://purl.org/dc/terms/"
BASIS_CONCEPT = {"printed": "PrintedStation", "measured": "MeasuredStation", "joint": "JointStation", "inferred": "InferredStation"}


def authored_span(node, where, fail):
    """Refuses a stationSpan key anywhere in a product file: spans are computed from the geometry."""
    if isinstance(node, dict):
        if FORBIDDEN in node:
            fail(f"{where}: {FORBIDDEN} is computed from the CAD geometry and the placements, never authored")
        for value in node.values():
            authored_span(value, where, fail)
    elif isinstance(node, list):
        for value in node:
            authored_span(value, where, fail)


def check_bounds(data, step_dir, fail):
    """The box of each part from bounds.json, after checking its STEP is listed with the hash of the file on disk."""
    key = data["product"]["key"]
    path = step_dir / key / "bounds.json"
    if not path.is_file():
        fail(f"{key}: no {path.name} beside the STEP files; run modules/cad/generate.py")
    bounds = json.loads(path.read_text(encoding="utf-8"))
    boxes = {}
    for part in data["parts"]:
        entry = bounds.get(part["cadPart"])
        if entry is None:
            fail(f"part {part['id']}: {part['cadPart']}.stp is not in {key}/bounds.json; run modules/cad/generate.py")
        digest = hashlib.sha256((step_dir / key / f"{part['cadPart']}.stp").read_bytes()).hexdigest()
        if entry["sha256"] != digest:
            fail(f"part {part['id']}: {key}/bounds.json describes another {part['cadPart']}.stp (sha256 differs); "
                 "run modules/cad/generate.py")
        boxes[part["id"]] = (tuple(entry["min"]), tuple(entry["max"]))
    return boxes


class Stations:
    """One product's stations, sections and spans; empty for a product without stations."""

    def __init__(self, data, bom, boxes, fail):
        self.key = data["product"]["key"]
        authored_span(data, data["file"], fail)
        extended = data.get("extended") or {}
        self.spec = extended.get("stations")
        self.sections = extended.get("sections", [])
        self.part_spans, self.row_spans = {}, {}
        if self.spec is None:
            if self.sections:
                fail(f"{self.key}: sections without stations")
            return
        self.check(data, fail)
        self.axis = AXES.index(self.spec["axis"])
        self.spans(bom, boxes, fail)

    def check(self, data, fail):
        spec, key = self.spec, self.key
        if spec.get("axis") not in AXES or not isinstance(spec.get("symmetric"), bool):
            fail(f"{key}: stations need an axis (x, y or z) and symmetric true or false")
        self.at = {}
        for station in spec["list"]:
            sid = station["id"]
            if sid in self.at:
                fail(f"{key}: station {sid} declared twice")
            if station.get("basis") not in BASES:
                fail(f"{key}: station {sid} has the basis {station.get('basis')!r}, not one of {', '.join(BASES)}")
            if not station.get("source"):
                fail(f"{key}: station {sid} names no source")
            if "tolerance" in station and Decimal(station["tolerance"]) < 0:
                fail(f"{key}: station {sid} has the negative tolerance {station['tolerance']}")
            at = Decimal(station["at"])
            if spec["symmetric"] and at < 0:
                fail(f"{key}: station {sid} at {at}: a symmetric axis measures stations from the plane of symmetry, n >= 0")
            self.at[sid] = at
        own = {p["id"] for p in data["parts"]}
        ids = set()
        for section in self.sections:
            sid = section["id"]
            if sid in ids:
                fail(f"{key}: section {sid} declared twice")
            ids.add(sid)
            if section.get("owner") not in PLMS:
                fail(f"{key}: section {sid} is owned by {section.get('owner')!r}, not one of {', '.join(PLMS)}")
            if section.get("side") not in SIDES or (not spec["symmetric"] and section["side"] != "both"):
                fail(f"{key}: section {sid} has the side {section.get('side')!r}; left, right or both, both on an axis "
                     "that is not symmetric")
            unknown = [s for s in section["stations"] if s not in self.at]
            if unknown or len(section["stations"]) < 2:
                fail(f"{key}: section {sid} needs two or more known stations; unknown: {unknown}")
            positions = [self.at[s] for s in section["stations"]]
            if positions != sorted(set(positions)):
                fail(f"{key}: section {sid} lists its stations out of order along the axis: {section['stations']}")
            if "part" in section and section["part"] not in own:
                fail(f"{key}: section {sid} is the part {section['part']}, which the product does not hold")

    def spans(self, bom, boxes, fail):
        """The reference span of every geometric part, the span of every placement row, and the check that every world
        occurrence's span follows from them as the layer derives it."""
        for part in bom.geometric:
            self.part_spans[part] = extent(places.IDENTITY, boxes[part], self.axis)
        world = places.world(bom)
        for line in bom.lines:
            if not line.get("placements"):
                continue
            parent, child = line["parent"], line["child"]
            first = world[parent][0]
            for number, m in enumerate(places.local(line, bom.plm_of[child]), start=1):
                if child in boxes:
                    self.row_spans[(parent, child, number)] = extent(places.multiply(first, m), boxes[child], self.axis)
        for part in sorted(bom.geometric):
            exact = [extent(w, boxes[part], self.axis) for w in world.get(part, [places.IDENTITY])]
            derived = self.derived(part, bom, world)
            for n, (e, d) in enumerate(zip(exact, derived), start=1):
                if d is None:
                    fail(f"{self.key}: occurrence {n} of {part} spans {e} along {self.spec['axis']}, but its parent is turned "
                         "so that the station axis mixes with another axis, and no stored span gives that envelope; place "
                         "the part on its own lines")
                if abs(e[0] - d[0]) > DERIVED_MM or abs(e[1] - d[1]) > DERIVED_MM:
                    fail(f"{self.key}: occurrence {n} of {part} spans {e} along {self.spec['axis']}, the stored spans give {d}")

    def derived(self, part, bom, world):
        """The spans of a part's world occurrences as the layer derives them, in the order of data/placements.py; None for
        an occurrence whose parent's motion turns the station axis onto another axis."""
        out = []
        for parent in sorted(bom.parents.get(part, [])):
            line = bom.children[parent][part]
            above = world[parent]
            back = inverse(above[0])
            for w in above:
                motion = places.multiply(w, back)
                for number in range(1, len(line.get("placements") or [None]) + 1):
                    base = self.row_spans[(parent, part, number)] if line.get("placements") else self.part_spans[part]
                    out.append(carried(motion, base, self.axis))
        return out or [self.part_spans[part]]

    def part_columns(self, item, plm):
        """The span columns of a part row: the reference span in the site's unit, or NULLs."""
        return native(self.part_spans.get(item["id"]), plm)

    def row_columns(self, parent, child, number, plm):
        return native(self.row_spans.get((parent, child, number)), plm)

    def figure(self):
        if self.spec is None:
            return []
        bases = [s["basis"] for s in self.spec["list"]]
        stations = f"stations: {len(bases)} along {self.spec['axis']} ({', '.join(f'{b} {bases.count(b)}' for b in BASES if b in bases)})"
        sections = f"sections {len(self.sections)} ({', '.join(f'{s['id']} {s['owner']}' for s in self.sections)})"
        spans = f"spans {len(self.part_spans)} parts, {len(self.row_spans)} placement rows"
        return [f"  {stations}; {sections}; {spans}"]

    def links(self, product_iri, part_iri, plm_of, turtle_string, iri_safe):
        """The Turtle of the product's stations and sections, for data/links.ttl; ids IRI-safe as in R2RML."""
        if self.spec is None:
            return []
        product = f"<{product_iri(self.key)}>"
        spec = self.spec
        out = [f"{product} atelier:stationAxis {turtle_string(spec['axis'])} ;",
               f'    atelier:stationsSymmetric "{str(spec["symmetric"]).lower()}"^^xsd:boolean ;',
               f"    atelier:stationMeasure {turtle_string(spec['measure'])} .", ""]
        for station in spec["list"]:
            iri = f"{BASE}/station/{iri_safe(self.key)}/{iri_safe(station['id'])}"
            lines = [f"<{iri}> a atelier:Station ;",
                     f"    <{SKOS}notation> {turtle_string(station['id'])} ;",
                     f"    atelier:sectionOf {product} ;",
                     f"    atelier:stationPosition <{iri}/position> ;",
                     f"    atelier:stationBasis atelier:{BASIS_CONCEPT[station['basis']]} ;"]
            if "tolerance" in station:
                lines.append(f"    atelier:stationTolerance <{iri}/tolerance> ;")
            lines.append(f"    <{DCTERMS}source> {turtle_string(station['source'])} .")
            lines.append(quantity(f"{iri}/position", station["at"]))
            if "tolerance" in station:
                lines.append(quantity(f"{iri}/tolerance", station["tolerance"]))
            out += lines + [""]
        for section in self.sections:
            iri = f"{BASE}/section/{iri_safe(self.key)}/{iri_safe(section['id'])}"
            stations = " , ".join(f"<{BASE}/station/{iri_safe(self.key)}/{iri_safe(s)}>" for s in section["stations"])
            lines = [f"<{iri}> a atelier:Section ;",
                     f"    <{SKOS}notation> {turtle_string(section['id'])} ;",
                     f"    rdfs:label {turtle_string(section['name'])} ;",
                     f"    atelier:sectionOf {product} ;",
                     f"    atelier:ownedBy {turtle_string(section['owner'])} ;",
                     f"    atelier:sectionSide {turtle_string(section['side'])} ;"]
            if "part" in section:
                lines.append(f"    atelier:sectionPart <{part_iri(plm_of[section['part']], section['part'])}> ;")
            out += lines + [f"    atelier:hasStation {stations} .", ""]
        return out


def extent(m, box, axis):
    """Lowest and highest coordinate along the axis of the box's eight corners moved by the transform m, in mm."""
    low, high = box
    values = []
    for x in (low[0], high[0]):
        for y in (low[1], high[1]):
            for z in (low[2], high[2]):
                values.append(m[axis][0] * x + m[axis][1] * y + m[axis][2] * z + m[axis][3])
    return (min(values), max(values))


def inverse(m):
    """The inverse of a rigid transform: the transposed rotation and the translation brought back through it."""
    r = [[m[j][i] for j in range(3)] for i in range(3)]
    t = [-sum(r[i][k] * m[k][3] for k in range(3)) for i in range(3)]
    return tuple(tuple(r[i]) + (t[i],) for i in range(3)) + ((0.0, 0.0, 0.0, 1.0),)


def carried(motion, span, axis):
    """A span along the axis moved by a rigid motion, by its ends: exact when the motion maps the axis onto itself or its
    opposite; None when it mixes the axis with another, whose extents a span does not hold."""
    row = motion[axis]
    sign = row[axis]
    if abs(abs(sign) - 1) > places.SAME or any(abs(row[k]) > places.SAME for k in range(3) if k != axis):
        return None
    ends = (sign * span[0] + row[3], sign * span[1] + row[3])
    return (min(ends), max(ends))


def native(span, plm):
    """A span in the site's columns: millimetres to 3 decimals, UK inches to 4 with the unit IN."""
    if span is None:
        return [None, None] + ([None] if plm == "UK" else [])
    if plm == "UK":
        return [decimal(v / 25.4, 4) for v in span] + ["IN"]
    return [decimal(v, 3) for v in span]


def decimal(value, places_):
    d = Decimal(str(round(value, places_))).quantize(Decimal(1).scaleb(-places_))
    return Decimal(0).quantize(d) if d == 0 else d


def positions(line, stations, plm):
    """The ES posiciones of a line with the span of each occurrence."""
    out = places.positions(line)
    if out is None:
        return None
    for number, posicion in enumerate(out, start=1):
        span = stations.row_spans.get((line["parent"], line["child"], number))
        if span is not None:
            posicion.update({k: places.number(v) for k, v in zip(POSITION_KEYS, native(span, plm))})
    return out


def quantity(iri, value):
    return f'<{iri}> <{QUDT}numericValue> "{Decimal(value)}"^^xsd:decimal ; <{QUDT}unit> <{UNIT}MilliM> .'

