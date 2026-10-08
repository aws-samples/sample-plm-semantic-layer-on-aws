# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Lilienthal Normalsegelapparat (1894), an authored reconstruction: 83 unique part numbers, one STEP file each.

Each part is placed at its reference occurrence (the left wing, the first rib of a set, the front hanger), so a part
number used several times in the bill of materials of data/products/glider.json is drawn once. Mirror-image left and
right parts (ribs, hub fittings, wire sets, coverings, arm bars and pads) are distinct part numbers.

Frame (mm): x aft from the nose of the frame (the front of the bumper bow) at the centreline, y to the pilot's right,
z up with z = 0 on the top face of the rib hub fittings, 8 mm above the axis plane of the cross rod (z -28). Envelope
from the public figures for the type: span 6.7 m (front rib tips at y +/-3349), wing area about 13 m2, length 5.3 m
(x 0 to 4910), empty mass about 20 kg.

Layout, after the published descriptions (the 1895 patent text, the museum records): a crossed frame of a willow hoop
(the opening the pilot hangs in) and a bamboo cross rod, a willow bumper bow in front and a bamboo tail boom aft; on
each end of the cross rod a hub fitting with eight pockets, from which eight radial ribs diverge like the fingers of
a bat wing, each turning on its own pin so the wing folds back to the boom; a cotton covering stretched over the
ribs, a hemp cord through the rib tips along the trailing edge, a front tension wire from the first rib to the hoop
holding the wing spread, four profile rails slid over each wing; a kingpost above and a lower post below the cross
rod, each anchoring three steel wires per wing to the ribs 1, 4 and 7; a fixed vertical fin on a post, and a tail plane
hinged at its leading edge that swings upward freely and rests on a stop at the fin post when it swings down. The
pilot hangs by the forearms on two padded arm bars below the frame.

Interpretation choices, none of them taken from a drawing: eight ribs per wing at angles -5 to 85 degrees from the
span direction, lengths 2960 to 2400; the first rib is bamboo (Ø 36), the others split willow (Ø 22); each rib is one
circular arc whose mid rise is its chordwise share of a 1/13 camber plus its spanwise share of a 1/25 arch, and whose
tip rises 8 % of its spanwise reach; the covering is one ruled loft over the eight rib arcs; the hoop is a circle of
760 mm; the tail plane is a lens of two circular arcs with a slot for the fin; the wire anchors are eye plates on the
posts; each rib root carries a willow bearing block on its forward face, where it rests on its neighbour when folded,
which makes left and right ribs mirror parts. Authored additions for the demonstrator: the body sling, the airspeed
indicator and its vane sensor, the nose ballast, the tail return spring and the front wire latch springs.

