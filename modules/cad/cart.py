# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Self-propelled cart of the Codex Atlanticus, folio 812r (about 1478-1480), built today as a full-size working
reconstruction: 27 parts, one STEP file each.

Frame (mm): x forward in the direction of travel, y to the left, z up with z = 0 on the ground; the origin is on
the ground under the centre of the drive axle. Length 1700 (drive wheel front at x 500, steering wheel rear at
x -1200), width 1420 over the drive wheels (centre planes at y +/-680), spring shaft heads at z 740.

The folio is incomplete and reconstructions disagree; the choices built here, each also recorded on the part it
concerns in data/products/cart.json:
- Running gear: the two large wheels of the folio are the drive wheels, 1000 mm in diameter, each on its own
  stub axle at the front of a timber ladder frame; the small wheel is a rear steering castor on a vertical pivot,
  set before the run. The cart is a tricycle.
- Drive springs: the hatched discs under the gear wheels are coiled flat-strip (leaf) springs, one per side, in an
  open timber housing; the inner tongue of each spring is pinned to the hub of a crown wheel above it, the outer
  end to the housing wall.
- Transmission: each crown wheel (teeth on its underside) drives a lantern pinion on one common transverse output
  shaft whose ends are cross-pinned into the stub axles; both springs drive both wheels.
- Regulation: no escapement is built; a ratchet wheel on each spring shaft and a pawl on the housing wall hold the
  wound spring, and the square shaft heads take a winding key.
- Steering programme: a cam on each spring shaft above the crown wheel nudges the follower bar of the steering
  tiller left or right as the gears turn, after the pre-set steering of the folio.

