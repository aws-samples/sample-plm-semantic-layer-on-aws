# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Difference engine of the No. 2 design (1847-49), an authored reconstruction: 192 unique part numbers, one STEP
file each, of a machine of about 7,500 BOM occurrences. Design basis, from the published description of the design:
eight columns of 31 figure wheels, the result column plus seven orders of difference, a cam-driven adding and carry
mechanism turned by a hand crank, and a printing and stereotyping output apparatus; about 3.4 m long, 2.1 m high,
0.5 m deep across the calculating section and about 5 t. Only those top-level figures are taken from the sources;
every shape and dimension here is this module's own.

Frame (mm): x along the row of columns from the output end, y towards the output apparatus (the operator's side),
z up with z = 0 on the floor under the levelling feet. The result column stands at x 450, the seventh-difference
column at x 2340 (pitch 270), the camshaft at (2730, -60), the driving train from x 2880 to the crank grip at
x 3340, the output apparatus in front of the result column between y 300 and 900. Floor to top rail 2090.
Shafts pass through hubs and bearings as overlapping solids (no boolean cuts, see geometry.py).

Interpretation choices (the design is read as a mechanism, not copied as geometry):
- Figure wheels are plain rings at pitch 50 up the column, digit 0 at z 420; the digit ring is a separate part on
  the wheel rim. Carry warning levers, carry arms and detent pawls are boxes on their own axes beside each column;
  springs are plain cylinders.
- The adding racks between adjacent columns are 31 boxes per gap on a lifting frame; the seven lifting rods and the
  eight carry trigger rods differ in length with the column they serve, so each is its own part number.
- Fourteen cams on the camshaft are short cylinders of distinct radii (70 to 135) at pitch 100; their profiles are
  attributes, not geometry.
- The output apparatus takes the result column through 31 output racks and two sets of sectors to two rows of type
  wheels (large and small face) and a row of stereotype punches over a tray carriage; type slugs are small boxes
  on the wheel rims.
- Bushes and thrust washers are bronze castings of the Spanish workshop; fastener kits are British part numbers.

Every part is placed at its reference occurrence: the result column, digit 0, gap 0, cam 0, the first type wheel.
The part keys here are the CAD keys of data/products/difference-engine.json (cad/difference-engine/<key>.stp);
the PLM part ids live in that file.
"""
import cadquery as cq

from geometry import V, box, compound, cyl

KEY = "difference-engine"
NAME = "Difference engine No. 2 design, authored reconstruction"
FRAME = ("x along the row of columns from the output end, y towards the output apparatus, z up with z = 0 on the "
         "floor; mm. Result column at x 450, pitch 270, camshaft at x 2730, crank grip to x 3340, top rail at z 2090")

# ---------------------------------------------------------------- layout

N_COLS, N_DIGITS, N_GAPS, N_CAMS = 8, 31, 7, 14
COL_X0, COL_PITCH = 450.0, 270.0
Z_DIGIT0, DIGIT_PITCH = 420.0, 50.0
Z_BED0, Z_BED1 = 150.0, 300.0            # cast bed
Z_PLATE1, Z_BASE1 = 344.0, 384.0         # column plate top (on 4 mm shims), column base top
Z_AXIS1, Z_HEAD1, Z_RAIL1 = 1990.0, 2030.0, 2090.0
CAM_X, CAM_Y = 2730.0, -60.0
ARBOR_Y, ARBOR_Z, ARBOR_X0, ARBOR_X1 = -170.0, 355.0, 340.0, 2820.0
PEDESTAL_X = [600.0, 1200.0, 1800.0, 2400.0]
PILLAR = [(260.0, -240.0), (260.0, 240.0), (2560.0, -240.0), (2560.0, 240.0)]
GEAR_X0, GEAR_X1 = 2880.0, 3100.0        # driving train case: plates at both ends, normal to the shafts
SHAFT_Z = {"crank": 825.0, "inter": 625.0, "out": ARBOR_Z}
Z_PLATEN, Z_CONTROL, X_CONTROL = 1195.0, 400.0, 300.0
TYPE_X0, TYPE_PITCH = 60.0, 15.0         # first type wheel, pitch along x
Z_TYPE_L, Z_TYPE_S, Y_TYPE = 1100.0, 900.0, 650.0


def col_x(k):
    return COL_X0 + COL_PITCH * k


def gap_x(g):
    """Mid-plane between columns g and g + 1: the adding racks and their lifting frame."""
    return col_x(g) + COL_PITCH / 2


def digit_z(d):
    return Z_DIGIT0 + DIGIT_PITCH * d


def cam_z(i):
    return 440.0 + 100.0 * i


def cam_r(i):
    return 70.0 + 5.0 * i


X0, Z0, XG, ZC = col_x(0), digit_z(0), gap_x(0), cam_z(0)   # reference occurrences


def ring_at(cx, cy, r_in, r_out, z0, h):
    return cq.Workplane("XY", origin=(cx, cy, z0)).circle(r_out).circle(r_in).extrude(h).val()


def ring_x(r_in, r_out, y, z, x0, x1):
    """Annulus with its axis along x."""
    return cq.Workplane(cq.Plane(origin=V(x0, y, z), xDir=V(0, 1, 0), normal=V(1, 0, 0))).circle(r_out).circle(r_in).extrude(x1 - x0).val()


def ring_y(r_in, r_out, x, z, y0, y1):
    """Annulus with its axis along y."""
    return cq.Workplane(cq.Plane(origin=V(x, y0, z), xDir=V(1, 0, 0), normal=V(0, 1, 0))).circle(r_out).circle(r_in).extrude(y1 - y0).val()


def hex_bolt(d, length, p, axis=(0, 0, -1)):
    """Bolt as shank plus round head, head on the surface at p, shank along `axis`."""
    a = V(*axis)
    return compound([cyl(d / 2, p, V(*p) + a * length), cyl(0.9 * d, p, V(*p) - a * 0.65 * d)])


def nut(d, p):
    return ring_at(p[0], p[1], d / 2, 0.9 * d, p[2], 0.8 * d)


def washer(d, p):
    return ring_at(p[0], p[1], 0.55 * d, d, p[2], 0.15 * d)


# ---------------------------------------------------------------- DE: figure wheel column (reference: column 0, digit 0)

def figure_wheel():
    return ring_at(X0, 0, 25, 60, Z0, 12)


def digit_ring():
    return ring_at(X0, 0, 60, 68, Z0 + 1, 10)


def spacer_ring():
    return ring_at(X0, 0, 20, 30, Z0 + 13.5, 36.5)


def carry_pin():
    return cyl(2.5, (X0 + 52, 0, Z0 + 12), (X0 + 52, 0, Z0 + 22))


def column_axis():
    return cyl(20, (X0, 0, Z_BASE1), (X0, 0, Z_AXIS1))


def column_base():
    return cyl(80, (X0, 0, Z_PLATE1), (X0, 0, Z_BASE1))


def column_head():
    return cyl(50, (X0, 0, Z_AXIS1), (X0, 0, Z_HEAD1))


def carry_lever():
    return box(X0 + 62, 52, Z0 + 1, X0 + 95, 68, Z0 + 11)


def carry_arm():
    return box(X0 + 103, 54, Z0 + 13, X0 + 111, 66, Z0 + 50)


def carry_roller():
    return cyl(4, (X0 + 66, 60, Z0 + 1), (X0 + 66, 60, Z0 + 11))


def carry_axis():
    return cyl(8, (X0 + 95, 60, 400), (X0 + 95, 60, Z_AXIS1))


def detent_pawl():
    return box(X0 - 85, -6, Z0 + 2, X0 - 69, 6, Z0 + 10)


def detent_axis():
    return cyl(6, (X0 - 85, 0, 400), (X0 - 85, 0, Z_AXIS1))


def pawl_pin():
    return cyl(2, (X0 - 72, 0, Z0 + 1), (X0 - 72, 0, Z0 + 11))


def carry_trip_lever():
    return box(X0 + 85, 68, 400, X0 + 105, 95, 420)


# ---------------------------------------------------------------- DE: adding racks (reference: gap 0, digit 0)

def rack():
    return box(XG - 5, -62, Z0 + 2, XG + 5, -50, Z0 + 42)


def rack_roller_pin():
    return cyl(3, (XG, -50, Z0 + 22), (XG, -38, Z0 + 22))


def rack_guide():
    return box(XG - 15, -70, 380, XG + 15, -40, 420)


def lifting_frame():
    return box(XG - 8, -90, 400, XG + 8, -74, 2000)


def lifting_rod(g):
    z = 1900.0 + 12.0 * g
    return cyl(6, (gap_x(g), -105, z), (2440, -105, z))


def trigger_rod(k):
    y = 95.0 + 12.0 * k
    return cyl(5, (col_x(k) + 95, y, 395), (CAM_X - 40, y, 395))


# ---------------------------------------------------------------- DE: main arbor, camshaft and cams

def main_arbor():
    return cyl(25, (ARBOR_X0, ARBOR_Y, ARBOR_Z), (ARBOR_X1, ARBOR_Y, ARBOR_Z))


def arbor_bearing():
    x = PEDESTAL_X[0]
    return box(x - 30, -205, 320, x + 30, -135, 390)


def camshaft():
    return cyl(25, (CAM_X, CAM_Y, 320), (CAM_X, CAM_Y, 2000))


def camshaft_bearing(z0):
    return ring_at(CAM_X, CAM_Y, 30, 50, z0, 40)


def bevel_pinion():
    return cyl(35, (CAM_X - 15, ARBOR_Y, ARBOR_Z), (CAM_X + 15, ARBOR_Y, ARBOR_Z))


def bevel_wheel():
    return ring_at(CAM_X, CAM_Y, 25, 75, 360, 30)


def output_drive_wheel():
    return cyl(40, (ARBOR_X0, ARBOR_Y, ARBOR_Z), (ARBOR_X0 + 30, ARBOR_Y, ARBOR_Z))


def cam(i):
    return ring_at(CAM_X, CAM_Y, 25, cam_r(i), cam_z(i), 30)


def cam_follower_lever():
    return box(2440, -68, ZC + 5, 2640, -52, ZC + 25)


def cam_roller():
    x = CAM_X - cam_r(0) - 15
    return cyl(15, (x, CAM_Y, ZC), (x, CAM_Y, ZC + 30))


def follower_axis():
    return cyl(10, (2450, CAM_Y, 400), (2450, CAM_Y, 1800))


# ---------------------------------------------------------------- FR: springs for the calculating section

def carry_spring():
    return cyl(3, (X0 + 80, 78, Z0 + 12), (X0 + 80, 78, Z0 + 37))


def detent_spring():
    return cyl(2.5, (X0 - 75, 6, Z0 + 6), (X0 - 75, 36, Z0 + 6))


def rack_spring():
    return cyl(2.5, (XG, -71, Z0 + 2), (XG, -71, Z0 + 30))


def follower_spring():
    return cyl(4, (2470, CAM_Y, ZC + 25), (2470, CAM_Y, ZC + 75))


# ---------------------------------------------------------------- FR: output apparatus (reference: digit 0, type wheel 0)

XT = TYPE_X0  # first type wheel


def output_rack():
    return box(X0 - 6, 70, Z0 + 2, X0 + 6, 320, Z0 + 14)


def rack_stop():
    return box(X0 - 6, 60, Z0 + 14, X0 + 6, 70, Z0 + 20)


def output_rack_guide():
    return box(X0 - 10, 150, Z0 + 14, X0 + 10, 170, Z0 + 18)


def output_rack_roller():
    return cyl(4, (X0 - 14, 200, Z0 + 8), (X0 - 6, 200, Z0 + 8))


def return_spring():
    return cyl(2.5, (X0, 290, Z0 + 14), (X0, 290, Z0 + 36))


def print_sector():
    return box(X0 - 4, 326, Z0 + 4, X0 + 4, 400, Z0 + 12)


def stereotype_sector():
    return box(X0 - 14, 326, Z0 + 4, X0 - 6, 400, Z0 + 12)


def sector_pin():
    return cyl(3, (X0 - 16, 332, Z0 + 8), (X0 + 8, 332, Z0 + 8))


def sector_spring():
    return cyl(2, (X0, 396, Z0 + 12), (X0, 396, Z0 + 40))


def sector_lock():
    return box(X0 + 4, 380, Z0 + 4, X0 + 10, 396, Z0 + 12)


def type_wheel(r, z):
    return cyl(r, (XT, Y_TYPE, z), (XT + 10, Y_TYPE, z))


def type_axle(z):
    return cyl(10, (-60, Y_TYPE, z), (580, Y_TYPE, z))


def wheel_spacer(z):
    return ring_x(10, 14, Y_TYPE, z, XT + 10, XT + 15)


def type_slug(r, z, w, h):
    return box(XT, Y_TYPE - w / 2, z + r, XT + 10, Y_TYPE + w / 2, z + r + h)


def wheel_pin():
    return cyl(1.5, (XT + 5, Y_TYPE - 10, Z_TYPE_L), (XT + 5, Y_TYPE + 10, Z_TYPE_L))


def hammer():
    return box(XT + 1, 700, 1090, XT + 9, 752, 1110)


def hammer_spring():
    return cyl(2, (XT + 5, 745, 1110), (XT + 5, 745, 1140))


def hammer_axle():
    return cyl(8, (-60, 760, 1100), (580, 760, 1100))


def ink_roller():
    return cyl(25, (-40, 585, 1100), (560, 585, 1100))


def ink_roller_axle():
    return cyl(6, (-60, 585, 1100), (580, 585, 1100))


def ink_trough():
    return box(-40, 540, 1020, 560, 580, 1075)


def platen():
    return cyl(50, (-40, Y_TYPE, Z_PLATEN), (560, Y_TYPE, Z_PLATEN))


def platen_axle():
    return cyl(10, (-60, Y_TYPE, Z_PLATEN), (580, Y_TYPE, Z_PLATEN))


def paper_reel():
    return cyl(80, (100, 870, 1500), (500, 870, 1500))


def reel_axle():
    return cyl(8, (-60, 870, 1500), (580, 870, 1500))


def paper_guide():
    return box(-60, 820, 1250, 580, 824, 1400)


def feed_roller():
    return cyl(20, (-60, 720, 1300), (580, 720, 1300))


def feed_ratchet():
    return cyl(35, (560, Y_TYPE, Z_PLATEN), (570, Y_TYPE, Z_PLATEN))


def feed_pawl():
    return box(560, Y_TYPE, Z_PLATEN + 35, 570, 700, Z_PLATEN + 45)


def feed_lever():
    return box(560, 690, 1100, 570, 700, Z_PLATEN + 35)


def ratchet_spring():
    return cyl(2, (565, 660, Z_PLATEN + 45), (565, 660, Z_PLATEN + 70))


def punch():
    return cyl(5, (XT + 5, 500, 560), (XT + 5, 500, 700))


def punch_spring():
    return cyl(4, (XT + 5, 500, 700), (XT + 5, 500, 740))


def punch_guide():
    return box(50, 480, 640, 530, 520, 680)


def stereotype_tray():
    return box(40, 440, 540, 540, 560, 560)


def tray_shim():
    return box(40, 440, 530, 60, 460, 540)


def tray_carriage():
    return box(30, 430, 500, 550, 570, 530)


def carriage_rail():
    return box(30, 350, 480, 50, 880, 500)


def carriage_rack():
    return box(20, 430, 500, 30, 570, 510)


def carriage_ratchet():
    return cyl(25, (10, 460, 470), (20, 460, 470))


def carriage_lever():
    return box(0, 455, 470, 10, 465, 600)


def side_frame(x0):
    return box(x0, 300, 300, x0 + 40, 900, 2000)


def cross_bar(z0):
    return box(-60, 300, z0, 580, 340, z0 + 40)


def control_shaft():
    return cyl(12, (X_CONTROL, -128, Z_CONTROL), (X_CONTROL, 880, Z_CONTROL))


def drive_pinion():
    return cyl(40, (X_CONTROL, -128, Z_CONTROL), (X_CONTROL, -98, Z_CONTROL))


def rack_lift_cam():
    return cyl(50, (X_CONTROL, 350, Z_CONTROL), (X_CONTROL, 380, Z_CONTROL))


def hammer_trip_cam():
    return cyl(40, (X_CONTROL, 760, Z_CONTROL), (X_CONTROL, 790, Z_CONTROL))


def lift_lever():
    return box(X_CONTROL - 10, 300, 450, X_CONTROL + 10, 350, 470)


def lift_rod():
    return cyl(5, (X_CONTROL, 325, 470), (X_CONTROL, 325, 1950))


# ---------------------------------------------------------------- ES: framework

def column_plate():
    x = col_x(1)
    return box(x - 130, -120, 304, x + 130, 120, Z_PLATE1)


def end_column_plate():
    return box(X0 - 130, -120, 304, X0 + 130, 160, Z_PLATE1)


def plate_shim():
    x = col_x(1)
    return box(x - 130, -120, 300, x - 90, -80, 304)


def bed(x0, x1):
    return box(x0, -300, Z_BED0, x1, 300, Z_BED1)


def pillar():
    x, y = PILLAR[0]
    return cyl(50, (x, y, Z_BED1), (x, y, Z_HEAD1))


def pillar_collar():
    x, y = PILLAR[0]
    return ring_at(x, y, 50, 80, Z_BED1, 40)


def top_rail():
    return box(220, -270, Z_HEAD1, 2600, -210, Z_RAIL1)


def head_cross_rail():
    return box(X0 - 30, -210, Z_HEAD1, X0 + 30, 210, Z_RAIL1)


def cam_tower():
    return compound([box(CAM_X - 40, 80, 320, CAM_X + 40, 200, 2000), box(CAM_X - 40, -250, 320, CAM_X + 40, -210, 2000)])


def tower_cap():
    return box(CAM_X - 60, -270, 2000, CAM_X + 60, 220, 2040)


def tower_base_plate():
    return box(CAM_X - 80, -270, Z_BED1, CAM_X + 60, 220, 320)


def drive_bracket():
    return compound([box(GEAR_X0 - 10, -410, Z_BED1, GEAR_X0 + 25, 70, 320), box(GEAR_X1 - 25, -410, Z_BED1, 3170, 70, 320)])


def arbor_pedestal():
    x = PEDESTAL_X[0]
    return box(x - 40, -215, Z_BED1, x + 40, -125, 320)


def guide_support():
    return box(XG - 20, -80, Z_PLATE1, XG + 20, -30, 380)


def counterweight():
    return box(XG - 20, -130, 400, XG + 20, -90, 480)


def output_bracket():
    return box(-150, 300, 300, -100, 900, 420)


def output_seat_plate():
    return box(-160, 340, 200, 680, 920, 300)


def output_plinth():
    return box(-160, 340, 60, 680, 920, 200)


def plinth_frame():
    return compound([box(150, -330, 60, 3200, -270, 110), box(150, 270, 60, 3200, 330, 110)])


def plinth_board():
    return box(150, -330, 110, 3200, -220, Z_BED0)


def levelling_foot():
    return cyl(30, (300, -300, 0), (300, -300, 60))


def guard_sheet():
    return box(320, -304, 340, 870, -300, 1900)


def wheel_bush():
    return ring_at(X0, 0, 20, 25, Z0, 12)


def wheel_washer():
    return ring_at(X0, 0, 20, 40, Z0 + 12, 1.5)


def lever_bush():
    return ring_at(X0 + 95, 60, 8, 11, Z0 + 1, 10)


def lever_washer():
    return ring_at(X0 + 95, 60, 8, 14, Z0 + 11, 1.5)


def rack_bush():
    return ring_y(3, 5, XG, Z0 + 22, -44, -38)


def sector_bush():
    return ring_x(3, 5, 332, Z0 + 8, X0 + 8, X0 + 12)


def arbor_bush():
    x = PEDESTAL_X[0]
    return ring_x(25, 30, ARBOR_Y, ARBOR_Z, x - 30, x + 30)


def camshaft_bush():
    return ring_at(CAM_X, CAM_Y, 25, 30, 320, 40)


def type_wheel_bush():
    return ring_x(10, 12, Y_TYPE, Z_TYPE_L, XT, XT + 10)


def hammer_bush():
    return ring_x(8, 10, 760, 1100, XT + 1, XT + 9)


# ---------------------------------------------------------------- UK: driving train, instrumentation, nameplate

def crank_shaft():
    return cyl(20, (GEAR_X0, ARBOR_Y, SHAFT_Z["crank"]), (3190, ARBOR_Y, SHAFT_Z["crank"]))


def crank_arm():
    return box(3190, ARBOR_Y - 15, 805, 3220, ARBOR_Y + 15, 1075)


def crank_grip():
    return cyl(15, (3220, ARBOR_Y, 1055), (3340, ARBOR_Y, 1055))


def crank_bearing_bracket():
    return box(3105, -220, 320, 3165, -120, 865)


def crank_bearing_bush():
    return ring_x(20, 28, ARBOR_Y, SHAFT_Z["crank"], 3105, 3165)


def spur_gear_1():
    return cyl(150, (3020, ARBOR_Y, SHAFT_Z["crank"]), (3050, ARBOR_Y, SHAFT_Z["crank"]))


def pinion_1():
    return cyl(50, (3020, ARBOR_Y, SHAFT_Z["inter"]), (3050, ARBOR_Y, SHAFT_Z["inter"]))


def intermediate_shaft():
    return cyl(18, (GEAR_X0, ARBOR_Y, SHAFT_Z["inter"]), (GEAR_X1, ARBOR_Y, SHAFT_Z["inter"]))


def spur_gear_2():
    return cyl(220, (2950, ARBOR_Y, SHAFT_Z["inter"]), (2980, ARBOR_Y, SHAFT_Z["inter"]))


def pinion_2():
    return cyl(50, (2950, ARBOR_Y, ARBOR_Z), (2980, ARBOR_Y, ARBOR_Z))


def output_shaft():
    return cyl(25, (ARBOR_X1 + 10, ARBOR_Y, ARBOR_Z), (GEAR_X1, ARBOR_Y, ARBOR_Z))


def coupling_muff():
    return ring_x(25, 45, ARBOR_Y, ARBOR_Z, ARBOR_X1 - 30, ARBOR_X1 + 50)


def thrust_collar():
    return ring_x(25, 40, ARBOR_Y, ARBOR_Z, 3060, 3075)


def intermediate_bush():
    return ring_x(18, 24, ARBOR_Y, SHAFT_Z["inter"], GEAR_X0, GEAR_X0 + 15)


def output_bush():
    return ring_x(25, 32, ARBOR_Y, ARBOR_Z, GEAR_X0, GEAR_X0 + 15)


def case_plate(x0):
    return box(x0, -400, 320, x0 + 15, 60, 1000)


def case_stay():
    return cyl(10, (GEAR_X0 + 15, -380, 340), (GEAR_X1 - 15, -380, 340))


def case_cover():
    return box(GEAR_X0, -400, 1000, GEAR_X1, 60, 1015)


def ratchet_wheel():
    return cyl(90, (3055, ARBOR_Y, SHAFT_Z["crank"]), (3067, ARBOR_Y, SHAFT_Z["crank"]))


def ratchet_pawl():
    return box(3055, ARBOR_Y - 10, 915, 3067, ARBOR_Y + 10, 965)


def counter_sensor():
    return cyl(9, (3035, ARBOR_Y, 980), (3035, ARBOR_Y, 1060))


def sensor_bracket():
    return box(3019, ARBOR_Y - 16, 1015, 3051, ARBOR_Y + 16, 1035)


def junction_box():
    return box(2940, -60, 1015, 3020, 0, 1075)


def nameplate():
    return box(CAM_X - 38, 200, 1500, CAM_X + 38, 204, 1560)


# ---------------------------------------------------------------- UK: fastener kits (one STEP per kit, at a first use)

FASTENER_KITS = [
    ("uk-kit-bolt-m6x25", "Hex bolt kit M6 x 25", lambda: hex_bolt(6, 25, (2950, -60, 1045), (0, 1, 0))),
    ("uk-kit-bolt-m8x30", "Hex bolt kit M8 x 30", lambda: hex_bolt(8, 30, (XG - 10, -55, 380))),
    ("uk-kit-bolt-m10x40", "Hex bolt kit M10 x 40", lambda: hex_bolt(10, 40, (PEDESTAL_X[0] - 20, -195, 320))),
    ("uk-kit-bolt-m12x50", "Hex bolt kit M12 x 50", lambda: hex_bolt(12, 50, (X0 + 60, 0, Z_BASE1))),
    ("uk-kit-bolt-m16x80", "Hex bolt kit M16 x 80", lambda: hex_bolt(16, 80, (X0 - 110, -100, Z_PLATE1))),
    ("uk-kit-nut-m8", "Hex nut kit M8", lambda: nut(8, (XG - 10, -55, Z_PLATE1 - 6.4))),
    ("uk-kit-nut-m12", "Hex nut kit M12", lambda: nut(12, (X0 + 60, 0, 294.4))),
    ("uk-kit-nut-m16", "Hex nut kit M16", lambda: nut(16, (X0 - 110, -100, 137.2))),
    ("uk-kit-washer-m8", "Washer kit M8", lambda: washer(8, (XG - 10, -55, Z_PLATE1 - 7.6))),
    ("uk-kit-washer-m12", "Washer kit M12", lambda: washer(12, (X0 + 60, 0, 292.6))),
    ("uk-kit-taper-pin-8x60", "Taper pin kit 8 x 60", lambda: cyl(4, (3205, ARBOR_Y - 30, SHAFT_Z["crank"]), (3205, ARBOR_Y + 30, SHAFT_Z["crank"]))),
    ("uk-kit-parallel-pin-3x20", "Parallel pin kit 3 x 20", lambda: cyl(1.5, (X0 + 80, 78, Z0 + 2), (X0 + 80, 78, Z0 + 22))),
    ("uk-kit-key-8x7x40", "Parallel key kit 8 x 7 x 40", lambda: box(3015, ARBOR_Y - 4, 841, 3055, ARBOR_Y + 4, 848)),
    ("uk-kit-key-10x8x50", "Parallel key kit 10 x 8 x 50", lambda: box(ARBOR_X1 - 25, ARBOR_Y - 5, 376, ARBOR_X1 + 25, ARBOR_Y + 5, 384)),
    ("uk-kit-csk-screw-m4x10", "Countersunk screw kit M4 x 10", lambda: hex_bolt(4, 10, (CAM_X - 30, 204, 1508), (0, -1, 0))),
    ("uk-kit-grub-screw-m6x10", "Grub screw kit M6 x 10", lambda: cyl(3, (3067, ARBOR_Y - 50, ARBOR_Z), (3067, ARBOR_Y - 38, ARBOR_Z))),
]

# ---------------------------------------------------------------- parts: (CAD key, owner, label, builder)

PARTS = [
    ("de-figure-wheel", "DE", "Figure wheel", figure_wheel),
    ("de-digit-ring", "DE", "Digit ring", digit_ring),
    ("de-spacer-ring", "DE", "Spacer ring", spacer_ring),
    ("de-carry-pin", "DE", "Carry pin", carry_pin),
    ("de-column-axis", "DE", "Column axis", column_axis),
    ("de-column-base", "DE", "Column base", column_base),
    ("de-column-head", "DE", "Column head", column_head),
    ("de-carry-lever", "DE", "Carry warning lever", carry_lever),
    ("de-carry-arm", "DE", "Carry arm", carry_arm),
    ("de-carry-roller", "DE", "Carry roller", carry_roller),
    ("de-carry-axis", "DE", "Carry axis", carry_axis),
    ("de-detent-pawl", "DE", "Detent pawl", detent_pawl),
    ("de-detent-axis", "DE", "Detent axis", detent_axis),
    ("de-pawl-pin", "DE", "Pawl pin", pawl_pin),
    ("de-rack", "DE", "Adding rack", rack),
    ("de-rack-roller-pin", "DE", "Rack roller pin", rack_roller_pin),
    ("de-rack-guide", "DE", "Rack guide", rack_guide),
    ("de-lifting-frame", "DE", "Rack lifting frame", lifting_frame),
    ("de-main-arbor", "DE", "Main arbor", main_arbor),
    ("de-arbor-bearing", "DE", "Main arbor bearing", arbor_bearing),
    ("de-camshaft", "DE", "Camshaft", camshaft),
    ("de-camshaft-bearing-lower", "DE", "Camshaft bearing, lower", lambda: camshaft_bearing(320)),
    ("de-camshaft-bearing-upper", "DE", "Camshaft bearing, upper", lambda: camshaft_bearing(1960)),
    ("de-bevel-pinion", "DE", "Bevel pinion", bevel_pinion),
    ("de-bevel-wheel", "DE", "Bevel wheel", bevel_wheel),
    ("de-cam-follower-lever", "DE", "Cam follower lever", cam_follower_lever),
    ("de-cam-roller", "DE", "Cam roller", cam_roller),
    ("de-output-drive-wheel", "DE", "Output drive wheel", output_drive_wheel),
    ("de-carry-trip-lever", "DE", "Carry trip lever", carry_trip_lever),
    ("de-follower-axis", "DE", "Cam follower axis", follower_axis),
]
PARTS += [(f"de-cam-{i + 1:02d}", "DE", f"Cam {i + 1}", lambda i=i: cam(i)) for i in range(N_CAMS)]
PARTS += [(f"de-lifting-rod-{g + 1}", "DE", f"Lifting rod {g + 1}", lambda g=g: lifting_rod(g)) for g in range(N_GAPS)]
PARTS += [(f"de-trigger-rod-{k + 1}", "DE", f"Carry trigger rod {k + 1}", lambda k=k: trigger_rod(k)) for k in range(N_COLS)]

PARTS += [
    ("fr-output-rack", "FR", "Output rack", output_rack),
    ("fr-output-rack-guide", "FR", "Output rack guide", output_rack_guide),
    ("fr-return-spring", "FR", "Rack return spring", return_spring),
    ("fr-output-rack-roller", "FR", "Output rack roller", output_rack_roller),
    ("fr-rack-stop", "FR", "Rack stop", rack_stop),
    ("fr-print-sector", "FR", "Printing sector", print_sector),
    ("fr-stereotype-sector", "FR", "Stereotyping sector", stereotype_sector),
    ("fr-sector-pin", "FR", "Sector pin", sector_pin),
    ("fr-sector-spring", "FR", "Sector spring", sector_spring),
    ("fr-sector-lock", "FR", "Sector lock", sector_lock),
    ("fr-type-wheel-large", "FR", "Type wheel, large face", lambda: type_wheel(40, Z_TYPE_L)),
    ("fr-type-wheel-small", "FR", "Type wheel, small face", lambda: type_wheel(30, Z_TYPE_S)),
    ("fr-type-slug-large", "FR", "Type slug, large face", lambda: type_slug(40, Z_TYPE_L, 6, 3)),
    ("fr-type-slug-small", "FR", "Type slug, small face", lambda: type_slug(30, Z_TYPE_S, 4, 2)),
    ("fr-type-axle-large", "FR", "Type wheel axle, large", lambda: type_axle(Z_TYPE_L)),
    ("fr-type-axle-small", "FR", "Type wheel axle, small", lambda: type_axle(Z_TYPE_S)),
    ("fr-wheel-spacer", "FR", "Type wheel spacer", lambda: wheel_spacer(Z_TYPE_L)),
    ("fr-wheel-pin", "FR", "Type wheel pin", wheel_pin),
    ("fr-hammer", "FR", "Printing hammer", hammer),
    ("fr-hammer-spring", "FR", "Hammer spring", hammer_spring),
    ("fr-hammer-axle", "FR", "Hammer axle", hammer_axle),
    ("fr-ink-roller", "FR", "Inking roller", ink_roller),
    ("fr-ink-roller-axle", "FR", "Inking roller axle", ink_roller_axle),
    ("fr-ink-trough", "FR", "Ink trough", ink_trough),
    ("fr-platen", "FR", "Platen", platen),
    ("fr-platen-axle", "FR", "Platen axle", platen_axle),
    ("fr-paper-reel", "FR", "Paper reel", paper_reel),
    ("fr-reel-axle", "FR", "Reel axle", reel_axle),
    ("fr-paper-guide", "FR", "Paper guide", paper_guide),
    ("fr-feed-roller", "FR", "Paper feed roller", feed_roller),
    ("fr-feed-ratchet", "FR", "Paper feed ratchet", feed_ratchet),
    ("fr-feed-pawl", "FR", "Paper feed pawl", feed_pawl),
    ("fr-feed-lever", "FR", "Paper feed lever", feed_lever),
    ("fr-ratchet-spring", "FR", "Ratchet spring", ratchet_spring),
    ("fr-punch", "FR", "Stereotype punch", punch),
    ("fr-punch-spring", "FR", "Punch spring", punch_spring),
    ("fr-punch-guide", "FR", "Punch guide", punch_guide),
    ("fr-stereotype-tray", "FR", "Stereotype tray", stereotype_tray),
    ("fr-tray-shim", "FR", "Tray shim", tray_shim),
    ("fr-tray-carriage", "FR", "Tray carriage", tray_carriage),
    ("fr-carriage-rail", "FR", "Carriage rail", carriage_rail),
    ("fr-carriage-rack", "FR", "Carriage advance rack", carriage_rack),
    ("fr-carriage-ratchet", "FR", "Carriage ratchet", carriage_ratchet),
    ("fr-carriage-lever", "FR", "Carriage lever", carriage_lever),
    ("fr-side-frame-left", "FR", "Side frame, left", lambda: side_frame(-100)),
    ("fr-side-frame-right", "FR", "Side frame, right", lambda: side_frame(580)),
    ("fr-cross-bar-upper", "FR", "Cross bar, upper", lambda: cross_bar(1960)),
    ("fr-cross-bar-lower", "FR", "Cross bar, lower", lambda: cross_bar(300)),
    ("fr-control-shaft", "FR", "Control shaft", control_shaft),
    ("fr-drive-pinion", "FR", "Drive pinion", drive_pinion),
    ("fr-rack-lift-cam", "FR", "Rack lifting cam", rack_lift_cam),
    ("fr-hammer-trip-cam", "FR", "Hammer trip cam", hammer_trip_cam),
    ("fr-lift-lever", "FR", "Lifting lever", lift_lever),
    ("fr-lift-rod", "FR", "Lifting rod", lift_rod),
    ("fr-carry-spring", "FR", "Carry spring", carry_spring),
    ("fr-detent-spring", "FR", "Detent spring", detent_spring),
    ("fr-rack-spring", "FR", "Adding rack spring", rack_spring),
    ("fr-follower-spring", "FR", "Cam follower spring", follower_spring),
]

PARTS += [
    ("es-column-plate", "ES", "Column plate", column_plate),
    ("es-end-column-plate", "ES", "End column plate", end_column_plate),
    ("es-plate-shim", "ES", "Plate shim", plate_shim),
    ("es-bed-left", "ES", "Bed, output end", lambda: bed(220, 1150)),
    ("es-bed-centre", "ES", "Bed, centre", lambda: bed(1150, 1990)),
    ("es-bed-right", "ES", "Bed, drive end", lambda: bed(1990, 3170)),
    ("es-pillar", "ES", "Pillar", pillar),
    ("es-pillar-collar", "ES", "Pillar collar", pillar_collar),
    ("es-top-rail", "ES", "Top rail", top_rail),
    ("es-head-cross-rail", "ES", "Column head cross rail", head_cross_rail),
    ("es-cam-tower", "ES", "Cam tower", cam_tower),
    ("es-tower-cap", "ES", "Tower cap", tower_cap),
    ("es-tower-base-plate", "ES", "Tower base plate", tower_base_plate),
    ("es-drive-bracket", "ES", "Driving train bracket", drive_bracket),
    ("es-arbor-pedestal", "ES", "Arbor pedestal", arbor_pedestal),
    ("es-guide-support", "ES", "Rack guide support", guide_support),
    ("es-counterweight", "ES", "Lifting frame counterweight", counterweight),
    ("es-output-bracket", "ES", "Output apparatus bracket", output_bracket),
    ("es-output-seat-plate", "ES", "Output apparatus seat plate", output_seat_plate),
    ("es-output-plinth", "ES", "Output apparatus plinth", output_plinth),
    ("es-plinth-frame", "ES", "Plinth frame", plinth_frame),
    ("es-plinth-board", "ES", "Plinth board", plinth_board),
    ("es-levelling-foot", "ES", "Levelling foot", levelling_foot),
    ("es-guard-sheet", "ES", "Guard sheet", guard_sheet),
    ("es-wheel-bush", "ES", "Figure wheel bush", wheel_bush),
    ("es-wheel-washer", "ES", "Figure wheel thrust washer", wheel_washer),
    ("es-lever-bush", "ES", "Carry lever bush", lever_bush),
    ("es-lever-washer", "ES", "Carry lever thrust washer", lever_washer),
    ("es-rack-bush", "ES", "Rack roller bush", rack_bush),
    ("es-sector-bush", "ES", "Sector bush", sector_bush),
    ("es-arbor-bush", "ES", "Main arbor bush", arbor_bush),
    ("es-camshaft-bush", "ES", "Camshaft bush", camshaft_bush),
    ("es-type-wheel-bush", "ES", "Type wheel bush", type_wheel_bush),
    ("es-hammer-bush", "ES", "Hammer bush", hammer_bush),
]

PARTS += [
    ("uk-crank-shaft", "UK", "Crank shaft", crank_shaft),
    ("uk-crank-arm", "UK", "Crank arm", crank_arm),
    ("uk-crank-grip", "UK", "Crank grip", crank_grip),
    ("uk-crank-bearing-bracket", "UK", "Crank bearing bracket", crank_bearing_bracket),
    ("uk-crank-bearing-bush", "UK", "Crank bearing bush", crank_bearing_bush),
    ("uk-spur-gear-1", "UK", "Spur gear, first reduction", spur_gear_1),
    ("uk-pinion-1", "UK", "Pinion, first reduction", pinion_1),
    ("uk-intermediate-shaft", "UK", "Intermediate shaft", intermediate_shaft),
    ("uk-spur-gear-2", "UK", "Spur gear, second reduction", spur_gear_2),
    ("uk-pinion-2", "UK", "Pinion, second reduction", pinion_2),
    ("uk-output-shaft", "UK", "Output shaft", output_shaft),
    ("uk-coupling-muff", "UK", "Coupling muff", coupling_muff),
    ("uk-thrust-collar", "UK", "Thrust collar", thrust_collar),
    ("uk-intermediate-bush", "UK", "Intermediate shaft bush", intermediate_bush),
    ("uk-output-bush", "UK", "Output shaft bush", output_bush),
    ("uk-case-plate-outer", "UK", "Gear case plate, outer", lambda: case_plate(GEAR_X1 - 15)),
    ("uk-case-plate-inner", "UK", "Gear case plate, inner", lambda: case_plate(GEAR_X0)),
    ("uk-case-stay", "UK", "Gear case stay", case_stay),
    ("uk-case-cover", "UK", "Gear case cover", case_cover),
    ("uk-ratchet-wheel", "UK", "Ratchet wheel", ratchet_wheel),
    ("uk-ratchet-pawl", "UK", "Ratchet pawl", ratchet_pawl),
    ("uk-counter-sensor", "UK", "Cycle counter sensor", counter_sensor),
    ("uk-sensor-bracket", "UK", "Sensor bracket", sensor_bracket),
    ("uk-junction-box", "UK", "Counter junction box", junction_box),
    ("uk-nameplate", "UK", "Nameplate", nameplate),
]
PARTS += [(key, "UK", label, make) for key, label, make in FASTENER_KITS]
