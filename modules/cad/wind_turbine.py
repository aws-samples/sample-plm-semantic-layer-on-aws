# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""5 MW reference-class wind turbine, authored: 176 unique part numbers, one STEP file each, at their reference
occurrence (blade 1, the lowest tower joint, the left-hand side).

The top-level figures are the public reference figures of the NREL 5-MW reference turbine (NREL/TP-500-38060, 2009):
rated power 5 MW, rotor diameter 126 m, hub height 90 m, rated rotor speed 12.1 rpm, gearbox ratio 97:1, rated
generator speed 1173.7 rpm, three blades of 61.5 m, tower 87.6 m tapering from 6.0 m to 3.87 m in diameter with a
27 mm to 19 mm wall. Every part below that level is this module's own design; the report carries no CAD-level detail.

Frame (mm): z up with z = 0 at the tower base flange, x downwind along the nacelle axis, y to the left seen from upwind.
The main shaft axis is horizontal at z 90000 (shaft tilt and blade precone are not modelled); the hub centre is at
x -5000 (the rotor overhang), blade 1 points straight up, so its tip is at z 153000; blades 2 and 3 are the same part
numbers at 120 and 240 degrees and are not drawn. The tower is four welded sections of a hollow cone (wall thickness from
the reference), each joint two L-flanges of one part number and one M48 bolt kit; the base flange sits on the onshore
anchor cage, or, in the offshore variant, on the transition piece and monopile below z 0. Both variants share the
rotor, drivetrain, electrical and tower parts, and both are drawn in this one frame.

Interpretation choices: the hub is a sphere with three stub rings and a rear collar, the blade root bolt circle is on
the stub ring and the pitch bearing sits inside it; a blade is three spanwise shell segments (upper and lower) over
two shear webs, a solid root cylinder and a tip cap, chord along x centred on the pitch axis; the gearbox is one
planetary stage (ring 99 teeth, sun 22) and two parallel helical stages (105/25 each) in three housings, the
intermediate shaft offset to +y; the generator is a doubly fed induction machine on the main axis behind a flexible
coupling; nacelle cabinets stand on two side platforms of the Spanish rear frame; the nacelle cover is five panels
(annular front panel) and a nose cone over the hub; pitch is hydraulic from a hub power unit through a British valve
block. Only analytic solids without booleans (see geometry.py): the root cut-outs of the nose cone, bolt holes and the
cable opening of the bedplate are not modelled, so a few parts touch or pass through their neighbours where a real
turbine has an opening.