The ribs, profile rails, frame woodwork, wire sets, turnbuckles and castings are the Spanish workshop's; the hub
fittings, pins, posts, hinges and machined clips the German one's; the coverings, cords, springs, eyelets, tacks
and binding the French one's; the pilot harness, instruments and ballast the British one's. The part keys here are
the CAD keys of data/products/glider.json (cad/glider/<key>.stp); the PLM part ids live in that file.
"""
import math

import cadquery as cq
from OCP.BRepIntCurveSurface import BRepIntCurveSurface_Inter
from OCP.gp import gp_Dir, gp_Lin, gp_Pnt

from geometry import V, box, compound, cyl, polyline_cable, prism, sector, swept_arc

KEY = "glider"
NAME = "Lilienthal Normalsegelapparat (1894), authored reconstruction"
FRAME = ("x aft from the nose of the frame (the front of the bumper bow) at the centreline, y to the pilot's right, "
         "z up with z = 0 on the top face of the rib hub fittings; mm")

LEFT, RIGHT = -1, 1

# ---------------------------------------------------------------- frame (ES woodwork)

X_ROD, Y_HUB, Z_ROD, ROD_R = 400, 400, -28, 20     # cross rod axis; hubs on its ends at y +/-400
Y_ROD_END = 520
HOOP_C, HOOP_R, HOOP_ROD = (700, 0, Z_ROD), 380, 14
Y_HOOP_CROSS = math.sqrt(HOOP_R ** 2 - (HOOP_C[0] - X_ROD) ** 2)   # where the cross rod crosses the hoop rim
BOW_END_Y, BOW_MID = 480, (14, 0, -220)
BOOM_X0, BOOM_X1, BOOM_R = 1070, 4850, 20


def hoop():
    """Willow hoop: two half circles round the pilot opening."""
    cx, cy, cz = HOOP_C
    front, back = (cx - HOOP_R, 0, cz), (cx + HOOP_R, 0, cz)
    return compound([swept_arc(HOOP_ROD, front, (cx, side * HOOP_R, cz), back) for side in (LEFT, RIGHT)])


def cross_rod():
    return cyl(ROD_R, (X_ROD, -Y_ROD_END, Z_ROD), (X_ROD, Y_ROD_END, Z_ROD))


def bumper_bow():
    return swept_arc(14, (X_ROD, -BOW_END_Y, Z_ROD), BOW_MID, (X_ROD, BOW_END_Y, Z_ROD))


def bow_point(y):
    """Centre line of the bumper bow at lateral position y (the bow lies on one circle)."""
    edge = cq.Edge.makeThreePointArc(V(X_ROD, -BOW_END_Y, Z_ROD), V(*BOW_MID), V(X_ROD, BOW_END_Y, Z_ROD))
    lo, hi = 0.0, 1.0
    for _ in range(50):
        mid = (lo + hi) / 2
        lo, hi = (mid, hi) if edge.positionAt(mid).y < y else (lo, mid)
    p = edge.positionAt(lo)
    return (p.x, p.y, p.z)


def tail_boom():
    return cyl(BOOM_R, (BOOM_X0, 0, Z_ROD), (BOOM_X1, 0, Z_ROD))


# Arm bars below the frame: front end under the cross rod, rear end under the hoop rim.
ARM_FRONT, ARM_REAR, ARM_TOP = (380, 200), (960, 275), -110


def arm_line(side, x):
    (x0, y0), (x1, y1) = ARM_FRONT, ARM_REAR
    t = (x - x0) / (x1 - x0)
    return (x, side * (y0 + (y1 - y0) * t))


def arm_bar(side):
    """Ash arm bar 40 x 30 with an elbow stop on the outboard face at the rear."""
    p0, p1 = arm_line(side, ARM_FRONT[0]), arm_line(side, ARM_REAR[0])
    z = ARM_TOP - 15
    bar = prism((p0[0], p0[1], z), (p1[0], p1[1], z), 40, 30)
    ex, ey = arm_line(side, 900)
    y0, y1 = sorted((ey + side * 20, ey + side * 32))
    stop = box(ex - 30, y0, ARM_TOP - 30, ex + 30, y1, ARM_TOP + 40)
    return compound([bar, stop])


# ---------------------------------------------------------------- wings (ES ribs and rails)

RIB_ANGLES = tuple(-5 + i * 90 / 7 for i in range(8))           # degrees aft of the span direction
RIB_LENGTHS = (2960, 2970, 3010, 3000, 2920, 2780, 2600, 2400)  # pivot to tip
RIB_R = (18, 11, 11, 11, 11, 11, 11, 11)                         # bamboo first rib, split willow others
R_ROOT, R_PIVOT = 170, 195                                         # rib root and pivot pin, from the hub centre
CAMBER, ARCH, DIHEDRAL = 1 / 13, 1 / 25, 0.08
WIRED_RIBS, WIRE_T = (0, 3, 6), 0.62                              # ribs 1, 4 and 7 take the bracing wires at 62 %
COVER, COVER_FROM = 3, 290                                         # covering thickness; its start off the hub
RAIL_STATIONS, RAIL_R = (900, 1500, 2100, 2700), 6


def hub_point(side):
    return (X_ROD, side * Y_HUB, 0.0)


def rib_dir(i, side):
    a = math.radians(RIB_ANGLES[i])
    return (math.sin(a), side * math.cos(a))


def rib_points(i, side):
    """(root, mid, tip) of the rib arc; the rib bottom touches the hub plate at its root."""
    hx, hy, _ = hub_point(side)
    ux, uy = rib_dir(i, side)
    L, r = RIB_LENGTHS[i], RIB_R[i]
    a = math.radians(RIB_ANGLES[i])
    root = (hx + ux * R_ROOT, hy + uy * R_ROOT, r)
    tip = (hx + ux * L, hy + uy * L, r + DIHEDRAL * L * math.cos(a))
    rise = L * (CAMBER * abs(math.sin(a)) + ARCH * math.cos(a))
    mid = ((root[0] + tip[0]) / 2, (root[1] + tip[1]) / 2, (root[2] + tip[2]) / 2 + rise)
    return root, mid, tip


def rib_edge(i, side):
    return cq.Edge.makeThreePointArc(*(V(*p) for p in rib_points(i, side)))


def rib_at(i, side, t):
    p = rib_edge(i, side).positionAt(t)
    return (p.x, p.y, p.z)


def rib(i, side):
    """Rib arc with the bearing block on its forward face at the root."""
    root, mid, tip = rib_points(i, side)
    ux, uy = rib_dir(i, side)
    a = math.radians(RIB_ANGLES[i])
    fx, fy = -math.cos(a), side * math.sin(a)        # towards the next rib forward (smaller angle)
    off = RIB_R[i] + 4
    p0 = (root[0] + fx * off, root[1] + fy * off, root[2])
    p1 = (p0[0] + ux * 100, p0[1] + uy * 100, p0[2])
    return compound([swept_arc(RIB_R[i], root, mid, tip), prism(p0, p1, 8, RIB_R[i] * 2)])


def wire_eye(i, side, up):
    """Eye of a bracing wire on rib i: on top (up = +1) for the upper wires, underneath (-1) for the lower ones."""
    x, y, z = rib_at(i, side, WIRE_T)
    return (x, y, z + up * RIB_R[i])


def rail_crossings(station, side):
    """(x, y, z) on the rib centre lines where the profile rail at |y| = station crosses each rib it reaches."""
    hx, hy, _ = hub_point(side)
    out = []
    for i in range(8):
        ux, uy = rib_dir(i, side)
        s = (side * station - hy) / uy
        if not COVER_FROM < s < RIB_LENGTHS[i]:
            continue
        edge, lo, hi = rib_edge(i, side), 0.0, 1.0
        for _ in range(50):
            m = (lo + hi) / 2
            p = edge.positionAt(m)
            lo, hi = (m, hi) if side * (p.y - hy) < side * (s * uy) else (lo, m)
        p = edge.positionAt(lo)
        out.append((p.x, p.y, p.z))
    return out


def cover_top(shape, x, y):
    """Highest z of the covering's surface above (x, y)."""
    hit = BRepIntCurveSurface_Inter()
    hit.Init(shape.wrapped, gp_Lin(gp_Pnt(x, y, 0), gp_Dir(0, 0, 1)), 1e-6)
    zs = []
    while hit.More():
        zs.append(hit.Pnt().Z())
        hit.Next()
    return max(zs)


