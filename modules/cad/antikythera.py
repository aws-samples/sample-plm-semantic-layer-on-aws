# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Antikythera mechanism, simplified interpretation, built today as a museum-scale bronze model in a wooden case:
33 parts, one STEP file each.

Frame (mm): x to the right as seen from the front dial, y towards the back of the case (depth), z up; x = 0 and
z = 0 on the main drive axis b, y = 0 on the front face of the bronze front plate. Case 180 x 96 x 340 (x, y, z):
front door frame at y -16, back door frame to y 80, side frames at x +/-90, top and bottom rails at z +/-170. The
casing is open so the mechanism shows: each side is a frame of four rails (the right side has a fifth, the crank
bearer), the top and bottom are two rails each, the doors are frames, and the two bronze plates are pierced with
circular openings between their arbor seats; every opening is an inner wire of one extruded face, not a boolean.

Interpretation choices (the gear scheme and tooth counts follow Freeth et al. 2006 and Freeth and Jones 2012; the
counts are attributes of the parts in data/products/antikythera.json, not geometry):
- 29 gears of the published reconstruction: the crank crown a1; the main drive wheel b1 with b2 and the lunar output
  b3 on axis b; the mean-sun train c1/c2, d1/d2 to e2; the lunar anomaly device e5, k1, k2, e6 with its output e1;
  the back-dial input l1/l2, m1/m2/m3; the Metonic gear n1; the Saros wheel e3/e4 with f1/f2 and g1/g2; the
  Exeligmos pair h1/h2 and i1. The Olympiad and Callippic trains (n2, n3, o1, p1, p2, q1) are left out.
- Every gear is a plain disc at its pitch diameter with one module of 0.55 mm for the whole machine (the original
  varies by gear); the triangular teeth of the original are not modelled, so a disc's radius is 0.275 mm times its
  tooth count. Axis positions are solved from the centre distances module x (z1 + z2) / 2 of the meshes, as circle
  intersections outward from axis b, so every meshing pair of discs is tangent.
- Gears fixed on one arbor are one part (a stepped gear with its arbor, a cylinder along y); the arbors pass through
  the plates without modelled holes.
- The pin-and-slot pair k1/k2 shares one stud on e3; the 1.1 mm eccentricity of the original, the pin and the slot
  are attributes. The lunar output e6/e1 is a tube around arbor e.
- The crank and its crown gear a1 are hypothetical in the scholarship (48 teeth adopted); modelled as a contrate
  disc on an arbor along x through the right board.
- Dials are plain plates with a raised rim; spiral scales and inscriptions are attributes.

