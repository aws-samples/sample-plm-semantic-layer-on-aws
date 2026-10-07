# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Ornithopter ground demonstrator after the prone-pilot flying machine of Paris Manuscript B, f. 74v (Milan,
about 1487-1490), built today as an instrumented rig: 76 parts, one STEP file each.

Frame (mm): x aft from the frame nose, y to the pilot's right, z up with z = 0 on the keel beam axis. Envelope from
the working model of the folio in the Museo Galileo (167 x 490 x 1100 cm): span 11.0 m (wing tips at y +/-5500),
length 4.9 m (frame x 0 to 3600, tail plane to 4900), height 1.67 m (skid underside z -580, control post top 1090).
Layout from the folio: a hinged two-piece spar per wing (inner spar and swept outer "finger" joined at a knuckle),
six curved cane ribs per wing hanging aft from the spar with linen between them, a prone pilot on a cradle with a
hand crank and windlass drum ahead of the chest, foot stirrups aft, a head hoop steering a tail plane on a boom,
drive cords over pulleys to the wings, and from its text the wings raised by pulling the feet back: a return cord
per wing from the stirrups to the root fitting. The crank is a shaft, two arms with their handles and two bearing
posts; it drives the drum through a lantern pinion of 8 staves and a peg wheel of 32 pegs at module 12 mm, the drum
on its own axle 240 mm aft of the crank. Inferred, not in any source: the dihedral and sweep angles, the rib camber,
the member sections, the crank's division into parts, the gear stage's teeth, modules and positions, the return
cords' path, the pilot harness's two straps, and the position of every modern item (hydraulic actuators and power
pack, instrumentation pod, sensor harnesses, tail servo, the water ballast tank under the keel with its ground
service panel and the trim bottle on the tail boom, the ballast stowage bay with its floor, liner and cans aft of
the cradle, the rib shoes, the lacing cords, the laced access flaps at WS 1500 and WS 3600 with their grease lines,
the ground handling wheel sets under the skids).

The left wing is the UK workshop's, the right wing the German one's, the frame the French integrator's and the crank,
pedal, cord and tail set the Spanish one's. The part keys here are the CAD keys of data/products/ornithopter.json
(cad/ornithopter/<key>.stp); the PLM part ids live in that file.
"""
import math

from geometry import V, box, compound, cyl, polyline_cable, prism, swept_arc

KEY = "ornithopter"
NAME = "Ornithopter ground demonstrator (Paris Manuscript B, f. 74v)"
FRAME = "x aft from the frame nose, y to the pilot's right, z up with z = 0 on the keel beam axis; mm"

# ---------------------------------------------------------------- frame and cradle (FR)

FRAME_X0, FRAME_X1 = 0, 3600
RAIL_Y, CROSS_X = 500, (600, 1900, 3200)
X_ROOT = 1900                       # wing root station = middle cross beam


def keel_beam():
    return box(FRAME_X0, -60, -60, FRAME_X1, 60, 60)


def side_rail(side):
    return box(FRAME_X0, side * RAIL_Y - 40, -50, FRAME_X1, side * RAIL_Y + 40, 50)


def cross_beam(x):
    return box(x - 50, -560, -50, x + 50, 560, 50)


def pilot_cradle():
    return box(800, -250, 50, 2700, 250, 80)


def head_hoop():
    import cadquery as cq
    return cq.Solid.makeTorus(220, 20, V(450, 0, 350), V(1, 0, 0))


def skid(side):
    y = side * RAIL_Y
    return compound([box(600, y - 30, -580, 3000, y + 30, -520),
                     cyl(25, (800, y, -50), (800, y, -520)), cyl(25, (2800, y, -50), (2800, y, -520))])


def control_post():
    return compound([cyl(40, (300, 0, 50), (300, 0, 1090)), box(280, -200, 1040, 320, 200, 1080)])


def instrumentation_pod():
    return box(125, -125, 800, 475, 125, 950)


def hydraulic_power_pack():
    return box(2300, -200, -400, 2900, 200, -60)


def ballast_tank():
    """Water ballast tank under the keel; fill and drain stubs to the ground service panel."""
    return compound([cyl(150, (1300, 0, -260), (2000, 0, -260)),
                     polyline_cable(12, [(1900, -100, -150), (2100, -300, -100), (2100, -540, 50)]),
                     polyline_cable(12, [(1950, -100, -350), (2100, -300, -200), (2100, -540, 0)])])


def ground_service_panel():
    """The panel outboard of the left side rail, with its brake and nitrogen hoses down to the left wheel set."""
    return compound([box(2000, -620, -50, 2200, -540, 55),
                     polyline_cable(5, [(2180, -565, -50), (2180, -560, -380), WHEEL_BRAKE_PORT]),
                     polyline_cable(4, [(2150, -610, -50), (2150, -650, -380), WHEEL_VALVE])])


def pilot_harness():
    return compound([box(1270, -260, 80, 1330, 260, 92), box(2070, -260, 80, 2130, 260, 92)])


def stowage_bay():
    """Open frame of 15 mm walls aft of the cradle, standing on the keel."""
    return compound([box(2720, -250, 60, 2735, 250, 260), box(3105, -250, 60, 3120, 250, 260),
                     box(2735, -250, 60, 3105, -235, 260), box(2735, 235, 60, 3105, 250, 260)])


def stowage_floor():
    return box(2735, -235, 60, 3105, 235, 72)


def bay_liner():
    """Leather liner 3 mm thick, open at the top, inside the bay walls and on the floor."""
    return compound([box(2735, -235, 72, 3105, 235, 75),
                     box(2735, -235, 75, 2738, 235, 250), box(3102, -235, 75, 3105, 235, 250),
                     box(2738, -235, 75, 3102, -232, 250), box(2738, 232, 75, 3102, 235, 250)])


# ---------------------------------------------------------------- ground handling wheel set (UK), left set

WHEEL = (1800, -590, -560)                # wheel centre under the left skid; the right set is the left turned about z
WHEEL_BRAKE_PORT = (1800, -540, -490)     # top of the brake drum
WHEEL_VALVE = (1800, -640, -490)          # inflation valve on the rim's outer face


def ground_wheel():
    """Rim and hub, radius 95 and 85 wide, with the axle stub to the skid's outer face."""
    x, y, z = WHEEL
    return compound([cyl(95, (x, y - 42.5, z), (x, y + 42.5, z)), cyl(20, (x, y + 42.5, z), (x, -530, z))])