def rail_points(k, side=LEFT, n=12):
    """The rail rides on the covering along its station, from the first rib it crosses to the last."""
    cover, ends = wing_cover(side), rail_crossings(RAIL_STATIONS[k], side)
    (x0, y, _), (x1, _, _) = ends[0], ends[-1]
    xs = [x0 + (x1 - x0) * j / (n - 1) for j in range(n)]
    return [(x, y, cover_top(cover, x, y) + RAIL_R) for x in xs]


def profile_rail(k, side=LEFT):
    return polyline_cable(RAIL_R, rail_points(k, side))


# ---------------------------------------------------------------- hub fittings, pins and clips (DE)

def hub_fitting(side):
    """Steel sector plate 8 mm under the rib roots with the pivot boss on top; the right one is the mirror."""
    plate = sector(20, 280, -102, 2, -8, 8)
    boss = cq.Solid.makeCylinder(25, 30, V(0, 0, 0))
    shape = compound([plate, boss]).translate(V(*hub_point(LEFT)))
    return shape if side == LEFT else shape.mirror("XZ")


def pivot_point(i, side):
    hx, hy, _ = hub_point(side)
    ux, uy = rib_dir(i, side)
    return (hx + ux * R_PIVOT, hy + uy * R_PIVOT, 0.0)


def pivot_pin(i=1, side=LEFT):
    x, y, _ = pivot_point(i, side)
    return compound([cyl(3, (x, y, -12), (x, y, 2 * RIB_R[i] + 6)), cyl(5, (x, y, 2 * RIB_R[i] + 6), (x, y, 2 * RIB_R[i] + 10))])


def rib_ferrule(i, side=LEFT):
    """Steel pocket ferrule over the rib root, 110 mm along the rib."""
    edge = rib_edge(i, side)
    p0, t = edge.positionAt(0), edge.tangentAt(0)
    return cyl(RIB_R[i] + 3, p0, p0 + t * 110)


def hub_bolt(side=LEFT):
    """ISO 4014 M8 x 70 through the hub plate and the cross rod end."""
    x, y = X_ROD, side * (Y_HUB + 30)
    return compound([cyl(4, (x, y, Z_ROD - ROD_R - 10), (x, y, 0)), cyl(7, (x, y, 0), (x, y, 5.5))])


def rib_tip_cap(i=1, side=LEFT):
    edge = rib_edge(i, side)
    p1, t = edge.positionAt(1), edge.tangentAt(1)
    return cyl(RIB_R[i] + 2, p1 - t * 40, p1 + t * 4)