The part keys here are the CAD keys of data/products/antikythera.json (cad/antikythera/<key>.stp); the PLM part
ids live in that file.
"""
import math

import cadquery as cq

from geometry import V, box, compound, cyl, prism, ring

KEY = "antikythera"
NAME = "Antikythera mechanism, simplified interpretation"
FRAME = ("x to the right seen from the front dial, y towards the back of the case, z up; x = z = 0 on the main "
         "drive axis b, y = 0 on the front face of the front plate; mm")

# ---------------------------------------------------------------- gears: tooth counts and one module

MODULE = 0.55
TEETH = dict(a1=48, b1=223, b2=64, b3=32, c1=38, c2=48, d1=24, d2=127, e1=32, e2=32, e3=223, e4=188, e5=50, e6=50,
             f1=53, f2=30, g1=54, g2=20, h1=60, h2=15, i1=60, k1=50, k2=50, l1=38, l2=53, m1=96, m2=15, m3=27, n1=53)


def pitch_radius(gear):
    return MODULE * TEETH[gear] / 2


def centre_distance(gear_a, gear_b):
    return MODULE * (TEETH[gear_a] + TEETH[gear_b]) / 2


# ---------------------------------------------------------------- axis layout in the plate plane (x, z)

def polar(p, r, deg):
    return (round(p[0] + r * math.cos(math.radians(deg)), 3), round(p[1] + r * math.sin(math.radians(deg)), 3))


def meet(p, rp, q, rq, side):
    """One of the two points at distance rp from p and rq from q (side +1 or -1 of the line p-q)."""
    dx, dz = q[0] - p[0], q[1] - p[1]
    d = math.hypot(dx, dz)
    a = (rp * rp - rq * rq + d * d) / (2 * d)
    h = math.sqrt(rp * rp - a * a)
    mx, mz = p[0] + a * dx / d, p[1] + a * dz / d
    return (round(mx + side * h * dz / d, 3), round(mz - side * h * dx / d, 3))


AXIS = {"b": (0.0, 0.0)}
AXIS["e"] = (0.0, -centre_distance("b3", "e1"))                                   # lunar output e1 -> b3
AXIS["c"] = polar(AXIS["b"], centre_distance("b2", "c1"), 150)                    # mean-sun train, upper left
AXIS["d"] = meet(AXIS["c"], centre_distance("c2", "d1"), AXIS["e"], centre_distance("d2", "e2"), 1)
AXIS["l"] = polar(AXIS["b"], centre_distance("b2", "l1"), 30)                     # back-dial input, upper right
AXIS["m"] = meet(AXIS["l"], centre_distance("l2", "m1"), AXIS["e"], centre_distance("m3", "e3"), 1)
AXIS["n"] = polar(AXIS["m"], centre_distance("m2", "n1"), 90)                     # Metonic dial centre
AXIS["k"] = polar(AXIS["e"], centre_distance("e5", "k1"), 0)                      # pin-and-slot stud on e3
AXIS["g"] = (-4.0, -96.0)                                                          # Saros dial centre
AXIS["f"] = meet(AXIS["e"], centre_distance("e4", "f1"), AXIS["g"], centre_distance("f2", "g1"), -1)
AXIS["h"] = polar(AXIS["g"], centre_distance("g2", "h1"), -30)
AXIS["i"] = polar(AXIS["h"], centre_distance("h2", "i1"), -10)                    # Exeligmos dial centre
X_CROWN = pitch_radius("b1")                                                       # a1 touches the rim of b1
Y_CROWN = 9.0 + pitch_radius("a1")                                                 # a1 axis behind the b1 plane

# ---------------------------------------------------------------- depth layers (y0, y1)

Y_FRONT_DOOR, Y_BACK_DOOR = (-16.0, -10.0), (74.0, 80.0)
Y_LUNAR_POINTER, Y_SUN_POINTER = (-8.0, -6.5), (-5.5, -4.0)
Y_FRONT_DIAL, Y_FRONT_PLATE = (-2.5, 0.0), (0.0, 3.0)
Y_B1 = (6.0, 9.0)
Y_L1, Y_L2, Y_L3 = (10.0, 12.5), (14.0, 16.5), (18.0, 20.5)      # b2 c1 l1 / c2 d1 l2 m1 / d2 e2 m2 n1
Y_L4, Y_L5, Y_L6 = (28.0, 30.5), (32.0, 34.5), (36.0, 38.5)      # e1 b3 / e6 k2 / e5 k1
Y_E3, Y_E4 = (40.0, 43.0), (44.0, 47.0)                          # e3 / e4 m3 f1
Y_L7, Y_L8, Y_L9 = (48.0, 50.5), (52.0, 54.5), (56.0, 58.5)      # f2 g1 / g2 h1 / h2 i1
Y_BACK_PLATE, Y_BACK_DIAL, Y_BACK_POINTER = (62.0, 65.0), (65.0, 67.5), (68.0, 70.0)
R_ARBOR = 1.5
PLATE_X, PLATE_Z = 84.0, 164.0


# ---------------------------------------------------------------- primitives along y and x

def disc(gear, axis, layer):
    """Plain disc at the pitch radius of `gear`, axis along y through AXIS[axis], between the layer's depths."""
    x, z = AXIS[axis]
    return cyl(pitch_radius(gear), (x, layer[0], z), (x, layer[1], z))


def arbor(axis, y0, y1, r=R_ARBOR):
    x, z = AXIS[axis]
    return cyl(r, (x, y0, z), (x, y1, z))


def ring_y(r_in, r_out, x, z, y0, y1):
    """Annulus with its axis along y: the z-axis ring rotated about x, then placed."""
    return ring(r_in, r_out, y0, y1 - y0).rotate(V(0, 0, 0), V(1, 0, 0), -90).translate(V(x, 0, z))


def ring_x(r_in, r_out, y, z, x0, x1):
    """Annulus with its axis along x."""
    return ring(r_in, r_out, x0, x1 - x0).rotate(V(0, 0, 0), V(0, 1, 0), 90).translate(V(0, y, z))