The CAD keys here are the cadPart values of data/products/wind-turbine.json (cad/wind-turbine/<key>.stp); the PLM
part ids live in that file.
"""
import math

import cadquery as cq

from geometry import V, box, compound, cyl, ring, sector

KEY = "wind-turbine"
NAME = "5 MW reference-class wind turbine, authored"
FRAME = ("z up with z = 0 at the tower base flange, x downwind along the nacelle axis, y to the left seen from upwind; mm. "
         "Hub centre at (-5000, 0, 90000), blade 1 up to z 153000, tower 87.6 m, offshore substructure below z 0")

# ---------------------------------------------------------------- reference figures (NREL/TP-500-38060)

Z_AX = 90000.0            # hub height: main shaft axis
HUB_X = -5000.0           # rotor overhang, hub centre
TIP_R = 63000.0           # rotor radius
ROOT_R = 1500.0           # hub radius: the blade root flange
H_TOWER = 87600.0
R_BASE, R_TOP = 3000.0, 1935.0
T_BASE, T_TOP = 27.0, 19.0

# ---------------------------------------------------------------- tower

FLANGE_T = 200.0
JOINTS = [21900.0, 43800.0, 65700.0]
SECTIONS = [(FLANGE_T, JOINTS[0] - FLANGE_T), (JOINTS[0] + FLANGE_T, JOINTS[1] - FLANGE_T),
            (JOINTS[1] + FLANGE_T, JOINTS[2] - FLANGE_T), (JOINTS[2] + FLANGE_T, H_TOWER - FLANGE_T)]


def r_tower(z):
    return R_BASE - (R_BASE - R_TOP) * z / H_TOWER


def t_tower(z):
    return T_BASE - (T_BASE - T_TOP) * z / H_TOWER


def cone_shell(r_out0, r_out1, z0, z1, t):
    """Hollow truncated cone standing on z0: two conical faces and two annular caps, no boolean."""
    profile = [(r_out0 - t, z0), (r_out0, z0), (r_out1, z1), (r_out1 - t, z1)]
    return cq.Workplane("XZ").polyline(profile).close().revolve(360, (0, 0, 0), (0, 1, 0)).val()


def tower_section(i):
    z0, z1 = SECTIONS[i]
    return cone_shell(r_tower(z0), r_tower(z1), z0, z1, t_tower((z0 + z1) / 2))


def joint_flange(j):
    """L-flange inside the shell at a joint; the lower of the two is the reference occurrence."""
    z = JOINTS[j]
    return ring(r_tower(z) - 350.0, r_tower(z), z - FLANGE_T, FLANGE_T)


def bolt_kit(r, p0, p1, head_r, head_h, nut_h=0.0):
    """One bolt of a kit: shank from p0 to p1, head beyond p1, nut below p0 when nut_h > 0."""
    d = (V(*p1) - V(*p0)).normalized()
    bodies = [cyl(r, p0, p1), cyl(head_r, p1, V(*p1) + d * head_h)]
    if nut_h:
        bodies.append(cyl(head_r, V(*p0) - d * nut_h, p0))
    return compound(bodies)


def joint_bolt_circle(j):
    return r_tower(JOINTS[j]) - 180.0


def flange_bolt():
    """M48 kit at the lowest joint, downwind side of the bolt circle, axis z through both flanges."""
    r, z = joint_bolt_circle(0), JOINTS[0]
    return bolt_kit(24.0, (r, 0, z - FLANGE_T - 40.0), (r, 0, z + FLANGE_T), 36.0, 40.0, nut_h=40.0)


def anchor_bolt():
    r = 2850.0
    return bolt_kit(21.0, (r, 0, -2200.0), (r, 0, FLANGE_T), 32.0, 40.0)


def platform(r_out, z):
    """Annular sector floor with a gap on +x for the ladder."""
    return sector(500.0, r_out, 30.0, 330.0, z, 60.0)


def ladder():
    return box(1480.0, -200.0, 300.0, 1540.0, 200.0, 6300.0)


# ---------------------------------------------------------------- substructures (variants, both below z 0)

def anchor_cage():
    return ring(2650.0, 3050.0, -2200.0, 200.0)


def transition_piece():
    return cone_shell(3150.0, 3150.0, -20000.0, -FLANGE_T, 60.0)


def monopile(z0, z1):
    return cone_shell(3000.0, 3000.0, z0, z1, 60.0)


# ---------------------------------------------------------------- hub and rotor

HUB = V(HUB_X, 0.0, Z_AX)
HUB_R = 1600.0
STUB_IN, STUB_OUT = 1500.0, 1750.0
AZIMUTHS = [0.0, 120.0, 240.0]        # blade 1 up; the others share its part numbers


def blade_dir(azimuth):
    a = math.radians(azimuth)
    return V(0.0, -math.sin(a), math.cos(a))


def ring_along(r_in, r_out, p0, p1):
    """Annulus whose axis runs from p0 to p1 (both in the y-z plane directions or along x)."""
    d = V(*p1) - V(*p0)
    n = d.normalized()
    x_dir = V(1, 0, 0) if abs(n.x) < 0.5 else V(0, 1, 0)
    return cq.Workplane(cq.Plane(origin=V(*p0), xDir=x_dir, normal=n)).circle(r_out).circle(r_in).extrude(d.Length).val()


def along_blade(s, azimuth=0.0):
    """Point at radial distance s from the hub centre along a blade axis."""
    return HUB + blade_dir(azimuth) * s


def hub_casting():
    body = cq.Solid.makeSphere(HUB_R, HUB)
    stubs = [ring_along(STUB_IN, STUB_OUT, along_blade(1100.0, a), along_blade(ROOT_R, a)) for a in AZIMUTHS]
    collar = ring_along(500.0, 1100.0, (-3600.0, 0, Z_AX), (-3400.0, 0, Z_AX))
    return compound([body] + stubs + [collar])


def pitch_bearing():
    return ring_along(1250.0, STUB_IN, along_blade(1200.0), along_blade(ROOT_R))


def root_bolt():
    """M30 kit of the blade root, bolt circle r 1625 on the downwind side."""
    x = HUB_X + 1625.0
    return bolt_kit(15.0, (x, 0, Z_AX + 1100.0), (x, 0, Z_AX + ROOT_R), 24.0, 1.0, nut_h=40.0)


def hub_shaft_bolt():
    """M36 kit of the hub collar to the main shaft flange, bolt circle r 800, top."""
    return bolt_kit(18.0, (-3700.0, 0, Z_AX + 800.0), (-3400.0, 0, Z_AX + 800.0), 27.0, 30.0, nut_h=30.0)


# blade stations: radial s, chord, thickness (mm); chord along x centred on the pitch axis, thickness along y
ROOT_END = 4000.0
STATIONS = {ROOT_END: (3542.0, 3542.0), 21000.0: (4400.0, 1500.0), 42000.0: (2800.0, 700.0), 62500.0: (600.0, 120.0)}
SEGMENTS = [(ROOT_END, 21000.0), (21000.0, 42000.0), (42000.0, 62500.0)]   # pied, median, bout
WEB_X = {"front": -600.0, "rear": 600.0}


def taper(z0, z1, c0, c1):
    """Ruled loft between two rectangles (cx, cy, width, height) in the planes z0 and z1: 6 faces."""
    cx0, cy0, w0, h0 = c0
    cx1, cy1, w1, h1 = c1
    wp = cq.Workplane("XY", origin=(cx0, cy0, z0)).rect(w0, h0).workplane(offset=z1 - z0).center(cx1 - cx0, cy1 - cy0).rect(w1, h1)
    return wp.loft(ruled=True).val()


def shell(seg, side):
    """Upper (+y) or lower (-y) half-shell of a blade segment."""
    s0, s1 = SEGMENTS[seg]
    (c0, t0), (c1, t1) = STATIONS[s0], STATIONS[s1]
    sign = 1.0 if side == "upper" else -1.0
    return taper(Z_AX + s0, Z_AX + s1, (HUB_X, sign * t0 / 4, c0, t0 / 2), (HUB_X, sign * t1 / 4, c1, t1 / 2))


def web(seg, which):
    s0, s1 = SEGMENTS[seg]
    (_, t0), (_, t1) = STATIONS[s0], STATIONS[s1]
    x = HUB_X + WEB_X[which]
    return taper(Z_AX + s0, Z_AX + s1, (x, 0, 80.0, t0 - 40.0), (x, 0, 80.0, t1 - 40.0))


def leading_edge_strip(seg):
    s0, s1 = SEGMENTS[seg]
    (c0, _), (c1, _) = STATIONS[s0], STATIONS[s1]
    return taper(Z_AX + s0, Z_AX + s1, (HUB_X - c0 / 2 + 30.0, 0, 60.0, 200.0), (HUB_X - c1 / 2 + 30.0, 0, 60.0, 120.0))


def root_cylinder():
    return cyl(STUB_OUT, along_blade(ROOT_R), along_blade(ROOT_END))


def root_insert():
    """One of the 72 bonded steel inserts of the root bolt circle."""
    x = HUB_X + 1625.0
    return cyl(25.0, (x, 0, Z_AX + ROOT_R), (x, 0, Z_AX + ROOT_R + 400.0))


def root_plate():
    return box(HUB_X - 1700.0, -850.0, Z_AX + ROOT_END, HUB_X + 1700.0, 850.0, Z_AX + ROOT_END + 40.0)


def tip_cap():
    c, t = STATIONS[62500.0]
    return taper(Z_AX + 62500.0, Z_AX + TIP_R, (HUB_X, 0, c, t), (HUB_X, 0, 200.0, 60.0))


def lightning_receptor():
    return cyl(40.0, (HUB_X + 100.0, -110.0, Z_AX + 62000.0), (HUB_X + 100.0, 110.0, Z_AX + 62000.0))


def down_conductor():
    return cyl(15.0, (HUB_X + 300.0, 0, Z_AX + ROOT_END), (HUB_X + 300.0, 0, Z_AX + 62000.0))


def sensor_mount():
    return box(HUB_X - 150.0, STUB_OUT, Z_AX + 2350.0, HUB_X + 150.0, STUB_OUT + 200.0, Z_AX + 2650.0)


def vortex_strip():
    return box(HUB_X + 700.0, 650.0, Z_AX + 25000.0, HUB_X + 1000.0, 680.0, Z_AX + 25020.0)


def balancing_weight():
    return box(HUB_X - 600.0, -100.0, Z_AX + 2800.0, HUB_X - 400.0, 100.0, Z_AX + 3100.0)


# ---------------------------------------------------------------- nacelle cover and nose (FR)

COVER_X0, COVER_X1, COVER_Y, COVER_Z0, COVER_Z1, PANEL_T = -3200.0, 9800.0, 3000.0, 87800.0, 93000.0, 60.0


def cover_roof():
    return box(COVER_X0, -COVER_Y, COVER_Z1 - PANEL_T, COVER_X1, COVER_Y, COVER_Z1)


def cover_side(sign):
    y0, y1 = sorted((sign * COVER_Y, sign * (COVER_Y - PANEL_T)))
    return box(COVER_X0, y0, COVER_Z0, COVER_X1, y1, COVER_Z1 - PANEL_T)


def cover_rear():
    return box(COVER_X1 - PANEL_T, -COVER_Y + PANEL_T, COVER_Z0, COVER_X1, COVER_Y - PANEL_T, COVER_Z1 - PANEL_T)


def cover_front():
    return ring_along(1100.0, COVER_Y, (COVER_X0, 0, Z_AX), (COVER_X0 + PANEL_T, 0, Z_AX))


def roof_hatch():
    return box(4000.0, -600.0, COVER_Z1, 5200.0, 600.0, COVER_Z1 + PANEL_T)


def nose_cone():
    return cq.Solid.makeCone(1900.0, 300.0, 2400.0, V(-6800.0, 0, Z_AX), V(-1, 0, 0))


def nose_hatch():
    return box(-9300.0, -300.0, Z_AX - 300.0, -9200.0, 300.0, Z_AX + 300.0)


def vent_grille():
    return box(8000.0, COVER_Y, 91000.0, 8800.0, COVER_Y + 40.0, 91600.0)


def cover_beam():
    """Cantilever from the rear frame edge to the side panel; 16 per side."""
    return box(7000.0, 1700.0, 88500.0, 7100.0, COVER_Y - PANEL_T, 88600.0)


def roof_railing():
    return box(-3000.0, 2800.0, COVER_Z1, 9600.0, 2860.0, COVER_Z1 + 1100.0)


def cover_bolt():
    return bolt_kit(5.0, (7050.0, COVER_Y - PANEL_T - 40.0, 88550.0), (7050.0, COVER_Y, 88550.0), 8.0, 6.0)


def nose_bolt():
    return bolt_kit(6.0, (-6800.0, 0, Z_AX + 1850.0), (-6700.0, 0, Z_AX + 1850.0), 9.0, 8.0)


# ---------------------------------------------------------------- bedplate, frames and nacelle platform (ES)

def bedplate_front():
    return box(-3400.0, -1500.0, 87900.0, 1500.0, 1500.0, 88600.0)


def rear_frame():
    return box(1500.0, -1700.0, 87900.0, 9000.0, 1700.0, 88500.0)


def pedestal(x0, x1):
    return box(x0, -900.0, 88600.0, x1, 900.0, 89000.0)


def torque_arm_support(sign):
    y0, y1 = sorted((sign * 1300.0, sign * 2100.0))
    return box(1600.0, y0, 88500.0, 2400.0, y1, 88650.0)


def generator_frame():
    return box(6400.0, -1600.0, 88500.0, 8800.0, 1600.0, 88600.0)


def nacelle_platform():
    return box(1500.0, 1700.0, 88400.0, 9000.0, 2900.0, 88500.0)


def housing_bolt():
    return bolt_kit(18.0, (-2200.0, 700.0, 88600.0), (-2200.0, 700.0, 89000.0), 27.0, 25.0, nut_h=25.0)


def bedplate_bolt():
    return bolt_kit(18.0, (0, -1900.0, 87600.0), (0, -1900.0, 87900.0), 27.0, 25.0, nut_h=25.0)


def top_flange_bolt():
    return bolt_kit(21.0, (0, 1950.0, 87400.0), (0, 1950.0, 87600.0), 32.0, 35.0, nut_h=35.0)


def frame_bolt():
    return bolt_kit(15.0, (6600.0, 1300.0, 88500.0), (6600.0, 1300.0, 88600.0), 24.0, 20.0, nut_h=20.0)


def platform_bolt():
    return bolt_kit(12.0, (3150.0, 0, -3000.0), (3150.0, 0, -2900.0), 18.0, 15.0, nut_h=15.0)


# ---------------------------------------------------------------- offshore substructure (ES)

def tp_flange():
    return ring(2650.0, 3150.0, -FLANGE_T, FLANGE_T)


def tp_platform():
    return ring(3150.0, 4600.0, -3000.0, 100.0)


def boat_landing():
    return compound([cyl(200.0, (-3700.0, y, -20000.0), (-3700.0, y, -3000.0)) for y in (1200.0, -1200.0)])


def tp_ladder():
    return box(-3450.0, -400.0, -20000.0, -3350.0, 400.0, -3000.0)


def j_tube():
    return cyl(160.0, (3400.0, 0, -45000.0), (3400.0, 0, -3000.0))


def anode():
    return box(3000.0, -100.0, -30500.0, 3200.0, 100.0, -29500.0)


def door_frame():
    return box(-3050.0, -600.0, 1000.0, -2950.0, 600.0, 3200.0)


# ---------------------------------------------------------------- drivetrain (DE)

SHAFT_R = 500.0
AX = lambda x: (x, 0.0, Z_AX)                      # point on the main axis
AXB = lambda x: (x, 1300.0, Z_AX)                  # intermediate shaft axis, offset to +y


def ring_x(r_in, r_out, x0, x1, axis=AX):
    return ring_along(r_in, r_out, axis(x0), axis(x1))


def cyl_x(r, x0, x1, axis=AX):
    return cyl(r, axis(x0), axis(x1))


def main_shaft():
    return cyl_x(SHAFT_R, -3400.0, 1500.0)


def shaft_flange():
    return ring_x(SHAFT_R, 1000.0, -3400.0, -3100.0)


def main_bearing(x0):
    return ring_x(SHAFT_R, 560.0, x0 + 100.0, x0 + 700.0)


def bearing_housing(x0):
    return ring_x(560.0, 1000.0, x0, x0 + 800.0)


def shaft_seal():
    return ring_x(SHAFT_R, 560.0, -2600.0, -2500.0)


def shrink_disc():
    return ring_x(SHAFT_R, 700.0, 1200.0, 1500.0)


def lock_disc():
    return ring_x(SHAFT_R, 900.0, 700.0, 900.0)


def lock_pin():
    return cyl(60.0, (800.0, 0, 88600.0), (800.0, 0, 89250.0))


def planetary_housing():
    return cyl_x(1300.0, 1500.0, 3000.0)


def middle_housing():
    return box(3000.0, -1150.0, 88850.0, 3800.0, 1600.0, 91150.0)


def hs_housing():
    return box(3800.0, -1200.0, 88800.0, 5000.0, 2500.0, 91200.0)


def torque_arm(sign):
    y0, y1 = sorted((sign * 1300.0, sign * 2000.0))
    return box(1700.0, y0, 88750.0, 2300.0, y1, 89600.0)


def elastomer_bushing():
    return cyl(200.0, (2000.0, 1650.0, 88650.0), (2000.0, 1650.0, 88750.0))


def torque_arm_pin():
    return cyl(80.0, (2000.0, 1650.0, 88550.0), (2000.0, 1650.0, 89650.0))


def ring_gear():
    return ring_x(1000.0, 1200.0, 1700.0, 2500.0)


def planet_carrier():
    return cyl_x(950.0, 1550.0, 1700.0)


PLANET = lambda x: (x, 0.0, Z_AX + 650.0)          # planet 1 axis, straight up


def planet_gear():
    return cyl_x(330.0, 1750.0, 2450.0, PLANET)


def planet_pin():
    return cyl_x(150.0, 1700.0, 2500.0, PLANET)


def planet_bearing():
    return ring_x(150.0, 220.0, 1760.0, 1960.0, PLANET)


def sun_gear():
    return cyl_x(300.0, 1750.0, 2450.0)


def sun_shaft():
    return cyl_x(150.0, 2450.0, 3700.0)


def ls_wheel():
    return cyl_x(1050.0, 3250.0, 3550.0)


def intermediate_shaft():
    return cyl_x(120.0, 3250.0, 4800.0, AXB)


def intermediate_pinion():
    return cyl_x(250.0, 3250.0, 3550.0, AXB)


def intermediate_wheel():
    return cyl_x(1050.0, 4300.0, 4600.0, AXB)


def intermediate_bearing():
    return ring_x(120.0, 180.0, 3050.0, 3200.0, AXB)


def hs_shaft():
    return cyl_x(150.0, 4300.0, 5600.0)


def hs_pinion():
    return cyl_x(250.0, 4300.0, 4600.0)


def hs_bearing():
    return ring_x(150.0, 220.0, 4050.0, 4200.0)


def bearing_cover():
    return ring_x(160.0, 320.0, 5000.0, 5040.0)


def cover_screw():
    return bolt_kit(6.0, (5000.0, 0, Z_AX + 280.0), (5040.0, 0, Z_AX + 280.0), 9.0, 8.0)


def brake_disc():
    return cyl_x(600.0, 5100.0, 5160.0)


def brake_caliper():
    return box(5050.0, -200.0, Z_AX + 450.0, 5210.0, 200.0, Z_AX + 750.0)


def coupling():
    return cyl_x(300.0, 5600.0, 6200.0)


def oil_pump():
    return box(3000.0, -1600.0, 88600.0, 3400.0, -1200.0, 89000.0)


def oil_cooler():
    return box(1500.0, -2800.0, 88600.0, 2500.0, -2000.0, 89800.0)


def oil_filter():
    return cyl(100.0, (3600.0, -1400.0, 89000.0), (3600.0, -1400.0, 89500.0))


def oil_pipes():
    pts = [(3200.0, -1400.0, 89000.0), (3200.0, -1400.0, 89900.0), (2000.0, -2000.0, 89900.0), (2000.0, -2000.0, 89800.0)]
    return compound([cyl(30.0, a, b) for a, b in zip(pts, pts[1:])])


def gearbox_bolt():
    return bolt_kit(15.0, (3800.0, 1500.0, 90800.0), (3860.0, 1500.0, 90800.0), 24.0, 20.0)


def yaw_bearing():
    return ring(1800.0, 2050.0, 87600.0, 300.0)


YAW_DRIVE = (-1300.0, 1750.0)


def yaw_drive():
    x, y = YAW_DRIVE
    return cyl(250.0, (x, y, 87900.0), (x, y, 89600.0))


def yaw_pinion():
    x, y = YAW_DRIVE
    return cyl(130.0, (x, y, 87550.0), (x, y, 87900.0))


def yaw_brake_disc():
    return ring(2050.0, 2400.0, 87850.0, 50.0)


def yaw_brake():
    return box(-150.0, -2450.0, 87700.0, 150.0, -2150.0, 88050.0)


PITCH_CYL = (HUB_X + 800.0, 0.0)


def pitch_cylinder():
    x, y = PITCH_CYL
    return cyl(120.0, (x, y, Z_AX - 1100.0), (x, y, Z_AX + 1100.0))


def pitch_lever():
    return box(HUB_X + 700.0, -150.0, Z_AX + 1100.0, HUB_X + 1500.0, 150.0, Z_AX + 1400.0)


def hub_hpu():
    return box(-5300.0, 500.0, 89400.0, -4700.0, 1000.0, 90000.0)


def nacelle_hpu():
    return box(3000.0, -2600.0, 88600.0, 4000.0, -1800.0, 89800.0)


def accumulator():
    return cyl(120.0, (-5600.0, -300.0, 89200.0), (-4400.0, -300.0, 89200.0))


def hub_hoses():
    a = [(-5000.0, 500.0, 89700.0), (-5000.0, -600.0, 89700.0)]
    b = [(-4800.0, -800.0, 89600.0), (-4200.0, -800.0, 89600.0), (-4200.0, -120.0, 89600.0)]
    return compound([cyl(20.0, p, q) for pts in (a, b) for p, q in zip(pts, pts[1:])])


def nacelle_hoses():
    a = [(3500.0, -1800.0, 89800.0), (3500.0, -1800.0, 90500.0), (5100.0, -300.0, 90900.0)]
    b = [(3500.0, -2200.0, 88600.0), (0.0, -2200.0, 88600.0), (0.0, -2350.0, 88100.0)]
    return compound([cyl(20.0, p, q) for pts in (a, b) for p, q in zip(pts, pts[1:])])


# ---------------------------------------------------------------- electrical and control (UK)

def stator_frame():
    return ring_x(1250.0, 1400.0, 6500.0, 8700.0)


def stator_winding():
    return ring_x(950.0, 1250.0, 6700.0, 8500.0)


def rotor_core():
    return cyl_x(900.0, 6700.0, 8500.0)


def generator_shaft():
    return cyl_x(150.0, 6200.0, 9100.0)


def end_shield(x0):
    return ring_x(300.0, 1400.0, x0, x0 + 80.0)


def generator_bearing(x0):
    return ring_x(150.0, 300.0, x0, x0 + 80.0)


def slip_ring_unit():
    return cyl_x(350.0, 8850.0, 9050.0)


def generator_fan():
    return cyl_x(800.0, 8780.0, 8820.0)


def heat_exchanger():
    return box(6700.0, -900.0, 91400.0, 8500.0, 900.0, 92000.0)


def terminal_box():
    return box(7000.0, -1800.0, 89500.0, 7600.0, -1400.0, 90300.0)


def converter_cabinet():
    return box(6500.0, 1800.0, 88600.0, 8500.0, 2600.0, 90800.0)


def converter_cooling():
    return box(6500.0, -2600.0, 88600.0, 7500.0, -1800.0, 89400.0)


def transformer():
    return box(-1500.0, -1200.0, 300.0, 1500.0, 1200.0, 2800.0)


def switchgear():
    return box(1700.0, -1500.0, 300.0, 2300.0, 1500.0, 2500.0)


def turbine_controller():
    return box(5200.0, 1800.0, 88600.0, 6200.0, 2600.0, 90600.0)


def tower_controller():
    return box(-2300.0, 1300.0, 300.0, -1800.0, 1900.0, 2300.0)


def scada_server():
    return box(-2300.0, -1900.0, 300.0, -1800.0, -1300.0, 2300.0)


def pitch_controller():
    return box(-5400.0, -500.0, 88600.0, -4600.0, 500.0, 89300.0)


def valve_block():
    return box(-5200.0, -1000.0, 89400.0, -4800.0, -600.0, 89800.0)


def pitch_battery():
    return box(-5300.0, -1000.0, 88800.0, -4900.0, -600.0, 89300.0)


def yaw_cabinet():
    return box(7700.0, -2600.0, 88600.0, 8500.0, -1800.0, 90000.0)


def ups_cabinet():
    return box(2500.0, 1800.0, 88600.0, 3000.0, 2400.0, 90000.0)


def met_mast():
    return cyl(40.0, (8500.0, 0, COVER_Z1), (8500.0, 0, 95000.0))


def wind_sensor():
    return box(8400.0, -100.0, 95000.0, 8600.0, 100.0, 95300.0)


def obstruction_light():
    return cyl(150.0, (7000.0, 0, COVER_Z1), (7000.0, 0, 93400.0))


def vibration_sensor():
    return box(1000.0, 1400.0, 88600.0, 1100.0, 1500.0, 88700.0)


def shaft_encoder():
    return ring_x(SHAFT_R, 540.0, -1400.0, -1300.0)


def generator_encoder():
    return cyl_x(60.0, 9100.0, 9180.0)


def cms_sensor():
    return box(4400.0, 600.0, 91200.0, 4460.0, 660.0, 91260.0)


def strain_sensor():
    return box(HUB_X - 100.0, STUB_OUT + 200.0, Z_AX + 2400.0, HUB_X + 100.0, STUB_OUT + 300.0, Z_AX + 2600.0)


def hub_slip_ring():
    return ring_x(SHAFT_R, 700.0, -3100.0, -2900.0)


def lightning_brush():
    return ring_x(SHAFT_R, 650.0, -2900.0, -2850.0)


def yaw_encoder():
    return cyl(60.0, (1300.0, -1750.0, 87900.0), (1300.0, -1750.0, 88300.0))


CABLE_XY = (700.0, 300.0)


def tower_cable():
    x, y = CABLE_XY
    pts = [(7000.0, 1800.0, 88700.0), (7000.0, y, 88700.0), (x, y, 88700.0), (x, y, 2800.0)]
    return compound([cyl(60.0, a, b) for a, b in zip(pts, pts[1:])])


def cable_cleat():
    x, y = CABLE_XY
    return box(x - 75.0, y - 60.0, 80000.0, x + 75.0, y + 120.0, 80100.0)


def nacelle_harness():
    pts = [(5700.0, 1800.0, 89500.0), (5700.0, 1000.0, 89500.0), (5700.0, 1000.0, 92800.0), (8500.0, 1000.0, 92800.0),
           (8500.0, 0.0, COVER_Z1 - PANEL_T)]
    return compound([cyl(25.0, a, b) for a, b in zip(pts, pts[1:])])


def hub_harness():
    a = [(-5000.0, 500.0, 89300.0), (-5000.0, 1100.0, 89300.0), (-5000.0, 1100.0, 91000.0), (-5000.0, 1900.0, 91000.0),
         (-5000.0, 1900.0, Z_AX + 2350.0)]
    b = [(-5000.0, 500.0, 89300.0), (-5000.0, -800.0, 89300.0), (-5000.0, -800.0, 89400.0)]
    return compound([cyl(20.0, p, q) for pts in (a, b) for p, q in zip(pts, pts[1:])])


def mounting_bolt():
    return bolt_kit(5.0, (5250.0, 1850.0, 88540.0), (5250.0, 1850.0, 88600.0), 8.0, 10.0)


# ---------------------------------------------------------------- parts: (CAD key, owner, label, builder)

PARTS = []

# FR: blades and covers
SEG = ["root", "mid", "tip"]
for i, seg in enumerate(SEG):
    PARTS.append((f"fr-shell-upper-{seg}", "FR", f"Blade upper shell, {seg} segment", lambda i=i: shell(i, "upper")))
    PARTS.append((f"fr-shell-lower-{seg}", "FR", f"Blade lower shell, {seg} segment", lambda i=i: shell(i, "lower")))
for i, seg in enumerate(SEG[:2]):
    for which in ("front", "rear"):
        PARTS.append((f"fr-web-{which}-{seg}", "FR", f"Shear web {which}, {seg} segment", lambda i=i, w=which: web(i, w)))
PARTS += [
    ("fr-root-cylinder", "FR", "Blade root cylinder", root_cylinder),
    ("fr-root-insert", "FR", "Blade root insert (72 per blade)", root_insert),
    ("fr-root-plate", "FR", "Blade root closing plate", root_plate),
]
for i, seg in enumerate(SEG):
    PARTS.append((f"fr-le-protection-{seg}", "FR", f"Leading-edge protection, {seg} segment", lambda i=i: leading_edge_strip(i)))
PARTS += [
    ("fr-tip-cap", "FR", "Blade tip cap", tip_cap),
    ("fr-lightning-receptor", "FR", "Lightning receptor (4 per blade)", lightning_receptor),
    ("fr-down-conductor", "FR", "Lightning down conductor", down_conductor),
    ("fr-sensor-mount", "FR", "Blade root sensor mount", sensor_mount),
    ("fr-vortex-strip", "FR", "Vortex generator strip (80 per blade)", vortex_strip),
    ("fr-balancing-weight", "FR", "Balancing weight (2 per blade)", balancing_weight),
    ("fr-cover-roof", "FR", "Nacelle cover, roof panel", cover_roof),
    ("fr-cover-side-left", "FR", "Nacelle cover, left side panel", lambda: cover_side(1)),
    ("fr-cover-side-right", "FR", "Nacelle cover, right side panel", lambda: cover_side(-1)),
    ("fr-cover-rear", "FR", "Nacelle cover, rear panel", cover_rear),
    ("fr-cover-front", "FR", "Nacelle cover, annular front panel", cover_front),
    ("fr-roof-hatch", "FR", "Roof hatch", roof_hatch),
    ("fr-nose-cone", "FR", "Nose cone", nose_cone),
    ("fr-nose-hatch", "FR", "Nose hatch", nose_hatch),
    ("fr-vent-grille", "FR", "Ventilation grille (4)", vent_grille),
    ("fr-cover-beam", "FR", "Cover support beam (32)", cover_beam),
    ("fr-roof-railing", "FR", "Roof railing (2)", roof_railing),
    ("fr-cover-bolt-kit", "FR", "Cover bolt kit M10", cover_bolt),
    ("fr-nose-bolt-kit", "FR", "Nose cone bolt kit M12", nose_bolt),
]

# DE: drivetrain
PARTS += [
    ("de-main-shaft", "DE", "Main shaft", main_shaft),
    ("de-shaft-flange", "DE", "Main shaft flange", shaft_flange),
    ("de-main-bearing-front", "DE", "Main bearing, front", lambda: main_bearing(-2600.0)),
    ("de-bearing-housing-front", "DE", "Main bearing housing, front", lambda: bearing_housing(-2600.0)),
    ("de-main-bearing-rear", "DE", "Main bearing, rear", lambda: main_bearing(-200.0)),
    ("de-bearing-housing-rear", "DE", "Main bearing housing, rear", lambda: bearing_housing(-200.0)),
    ("de-shaft-seal", "DE", "Shaft seal (4)", shaft_seal),
    ("de-shrink-disc", "DE", "Shrink disc", shrink_disc),
    ("de-lock-disc", "DE", "Rotor lock disc", lock_disc),
    ("de-lock-pin", "DE", "Rotor lock pin", lock_pin),
    ("de-planetary-housing", "DE", "Gearbox housing, planetary stage", planetary_housing),
    ("de-middle-housing", "DE", "Gearbox housing, intermediate stage", middle_housing),
    ("de-hs-housing", "DE", "Gearbox housing, high-speed stage", hs_housing),
    ("de-torque-arm-left", "DE", "Torque arm, left", lambda: torque_arm(1)),
    ("de-torque-arm-right", "DE", "Torque arm, right", lambda: torque_arm(-1)),
    ("de-elastomer-bushing", "DE", "Torque arm elastomer bushing (4)", elastomer_bushing),
    ("de-torque-arm-pin", "DE", "Torque arm pin (2)", torque_arm_pin),
    ("de-ring-gear", "DE", "Ring gear, 99 teeth", ring_gear),
    ("de-planet-carrier", "DE", "Planet carrier", planet_carrier),
    ("de-planet-gear", "DE", "Planet gear (3)", planet_gear),
    ("de-planet-pin", "DE", "Planet pin (3)", planet_pin),
    ("de-planet-bearing", "DE", "Planet bearing (6)", planet_bearing),
    ("de-sun-gear", "DE", "Sun gear, 22 teeth", sun_gear),
    ("de-sun-shaft", "DE", "Sun shaft", sun_shaft),
    ("de-ls-wheel", "DE", "Low-speed stage wheel, 105 teeth", ls_wheel),
    ("de-intermediate-shaft", "DE", "Intermediate shaft", intermediate_shaft),
    ("de-intermediate-pinion", "DE", "Intermediate pinion, 25 teeth", intermediate_pinion),
    ("de-intermediate-wheel", "DE", "Intermediate stage wheel, 105 teeth", intermediate_wheel),
    ("de-intermediate-bearing", "DE", "Intermediate shaft bearing (2)", intermediate_bearing),
    ("de-hs-shaft", "DE", "High-speed shaft", hs_shaft),
    ("de-hs-pinion", "DE", "High-speed pinion, 25 teeth", hs_pinion),
    ("de-hs-bearing", "DE", "High-speed shaft bearing (2)", hs_bearing),
    ("de-bearing-cover", "DE", "Bearing cover (6)", bearing_cover),
    ("de-cover-screw-kit", "DE", "Bearing cover screw kit M12", cover_screw),
    ("de-brake-disc", "DE", "Rotor brake disc", brake_disc),
    ("de-brake-caliper", "DE", "Rotor brake caliper", brake_caliper),
    ("de-coupling", "DE", "Gearbox-generator coupling", coupling),
    ("de-oil-pump", "DE", "Lubrication oil pump", oil_pump),
    ("de-oil-cooler", "DE", "Oil cooler", oil_cooler),
    ("de-oil-filter", "DE", "Oil filter", oil_filter),
    ("de-oil-pipes", "DE", "Oil pipe set", oil_pipes),
    ("de-gearbox-bolt-kit", "DE", "Gearbox housing bolt kit M30", gearbox_bolt),
    ("de-yaw-bearing", "DE", "Yaw bearing", yaw_bearing),
    ("de-yaw-drive", "DE", "Yaw drive (4)", yaw_drive),
    ("de-yaw-pinion", "DE", "Yaw pinion (4)", yaw_pinion),
    ("de-yaw-brake-disc", "DE", "Yaw brake disc", yaw_brake_disc),
    ("de-yaw-brake", "DE", "Yaw brake caliper (8)", yaw_brake),
    ("de-pitch-bearing", "DE", "Pitch bearing (3)", pitch_bearing),
    ("de-pitch-cylinder", "DE", "Pitch cylinder (3)", pitch_cylinder),
    ("de-pitch-lever", "DE", "Pitch lever (3)", pitch_lever),
    ("de-hub-hpu", "DE", "Hydraulic power unit, hub", hub_hpu),
    ("de-nacelle-hpu", "DE", "Hydraulic power unit, nacelle", nacelle_hpu),
    ("de-accumulator", "DE", "Pitch accumulator (3)", accumulator),
    ("de-hub-hoses", "DE", "Hose set, hub", hub_hoses),
    ("de-nacelle-hoses", "DE", "Hose set, nacelle", nacelle_hoses),
]

# ES: structure, tower, substructures
PARTS += [
    ("es-hub-casting", "ES", "Hub casting", hub_casting),
    ("es-bedplate-front", "ES", "Bedplate front casting", bedplate_front),
    ("es-rear-frame", "ES", "Rear frame", rear_frame),
    ("es-pedestal-front", "ES", "Main bearing pedestal, front", lambda: pedestal(-2600.0, -1800.0)),
    ("es-pedestal-rear", "ES", "Main bearing pedestal, rear", lambda: pedestal(-200.0, 600.0)),
    ("es-torque-support-left", "ES", "Torque arm support, left", lambda: torque_arm_support(1)),
    ("es-torque-support-right", "ES", "Torque arm support, right", lambda: torque_arm_support(-1)),
    ("es-generator-frame", "ES", "Generator frame", generator_frame),
    ("es-nacelle-platform", "ES", "Nacelle side platform (2)", nacelle_platform),
]
for i in range(4):
    PARTS.append((f"es-tower-section-{i + 1}", "ES", f"Tower section {i + 1}", lambda i=i: tower_section(i)))
PARTS.append(("es-base-flange", "ES", "Tower base flange", lambda: ring(2650.0, 3050.0, 0.0, FLANGE_T)))
for j in range(3):
    PARTS.append((f"es-joint-flange-{j + 1}", "ES", f"Tower joint flange {j + 1} (2)", lambda j=j: joint_flange(j)))
PARTS += [
    ("es-top-flange", "ES", "Tower top flange", lambda: ring(1700.0, 2200.0, H_TOWER - FLANGE_T, FLANGE_T)),
    ("es-flange-bolt-kit", "ES", "Tower flange bolt kit M48", flange_bolt),
    ("es-top-flange-bolt-kit", "ES", "Top flange bolt kit M42", top_flange_bolt),
    ("es-anchor-bolt-kit", "ES", "Anchor bolt kit M42", anchor_bolt),
    ("es-hub-shaft-bolt-kit", "ES", "Hub to shaft bolt kit M36", hub_shaft_bolt),
    ("es-root-bolt-kit", "ES", "Blade root bolt kit M30", root_bolt),
    ("es-bedplate-bolt-kit", "ES", "Bedplate to yaw bearing bolt kit M36", bedplate_bolt),
    ("es-housing-bolt-kit", "ES", "Bearing housing bolt kit M36", housing_bolt),
    ("es-frame-bolt-kit", "ES", "Generator frame bolt kit M30", frame_bolt),
    ("es-anchor-cage", "ES", "Anchor cage (onshore)", anchor_cage),
    ("es-platform-lower", "ES", "Tower platform, lower (2)", lambda: platform(2440.0, 21000.0)),
    ("es-platform-upper", "ES", "Tower platform, upper (2)", lambda: platform(1910.0, 65000.0)),
    ("es-ladder", "ES", "Tower ladder segment (15)", ladder),
    ("es-door-frame", "ES", "Tower door frame", door_frame),
    ("es-transition-piece", "ES", "Transition piece (offshore)", transition_piece),
    ("es-tp-flange", "ES", "Transition piece top flange", tp_flange),
    ("es-tp-platform", "ES", "Transition piece platform", tp_platform),
    ("es-boat-landing", "ES", "Boat landing", boat_landing),
    ("es-tp-ladder", "ES", "Transition piece ladder", tp_ladder),
    ("es-j-tube", "ES", "J-tube", j_tube),
    ("es-monopile-upper", "ES", "Monopile, upper section", lambda: monopile(-32000.0, -8000.0)),
    ("es-monopile-lower", "ES", "Monopile, lower section", lambda: monopile(-56000.0, -32000.0)),
    ("es-anode", "ES", "Sacrificial anode (36)", anode),
    ("es-platform-bolt-kit", "ES", "Platform bolt kit M24", platform_bolt),
]

# UK: electrical, control, sensors
PARTS += [
    ("uk-stator-frame", "UK", "Generator stator frame", stator_frame),
    ("uk-generator-shaft", "UK", "Generator rotor shaft", generator_shaft),
    ("uk-rotor-core", "UK", "Generator rotor core", rotor_core),
    ("uk-stator-winding", "UK", "Generator stator winding", stator_winding),
    ("uk-generator-bearing-de", "UK", "Generator bearing, drive end", lambda: generator_bearing(6420.0)),
    ("uk-generator-bearing-nde", "UK", "Generator bearing, non-drive end", lambda: generator_bearing(8700.0)),
    ("uk-end-shield-de", "UK", "Generator end shield, drive end", lambda: end_shield(6420.0)),
    ("uk-end-shield-nde", "UK", "Generator end shield, non-drive end", lambda: end_shield(8700.0)),
    ("uk-slip-ring-unit", "UK", "Generator slip ring unit", slip_ring_unit),
    ("uk-generator-fan", "UK", "Generator cooling fan", generator_fan),
    ("uk-heat-exchanger", "UK", "Generator air-water heat exchanger", heat_exchanger),
    ("uk-terminal-box", "UK", "Generator terminal box", terminal_box),
    ("uk-converter-cabinet", "UK", "Power converter cabinet", converter_cabinet),
    ("uk-converter-cooling", "UK", "Converter cooling unit", converter_cooling),
    ("uk-transformer", "UK", "Medium-voltage transformer", transformer),
    ("uk-switchgear", "UK", "Medium-voltage switchgear", switchgear),
    ("uk-turbine-controller", "UK", "Turbine controller cabinet", turbine_controller),
    ("uk-tower-controller", "UK", "Tower base controller cabinet", tower_controller),
    ("uk-pitch-controller", "UK", "Pitch controller cabinet", pitch_controller),
    ("uk-valve-block", "UK", "Pitch valve block (3)", valve_block),
    ("uk-pitch-battery", "UK", "Pitch battery backup (3)", pitch_battery),
    ("uk-yaw-cabinet", "UK", "Yaw control cabinet", yaw_cabinet),
    ("uk-ups-cabinet", "UK", "UPS cabinet", ups_cabinet),
    ("uk-met-mast", "UK", "Meteorological mast", met_mast),
    ("uk-wind-sensor", "UK", "Ultrasonic wind sensor", wind_sensor),
    ("uk-obstruction-light", "UK", "Obstruction light", obstruction_light),
    ("uk-vibration-sensor", "UK", "Nacelle vibration sensor", vibration_sensor),
    ("uk-shaft-encoder", "UK", "Main shaft speed encoder", shaft_encoder),
    ("uk-generator-encoder", "UK", "Generator speed encoder", generator_encoder),
    ("uk-cms-sensor", "UK", "Gearbox condition monitoring sensor set", cms_sensor),
    ("uk-strain-sensor", "UK", "Blade root strain sensor (3)", strain_sensor),
    ("uk-hub-slip-ring", "UK", "Hub slip ring unit", hub_slip_ring),
    ("uk-lightning-brush", "UK", "Lightning brush ring", lightning_brush),
    ("uk-yaw-encoder", "UK", "Yaw position encoder", yaw_encoder),
    ("uk-tower-cable", "UK", "Tower power cable", tower_cable),
    ("uk-cable-cleat", "UK", "Cable cleat (120)", cable_cleat),
    ("uk-scada-server", "UK", "SCADA server cabinet", scada_server),
    ("uk-nacelle-harness", "UK", "Nacelle main harness", nacelle_harness),
    ("uk-hub-harness", "UK", "Hub harness", hub_harness),
    ("uk-mounting-bolt-kit", "UK", "Cabinet mounting kit M10", mounting_bolt),
]


# ---------------------------------------------------------------- purchased O-rings (DE, UK, ES)

def o_ring(inner_d, section, p0, axis):
    """O-ring envelope: an annulus of the ring's inner diameter and cross-section, one cross-section thick along axis."""
    p1 = tuple(c + section * a for c, a in zip(p0, axis))
    return ring_along(inner_d / 2, inner_d / 2 + section, p0, p1)


