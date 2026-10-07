# CAD generators

One STEP AP242 file per part of each machine, written by `generate.py` into `stp/<product key>/<cad key>.stp`.
The infrastructure uploads `stp/` recursively to the CAD bucket, so a file's bucket key is
`cad/<product key>/<cad key>.stp`, the `cadFile` value of the part in `data/products/<product key>.json`.
The viewer loads these files; the PLM services and the file index only name them.

| Module | Product key | Parts | STEP total | Frame |
|---|---|---|---|---|
| `ornithopter.py` | `ornithopter` | 79 (FR 22, UK 19, DE 16, ES 22) | 1,864 KB | x aft from the frame nose, y to the pilot's right, z up with z = 0 on the keel beam axis; mm |
| `aerial_screw.py` | `aerial-screw` | 36 (FR 11, DE 11, UK 7, ES 7) | 528 KB | z up on the mast axis with z = 0 on the floor, x towards the hydraulic skid, y to its left; mm |
| `cart.py` | `cart` | 27 (FR 5, DE 11, UK 4, ES 7) | 780 KB | x forward in the direction of travel, y to the left, z up with z = 0 on the ground under the drive axle centre; mm |
| `antikythera.py` | `antikythera` | 33 (FR 8, DE 12, UK 7, ES 6) | 786 KB | x to the right seen from the front dial, y towards the back of the case, z up; x = z = 0 on the main drive axis b, y = 0 on the front face of the bronze front plate; mm |
| `steam_engine.py` | `steam-engine` | 28 (FR 6, DE 9, UK 6, ES 7) | 592 KB | x from the boiler towards the flywheel, y across the engine with the flywheel on the negative side, z up with z = 0 on the floor; mm |
| `glider.py` | `glider` | 83 (FR 12, DE 17, UK 10, ES 44) | 1,824 KB | x aft from the nose of the frame (the front of the bumper bow) at the centreline, y to the pilot's right, z up with z = 0 on the top face of the rib hub fittings; mm |
| `cubesat.py` | `cubesat` | 26 (FR 5, DE 8, UK 9, ES 4) | 719 KB | z along the rails with z = 0 on the outer face of the -Z end plate (the face inserted first into the dispenser), x and y from the axis of the rail square; mm |
| `difference_engine.py` | `difference-engine` | 192 (FR 58, DE 59, UK 41, ES 34) | 2,223 KB | x along the row of columns from the output end, y towards the output apparatus, z up with z = 0 on the floor; mm |
| `wind_turbine.py` | `wind-turbine` | 176 (FR 35, DE 57, UK 42, ES 42) | 2,358 KB | z up with z = 0 at the tower base flange, x downwind along the nacelle axis, y to the left seen from upwind; mm |
| `rover.py` | `rover` | 110 (FR 18, DE 25, UK 32, ES 35) | 1,692 KB | x forward in the direction of travel, y to the left, z up with z = 0 on the ground under the wheel contact points; mm |

`geometry.py` holds the primitives both modules share (boxes, cylinders, prisms, rings, annular sectors, swept
arcs, cords as thin cylinders, compounds for parts with several bodies), the validity check and the STEP writer.
Every part is analytic solids without a boolean, because STEP text costs about 3 KB per face whatever the surface
and a cut multiplies faces; the two exceptions are the ruled lofts of the ornithopter's linen panels and the Frenet
sweep of the aerial screw's helical sail, both 6-face bodies. Each shape and each of its bodies must pass `isValid()`
before it is written.

## Ornithopter (`ornithopter.py`)