def pointer(axis, layer, length, deg, hub_in, hub_out, width=4.0):
    """Flat arm from the hub to `length` in the plate plane, `deg` clockwise from 12 o'clock seen from the front."""
    x, z = AXIS[axis]
    y = (layer[0] + layer[1]) / 2
    d = (math.sin(math.radians(deg)), math.cos(math.radians(deg)))
    arm = prism((x + hub_out * d[0], y, z + hub_out * d[1]), (x + length * d[0], y, z + length * d[1]),
                width, layer[1] - layer[0], up=(0, 1, 0))
    return [arm, ring_y(hub_in, hub_out, x, z, *layer)]


def dial(axis, r, layer):
    """Plate with a raised rim, 1.5 mm proud of the plate."""
    x, z = AXIS[axis]
    return compound([cyl(r, (x, layer[0], z), (x, layer[1], z)), ring_y(r - 4, r, x, z, layer[1], layer[1] + 1.5)])


# ---------------------------------------------------------------- DE: crank, main drive, mean sun, lunar, Metonic

def crank_handle():
    y, hub = Y_CROWN, (94.0, 100.0)
    return compound([ring_x(2.0, 5.0, y, 0.0, *hub), box(hub[0], y - 1.5, 0.0, hub[1], y + 1.5, 40.0),
                     cyl(4.0, (hub[1], y, 40.0), (125.0, y, 40.0))])


def crown_gear_a1():
    r = pitch_radius("a1")
    return compound([cyl(r, (X_CROWN - 1.25, Y_CROWN, 0.0), (X_CROWN + 1.25, Y_CROWN, 0.0)),
                     cyl(2.0, (X_CROWN + 1.25, Y_CROWN, 0.0), (94.0, Y_CROWN, 0.0))])


def main_drive_wheel_b1():
    """b1 on the hollow arbor b; b2 (UK) and the sun pointer (FR) key onto the same arbor."""
    return compound([disc("b1", "b", Y_B1), ring_y(1.5, 3.0, 0.0, 0.0, -6.0, Y_L1[1])])


def gear_cluster_c():
    return compound([disc("c1", "c", Y_L1), disc("c2", "c", Y_L2), arbor("c", 1.0, Y_L2[1] + 1.0)])


def gear_cluster_d():
    return compound([disc("d1", "d", Y_L2), disc("d2", "d", Y_L3), arbor("d", 1.0, Y_L3[1] + 1.0)])


def lunar_arbor_e():
    """Arbor e with e2 (mean sun input) and e5 (drives k1); the tube e6/e1 and the Saros wheel ride on it."""
    return compound([disc("e2", "e", Y_L3), disc("e5", "e", Y_L6), arbor("e", 1.0, Y_E4[1] + 1.0)])


def lunar_output_tube_e():
    x, z = AXIS["e"]
    return compound([disc("e6", "e", Y_L5), disc("e1", "e", Y_L4), ring_y(1.6, 2.6, x, z, Y_L4[0] - 0.5, Y_L5[1] + 0.5)])


def lunar_pointer_arbor_b3():
    """Inner arbor through the hollow arbor b; b3 at the back, the lunar pointer (FR) on its front tip."""
    return compound([disc("b3", "b", Y_L4), arbor("b", Y_LUNAR_POINTER[0] - 0.5, Y_L4[1], r=1.4)])


def pin_slot_driver_k1():
    x, z = AXIS["k"]
    return compound([disc("k1", "k", Y_L6), cyl(0.75, (x + 9.0, Y_L5[0] + 1.0, z), (x + 9.0, Y_L6[0], z))])


def gear_cluster_l():
    return compound([disc("l1", "l", Y_L1), disc("l2", "l", Y_L2), arbor("l", 1.0, Y_L2[1] + 1.0)])


def gear_cluster_m():
    return compound([disc("m1", "m", Y_L2), disc("m2", "m", Y_L3), disc("m3", "m", Y_E4), arbor("m", 1.0, Y_E4[1] + 1.0)])


def metonic_gear_n1():
    return compound([disc("n1", "n", Y_L3), arbor("n", 1.0, Y_BACK_POINTER[1] + 1.0)])


