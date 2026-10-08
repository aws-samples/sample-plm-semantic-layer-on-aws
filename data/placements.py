# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""The placements of the bill-of-materials lines, for data/generate.py.

A line may carry `placements: [[x, y, z, rx, ry, rz], ...]`, one per occurrence of its child under its parent, as
many as its quantity. Translations are in the site's length unit (inches for UK, millimetres elsewhere), rotations
in degrees about the fixed x, y and z axes of the product frame, applied in that order: a point p of the child's
STEP lands at Rz(rz) Ry(ry) Rx(rx) p + (x, y, z). A placement is relative to the STEP as authored: the STEP sits
at the part's reference occurrence, so the reference occurrence is the identity and the other placements are rigid
motions of the authored geometry. A line without placements has one occurrence, the identity.

Occurrences multiply down the tree: an occurrence of a child under an occurrence of its parent is the parent's
transform applied after the child's, M = M_parent M_line, from the identity at the site kit. A part's occurrences
are those of every path from its site kit, its parents in id order, each parent's occurrences in order and, under
each, the line's placements in order; the first is the composition of reference occurrences and must be the
identity, the position of the STEP. `rows` writes the placements in each site's idiom.
"""

import math
from decimal import Decimal

MM_PER_UNIT = {"DE": 1.0, "FR": 1.0, "ES": 1.0, "UK": 25.4}
IDENTITY = ((1.0, 0.0, 0.0, 0.0), (0.0, 1.0, 0.0, 0.0), (0.0, 0.0, 1.0, 0.0), (0.0, 0.0, 0.0, 1.0))
# A world occurrence counts as the identity within these tolerances (STEP coordinates are written to 1e-4 mm).
SAME_MM, SAME = 1e-3, 1e-6


def check(bom, fail):
    """Each line's placements: a list of six finite numbers per occurrence, as many as the line's quantity."""
    for line in bom.lines:
        placements = line.get("placements")
        if placements is None:
            continue
        where = f"{bom.key}: line {line['parent']} -> {line['child']}"
        if line["quantity"] != line["quantity"].to_integral_value():
            fail(f"{where} has placements for the non-integer quantity {line['quantity']}")
        if len(placements) != line["quantity"]:
            fail(f"{where} has {len(placements)} placements for the quantity {line['quantity']}; one per occurrence")
        for p in placements:
            if (not isinstance(p, list) or len(p) != 6
                    or not all(isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v) for v in p)):
                fail(f"{where}: placement {p} is not [x, y, z, rx, ry, rz]")


def matrix(placement, mm_per_unit=1.0):
    """The 4 x 4 transform of a placement, translation in millimetres."""
    x, y, z, rx, ry, rz = placement
    cx, sx = math.cos(math.radians(rx)), math.sin(math.radians(rx))
    cy, sy = math.cos(math.radians(ry)), math.sin(math.radians(ry))
    cz, sz = math.cos(math.radians(rz)), math.sin(math.radians(rz))
    # Rz Ry Rx
    return ((cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx, x * mm_per_unit),
            (sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx, y * mm_per_unit),
            (-sy, cy * sx, cy * cx, z * mm_per_unit),
            (0.0, 0.0, 0.0, 1.0))


def multiply(a, b):
    return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)) for i in range(4))


def placement(m):
    """A transform back to [x, y, z, rx, ry, rz], millimetres and degrees, in the convention of `matrix`."""
    ry = math.asin(max(-1.0, min(1.0, -m[2][0])))
    if abs(m[2][0]) < 1 - 1e-9:
        rx, rz = math.atan2(m[2][1], m[2][2]), math.atan2(m[1][0], m[0][0])
    else:  # x and z turn about the same axis: put the whole turn on z
        rx, rz = 0.0, math.atan2(-m[0][1], m[1][1])
    return [m[0][3], m[1][3], m[2][3], math.degrees(rx), math.degrees(ry), math.degrees(rz)]


def is_identity(m):
    return all(abs(m[i][3]) <= SAME_MM for i in range(3)) and all(
        abs(m[i][j] - (1.0 if i == j else 0.0)) <= SAME for i in range(3) for j in range(3))


def local(line, plm):
    """The transforms of one line's occurrences: its placements, or the identity once."""
    placements = line.get("placements")
    if not placements:
        return [IDENTITY]
    return [matrix(p, MM_PER_UNIT[plm]) for p in placements]


def world(bom):
    """Per item of each site's tree, the world transforms of its occurrences, the order of the module docstring."""
    out = {}

    def visit(item, plm):
        if item in out:
            return out[item]
        parents = sorted(bom.parents.get(item, []))
        if not parents:
            out[item] = [IDENTITY]
            return out[item]
        found = []
        for parent in parents:
            line = bom.children[parent][item]
            for above in visit(parent, plm):
                found += [multiply(above, m) for m in local(line, plm)]
        out[item] = found
        return found

    for item, plm in bom.plm_of.items():
        visit(item, plm)
    return out


def check_reference(bom, fail):
    """The first occurrence of every geometric part is the identity: its STEP sits where the part is first used."""
    occurrences = world(bom)
    for part in sorted(bom.geometric):
        first = occurrences.get(part, [IDENTITY])[0]
        if not is_identity(first):
            fail(f"{bom.key}: the first occurrence of {part} is {[round(v, 4) for v in placement(first)]}, not the "
                 "identity; the reference occurrence of a part is where its STEP sits")
    return occurrences


def placed(bom):
    """Occurrences of the geometric parts drawn from placements: one per world transform."""
    occurrences = world(bom)
    return sum(len(occurrences.get(p, [IDENTITY])) for p in bom.geometric)


def decimal(value):
    text = f"{value:.4f}".rstrip("0").rstrip(".")
    return Decimal("0" if text in ("", "-0") else text)


def rows(bom, plm):
    """The site's placement rows, one per occurrence of a line with placements, numbered from 1:
    (parent, child, number, x, y, z, rx, ry, rz) in the site's unit."""
    out = []
    for line in bom.site_lines(plm):
        for number, p in enumerate(line.get("placements") or [], start=1):
            out.append([line["parent"], line["child"], number] + [decimal(v) for v in p])
    return out


def positions(line):
    """The ES posiciones of one line of a lista_materiales document, or None for a line without placements."""
    placements = line.get("placements")
    if not placements:
        return None
    keys = ("x_mm", "y_mm", "z_mm", "rx_grados", "ry_grados", "rz_grados")
    return [{k: number(v) for k, v in zip(keys, p)} for p in placements]


def number(value):
    v = round(float(value), 4)
    return int(v) if v == int(v) else v