def wire_clip(i=0, side=LEFT):
    """Machined clip round the rib at the bracing station, with the wire eyes above and below."""
    x, y, z = rib_at(i, side, WIRE_T)
    r = RIB_R[i] + COVER + 5
    return box(x - 10, y - r, z - r, x + 10, y + r, z + r)


def rail_clip(k=0, side=LEFT):
    """Spring-steel clip holding the rail down on the first rib it crosses."""
    x, y, z = rail_crossings(RAIL_STATIONS[k], side)[0]
    top = cover_top(wing_cover(side), x, y) + 2 * RAIL_R
    return box(x - 12, y - 8, z - RIB_R[0] - 2, x + 12, y + 8, top + 2)


# ---------------------------------------------------------------- posts and stays (DE posts, ES wires)

KINGPOST_TOP, LOWER_POST_FOOT = 1050, -720
UPPER_EYE_Z, LOWER_EYE_Z, EYE_Y = 1025, -700, 30


def kingpost():
    return compound([cyl(20, (X_ROD, 0, -8), (X_ROD, 0, KINGPOST_TOP)),
                     box(X_ROD - 8, -40, UPPER_EYE_Z - 15, X_ROD + 8, 40, UPPER_EYE_Z + 15)])


def lower_post():
    return compound([cyl(18, (X_ROD, 0, Z_ROD - ROD_R), (X_ROD, 0, LOWER_POST_FOOT)),
                     box(X_ROD - 8, -40, LOWER_EYE_Z - 15, X_ROD + 8, 40, LOWER_EYE_Z + 15)])


def upper_anchor(side):
    return (X_ROD, side * EYE_Y, UPPER_EYE_Z)


def lower_anchor(side):
    return (X_ROD, side * EYE_Y, LOWER_EYE_Z)


WIRE_R = 1.0


def upper_wires(side):
    return compound([cyl(WIRE_R, upper_anchor(side), wire_eye(i, side, +1)) for i in WIRED_RIBS])


def lower_wires(side):
    return compound([cyl(WIRE_R, lower_anchor(side), wire_eye(i, side, -1)) for i in WIRED_RIBS])


FRONT_WIRE_T = 0.45


def front_wire_ends(side=LEFT):
    x, y, z = rib_at(0, side, FRONT_WIRE_T)
    return (x, y, z - RIB_R[0]), (HOOP_C[0] - HOOP_R, 0, Z_ROD)


def front_wire(side=LEFT):
    return cyl(WIRE_R, *front_wire_ends(side))


def along(a, b, d):
    a, b = V(*a), V(*b)
    p = a + (b - a).normalized() * d
    return (p.x, p.y, p.z)


def turnbuckle(side=LEFT):
    a, b = lower_anchor(side), wire_eye(0, side, -1)
    return cyl(5, along(a, b, 150), along(a, b, 230))


def thimble(side=LEFT):
    a, b = upper_anchor(side), wire_eye(0, side, +1)
    return cyl(4, along(a, b, 20), along(a, b, 34))


KINGPOST_HEAD = (X_ROD, 0, KINGPOST_TOP - 10)
BOOM_TOP = Z_ROD + BOOM_R


Y_BOW_STAY = 300


def kingpost_stays():
    bx, by, bz = bow_point(Y_BOW_STAY)
    return compound([cyl(WIRE_R, KINGPOST_HEAD, (bx, by, bz + 14)),
                     cyl(WIRE_R, KINGPOST_HEAD, (2600, 0, BOOM_TOP))])


def lower_post_stay():
    bx, by, bz = bow_point(Y_BOW_STAY)
    return cyl(WIRE_R, (X_ROD, 0, LOWER_POST_FOOT + 10), (bx, by, bz - 14))


# ---------------------------------------------------------------- castings (ES)

def hoop_clamp(side=LEFT):
    y = side * Y_HOOP_CROSS
    return box(X_ROD - 25, y - 25, Z_ROD - 25, X_ROD + 25, y + 25, Z_ROD + 25)


def kingpost_step():
    return compound([cyl(30, (X_ROD, 0, -8), (X_ROD, 0, 40)), box(X_ROD - 30, -45, -12, X_ROD + 30, 45, -8)])


def lower_post_shoe():
    z = Z_ROD - ROD_R
    return compound([cyl(28, (X_ROD, 0, z), (X_ROD, 0, z - 45)), box(X_ROD - 30, -45, z, X_ROD + 30, 45, z + 4)])