The part keys here are the CAD keys of data/products/cart.json (cad/cart/<key>.stp); the PLM part ids live in
that file.
"""
import math

import cadquery as cq

from geometry import V, box, compound, cyl, prism, ring

KEY = "cart"
NAME = "Self-propelled cart (Codex Atlanticus, f. 812r)"
FRAME = "x forward in the direction of travel, y to the left, z up with z = 0 on the ground under the drive axle centre; mm"

# ---------------------------------------------------------------- running gear and frame (ES)

R_WHEEL, W_WHEEL, Y_WHEEL = 500.0, 60.0, 680.0      # drive wheel radius, width, centre plane (+/-y)
Z_AXLE = R_WHEEL                                    # the drive axle line is x = 0, z = 500
RAIL_Y0, RAIL_Y1 = 540.0, 620.0                     # side rail faces, one side
RAIL_Z0, RAIL_Z1 = 380.0, 460.0
X_FRONT, X_REAR = 330.0, -1050.0                    # frame ends
CROSS_X = ((250.0, 330.0), (-180.0, -100.0), (-650.0, -570.0), (-1050.0, -970.0))  # front, housing, housing, rear
BLOCK_X, BLOCK_Z1 = 70.0, 560.0                     # bearing blocks on the rails, x +/-70, up to z 560
Y_MUFF0, Y_MUFF1, Y_AXLE_END = 400.0, 450.0, 740.0  # stub axle: coupling muff over the output shaft end, outer end
R_AXLE, R_MUFF = 30.0, 45.0

# ---------------------------------------------------------------- spring drive and steering (FR)

X_GEAR, Y_GEAR = -300.0, 330.0                      # spring shaft axes (x, +/-y)
HOUSING = dict(x0=-650.0, x1=-100.0, y=520.0, z0=460.0, z1=560.0, wall=20.0)
SPRING = dict(r0=70.0, r1=170.0, z0=490.0, h=50.0, t=3.0, half_turns=5)   # 2.5 turns
X_PIVOT = -1010.0                                   # steering pivot, on the rear cross member
X_CASTOR, R_CASTOR = -1050.0, 150.0
Z_TILLER0, Z_TILLER1 = 640.0, 680.0

# ---------------------------------------------------------------- gear train (DE)

R_GEAR, Z_GEAR0, Z_GEAR1 = 300.0, 580.0, 620.0      # crown wheel: rim reaches x = 0 over the output shaft
R_HUB, Z_HUB0 = 60.0, 490.0
R_PINION, PINION_W, N_STAVES = 80.0, 10.0, 6        # lantern pinion under the crown wheel rim
R_RATCHET, Z_RATCHET0 = 100.0, 560.0
R_CAM, R_LOBE, Z_CAM0 = 100.0, 40.0, 630.0
Z_SHAFT0, Z_SHAFT1, R_SHAFT = 480.0, 700.0, 25.0
HEAD = 30.0                                         # square winding head, z 700 to 740


def ring_along(r_in, r_out, p0, p1):
    """Annulus between r_in and r_out whose axis runs from p0 to p1."""
    d = V(*p1) - V(*p0)
    n = d.normalized()
    x_dir = V(0, 0, 1) if abs(n.z) < 0.5 else V(1, 0, 0)
    return cq.Workplane(cq.Plane(origin=V(*p0), xDir=x_dir, normal=n)).circle(r_out).circle(r_in).extrude(d.Length).val()


def spiral_strip(cx, cy, z0, h, r0, r1, half_turns, t):
    """Flat strip t thick and h tall coiled about (cx, cy): semicircles of radius r0 to r1 on two centres d apart,
    tangent-continuous, swept with the Frenet frame; one toroidal face per arc and side. The coil starts at
    (cx, cy - r0) and ends at (cx, cy + r1)."""
    d = (r1 - r0) / (half_turns - 1)
    edges = []
    for k in range(half_turns):
        r = r0 + k * d
        c = V(cx, cy if k % 2 == 0 else cy - d, z0 + h / 2)
        a0, am = (270, 0) if k % 2 == 0 else (90, 180)
        pt = lambda a, c=c, r=r: c + V(r * math.cos(math.radians(a)), r * math.sin(math.radians(a)), 0)
        edges.append(cq.Edge.makeThreePointArc(pt(a0), pt(am), pt(a0 + 180)))
    path = cq.Wire.assembleEdges(edges)
    start, tangent = path.startPoint(), path.tangentAt(0)
    radial = V(start.x - cx, start.y - cy, 0).normalized()
    prof = cq.Workplane(cq.Plane(origin=start, xDir=radial, normal=tangent)).rect(t, h)
    return prof.sweep(path, isFrenet=True).val()


# ---------------------------------------------------------------- chassis (ES)

def chassis_frame():
    rails = [box(X_REAR, min(s * RAIL_Y0, s * RAIL_Y1), RAIL_Z0, X_FRONT, max(s * RAIL_Y0, s * RAIL_Y1), RAIL_Z1) for s in (1, -1)]
    crosses = [box(x0, -RAIL_Y1, RAIL_Z0, x1, RAIL_Y1, RAIL_Z1) for x0, x1 in CROSS_X]
    return compound(rails + crosses)


def drive_wheel(side):
    y = side * Y_WHEEL
    parts = [ring_along(R_WHEEL - 60, R_WHEEL, (0, y - W_WHEEL / 2, Z_AXLE), (0, y + W_WHEEL / 2, Z_AXLE)),
             cyl(70, (0, y - 40, Z_AXLE), (0, y + 40, Z_AXLE))]
    for k in range(6):
        a = math.radians(60 * k)
        parts.append(cyl(18, (70 * math.cos(a), y, Z_AXLE + 70 * math.sin(a)),
                         ((R_WHEEL - 60) * math.cos(a), y, Z_AXLE + (R_WHEEL - 60) * math.sin(a))))
    return compound(parts)


def stub_axle(side):
    return compound([cyl(R_MUFF, (0, side * Y_MUFF0, Z_AXLE), (0, side * Y_MUFF1, Z_AXLE)),
                     cyl(R_AXLE, (0, side * Y_MUFF1, Z_AXLE), (0, side * Y_AXLE_END, Z_AXLE))])


def bearing_block(side):
    y0, y1 = sorted((side * RAIL_Y0, side * RAIL_Y1))
    return box(-BLOCK_X, y0, RAIL_Z1, BLOCK_X, y1, BLOCK_Z1)


# ---------------------------------------------------------------- spring drive and steering (FR)

def drive_spring(side):
    """Left spring coils from its inner tongue at (x -300, y 260) to the housing wall at y 500; the right spring is
    its mirror image."""
    s = SPRING
    left = spiral_strip(X_GEAR, Y_GEAR, s["z0"], s["h"], s["r0"], s["r1"], s["half_turns"], s["t"])
    return left if side > 0 else left.mirror("XZ")


def spring_housing():
    """Open-top timber box: floor, two end walls, two side walls."""
    h = HOUSING
    floor = box(h["x0"], -h["y"], h["z0"], h["x1"], h["y"], h["z0"] + h["wall"])
    ends = [box(h["x0"], -h["y"], h["z0"] + h["wall"], h["x0"] + h["wall"], h["y"], h["z1"]),
            box(h["x1"] - h["wall"], -h["y"], h["z0"] + h["wall"], h["x1"], h["y"], h["z1"])]
    sides = [box(h["x0"], s * h["y"] - (h["wall"] if s > 0 else 0), h["z0"] + h["wall"],
                 h["x1"], s * h["y"] + (0 if s > 0 else h["wall"]), h["z1"]) for s in (1, -1)]
    return compound([floor] + ends + sides)


def steering_lever():
    """Vertical pivot pin through the rear cross member, tiller forward between the crown wheels, follower bar
    between the two steering cams."""
    return compound([cyl(20, (X_PIVOT, 0, 330), (X_PIVOT, 0, Z_TILLER1)),
                     box(X_PIVOT, -20, Z_TILLER0, X_GEAR + 20, 20, Z_TILLER1),
                     box(X_GEAR - 20, -190, Z_TILLER0, X_GEAR + 20, 190, Z_TILLER1)])


def steering_wheel():
    """Rear castor: fork head on the pivot pin, two trailing arms, wheel disc and its axle pin."""
    head = cyl(35, (X_PIVOT, 0, 330), (X_PIVOT, 0, RAIL_Z0))
    arms = [prism((X_PIVOT, s * 45, 355), (X_CASTOR, s * 45, R_CASTOR), 30, 30) for s in (1, -1)]
    disc = cyl(R_CASTOR, (X_CASTOR, -20, R_CASTOR), (X_CASTOR, 20, R_CASTOR))
    pin = cyl(12, (X_CASTOR, -60, R_CASTOR), (X_CASTOR, 60, R_CASTOR))
    return compound([head] + arms + [disc, pin])


# ---------------------------------------------------------------- gear train (DE)

def crown_wheel(side):
    y = side * Y_GEAR
    return compound([ring(R_HUB, R_GEAR, Z_GEAR0, Z_GEAR1 - Z_GEAR0).translate(V(X_GEAR, y, 0)),
                     cyl(R_HUB, (X_GEAR, y, Z_HUB0), (X_GEAR, y, Z_CAM0 + 10))])


def lantern_pinion(side):
    y0, y1 = sorted((side * (Y_GEAR - 60), side * (Y_GEAR + 60)))
    parts = [cyl(R_PINION, (0, y0, Z_AXLE), (0, y0 + PINION_W, Z_AXLE)),
             cyl(R_PINION, (0, y1 - PINION_W, Z_AXLE), (0, y1, Z_AXLE))]
    for k in range(N_STAVES):
        a = math.radians(360 * k / N_STAVES)
        x, z = 60 * math.cos(a), Z_AXLE + 60 * math.sin(a)
        parts.append(cyl(8, (x, y0 + PINION_W, z), (x, y1 - PINION_W, z)))
    return compound(parts)


def ratchet_wheel(side):
    return ring(R_HUB, R_RATCHET, Z_RATCHET0, 20).translate(V(X_GEAR, side * Y_GEAR, 0))


def steering_cam(side):
    """Disc on the spring shaft with one lobe towards the centre line, where the tiller's follower bar runs."""
    y = side * Y_GEAR
    return compound([cyl(R_CAM, (X_GEAR, y, Z_CAM0), (X_GEAR, y, Z_CAM0 + 40)),
                     cyl(R_LOBE, (X_GEAR, y - side * R_CAM, Z_CAM0), (X_GEAR, y - side * R_CAM, Z_CAM0 + 40))])


