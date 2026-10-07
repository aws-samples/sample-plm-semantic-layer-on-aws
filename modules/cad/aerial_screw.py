# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Aerial screw of Paris Manuscript B, folio 83v (about 1489), built today as a museum-scale working replica with a
hydraulic rotation drive: 36 parts, one STEP file each.

Frame (mm): z up along the mast axis, z = 0 on the floor, x towards the hydraulic skid, y to the left of it.
Proportions are read on the public-domain scan of the folio (pixel coordinates in DRAWING). Scale: the note on the
folio gives 8 braccia from the circumference to the centre, a radius; the replica is built at 1:2, so the sail
diameter is 8 Florentine braccia of 583.6 mm = 4669 mm. The sail is one left-handed turn (clockwise seen from
above) of a 20 mm ribbon, split into four quarter segments, each owned by one site; the mast is three segments, each owned by one site; nine
radial arms at 45 degree steps carry the sail and nine tension stays run from the arm tips to the rotor ring. The
arms and the stays are two set parts each (arms 1 to 5 bracket onto the middle mast segment, 6 to 9 onto the upper
one, the stays follow their arms), one STEP product with several bodies.

The part keys here are the CAD keys of data/products/aerial-screw.json (cad/aerial-screw/<key>.stp); the PLM part
ids live in that file.
"""
import math

import cadquery as cq

from geometry import V, box, compound, cyl, ring, sector

KEY = "aerial-screw"
NAME = "Aerial screw working replica (Paris Manuscript B, f. 83v)"
FRAME = "z up on the mast axis with z = 0 on the floor, x towards the hydraulic skid, y to its left; mm"

# ---------------------------------------------------------------- folio measurements (px on the 700 x 548 scan)

DRAWING = dict(
    source="https://commons.wikimedia.org/wiki/File:Leonardo_da_Vinci_helicopter.jpg (crop of Ms B 83v, Institut de France)",
    mast_top_px=(322, 40), mast_base_px=(322, 420),
    sail_lower_sweep_px=dict(left=(35, 220), right=(640, 190)),
    sail_upper_sweep_px=dict(left=(100, 120), right=(560, 95)),
    platform_px=dict(left=(180, 420), right=(470, 420)),
    collar_px=(322, 275),
)
_D_PX = DRAWING["sail_lower_sweep_px"]["right"][0] - DRAWING["sail_lower_sweep_px"]["left"][0]   # 605
_H_PX = DRAWING["mast_base_px"][1] - DRAWING["mast_top_px"][1]                                     # 380
_PITCH_PX = 0.5 * ((220 - 120) + (190 - 95))                                                        # 97.5
_PLATFORM_PX = DRAWING["platform_px"]["right"][0] - DRAWING["platform_px"]["left"][0]              # 290
_COLLAR_FRAC = (DRAWING["mast_base_px"][1] - DRAWING["collar_px"][1]) / _H_PX                       # 0.38
_SAIL0_FRAC = (420 - 215) / _H_PX                                                                    # 0.54

BRACCIO = 583.6                      # Florentine braccio, mm
D_SAIL = 8 * BRACCIO                 # 4669 mm: 8 braccia radius on the folio, built at 1:2
R_OUT = D_SAIL / 2                   # 2334.4
R_IN = 120.0                         # sail inner edge, just outside the 200 mm mast
SAIL_T = 20.0                        # ribbon thickness (linen on a lattice frame)
PITCH = round(D_SAIL * _PITCH_PX / _D_PX)          # 752 mm per turn
H_MAST = round(D_SAIL * _H_PX / _D_PX)             # 2933 mm above the deck
Z_DECK = 600.0
Z_MAST_TOP = Z_DECK + H_MAST                        # 3533
Z_COLLAR = round(Z_DECK + _COLLAR_FRAC * H_MAST)   # 1719
Z_SAIL0 = round(Z_DECK + _SAIL0_FRAC * H_MAST)     # 2182
R_DECK = round(D_SAIL * _PLATFORM_PX / _D_PX / 2)  # 1119
R_MAST = 100.0
HAND = -1                            # inner edge winds clockwise seen from above (left-handed about +z)
N_ARMS = 9                           # at 0, 45, ..., 360 deg: both ends of the one-turn sail carry an arm
Z_MAST_JOINT_1 = Z_COLLAR            # FR lower / DE middle flange at the collar
Z_MAST_JOINT_2 = 2600.0              # DE middle / UK upper flange
LOWER_ARMS, UPPER_ARMS = range(0, 5), range(5, 9)   # arms 1-5 on the middle mast, 6-9 on the upper


def arm_angle(k):
    return 45.0 * k


def helix_z(theta_deg):
    return Z_SAIL0 + PITCH * theta_deg / 360.0


def pol(r, theta_deg, z):
    a = math.radians(HAND * theta_deg)
    return V(r * math.cos(a), r * math.sin(a), z)


# ---------------------------------------------------------------- helical sail

def sail_segment(theta0):
    """Quarter turn: a thin radial rectangle swept along the mid-radius helix with the Frenet frame (the helix normal
    is radial, so the profile stays radial along the sweep); 6 faces, the smallest valid construction."""
    r_mid = (R_IN + R_OUT) / 2
    path = cq.Wire.makeHelix(pitch=PITCH, height=PITCH / 4, radius=r_mid, center=V(0, 0, Z_SAIL0),
                             dir=V(0, 0, 1), lefthand=(HAND < 0))
    start, tangent = path.startPoint(), path.tangentAt(0)
    radial = V(start.x, start.y, 0).normalized()
    prof = cq.Workplane(cq.Plane(origin=start, xDir=radial, normal=tangent)).rect(R_OUT - R_IN, SAIL_T)
    solid = prof.sweep(path, isFrenet=True).val()
    return solid.rotate(V(0, 0, 0), V(0, 0, 1), HAND * theta0).translate(V(0, 0, PITCH * theta0 / 360.0))


# ---------------------------------------------------------------- part families

def base_ring():
    return ring(1000, 1200, 0, 480)


def roller_track():
    return ring(1040, 1190, 480, 80)


def rotor_ring():
    return ring(1060, 1180, 560, 80)


def deck_sector(k):
    return sector(320, 1040, 90 * k, 90 * (k + 1), 520, 80)


def slewing_bearing():
    return ring(220, 320, 520, 80)


def hub():
    return cq.Solid.makeCylinder(200, 160, V(0, 0, Z_DECK), V(0, 0, 1))


def mast(z0, z1):
    return cq.Solid.makeCylinder(R_MAST, z1 - z0, V(0, 0, z0), V(0, 0, 1))


def collar():
    return ring(R_MAST, 165, Z_COLLAR - 15, 30)


def strut(k):
    a = 22.5 + 90 * k
    return cyl(25, pol(1080, a, 640), pol(165, a, Z_COLLAR))


def arm(k):
    a = arm_angle(k)
    return cyl(30, pol(R_IN - 10, a, helix_z(a)), pol(R_OUT, a, helix_z(a)))


def arm_set(ks):
    return compound([arm(k) for k in ks])


def stay(k):
    a = arm_angle(k)
    return cyl(6, pol(R_OUT, a, helix_z(a)), pol(1120, a, 640))


def stay_set(ks):
    return compound([stay(k) for k in ks])


def push_post(k):
    a = 67.5 + 90 * k
    return cyl(40, pol(1120, a, 640), pol(1120, a, 1700))


def hydraulic_motor():
    return cq.Solid.makeCylinder(110, 210, V(0, 0, 120), V(0, 0, 1))


def brake():
    return cq.Solid.makeCylinder(170, 60, V(0, 0, 330), V(0, 0, 1))


def reducer():
    return cq.Solid.makeCylinder(200, 130, V(0, 0, 390), V(0, 0, 1))


def manifold():
    return box(1300, -150, 0, 1600, 150, 300)


def power_pack():
    return box(1900, -400, 0, 2700, 400, 600)


def control_post():
    return compound([cyl(60, (-1600, 1200, 0), (-1600, 1200, 950)), box(-1750, 1100, 950, -1450, 1300, 1100)])


def instrumentation_mast():
    return compound([cyl(40, (-2300, -1700, 0), (-2300, -1700, 3800)), box(-2600, -1740, 3700, -2000, -1660, 3780)])


# ---------------------------------------------------------------- parts: (CAD key, owner, label, builder)

PARTS = [
    ("fr-base-ring", "FR", "Base ring (floor drum)", base_ring),
    ("de-roller-track", "DE", "Roller track for the rotor ring", roller_track),
    ("fr-rotor-ring", "FR", "Rotor ring (turns with the screw)", rotor_ring),
]
PARTS += [(f"es-deck-sector-{k + 1}", "ES", f"Deck sector {k + 1} ({90 * k}-{90 * (k + 1)} deg)", lambda k=k: deck_sector(k)) for k in range(4)]
PARTS += [
    ("de-slewing-bearing", "DE", "Slewing bearing with slip ring", slewing_bearing),
    ("fr-hub", "FR", "Hub drum", hub),
    ("fr-mast-lower", "FR", "Mast, lower segment", lambda: mast(Z_DECK + 160, Z_MAST_JOINT_1)),
    ("de-mast-middle", "DE", "Mast, middle segment", lambda: mast(Z_MAST_JOINT_1, Z_MAST_JOINT_2)),
    ("uk-mast-upper", "UK", "Mast, upper segment", lambda: mast(Z_MAST_JOINT_2, Z_MAST_TOP)),
    ("fr-collar-plate", "FR", "Collar plate (strut node)", collar),
]
PARTS += [(f"de-strut-{k + 1}", "DE", f"Diagonal strut {k + 1}", lambda k=k: strut(k)) for k in range(4)]
PARTS += [
    ("es-arm-set-lower", "ES", "Radial arms 1-5 (middle mast)", lambda: arm_set(LOWER_ARMS)),
    ("es-arm-set-upper", "ES", "Radial arms 6-9 (upper mast)", lambda: arm_set(UPPER_ARMS)),
    ("fr-sail-segment-a", "FR", "Sail segment A (0-90 deg)", lambda: sail_segment(0.0)),
    ("de-sail-segment-b", "DE", "Sail segment B (90-180 deg)", lambda: sail_segment(90.0)),
    ("uk-sail-segment-c", "UK", "Sail segment C (180-270 deg)", lambda: sail_segment(180.0)),
    ("es-sail-segment-d", "ES", "Sail segment D (270-360 deg)", lambda: sail_segment(270.0)),
    ("uk-stay-set-lower", "UK", "Tension stays 1-5 with load cells", lambda: stay_set(LOWER_ARMS)),
    ("uk-stay-set-upper", "UK", "Tension stays 6-9 with load cells", lambda: stay_set(UPPER_ARMS)),
]
PARTS += [(f"fr-push-post-{k + 1}", "FR", f"Turning post {k + 1} (heritage handle)", lambda k=k: push_post(k)) for k in range(4)]
PARTS += [
    ("de-hydraulic-motor", "DE", "Hydraulic rotation motor", hydraulic_motor),
    ("uk-brake", "UK", "Hydraulic release brake", brake),
    ("de-reducer", "DE", "Planetary reducer", reducer),
    ("de-manifold", "DE", "Hydraulic manifold with solenoid valves", manifold),
    ("uk-power-pack", "UK", "Hydraulic power pack (reservoir, pump, filter)", power_pack),
    ("fr-control-post", "FR", "Control post (HMI, PLC)", control_post),
    ("uk-instrumentation-mast", "UK", "Instrumentation mast (anemometer, tilt, RPM)", instrumentation_mast),
]