def ground_tyre():
    import cadquery as cq
    return cq.Solid.makeTorus(112.5, 17.5, V(*WHEEL), V(0, 1, 0))


def wheel_brake():
    """Drum brake on the wheel's inboard face: an annulus round the axle stub."""
    import cadquery as cq
    x, y, z = WHEEL
    plane = cq.Plane(origin=(x, y + 42.5, z), xDir=(1, 0, 0), normal=(0, 1, 0))
    return cq.Workplane(plane).circle(70).circle(20).extrude(15).val()


def ballast_can(x0, y0):
    """A 5 l ballast can, 120 deep (x), 190 wide (y) and 280 high, standing on the bay liner."""
    return box(x0, y0, 75, x0 + 120, y0 + 190, 355)


def wing_root_carrier(side):
    y0, y1 = side * 520, side * 640
    return compound([box(X_ROOT - 150, min(y0, y1), 60, X_ROOT + 150, max(y0, y1), 310),
                     cyl(20, (X_ROOT, side * 520, 250), (X_ROOT, side * 780, 250))])


# ---------------------------------------------------------------- wings (UK left, DE right)

S_ROOT, S_KNUCKLE, S_TIP = 700, 3000, 5500                 # spanwise stations (mm from the centreline)
DIHEDRAL_IN, DIHEDRAL_OUT, SWEEP_OUT = 8.0, 10.0, 12.0     # degrees, read from the folio
SPAR_Z0 = 240
S1 = (X_ROOT, S_KNUCKLE, SPAR_Z0 + (S_KNUCKLE - S_ROOT) * math.tan(math.radians(DIHEDRAL_IN)))
S2 = (X_ROOT + (S_TIP - S_KNUCKLE) * math.tan(math.radians(SWEEP_OUT)), S_TIP,
      S1[2] + (S_TIP - S_KNUCKLE) * math.tan(math.radians(DIHEDRAL_OUT)))
CHORD = {S_ROOT: 2400, S_KNUCKLE: 1700, S_TIP: 500}        # rib length root / knuckle / tip
RIB_STATIONS_IN, RIB_STATIONS_OUT = (1200, 1800, 2500), (3400, 4100, 4800)


def lerp(a, b, t):
    return tuple(ai + (bi - ai) * t for ai, bi in zip(a, b))


