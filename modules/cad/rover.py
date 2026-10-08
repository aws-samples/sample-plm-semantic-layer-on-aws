# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Six-wheel rocker-bogie rover, an authored demonstrator built mostly from purchased parts: 110 unique part
numbers, one STEP file each, placed at the part's reference occurrence (front-left wheel station, left side,
lowest bearing). The BOM of data/products/rover.json gives the quantities; the viewer shows one instance per
part number.

Frame (mm): x forward, y to the left, z up with z = 0 on the ground under the wheel contact points. Wheel
stations at x 330 (front, steered), -30 (middle, fixed) and -330 (rear, steered), wheel centre planes at
y +/-340, axles at z 100, wheel diameter 200. Suspension tubes (25 mm) lie in the planes y +/-240: each rocker
pivots on the body side at (0, +/-240, 320) and carries the front steering head and, at (-180, +/-240, 230), the
bogie that carries the middle drive motor and the rear steering head. The differential bar at z 420 over x -80
links the two rockers through vertical rods. The sheet-metal body is 560 x 400 from z 220 to 380 with a 3 mm
lid; camera mast top at z 620, camera housing to z 660.

Interpretation choices (the layout is this module's own): six identical drive gearmotors on the wheel axis,
each inside a composite wheel's hub; four identical steering drives (gearmotor, coupler, column in a two-bearing
housing) on the steering heads of the rocker and bogie tube ends; left and right rockers, bogies, knuckles,
side panels and middle motor mounts are mirror images and distinct part numbers; purchased items are simple
envelope solids (a motor is a cylinder with a shaft, a board is a thin box, a screw is two cylinders); no
fastener hole, thread or fillet is modelled (analytic solids without booleans, see geometry.py). The structure
(rocker-bogie, six drive gearmotors, four steering assemblies, differential, sheet-metal body, single-board
computer, motor controllers, battery pack, camera) follows the public open-source rover pattern cited in
data/products/rover.json; no dimension, CAD, BOM row, vendor or part number was taken from it.