# ---------------------------------------------------------------- UK: Saros and Exeligmos trains, pin-and-slot follower

def input_gear_b2():
    return disc("b2", "b", Y_L1)


def saros_wheel_e3():
    """e3 and e4 fixed together on a bush around arbor e, with the stud that carries k1 and k2."""
    x, z = AXIS["e"]
    kx, kz = AXIS["k"]
    return compound([disc("e3", "e", Y_E3), disc("e4", "e", Y_E4), ring_y(1.6, 4.0, x, z, Y_E3[0], Y_E4[1]),
                     cyl(1.2, (kx, Y_L5[0] - 0.5, kz), (kx, Y_E3[0], kz))])


def pin_slot_follower_k2():
    return disc("k2", "k", Y_L5)


def gear_cluster_f():
    return compound([disc("f1", "f", Y_E4), disc("f2", "f", Y_L7), arbor("f", Y_E4[0] - 2.0, Y_BACK_PLATE[1])])


def gear_cluster_g():
    return compound([disc("g1", "g", Y_L7), disc("g2", "g", Y_L8), arbor("g", Y_L7[0] - 1.0, Y_BACK_POINTER[1] + 1.0)])


def gear_cluster_h():
    return compound([disc("h1", "h", Y_L8), disc("h2", "h", Y_L9), arbor("h", Y_L8[0] - 1.0, Y_BACK_PLATE[1])])


def exeligmos_gear_i1():
    return compound([disc("i1", "i", Y_L9), arbor("i", Y_L9[0] - 1.0, Y_BACK_POINTER[1] + 1.0)])


# ---------------------------------------------------------------- FR: dials and pointers

def front_dial_ring():
    """Zodiac and Egyptian calendar scales: an engraved ring outside the main drive wheel."""
    return ring_y(64.0, 84.0, 0.0, 0.0, *Y_FRONT_DIAL)


def sun_pointer():
    return compound(pointer("b", Y_SUN_POINTER, 80.0, 20.0, 3.0, 6.0))


def lunar_pointer():
    """Lunar pointer with the moon-phase sphere near its tip."""
    arm, hub = pointer("b", Y_LUNAR_POINTER, 70.0, 110.0, 1.4, 4.0)
    d = (math.sin(math.radians(110.0)), math.cos(math.radians(110.0)))
    y = (Y_LUNAR_POINTER[0] + Y_LUNAR_POINTER[1]) / 2
    return compound([arm, hub, cq.Solid.makeSphere(3.0, V(58.0 * d[0], y, 58.0 * d[1]))])


def metonic_dial():
    return dial("n", 62.0, Y_BACK_DIAL)


def saros_dial():
    return dial("g", 62.0, Y_BACK_DIAL)


def metonic_pointer():
    """Pointer with the sliding follower that rides the spiral groove."""
    arm, hub = pointer("n", Y_BACK_POINTER, 58.0, 200.0, 1.5, 4.0)
    x, z = AXIS["n"]
    d = (math.sin(math.radians(200.0)), math.cos(math.radians(200.0)))
    return compound([arm, hub, cyl(2.0, (x + 50.0 * d[0], Y_BACK_DIAL[1], z + 50.0 * d[1]),
                                    (x + 50.0 * d[0], Y_BACK_POINTER[1], z + 50.0 * d[1]))])


def saros_pointer():
    arm, hub = pointer("g", Y_BACK_POINTER, 58.0, 300.0, 1.5, 4.0)
    x, z = AXIS["g"]
    d = (math.sin(math.radians(300.0)), math.cos(math.radians(300.0)))
    return compound([arm, hub, cyl(2.0, (x + 50.0 * d[0], Y_BACK_DIAL[1], z + 50.0 * d[1]),
                                    (x + 50.0 * d[0], Y_BACK_POINTER[1], z + 50.0 * d[1]))])


def exeligmos_pointer():
    return compound(pointer("i", Y_BACK_POINTER, 13.0, 45.0, 1.5, 3.0, width=2.5))


# ---------------------------------------------------------------- ES: casing, plates, doors, hinges, pillars