The prone-pilot flying machine of Paris Manuscript B, folio 74v (Milan, about 1487-1490), built as an instrumented
ground demonstrator: a ladder frame with a pilot cradle, hand crank and windlass drum ahead of the chest, foot
stirrups aft, a head hoop steering a tail plane on a boom, and two bat wings, each a hinged two-piece spar (inner spar
and swept outer "finger" joined at a knuckle) with six curved cane ribs, each seated in a rib shoe on the spar (one
STEP placed six times), linen panels and a raw silk lacing cord; a return cord per wing raises it from the stirrups,
as the folio's text prefers. The crank is an assembly of shaft, two arms with their handles and two bearing posts; it
drives the drum through a lantern pinion of 8 staves and a peg wheel of 32 pegs at module 12 mm, the drum on its own
axle 240 mm aft of the crank. Modern items for the rig: a hydraulic actuator per wing root fed by a frame-mounted
power pack, an instrumentation pod, a sensor harness per wing and a tail trim servo; a water ballast tank under the
keel trims the centre of gravity through a bottle on the tail boom and fills and drains at a ground service panel; a
ballast stowage bay with its floor panel and leather liner stands on the keel aft of the cradle and holds four 5 l
ballast cans; laced access flaps at WS 1500 and WS 3600 carry grease lines to the actuator rod end and the drive cord
shackle; a ground handling wheel set of wheel, tyre and brake, one assembly placed under each skid, takes its brake
line and nitrogen from the ground service panel; a pilot harness of two straps holds the pilot to the cradle. The left
wing is the UK workshop's, the right wing the German one's, the frame the French integrator's and the crank, pedals,
cords and tail the Spanish one's.

Sources:

- Folio scan (public domain, Web Gallery of Art reproduction): https://commons.wikimedia.org/wiki/File:Leonardo_da_vinci,_Drawing_of_a_flying_machine.jpg
  gives the layout: the hinged spar with a ladder-truss inner section, the ribs hanging aft, the pilot frame, crank,
  stirrups, head hoop and leaf-shaped tail.
- Working model of the folio in the Museo Galileo, Florence (167 x 490 x 1100 cm):
  https://brunelleschi.imss.fi.it/genscheda.asp?appl=LIR&chiave=100934&xsl=modello gives the envelope: span 11.0 m,
  length 4.9 m, height 1.67 m.
- Scale model in the Museo Leonardiano di Vinci: https://museoleonardiano.it/opera/macchina-volante/ confirms the
  stirrup-and-cord wing drive.
- Paris Manuscript B, ff. 74v and 77r, Péladan translation:
  https://fr.wikisource.org/wiki/L%C3%A9onard_de_Vinci_:_les_14_manuscrits_de_l%E2%80%99Institut_de_France/Texte_entier
  gives the trial over a lake (74v) and wheels and pinions multiplying force (77r), the nearest gear note.
- Codex on the Flight of Birds, ff. 5 and 7: https://en.wikipedia.org/wiki/Codex_on_the_Flight_of_Birds (digitised:
  https://www.loc.gov/item/2021668201/) gives the tanned leather of the harness (f. 7) and the pilot balancing as in a
  boat (f. 5), the balance the ballast trims.

Dimensions adopted (mm): wing tips at y +/-5500, wing root at y +/-700, knuckle at y +/-3000, frame x 0 to 3600 with
the tail plane to 4900, skid underside at z -580, control post top at 1090, rib chord 2400 at the root, 1700 at the
knuckle, 500 at the tip. Inferred, not in any source: inner dihedral 8 degrees, finger dihedral 10 degrees and sweep
12 degrees, rib camber, member sections, the crank's division into parts, the gear stage's teeth, modules and
positions, the return cords' path, the pilot harness, the ballast system, the stowage bay and its cans, the rib shoes
and lacing cords, the access flaps, the wheel set and every service figure, and the position of every modern item.

## Aerial screw (`aerial_screw.py`)

The aerial screw of Paris Manuscript B, folio 83v (about 1489), built as a museum-scale working replica with a
hydraulic rotation drive: a base ring and roller track carrying a rotor ring, a timber deck in four sectors, a
three-segment mast on a hub and slewing bearing, one left-handed turn of helical sail in four quarter segments, nine
radial arms in two sets and nine tension stays in two sets, four diagonal struts to a collar plate, four turning
posts (the heritage handles), and the drive: hydraulic motor, planetary reducer, release brake, manifold and power
pack, with a control post and an instrumentation mast beside the machine. The sail segments, the mast segments and
the drive are split across the four workshops; the arms and deck are Spanish, the stays and instrumentation British.

Sources:

- Folio scan (public domain): https://commons.wikimedia.org/wiki/File:Leonardo_da_Vinci_helicopter.jpg (700 x 548 px
  crop of the folio, Institut de France); proportions read on it are the `DRAWING` pixel coordinates in the module:
  sail diameter 605 px, helix pitch 97.5 px, mast height above the platform 380 px, platform diameter 290 px.
- https://en.wikipedia.org/wiki/Leonardo%27s_aerial_screw for the folio reference and the radius of about 5 m.
- The note on the folio gives 8 braccia from the circumference to the centre, a radius; with the Florentine braccio
  of 583.6 mm and a 1:2 replica the sail diameter is 4669 mm.

Dimensions derived (mm): pitch 752 per turn, deck top at z 600, deck radius 1119, collar plate at z 1719, sail from
z 2182 to 2934, mast top at 3532. The sail is a 20 mm ribbon from r 120 to r 2334.

## Self-propelled cart (Codex Atlanticus, f. 812r) (`cart.py`)

Self-propelled cart of the Codex Atlanticus, folio 812r (about 1478-1480), built today as a full-size working reconstruction: 27 parts, one STEP file each.

Frame (mm): x forward in the direction of travel, y to the left, z up with z = 0 on the ground; the origin is on the ground under the centre of the drive axle. Length 1700 (drive wheel front at x 500, steering wheel rear at x -1200), width 1420 over the drive wheels (centre planes at y +/-680), spring shaft heads at z 740.

## Antikythera mechanism, simplified interpretation (`antikythera.py`)

Antikythera mechanism, simplified interpretation, built today as a museum-scale bronze model in a wooden case: 33 parts, one STEP file each.

Frame (mm): x to the right as seen from the front dial, y towards the back of the case (depth), z up; x = 0 and z = 0 on the main drive axis b, y = 0 on the front face of the bronze front plate. Case 180 x 96 x 340 (x, y, z): front door frame at y -16, back door frame to y 80, side frames at x +/-90, top and bottom rails at z +/-170. The casing is open so the mechanism shows: each side is a frame of four rails (the right side has a fifth, the crank bearer), the top and bottom are two rails each, the doors are frames, and the two bronze plates are pierced with circular openings between their arbor seats; every opening is an inner wire of one extruded face, not a boolean.

## Watt rotative beam engine, simplified (1788 type) (`steam_engine.py`)

Watt rotative beam engine of the 1788 type, simplified and built today as a double-acting demonstrator with a centrifugal governor: 28 parts, one STEP file each. The three purchased O-rings (cylinder cover, throttle valve spindle, governor gland) are envelope annuli of the ring's inner diameter and cross-section.

Frame (mm): x along the engine axis from the boiler towards the flywheel, y across the engine with the flywheel on the negative side, z up with z = 0 on the floor. The bed frame top is at z 300. The cylinder stands at x 600, the beam pivots at x 3100, z 4000, the crankshaft turns at x 5600, z 2100 and the boiler lies from x -3000 to -1200. Every moving part is drawn at mid-stroke: the piston at mid-height, the beam horizontal, the crank horizontal towards +x.

## Lilienthal Normalsegelapparat (1894), authored reconstruction (`glider.py`)

Lilienthal Normalsegelapparat (1894), an authored reconstruction: 83 unique part numbers, one STEP file each, placed at their reference occurrence (the left wing, the first rib of a set, the front hanger); the placements of the bill-of-materials lines of `data/products/glider.json` put its other occurrences, relative to that one. Mirror-image left and right parts (ribs, hub fittings, wire sets, coverings, arm bars and pads) are distinct part numbers.

Frame (mm): x aft from the nose of the frame (the front of the bumper bow) at the centreline, y to the pilot's right, z up with z = 0 on the top face of the rib hub fittings, 8 mm above the axis plane of the cross rod (z -28). Envelope from the public figures for the type: span 6.7 m (front rib tips at y +/-3349), wing area about 13 m2 (12.6 m2 of authored covering), length 5.3 m (x 0 to 4910), empty mass about 20 kg (20.6 kg over the bill of materials).