def spar_point(s, side=1):
    if s <= S_KNUCKLE:
        p = lerp((X_ROOT, S_ROOT, SPAR_Z0), S1, (s - S_ROOT) / (S_KNUCKLE - S_ROOT))
    else:
        p = lerp(S1, S2, (s - S_KNUCKLE) / (S_TIP - S_KNUCKLE))
    return (p[0], side * p[1], p[2])


def chord(s):
    if s <= S_KNUCKLE:
        return CHORD[S_ROOT] + (CHORD[S_KNUCKLE] - CHORD[S_ROOT]) * (s - S_ROOT) / (S_KNUCKLE - S_ROOT)
    return CHORD[S_KNUCKLE] + (CHORD[S_TIP] - CHORD[S_KNUCKLE]) * (s - S_KNUCKLE) / (S_TIP - S_KNUCKLE)


def rib_arc(s, side, dz=0.0):
    """(start, mid, end) of the cambered rib at station s: hangs aft from the spar's aft face."""
    x, y, z = spar_point(s, side)
    c = chord(s)
    return (x + 50, y, z + dz), (x + 50 + 0.5 * c, y, z + 0.12 * c + dz), (x + 50 + c, y, z - 0.18 * c + dz)


def inner_spar(side):
    return prism(spar_point(S_ROOT, side), spar_point(S_KNUCKLE, side), 100, 140)


def outer_spar(side):
    return prism(spar_point(S_KNUCKLE, side), spar_point(S_TIP, side), 80, 110)


def knuckle_hinge(side):
    x, y, z = spar_point(S_KNUCKLE, side)
    return compound([cyl(25, (x - 150, y, z), (x + 150, y, z)),
                     box(x - 150, y - 90, z - 100, x + 150, y - 70, z + 100),
                     box(x - 150, y + 70, z - 100, x + 150, y + 90, z + 100)])


def lacing_cord(side):
    """Raw silk lacing cord, 10 mm, along the spars' aft face from the root to the tip."""
    return polyline_cable(5, [(1960, side * 750, 247), (1960, side * 3000, 563), (2481, side * 5450, 995)])


def rib_shoe(side):
    """The socket seating a cane on the spar's aft face, drawn at WS 1200; its bill-of-materials line places it at
    every rib station."""
    y0, y1 = sorted((side * 1170, side * 1230))
    return box(1950, y0, 250, 2010, y1, 370)


def ribs(side, stations):
    return compound([swept_arc(18, *rib_arc(s, side)) for s in stations])


def linen_panel(side, s0, s1, n=7):
    """Ruled loft of two 6-edge profiles that follow the rib arc, 10 mm thick, under the rib centreline."""
    import cadquery as cq

    def profile(s):
        p, m, q = rib_arc(s, side, dz=-18)
        edge = cq.Edge.makeThreePointArc(V(*p), V(*m), V(*q))
        up = [edge.positionAt(i / (n - 1)) for i in range(n)]
        lo = [V(v.x, v.y, v.z - 10) for v in up[::-1]]
        return cq.Wire.assembleEdges([cq.Edge.makeSpline(up), cq.Edge.makeLine(up[-1], lo[0]),
                                      cq.Edge.makeSpline(lo), cq.Edge.makeLine(lo[-1], up[0])])
    return cq.Solid.makeLoft([profile(s0), profile(s1)], ruled=True)


def root_fitting(side):
    y0, y1 = sorted((side * 640, side * 760))
    return compound([box(X_ROOT - 100, y0, 150, X_ROOT + 100, y1, 330),
                     cyl(45, (X_ROOT - 100, side * 700, 250), (X_ROOT + 100, side * 700, 250))])


def wing_actuator(side):
    """Hydraulic linear actuator: barrel on the side rail, rod end on the inner spar at s 1500."""
    a = (2300, side * 480, -120)
    b = tuple(c + d for c, d in zip(spar_point(1500, side), (0, 0, -70)))
    return compound([cyl(45, a, lerp(a, b, 0.55)), cyl(20, lerp(a, b, 0.5), b)])


def sensor_harness(side):
    """Strain-gauge and actuator-sensor loom from the instrumentation pod to the wing tip along the spar."""
    kx, ky, kz = spar_point(S_KNUCKLE, side)
    tx, ty, tz = spar_point(S_TIP, side)
    return polyline_cable(6, [(475, side * 125, 950), (X_ROOT, side * 700, 330), (kx, ky, kz + 80), (tx, ty, tz + 60)])


