# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Analytic solid primitives shared by the machine generators, and the STEP AP242 writer. Millimetres throughout.

Every part is built from boxes, cylinders, prisms, rings, annular sectors, swept arcs and ruled lofts; no boolean is
applied, because STEP text costs about 3 KB per face whatever the surface and a cut multiplies faces. Several bodies
of one part are a compound in one STEP product. Points are (x, y, z) tuples or cq.Vector.
"""
import re
from pathlib import Path

import cadquery as cq
from OCP.Bnd import Bnd_Box
from OCP.BRepBndLib import BRepBndLib
from OCP.Interface import Interface_Static
from OCP.STEPControl import STEPControl_Controller

V = cq.Vector
STEP_TIME = re.compile(r"'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}'")  # the FILE_NAME time stamp of a STEP header
# The one next_assembly_usage_occurrence: its id counts every file the process has written, and the writer wraps the
# statement where that count makes it long.
STEP_USAGE = re.compile(r"NEXT_ASSEMBLY_USAGE_OCCURRENCE\('\d+',(.*?)\);", re.S)
IN = 25.4  # mm per inch: the UK PLM stores the same points in inches


def vec(p):
    return p if isinstance(p, cq.Vector) else V(*p)


def box(x0, y0, z0, x1, y1, z1):
    return cq.Solid.makeBox(x1 - x0, y1 - y0, z1 - z0, V(x0, y0, z0))


def cyl(r, p0, p1):
    """Cylinder of radius r from p0 to p1."""
    d = vec(p1) - vec(p0)
    return cq.Solid.makeCylinder(r, d.Length, vec(p0), d.normalized())


def prism(p0, p1, w, h, up=(0, 0, 1)):
    """Rectangular beam w wide, h tall (along `up`) from p0 to p1."""
    d = vec(p1) - vec(p0)
    n = d.normalized()
    side = n.cross(V(*up)).normalized()
    return cq.Workplane(cq.Plane(origin=vec(p0), xDir=side, normal=n)).rect(w, h).extrude(d.Length).val()


def ring(r_in, r_out, z0, h):
    """Annulus of height h standing on z0, axis z."""
    return cq.Workplane("XY", origin=(0, 0, z0)).circle(r_out).circle(r_in).extrude(h).val()


def sector(r_in, r_out, a0, a1, z0, h):
    """Annular sector a0..a1 degrees (two arcs, two radial lines): 6 faces."""
    import math
    am = math.radians((a0 + a1) / 2)
    p = lambda r, a: (r * math.cos(math.radians(a)), r * math.sin(math.radians(a)))
    wp = (cq.Workplane("XY", origin=(0, 0, z0)).moveTo(*p(r_in, a0)).lineTo(*p(r_out, a0))
          .threePointArc((r_out * math.cos(am), r_out * math.sin(am)), p(r_out, a1)).lineTo(*p(r_in, a1))
          .threePointArc((r_in * math.cos(am), r_in * math.sin(am)), p(r_in, a0)).close())
    return wp.extrude(h).val()


def swept_arc(r, p, mid, q):
    """Rod of radius r along the three-point arc p-mid-q (one toroidal face)."""
    edge = cq.Edge.makeThreePointArc(vec(p), vec(mid), vec(q))
    circle = cq.Wire.makeCircle(r, vec(p), edge.tangentAt(0))
    return cq.Solid.sweep(circle, [], cq.Wire.assembleEdges([edge]))


def polyline_cable(r, points):
    """Cord or loom: one thin cylinder per segment."""
    return compound([cyl(r, a, b) for a, b in zip(points, points[1:])])


def compound(solids):
    return cq.Compound.makeCompound(solids)


def solids_of(shape):
    return shape.Solids() if isinstance(shape, cq.Compound) else [shape]


def validate(shape, pid):
    """Every body of the part must be a valid solid before it is written."""
    assert shape.isValid(), f"{pid}: invalid shape"
    for s in solids_of(shape):
        assert s.isValid(), f"{pid}: invalid solid"


def bbox(shape):
    """Bounds of the tessellated shape, rounded to the mm: BoundingBox() on B-spline bodies is control-point bound
    and up to 140 mm loose, the mesh is within 1 mm."""
    xs, ys, zs = zip(*[(v.x, v.y, v.z) for v in shape.tessellate(0.5, 0.2)[0]])
    return [[round(min(xs)), round(min(ys)), round(min(zs))], [round(max(xs)), round(max(ys)), round(max(zs))]]


def extent(shape):
    """The exact axis-aligned box of the shape in mm, [[xmin, ymin, zmin], [xmax, ymax, zmax]] to 0.001 mm: the optimal
    box of the B-rep, which neither the tessellation (inside the surface) nor BoundingBox() (control points) gives."""
    box = Bnd_Box()
    BRepBndLib.AddOptimal_s(shape.wrapped, box, False, False)
    x0, y0, z0, x1, y1, z1 = box.Get()
    return [[round(x0, 3), round(y0, 3), round(z0, 3)], [round(x1, 3), round(y1, 3), round(z1, 3)]]


def write_step(shape, name, path):
    """One STEP AP242 product per part, in millimetres."""
    STEPControl_Controller.Init_s()
    assert Interface_Static.SetCVal_s("write.step.schema", "AP242DIS")
    assert Interface_Static.SetCVal_s("write.step.unit", "MM")
    assy = cq.Assembly(name=name)
    assy.add(shape, name=f"{name}.body")
    assy.export(str(path), exportType="STEP")
    # The FILE_NAME header carries the write time and the usage statement the count of the files written before: a
    # constant time, and the statement on one line with the id 1, keep the output a function of the geometry alone.
    text = STEP_TIME.sub("'2026-01-01T00:00:00'", Path(path).read_text(), count=1)
    usage = lambda m: "NEXT_ASSEMBLY_USAGE_OCCURRENCE('1'," + re.sub(r"\n\s*", "", m.group(1)) + ");"
    Path(path).write_text(STEP_USAGE.sub(usage, text, count=1))