def boom_socket():
    return compound([cyl(28, (BOOM_X0 - 20, 0, Z_ROD), (BOOM_X0 + 90, 0, Z_ROD))])


X_FIN_POST, FIN_TOP = 4000, 720


def fin_step():
    return compound([cyl(28, (X_FIN_POST - 30, 0, Z_ROD), (X_FIN_POST + 30, 0, Z_ROD)),
                     cyl(24, (X_FIN_POST, 0, BOOM_TOP), (X_FIN_POST, 0, BOOM_TOP + 20))])


def tail_skid():
    return compound([cyl(26, (BOOM_X1 - 70, 0, Z_ROD), (BOOM_X1 + 10, 0, Z_ROD)),
                     box(BOOM_X1 - 70, -20, Z_ROD - 60, BOOM_X1, 20, Z_ROD - 20)])


# ---------------------------------------------------------------- tail (ES frames, DE post and hinge, FR coverings)

TP_Z, TP_FRONT, TP_REAR, TP_SIDE, TP_HALF_SPAN = 20, 3910, 4910, 4410, 800
SLOT = 20                                                     # half width of the fin slot in the tail plane covering
FIN_REAR, FIN_LOW, FIN_ARC_MID = 4780, 20, (4600, 0, 520)


def tail_plane_frame():
    """Willow rim: two circular arcs from tip to tip, the front one round the hinge pin."""
    left, right = (TP_SIDE, -TP_HALF_SPAN, TP_Z), (TP_SIDE, TP_HALF_SPAN, TP_Z)
    return compound([swept_arc(10, left, (TP_FRONT, 0, TP_Z), right), swept_arc(10, left, (TP_REAR, 0, TP_Z), right)])


def circle_x(p, m, q, y):
    """x on the circle through p, m, q (in the plane z = const) at lateral position y, on m's side."""
    (x1, y1), (x2, y2), (x3, y3) = (p[0], p[1]), (m[0], m[1]), (q[0], q[1])
    d = 2 * (x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2))
    cx = ((x1 ** 2 + y1 ** 2) * (y2 - y3) + (x2 ** 2 + y2 ** 2) * (y3 - y1) + (x3 ** 2 + y3 ** 2) * (y1 - y2)) / d
    cy = ((x1 ** 2 + y1 ** 2) * (x3 - x2) + (x2 ** 2 + y2 ** 2) * (x1 - x3) + (x3 ** 2 + y3 ** 2) * (x2 - x1)) / d
    r = math.hypot(x1 - cx, y1 - cy)
    dx = math.sqrt(r * r - (y - cy) ** 2)
    return cx + dx if m[0] > cx else cx - dx


def tail_plane_cover():
    """Two half lenses of cotton 3 mm thick on the rim, either side of the fin slot."""
    tip = (TP_SIDE, TP_HALF_SPAN)
    front, rear = (TP_FRONT, 0), (TP_REAR, 0)
    z = TP_Z + 10
    halves = []
    for side in (LEFT, RIGHT):
        fl, fr = (tip[0], side * tip[1]), (tip[0], -side * tip[1])
        xf, xr = circle_x(fl, front, fr, side * SLOT), circle_x(fl, rear, fr, side * SLOT)
        ym = side * (SLOT + TP_HALF_SPAN) / 2
        mf, mr = circle_x(fl, front, fr, ym), circle_x(fl, rear, fr, ym)
        wp = (cq.Workplane("XY", origin=(0, 0, z)).moveTo(xf, side * SLOT).threePointArc((mf, ym), fl)
              .threePointArc((mr, ym), (xr, side * SLOT)).close())
        halves.append(wp.extrude(3).val())
    return compound(halves)


def fin_post():
    return cyl(15, (X_FIN_POST, 0, BOOM_TOP), (X_FIN_POST, 0, FIN_TOP + 20))


def fin_frame():
    return compound([cyl(8, (X_FIN_POST + 15, 0, FIN_LOW), (FIN_REAR, 0, FIN_LOW)),
                     swept_arc(8, (X_FIN_POST + 15, 0, FIN_TOP), FIN_ARC_MID, (FIN_REAR, 0, FIN_LOW))])


FIN_PLANE = cq.Plane(origin=(0, 1, 0), xDir=(1, 0, 0), normal=(0, -1, 0))   # local (x, z), extruded towards -y


