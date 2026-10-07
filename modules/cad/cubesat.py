# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Generic 1U CubeSat, an authored demonstrator inside the envelope of the CubeSat Design Specification Rev. 14.1
(Cal Poly, 2022): 26 parts, one STEP file each. No commercial CubeSat product is reproduced; everything inside the
envelope is this module's own design.

Frame (mm): z along the rails, z = 0 on the outer face of the -Z end plate (the face inserted first into the
dispenser), z = 100 on the outer face of the +Z end plate; x and y from the axis of the rail square, so the body is
|x|, |y| <= 50. The four rails are 8.5 mm square (CDS 2.2.5, minimum rail width) at the corners of the 100 mm square
and run from z -6.75 to 106.75, the 113.5 mm overall length of a 1U; their ends are the 6.5 x 6.5 mm contact pads of
CDS 2.2.8. Nothing outside the rail planes protrudes more than 6.5 mm (CDS 2.2.3): side solar panel boards 1.6 mm,
the antenna module 4 mm.

Interpretation choices (the specification gives the envelope only): the frame is two ring frames of four bars each,
one at each end between the rails; the end plates are 83 mm squares inside those rings; the electronics are three
boards of the PC/104 footprint with the corners notched for the rails, stacked at the 15.24 mm (0.6 in) PC/104 pitch
on one set of twelve threaded standoffs; the battery is two 26650 cells in a cast tray on the -Z end plate; the
radiator is the -Z face (white coating) and the +Z end plate carries a black coating inside; the UHF antenna module
sits on the -X face, the only face without a solar panel board; the rail edge radius of CDS 2.2.7 and the fastener
holes are not modelled (analytic solids without booleans, see geometry.py).

