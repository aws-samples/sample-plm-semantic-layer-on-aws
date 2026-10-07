# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Watt rotative beam engine of the 1788 type, simplified and built today as a double-acting demonstrator with a
centrifugal governor: 28 parts, one STEP file each.

Frame (mm): x along the engine axis from the boiler towards the flywheel, y across the engine with the flywheel on
the negative side, z up with z = 0 on the floor. The bed frame top is at z 300. The cylinder stands at x 600, the
beam pivots at x 3100, z 4000, the crankshaft turns at x 5600, z 2100 and the boiler lies from x -3000 to -1200.
Every moving part is drawn at mid-stroke: the piston at mid-height, the beam horizontal, the crank horizontal
towards +x.

Proportions follow the 1788 engine as a type: bore 480 mm, stroke 1220 mm, a beam of 5500 mm pivoting at its middle,
a flywheel of 3500 mm diameter, the beam at about 4 m. Interpretation choices, not in any source: the valve chest
is omitted (one steam port and one eduction port on the cylinder), the parallel motion is reduced to two link sets
whose radius rods end in the air where a column bracket would carry them, the governor stands on the bed frame at the
boiler end and its belt drive from the flywheel shaft is not drawn, the boiler has no setting or chimney, the
condenser has no cistern, and the pressure gauge and the governor speed indicator are modern replica instruments.