The part keys here are the CAD keys of data/products/rover.json (cad/rover/<key>.stp); the PLM part ids live in
that file.
"""
import math

import cadquery as cq

from geometry import V, box, compound, cyl

KEY = "rover"
NAME = "Six-wheel rocker-bogie rover, authored"
FRAME = ("x forward, y to the left, z up with z = 0 on the ground under the wheel contact points; mm. Wheel stations "
         "at x 330, -30 and -330, wheel centre planes at y +/-340, axles at z 100, suspension tubes in the planes "
         "y +/-240, body 560 x 400 from z 220 to 380, camera housing top at z 660")

# ---------------------------------------------------------------- stations and envelope

R_WHEEL, W_WHEEL, Y_WHEEL = 100.0, 60.0, 340.0      # wheel radius, width, centre plane (+/-y)
Z_AXLE = R_WHEEL
X_FRONT, X_MID, X_REAR = 330.0, -30.0, -330.0       # wheel stations
Y_SUSP, R_TUBE = 240.0, 12.5                        # suspension tube plane and radius
X_ROCKER, Z_ROCKER = 0.0, 320.0                     # rocker pivot on the body side
X_BOGIE, Z_BOGIE = -180.0, 230.0                    # bogie pivot on the rocker
Z_HEAD0, Z_HEAD1, R_HEAD = 195.0, 240.0, 20.0       # steering head housing (ES cast fitting)
Z_ARM_END, X_ARM_IN = 226.0, 8.0                    # tube ends 8 mm short of the head axis, at z 226
Z_MID_ARM = 165.0                                   # bogie front arm end on the middle motor mount
X_DIFF, Z_BAR = -80.0, 420.0                        # differential bar
Z_LUG = 305.0                                       # rod end on the rocker lug
BODY_X, BODY_Y, Z_FLOOR, Z_TOP = 280.0, 200.0, 220.0, 380.0
T = 3.0                                             # sheet thickness
LID_X = 262.0                                       # lid half-length (the cross-member tops stay exposed)
X_MAST, Z_MAST1 = 271.0, 620.0
X_ESTOP, Y_ESTOP = -271.0, 110.0
Y_ANT = -110.0

# ---------------------------------------------------------------- drive and wheel (along y, axis at z 100)

R_MOTOR, Y_MOTOR0, Y_MOTOR1 = 17.0, 246.0, 306.0    # drive gearmotor body
R_SHAFT, Y_SHAFT1 = 4.0, 345.0                      # output shaft into the hub
R_HUB, Y_HUB0, Y_HUB1 = 25.0, 305.0, 345.0
R_FLANGE, Y_FLANGE0 = 45.0, 331.0                   # hub flange against the rim web
R_RIM, Y_WEB0, Y_WEB1 = 85.0, 335.0, 342.0          # composite rim web
Y_TIRE0, Y_TIRE1 = Y_WHEEL - W_WHEEL / 2, Y_WHEEL + W_WHEEL / 2

# ---------------------------------------------------------------- steering drive (along z on the head axis)

Z_PLATE0, Z_PLATE1 = 240.0, 246.0                   # DE base plate on the ES head
Z_HOUS1 = 288.0                                     # bearing housing top
Z_FL0, Z_FL1 = 288.0, 294.0                         # steering motor flange
Z_SMOTOR1 = 354.0                                   # steering gearmotor top
R_SMOTOR, R_COL = 16.0, 6.0
Z_COL0, Z_COL1 = 115.0, 272.0                       # steering column
Z_BAR0, Z_BAR1 = 83.0, 193.0                        # knuckle vertical bar
Y_BAR0, Y_BAR1 = 222.0, 246.0
Z_CRADLE0, Z_CRADLE1 = 75.0, 83.0                   # knuckle motor cradle under the motor
X_CRADLE = 34.0
Y_TAB0, Y_TAB1, X_TAB0, X_TAB1 = 262.0, 268.0, 22.0, 32.0   # fender bracket tab

# ---------------------------------------------------------------- electronics (inside the body)

Z_PLATE_E = 265.0                                   # electronics plate top
STANDOFF = 6.35                                     # 0.25 in nylon standoff under the boards
Z_BOARD0 = Z_PLATE_E + STANDOFF                     # 271.35
Z_BOARD1 = Z_BOARD0 + 15.0
Z_SBC0 = Z_PLATE_E + 12.7                           # 0.5 in standoffs under the single-board computer
Z_SBC1 = Z_SBC0 + 1.6


def ring_along(r_in, r_out, p0, p1):
    """Annulus between r_in and r_out whose axis runs from p0 to p1 (4 faces)."""
    d = V(*p1) - V(*p0)
    n = d.normalized()
    x_dir = V(0, 0, 1) if abs(n.z) < 0.5 else V(1, 0, 0)
    return cq.Workplane(cq.Plane(origin=V(*p0), xDir=x_dir, normal=n)).circle(r_out).circle(r_in).extrude(d.Length).val()


def ring_z(r_in, r_out, x, y, z0, z1):
    return ring_along(r_in, r_out, (x, y, z0), (x, y, z1))


def arch_y(cx, cz, r_in, r_out, a0, a1, y0, y1):
    """Annular sector about an axis along y through (cx, cz), angles in degrees in the xz plane (6 faces)."""
    am = math.radians((a0 + a1) / 2)
    p = lambda r, a: (cx + r * math.cos(math.radians(a)), cz + r * math.sin(math.radians(a)))
    # Plane x = world x, plane y = world z (normal -y), extruded from y1 back to y0.
    wp = (cq.Workplane(cq.Plane(origin=V(0, y1, 0), xDir=V(1, 0, 0), normal=V(0, -1, 0)))
          .moveTo(*p(r_in, a0)).lineTo(*p(r_out, a0))
          .threePointArc((cx + r_out * math.cos(am), cz + r_out * math.sin(am)), p(r_out, a1))
          .lineTo(*p(r_in, a1))
          .threePointArc((cx + r_in * math.cos(am), cz + r_in * math.sin(am)), p(r_in, a0)).close())
    return wp.extrude(y1 - y0).val()


def mirrored(make):
    """Right-hand part: the left-hand part mirrored in the plane y = 0."""
    return lambda: make().mirror("XZ")


def screw(r, head_r, p0, p1, head_len):
    """Shank from p0 to p1, head continuing beyond p1 (two cylinders, 6 faces)."""
    d = (V(*p1) - V(*p0)).normalized()
    return compound([cyl(r, p0, p1), cyl(head_r, p1, V(*p1) + d * head_len)])


def nut(r, p0, p1):
    return cyl(r, p0, p1)


# ---------------------------------------------------------------- ES suspension

def tube(p0, p1):
    return cyl(R_TUBE, p0, p1)


def rocker():
    """Left rocker weldment: front arm to the front steering head, rear arm to the bogie pivot, pivot hub,
    differential lug."""
    pivot = (X_ROCKER, Y_SUSP, Z_ROCKER)
    return compound([tube(pivot, (X_FRONT - X_ARM_IN, Y_SUSP, Z_ARM_END)),
                     tube(pivot, (X_BOGIE, Y_SUSP, Z_BOGIE)),
                     ring_along(6.0, 12.0, (X_ROCKER, 226.0, Z_ROCKER), (X_ROCKER, 254.0, Z_ROCKER)),
                     box(X_DIFF - 6, 234.0, 285.0, X_DIFF + 6, 246.0, Z_LUG)])


def bogie():
    """Left bogie weldment: front arm to the middle motor mount, rear arm to the rear steering head, pivot hub."""
    pivot = (X_BOGIE, Y_SUSP, Z_BOGIE)
    return compound([tube(pivot, (X_MID, Y_SUSP, Z_MID_ARM)),
                     tube(pivot, (X_REAR + X_ARM_IN, Y_SUSP, Z_ARM_END)),
                     ring_along(6.0, 12.0, (X_BOGIE, 226.0, Z_BOGIE), (X_BOGIE, 254.0, Z_BOGIE))])


def differential_bar():
    return cyl(7.0, (X_DIFF, -250.0, Z_BAR), (X_DIFF, 250.0, Z_BAR))


def differential_bracket():
    """Beam across the lid with the centre post the bar pivots in."""
    return compound([box(X_DIFF - 12, -BODY_Y, 383.0, X_DIFF + 12, BODY_Y, 398.0),
                     box(X_DIFF - 12, -8.0, 398.0, X_DIFF + 12, 8.0, 432.0)])


def differential_link():
    return cyl(4.0, (X_DIFF, Y_SUSP, Z_LUG + 8), (X_DIFF, Y_SUSP, Z_BAR - 8))


def rocker_pivot_housing():
    """Cast boss on the body side wall: flange on the panel, boss over the rocker hub."""
    return compound([box(-30.0, BODY_Y, 290.0, 30.0, BODY_Y + 4, 350.0),
                     ring_along(16.0, 22.0, (X_ROCKER, BODY_Y + 4, Z_ROCKER), (X_ROCKER, 254.0, Z_ROCKER))])


def pivot_bolt():
    """M12 x 80 through the side panel, the housing and the rocker hub."""
    return screw(6.0, 9.5, (X_ROCKER, 190.0, Z_ROCKER), (X_ROCKER, 262.0, Z_ROCKER), 8.0)


def bogie_pivot_housing():
    return ring_along(16.0, 20.0, (X_BOGIE, 226.0, Z_BOGIE), (X_BOGIE, 254.0, Z_BOGIE))


def pivot_spacer():
    return ring_along(6.0, 9.0, (X_ROCKER, 256.0, Z_ROCKER), (X_ROCKER, 262.0, Z_ROCKER))


def middle_motor_mount():
    """Left mount at the bogie front arm end: plate the gearmotor face bolts to, bridge under the motor carrying
    the fender bracket, clamp ring round the motor body."""
    return compound([box(X_MID - 20, 234.0, Z_BAR0, X_MID + 20, Y_MOTOR0, Z_MID_ARM),
                     box(X_MID - X_CRADLE, Y_MOTOR0, Z_BAR0, X_MID + X_CRADLE, Y_TAB1, 95.0),
                     ring_along(R_MOTOR, 22.0, (X_MID, 262.0, Z_AXLE), (X_MID, 274.0, Z_AXLE))])


def steering_head():
    return cyl(R_HEAD, (X_FRONT, Y_SUSP, Z_HEAD0), (X_FRONT, Y_SUSP, Z_HEAD1))


def rod_end():
    return cyl(8.0, (X_DIFF, Y_SUSP - 7, Z_LUG), (X_DIFF, Y_SUSP + 7, Z_LUG))


# ---------------------------------------------------------------- ES body

def floor_pan():
    return box(-BODY_X, -BODY_Y, Z_FLOOR, BODY_X, BODY_Y, Z_FLOOR + T)


def side_panel():
    return box(-BODY_X, BODY_Y - T, Z_FLOOR + T, BODY_X, BODY_Y, Z_TOP - 1.0)


def cross_member(front):
    x0, x1 = (BODY_X - 30, BODY_X) if front else (-BODY_X, -BODY_X + 30)
    return box(x0, -(BODY_Y - T), 363.0, x1, BODY_Y - T, Z_TOP)


def corner_angle():
    """Front-left: one leg on the front panel's inner face, one on the side panel's inner face."""
    return compound([box(BODY_X - T, 177.0, Z_FLOOR + T, BODY_X, BODY_Y - T, 363.0),
                     box(BODY_X - 20, BODY_Y - 2 * T, Z_FLOOR + T, BODY_X, BODY_Y - T, 363.0)])


def mast_base():
    return box(X_MAST - 8, -14.0, Z_TOP, X_MAST + 8, 14.0, 388.0)


def mast():
    return cyl(7.0, (X_MAST, 0.0, 388.0), (X_MAST, 0.0, Z_MAST1))


def electronics_rail():
    return box(-40.0, 165.0, Z_FLOOR + T, 260.0, 175.0, 262.0)


def estop_bracket():
    return box(X_ESTOP - 8, Y_ESTOP - 15, Z_TOP, X_ESTOP + 8, Y_ESTOP + 15, 384.0)


def hub_insert():
    """Cast aluminium insert bonded into the composite rim web round the hub (draft part)."""
    return ring_along(R_HUB, 40.0, (X_FRONT, Y_WEB0, Z_AXLE), (X_FRONT, Y_WEB1, Z_AXLE))


# ---------------------------------------------------------------- ES purchased items

def es_m3_screw():
    return screw(1.5, 2.75, (X_ESTOP - 4, Y_ESTOP - 10, 374.0), (X_ESTOP - 4, Y_ESTOP - 10, 384.0), 3.0)


def es_m6_bolt():
    return screw(3.0, 5.0, (X_DIFF - 22, 0.0, Z_BAR), (X_DIFF + 23, 0.0, Z_BAR), 4.0)


def es_m12_nut():
    return nut(10.0, (X_ROCKER, 180.0, Z_ROCKER), (X_ROCKER, 190.0, Z_ROCKER))


def es_m4_screw():
    return screw(2.0, 3.5, (X_MID, 246.0, 112.0), (X_MID, 230.0, 112.0), 4.0)


def es_rivet():
    return screw(2.0, 3.5, (-20.0, 170.0, 231.0), (-20.0, 170.0, Z_FLOOR), 3.0)


def es_m12_washer():
    return ring_along(6.5, 12.0, (X_ROCKER, 254.0, Z_ROCKER), (X_ROCKER, 256.0, Z_ROCKER))


def bronze_bushing():
    return ring_along(12.0, 16.0, (X_ROCKER, 226.0, Z_ROCKER), (X_ROCKER, 254.0, Z_ROCKER))


def es_m4_nut():
    return nut(3.5, (X_DIFF - 5, 190.0, 377.0), (X_DIFF - 5, 190.0, Z_TOP))


def grease_nipple():
    return cyl(3.0, (X_ROCKER, 229.0, 342.0), (X_ROCKER, 229.0, 350.0))


# ---------------------------------------------------------------- DE drive and steering (front-left station)

def drive_gearmotor():
    return compound([cyl(R_MOTOR, (X_FRONT, Y_MOTOR0, Z_AXLE), (X_FRONT, Y_MOTOR1, Z_AXLE)),
                     cyl(R_SHAFT, (X_FRONT, Y_MOTOR1, Z_AXLE), (X_FRONT, Y_SHAFT1, Z_AXLE))])


def steering_gearmotor():
    return compound([cyl(R_SMOTOR, (X_FRONT, Y_SUSP, Z_FL1), (X_FRONT, Y_SUSP, Z_SMOTOR1)),
                     cyl(3.0, (X_FRONT, Y_SUSP, 284.0), (X_FRONT, Y_SUSP, Z_FL1))])


def knuckle():
    """Left steering knuckle: vertical bar on the column, cradle under the drive gearmotor."""
    return compound([box(X_FRONT - 12, Y_BAR0, Z_BAR0, X_FRONT + 12, Y_BAR1, Z_BAR1),
                     box(X_FRONT - X_CRADLE, Y_MOTOR0, Z_CRADLE0, X_FRONT + X_CRADLE, Y_MOTOR1, Z_CRADLE1)])


def steering_column():
    return cyl(R_COL, (X_FRONT, Y_SUSP, Z_COL0), (X_FRONT, Y_SUSP, Z_COL1))


def clamp_coupler():
    return ring_z(R_COL, 11.0, X_FRONT, Y_SUSP, Z_COL1, 284.0)


def ball_bearing():
    return ring_z(6.0, 14.0, X_FRONT, Y_SUSP, 248.0, 255.0)


def bearing_housing():
    return ring_z(14.0, 20.0, X_FRONT, Y_SUSP, Z_PLATE1, Z_HOUS1)


def motor_flange():
    return box(X_FRONT - 22, Y_SUSP - 22, Z_FL0, X_FRONT + 22, Y_SUSP + 22, Z_FL1)


def base_plate():
    return box(X_FRONT - 25, Y_SUSP - 25, Z_PLATE0, X_FRONT + 25, Y_SUSP + 25, Z_PLATE1)


def steering_stop():
    return box(X_FRONT + 18, Y_SUSP - 4, Z_PLATE1, X_FRONT + 25, Y_SUSP + 4, 254.0)


def spacer_sleeve():
    return ring_z(6.0, 9.0, X_FRONT, Y_SUSP, 255.0, 261.0)


def retaining_ring():
    return ring_z(5.5, 7.5, X_FRONT, Y_SUSP, 268.0, 269.0)


def de_m3_screw():
    return screw(1.5, 2.75, (X_FRONT + 16, Y_SUSP + 16, 284.0), (X_FRONT + 16, Y_SUSP + 16, Z_FL1), 3.0)


def de_m4_screw():
    return screw(2.0, 3.5, (X_FRONT + 18, Y_SUSP + 18, 230.8), (X_FRONT + 18, Y_SUSP + 18, 246.8), 3.0)


def set_screw():
    return cyl(2.0, (X_FRONT, 325.0, 110.0), (X_FRONT, 325.0, 120.0))


def shaft_seal():
    return ring_z(6.0, 14.0, X_FRONT, Y_SUSP, Z_PLATE1, 247.0)


def clamp_ring():
    return ring_along(R_MOTOR, 21.0, (X_FRONT, 262.0, Z_AXLE), (X_FRONT, 272.0, Z_AXLE))


def shim():
    return ring_z(6.0, 9.0, X_FRONT, Y_SUSP, 247.0, 248.0)


def de_m4_washer():
    return ring_z(2.2, 4.5, X_FRONT + 18, Y_SUSP + 18, Z_PLATE1, 246.8)


def de_m4_nut():
    return nut(3.5, (X_FRONT, 218.8, 180.0), (X_FRONT, Y_BAR0, 180.0))


def adapter_shaft():
    return ring_z(3.0, 6.0, X_FRONT, Y_SUSP, 276.0, Z_FL0)


def dowel_pin():
    return cyl(1.5, (X_FRONT + 15, Y_SUSP - 15, 236.0), (X_FRONT + 15, Y_SUSP - 15, 250.0))


def thrust_washer():
    return ring_z(6.0, 12.0, X_FRONT, Y_SUSP, Z_BAR1, Z_HEAD0)


def spring_washer():
    return ring_along(2.2, 3.8, (X_FRONT, Y_BAR1, 180.0), (X_FRONT, Y_BAR1 + 1, 180.0))


# ---------------------------------------------------------------- FR wheels and covers

def wheel():
    """Composite rim web and elastomer tire, bonded."""
    return compound([ring_along(R_RIM, R_WHEEL, (X_FRONT, Y_TIRE0, Z_AXLE), (X_FRONT, Y_TIRE1, Z_AXLE)),
                     cyl(R_RIM, (X_FRONT, Y_WEB0, Z_AXLE), (X_FRONT, Y_WEB1, Z_AXLE))])


def wheel_hub():
    return compound([cyl(R_HUB, (X_FRONT, Y_HUB0, Z_AXLE), (X_FRONT, Y_HUB1, Z_AXLE)),
                     cyl(R_FLANGE, (X_FRONT, Y_FLANGE0, Z_AXLE), (X_FRONT, Y_WEB0, Z_AXLE))])


def hub_cap():
    return cyl(30.0, (X_FRONT, Y_WEB1, Z_AXLE), (X_FRONT, Y_WEB1 + 6, Z_AXLE))


def end_panel(front):
    x0, x1 = (BODY_X, BODY_X + T) if front else (-BODY_X - T, -BODY_X)
    return box(x0, -(BODY_Y + 5), 215.0, x1, BODY_Y + 5, Z_TOP + T)


def top_cover():
    return box(-LID_X, -(BODY_Y + 5), Z_TOP, LID_X, BODY_Y + 5, Z_TOP + T)


def camera_housing():
    return box(X_MAST - 15, -25.0, Z_MAST1, X_MAST + 15, 25.0, 660.0)


def steering_cowl():
    return cyl(20.0, (X_FRONT, Y_SUSP, Z_FL1), (X_FRONT, Y_SUSP, 360.0))


def fender():
    return arch_y(X_FRONT, Z_AXLE, 108.0, 112.0, 20.0, 160.0, 300.0, 375.0)


def fender_bracket():
    """Tab standing on the knuckle cradle outside the motor, plate over the fender apex."""
    return compound([box(X_FRONT + X_TAB0, Y_TAB0, Z_CRADLE1, X_FRONT + X_TAB1, Y_TAB1, 214.0),
                     box(X_FRONT + X_TAB0, Y_TAB0, 208.0, X_FRONT + X_TAB1, 320.0, 214.0)])


def fr_m3_screw():
    return screw(1.5, 2.75, (X_FRONT, 341.0, 135.0), (X_FRONT, Y_FLANGE0, 135.0), 3.0)


def fr_m4_countersunk():
    return screw(2.0, 4.0, (256.0, 150.0, 368.0), (256.0, 150.0, Z_TOP), 3.0)


def rivet_nut():
    return cyl(3.0, (272.0, 187.0, 300.0), (BODY_X, 187.0, 300.0))


def latch():
    return box(20.0, 203.0, 370.0, 50.0, 213.0, 386.0)


def hinge():
    return box(-LID_X - 6, 100.0, Z_TOP + T, -LID_X + 6, 140.0, 386.0)


def lid_gasket():
    """Two strips on the side panel tops under the lid."""
    return compound([box(-LID_X, BODY_Y - T, Z_TOP - 1.0, LID_X, BODY_Y, Z_TOP),
                     box(-LID_X, -BODY_Y, Z_TOP - 1.0, LID_X, -(BODY_Y - T), Z_TOP)])


def fr_m3_washer():
    return ring_along(1.6, 3.5, (X_FRONT, Y_FLANGE0 - 0.5, 135.0), (X_FRONT, Y_FLANGE0, 135.0))


def nameplate():
    return box(-BODY_X - T - 1, -40.0, 300.0, -BODY_X - T, 40.0, 330.0)


# ---------------------------------------------------------------- UK electronics hardware

def sbc():
    return box(130.0, -28.0, Z_SBC0, 215.0, 28.0, Z_SBC1)


def controller(y0):
    return box(-30.0, y0, Z_BOARD0, 30.0, y0 + 40, Z_BOARD1)


def power_board():
    return box(60.0, -170.0, Z_BOARD0, 120.0, -110.0, Z_BOARD0 + 10)


def battery_pack():
    return box(-250.0, -75.0, 230.0, -60.0, 75.0, 320.0)


def battery_tray():
    return compound([box(-255.0, -80.0, Z_FLOOR + T, -55.0, 80.0, 230.0),
                     box(-255.0, -80.0, 230.0, -252.0, 80.0, 262.0),
                     box(-58.0, -80.0, 230.0, -55.0, 80.0, 262.0)])


def battery_strap():
    return box(-220.0, -82.0, 320.0, -200.0, 82.0, 322.0)


def camera_module():
    return compound([box(X_MAST - 1, -12.0, 630.0, X_MAST + 13, 12.0, 650.0),
                     cyl(5.0, (X_MAST + 13, 0.0, 640.0), (X_MAST + 19, 0.0, 640.0))])


def camera_cable():
    pts = [(X_MAST - 1, 0.0, 630.0), (258.0, 0.0, 625.0), (258.0, 0.0, Z_TOP + T), (258.0, 0.0, 300.0), (215.0, 0.0, 290.0)]
    return compound([cyl(2.0, a, b) for a, b in zip(pts, pts[1:])])


def main_switch():
    return compound([cyl(8.0, (-298.0, -120.0, 340.0), (-BODY_X, -120.0, 340.0)),
                     box(-BODY_X, -128.0, 332.0, -262.0, -112.0, 348.0)])


def estop():
    return compound([cyl(11.0, (X_ESTOP, Y_ESTOP, 384.0), (X_ESTOP, Y_ESTOP, 396.0)),
                     cyl(16.0, (X_ESTOP, Y_ESTOP, 396.0), (X_ESTOP, Y_ESTOP, 410.0)),
                     box(X_ESTOP - 8, Y_ESTOP - 8, 340.0, X_ESTOP + 8, Y_ESTOP + 8, 363.0)])


def fuse_holder():
    return box(-40.0, 90.0, Z_FLOOR + T, -15.0, 110.0, 243.0)


def dc_converter():
    return box(60.0, 120.0, Z_BOARD0, 100.0, 160.0, Z_BOARD1)


def loom(r, pts):
    return compound([cyl(r, a, b) for a, b in zip(pts, pts[1:])])


def drive_harness():
    z = Z_BOARD0 + 8
    return loom(4.0, [(0.0, -176.0, z), (-45.0, -176.0, 300.0), (-45.0, 150.0, 300.0), (60.0, 206.0, 300.0),
                      (X_FRONT, 232.0, 115.0)])


def steering_harness():
    z = Z_BOARD0 + 8
    return loom(3.0, [(0.0, 6.0, z), (-50.0, 6.0, 310.0), (-50.0, 150.0, 310.0), (80.0, 206.0, 310.0),
                      (X_FRONT, 236.0, 300.0)])


def power_harness():
    return loom(5.0, [(-60.0, 0.0, 300.0), (-48.0, -100.0, 300.0), (60.0, -140.0, 285.0)])


def signal_harness():
    return loom(3.0, [(130.0, -10.0, 282.0), (90.0, -10.0, 292.0), (45.0, -150.0, 292.0), (30.0, -150.0, 282.0)])


def radio_module():
    return box(200.0, 110.0, Z_BOARD0, 250.0, 160.0, Z_BOARD0 + 7)


def antenna():
    return compound([cyl(7.0, (X_ESTOP, Y_ANT, Z_TOP), (X_ESTOP, Y_ANT, 390.0)),
                     cyl(2.5, (X_ESTOP, Y_ANT, 390.0), (X_ESTOP, Y_ANT, 600.0))])


def led_board():
    return box(272.0, -30.0, 330.0, 275.0, 30.0, 350.0)


def sbc_standoff():
    return cyl(2.5, (135.0, -23.0, Z_PLATE_E), (135.0, -23.0, Z_SBC0))


def electronics_plate():
    return box(-40.0, -175.0, 262.0, 255.0, 175.0, Z_PLATE_E)


def uk_cap_screw():
    return screw(1.5, 2.75, (135.0, -23.0, Z_SBC1 - 10), (135.0, -23.0, Z_SBC1), 3.0)


def uk_machine_screw():
    return screw(2.08, 3.5, (-243.8, 0.0, 250.0), (-256.5, 0.0, 250.0), 2.5)


def uk_nut():
    return nut(4.5, (-210.0, 60.0, 322.0), (-210.0, 60.0, 325.2))


def cable_tie():
    return ring_along(4.5, 5.5, (60.0, 100.0, 300.0), (60.0, 104.8, 300.0))


def cable_gland():
    return cyl(10.0, (60.0, 196.0, 300.0), (60.0, 212.0, 300.0))


def connector_housing():
    return box(-7.5, -180.0, 274.0, 7.5, -170.0, 284.0)


def heat_sink():
    return box(-25.0, -165.0, Z_BOARD1, 25.0, -135.0, Z_BOARD1 + 15)


def terminal_block():
    return box(130.0, -170.0, Z_PLATE_E, 170.0, -150.0, 280.0)


def current_sensor():
    return box(60.0, -95.0, Z_BOARD0, 90.0, -65.0, Z_BOARD0 + 5)


def nylon_standoff():
    return cyl(3.0, (-25.0, -165.0, Z_PLATE_E), (-25.0, -165.0, Z_BOARD0))


# ---------------------------------------------------------------- parts: (CAD key, owner, label, builder)

PARTS = [
    # ES suspension and body
    ("es-rocker-left", "ES", "Left rocker", rocker),
    ("es-rocker-right", "ES", "Right rocker", mirrored(rocker)),
    ("es-bogie-left", "ES", "Left bogie", bogie),
    ("es-bogie-right", "ES", "Right bogie", mirrored(bogie)),
    ("es-differential-bar", "ES", "Differential bar", differential_bar),
    ("es-differential-bracket", "ES", "Differential beam and pivot post", differential_bracket),
    ("es-differential-link", "ES", "Differential link rod", differential_link),
    ("es-rocker-pivot-housing", "ES", "Rocker pivot housing (cast)", rocker_pivot_housing),
    ("es-pivot-bolt", "ES", "Pivot bolt M12 x 80", pivot_bolt),
    ("es-bogie-pivot-housing", "ES", "Bogie pivot housing (cast)", bogie_pivot_housing),
    ("es-pivot-spacer", "ES", "Pivot spacer sleeve", pivot_spacer),
    ("es-middle-mount-left", "ES", "Left middle motor mount (cast)", middle_motor_mount),
    ("es-middle-mount-right", "ES", "Right middle motor mount (cast)", mirrored(middle_motor_mount)),
    ("es-steering-head", "ES", "Steering head (cast)", steering_head),
    ("es-rod-end", "ES", "Rod end M6", rod_end),
    ("es-floor-pan", "ES", "Floor pan", floor_pan),
    ("es-side-panel-left", "ES", "Left side panel", side_panel),
    ("es-side-panel-right", "ES", "Right side panel", mirrored(side_panel)),
    ("es-cross-member-front", "ES", "Front cross member", lambda: cross_member(True)),
    ("es-cross-member-rear", "ES", "Rear cross member", lambda: cross_member(False)),
    ("es-corner-angle", "ES", "Body corner angle", corner_angle),
    ("es-mast-base", "ES", "Camera mast base (cast)", mast_base),
    ("es-mast", "ES", "Camera mast", mast),
    ("es-electronics-rail", "ES", "Electronics plate rail", electronics_rail),
    ("es-estop-bracket", "ES", "Emergency stop bracket", estop_bracket),
    ("es-screw-m3x10", "ES", "Screw M3 x 10 (inch catalogue)", es_m3_screw),
    ("es-bolt-m6x45", "ES", "Hex bolt M6 x 45", es_m6_bolt),
    ("es-nut-m12", "ES", "Lock nut M12", es_m12_nut),
    ("es-screw-m4x16", "ES", "Screw M4 x 16", es_m4_screw),
    ("es-rivet-4x8", "ES", "Blind rivet 4 x 8", es_rivet),
    ("es-washer-m12", "ES", "Washer M12", es_m12_washer),
    ("es-bronze-bushing", "ES", "Bronze bushing 12 x 16 x 28", bronze_bushing),
    ("es-hub-insert", "ES", "Cast hub insert (draft)", hub_insert),
    ("es-nut-m4", "ES", "Nut M4", es_m4_nut),
    ("es-grease-nipple", "ES", "Grease nipple M6", grease_nipple),
    # DE drive and steering
    ("de-drive-gearmotor", "DE", "Drive gearmotor 12 V", drive_gearmotor),
    ("de-steering-gearmotor", "DE", "Steering gearmotor 12 V", steering_gearmotor),
    ("de-knuckle-left", "DE", "Left steering knuckle", knuckle),
    ("de-knuckle-right", "DE", "Right steering knuckle", mirrored(knuckle)),
    ("de-steering-column", "DE", "Steering column", steering_column),
    ("de-clamp-coupler", "DE", "Clamp coupler 6/6", clamp_coupler),
    ("de-ball-bearing", "DE", "Deep groove ball bearing 6001", ball_bearing),
    ("de-bearing-housing", "DE", "Steering bearing housing", bearing_housing),
    ("de-motor-flange", "DE", "Steering motor flange", motor_flange),
    ("de-base-plate", "DE", "Steering unit base plate", base_plate),
    ("de-steering-stop", "DE", "Steering stop", steering_stop),
    ("de-spacer-sleeve", "DE", "Bearing spacer sleeve", spacer_sleeve),
    ("de-retaining-ring", "DE", "Retaining ring 12", retaining_ring),
    ("de-screw-m3x10", "DE", "Screw M3 x 10", de_m3_screw),
    ("de-screw-m4x16", "DE", "Screw M4 x 16", de_m4_screw),
    ("de-set-screw-m4x6", "DE", "Set screw M4 x 6", set_screw),
    ("de-shaft-seal", "DE", "Shaft seal 12 x 28 x 7", shaft_seal),
    ("de-clamp-ring", "DE", "Drive motor clamp ring", clamp_ring),
    ("de-shim-12", "DE", "Shim washer 12 x 18 x 1", shim),
    ("de-washer-m4", "DE", "Washer M4", de_m4_washer),
    ("de-nut-m4", "DE", "Hex nut M4", de_m4_nut),
    ("de-adapter-shaft", "DE", "Steering motor adapter sleeve", adapter_shaft),
    ("de-dowel-pin", "DE", "Dowel pin 3 x 14", dowel_pin),
    ("de-thrust-washer", "DE", "Thrust washer 12 x 24 x 2", thrust_washer),
    ("de-spring-washer-m4", "DE", "Spring washer M4", spring_washer),
    # FR wheels and covers
    ("fr-wheel", "FR", "Wheel (composite rim and elastomer tire)", wheel),
    ("fr-wheel-hub", "FR", "Wheel hub", wheel_hub),
    ("fr-hub-cap", "FR", "Hub cap", hub_cap),
    ("fr-front-panel", "FR", "Front panel", lambda: end_panel(True)),
    ("fr-rear-panel", "FR", "Rear panel", lambda: end_panel(False)),
    ("fr-top-cover", "FR", "Top cover", top_cover),
    ("fr-camera-housing", "FR", "Camera housing", camera_housing),
    ("fr-steering-cowl", "FR", "Steering motor cowl", steering_cowl),
    ("fr-fender", "FR", "Fender", fender),
    ("fr-fender-bracket", "FR", "Fender bracket", fender_bracket),
    ("fr-screw-m3x10", "FR", "Screw M3 x 10", fr_m3_screw),
    ("fr-screw-m4x12-csk", "FR", "Countersunk screw M4 x 12", fr_m4_countersunk),
    ("fr-rivet-nut-m4", "FR", "Rivet nut M4", rivet_nut),
    ("fr-latch", "FR", "Quarter-turn latch", latch),
    ("fr-hinge", "FR", "Hinge", hinge),
    ("fr-lid-gasket", "FR", "Lid gasket", lid_gasket),
    ("fr-washer-m3", "FR", "Washer M3", fr_m3_washer),
    ("fr-nameplate", "FR", "Nameplate", nameplate),
    # UK electronics hardware
    ("uk-sbc", "UK", "Single-board computer", sbc),
    ("uk-drive-controller", "UK", "Drive motor controller (dual channel)", lambda: controller(-170.0)),
    ("uk-steering-controller", "UK", "Steering motor controller (dual channel)", lambda: controller(10.0)),
    ("uk-power-board", "UK", "Power distribution board", power_board),
    ("uk-battery-pack", "UK", "Battery pack 12 V", battery_pack),
    ("uk-battery-tray", "UK", "Battery tray", battery_tray),
    ("uk-battery-strap", "UK", "Battery strap", battery_strap),
    ("uk-camera-module", "UK", "Camera module", camera_module),
    ("uk-camera-cable", "UK", "Camera cable", camera_cable),
    ("uk-main-switch", "UK", "Main power switch", main_switch),
    ("uk-estop", "UK", "Emergency stop button", estop),
    ("uk-fuse-holder", "UK", "Blade fuse holder", fuse_holder),
    ("uk-dc-converter", "UK", "DC-DC converter 12 V to 5 V", dc_converter),
    ("uk-drive-harness", "UK", "Drive motor harness", drive_harness),
    ("uk-steering-harness", "UK", "Steering motor harness", steering_harness),
    ("uk-power-harness", "UK", "Power harness", power_harness),
    ("uk-signal-harness", "UK", "Signal harness", signal_harness),
    ("uk-radio-module", "UK", "Radio receiver module", radio_module),
    ("uk-antenna", "UK", "Whip antenna", antenna),
    ("uk-led-board", "UK", "Status LED board", led_board),
    ("uk-sbc-standoff", "UK", "SBC standoff 0.5 in", sbc_standoff),
    ("uk-electronics-plate", "UK", "Electronics plate", electronics_plate),
    ("uk-cap-screw-0118", "UK", "Cap screw 0.118 x 0.394 in", uk_cap_screw),
    ("uk-machine-screw-8-32", "UK", "Machine screw No. 8-32 x 0.5 in", uk_machine_screw),
    ("uk-nut-8-32", "UK", "Nut No. 8-32", uk_nut),
    ("uk-cable-tie", "UK", "Cable tie 8 in", cable_tie),
    ("uk-cable-gland", "UK", "Cable gland", cable_gland),
    ("uk-connector-housing", "UK", "Motor connector housing, 6-way", connector_housing),
    ("uk-heat-sink", "UK", "Controller heat sink", heat_sink),
    ("uk-terminal-block", "UK", "Terminal block, 4-way", terminal_block),
    ("uk-current-sensor", "UK", "Current sensor board", current_sensor),
    ("uk-nylon-standoff", "UK", "Nylon standoff 0.25 in", nylon_standoff),
]