PARTS += [
    ("de-oring-pitch-214", "DE", "O-ring ISO 3601-1 214A at the pitch cylinder rod (6)",
     lambda: o_ring(24.99, 3.53, (PITCH_CYL[0], PITCH_CYL[1], Z_AX + 1100.0), (0, 0, 1))),
    ("de-oring-yaw-222", "DE", "O-ring ISO 3601-1 222A on the yaw brake caliper (16)",
     lambda: o_ring(37.69, 3.53, (0.0, -2300.0, 88050.0), (0, 0, 1))),
    ("uk-oring-valve-nbr", "UK", "O-ring 0.984 x 0.139 in, NBR 70, on the pitch valve block (4)",
     lambda: o_ring(24.99, 3.53, (-5000.0, -800.0, 89800.0), (0, 0, 1))),
    ("uk-oring-valve-fkm", "UK", "O-ring 0.984 x 0.139 in, FKM 75, on the pitch valve block (2)",
     lambda: o_ring(24.99, 3.53, (-4900.0, -700.0, 89800.0), (0, 0, 1))),
    ("es-oring-yaw-3769", "ES", "O-ring 37.69 x 3.53 mm at the yaw brake connection on the bedplate (16)",
     lambda: o_ring(37.69, 3.53, (0.0, -2300.0, 87700.0), (0, 0, -1))),
]