def pierced(plane, width, height, openings, depth):
    """Sheet `width` x `height` centred on `plane`, extruded `depth` along its normal, with openings given in plane
    coordinates as (x, y, r) circles or (x, y, w, h) rectangles: one face with inner wires, 6 + n faces."""
    wp = cq.Workplane(plane).rect(width, height)
    for o in openings:
        wp = wp.pushPoints([(o[0], o[1])])
        wp = wp.circle(o[2]) if len(o) == 3 else wp.rect(o[2], o[3])
    return wp.extrude(depth).val()


def plate_plane(y_back):
    """Plane of a front-facing sheet whose back face is at y_back; local (x, z), extrudes towards -y."""
    return cq.Plane(origin=(0, y_back, 0), xDir=(1, 0, 0), normal=(0, -1, 0))


def side_plane(x_in):
    """Plane of a side sheet whose inner face is at x_in; local (y, z) about the case centre, extrudes towards +x."""
    return cq.Plane(origin=(x_in, (Y_FRONT_DOOR[0] + Y_BACK_DOOR[1]) / 2, 0), xDir=(0, 1, 0), normal=(1, 0, 0))


CASE_DEPTH = Y_BACK_DOOR[1] - Y_FRONT_DOOR[0]                  # 96
RAIL = 12.0                                                    # width of the side frame rails


def casing_body():
    """Open frame: a left side frame of four rails, a right side frame with a crank bearer rail across the middle,
    and two rails each along the top and the bottom."""
    inner_w, half = CASE_DEPTH - 2 * RAIL, 150.0
    left = pierced(side_plane(-90.0), CASE_DEPTH, 340.0, [(0.0, 0.0, inner_w, 2 * half)], 6.0)
    right = pierced(side_plane(84.0), CASE_DEPTH, 340.0,
                    [(0.0, (half + 8.0) / 2, inner_w, half - 8.0), (0.0, -(half + 8.0) / 2, inner_w, half - 8.0)], 6.0)
    rails = [box(-84.0, y0, z0, 84.0, y1, z1) for y0, y1 in (Y_FRONT_DOOR, Y_BACK_DOOR) for z0, z1 in ((164.0, 170.0), (-170.0, -164.0))]
    return compound([left, right] + rails)


def front_plate():
    """Three openings between the arbor seats: two above the main drive wheel, one over the Saros train."""
    return pierced(plate_plane(Y_FRONT_PLATE[1]), 2 * PLATE_X, 2 * PLATE_Z,
                   [(-46.0, 54.0, 32.0), (46.0, 54.0, 32.0), (0.0, -92.0, 50.0)], Y_FRONT_PLATE[1] - Y_FRONT_PLATE[0])


def back_plate():
    """Four openings clear of the arbor seats, the dial screws and the pass-through connector."""
    return pierced(plate_plane(Y_BACK_PLATE[1]), 2 * PLATE_X, 2 * PLATE_Z,
                   [(-44.0, 60.0, 28.0), (42.0, 58.0, 20.0), (-45.0, -112.0, 28.0), (56.0, -140.0, 18.0)],
                   Y_BACK_PLATE[1] - Y_BACK_PLATE[0])


def door_pair():
    """Two door frames with 14 mm rails."""
    opening = [(0.0, 0.0, 2 * PLATE_X - 28.0, 2 * PLATE_Z - 28.0)]
    return compound([pierced(plate_plane(Y_FRONT_DOOR[1]), 2 * PLATE_X, 2 * PLATE_Z, opening, Y_FRONT_DOOR[1] - Y_FRONT_DOOR[0]),
                     pierced(plate_plane(Y_BACK_DOOR[1]), 2 * PLATE_X, 2 * PLATE_Z, opening, Y_BACK_DOOR[1] - Y_BACK_DOOR[0])])


def hinge_set():
    """Two knuckle hinges per door on the left board."""
    pins = []
    for y in ((Y_FRONT_DOOR[0] + Y_FRONT_DOOR[1]) / 2, (Y_BACK_DOOR[0] + Y_BACK_DOOR[1]) / 2):
        for z in (-120.0, 120.0):
            pins.append(cyl(3.0, (-88.0, y, z - 15.0), (-88.0, y, z + 15.0)))
    return compound(pins)


def plate_pillar_set():
    """Four corner pillars between the plates, screwed to the side boards."""
    return compound([box(min(sx * 78.0, sx * 84.0), Y_FRONT_PLATE[1], min(sz * 150.0, sz * 160.0),
                         max(sx * 78.0, sx * 84.0), Y_BACK_PLATE[0], max(sz * 150.0, sz * 160.0))
                     for sx in (-1, 1) for sz in (-1, 1)])