LINEN_SECTORS = ((750, 2950), (3050, 5450))   # the inner and outer linen panels' end stations


_LINEN = {}


def linen_underside(side, s, x):
    """Point of the linen panel's lower surface at station s whose x is `x`: the ruled loft of linen_panel()
    between its two end profiles, found by bisection along the chord and then on the solid itself."""
    import cadquery as cq
    s0, s1 = next(sector for sector in LINEN_SECTORS if sector[0] <= s <= sector[1])
    edges = [cq.Edge.makeThreePointArc(*[V(*q) for q in rib_arc(st, side, dz=-18)]) for st in (s0, s1)]
    t = (s - s0) / (s1 - s0)

    def at(u):
        a, b = edges[0].positionAt(u), edges[1].positionAt(u)
        return (a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t - 10)
    lo, hi = 0.0, 1.0
    for _ in range(60):
        mid = (lo + hi) / 2
        lo, hi = (mid, hi) if at(mid)[0] < x else (lo, mid)
    px, py, pz = at((lo + hi) / 2)
    panel = _LINEN.setdefault((side, s0), linen_panel(side, s0, s1))
    below, inside = pz - 8, pz + 5
    for _ in range(30):
        mid = (below + inside) / 2
        below, inside = (below, mid) if panel.isInside(V(x, py, mid)) else (mid, inside)
    return (x, py, below)


def access_point(side, s):
    """(flap centre, flap leading edge on the linen): the flap sits 300 mm aft of the spar at station s."""
    x = spar_point(s, side)[0]
    return linen_underside(side, s, x + 300), linen_underside(side, s, x + 200)


def served_point(side, s):
    """The point an access flap serves: the actuator rod end at WS 1500, the drive cord shackle at WS 3600."""
    if s == 1500:
        return tuple(c + d for c, d in zip(spar_point(1500, side), (0, 0, -70)))
    return tuple(c + d for c, d in zip(spar_point(3600, side), (0, 0, 80)))


def access_flap(side, s):
    """A 200 x 160 mm laced flap 4 mm thick following the linen's lower surface at station s, and its 6 mm grease
    line to the point it serves."""
    import cadquery as cq
    centre, _ = access_point(side, s)
    x = spar_point(s, side)[0] + 300

    def profile(st, n=7):
        up = [V(*linen_underside(side, st, x - 100 + 200 * i / (n - 1))) - V(0, 0, 0.5) for i in range(n)]
        lo = [V(v.x, v.y, v.z - 4) for v in up[::-1]]
        return cq.Wire.assembleEdges([cq.Edge.makeSpline(up), cq.Edge.makeLine(up[-1], lo[0]),
                                      cq.Edge.makeSpline(lo), cq.Edge.makeLine(lo[-1], up[0])])
    plate = cq.Solid.makeLoft([profile(s - 80), profile(s + 80)], ruled=True)
    # The WS 1500 line passes under the inner spar to the actuator rod end below it.
    route = [centre, (spar_point(s, side)[0] + 60, side * s, served_point(side, s)[2] - 20)] if s == 1500 else [centre]
    return compound([plate, polyline_cable(3, route + [served_point(side, s)])])


# ---------------------------------------------------------------- crank mechanism and tail (ES)

CRANK = dict(x=900, z=450)
DRUM = dict(x=1140, z=450)          # the drum axle, the gear stage's centre distance (48 + 192 mm) aft of the crank


def crank_shaft():
    return cyl(25, (CRANK["x"], -480, CRANK["z"]), (CRANK["x"], 480, CRANK["z"]))


def crank_arm(side):
    """Arm keyed to the shaft at y = side * 460 and its handle."""
    x, y, z = CRANK["x"], side * 460, CRANK["z"]
    return compound([box(x, y - 20, z - 20, x + 200, y + 20, z + 20), cyl(15, (x + 180, y, z), (x + 180, y + side * 160, z))])


def crank_post(side):
    """Bearing pedestal under the shaft: its top is the shaft's underside, so the arm keyed above it clears it."""
    x, y = CRANK["x"], side * 460
    return box(x - 50, y - 40, 50, x + 50, y + 40, CRANK["z"] - 25)