def spring_shaft(side):
    y = side * Y_GEAR
    return compound([cyl(R_SHAFT, (X_GEAR, y, Z_SHAFT0), (X_GEAR, y, Z_SHAFT1)),
                     box(X_GEAR - HEAD / 2, y - HEAD / 2, Z_SHAFT1, X_GEAR + HEAD / 2, y + HEAD / 2, Z_SHAFT1 + 40)])


def output_shaft():
    return cyl(R_AXLE, (0, -(Y_MUFF0 + 40), Z_AXLE), (0, Y_MUFF0 + 40, Z_AXLE))


# ---------------------------------------------------------------- assembly kit (UK)

def fastener_kit():
    """The loose fasteners of the kit at their installed positions: four bearing block bolts through the rails,
    two axle keeper pins."""
    bolts = [cyl(6, (x, y, RAIL_Z0 - 10), (x, y, BLOCK_Z1 + 15)) for x in (-40, 40) for y in (-580, 580)]
    pins = [cyl(4, (-30, s * (Y_AXLE_END - 5), Z_AXLE), (30, s * (Y_AXLE_END - 5), Z_AXLE)) for s in (1, -1)]
    return compound(bolts + pins)


def nameplate():
    return box(-1040, -60, RAIL_Z1, -980, 60, RAIL_Z1 + 3)