# ---------------------------------------------------------------- parts: (CAD key, owner, label, builder)

PARTS = [
    ("de-crank-handle", "DE", "Crank handle", crank_handle),
    ("de-crown-gear-a1", "DE", "Crown gear a1 with arbor a (48 teeth)", crown_gear_a1),
    ("de-main-drive-wheel-b1", "DE", "Main drive wheel b1 with hollow arbor b (223 teeth)", main_drive_wheel_b1),
    ("de-gear-cluster-c", "DE", "Stepped gear c1/c2 with arbor c (38/48 teeth)", gear_cluster_c),
    ("de-gear-cluster-d", "DE", "Stepped gear d1/d2 with arbor d (24/127 teeth)", gear_cluster_d),
    ("de-lunar-arbor-e", "DE", "Lunar arbor e with e2/e5 (32/50 teeth)", lunar_arbor_e),
    ("de-lunar-output-tube-e", "DE", "Lunar output tube e6/e1 (50/32 teeth)", lunar_output_tube_e),
    ("de-lunar-pointer-arbor-b3", "DE", "Lunar pointer arbor with b3 (32 teeth)", lunar_pointer_arbor_b3),
    ("de-pin-slot-driver-k1", "DE", "Pin-and-slot driver k1 (50 teeth)", pin_slot_driver_k1),
    ("de-gear-cluster-l", "DE", "Stepped gear l1/l2 with arbor l (38/53 teeth)", gear_cluster_l),
    ("de-gear-cluster-m", "DE", "Stepped gear m1/m2/m3 with arbor m (96/15/27 teeth)", gear_cluster_m),
    ("de-metonic-gear-n1", "DE", "Metonic gear n1 with arbor n (53 teeth)", metonic_gear_n1),
    ("uk-input-gear-b2", "UK", "Saros train input gear b2 (64 teeth)", input_gear_b2),
    ("uk-saros-wheel-e3", "UK", "Saros wheel e3/e4 with epicyclic stud (223/188 teeth)", saros_wheel_e3),
    ("uk-pin-slot-follower-k2", "UK", "Pin-and-slot follower k2 (50 teeth)", pin_slot_follower_k2),
    ("uk-gear-cluster-f", "UK", "Stepped gear f1/f2 with arbor f (53/30 teeth)", gear_cluster_f),
    ("uk-gear-cluster-g", "UK", "Saros pointer gear g1/g2 with arbor g (54/20 teeth)", gear_cluster_g),
    ("uk-gear-cluster-h", "UK", "Stepped gear h1/h2 with arbor h (60/15 teeth)", gear_cluster_h),
    ("uk-exeligmos-gear-i1", "UK", "Exeligmos pointer gear i1 with arbor i (60 teeth)", exeligmos_gear_i1),
    ("fr-front-dial-ring", "FR", "Front dial ring (zodiac and Egyptian calendar)", front_dial_ring),
    ("fr-sun-pointer", "FR", "Sun pointer", sun_pointer),
    ("fr-lunar-pointer", "FR", "Lunar pointer with moon-phase sphere", lunar_pointer),
    ("fr-metonic-dial", "FR", "Upper back dial, Metonic spiral", metonic_dial),
    ("fr-saros-dial", "FR", "Lower back dial, Saros spiral", saros_dial),
    ("fr-metonic-pointer", "FR", "Metonic pointer with follower", metonic_pointer),
    ("fr-saros-pointer", "FR", "Saros pointer with follower", saros_pointer),
    ("fr-exeligmos-pointer", "FR", "Exeligmos pointer", exeligmos_pointer),
    ("es-casing-body", "ES", "Wooden casing frame (side frames, top and bottom rails)", casing_body),
    ("es-front-plate", "ES", "Bronze front plate (arbor seats, three openings)", front_plate),
    ("es-back-plate", "ES", "Bronze back plate (arbor seats, four openings)", back_plate),
    ("es-door-pair", "ES", "Front and back door frames", door_pair),
    ("es-hinge-set", "ES", "Hinges (set of four)", hinge_set),
    ("es-plate-pillar-set", "ES", "Plate pillars (set of four)", plate_pillar_set),
]