def windlass_drum():
    x, z = DRUM["x"], DRUM["z"]
    parts = [cyl(120, (x, -150, z), (x, 150, z)), cyl(25, (x, -480, z), (x, 480, z))]
    parts += [box(x - 40, min(side * 420, side * 500), 50, x + 40, max(side * 420, side * 500), z + 20) for side in (1, -1)]
    return compound(parts)


def lantern_pinion():
    """Two discs on the crank shaft and 8 staves on the 48 mm pitch circle between them, a gap between two staves
    facing the peg wheel so that the drawn pegs and staves do not overlap."""
    x, z = CRANK["x"], CRANK["z"]
    parts = [cyl(60, (x, y - 5, z), (x, y + 5, z)) for y in (-270, -230)]
    for k in range(8):
        a = 2 * math.pi * (k + 0.5) / 8
        px, pz = x + 48 * math.cos(a), z + 48 * math.sin(a)
        parts.append(cyl(8, (px, -265, pz), (px, -235, pz)))
    return compound(parts)


def peg_wheel():
    """A disc on the drum axle and 32 radial pegs on the 192 mm pitch circle; the disc stops short of the staves."""
    x, z = DRUM["x"], DRUM["z"]
    parts = [cyl(180, (x, -265, z), (x, -235, z))]
    for k in range(32):
        a = 2 * math.pi * k / 32
        c, s = math.cos(a), math.sin(a)
        parts.append(cyl(8, (x + 175 * c, -250, z + 175 * s), (x + 215 * c, -250, z + 215 * s)))
    return compound(parts)


def pedal_set():
    parts = [cyl(20, (3300, -400, 50), (3300, 400, 50))]
    for side in (1, -1):
        y = side * 300
        parts += [cyl(15, (3300, y, 50), (3350, y, 150)), box(3300, y - 60, 150, 3450, y + 60, 190)]
    return compound(parts)


def pedal_cables():
    return compound([cyl(5, (3350, side * 300, 150), (DRUM["x"], side * 150, 330)) for side in (1, -1)])


def return_cord(side):
    """Wing return cord: from the stirrup heel over the pulley top, clear of the sheave, to the root fitting eye."""
    return polyline_cable(5, [(3450, side * 300, 170), (1955, side * 560, 590), (1925, side * 560, 590),
                              (1900, side * 700, 340)])


def pulley_block(side):
    y = side * 560
    return compound([box(X_ROOT - 30, y - 30, 50, X_ROOT + 30, y + 30, 500),
                     cyl(70, (X_ROOT + 35, y, 500), (X_ROOT + 75, y, 500))])


def wing_drive_cable(side):
    ax, ay, az = spar_point(3600, side)
    return polyline_cable(5, [(DRUM["x"], side * 150, DRUM["z"] + 120), (X_ROOT + 55, side * 560, 570), (ax, ay, az + 80)])


def tail_boom():
    return cyl(40, (FRAME_X1, 0, 0), (4700, 0, 100))


def tail_plane():
    import cadquery as cq
    plate = cq.Workplane("XY", origin=(4250, 0, 100)).ellipse(650, 400).extrude(15).val()
    return compound([plate, cyl(20, (4250, -420, 122), (4250, 420, 122))])


def tail_control_cable():
    return polyline_cable(5, [(450, 0, 130), (600, 80, 70), (FRAME_X1, 80, 70), (3700, 0, 110)])


def tail_servo():
    return box(3620, 50, 100, 3780, 140, 180)


def tail_trim_bottle():
    """Trim water bottle under the tail boom and its line from the ballast tank's aft face."""
    return compound([cyl(70, (4300, 0, -60), (4600, 0, -60)),
                     polyline_cable(8, [(2000, 0, -260), (2100, -250, -260), (3600, -250, -60), (4300, 0, -60)])])


# ---------------------------------------------------------------- parts: (CAD key, owner, label, builder)