def trip_counter():
    """Wheel revolution counter on the left rail with a probe towards the left wheel hub (authored modern item)."""
    return compound([box(100, 550, RAIL_Z1, 160, 610, RAIL_Z1 + 60), cyl(6, (130, 610, 505), (130, 645, 505))])


def ratchet_pawls():
    """One pawl per side, pivoting on a pin set into the top of the housing side wall, tip on the ratchet rim."""
    parts = []
    for s in (1, -1):
        y_wall = s * (HOUSING["y"] - HOUSING["wall"] / 2)
        parts.append(cyl(8, (X_GEAR, y_wall, 540), (X_GEAR, y_wall, 590)))
        y0, y1 = sorted((s * (Y_GEAR + R_RATCHET - 5), s * HOUSING["y"]))
        parts.append(box(X_GEAR - 15, y0, Z_RATCHET0, X_GEAR + 15, y1, Z_RATCHET0 + 20))
    return compound(parts)


# ---------------------------------------------------------------- parts: (CAD key, owner, label, builder)

PARTS = [
    ("es-chassis-frame", "ES", "Chassis frame (two rails, four cross members)", chassis_frame),
    ("es-drive-wheel-left", "ES", "Left drive wheel", lambda: drive_wheel(+1)),
    ("es-drive-wheel-right", "ES", "Right drive wheel", lambda: drive_wheel(-1)),
    ("es-stub-axle-left", "ES", "Left stub axle with coupling muff", lambda: stub_axle(+1)),
    ("es-stub-axle-right", "ES", "Right stub axle with coupling muff", lambda: stub_axle(-1)),
    ("es-bearing-block-left", "ES", "Left bearing block", lambda: bearing_block(+1)),
    ("es-bearing-block-right", "ES", "Right bearing block", lambda: bearing_block(-1)),
    ("fr-drive-spring-left", "FR", "Left coiled leaf spring", lambda: drive_spring(+1)),
    ("fr-drive-spring-right", "FR", "Right coiled leaf spring", lambda: drive_spring(-1)),
    ("fr-spring-housing", "FR", "Spring housing (open timber box)", spring_housing),
    ("fr-steering-lever", "FR", "Steering lever (pivot, tiller, follower bar)", steering_lever),
    ("fr-steering-wheel", "FR", "Rear steering wheel (castor)", steering_wheel),
    ("de-crown-wheel-left", "DE", "Left first drive gear (crown wheel)", lambda: crown_wheel(+1)),
    ("de-crown-wheel-right", "DE", "Right first drive gear (crown wheel)", lambda: crown_wheel(-1)),
    ("de-lantern-pinion-left", "DE", "Left lantern pinion", lambda: lantern_pinion(+1)),
    ("de-lantern-pinion-right", "DE", "Right lantern pinion", lambda: lantern_pinion(-1)),
    ("de-ratchet-wheel-left", "DE", "Left ratchet wheel", lambda: ratchet_wheel(+1)),
    ("de-ratchet-wheel-right", "DE", "Right ratchet wheel", lambda: ratchet_wheel(-1)),
    ("de-steering-cam-left", "DE", "Left steering cam", lambda: steering_cam(+1)),
    ("de-steering-cam-right", "DE", "Right steering cam", lambda: steering_cam(-1)),
    ("de-spring-shaft-left", "DE", "Left spring shaft with winding head", lambda: spring_shaft(+1)),
    ("de-spring-shaft-right", "DE", "Right spring shaft with winding head", lambda: spring_shaft(-1)),
    ("de-output-shaft", "DE", "Output shaft", output_shaft),
    ("uk-fastener-kit", "UK", "Fastener kit (bearing bolts, axle keeper pins)", fastener_kit),
    ("uk-nameplate", "UK", "Nameplate", nameplate),
    ("uk-trip-counter", "UK", "Trip counter (wheel revolution counter)", trip_counter),
    ("uk-ratchet-pawls", "UK", "Ratchet pawls, left and right", ratchet_pawls),
]