Layout: a crossed frame of a willow hoop (the opening the pilot hangs in) and a bamboo cross rod, a willow bumper bow in front and a bamboo tail boom aft; on each end of the cross rod a hub fitting with eight pockets, from which eight radial ribs diverge like the fingers of a bat wing, each turning on its own pin so the wing folds back to the boom; a cotton covering stretched over the ribs, a hemp cord through the rib tips along the trailing edge, a front tension wire from the first rib to the hoop holding the wing spread, four profile rails slid over each wing; a kingpost above and a lower post below the cross rod, each anchoring three steel wires per wing to the ribs 1, 4 and 7; a fixed vertical fin on a post, and a tail plane hinged at its leading edge that swings upward freely and rests on a stop at the fin post when it swings down. The pilot hangs by the forearms on two padded arm bars below the frame.

Interpretation choices, none of them taken from a drawing: eight ribs per wing at angles -5 to 85 degrees from the span direction, lengths 2960 to 2400 mm; the first rib bamboo (Ø 36), the others split willow (Ø 22); each rib one circular arc whose mid rise is its chordwise share of a 1/13 camber plus its spanwise share of a 1/25 arch, its tip rising 8 % of its spanwise reach; the covering one ruled loft over the eight rib arcs; the hoop a 760 mm circle; the tail plane a lens of two circular arcs with a slot for the fin; the wire anchors eye plates on the posts; a willow bearing block on the forward face of each rib root, where it rests on its neighbour when folded, which makes left and right ribs mirror parts. Authored additions for the demonstrator: the body sling, the airspeed indicator and its vane sensor, the nose ballast, the tail return spring and the front wire latch springs.

The ribs, profile rails, frame woodwork, wire sets, turnbuckles and castings are the Spanish workshop's; the hub fittings, pins, posts, hinges and machined clips the German one's; the coverings, cords, springs, eyelets, tacks and binding the French one's; the pilot harness, instruments and ballast the British one's.

Sources (layout and figures only):

- Otto Lilienthal, Flying machine, US patent 544,816 (1895): https://patents.google.com/patent/US544816A/en
- Normalsegelapparat: https://de.wikipedia.org/wiki/Normalsegelapparat and https://en.wikipedia.org/wiki/Lilienthal_Normalsegelapparat
- National Air and Space Museum, Lilienthal glider: https://airandspace.si.edu/collection-objects/lilienthal-glider/nasm_A19060001000
- Otto-Lilienthal-Museum Anklam, aeroplane models: https://lilienthal-museum.de/olma/e213.htm

## 1U CubeSat, authored demonstrator (`cubesat.py`)

Generic 1U CubeSat, an authored demonstrator inside the envelope of the CubeSat Design Specification Rev. 14.1 (Cal Poly, 2022): 26 parts, one STEP file each. No commercial CubeSat product is reproduced; everything inside the envelope is this module's own design.

Frame (mm): z along the rails, z = 0 on the outer face of the -Z end plate (the face inserted first into the dispenser), z = 100 on the outer face of the +Z end plate; x and y from the axis of the rail square, so the body is |x|, |y| <= 50. The four rails are 8.5 mm square (CDS 2.2.5, minimum rail width) at the corners of the 100 mm square and run from z -6.75 to 106.75, the 113.5 mm overall length of a 1U; their ends are the 6.5 x 6.5 mm contact pads of CDS 2.2.8. Nothing outside the rail planes protrudes more than 6.5 mm (CDS 2.2.3): side solar panel boards 1.6 mm, the antenna module 4 mm.

## Difference engine No. 2 design, authored reconstruction (`difference_engine.py`)

Difference engine of the No. 2 design (1847-49), an authored reconstruction: 192 unique part numbers, one STEP file each, of a machine of about 7,500 occurrences in the bill of materials of `data/products/difference-engine.json`. Each part is placed at its reference occurrence: the result column, digit 0, the first gap between columns, the first cam and the first type wheel; the placements of the bill-of-materials lines put the other columns, digits, gaps, cams and wheels relative to it. The design basis is the only thing taken from the sources: eight columns of 31 figure wheels (the result column and seven orders of difference), a cam-driven adding and carry mechanism turned by a hand crank, a printing and stereotyping output apparatus, about 3.4 m long, 2.1 m high, 0.5 m deep across the calculating section and about 5 t. Every shape and dimension is this module's own.