PARTS = [
    ("fr-keel-beam", "FR", "Keel beam", keel_beam),
    ("fr-side-rail-left", "FR", "Left side rail", lambda: side_rail(-1)),
    ("fr-side-rail-right", "FR", "Right side rail", lambda: side_rail(+1)),
    ("fr-cross-beam-fwd", "FR", "Forward cross beam (crank bearings)", lambda: cross_beam(CROSS_X[0])),
    ("fr-cross-beam-mid", "FR", "Middle cross beam (wing root station)", lambda: cross_beam(CROSS_X[1])),
    ("fr-cross-beam-aft", "FR", "Aft cross beam (pedal station)", lambda: cross_beam(CROSS_X[2])),
    ("fr-pilot-cradle", "FR", "Pilot cradle board", pilot_cradle),
    ("fr-head-hoop", "FR", "Head hoop (tail steering)", head_hoop),
    ("fr-skid-left", "FR", "Left landing skid", lambda: skid(-1)),
    ("fr-skid-right", "FR", "Right landing skid", lambda: skid(+1)),
    ("fr-control-post", "FR", "Control post", control_post),
    ("fr-instrumentation-pod", "FR", "Instrumentation pod (data acquisition)", instrumentation_pod),
    ("fr-hydraulic-power-pack", "FR", "Hydraulic power pack (ground demonstrator)", hydraulic_power_pack),
    ("fr-wing-root-carrier-left", "FR", "Left wing root carrier and pin", lambda: wing_root_carrier(-1)),
    ("fr-wing-root-carrier-right", "FR", "Right wing root carrier and pin", lambda: wing_root_carrier(+1)),
]
for _side, _owner, _tag in ((-1, "UK", "left"), (+1, "DE", "right")):
    _pre = f"{_owner.lower()}-{_tag}"
    PARTS += [
        (f"{_pre}-inner-spar", _owner, f"{_tag.capitalize()} inner wing spar", lambda s=_side: inner_spar(s)),
        (f"{_pre}-outer-spar", _owner, f"{_tag.capitalize()} outer wing spar (finger)", lambda s=_side: outer_spar(s)),
        (f"{_pre}-knuckle-hinge", _owner, f"{_tag.capitalize()} knuckle hinge", lambda s=_side: knuckle_hinge(s)),
        (f"{_pre}-ribs-inner", _owner, f"{_tag.capitalize()} inner ribs (3 canes)", lambda s=_side: ribs(s, RIB_STATIONS_IN)),
        (f"{_pre}-ribs-outer", _owner, f"{_tag.capitalize()} outer ribs (3 canes)", lambda s=_side: ribs(s, RIB_STATIONS_OUT)),
        (f"{_pre}-linen-inner", _owner, f"{_tag.capitalize()} inner linen panel", lambda s=_side: linen_panel(s, 750, 2950)),
        (f"{_pre}-linen-outer", _owner, f"{_tag.capitalize()} outer linen panel", lambda s=_side: linen_panel(s, 3050, 5450)),
        (f"{_pre}-root-fitting", _owner, f"{_tag.capitalize()} wing root fitting", lambda s=_side: root_fitting(s)),
        (f"{_pre}-wing-actuator", _owner, f"{_tag.capitalize()} wing hydraulic actuator", lambda s=_side: wing_actuator(s)),
        (f"{_pre}-sensor-harness", _owner, f"{_tag.capitalize()} wing sensor harness", lambda s=_side: sensor_harness(s)),
    ]