The part keys here are the CAD keys of data/products/cubesat.json (cad/cubesat/<key>.stp); the PLM part ids live in
that file.
"""
import cadquery as cq

from geometry import V, box, compound, cyl

KEY = "cubesat"
NAME = "1U CubeSat, authored demonstrator"
FRAME = ("z along the rails with z = 0 on the outer face of the -Z end plate, x and y from the rail-square axis; mm. "
         "Body 100 mm cube, rails 8.5 mm square from z -6.75 to 106.75")

# ---------------------------------------------------------------- envelope (CDS Rev. 14.1)

HALF = 50.0                 # half the 100 mm body
RAIL_W = 8.5                # CDS 2.2.5 minimum rail width
RAIL_IN = HALF - RAIL_W     # 41.5: inner faces of the rails
RAIL_EXT = 6.75             # rail end beyond each end plate: 113.5 mm overall
Z_RAIL0, Z_RAIL1 = -RAIL_EXT, 100.0 + RAIL_EXT
PLATE_T = 1.5               # end plates
RING_H = 4.0                # ring frame bars
PANEL_T = 1.6               # printed circuit boards and solar panel boards
COAT_T = 0.1                # thermal coatings as applied layers

# ---------------------------------------------------------------- stack (PC/104 pitch 15.24 mm)

PITCH = 15.24
Z_EPS = 32.0                # EPS board underside, above the battery tray
Z_OBC = Z_EPS + PITCH       # 47.24
Z_UHF = Z_OBC + PITCH       # 62.48
STANDOFF_XY = 36.0          # the four standoff columns at (+/-36, +/-36)
STANDOFF_R = 2.5            # M3 threaded spacers, 5 mm across

# ---------------------------------------------------------------- battery tray (ES) and cells (UK)

TRAY_X, TRAY_Y, TRAY_WALL = 36.0, 30.5, 2.0
CELL_R, CELL_HALF_L, CELL_Y = 13.0, 32.5, 14.5   # two 26650 cells along x
Z_TRAY0 = PLATE_T                                 # tray base on the -Z end plate
Z_CELL = Z_TRAY0 + TRAY_WALL + CELL_R             # 16.5, cell axis
Z_WALL1 = Z_CELL + CELL_R                         # 29.5, wall top


def corners():
    return [(1, 1), (1, -1), (-1, 1), (-1, -1)]


# ---------------------------------------------------------------- DE structure

def rail(sx, sy):
    x0, x1 = sorted((sx * RAIL_IN, sx * HALF))
    y0, y1 = sorted((sy * RAIL_IN, sy * HALF))
    return box(x0, y0, Z_RAIL0, x1, y1, Z_RAIL1)


def ring_frame(z0):
    """Four bars between the rails, flush with their inner faces."""
    z1 = z0 + RING_H
    return [box(-RAIL_IN, RAIL_IN, z0, RAIL_IN, HALF, z1), box(-RAIL_IN, -HALF, z0, RAIL_IN, -RAIL_IN, z1),
            box(RAIL_IN, -RAIL_IN, z0, HALF, RAIL_IN, z1), box(-HALF, -RAIL_IN, z0, -RAIL_IN, RAIL_IN, z1)]


def frame():
    return compound(ring_frame(0.0) + ring_frame(100.0 - RING_H))


def end_plate(z0):
    return box(-RAIL_IN, -RAIL_IN, z0, RAIL_IN, RAIL_IN, z0 + PLATE_T)


def standoff_set():
    """Twelve M3 spacers: end plate to EPS, EPS to OBC, OBC to UHF, at the four stack columns."""
    spans = [(PLATE_T, Z_EPS), (Z_EPS + PANEL_T, Z_OBC), (Z_OBC + PANEL_T, Z_UHF)]
    return compound([cyl(STANDOFF_R, (sx * STANDOFF_XY, sy * STANDOFF_XY, z0), (sx * STANDOFF_XY, sy * STANDOFF_XY, z1))
                     for z0, z1 in spans for sx, sy in corners()])


# ---------------------------------------------------------------- UK electronics

def stack_board(z0):
    """PC/104 footprint (90 x 96) with the corners notched 3.5 x 6.5 for the rails: 12 sides, 14 faces."""
    a, b, c, d = 45.0, RAIL_IN, 48.0, RAIL_IN   # x half-width, notch x, y half-width, notch y
    pts = [(-a, -d), (-b, -d), (-b, -c), (b, -c), (b, -d), (a, -d), (a, d), (b, d), (b, c), (-b, c), (-b, d), (-a, d)]
    return cq.Workplane("XY", origin=(0, 0, z0)).polyline(pts).close().extrude(PANEL_T).val()


def battery_pack():
    return compound([cyl(CELL_R, (-CELL_HALF_L, sy * CELL_Y, Z_CELL), (CELL_HALF_L, sy * CELL_Y, Z_CELL)) for sy in (1, -1)])


def side_panel(axis, sign):
    """Solar panel board on a side face, outside the rail plane, screwed to the two rails of that face."""
    lo, hi = sign * HALF, sign * (HALF + PANEL_T)
    lo, hi = sorted((lo, hi))
    if axis == "x":
        return box(lo, -49.0, 0.0, hi, 49.0, 100.0)
    return box(-49.0, lo, 0.0, 49.0, hi, 100.0)


def top_panel():
    return box(-RAIL_IN, -RAIL_IN, 100.0, RAIL_IN, RAIL_IN, 100.0 + PANEL_T)


def antenna_module():
    """Stowed tape antennas in a flat housing on the -X face, on the two -X rails."""
    return box(-HALF - 4.0, -48.0, 60.0, -HALF, 48.0, 90.0)


def harness():
    """Power loom from the EPS edge up the -X channel to the UHF board, and the antenna coax; 1.5 mm radius."""
    loom = [(-35.0, 10.0, Z_EPS + PANEL_T + 1.5), (-47.5, 10.0, Z_EPS + PANEL_T + 1.5), (-47.5, 10.0, Z_UHF + PANEL_T + 1.5),
            (-30.0, 30.0, Z_UHF + PANEL_T + 1.5)]
    coax = [(-47.5, 10.0, Z_UHF + PANEL_T + 1.5), (-47.5, 0.0, 75.0), (-HALF, 0.0, 75.0)]
    return compound([cyl(1.5, a, b) for pts in (loom, coax) for a, b in zip(pts, pts[1:])])


# ---------------------------------------------------------------- FR thermal

def radiator_coating():
    """White coating of the -Z face, the applied layer on the end plate's outer face."""
    return box(-RAIL_IN, -RAIL_IN, -COAT_T, RAIL_IN, RAIL_IN, 0.0)


def inner_coating():
    """Black coating inside the +Z end plate."""
    return box(-RAIL_IN, -RAIL_IN, 100.0 - PLATE_T - COAT_T, RAIL_IN, RAIL_IN, 100.0 - PLATE_T)


def strap_eps():
    """Copper braid from the -Z ring frame's +X bar up to the EPS board underside."""
    return box(43.0, -25.0, RING_H, 44.0, -15.0, Z_EPS)


def insulation_blanket():
    """Multi-layer blanket closing the battery tray under the EPS board."""
    return box(-37.0, -31.5, Z_WALL1, 37.0, 31.5, Z_WALL1 + 1.5)