The part keys here are the CAD keys of data/products/steam-engine.json (cad/steam-engine/<key>.stp); the PLM part
ids live in that file.
"""
import math

import cadquery as cq

from geometry import V, box, compound, cyl

KEY = "steam-engine"
NAME = "Watt rotative beam engine, simplified (1788 type)"
FRAME = "x from the boiler towards the flywheel, y across the engine with the flywheel on the negative side, z up with z = 0 on the floor; mm"

# ---------------------------------------------------------------- stations

Z_BED = 300.0                        # bed frame top
X_CYL, R_CYL = 600.0, 300.0          # cylinder axis and outer radius (bore 480)
Z_CYL_TOP = 2200.0
Z_PISTON = 1225.0                    # piston centre at mid-stroke
Z_CROSSHEAD = 3300.0                 # piston rod head and parallel motion lower pins
X_PIVOT, Z_BEAM = 3100.0, 4000.0     # beam gudgeon axis
X_BEAM0, X_BEAM1 = 350.0, 5850.0
X_BACK = 1500.0                      # back link of the parallel motion
X_PUMP = 2200.0                      # air pump axis
X_COND = 1500.0                      # condenser axis
X_CRANK, Z_SHAFT = 5600.0, 2100.0    # crankshaft axis
CRANK_R = 610.0                      # half stroke at the crank end
X_PIN = X_CRANK + CRANK_R            # crank pin at mid-stroke
Y_FLY, R_FLY = -900.0, 1750.0        # flywheel plane and outer radius
X_BOILER0, X_BOILER1, R_BOILER, Z_BOILER = -3000.0, -1200.0, 650.0, 950.0
X_DOME = -1800.0                     # steam dome and throttle valve axis
Z_STEAM = 2150.0                     # steam pipe axis
X_GOV, Y_GOV = -300.0, -900.0        # governor spindle
Y_COLUMN = 700.0


def ring_x(r_in, r_out, x0, x1, y, z):
    """Annulus with its axis along x: a pipe flange."""
    plane = cq.Plane(origin=V(x0, y, z), xDir=V(0, 1, 0), normal=V(1, 0, 0))
    return cq.Workplane(plane).circle(r_out).circle(r_in).extrude(x1 - x0).val()


def ring_y(r_in, r_out, y0, y1, x, z):
    """Annulus with its axis along y: the flywheel rim."""
    plane = cq.Plane(origin=V(x, y0, z), xDir=V(1, 0, 0), normal=V(0, 1, 0))
    return cq.Workplane(plane).circle(r_out).circle(r_in).extrude(y1 - y0).val()


def ring_z(r_in, r_out, z0, h, x, y):
    """Annulus standing on z0 at (x, y): a base or cover flange."""
    return cq.Workplane("XY", origin=(x, y, z0)).circle(r_out).circle(r_in).extrude(h).val()


def sphere(r, p):
    return cq.Solid.makeSphere(r, V(*p))


# ---------------------------------------------------------------- power cylinder and motion (DE)

def cylinder():
    body = cyl(R_CYL, (X_CYL, 0, Z_BED), (X_CYL, 0, Z_CYL_TOP))
    base_flange = ring_z(R_CYL, 400, Z_BED, 50, X_CYL, 0)
    top_flange = ring_z(R_CYL, 360, Z_CYL_TOP - 50, 50, X_CYL, 0)
    return compound([body, base_flange, top_flange])


def piston():
    return cyl(290, (X_CYL, 0, Z_PISTON - 75), (X_CYL, 0, Z_PISTON + 75))


def piston_rod():
    return cyl(45, (X_CYL, 0, Z_PISTON + 75), (X_CYL, 0, Z_CROSSHEAD))


def beam():
    body = box(X_BEAM0, -100, Z_BEAM - 250, X_BEAM1, 100, Z_BEAM + 250)
    gudgeon = cyl(100, (X_PIVOT, -450, Z_BEAM), (X_PIVOT, 450, Z_BEAM))
    return compound([body, gudgeon])


def beam_bearing_blocks():
    """Two plummer blocks on the entablature, base below the gudgeon and cap above it."""
    blocks = []
    for side in (-1, 1):
        y0, y1 = sorted((side * 200, side * 400))
        blocks.append(box(X_PIVOT - 200, y0, 3800, X_PIVOT + 200, y1, 3900))
        blocks.append(box(X_PIVOT - 150, y0, 4100, X_PIVOT + 150, y1, 4200))
    return compound(blocks)


def parallel_motion():
    """Two link sets at y +/-150 straddling the beam: main link, parallel bar, back link and radius rod, joined by
    a crosshead pin and a back pin."""
    links = []
    for side in (-1, 1):
        y = side * 150
        links += [cyl(25, (X_CYL, y, Z_CROSSHEAD), (X_CYL, y, Z_BEAM)),
                  cyl(25, (X_CYL, y, Z_CROSSHEAD), (X_BACK, y, Z_CROSSHEAD)),
                  cyl(25, (X_BACK, y, Z_CROSSHEAD), (X_BACK, y, Z_BEAM)),
                  cyl(25, (X_BACK, y, Z_CROSSHEAD), (2500, y, Z_CROSSHEAD))]
    links += [cyl(30, (X_CYL, -180, Z_CROSSHEAD), (X_CYL, 180, Z_CROSSHEAD)),
              cyl(30, (X_BACK, -180, Z_CROSSHEAD), (X_BACK, 180, Z_CROSSHEAD))]
    return compound(links)


def connecting_rod():
    return cyl(50, (X_CRANK, 0, Z_BEAM), (X_PIN, 0, Z_SHAFT))


def crank():
    web = box(X_CRANK - 100, 50, Z_SHAFT - 100, X_PIN + 100, 150, Z_SHAFT + 100)
    pin = cyl(50, (X_PIN, -60, Z_SHAFT), (X_PIN, 50, Z_SHAFT))
    return compound([web, pin])


# ---------------------------------------------------------------- steam side (ES)

def boiler():
    barrel = cyl(R_BOILER, (X_BOILER0, 0, Z_BOILER), (X_BOILER1, 0, Z_BOILER))
    dome = cyl(250, (X_DOME, 0, 1550), (X_DOME, 0, 2000))
    return compound([barrel, dome])


def throttle_valve():
    body = cyl(150, (X_DOME, 0, 2000), (X_DOME, 0, 2300))
    outlet = cyl(90, (X_DOME, 0, Z_STEAM), (X_DOME + 150, 0, Z_STEAM))
    spindle = cyl(15, (X_DOME, 0, 2300), (X_DOME, 0, 2400))
    lever = cyl(12, (X_DOME, 0, 2300), (X_DOME, -150, 2300))
    return compound([body, outlet, spindle, lever])


def steam_pipe():
    x0, x1 = X_DOME + 150, X_CYL - R_CYL
    pipe = cyl(90, (x0, 0, Z_STEAM), (x1, 0, Z_STEAM))
    return compound([pipe, ring_x(90, 160, x0, x0 + 25, 0, Z_STEAM), ring_x(90, 160, x1 - 25, x1, 0, Z_STEAM)])


def eduction_pipe():
    x0, x1 = X_CYL + R_CYL, X_COND - 250
    pipe = cyl(80, (x0, 0, 500), (x1, 0, 500))
    return compound([pipe, ring_x(80, 140, x0, x0 + 25, 0, 500), ring_x(80, 140, x1 - 25, x1, 0, 500)])


def condenser():
    body = cyl(250, (X_COND, 0, Z_BED), (X_COND, 0, 1300))
    return compound([body, ring_z(250, 320, Z_BED, 40, X_COND, 0)])


def air_pump():
    body = cyl(200, (X_PUMP, 0, Z_BED), (X_PUMP, 0, 1500))
    rod = cyl(30, (X_PUMP, 0, 1500), (X_PUMP, 0, Z_BEAM))
    return compound([body, rod])


# ---------------------------------------------------------------- regulation (UK)

def flywheel():
    rim = ring_y(R_FLY - 150, R_FLY, Y_FLY - 125, Y_FLY + 125, X_CRANK, Z_SHAFT)
    hub = cyl(250, (X_CRANK, Y_FLY - 100, Z_SHAFT), (X_CRANK, Y_FLY + 100, Z_SHAFT))
    spokes = []
    for k in range(8):
        a = math.radians(45 * k)
        spokes.append(cyl(45, (X_CRANK + 250 * math.cos(a), Y_FLY, Z_SHAFT + 250 * math.sin(a)),
                          (X_CRANK + (R_FLY - 150) * math.cos(a), Y_FLY, Z_SHAFT + (R_FLY - 150) * math.sin(a))))
    return compound([rim, hub] + spokes)


def flywheel_shaft():
    return cyl(120, (X_CRANK, -1600, Z_SHAFT), (X_CRANK, 150, Z_SHAFT))


def governor():
    """Spindle, drive pulley, sleeve, two arms with balls, sleeve lever and the link rod to the throttle lever."""
    plate = box(X_GOV - 150, Y_GOV - 100, Z_BED, X_GOV + 150, Y_GOV + 100, Z_BED + 30)
    spindle = cyl(20, (X_GOV, Y_GOV, Z_BED + 30), (X_GOV, Y_GOV, 2400))
    pulley = cyl(120, (X_GOV, Y_GOV, 600), (X_GOV, Y_GOV, 650))
    sleeve = cyl(40, (X_GOV, Y_GOV, 1700), (X_GOV, Y_GOV, 1800))
    arms = [cyl(8, (X_GOV, Y_GOV, 2350), (X_GOV + side * 180, Y_GOV, 1950)) for side in (-1, 1)]
    balls = [sphere(60, (X_GOV + side * 180, Y_GOV, 1950)) for side in (-1, 1)]
    lever = cyl(12, (X_GOV, Y_GOV, 1750), (X_GOV, -150, 1750))
    link = cyl(12, (X_GOV, -150, 1750), (X_DOME, -150, 2300))
    return compound([plate, spindle, pulley, sleeve, lever, link] + arms + balls)


def pressure_gauge():
    stem = cyl(10, (-1300, 0, 1600), (-1300, 0, 1900))
    dial = cyl(80, (-1300, -20, 1980), (-1300, 20, 1980))
    return compound([stem, dial])


def speed_indicator():
    post = cyl(15, (X_GOV + 100, Y_GOV, Z_BED + 30), (X_GOV + 100, Y_GOV, 1300))
    display = box(X_GOV + 50, Y_GOV - 60, 1300, X_GOV + 150, Y_GOV + 60, 1500)
    return compound([post, display])


# ---------------------------------------------------------------- frame and fitting (FR)

def bed_frame():
    return box(-600, -1700, 0, 7000, 1100, Z_BED)


def column(side):
    return cyl(150, (X_PIVOT, side * Y_COLUMN, Z_BED), (X_PIVOT, side * Y_COLUMN, 3600))


def entablature():
    return box(X_PIVOT - 500, -900, 3600, X_PIVOT + 500, 900, 3800)


def shaft_pedestals():
    """Two plummer blocks for the flywheel shaft, outboard of the flywheel and between flywheel and crank."""
    blocks = []
    for y0, y1 in ((-1550, -1300), (-600, -350)):
        blocks.append(box(X_CRANK - 250, y0, Z_BED, X_CRANK + 250, y1, Z_SHAFT - 120))
        blocks.append(box(X_CRANK - 150, y0, Z_SHAFT + 120, X_CRANK + 150, y1, Z_SHAFT + 220))
    return compound(blocks)


def nameplate():
    return box(X_PIVOT - 250, -910, 3650, X_PIVOT + 250, -900, 3750)


# ---------------------------------------------------------------- parts: (CAD key, owner, label, builder)

PARTS = [
    ("de-cylinder", "DE", "Steam cylinder with base and top flanges", cylinder),
    ("de-piston", "DE", "Piston", piston),
    ("de-piston-rod", "DE", "Piston rod", piston_rod),
    ("de-beam", "DE", "Beam with gudgeon", beam),
    ("de-beam-bearing-blocks", "DE", "Beam bearing blocks (two plummer blocks)", beam_bearing_blocks),
    ("de-parallel-motion", "DE", "Parallel motion linkage", parallel_motion),
    ("de-connecting-rod", "DE", "Connecting rod", connecting_rod),
    ("de-crank", "DE", "Crank web and crank pin", crank),
    ("es-boiler", "ES", "Boiler barrel with steam dome", boiler),
    ("es-throttle-valve", "ES", "Throttle valve with spindle and lever", throttle_valve),
    ("es-steam-pipe", "ES", "Steam pipe with flanges", steam_pipe),
    ("es-eduction-pipe", "ES", "Eduction pipe with flanges", eduction_pipe),
    ("es-condenser", "ES", "Condenser casting", condenser),
    ("es-air-pump", "ES", "Air pump with pump rod", air_pump),
    ("uk-flywheel", "UK", "Flywheel (rim, hub, eight spokes)", flywheel),
    ("uk-flywheel-shaft", "UK", "Flywheel shaft", flywheel_shaft),
    ("uk-governor", "UK", "Centrifugal governor with throttle linkage", governor),
    ("uk-pressure-gauge", "UK", "Pressure gauge on its siphon stem", pressure_gauge),
    ("uk-speed-indicator", "UK", "Governor speed indicator", speed_indicator),
    ("fr-bed-frame", "FR", "Bed frame", bed_frame),
    ("fr-column-left", "FR", "Left column (positive y)", lambda: column(+1)),
    ("fr-column-right", "FR", "Right column (negative y)", lambda: column(-1)),
    ("fr-entablature", "FR", "Entablature", entablature),
    ("fr-shaft-pedestals", "FR", "Flywheel shaft pedestals (two plummer blocks)", shaft_pedestals),
    ("fr-nameplate", "FR", "Nameplate", nameplate),
]


# ---------------------------------------------------------------- purchased O-rings (DE, ES, UK)

def o_ring(inner_d, section, z0, x, y):
    """O-ring envelope: an annulus of the ring's inner diameter and cross-section, one cross-section thick."""
    return ring_z(inner_d / 2, inner_d / 2 + section, z0, section, x, y)


PARTS += [
    ("de-oring-332", "DE", "O-ring ISO 3601-1 332A on the cylinder cover", lambda: o_ring(50.17, 5.33, Z_CYL_TOP, X_CYL, 0)),
    ("es-oring-5017", "ES", "O-ring 50.17 x 5.33 mm on the throttle valve spindle", lambda: o_ring(50.17, 5.33, 2300, X_DOME, 0)),
    ("uk-oring-1975", "UK", "O-ring 1.975 x 0.210 in on the governor spindle gland", lambda: o_ring(50.17, 5.33, 1600, X_GOV, Y_GOV)),
]