def fin_cover():
    wp = (cq.Workplane(FIN_PLANE).moveTo(X_FIN_POST + 15, FIN_LOW + 8).lineTo(X_FIN_POST + 15, FIN_TOP - 8)
          .threePointArc((FIN_ARC_MID[0] - 8, FIN_ARC_MID[2] - 8), (FIN_REAR - 12, FIN_LOW + 8)).close())
    return wp.extrude(2).val()


HINGE_X, HINGE_Z = TP_FRONT, TP_Z


def tail_hinge_fitting():
    """Base plate on the boom with two lugs either side of the tail plane's front rim."""
    base = box(HINGE_X - 30, -90, BOOM_TOP, HINGE_X + 30, 90, BOOM_TOP + 6)
    lugs = [box(HINGE_X - 15, y0, BOOM_TOP + 6, HINGE_X + 15, y0 + 15, HINGE_Z + 8) for y0 in (-80, 65)]
    return compound([base] + lugs)


def tail_hinge_pin():
    return compound([cyl(4, (HINGE_X, -88, HINGE_Z), (HINGE_X, 88, HINGE_Z)),
                     cyl(7, (HINGE_X, -94, HINGE_Z), (HINGE_X, -88, HINGE_Z))])


STOP_Z = TP_Z + 10 - 4


def tail_stop():
    """Cross pin through the fin post under the slot edges of the tail plane covering."""
    return cyl(4, (X_FIN_POST, -60, STOP_Z), (X_FIN_POST, 60, STOP_Z))


def tail_spring():
    return cyl(4, (X_FIN_POST + 4, 50, STOP_Z), (X_FIN_POST + 200, 50, STOP_Z))


def tail_wire():
    return cyl(WIRE_R, (X_FIN_POST, 0, FIN_TOP + 10), (2900, 0, BOOM_TOP))


# ---------------------------------------------------------------- coverings (FR)

def wing_cover(side, n=7):
    """Ruled loft over the eight rib arcs, COVER mm thick on top of the ribs, from 290 mm off the hub (past the pocket
    ferrules) to the tips."""
    def profile(i):
        edge = rib_edge(i, side)
        t0 = (COVER_FROM - R_ROOT) / (RIB_LENGTHS[i] - R_ROOT)
        pts = [edge.positionAt(t0 + (1 - t0) * k / (n - 1)) for k in range(n)]
        lo = [V(p.x, p.y, p.z + RIB_R[i]) for p in pts]
        up = [V(p.x, p.y, p.z + COVER) for p in lo]
        return cq.Wire.assembleEdges([cq.Edge.makeSpline(up), cq.Edge.makeLine(up[-1], lo[-1]),
                                      cq.Edge.makeSpline(lo[::-1]), cq.Edge.makeLine(lo[0], up[0])])
    return cq.Solid.makeLoft([profile(i) for i in range(8)], ruled=True)


def trailing_edge_cord(side):
    return polyline_cable(3, [rib_points(i, side)[2] for i in range(8)])


def leading_edge_binding(side=LEFT):
    """Cotton tape wrapping the first rib and the front edge of the covering."""
    edge = rib_edge(0, side)
    p = [edge.positionAt(t) for t in (0.05, 0.5, 1.0)]
    return swept_arc(RIB_R[0] + COVER + 1, *p)


def latch_spring(side=LEFT):
    a, b = front_wire_ends(side)
    return cyl(3, along(b, a, 40), along(b, a, 110))


def eyelet(side=LEFT):
    x, y, z = wire_eye(3, side, +1)
    return cyl(5, (x + 15, y, z + COVER), (x + 15, y, z + COVER + 1.5))


def tack(side=LEFT):
    x, y, z = rib_at(0, side, 0.5)
    return cyl(1, (x + 12, y, z + RIB_R[0]), (x + 12, y, z + RIB_R[0] + COVER + 1))


def lacing_cord(side=LEFT):
    tip = rib_points(7, side)[2]
    return cyl(1.5, (tip[0] - 30, tip[1], tip[2] - 20), (tip[0] - 30, tip[1], tip[2] + 20))


# ---------------------------------------------------------------- pilot interface (UK)

def arm_pad(side):
    """Leather pad on the arm bar with a raised elbow cup at the rear."""
    p0, p1 = arm_line(side, 450), arm_line(side, 880)
    pad = prism((p0[0], p0[1], ARM_TOP + 7.5), (p1[0], p1[1], ARM_TOP + 7.5), 50, 15)
    cx, cy = arm_line(side, 860)
    cup = box(cx - 20, cy - 25, ARM_TOP + 15, cx + 20, cy + 25, ARM_TOP + 45)
    return compound([pad, cup])