PARTS += [
    ("es-crank-shaft", "ES", "Crank shaft", crank_shaft),
    ("es-windlass-drum", "ES", "Windlass drum", windlass_drum),
    ("es-pedal-set", "ES", "Foot stirrups and pivot shaft", pedal_set),
    ("es-pedal-cables", "ES", "Pedal cords to the drum", pedal_cables),
    ("es-pulley-block-left", "ES", "Left pulley block", lambda: pulley_block(-1)),
    ("es-pulley-block-right", "ES", "Right pulley block", lambda: pulley_block(+1)),
    ("es-wing-drive-cable-left", "ES", "Left wing drive cord", lambda: wing_drive_cable(-1)),
    ("es-wing-drive-cable-right", "ES", "Right wing drive cord", lambda: wing_drive_cable(+1)),
    ("es-tail-boom", "ES", "Tail boom", tail_boom),
    ("es-tail-plane", "ES", "Tail plane (linen on a cross spar)", tail_plane),
    ("es-tail-control-cable", "ES", "Tail control cord from the head hoop", tail_control_cable),
    ("es-tail-servo", "ES", "Tail trim servo", tail_servo),
]
# The crank assembly's arms and posts, and the gear stage between the crank and the drum.
PARTS += [
    ("es-crank-arm-left", "ES", "Left crank arm and handle", lambda: crank_arm(-1)),
    ("es-crank-arm-right", "ES", "Right crank arm and handle", lambda: crank_arm(+1)),
    ("es-crank-post-left", "ES", "Left crank bearing post", lambda: crank_post(-1)),
    ("es-crank-post-right", "ES", "Right crank bearing post", lambda: crank_post(+1)),
    ("es-lantern-pinion", "ES", "Crank lantern pinion (8 staves)", lantern_pinion),
    ("fr-peg-wheel", "FR", "Windlass peg wheel (32 pegs)", peg_wheel),
]
# Installed equipment: the water ballast system and the pilot harness.
PARTS += [
    ("fr-ballast-tank", "FR", "Ballast water tank (centre of gravity trim)", ballast_tank),
    ("fr-ground-service-panel", "FR", "Ground service panel (water, drain, 24 V)", ground_service_panel),
    ("es-tail-trim-bottle", "ES", "Tail trim water bottle and line", tail_trim_bottle),
    ("fr-pilot-harness", "FR", "Pilot harness", pilot_harness),
]
# The wing return cords, from the stirrups to the root fittings.
PARTS += [
    ("es-return-cord-left", "ES", "Left return cord", lambda: return_cord(-1)),
    ("es-return-cord-right", "ES", "Right return cord", lambda: return_cord(+1)),
]
# Access flaps laced into the linen at WS 1500 and WS 3600.
PARTS += [
    ("uk-left-access-ws1500", "UK", "Left actuator access flap, WS 1500", lambda: access_flap(-1, 1500)),
    ("uk-left-access-ws3600", "UK", "Left cord shackle access flap, WS 3600", lambda: access_flap(-1, 3600)),
    ("de-right-access-ws1500", "DE", "Right actuator access flap, WS 1500", lambda: access_flap(+1, 1500)),
    ("de-right-access-ws3600", "DE", "Right cord shackle access flap, WS 3600", lambda: access_flap(+1, 3600)),
]
# The ballast stowage bay aft of the cradle: frame, floor panel and liner.
PARTS += [
    ("fr-stowage-bay", "FR", "Ballast stowage bay", stowage_bay),
    ("fr-stowage-floor", "FR", "Stowage bay floor panel", stowage_floor),
    ("es-bay-liner", "ES", "Ballast bay leather liner", bay_liner),
]
# The ground handling wheel set, drawn at the left skid; its bill-of-materials line places it twice.
PARTS += [
    ("uk-ground-wheel", "UK", "Ground handling wheel, 260 x 85", ground_wheel),
    ("uk-ground-tyre", "UK", "Tyre 260 x 85, pneumatic", ground_tyre),
    ("uk-wheel-brake", "UK", "Wheel brake, hydraulic drum", wheel_brake),
]
# Raw silk lacing cords, rib shoes and the ballast cans: one STEP each, placed by their bill-of-materials lines.
PARTS += [
    ("uk-left-lacing-cord", "UK", "Left wing lacing cord, raw silk", lambda: lacing_cord(-1)),
    ("de-right-lacing-cord", "DE", "Right wing lacing cord, raw silk", lambda: lacing_cord(+1)),
    ("uk-left-rib-shoe", "UK", "Left rib shoe", lambda: rib_shoe(-1)),
    ("de-right-rib-shoe", "DE", "Right rib shoe", lambda: rib_shoe(+1)),
    ("fr-ballast-can", "FR", "Ballast can, 5 l", lambda: ballast_can(2745, -225)),
    ("de-ballast-can", "DE", "Ballast can, 5 l", lambda: ballast_can(2975, -225)),
    ("uk-ballast-can", "UK", "Ballast can, 5 l", lambda: ballast_can(2975, 35)),
]
# The variant options' own parts: the return spring of each wing (option spring-return of the wing return) and the
# crank forged in one piece (option one-piece of the crank build), drawn where the parts they replace are.


def return_spring(side):
    """A 40 mm coil, drawn as a cylinder, from the root carrier's spring anchor to the root fitting's return eye."""
    return cyl(20, (1950, side * 580, 310), (1900, side * 700, 340))


def crank_one_piece():
    """The crank shaft, both arms with their handles and both bearing posts as one forged part."""
    arms = [solid for side in (-1, 1) for solid in crank_arm(side).Solids()]
    return compound([crank_shaft(), *arms, crank_post(-1), crank_post(1)])


PARTS += [
    ("uk-left-return-spring", "UK", "Left wing return spring", lambda: return_spring(-1)),
    ("de-right-return-spring", "DE", "Right wing return spring", lambda: return_spring(+1)),
    ("es-crank-one-piece", "ES", "One-piece forged crank", crank_one_piece),
]