def strap_uhf():
    """Copper braid from the UHF transceiver's amplifier pad to the -X+Y rail."""
    return box(-45.0, 36.0, Z_UHF + PANEL_T, -44.0, RAIL_IN, 84.0)


# ---------------------------------------------------------------- ES brackets and housings

def board_bracket(sy):
    """L-angle on a +X rail's inner face, its foot on the UHF board."""
    z0 = Z_UHF + PANEL_T
    leg_y0, leg_y1 = sorted((sy * 39.5, sy * RAIL_IN))
    foot_y0, foot_y1 = sorted((sy * 29.5, sy * 39.5))
    return compound([box(RAIL_IN, leg_y0, z0, HALF, leg_y1, 80.0), box(RAIL_IN, foot_y0, z0, HALF, foot_y1, z0 + 2.0)])


def battery_enclosure():
    """Cast tray: base on the -Z end plate, two side walls along the cells."""
    base = box(-TRAY_X, -TRAY_Y, Z_TRAY0, TRAY_X, TRAY_Y, Z_TRAY0 + TRAY_WALL)
    walls = []
    for sy in (1, -1):
        y0, y1 = sorted((sy * (TRAY_Y - TRAY_WALL), sy * TRAY_Y))
        walls.append(box(-TRAY_X, y0, Z_TRAY0 + TRAY_WALL, TRAY_X, y1, Z_WALL1))
    return compound([base] + walls)


def switch_bracket():
    """L-angle on the -X-Y rail's inner face, standing on the -Z ring frame, carrying the deployment switch."""
    leg = box(-RAIL_IN, -HALF, RING_H, -39.5, -RAIL_IN, 20.0)
    shelf = box(-RAIL_IN, -RAIL_IN, RING_H, -31.5, -39.5, RING_H + 2.0)
    return compound([leg, shelf])


# ---------------------------------------------------------------- parts: (CAD key, owner, label, builder)

RAILS = [((1, 1), "px-py", "+X+Y"), ((1, -1), "px-ny", "+X-Y"), ((-1, 1), "nx-py", "-X+Y"), ((-1, -1), "nx-ny", "-X-Y")]

PARTS = [("de-frame", "DE", "1U frame (two ring frames)", frame)]
PARTS += [(f"de-rail-{key}", "DE", f"Rail {label}", lambda c=c: rail(*c)) for c, key, label in RAILS]
PARTS += [
    ("de-end-plate-nz", "DE", "End plate -Z", lambda: end_plate(0.0)),
    ("de-end-plate-pz", "DE", "End plate +Z", lambda: end_plate(100.0 - PLATE_T)),
    ("de-standoff-set", "DE", "Stack standoff set (12 spacers)", standoff_set),
    ("uk-eps-board", "UK", "EPS board", lambda: stack_board(Z_EPS)),
    ("uk-battery-pack", "UK", "Battery pack (two 26650 cells)", battery_pack),
    ("uk-solar-panel-px", "UK", "Solar panel board +X", lambda: side_panel("x", 1)),
    ("uk-solar-panel-py", "UK", "Solar panel board +Y", lambda: side_panel("y", 1)),
    ("uk-solar-panel-pz", "UK", "Solar panel board +Z", top_panel),
    ("uk-obc-board", "UK", "On-board computer board", lambda: stack_board(Z_OBC)),
    ("uk-uhf-transceiver", "UK", "UHF transceiver board", lambda: stack_board(Z_UHF)),
    ("uk-uhf-antenna", "UK", "UHF antenna module (stowed)", antenna_module),
    ("uk-harness", "UK", "Harness (power loom and antenna coax)", harness),
    ("fr-radiator-coating", "FR", "Radiator coating, -Z face", radiator_coating),
    ("fr-inner-coating", "FR", "Black coating, +Z end plate inside", inner_coating),
    ("fr-thermal-strap-eps", "FR", "Thermal strap, EPS to frame", strap_eps),
    ("fr-insulation-blanket", "FR", "Insulation blanket, battery", insulation_blanket),
    ("fr-thermal-strap-uhf", "FR", "Thermal strap, UHF transceiver to rail", strap_uhf),
    ("es-board-bracket-py", "ES", "Board bracket +Y", lambda: board_bracket(1)),
    ("es-board-bracket-ny", "ES", "Board bracket -Y", lambda: board_bracket(-1)),
    ("es-battery-enclosure", "ES", "Battery enclosure (cast tray)", battery_enclosure),
    ("es-switch-bracket", "ES", "Deployment switch bracket", switch_bracket),
]