def hand_grip(side=LEFT):
    x, y = arm_line(side, 395)
    return cyl(15, (x, y - side * 20, ARM_TOP - 15), (x, y - side * 70, ARM_TOP - 15))


def arm_hanger(side=LEFT):
    x, y = arm_line(side, 400)
    return box(x - 15, y - 3, ARM_TOP - 10, x + 15, y + 3, Z_ROD - ROD_R + 5)


def forearm_strap(side=LEFT):
    x, y = arm_line(side, 820)
    w = 30
    return polyline_cable(3, [(x, y - w, ARM_TOP - 33), (x, y - w, ARM_TOP + 70), (x, y + w, ARM_TOP + 70),
                              (x, y + w, ARM_TOP - 33), (x, y - w, ARM_TOP - 33)])


def buckle(side=LEFT):
    x, y = arm_line(side, 820)
    return box(x - 4, y + 25, ARM_TOP + 60, x + 4, y + 35, ARM_TOP + 80)


SLING_X = 900
Y_SLING = math.sqrt(HOOP_R ** 2 - (SLING_X - HOOP_C[0]) ** 2)


def body_sling():
    z = Z_ROD - HOOP_ROD
    return polyline_cable(12, [(SLING_X, -Y_SLING, z), (SLING_X, -Y_SLING, -450), (SLING_X, Y_SLING, -450),
                               (SLING_X, Y_SLING, z)])


X_INDICATOR, Z_INDICATOR = 438, -300


def airspeed_indicator():
    """Dial case aft of the lower post, facing the pilot; its mount is not modelled."""
    return compound([box(X_INDICATOR - 20, -40, Z_INDICATOR - 30, X_INDICATOR + 20, 40, Z_INDICATOR + 30),
                     cyl(4, (X_INDICATOR + 20, 0, Z_INDICATOR), (X_INDICATOR + 35, 0, Z_INDICATOR))])


Y_SENSOR = -200


def sensor_base():
    x, y, z = bow_point(Y_SENSOR)
    return (x, y, z + 14)


def airspeed_sensor():
    """Vane sensor clamped on top of the bumper bow: housing and vane plate."""
    x, y, z = sensor_base()
    return compound([cyl(16, (x, y, z), (x, y, z + 70)), box(x - 30, y - 2, z + 70, x + 30, y + 2, z + 150)])


def ballast_carrier():
    x, y, z = bow_point(0)
    return box(x - 5, -90, z + 14, x + 55, 90, z + 20)


def ballast_bag():
    x, y, z = bow_point(0)
    return cyl(38, (x + 30, -80, z + 58), (x + 30, 80, z + 58))


# ---------------------------------------------------------------- parts: (CAD key, owner, label, builder)

SIDES = (("left", LEFT), ("right", RIGHT))