Frame (mm): x along the row of columns from the output end, y towards the output apparatus (the operator's side), z up with z = 0 on the floor under the levelling feet. The result column stands at x 450 and the seventh-difference column at x 2340 (pitch 270). Figure wheels are plain rings at pitch 50 up each column, digit 0 at z 420. The camshaft is at (2730, -60) with 14 cams of radii 70 to 135 at pitch 100, and the driving train runs from x 2880 to the crank grip at x 3340. The output apparatus stands in front of the result column between y 300 and 950. Floor to top rail is 2090.

## 5 MW reference-class wind turbine, authored (`wind_turbine.py`)

5 MW reference-class wind turbine, authored: 176 unique part numbers, one STEP file each, placed at their reference occurrence (blade 1, the lowest tower joint, the left-hand side); the placements of the bill-of-materials lines of `data/products/wind-turbine.json` put blades 2 and 3 and the bolt circles, relative to it. The five purchased O-rings (pitch cylinder rod, pitch valve block, yaw brake) are envelope annuli of the ring's inner diameter and cross-section. The top-level figures are the public reference figures of the NREL 5-MW reference turbine (NREL/TP-500-38060, 2009): 5 MW, rotor diameter 126 m, hub height 90 m, three blades of 61.5 m, gearbox ratio 97:1, tower 87.6 m tapering from 6.0 m to 3.87 m in diameter. Every part below that level is this module's own design.

Frame (mm): z up with z = 0 at the tower base flange, x downwind along the nacelle axis, y to the left seen from upwind. The main shaft axis is horizontal at z 90000 and the hub centre is at x -5000; blade 1 points straight up with its tip at z 153000. The tower is four sections from z 200 to 87400 with joint flanges at 21900, 43800 and 65700, the yaw bearing runs from 87600 to 87900, and the nacelle cover spans x -3200 to 9800, y +/-3000, z 87800 to 93000, with the nose cone to x -9200. The onshore variant stands on an anchor cage at z -2200; the offshore variant stands on a transition piece and monopile down to z -56000. Both variants are drawn in this one frame.

## Six-wheel rocker-bogie rover, authored (`rover.py`)

Six-wheel rocker-bogie rover built mostly from purchased parts: 110 unique part numbers, one STEP file each, placed at the part's reference occurrence (front-left wheel station, left side, lowest bearing). The bill of materials of `data/products/rover.json` gives the quantities and, on its lines, the placements of the other occurrences relative to the reference one: the viewer draws every placed occurrence.

Frame (mm): x forward, y to the left, z up with z = 0 on the ground under the wheel contact points. Wheel stations are at x 330 (front, steered), -30 (middle) and -330 (rear, steered), wheel centre planes at y +/-340, axles at z 100, wheel diameter 200. The suspension tubes lie in the planes y +/-240, with the rocker pivots at (0, +/-240, 320) and the bogie pivots at (-180, +/-240, 230). The body is 560 x 400 from z 220 to 380, the differential bar is at z 420 and the camera housing top at z 660. Purchased items (gearmotors, bearings, boards, battery, fasteners) are simple envelope solids; left and right rockers, bogies, knuckles, side panels and middle motor mounts are mirror images and distinct part numbers.

## Running

Any Python environment with CadQuery 2.x works, for example:

```sh
python3.12 -m venv modules/cad/.venv
modules/cad/.venv/bin/pip install cadquery==2.8.0
modules/cad/.venv/bin/python modules/cad/generate.py
```

The run takes about three seconds plus the CadQuery import, prints every file with its size, face count and bounding
box, and ends with the part count and STEP total per product. Output is deterministic: a STEP file is a function of
its geometry alone. Beside the files of each product, `stp/<product key>/bounds.json` holds per CAD key the exact box of
the part in mm in the product frame and the sha256 of its STEP file; `data/generate.py` computes every part's station
span from it and refuses a box whose hash is not that of the STEP file on disk. `data/generate.py` requires every STEP
file named by `data/products/*.json` to exist here, so run this generator first.