PARTS = (
    [(f"es-rib-{n}-{i + 1}", "ES", f"{n.capitalize()} rib {i + 1}", (lambda i=i, s=s: rib(i, s)))
     for n, s in SIDES for i in range(8)]
    + [(f"es-profile-rail-{k + 1}", "ES", f"Profile rail {k + 1}", (lambda k=k: profile_rail(k))) for k in range(4)]
    + [
        ("es-hoop", "ES", "Hoop", hoop),
        ("es-cross-rod", "ES", "Cross rod", cross_rod),
        ("es-bumper-bow", "ES", "Bumper bow", bumper_bow),
        ("es-tail-boom", "ES", "Tail boom", tail_boom),
        ("es-arm-bar-left", "ES", "Left arm bar", lambda: arm_bar(LEFT)),
        ("es-arm-bar-right", "ES", "Right arm bar", lambda: arm_bar(RIGHT)),
        ("es-tail-plane-frame", "ES", "Tail plane frame", tail_plane_frame),
        ("es-fin-frame", "ES", "Fin frame", fin_frame),
        ("es-upper-wires-left", "ES", "Left upper bracing wires (3)", lambda: upper_wires(LEFT)),
        ("es-upper-wires-right", "ES", "Right upper bracing wires (3)", lambda: upper_wires(RIGHT)),
        ("es-lower-wires-left", "ES", "Left lower bracing wires (3)", lambda: lower_wires(LEFT)),
        ("es-lower-wires-right", "ES", "Right lower bracing wires (3)", lambda: lower_wires(RIGHT)),
        ("es-front-wire", "ES", "Front tension wire", front_wire),
        ("es-tail-wire", "ES", "Fin stay wire", tail_wire),
        ("es-kingpost-stays", "ES", "Kingpost stays (2)", kingpost_stays),
        ("es-lower-post-stay", "ES", "Lower post stay", lower_post_stay),
        ("es-turnbuckle", "ES", "Turnbuckle", turnbuckle),
        ("es-thimble", "ES", "Wire thimble", thimble),
        ("es-hoop-clamp", "ES", "Cast hoop clamp", hoop_clamp),
        ("es-kingpost-step", "ES", "Cast kingpost step", kingpost_step),
        ("es-lower-post-shoe", "ES", "Cast lower post shoe", lower_post_shoe),
        ("es-boom-socket", "ES", "Cast boom socket", boom_socket),
        ("es-fin-step", "ES", "Cast fin step", fin_step),
        ("es-tail-skid", "ES", "Cast tail skid", tail_skid),
        ("de-kingpost", "DE", "Kingpost", kingpost),
        ("de-lower-post", "DE", "Lower post", lower_post),
        ("de-fin-post", "DE", "Fin post", fin_post),
        ("de-hub-left", "DE", "Left rib hub fitting", lambda: hub_fitting(LEFT)),
        ("de-hub-right", "DE", "Right rib hub fitting", lambda: hub_fitting(RIGHT)),
        ("de-rib-ferrule-large", "DE", "Rib pocket ferrule, first rib", lambda: rib_ferrule(0)),
        ("de-rib-ferrule", "DE", "Rib pocket ferrule", lambda: rib_ferrule(1)),
        ("de-pivot-pin", "DE", "Rib pivot pin ISO 2341", pivot_pin),
        ("de-hub-bolt", "DE", "Hub bolt ISO 4014", hub_bolt),
        ("de-rib-tip-cap", "DE", "Rib tip cap", rib_tip_cap),
        ("de-wire-clip", "DE", "Rib wire clip", wire_clip),
        ("de-rail-clip", "DE", "Profile rail clip", rail_clip),
        ("de-arm-hanger", "DE", "Arm bar hanger", arm_hanger),
        ("de-bow-clamp", "DE", "Bumper bow clamp", lambda: box(X_ROD - 20, -BOW_END_Y - 20, Z_ROD - 22, X_ROD + 20,
                                                                 -BOW_END_Y + 20, -8)),
        ("de-tail-hinge", "DE", "Tail plane hinge fitting", tail_hinge_fitting),
        ("de-tail-hinge-pin", "DE", "Tail plane hinge pin", tail_hinge_pin),
        ("de-tail-stop", "DE", "Tail plane stop", tail_stop),
        ("fr-cover-left", "FR", "Left wing covering", lambda: wing_cover(LEFT)),
        ("fr-cover-right", "FR", "Right wing covering", lambda: wing_cover(RIGHT)),
        ("fr-tail-plane-cover", "FR", "Tail plane covering", tail_plane_cover),
        ("fr-fin-cover", "FR", "Fin covering", fin_cover),
        ("fr-trailing-cord-left", "FR", "Left trailing edge cord", lambda: trailing_edge_cord(LEFT)),
        ("fr-trailing-cord-right", "FR", "Right trailing edge cord", lambda: trailing_edge_cord(RIGHT)),
        ("fr-leading-binding", "FR", "Leading edge binding", leading_edge_binding),
        ("fr-tail-spring", "FR", "Tail plane return spring", tail_spring),
        ("fr-latch-spring", "FR", "Front wire latch spring", latch_spring),
        ("fr-eyelet", "FR", "Covering eyelet", eyelet),
        ("fr-tack", "FR", "Copper tack", tack),
        ("fr-lacing-cord", "FR", "Lacing cord", lacing_cord),
        ("uk-arm-pad-left", "UK", "Left arm pad", lambda: arm_pad(LEFT)),
        ("uk-arm-pad-right", "UK", "Right arm pad", lambda: arm_pad(RIGHT)),
        ("uk-hand-grip", "UK", "Hand grip", hand_grip),
        ("uk-forearm-strap", "UK", "Forearm strap", forearm_strap),
        ("uk-buckle", "UK", "Strap buckle", buckle),
        ("uk-body-sling", "UK", "Pilot body sling (demonstrator addition)", body_sling),
        ("uk-airspeed-indicator", "UK", "Airspeed indicator (demonstrator addition)", airspeed_indicator),
        ("uk-airspeed-sensor", "UK", "Airspeed vane sensor (demonstrator addition)", airspeed_sensor),
        ("uk-ballast-bag", "UK", "Nose ballast bag", ballast_bag),
        ("uk-ballast-carrier", "UK", "Ballast carrier plate", ballast_carrier),
    ]
)
