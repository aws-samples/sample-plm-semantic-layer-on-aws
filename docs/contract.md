# Integration contract

The shared agreements between the PLM services, the virtual graphs, the link
store, the query service, the agent and the front end. Every component follows
this file; `tests/smoke.mjs` asserts the deployed system against it.

## Products and owners

Several products live in one deployment. Each is one file,
`data/products/<key>.json`, in the same schema: `product` (key, name), `frame`
(the coordinate frame of its CAD and feature positions), `parts`, `interfaces`
with their plug, fastener and hydraulic coupling pairs, `sparePlugs` and
`seededDefects`, `seededReferenceDefects`, and in `extended` each site's bill of materials (see "Bill of
materials") and its external references (see "External references"). Positions are millimetres in the product's own frame; the UK
PLM stores them in inches and the layer converts.

No site holds a complete product. Each site holds its own slice as a tree under
its own site kit, in its own relational idiom; the product root exists only in
the semantic layer, which reads the four idioms as one relation and answers the
whole bill of materials, its roll-ups and where a part is used in it.

| Product key | Name | Parts | Interfaces | Features |
|---|---|---|---|---|
| `ornithopter` | Ornithopter ground demonstrator (Paris Manuscript B, f. 74v) | 76 (FR 22, DE 15, UK 18, ES 21), 6 supplier-built | `IF-01` to `IF-47` and `IF-150` to `IF-178` (76), 25 between parts of different PLMs | 190: 36 plugs, 126 fasteners, 28 hydraulic couplings |
| `aerial-screw` | Aerial screw working replica (Paris Manuscript B, f. 83v) | 36 (FR 11, DE 11, UK 7, ES 7), 2 supplier-built | `IF-48` to `IF-99` (52), 38 between parts of different PLMs | 172: 72 plugs, 90 fasteners, 10 hydraulic couplings |
| `cart` | Self-propelled cart (Codex Atlanticus, f. 812r) | 27 (FR 5, DE 11, UK 4, ES 7), 2 supplier-built | `IF-100` to `IF-119` (20), 11 between parts of different PLMs | 45: 1 plug, 44 fasteners, 0 hydraulic couplings |
| `antikythera` | Antikythera mechanism, simplified interpretation | 33 (FR 8, DE 12, UK 7, ES 6), 2 supplier-built | `IF-100` to `IF-120` (21), 16 between parts of different PLMs | 44: 2 plugs, 42 fasteners, 0 hydraulic couplings |
| `steam-engine` | Watt rotative beam engine, simplified (1788 type) | 28 (FR 6, DE 9, UK 6, ES 7), 2 supplier-built | `IF-01` to `IF-24` (24), 11 between parts of different PLMs | 54: 4 plugs, 38 fasteners, 12 hydraulic couplings |
| `glider` | Lilienthal Normalsegelapparat (1894), authored reconstruction | 83 (FR 12, DE 17, UK 10, ES 44), 6 supplier-built | `IF-100` to `IF-147` (48), 40 between parts of different PLMs | 95: 3 plugs, 92 fasteners, 0 hydraulic couplings |
| `cubesat` | 1U CubeSat, authored demonstrator | 26 (FR 5, DE 8, UK 9, ES 4), 1 supplier-built | `IF-100` to `IF-124` (25), 19 between parts of different PLMs | 52: 16 plugs, 36 fasteners, 0 hydraulic couplings |
| `difference-engine` | Difference engine No. 2 design, authored reconstruction | 192 (FR 58, DE 59, UK 41, ES 34), 7 supplier-built | `IF-100` to `IF-144` (45), 20 between parts of different PLMs | 91: 3 plugs, 88 fasteners, 0 hydraulic couplings |
| `wind-turbine` | 5 MW reference-class wind turbine, authored | 166 (FR 35, DE 57, UK 42, ES 32), 3 supplier-built | `IF-100` to `IF-141` without `IF-126` and `IF-127` (40), 23 between parts of different PLMs | 82: 22 plugs, 50 fasteners, 10 hydraulic couplings |
| `rover` | Six-wheel rocker-bogie rover, authored | 110 (FR 18, DE 25, UK 32, ES 35), 59 supplier-built | `IF-100` to `IF-144` (45), 19 between parts of different PLMs | 92: 20 plugs, 72 fasteners, 0 hydraulic couplings |

The owner of a part is the site PLM that holds its data; the French PLM
integrates the two flying machines, and on the eight further products each
workshop builds to its specialty. A supplier-built part is owned by the PLM that
integrates it and carries `atelier:builtBy "<supplier>"` in the file index
(all fictional, for example Forja del Tajo, Toledo and Sheaf Hydraulik Works, Sheffield).
Part ids and feature keys are unique across products. Interface ids are unique
within a product, so an interface is identified by its product and its id (two
products may both have an `IF-13`). Every tagged part belongs to exactly one
product.

Frames, as the core database states them under the product switcher:

- `ornithopter`: x aft from the frame nose, y to the pilot's right, z up with z = 0 on the keel beam axis; mm, the frame of modules/cad/ornithopter.py. Span 11.0 m (wing tips at y +/-5500), frame x 0 to 3600 with the tail plane to 4900, skid underside at z -580, control post top at 1090.
- `aerial-screw`: z up on the mast axis with z = 0 on the floor, x towards the hydraulic skid, y to its left; mm, the frame of modules/cad/aerial_screw.py. Sail diameter 4669 (a 1:2 replica of the folio's 8 braccia radius), deck top at z 600, mast top at 3532; the sail is one left-handed turn from z 2182 to 2934.
- `cart`: x forward in the direction of travel, y to the left, z up with z = 0 on the ground under the drive axle centre; mm, the frame of modules/cad/cart.py. Length 1700 (drive wheel front at x 500, steering wheel rear at x -1200), width 1420 over the drive wheels (centre planes at y +/-680), drive wheel diameter 1000 with the axle at z 500, spring shaft heads at z 740.
- `antikythera`: x to the right seen from the front dial, y towards the back of the case, z up; x = z = 0 on the main drive axis b, y = 0 on the front face of the bronze front plate; mm, the frame of modules/cad/antikythera.py. Case 180 x 96 x 340: front door at y -16, back door to y 80, plates at y 0 to 3 and 62 to 65; gears are plain discs at pitch diameter (module 0.55) in layers between y 6 and 58.5.
- `steam-engine`: x from the boiler towards the flywheel, y across the engine with the flywheel on the negative side, z up with z = 0 on the floor; mm, the frame of modules/cad/steam_engine.py. Bed frame top at z 300, cylinder axis at x 600, beam pivot at x 3100, z 4000, crankshaft at x 5600, z 2100, flywheel diameter 3500 centred at y -900, boiler from x -3000 to -1200; overall x -3000 to 7350, beam top at z 4250.
- `glider`: x aft from the nose of the frame (the front of the bumper bow) at the centreline, y to the pilot's right, z up with z = 0 on the top face of the rib hub fittings; mm, the frame of modules/cad/glider.py. Span 6.7 m (front rib tips at y +/-3349), hubs of the eight radial ribs per wing at (400, +/-400), cross rod axis at z -28, hoop of 760 mm centred at x 700, kingpost head at z 1050, lower post foot at z -720, tail boom to x 4850, tail plane hinge at x 3910, fin post at x 4000.
- `cubesat`: z along the rails with z = 0 on the outer face of the -Z end plate (the face inserted first into the dispenser), x and y from the axis of the rail square; mm, the frame of modules/cad/cubesat.py. Body 100 x 100 x 100 with |x|, |y| <= 50, rails 8.5 mm square at the corners from z -6.75 to 106.75 (113.5 overall), board stack at z 32, 47.24 and 62.48, solar panel boards 1.6 mm outside the +X, +Y and +Z faces, antenna module 4 mm outside the -X face.
- `difference-engine`: x along the row of columns from the output end, y towards the output apparatus, z up with z = 0 on the floor; mm. Result column at x 450, pitch 270, camshaft at x 2730, crank grip to x 3340, top rail at z 2090; the frame of modules/cad/difference_engine.py. Eight columns of 31 figure wheels at pitch 50 from z 420, camshaft with 14 cams at x 2730, driving train x 2880 to 3340, output apparatus x -160 to 680 in front (y 300 to 950); overall x -160 to 3340, y -410 to 950, z 0 to 2090.
- `wind-turbine`: z up with z = 0 at the tower base flange, x downwind along the nacelle axis, y to the left seen from upwind; mm, the frame of modules/cad/wind_turbine.py. Hub centre at (-5000, 0, 90000), main shaft axis horizontal at z 90000, blade 1 up with its tip at z 153000, tower sections from z 200 to 87400 with joint flanges at 21900, 43800 and 65700, yaw bearing 87600 to 87900, nacelle cover x -3200 to 9800, y +/-3000, z 87800 to 93000, nose cone to x -9200; onshore anchor cage at z -2200, offshore transition piece and monopile from z -200 down to -56000.
- `rover`: x forward in the direction of travel, y to the left, z up with z = 0 on the ground under the wheel contact points; mm, the frame of modules/cad/rover.py. Wheel stations at x 330 (front, steered), -30 (middle) and -330 (rear, steered), wheel centre planes at y +/-340, axles at z 100, wheel diameter 200; suspension tubes in the planes y +/-240 with the rocker pivots at (0, +/-240, 320) and the bogie pivots at (-180, +/-240, 230); body 560 x 400 from z 220 to 380, lid to 383, differential bar at z 420, camera housing top at z 660.

## Source of truth for seed data

`data/generate.py` reads every product file, checks the dataset against this
contract (unique ids, key length, features owned by the interface's parts, a
STEP (ISO 10303) file per part under `modules/cad/stp/<product key>/`, storage units per
PLM, classification vocabulary, each part's attributes in its PLM's idiom (see
"Part attributes"), the shape of each bill of materials (see "Bill of
materials"), the software and document items (see "Software and documents"),
the product's mass limit (see "Product mass limit"), exactly one position row without a unit in the
whole dataset, that the interface rules fail exactly on the interfaces each
product lists in `seededDefects`, and the reference and lifecycle rules exactly on
the references and dependencies it lists in `seededReferenceDefects` and
`seededLifecycleDefects`, see "Rules", the functional edges and gears and the meshModule rule
exactly on the meshes it lists in `seededMeshDefects`, see "Functional edges and
gears", and an English name, `nameEn`, for every
item of a site that does not write English), then writes the seed migration of each PLM
service and of the core service, `data/links.ttl`, `data/fileindex.ttl` and
`data/labels.ttl` (see "Where the additional metadata lives").
The seed is a repeatable Flyway migration (`R__products_seed.sql`): it empties
the tables it fills, and Flyway re-applies it after the versioned schema
migrations whenever its checksum changes, so a changed or added product reaches
a running deployment with a redeploy of the services.
Nobody edits those files by hand; output is deterministic. The run ends with the
figures per product: parts per PLM, supplier-built parts, interfaces and
cross-PLM interfaces, features by kind, spare plugs, assemblies, bill-of-materials
lines and part occurrences per site and in total, the software and document
items, the mass limit, the functional edges per flow kind and the gears, the pass / fail / not-evaluable tally of every profile in
`ontology/policy.json`, and the STEP total.

## Seeded defects

Seven defects on the ornithopter, seven interfaces fail, six of them between parts
of different PLMs; the aerial screw has none; each of the eight further products
carries four to seven, listed after the ornithopter's table.

| Interface | Rule | Data |
|---|---|---|
| `IF-13` FR right wing root carrier / DE right wing root fitting | `position` | plugs `FR-ORN-EMPL-R-001-J01` / `WURZ-R-61080-X01`: y 640.0 vs 644.3, 4.3 mm apart for a 2.0 mm tolerance per axis; the DE row is the outlier |
| `IF-03` FR instrumentation pod / UK left wing sensor harness | `connector` | plugs `FR-ORN-NACI-001-J01` / `PL 6200-01`: EN4165 vs EN3645, 22 pins each |
| `IF-05` UK left wing root fitting / UK left inner spar | `unit` | UK fastener `HL 6180-02` stored with `pos_uom` NULL: its position carries no unit |
| `IF-02` FR hydraulic power pack / UK left wing actuator | `hydraulic` | couplings `FR-ORN-GHYD-001-H01` / `HC 6190-01`: UK `rating` 5000 PSI (344.7 bar) vs FR `pression_bar` 206.8 |
| `IF-25` FR forward cross beam / ES left crank bearing post | `fastener` | fasteners `FR-ORN-TRAV-AV-001-F01` / `MANV-6010-R01`: FR `diametre_mm` 10 vs ES `diametro_mm` 12 |
| `IF-30` FR control post / ES tail trim servo | `orphan` | ES plug `SERV-6120-C03` declared on the interface with no FR mate; the spare FR plug `FR-ORN-PCMD-001-J03` sits at its position |
| `IF-154` FR ballast water tank / ES tail trim water bottle | `hydraulic` | couplings `FR-ORN-BALL-001-H03` / `DEPO-6130-A01`: ES `presion_bar` 43.5, the psi figure of the coupling's data sheet (3.0 bar), vs FR `pression_bar` 3.0 |

Seeded defects of the eight further products:

| Product | Interface | Rule | Data |
|---|---|---|---|
| `cart` | `IF-105` | `unit` | UK row stored with pos_uom NULL |
| `cart` | `IF-101` | `position` | y differs by 3.4 mm; the DE row is the outlier |
| `cart` | `IF-103` | `fastener` | DE diameter 10 mm vs ES 12 mm |
| `cart` | `IF-109` | `orphan` | UK harness plug declared with no mate; its data logger is not a part of the cart |
| `antikythera` | `IF-104` | `fastener` | DE pin diameter 1.5 mm vs UK 0.0472 in (1.199 mm) |
| `antikythera` | `IF-102` | `orphan` | FR exhibit lighting plug declared with no ES mate; the spare ES plug ES-3203-C01 sits at its position |
| `antikythera` | `IF-106` | `position` | z differs by 1.2 mm (FR -94.800 vs UK -3.7795 in); the FR row is the outlier |
| `antikythera` | `IF-116` | `unit` | UK row stored with pos_uom NULL |
| `steam-engine` | `IF-06` | `hydraulic` | UK gauge rating 150 PSI (10.3 bar) vs ES boiler 6 bar |
| `steam-engine` | `IF-02` | `fastener` | DE key 20 mm vs UK 3/4 in (19.05 mm) |
| `steam-engine` | `IF-07` | `position` | x differs by 3.5 mm; the FR row is the outlier |
| `steam-engine` | `IF-23` | `unit` | UK row stored with pos_uom NULL |
| `steam-engine` | `IF-24` | `orphan` | UK governor sleeve sensor plug declared with no indicator mate; the spare plug UK-3305-PL02 sits at its position |
| `glider` | `IF-122` | `position` | ES anchor x 412.7 vs DE eye x 400.0, 12.7 mm (0.5 in) apart for a 2.0 mm tolerance: the half-inch offset of the DE eye plate drawing carried as millimetres on the ES row |
| `glider` | `IF-144` | `unit` | UK row stored with pos_uom NULL |
| `glider` | `IF-133` | `fastener` | DE hinge pin diameter 8 mm vs ES eye bore 10 mm |
| `glider` | `IF-141` | `orphan` | UK mount connector declared with no DE mate: the indicator mount is owned by no workshop |
| `cubesat` | `IF-107` | `unit` | UK row stored with pos_uom NULL |
| `cubesat` | `IF-105` | `position` | x differs by 2.5 mm for a 0.5 mm tolerance; the DE hole pattern is the outlier |
| `cubesat` | `IF-110` | `connector` | Nano-D vs Micro-D shell, 9 pins each |
| `cubesat` | `IF-104` | `orphan` | FR thermistor plug declared with no UK mate (the FR external reference carries the wrong site code); the spare UK plug UK-3501-J05 sits at its position |
| `cubesat` | `IF-102` | `fastener` | ES diámetro 3 mm vs DE Durchmesser 2,5 mm |
| `difference-engine` | `IF-112` | `unit` | UK row stored with pos_uom NULL |
| `difference-engine` | `IF-116` | `position` | x differs by 2.5 mm for a 1.0 mm tolerance; the ES casting hole pattern is the outlier |
| `difference-engine` | `IF-127` | `position` | y differs by 2.2 mm for a 1.0 mm tolerance; the FR rail drawing is the outlier |
| `difference-engine` | `IF-103` | `fastener` | key standard BS 4235 on the UK muff vs DIN 6885 on the DE arbor, 10 mm each |
| `difference-engine` | `IF-105` | `fastener` | ES bracket casting has 4 bolt holes, the FR side frame drawing calls 6 |
| `difference-engine` | `IF-121` | `connector` | M12-A 4 pins on the sensor vs 5 pins on the junction box socket |
| `difference-engine` | `IF-122` | `orphan` | junction box display cable plug declared with no mate; the counter display is not a part of the engine |
| `wind-turbine` | `IF-102` | `unit` | UK row stored with pos_uom NULL on the generator coupling bolts |
| `wind-turbine` | `IF-110` | `position` | z differs by 3.5 mm for a 2.0 mm tolerance (DE 87900.000 vs ES 87903.500); the ES bedplate hole pattern is the outlier |
| `wind-turbine` | `IF-106` | `fastener` | ES diámetro 30 mm vs DE Durchmesser 36 mm, 12 bolts each |
| `wind-turbine` | `IF-113` | `connector` | UK M12-D vs DE M12-A shell, 4 pins each |
| `wind-turbine` | `IF-112` | `hydraulic` | UK rating 5000 PSI (344.7 bar) vs DE nenndruck_bar 206.8 |
| `wind-turbine` | `IF-121` | `orphan` | UK vibration sensor plug declared on the harness interface with no harness mate; the spare harness plug UK-3738-J03 sits at its position |
| `wind-turbine` | `IF-130` | `position` | x differs by 4.0 mm for a 2.0 mm tolerance (FR -6800.000 vs ES -6796.000); the ES hub bolt circle is the outlier |
| `rover` | `IF-105` | `unit` | UK row stored with pos_uom NULL |
| `rover` | `IF-109` | `position` | z differs by 3.5 mm for a 1.0 mm tolerance; the DE base plate row is the outlier (hole pattern measured from the plate top face) |
| `rover` | `IF-102` | `connector` | UK 6W-LATCH-2.5 socket vs DE 6W-HDR-2.54 header, 6 pins each |
| `rover` | `IF-134` | `connector` | UK-3801 4W-HDR-2.54 with 4 pins vs UK-3803 6W-HDR-2.54 with 6 pins |
| `rover` | `IF-122` | `fastener` | FR diamètre 4 mm vs ES diámetro 5 mm, ISO 10642, 3 each |
| `rover` | `IF-129` | `orphan` | UK emergency-stop contact plug declared on the bracket interface with no ES mate; the spare UK plug UK-3804-J03 (the power distribution board's loop pigtail) sits at its position |

Every other interface passes. UK positions are stored in inches with
`pos_uom = 'IN'`, diameters and grips in inches, ratings in psi; after conversion
they coincide with their mates, which is the proof that unit normalisation
works. Hydraulic couplings are rated 206.8 bar in the PLMs that store bar and
3000 psi in the UK PLM; the ornithopter's seeded hydraulic defects are the one UK
coupling rated 5000 psi and the ES trim water coupling whose 3.0 bar rating is
stored as its psi figure, 43.5, in a bar column. Its water couplings (ISO 7241 A,
dash 4, 3.0 bar, `water` or `waste water`) run on the same hydraulic rule.

Tallies (pass / fail / not evaluable) depend on the profile and the product; `fr-engineer` differs from `programme-cleared` only where a product carries NATIONAL-DE, NATIONAL-UK or NATIONAL-ES parts:

| Profile | ornithopter (76) | aerial-screw (52) | cart (20) | antikythera (21) | steam-engine (24) | glider (48) | cubesat (25) | difference-engine (45) | wind-turbine (40) | rover (45) |
|---|---|---|---|---|---|---|---|---|---|---|
| `export-officer` | 69 / 7 / 0 | 52 / 0 / 0 | 16 / 4 / 0 | 17 / 4 / 0 | 19 / 5 / 0 | 44 / 4 / 0 | 20 / 5 / 0 | 38 / 7 / 0 | 33 / 7 / 0 | 39 / 6 / 0 |
| `programme-cleared` | 69 / 7 / 0 | 49 / 0 / 3 | 16 / 4 / 0 | 17 / 4 / 0 | 19 / 5 / 0 | 44 / 4 / 0 | 18 / 5 / 2 | 38 / 5 / 2 | 33 / 7 / 0 | 37 / 6 / 2 |
| `fr-engineer` | 69 / 7 / 0 | 49 / 0 / 3 | 14 / 3 / 3 | 12 / 3 / 6 | 16 / 3 / 5 | 42 / 3 / 3 | 18 / 5 / 2 | 36 / 5 / 4 | 33 / 7 / 0 | 33 / 6 / 6 |
| `de-engineer` | 66 / 5 / 5 | 49 / 0 / 3 | 16 / 3 / 1 | 15 / 3 / 3 | 16 / 3 / 5 | 42 / 3 / 3 | 18 / 5 / 2 | 36 / 5 / 4 | 30 / 7 / 3 | 35 / 6 / 4 |
| `uk-engineer` | 66 / 5 / 5 | 49 / 0 / 3 | 14 / 4 / 2 | 14 / 4 / 3 | 18 / 4 / 2 | 44 / 4 / 0 | 18 / 5 / 2 | 34 / 5 / 6 | 30 / 7 / 3 | 32 / 6 / 7 |
| `es-engineer` | 66 / 5 / 5 | 49 / 0 / 3 | 14 / 3 / 3 | 12 / 3 / 6 | 17 / 4 / 3 | 16 / 3 / 3 | 18 / 5 / 2 | 34 / 5 / 6 | 30 / 7 / 3 | 33 / 6 / 6 |
| `unknown` | 60 / 4 / 12 | 44 / 0 / 8 | 14 / 3 / 3 | 12 / 3 / 6 | 16 / 3 / 5 | 15 / 3 / 4 | 14 / 5 / 6 | 34 / 5 / 6 | 28 / 7 / 5 | 28 / 5 / 12 |

On the ornithopter, the DE, UK and ES engineers lose the five interfaces of the
NATIONAL-FR control post and instrumentation pod (`IF-03`, `IF-15`, `IF-30`,
`IF-43`, `IF-44`). On the aerial screw, every profile but the officer loses the
three interfaces of the EXPORT-LICENCE hydraulic motor `HMOT-70090` (`IF-89`,
`IF-90`, `IF-99`).

Catalogue-level finding (not a violation of a SHACL shape; SHACL is the Shapes Constraint Language): the ES `conector` table has one
column without `@Describe` (`obsoleto`), flagged as undescribed by `/es/catalogue`. Every
other column, the part attribute columns included, is described.

## Native schemas

Key = first column, part foreign key = second column, in every table.

| PLM | Part table | Plug table |
|---|---|---|
| FR | `piece(ref_piece, designation, fichier_cao, indice, etat, masse_kg, matiere, type_piece)` | `connecteur(id_connecteur, ref_piece, pos_x_mm, pos_y_mm, pos_z_mm, type_connecteur, nb_broches)` |
| DE | `bauteil(teil_nr, benennung, cad_datei, revision, status, masse_kg, werkstoff, teileart)` | `stecker(stecker_id, teil_nr, pos_x_mm, pos_y_mm, pos_z_mm, typ, polzahl)` |
| UK | `component(comp_id, name, cad_file, revision, lifecycle, mass_lb, material, part_type)` | `harness_connector(conn_ref, comp_id, pos_x, pos_y, pos_z, pos_uom, shell_type, pin_qty)` |
| ES | `pieza(cod_pieza, denominacion, fichero_cad, revision, estado, masa_kg, material, tipo)` | `conector(cod_conector, cod_pieza, pos_x_mm, pos_y_mm, pos_z_mm, tipo, num_contactos, obsoleto)` |

| PLM | Fastener table | Hydraulic coupling table |
|---|---|---|
| FR | `fixation(ref_fixation, ref_piece, pos_x_mm, pos_y_mm, pos_z_mm, norme, diametre_mm, nombre, longueur_serrage_mm)` | `raccord_hydraulique(ref_raccord, ref_piece, pos_x_mm, pos_y_mm, pos_z_mm, norme, taille_dash, pression_bar, fluide)` |
| DE | `befestiger(befestiger_id, teil_nr, pos_x_mm, pos_y_mm, pos_z_mm, norm, durchmesser_mm, anzahl, klemmlaenge_mm)` | `hydraulikkupplung(kupplung_id, teil_nr, pos_x_mm, pos_y_mm, pos_z_mm, norm, dash_groesse, nenndruck_bar, fluid)` |
| UK | `fastener(fast_ref, comp_id, pos_x, pos_y, pos_z, pos_uom, standard, dia, dia_uom, qty, grip, grip_uom)` | `hyd_coupling(cplg_ref, comp_id, pos_x, pos_y, pos_z, pos_uom, standard, dash, rating, rating_uom, fluid)` |
| ES | `remache(cod_remache, cod_pieza, pos_x_mm, pos_y_mm, pos_z_mm, norma, diametro_mm, cantidad, longitud_apriete_mm)` | `acoplamiento(cod_acoplamiento, cod_pieza, pos_x_mm, pos_y_mm, pos_z_mm, norma, tamano_dash, presion_bar, fluido)` |

The PLM tables carry no classification column. Each part table states the
part attributes in its PLM's idiom, in the PLM's versioned schema migration
`V2__part_attributes.sql`:

### Part attributes

| Concept | Ontology term | FR `piece` | DE `bauteil` | UK `component` | ES `pieza` |
|---|---|---|---|---|---|
| revision | `atelier:revision` | `indice` (A, B) | `revision` (01, 02) | `revision` (P1, P2, C1) | `revision` (integer 1, 2) |
| lifecycle | `atelier:lifecycleLabel` | `etat` (En cours, Publié, Bloqué, Remplacé) | `status` (In Arbeit, Freigegeben, Gesperrt, Ersetzt) | `lifecycle` (Draft, Released, Frozen, Superseded) | `estado` (Borrador, Liberado, Bloqueado, Sustituido) |
| mass | `atelier:mass` (`qudt:QuantityValue`) | `masse_kg`, `@Unit("KiloGM")` | `masse_kg`, `@Unit("KiloGM")` | `mass_lb`, `@Unit("LB")` | `masa_kg`, `@Unit("KiloGM")` |
| material | `atelier:material` | `matiere` | `werkstoff` | `material` | `material` |
| part type | `atelier:partType` | `type_piece` | `teileart` | `part_type` | `tipo` |

The part type is `PART`, `ASSEMBLY`, `SOFTWARE` or `DOCUMENT`, `PART` by
default; only a `PART` has geometry. The
seed takes them from each part's `extended` object in `data/products/*.json`
(`revision`, `lifecycle`, `mass` with `massUnit`, `material`, `type`, which
is `PART` when absent); `data/generate.py` refuses a lifecycle word outside
the PLM's vocabulary, a revision outside its form and a negative mass, and
accepts a decimal comma in `mass`. A mass is written as the file states it,
in the PLM's mass column: a part whose `massUnit` is not the PLM's storage
unit keeps its number unconverted. The cart's trip counter `UK-3103` (0.6 kg
in the file) is stored as 0.6 in `mass_lb` that way, its seeded mass-unit
defect: the layer reads 0.272 kg.

### Software and documents

A product is more than geometry: flight software, a certificate or a setting
manual is released and revised like a part. Each entry of
`extended.nonGeometricItems` is a row of its site's part table with part type
`SOFTWARE` or `DOCUMENT`, no CAD file, its revision in the site's form and its
lifecycle word, a `product_part` row and a tag in the core, and one line of
quantity 1 under its site kit. `data/generate.py` normalises the entry: the
local id `UK/3511` is the row `UK-3511` (and the site kit `UK/3500` the row
`UK-3500`), and a software version is part of the native name ("Flight software
package 1.4.2"). It needs no STEP file and the file index has no entry for it,
so the viewer, the CAD URL issuer and `cadMissing` pass it by. It is on no
interface.

| Product | Item | Site | Type |
|---|---|---|---|
| cubesat | `UK-3511` Flight software package 1.4.2 | UK | SOFTWARE |
| difference-engine | `UK-3690` Operating and setting instructions | UK | DOCUMENT |
| difference-engine | `D-36060` Nockenprofil- und Steuerzeitentabelle (releasable to DE) | DE | DOCUMENT |
| rover | `UK-3840` Rover control software package 2.3.0 | UK | SOFTWARE |
| wind-turbine | `UK-3790` Turbine controller software package 4.2.0, `UK-3791` SCADA package 7.1.3 | UK | SOFTWARE |
| wind-turbine | `UK-3792` Type certificate | UK | DOCUMENT |

### Product mass limit

The core `product` table carries `mass_limit_kg` (`NUMERIC(12, 3)`, empty when
the product has none), mapped with `@Unit("KiloGM")` to `atelier:massLimit`, a
`qudt:QuantityValue` minted as `<product IRI>/mass-limit`. `data/generate.py`
writes it from `extended.massLimitKg`. The cubesat states 2.000 kg (the 1U
limit), and its parts weigh 2.010 kg over the four sites; the ornithopter states
260.500 kg, the authored rated load of the ground test stand it is mounted on,
and its parts weigh 261.237 kg, each placed part counted at every occurrence.

### Bill of materials

Each PLM stores its site's tree in its own idiom, in the versioned migration
`V3__bill_of_materials.sql`. A line names a parent item, a child item of the
same PLM and the quantity the parent uses; the quantity aggregates every
occurrence of the child under that parent.

| PLM | Idiom | Storage |
|---|---|---|
| DE | self-referencing tree on the part row | `bauteil.parent_id` (foreign key to `bauteil.teil_nr`, empty on the site kit) and `bauteil.menge`; an item has at most one parent |
| FR | link table | `nomenclature(parent, enfant, quantite, repere)`, key `(parent, enfant)`, both foreign keys to `piece(ref_piece)`; an item may have several parents |
| ES | versioned JSONB document on the parent row | `pieza.lista_materiales` = `{"version": n, "lineas": [{"referencia", "cantidad"}]}`, read through the view `linea_lista_materiales(padre, referencia, cantidad)` over `jsonb_array_elements`, which a read-only (`@Immutable`) entity maps: Ontop maps relations, not JSON. The document names children the profile may not see, so it is `@ReadThrough("linea_lista_materiales")`: the catalogue documents it with `readThrough`, `/es/tables/pieza` omits it and `/es/sql` refuses any reference to it (`read-through-column`); the view filters each line by its child |
| UK | flat indented rows | `bom_line(line_no, level, parent_part_no, child_part_no, qty)`: the site kit on level 0 with no parent, then every line depth first |

Assemblies and site kits are rows of each PLM's part table with part type
`ASSEMBLY` and no CAD file; the file index has no `atelier:cadFile` for them.
The core database's `product_part` and `part_tag` list every part and assembly
of each product, an assembly tagged `NONE` / `ALL` unless its product file
classifies it.

In the product file, `extended.assemblies` holds the non-geometric items
(`id`, `plm`, `name` in the PLM's language, `nameEn`, `kind` `SITE_KIT` or
`ASSEMBLY`, `revision`, `lifecycle`, optional `classification`) and `extended.bomLines` the lines
(`parent`, `child`, `quantity`, optional `findNumber`). A use across sites is
not a line but an external reference, `extended.externalRefs` (`localPart`,
`remoteUrn`, `quantity`, `expectedRevision`, `note`). `data/generate.py`
(with `data/bom.py`) refuses an assembly with the id of a part, a kind outside
the two, a line naming an item the file does not hold, a line between two
sites, a quantity that is not positive, a line declared twice, a site without
exactly one site kit, a site kit with a parent, an item without a parent, a DE
item with several parents and a cycle; it writes each site's lines in its
idiom into the PLM seed migrations. A product file that declares neither
assemblies nor lines, only parts and interfaces, loads with one site kit per
site (`<KEY>-<PLM>-KIT`, named in the site's language) holding every part of the
site at quantity 1.

| Product | Assemblies | Lines | Part occurrences |
|---|---|---|---|
| `ornithopter` | 6 | 78 | 90 (DE 20, FR 23, ES 21, UK 26) |
| `aerial-screw` | 4 | 36 | 36 |
| `cart` | 4 | 27 | 27 |
| `antikythera` | 4 | 33 | 33 |
| `steam-engine` | 4 | 28 | 30 (DE 10, FR 6, ES 8, UK 6) |
| `glider` | 18 | 108 | 420 (DE 86, FR 237, ES 84, UK 13) |
| `cubesat` | 4 | 26 | 26 |
| `difference-engine` | 66 | 260 | 7,484 (DE 2,804, FR 2,285, ES 1,485, UK 910) |
| `wind-turbine` | 58 | 239 | 3,738 (DE 273, FR 1,414, ES 1,811, UK 240) |
| `rover` | 49 | 198 | 819 (DE 320, FR 210, ES 158, UK 131) |

Part occurrences count the items of part type `PART`, the quantities
multiplied down from each site kit.

Each PLM also computes the closure of its own tree in its database, in the
versioned migration `V6__bom_closure.sql`: a view whose rows are every
(ancestor, descendant) pair of the site's lines, the pair of each item with
itself included, by a recursive common table expression over the site's
idiom. A read-only (`@Immutable`) entity maps each view to `atelier:contains`
between the site's part IRIs; the mapping states no class, so a request for
parts never reads the view. The pair is visible on the `/tables` and SQL
routes when the contained item is.

| PLM | View | Columns | Over |
|---|---|---|---|
| DE | `teilestruktur` | `vorfahr`, `nachfahr` | `bauteil.parent_id` |
| FR | `nomenclature_fermeture` | `ascendant`, `descendant` | `nomenclature` |
| ES | `cierre_lista_materiales` | `ascendiente`, `descendiente` | the view `linea_lista_materiales` |
| UK | `bom_closure` | `ancestor`, `descendant` | `bom_line` |

### Placements

A part number is drawn once, in its STEP file; a product holds it many times.
Where each occurrence sits is data of the line that uses it: each PLM stores
the placements of its own lines, in its own idiom and units, in the versioned
migration `V10__*.sql`. A placement is relative to the child as its STEP file
draws it, which is the child's reference occurrence: a translation in the
site's length unit, then a rotation in degrees about the fixed x, y and z axes
of the product frame, in that order, so a point p of the STEP lands at
`Rz(rz) Ry(ry) Rx(rx) p + (x, y, z)`. Occurrence 1 is the reference occurrence,
the identity. A line without placements is used once, where the child's STEP
draws it, however large its quantity.

| PLM | Idiom | Storage |
|---|---|---|
| DE | installation positions of the part row's line | `einbaulage(parent_id, teil_nr, lfd_nr, x_mm, y_mm, z_mm, rx_grad, ry_grad, rz_grad)`, key `(parent_id, teil_nr, lfd_nr)`, foreign key `(teil_nr, parent_id)` to the line on `bauteil`, cascading |
| FR | positions of a nomenclature link | `nomenclature_position(parent, enfant, rang, x_mm, y_mm, z_mm, rx_deg, ry_deg, rz_deg)`, key `(parent, enfant, rang)`, foreign key `(parent, enfant)` to `nomenclature`, cascading |
| ES | inside the document's line objects | `lista_materiales.lineas[].posiciones` = `[{"x_mm", "y_mm", "z_mm", "rx_grados", "ry_grados", "rz_grados"}]`, read through the table `posicion_lista_materiales(padre, referencia, orden, x_mm, ..., rz_grados)` that a trigger on `pieza` keeps from the documents, `orden` the position in the document from 1; a table, not a view, because its key lets Ontop read an occurrence's six values as one row where a view without a key joins itself once per value |
| UK | child table of `bom_line`, in inches | `bom_line_placement(parent_part_no, child_part_no, occurrence_no, x_in, y_in, z_in, rx_deg, ry_deg, rz_deg)`, foreign key `(parent_part_no, child_part_no)` to `bom_line`, cascading |

The UK placements are a child table rather than one `bom_line` row per
occurrence: a `bom_line` row stays one line with its quantity, so the indented
listing and its closure are unchanged, and the rows key on the line's parent and
child part numbers because a `line_no` is a position in the listing that changes
whenever a line is inserted above it.

Each idiom maps to `atelier:Occurrence` through the annotations
(`@OntologyClass("atelier:Occurrence")`; the columns mapped to `atelier:parent`
and `atelier:child` name the line, `atelier:ofLine`; `atelier:index`; the
translations carry `@Unit`, so they become QUDT lengths the units graph
converts; the rotations are plain degrees). The tables are filtered on the
`/tables` and SQL routes through the child, as the lines are: a hidden part has
no placements for that profile.

In the product file a line may carry `placements`, one entry per occurrence,
as many as its quantity, each `[x, y, z, rx, ry, rz]` in the site's unit:

```
{"parent": "UK-3685", "child": "UK-3614", "quantity": 2,
 "placements": [[0, 0, 0, 0, 0, 0], [8.0709, 0, 0, 0, 0, 0]]}
```

Occurrences compose down the tree: an occurrence of a child under an
occurrence of its parent is `M_parent . M_line`, the parent's transform applied
after the child's, from the identity at the site kit. A part's occurrences are
those of every path from its site kit: its parents in id order, each parent's
occurrences in order and, under each, the line's placements in order; the first
is the composition of the reference occurrences. `data/generate.py` (with
`data/placements.py`) refuses placements on a line whose quantity is not a whole
number, a count that differs from the quantity, an entry that is not six finite
numbers, and a part whose first occurrence is not the identity (its STEP would
not sit where the part is used).

| Product | Lines with placements | Occurrences drawn |
|---|---|---|
| `glider` | 18 | 147 |
| `difference-engine` | 52 | 6,525 |
| `wind-turbine` | 18 | 1,272 |
| `rover` | 34 | 287 |

Occurrences drawn count the parts of part type `PART` once per world
placement: a line without placements under a placed assembly is drawn once per
occurrence of the assembly, and a repeated line without placements once. The
other products repeat no part number in their CAD layouts.

### Stations, sections and spans

A product may name its stations: positions along one axis of its frame, such as
wing stations or fuselage frames, and its sections: stretches between stations,
each owned by one site. They are product data, which no PLM holds: the layer
publishes them in the link store. In the product file, in `extended`:

```
"stations": {"key": "WS", "name": "wing station", "axis": "y", "symmetric": true,
  "measure": "mm from the centreline; WS n is y = +n on the right wing and y = -n on the left",
  "list": [{"id": "WS 700", "at": "700", "basis": "joint", "source": "S_ROOT; IF-01 and IF-13"},
           {"id": "WS 5500", "at": "5500", "basis": "measured", "source": "Museo Galileo working model", "tolerance": "50"}]},
"sections": [{"id": "S-R-IN", "name": "Right wing, fixed inner sector", "owner": "DE", "side": "right",
              "stations": ["WS 700", "WS 1200", "WS 3000"]}]
```

A station has `id`, `at` (mm along the axis; on a symmetric axis station n is
at +n on the right side and -n on the left), `basis` (`printed`, `measured`,
`joint` or `inferred`), `source` and an optional `tolerance` in mm, the accuracy
of a station read from a drawing or a model. A section has `id`, `name`,
`owner` (FR, DE, UK or ES), `side` (`left`, `right` or `both`; `both` on an axis
that is not symmetric), `stations` in order along the axis, and an optional
`part`, the part the section is when one part is the whole section.

A part's span is computed, never authored. `modules/cad/generate.py` writes
`modules/cad/stp/<product key>/bounds.json` beside the STEP files: per CAD key
the exact box of the part in mm in the product frame and the sha256 of its STEP
file. `data/generate.py` refuses a part whose STEP is missing from it or whose
hash differs, for every product, so a span never outlives its geometry. The
part's span is the extent of its box along the axis, at its reference
occurrence; each placement row carries the span of its occurrence with the
parent at its first occurrence, the extent of the box's eight corners moved by
that world transform, so a rotated occurrence gets its true envelope. Every
other world occurrence's span is the stored span of its line's occurrence (the
part's own for a line without placements) carried by its parent's motion from
the parent's first occurrence to this one, the composed transform of the whole
path with its rotations: the ends of the span move exactly when that motion
maps the station axis onto itself or its opposite, as a sub-assembly placed a
second time turned 180 degrees does. The generator checks every derived span
against the true envelope of its occurrence, and refuses the one case no span
can give: a parent turned so that the station axis mixes with another axis (a
yaw of 30 degrees), whose envelope depends on the part's extents across the
axis.

Each site stores spans in its own columns and unit, migrations `V12__*.sql`,
empty for an assembly and for a product without stations:

| PLM | Part row | Placement row |
|---|---|---|
| FR | `piece.travee_debut_mm, travee_fin_mm` | `nomenclature_position.travee_debut_mm, travee_fin_mm` |
| DE | `bauteil.spanne_von_mm, spanne_bis_mm` | `einbaulage.spanne_von_mm, spanne_bis_mm` |
| UK | `component.span_from, span_to` in the unit of `span_uom` (`IN`, 4 decimals) | `bom_line_placement.span_from, span_to, span_uom` |
| ES | `pieza.tramo_desde_mm, tramo_hasta_mm` | `"tramo_desde_mm", "tramo_hasta_mm"` of each `posiciones` entry, read through `posicion_lista_materiales.tramo_desde_mm, tramo_hasta_mm` |

The columns map to `atelier:spanFrom` and `atelier:spanTo`, quantity values
under the part or occurrence IRI. `data/generate.py` (with `data/stations.py`)
refuses a `stationSpan` key anywhere in a product file, a station declared
twice, an unknown basis, a negative tolerance, a section on an unknown station,
with fewer than two stations or with its stations out of order, a section part
the product does not hold, an owner outside the four sites, and sections
without stations.

The ornithopter has twelve wing stations, WS 0 to WS 5500 along y, symmetric:
WS 700 and WS 3000 are the wing root and knuckle joints, WS 5500 is measured on
the Museo Galileo model (tolerance 50 mm), the others are inferred from the CAD
module. Five sections: `S-CTR` (FR, both sides, WS 0 to WS 700), `S-L-IN` and
`S-L-OUT` (UK, left, WS 700 to WS 3000 and WS 3000 to WS 5500), `S-R-IN` and
`S-R-OUT` (DE, right, the same stations).

### External references

A site never copies another site's part: a part row names it by URN,
`urn:plm:<site>:part:<local id>`, with the revision it expects, in the
versioned migration `V4__external_references.sql`. `id` is a surrogate key
(`XR-0001`, numbered per PLM across the products by `data/generate.py`), the
second column the local part, a foreign key to the PLM's part table.

| PLM | Table |
|---|---|
| FR | `reference_externe(id, piece, urn, quantite, indice_attendu, note)`, `piece` to `piece(ref_piece)` |
| DE | `externer_verweis(id, teil_nr, urn, menge, erwartete_revision, bemerkung)`, `teil_nr` to `bauteil(teil_nr)` |
| UK | `external_ref(id, part_no, remote_urn, qty, expected_revision, note)`, `part_no` to `component(comp_id)` |
| ES | `referencia_externa(id, pieza, urn, cantidad, revision_esperada, nota)`, `pieza` to `pieza(cod_pieza)` |

The URN is stored as entered and never resolved by a PLM: whether it names a
part, and whether that part is still at the expected revision, only the layer
can say, since only the layer sees both ends. The British native form
`UK/nnnn` names the part key `UK-nnnn`.

`data/generate.py` (with `data/references.py`) writes one row per entry of
`extended.externalRefs` whose `localPart` is a row of the site's part table (a
part, an assembly, a site kit, a software or a document item), refuses a
local part the product does not hold and a quantity that is not positive, and
checks that the reference rules fail on exactly the references the product lists
in `seededReferenceDefects` (`localPart`, `remoteUrn`, `rule`), which are the
dangling and stale references its `extended.proposedDefects` name. A target that
exists belongs to the referencing product. A URN that does not parse
(`malformedUrn` in the proposed defects) is a `danglingReference`; the steam
engine's reference to the single-acting variant's equilibrium valve, a part no
PLM holds, is one too.

| Product | References written | `danglingReference` | `staleRevision` |
|---|---|---|---|
| `ornithopter` | 6 | 1 | 1 |
| `aerial-screw` | 0 | 0 | 0 |
| `cart` | 12 | 1 | 1 |
| `antikythera` | 16 | 1 | 1 |
| `steam-engine` | 12 | 1 | 0 |
| `glider` | 42 | 1 | 0 |
| `cubesat` | 20 | 1 | 1 |
| `difference-engine` | 55 | 3 | 2 |
| `wind-turbine` | 24 | 1 | 0 |
| `rover` | 21 | 0 | 1 |

CAD keys are
`cad/<product key>/<file>.stp` (for example `cad/ornithopter/fr-keel-beam.stp`),
matching `^cad/[a-z0-9-]+/[a-z0-9-]+\.stp$`; the front end reads them through
presigned URLs (see "Export control").

### Purchased items and suppliers

Each PLM describes the items it buys on its part rows and keeps its own
supplier list and supplier offers, in the versioned migrations
`V5__purchased_items.sql` (standard, nominal size, suppliers, offers) and
`V9__purchased_item_classes.sql` (item class, the attributes of the O-ring,
placard, container, tyre, wheel and brake classes, shelf life). No column carries a cost.

| Concept | Ontology term | FR | DE | UK | ES |
|---|---|---|---|---|---|
| standard | `atelier:standard` | `piece.norme` | `bauteil.norm` | `component.standard` | `pieza.norma` |
| nominal diameter | `atelier:nominalDiameter` (`qudt:QuantityValue`) | `diametre_nominal_mm`, `@Unit("MilliM")` | `nenndurchmesser_mm`, `@Unit("MilliM")` | `nominal_dia`, unit in `size_uom` | `diametro_nominal`, unit in `unidad_medida` |
| nominal length | `atelier:nominalLength` (`qudt:QuantityValue`) | `longueur_nominale_mm` | `nennlaenge_mm` | `nominal_length`, unit in `size_uom` | `longitud_nominal`, unit in `unidad_medida` |
| item class | `atelier:itemClass` | `classe_article` | `teileklasse` | `item_class` | `clase_articulo` |
| O-ring inner diameter | `atelier:innerDiameter` (`qudt:QuantityValue`) | `diametre_interieur_mm` | `innendurchmesser_mm` | `inside_dia`, unit in `size_uom` | `diametro_interior`, unit in `unidad_medida` |
| O-ring cross-section | `atelier:crossSection` (`qudt:QuantityValue`) | `section_mm` | `schnurstaerke_mm` | `cross_section`, unit in `size_uom` | `seccion`, unit in `unidad_medida` |
| O-ring compound | `atelier:compound` | `melange` | `mischung` | `compound` | `compuesto` |
| placard legend | `atelier:legend` | `legende` | `beschriftung` | `legend` | `leyenda` |
| placard width, height | `atelier:itemWidth`, `atelier:itemHeight` (`qudt:QuantityValue`) | `largeur_mm`, `hauteur_mm` | `breite_mm`, `hoehe_mm` | `width`, `height`, unit in `size_uom` | `ancho`, `alto`, unit in `unidad_medida` |
| placard face material | `atelier:facestock` | `support` | `traegerwerkstoff` | `face_material` | `soporte` |
| placard adhesive | `atelier:adhesive` | `adhesif` | `klebstoff` | `adhesive` | `adhesivo` |
| container base width, depth, contour width | `atelier:baseWidth`, `atelier:itemDepth`, `atelier:contourWidth` (`qudt:QuantityValue`); its height is `atelier:itemHeight` | `largeur_base_mm`, `profondeur_mm`, `largeur_contour_mm` | `bodenbreite_mm`, `tiefe_mm`, `konturbreite_mm` | `base_width`, `depth`, `contour_width`, unit in `size_uom` | `ancho_base`, `fondo`, `ancho_contorno`, unit in `unidad_medida` |
| container shell material | `atelier:shellMaterial` | `materiau_coque` | `schalenwerkstoff` | `shell_material` | `material_casco` |
| tyre outer diameter, section width | `atelier:outerDiameter`, `atelier:sectionWidth` (`qudt:QuantityValue`) | `diametre_exterieur_mm`, `largeur_section_mm` | `aussendurchmesser_mm`, `querschnittsbreite_mm` | `outside_dia`, `section_width`, unit in `size_uom` | `diametro_exterior`, `ancho_seccion`, unit in `unidad_medida` |
| tyre or wheel rim diameter | `atelier:rimDiameter` (`qudt:QuantityValue`); a wheel's width is `atelier:itemWidth` | `diametre_jante_mm` | `felgendurchmesser_mm` | `rim_dia`, unit in `size_uom` | `diametro_llanta`, unit in `unidad_medida` |
| tyre ply rating | `atelier:plyRating` (`xsd:integer`) | `indice_plis` | `lagenzahl` | `ply_rating` | `indice_telas` |
| brake heat stack diameter, rotors | `atelier:heatStackDiameter` (`qudt:QuantityValue`), `atelier:rotorCount` (`xsd:integer`) | `diametre_empilage_mm`, `nombre_rotors` | `waermesenkendurchmesser_mm`, `rotorzahl` | `heat_stack_dia`, `rotors`, unit in `size_uom` | `diametro_disipador`, `numero_rotores`, unit in `unidad_medida` |
| shelf life (months) | `atelier:shelfLifeMonths` (`xsd:integer`) | `duree_stockage_mois` | `lagerdauer_monate` | `shelf_life_months` | `vida_util_meses` |
| supplier | `atelier:Supplier`: `atelier:label`, `atelier:location` | `fournisseur(code_fournisseur, raison_sociale, ville)` | `lieferant(id, name, ort)` | `supplier(supplier_id, name, town)` | `proveedor(cod_proveedor, nombre, ciudad)` |
| supplier offer | `atelier:SupplierOffer`: `atelier:offersPart`, `atelier:fromSupplier`, `atelier:supplierPartNumber`, `atelier:leadTimeDays`, `atelier:preferred` | `article_fournisseur(id, ref_piece, code_fournisseur, reference_fournisseur, delai_jours, prefere)` | `lieferantenteil(id, teil_nr, lieferant_id, lieferanten_teilenummer, lieferzeit_tage, bevorzugt)` | `supplier_part(id, comp_id, supplier_id, supplier_part_no, lead_time_days, preferred)` | `pieza_proveedor(id, cod_pieza, cod_proveedor, referencia_proveedor, plazo_dias, preferido)` |

The UK and ES sites buy from inch catalogues too, so their size columns carry
the unit per row (`IN` or `MilliM`); FR and DE store millimetres. Texts are in
the site's language. A part may have several offers, each from a supplier of
its own PLM's list. An offer row is visible when its part is; the supplier
lists are not export-controlled.

A purchased part states what it is in `extended.purchased`, which
`data/generate.py` (with `data/purchasing.py`) writes into these columns. The
generator reads the classes and their attributes from the ontology's
`atelier:ItemClasses` scheme (each attribute's key in the block is its
`atelier:stagingKey`; its property's range says whether it is a length, a whole
number or a text) and the column of each property, with its unit, from the
site's R2RML mapping. A class whose attributes are properties the sites already
map is data only: a concept in the scheme. A new property needs, at each site, a
column in a migration and an annotated entity field, then regenerated mappings.

```
"purchased": {
  "class": "fastener" | "o-ring" | "placard" | "container" | "tyre" | "wheel" | "brake" | "cord" | "canister",   // skos:notation of an atelier:ItemClasses concept
  "standard": "AS568-014",                        // optional: the standard as the site writes it
  "unit": "IN",                                   // optional: unit of the numbers; default IN at UK, MilliM elsewhere
  "attributes": {...},                            // numbers as text in the unit; texts in the site's language
  "shelfLifeMonths": 120                          // optional
}
```

| Class | `attributes` | Example |
|---|---|---|
| `fastener` | `diameter`, `length` (none for a nut, neither for a circlip) | `{"diameter": "0.118", "length": "0.394"}` with `"unit": "IN"` |
| `o-ring` | `innerDiameter`, `crossSection`, `compound`; a part whose `standard` names an AS568 dash size or an ISO 3601-1 code may state the compound only | `{"compound": "EPDM 80 Shore A, phosphatesterbeständig"}` with `"standard": "ISO 3601-1 214A"` |
| `placard` | `legend`, `width`, `height`, `material` (the face material with its thickness), `adhesive` | `{"legend": "14ABC", "width": "4.724", "height": "1.575", "material": "polycarbonate film 0.010 in", "adhesive": "acrylic pressure-sensitive"}` |
| `container` | `baseWidth`, `depth`, `height`, `contourWidth`, `material` (the shell); `standard` the IATA type | `{"baseWidth": "61.50", "depth": "60.39", "height": "45.00", "contourWidth": "95.98", "material": "aluminium alloy"}` with `"standard": "IATA AKH"` |
| `tyre` | `outerDiameter`, `sectionWidth`, `rimDiameter`, `plyRating` (a whole number) | `{"outerDiameter": "46.00", "sectionWidth": "17.00", "rimDiameter": "20.00", "plyRating": "30"}` |
| `wheel` | `rimDiameter`, `width` | `{"rimDiameter": "20.00", "width": "15.00"}` |
| `brake` | `heatStackDiameter`, `rotors` (a whole number) | `{"heatStackDiameter": "17.30", "rotors": "4"}` |
| `cord` | `diameter`; the material is the part's own (`extended.material`, `atelier:material`) | `{"diameter": "0.394"}` |
| `canister` | `baseWidth`, `depth`, `height`, `material` (the shell) | `{"baseWidth": "7.480", "depth": "4.724", "height": "11.024", "material": "polyethylene"}` |

The generator refuses a block on a part whose `extended.partType` is not
`PURCHASED`, an unknown class or attribute, a unit FR or DE cannot store, a
number with more decimals than the site's column, a text over 120 characters
and a shelf life that is not a positive number of months. Its figures list, per
product and class, the part numbers, the items and each item bought under
several part numbers with its sites, shelf life and stocking line, found from
the identifying attributes of `ontology/atelier.ttl` as the query service
finds them. `extended.suppliers` and `extended.supplierParts` hold each site's
rows in its own column names, an offer naming its part by the site's own id.
The generator refuses an offer for a part the product does not hold, a supplier
its site does not list, a lead time that is not a whole number of days and a
supplier key two products list, and writes neither the price nor the currency.

| Product | Purchased items described | Supplier rows | Offers | Single-source parts |
|---|---|---|---|---|
| `glider` | 2 fasteners | 0 | 0 | 0 |
| `rover` | 14 fasteners | 11 (10 names) | 60 | 58 |
| `wind-turbine` | 5 O-rings (pitch cylinder, pitch valve block, yaw brake) | 0 | 0 | 0 |
| `steam-engine` | 3 O-rings (cylinder cover, throttle valve spindle, governor gland) | 0 | 0 | 0 |
| `ornithopter` | 3 cords, 3 canisters, 1 tyre | 0 | 0 | 0 |

The rover has no fluid joint, so no O-ring; placards belong to a cabin, which
no public product has, and so do containers, aircraft wheels and brakes: those
classes are tested on fixtures. The ornithopter's tyre is its ground handling
wheel's, 260 x 85, with a 60-month shelf life.

### Functional edges and gears

A bill of materials says what contains what; the functional edges say where
power or motion goes, from a part to the next, across the sites. No site holds
both ends of a joint between two sites, so the edges are the layer's own
engineering knowledge, published in the link store as the interfaces are, with
no PLM table. A product file states them as `extended.functionalEdges`, each
`{ from, to, flow, via?, reaction? }`: two parts of the product, the flow kind
(`mechanical`, `electrical`, `hydraulic` or `steam`), `via` the interface of the
product joining exactly those two parts when the joint is one (a gear mesh,
inside one site or between two, and a shaft carrying a wheel inside one site have
none), and `reaction` the gear held
fixed that the driving gear also meshes (the ring of a planetary stage driven
through its carrier). `extended.ratedSpeeds` lists `{ part, rpm }`, the speeds
of rotating parts at the product's rated operating point. `data/generate.py`
refuses an edge between unknown parts, through another interface or of an
unknown flow, and writes each edge to `data/links.ttl` as an `atelier:Drive`
(IRI `https://example.com/atelier/drive/{product}/D{nn}`) with the plain
`atelier:drives` triple beside it, and each rated speed as
`atelier:ratedSpeedRpm`.

| Product | Edges | What they carry |
|---|---|---|
| `ornithopter` | 21 mechanical, 3 hydraulic, 1 electrical | both crank arms through the crank shaft and its lantern pinion (8 staves) and the peg wheel (32 pegs) to the windlass drum, the stirrups through the pedal cords to the drum and through the return cords to both root fittings, the drum's two drive cords to the outer spars and the outer ribs, both wing actuators to the inner spars, the head hoop through the tail control cord to the tail plane; the power pack to both actuators, the ground service panel to the left wheel brake; the control post to the tail trim servo |
| `cart` | 18 mechanical | each drive spring through its crown wheel (30 teeth) and lantern pinion (6) to the common output shaft and both drive wheels, the trip counter on the left wheel, the steering cams to the tiller and the rear wheel |
| `steam-engine` | 7 steam, 10 mechanical, 1 electrical | boiler to throttle, steam pipe, cylinder and piston, eduction pipe and condenser, the pressure gauge; piston rod, parallel motion, beam, connecting rod, crank, flywheel shaft and flywheel; the governor's belt from the shaft and its link back to the throttle, a loop; the speed indicator |
| `wind-turbine` | 25 mechanical, 9 electrical, 4 hydraulic | blade root to hub, main shaft, shrink disc, planetary stage (planet to sun against the fixed ring), low-speed and high-speed stages, coupling, generator rotor, then stator, converter, tower cable, transformer and switchgear; pitch (power unit, valve block, cylinder, lever, bearing, blade) and yaw (cabinet, drive, pinion, slewing ring, bedplate), the brakes from the nacelle power unit |
| `rover` | 6 mechanical, 9 electrical | battery to power board to the motor controllers to the gearmotors, the wheel hub and wheel, the steering column to the knuckle; the computer's commands to both controllers |

The beam engine is the crank variant: its file holds no sun-and-planet gear, so
its edges carry no gear stage. Gears carry their tooth count and module as part
attributes in their site's idiom, in the columns of the versioned migration `V8` of each
PLM and mapped by the annotations:

| Concept | Ontology term | FR `piece` | DE `bauteil` | UK `component` | ES `pieza` |
|---|---|---|---|---|---|
| tooth count | `atelier:toothCount` (`xsd:integer`) | `nombre_dents` | `zaehnezahl` | `teeth` | `numero_dientes` |
| module | `atelier:gearModule` (`qudt:QuantityValue`, node `<part IRI>/module`) | `module_mm`, `@Unit("MilliM")` | `modul_mm`, `@Unit("MilliM")` | `module_in`, `@Unit("IN")` | `modulo_mm`, `@Unit("MilliM")` |

The generator writes them from a part's `extended.gear` (`{ teeth, module }`,
the module in the site's unit) or, for a product recording its gearing as
`extended.meshes`, from the mesh list: every part holding gears gets the module,
a part holding one gear its tooth count; a stepped cluster of several gears
states no tooth count. The wind turbine's gears are 20 mm module (ring 99,
planets 38, sun 22, both parallel stages 105 over 25) but for the seeded defect
below; its yaw pinion (14 teeth) meshes the slewing ring (254) at 16 mm; the
Antikythera mechanism's gears are 0.55 mm, `0.02165` in at the British site; the
ornithopter's ES lantern pinion (8 staves) drives its FR peg wheel (32 pegs) at
12 mm, the peg wheel recorded otherwise below, and its rated speeds give the
drum 10 rpm for 40 at the pinion.

`data/generate.py` applies the meshModule rule to the edges as the shape does
and compares the result with the product's `seededMeshDefects`
(`{ driver, driven, rule }`), and computes the gear train along the edges from the
first rated speed to the last: the wind turbine's must give the design ratio its
file records from the tooth counts (`extended.drivetrainChain.gearboxRatio.ratioFromTeeth`,
97.02).

| Product | `meshModule` (driver over driven) | Data |
|---|---|---|
| `ornithopter` | `LINT-6015` over `FR-ORN-ROUE-001` | the FR peg wheel is recorded with `module_mm` 12.7, a half-inch module, against the 12 mm of the ES lantern pinion it meshes |
| `wind-turbine` | `D-37028` over `D-37031` | the DE high-speed pinion is recorded with `modul_mm` 18 against the 20 mm of the intermediate stage wheel it meshes |

### Variants and options

A variant group is one installation slot of a product and the options it can
take: the wind turbine's foundation, the beam engine's acting and its governor,
the difference engine's output apparatus, the ornithopter's wing return and crank
build. The base product is the default configuration: the default option names
the base items it is made of, and every other option states what it changes
against it. `extended.variants` in the product file:

```
extended.variants.<group> = {
  name, selects, default: <option code>,
  options: { <option code>: {
    default: bool, source?, applicability?,  // applicability: the product model or build the option applies to, in words
    parts:       [id | Part],        // ids: base items (the default option lists its own);
    assemblies?: [id | Assembly],    // objects: items only this option adds, in the base item shapes
    interfaces:  [id | Interface],   // an option interface's features carry "port"
    ports?:      { <IF>: { <feature id>: <port> } },   // the default option's: ports of base features
    portsNotModelled?: [port],       // ports the host has for this option that the file does not model
    replaces?:   { parts: {old: new}, assemblies: {old: new}, interfaces: {old: new},
                   removes: { parts: [], assemblies: [], interfaces: [] } },
    bomLines?, functionalEdges?, externalRefs?,
    seededDefects?                   // the interface rules' failures on the option's own interfaces
  } } }
```

An option takes out exactly the items its group's default option lists, by
replacing or removing them; its configuration is the base without them, with
the option's own items, lines and interfaces. An assembly replaced or removed
takes its lines with it, and an item no line of the configuration reaches any
more leaves it too; an assembly may be replaced by a single part
(`replaces.assemblies`), whose line the option's `bomLines` give. A host is a
part both configurations hold; a port is the connection point a host's feature
serves, named the same under every option. `data/generate.py` (with
`data/variants.py`) refuses a group without exactly one default option, an
option code used twice in the dataset, an option that replaces or removes an id
the base does not hold or the default option does not list, an option object
whose id the base holds, an interface of a configuration joining an item the
configuration does not hold, a port named twice on one part within a
configuration, a feature given two ports, an option item without one line of the
option, and an option whose interfaces fail other rules than its
`seededDefects` say. A base feature an option interface mates again is the same
row, stated as the base states it.

The sites store what exists only under an option with the option's code, and
every feature's port, in columns of their versioned migration `V11`:

| PLM | Option code (part and feature tables) | Port (feature tables) |
|---|---|---|
| FR | `variante` | `point_raccordement` |
| DE | `variante` | `anschluss` |
| UK | `option_code` | `port_name` |
| ES | `opcion` | `puerto` |

The mappings read the code as `atelier:appliesUnderOption` to the option IRI
and the port as `atelier:port`. An option's own items have a tag in the core and
no `product_part` row, and their lines are not in the sites' trees, so no answer
of the base product names them, counts them in its bill of materials or weighs
them; a base run also leaves out every feature row that carries an option code.
The options graph (`data/options.ttl`, named graph
`https://example.com/atelier/graph/options`) holds each group
(`atelier:Variant`, `atelier:ofProduct`, `atelier:defaultOption`), its options
(`atelier:Option`, `atelier:optionOf`, `dcterms:source`, `atelier:applicability`,
`atelier:portNotModelled`), `atelier:appliesUnderOption` on the base items and
interfaces of each default option and on every option's own items, and the
option interfaces, lines and functional edges with the facts the links graph
states for the base ones, and the occurrences of an option line with
placements (`<line IRI>/occurrence/{n}`, `atelier:Occurrence` with
`atelier:ofLine`, `atelier:index`, the translations in the site's unit and the
rotations, as the sites map theirs). An option's external references are
checked and not written.

| Product | Group | Default option | Other options |
|---|---|---|---|
| `wind-turbine` | `foundation` | `onshore`: `ES-3788` (anchor cage `ES-3727`, anchor bolts `ES-3721`), `IF-125` | `offshore`: the transition piece and monopile `ES-3789` (3 assemblies, 10 parts, 14 lines), `IF-126`, `IF-127`; 41 interfaces, 3,797 part occurrences |
| `steam-engine` | `acting` | `double-acting`: `D-33006`, `IF-19`, `IF-20` | `single-acting`, the equilibrium valve and arch head not modelled |
| `steam-engine` | `governor` | `with-governor`: `UK-3303`, `UK-3305`, `IF-03`, `IF-10`, `IF-24` | `without-governor` |
| `difference-engine` | `output` | `engine-with-output`: `FR3660`, `FR3661`, `FR3663`, `FR3664`, `ES-3664`, `ES-3669` and ten interfaces | `engine`: 35 interfaces, 5,767 part occurrences |
| `ornithopter` | `wing-return` | `stirrup-return`: `CRET-L-6075`, `CRET-R-6085`, `IF-157` to `IF-160` | `spring-return`: `SPRG-6210-L` (UK), `FEDR-R-61110` (DE), `IF-180` to `IF-183`; `IF-181` fails its fastener rule, the UK 0.3150 in anchor pin against the FR 10 mm bore |
| `ornithopter` | `crank-build` | `assembled`: `MANV-6010` and its five parts, `IF-25`, `IF-32`, `IF-150`, `IF-161` to `IF-164` | `one-piece`: `MANV-6019`, `IF-184` to `IF-186`; the forged crank is drilled 10 mm as the FR beam, so `IF-25`'s fastener failure is not in this configuration |

## Annotations (the ORM is the catalogue)

On the JPA (Jakarta Persistence) entities of the object-relational mapping (ORM) layer (`plm-common`, runtime retention):
`@OntologyClass("atelier:Part")`, `@Describe("...")`, `@Maps("atelier:positionX")`,
and `@Unit("MilliM")` for a fixed unit or `@Unit(column = "pos_uom")` for a
per-row unit column holding a QUDT (Quantities, Units, Dimensions and Types) unit local name, with
`stores` naming the units the site writes there (`@Unit(column = "pos_uom", stores = "IN")`);
`@Accepts` gives a text column its closed vocabulary (a lifecycle column's four words) or its form
(a revision, the URN syntax), which the catalogue lists and a correction must respect. The same annotations drive
`GET /{plm}/catalogue` and the generated R2RML (RDB to RDF Mapping Language; `atelier.plm.common.r2rml.R2rmlGenerator`,
run by `modules/ontop/scripts/generate-mappings.sh`); R2RML is never written by
hand, and CI fails when the committed mappings differ from the generated ones.
A bill-of-materials line is `@OntologyClass("atelier:BomLine")` on a link table
or a view, its subject minted over the columns mapped to `atelier:parent` and
`atelier:child`; a part entity with a column mapped to `atelier:parent` (DE)
yields a second TriplesMap over the part table for its lines. Columns mapped to
`atelier:parent` and `atelier:child` become part IRIs. An external reference is
`@OntologyClass("atelier:ExternalReference")`, its subject minted over its `id`;
its column mapped to `atelier:fromPart` becomes a part IRI and its note is
`rdfs:comment`. A line placement is `@OntologyClass("atelier:Occurrence")`, its
subject minted over its columns mapped to `atelier:parent`, `atelier:child` and
`atelier:index`; the first two name its line, `atelier:ofLine`, and are not
stated on the occurrence. The generated mappings
are `modules/ontop/mappings/<plm>.r2rml.ttl`, and Ontop's schema metadata
includes the ES view. The catalogue lists the line tables, the view and the ES
document column.
`scripts/check-ontology-terms.py` fails when a mapping or a shape uses an
`atelier:` term the ontology does not declare.

## IRIs

- Ontology namespace `atelier:` = `https://example.com/atelier/ontology#`
- Part `https://example.com/atelier/{plm}/part/{id}`
- Plug `https://example.com/atelier/{plm}/plug/{id}`, fastener `.../{plm}/fastener/{id}`, coupling `.../{plm}/coupling/{id}`
- Quantity value nodes `<feature IRI>/position/{x|y|z}`, `<feature IRI>/diameter`, `<feature IRI>/grip`, `<feature IRI>/rating`
- Interface `https://example.com/atelier/interface/{product key}/{id}`: the last segment is the interface's id, unique within the product; the segment before it is the key of its product
- Product `https://example.com/atelier/product/{key}` (the integrator's own, no PLM segment)
- Bill-of-materials line `https://example.com/atelier/{plm}/bomline/{parent}/{child}`, the native keys of its two items; the DE line is minted from the child row and its `parent_id`
- Occurrence `https://example.com/atelier/{plm}/occurrence/{parent}/{child}/{index}`, its line's two items and its number on the line; translation nodes `<occurrence IRI>/translation/{x|y|z}`
- External reference `https://example.com/atelier/{plm}/ref/{id}`, the PLM that stores it and its surrogate key
- Station `https://example.com/atelier/station/{product key}/{id}` and section `https://example.com/atelier/section/{product key}/{id}`, minted by the layer in the links graph; position and tolerance nodes `<station IRI>/position`, `<station IRI>/tolerance`; span nodes `<part or occurrence IRI>/span-from`, `/span-to`
- Supplier `https://example.com/atelier/{plm}/supplier/{key}` and supplier offer `https://example.com/atelier/{plm}/offer/{id}`; a purchased item's size nodes `<part IRI>/nominal-diameter`, `<part IRI>/nominal-length`, `<part IRI>/inner-diameter`, `<part IRI>/cross-section`, `<part IRI>/width`, `<part IRI>/height`, `<part IRI>/base-width`, `<part IRI>/depth`, `<part IRI>/contour-width`, `<part IRI>/outer-diameter`, `<part IRI>/section-width`, `<part IRI>/rim-diameter` and `<part IRI>/heat-stack-diameter`
- `{plm}` is lower case (`fr`, `de`, `uk`, `es`); `{id}` is the native key, percent-encoded as R2RML does (a space becomes `%20`).
- QUDT: `qudt:` = `http://qudt.org/schema/qudt/`, `unit:` = `http://qudt.org/vocab/unit/`.
- Variant group `https://example.com/atelier/variant/{product key}/{group key}`; option `https://example.com/atelier/option/{option code}`, the code unique across the products; an option's bill-of-materials line `<option IRI>/line/{parent}/{child}` and functional edge `<option IRI>/drive/D{n}`
- Named graphs in Neptune: `https://example.com/atelier/graph/links`, `https://example.com/atelier/graph/fileindex`, `https://example.com/atelier/graph/labels` and `https://example.com/atelier/graph/options` (the variant groups, see Variants and options).

## Ontology terms

Classes: `atelier:Product`, `atelier:Part`, `atelier:Interface`,
`atelier:InterfaceFeature` with subclasses `atelier:Plug`, `atelier:Fastener`,
`atelier:HydraulicCoupling`, `atelier:BomLine` (a reified bill-of-materials
line, the AP242 `next_assembly_usage_occurrence`), `atelier:ExternalReference`
(a part's use of another site's part, named by URN), `atelier:Occurrence` (one
use of a line's child with its placement, the transformation of an AP242
`next_assembly_usage_occurrence`), `atelier:Drive` (a functional edge, from the
link store), `atelier:Station` and `atelier:Section` (from the link store).

Virtual-graph properties (from the PLMs): `atelier:identifier`, `atelier:label`,
`atelier:sourceFileRef` (the PLM's own file reference, informational),
`atelier:revision`, `atelier:lifecycleLabel`, `atelier:mass` (to a
`qudt:QuantityValue`, node `<part IRI>/mass`), `atelier:material` and
`atelier:partType` and `atelier:ownedBy` on parts, `atelier:toothCount` and
`atelier:gearModule` (to a `qudt:QuantityValue`, node `<part IRI>/module`) on gears,
`atelier:spanFrom` and `atelier:spanTo` (QuantityValues, MilliM or IN) on parts and occurrences;
`atelier:onPart`, `atelier:positionX|Y|Z` (to a `qudt:QuantityValue` with
`qudt:numericValue` and `qudt:unit`), `atelier:ownedBy` (literal `"FR"` etc.) on
every feature, plus per class:

| Class | Properties |
|---|---|
| `atelier:Plug` | `atelier:connectorType`, `atelier:pinCount` |
| `atelier:Fastener` | `atelier:fastenerStandard`, `atelier:diameter` (QuantityValue, MilliM or IN), `atelier:fastenerCount` (integer), `atelier:gripLength` (QuantityValue) |
| `atelier:HydraulicCoupling` | `atelier:couplingStandard`, `atelier:dashSize` (integer), `atelier:pressureRating` (QuantityValue, BAR or PSI), `atelier:fluid` |
| `atelier:BomLine` | `atelier:parent` and `atelier:child` (both to `atelier:Part`, items of the same PLM), `atelier:quantity` (`xsd:decimal`) |
| `atelier:Occurrence` | `atelier:ofLine` (to `atelier:BomLine`), `atelier:index` (`xsd:integer`, 1 for the reference occurrence), `atelier:translationX|Y|Z` (QuantityValue, MilliM or IN), `atelier:rotationX|Y|Z` (`xsd:decimal`, degrees, applied about x, then y, then z) |
| `atelier:Station` | `skos:notation` (its id), `atelier:sectionOf` (its product), `atelier:stationPosition` and the optional `atelier:stationTolerance` (QuantityValues, MilliM), `atelier:stationBasis` (a concept of `atelier:StationBases`: `atelier:PrintedStation`, `MeasuredStation`, `JointStation`, `InferredStation`), `dcterms:source` |
| `atelier:Section` | `skos:notation`, `rdfs:label`, `atelier:sectionOf`, `atelier:ownedBy`, `atelier:sectionSide` (`left`, `right`, `both`), `atelier:hasStation`, the optional `atelier:sectionPart` (to `atelier:Part`); the product carries `atelier:stationAxis`, `atelier:stationsSymmetric` and `atelier:stationMeasure` |
| `atelier:ExternalReference` | `atelier:fromPart` (to `atelier:Part`, the local part), `atelier:remoteUrn` (string, as stored), `atelier:expectedRevision` (string), `atelier:quantity` (`xsd:decimal`), `rdfs:comment` (the note) |

Purchased items and suppliers (from the PLMs): `atelier:standard` and
`atelier:nominalDiameter`, `atelier:nominalLength` (QuantityValues, MilliM or
IN) on parts; `atelier:itemClass`; `atelier:innerDiameter`, `atelier:crossSection`,
`atelier:itemWidth`, `atelier:itemHeight`, `atelier:baseWidth`, `atelier:itemDepth`,
`atelier:contourWidth`, `atelier:outerDiameter`, `atelier:sectionWidth`, `atelier:rimDiameter`
and `atelier:heatStackDiameter` (QuantityValues); `atelier:plyRating` and `atelier:rotorCount` (`xsd:integer`); `atelier:compound`, `atelier:legend`,
`atelier:facestock`, `atelier:adhesive` and `atelier:shellMaterial` (strings in the
site's language); and `atelier:shelfLifeMonths` (`xsd:integer`) on parts; `atelier:Supplier` with `atelier:label` and `atelier:location`;
`atelier:SupplierOffer` with `atelier:offersPart` (to the part), `atelier:fromSupplier`
(to a supplier of the same PLM), `atelier:supplierPartNumber`,
`atelier:leadTimeDays` (`xsd:integer`) and `atelier:preferred` (`xsd:boolean`).
`atelier:builtBy`, the file index's supplier name, is distinct.

Variants (from the options graph, and the option code and port from the PLMs):
`atelier:Variant` (one installation slot of a product) with `atelier:ofProduct`
and `atelier:defaultOption`; `atelier:Option` with `atelier:optionOf`,
`atelier:applicability` and `atelier:portNotModelled` (strings);
`atelier:appliesUnderOption` (to an `atelier:Option`) on an item, feature,
interface, line or functional edge that the product holds only under that
option, or that a default option is made of; `atelier:port` (string) on an
interface feature.

Fastener standards: the SKOS concept scheme `atelier:FastenerStandard` holds
one concept per kind of purchased item, with each standard a site records for it
as its `skos:prefLabel` or a `skos:altLabel`: `atelier:SocketHeadCapScrew`
(`ISO 4762`; `DIN 912`, the DIN standard it replaced with the same dimensions,
and the national adoptions `NF EN ISO 4762`, `UNE-EN ISO 4762`,
`BS EN ISO 4762`). A standard no concept carries (`DIN 934`, `NF EN ISO 10642`)
designates no known item. The ontology, not the query service, decides which
standards designate one item.

Item classes: the scheme `atelier:ItemClasses` holds one concept per class of
purchased item, its `skos:notation` the value of a part's `atelier:itemClass`.
Each class names its identifying attributes with `atelier:identifiedBy`; an
attribute states the part property it compares (`atelier:onAttribute`), its
place (`atelier:attributeOrder`), the tolerance of a length in millimetres after
conversion (`atelier:matchToleranceMm`) and, for a text, the scheme it resolves
in (`atelier:resolvedIn`), and its key in a product file's `extended.purchased`
(`atelier:stagingKey`). A class may name the scheme its standards designate
sizes in (`atelier:standardScheme`): the size concept supplies the attributes a
part does not state. These scheme properties are `owl:AnnotationProperty`.

| Class | Identifying attributes |
|---|---|
| `atelier:FastenerItem` (`fastener`) | `atelier:standard` resolved in `atelier:FastenerStandard`; `atelier:nominalDiameter` and `atelier:nominalLength` within 0.1 mm (an inch catalogue's three decimals convert up to 0.013 mm from the metric size) |
| `atelier:ORingItem` (`o-ring`) | `atelier:innerDiameter` and `atelier:crossSection` within 0.01 mm, stated or given by the standard's size in `atelier:ORingSizes`; `atelier:compound` resolved in `atelier:Materials` |
| `atelier:ContainerItem` (`container`) | `atelier:standard` resolved in `atelier:ULDTypes`; `atelier:baseWidth`, `atelier:itemDepth`, `atelier:itemHeight` and `atelier:contourWidth` within 1 mm; `atelier:shellMaterial` resolved in `atelier:Materials` |
| `atelier:TyreItem` (`tyre`) | `atelier:outerDiameter` and `atelier:sectionWidth` within 1 mm; `atelier:rimDiameter` within 0.5 mm; `atelier:plyRating` |
| `atelier:WheelItem` (`wheel`) | `atelier:rimDiameter` within 0.5 mm; `atelier:itemWidth` within 1 mm |
| `atelier:BrakeItem` (`brake`) | `atelier:heatStackDiameter` within 1 mm; `atelier:rotorCount` |
| `atelier:CordItem` (`cord`) | `atelier:nominalDiameter` within 0.1 mm; `atelier:material`, the part's own, resolved in `atelier:Materials` |
| `atelier:CanisterItem` (`canister`) | `atelier:baseWidth`, `atelier:itemDepth` and `atelier:itemHeight` within 1 mm; `atelier:shellMaterial` resolved in `atelier:Materials` |
| `atelier:PlacardItem` (`placard`) | `atelier:legend`, case and spaces ignored; `atelier:itemWidth` and `atelier:itemHeight` within 0.5 mm; `atelier:facestock` resolved in `atelier:Materials`, the thickness written in it within 0.01 mm; `atelier:adhesive` resolved in `atelier:Materials` |

O-ring sizes: `atelier:ORingSizes` holds the AS568 dash sizes (`AS568-014`,
`-214`, `-222`, `-332`) and the ISO 3601-1 codes of the same rings (`ISO 3601-1
014A` with the designation `O-ring ISO 3601-1 - 014A - 12,42 x 1,78 - N`, and
`214A`, `222A`, `332A`), each with `atelier:innerDiameter` and
`atelier:crossSection` in millimetres; an AS568 size is `skos:exactMatch` its ISO
code. Materials: `atelier:Materials` holds one concept per material, adhesive or
compound with each language's words as labels: `atelier:PolycarbonateFilm`
(`polycarbonate film`@en, `film polycarbonate`@fr, `Polycarbonatfolie`@de,
`película de policarbonato`@es), `atelier:AcrylicPressureSensitiveAdhesive`,
`atelier:EPDM80PhosphateEsterResistant` (`EPDM 80 Shore A, phosphatesterbeständig`@de,
`EPDM 80 Shore A, résistant aux esters phosphates`@fr, `EPDM 80 Shore A, phosphate
ester resistant`@en), `atelier:EPDM70SteamResistant`, `atelier:NBR70`, `atelier:FKM75` and
`atelier:AluminiumAlloy`. Unit load device types: `atelier:ULDTypes` holds
`atelier:ULDTypeAKH` (`AKH`; `IATA AKH`, `LD3-45`, `LD3-45W`). A compound carries its hardness and qualifiers in its labels, so
`EPDM` alone names no concept. A text resolves to the concept of the scheme that
carries it as a label, case, spaces and hyphens ignored; a new class, size or
material is a concept, not code.

`atelier:assembles` (Part to Part, "parent assembles child") is the shortcut of
a line, derived by the query service for the lines between items the viewer
may see. `atelier:contains` (Part to Part) is the site-local reflexive
transitive closure of the lines, from each PLM's closure table: an item contains
every item under it in its own site's tree, and itself; it never crosses to
another site, which an external reference does. It declares no domain or
range, since Ontop would otherwise read every closure row as a part. `atelier:occurrences` (`xsd:decimal` on an item) is how many times the
item occurs in its product, derived by the products answer from the lines (see
"Rules").

Core-graph properties (the integrator's own, from `atelier_core`):
`atelier:jurisdiction`, `atelier:releasableTo`, `atelier:taggedBy`,
`atelier:taggedAt` on parts; `atelier:partOf` (Part to Product); `atelier:label`,
`atelier:frame` and `atelier:massLimit` (to a `qudt:QuantityValue` in `unit:KiloGM`,
node `<product IRI>/mass-limit`) on products.

Link-store properties (Neptune): `atelier:ofProduct` (Interface to Product, one
value), `atelier:betweenPart` (Interface to Part, two
values), `atelier:declaresFeature` (Interface to every feature),
`atelier:declaresPlug` (the plugs again), `atelier:matesWith` (feature to feature
of the same class, symmetric, stated both ways), `atelier:toleranceMm`
(Interface, decimal), `rdfs:label` (Interface), `owl:sameAs` (part to part, a confirmed
equivalence, stated both ways); functional edges: `atelier:Drive`
with `atelier:ofProduct`, `atelier:driver` and `atelier:driven` (to the parts),
`atelier:flow` (string), `atelier:viaInterface` (to the interface, when the joint is
one) and `atelier:reactionPart` (to the fixed gear of a planetary stage),
`atelier:drives` (Part to Part, beside each edge, the chain of the inverse of
`atelier:driver` and `atelier:driven`), `atelier:ratedSpeedRpm` (decimal, on a part);
file index: `atelier:cadFile` and `atelier:builtBy` on parts; labels graph: `skos:prefLabel` and
`skos:altLabel` on items and the concepts of `atelier:Terms`.

QUDT multipliers in `ontology/units.ttl`: `unit:MilliM` 0.001 m, `unit:IN`
0.0254 m, `unit:BAR` 100000 Pa, `unit:PSI` 6894.757293168 Pa, `unit:KiloGM`
1 kg, `unit:LB` 0.45359237 kg. A feature quantity whose unit is not listed
fails the `unit` rule.

Lifecycle states: the SKOS concept scheme `atelier:Lifecycle` in
`ontology/atelier.ttl` holds `atelier:Working`, `atelier:Released`,
`atelier:Blocked` and `atelier:Superseded`, each with `skos:prefLabel`
`WORKING`, `RELEASED`, `BLOCKED` or `SUPERSEDED` and one `skos:altLabel` per PLM
word with the PLM's language tag (`"Freigegeben"@de`, `"Publié"@fr`,
`"Liberado"@es`, `"Released"@en`). The British `"Frozen"@en` sits on
`atelier:Blocked` by decision, stated in the concept's comment, since the word
is ambiguous. A part's canonical state is the concept whose altLabel equals its
`atelier:lifecycleLabel`; the query service merges the scheme into the graph
SHACL validates and resolves the state from it, so the ontology carries the
mapping of the four vocabularies.

Terms: the SKOS concept scheme `atelier:Terms`, declared in `ontology/atelier.ttl`,
is the products' glossary. Its concepts, `https://example.com/atelier/term/<slug>`
(`term/gear`, `term/crown-gear`), live in the labels graph of the link store with one
`skos:prefLabel` in each of `en`, `de`, `fr` and `es` (`"gear"@en`,
`"Zahnrad"@de`, `"engrenage"@fr`, `"engranaje"@es`), a `skos:altLabel` where a
language writes the term otherwise (`"roue dentée"@fr` on the gear, `"Gestell"@de`
and `"bâti"@fr` on the frame) and `skos:broader` to the term a term is a kind of
(crown gear, pinion and figure wheel to gear, hand crank to crank). The same graph
names every item: `skos:prefLabel` is its native name in its site's language
(`fr`, `de`, `es`, `en` for the UK) and `skos:altLabel "..."@en` its English name,
on every item of the French, German and Spanish sites; a UK item's native name is
its English name. `owl:sameAs` between two part IRIs, in the links graph, is a
user's confirmation that they are one item (see "Freshness").

## Rules

SHACL shapes in `ontology/shapes.ttl`, each tagged `ateliersh:rule`; the
message is the shape's own text with the measured values filled in.

| Rule | Severity | Fails when |
|---|---|---|
| `unit` | Violation | a quantity of a mated feature has no unit or a unit without a known multiplier |
| `position` | Violation | two mated features are more than the interface's `toleranceMm` apart on any axis after conversion |
| `kind` | Violation | two mated features are not of the same class |
| `connector` | Violation | mated plugs differ in connector type or pin count |
| `fastener` | Violation | mated fasteners differ in standard or count, or their diameters differ by more than 0.05 mm after conversion |
| `hydraulic` | Violation | mated couplings differ in standard, dash size or fluid, or their ratings differ by more than 2 % after conversion |
| `orphan` | Violation | a feature declared on an interface mates with nothing |
| `doubleMate` | Violation | a feature mates with more than one feature (`sh:maxCount 1` on `atelier:matesWith`) |
| `cadMissing` | Warning | a visible part with geometry (part type `PART` or unstated, selected by a SPARQL-based target) has no `atelier:cadFile` in the file index |
| `danglingReference` | Warning | an external reference a part makes has a URN that is not a `urn:plm:(de\|fr\|es\|uk):part:<local id>` key, or that resolves to no part of any PLM |
| `staleRevision` | Warning | the part an external reference resolves to, visible to the viewer, is at a revision other than the expected one (compared as text) or its canonical lifecycle state is `SUPERSEDED` |
| `conflictingLeadTime` | Warning | a part has two preferred supplier offers with different lead times (both offers are of the part's PLM); one result per pair, `sh:value` the longer offer |
| `massScale` | Warning | within one product, a site's lower median part mass in kg is more than 100 times the lower median of the other sites' medians (sites of at least five visible parts of type `PART` with a mass, the site being the part's `atelier:ownedBy`); the result's focus is the product and each part of the site is its `sh:value` |
| `lifecycleConflict` | Warning | an item whose canonical lifecycle state is `RELEASED` depends on an item, visible to the viewer, whose state is `WORKING` or `BLOCKED`: a child of one of its bill-of-materials lines, or the part one of its external references resolves to; the focus is the dependent item, `sh:value` the dependency |
| `meshModule` | Warning | two gears joined by a mechanical `atelier:Drive`, both visible to the viewer, have modules that differ by more than 0.01 mm after conversion to millimetres (0.0984 in, 2.4994 mm, meshes 2.5 mm; 0.1 in, 2.54 mm, does not); the focus is the driven gear, `sh:value` the driving gear; the parts answer (with `root`, of the subtree's gears), the flow answer and the rules failures report it |
| `massLimit` | Violation | a product's items weigh more than its `atelier:massLimit`: occurrences (`atelier:occurrences`) times unit mass in kg, summed over every item that states a mass; the focus is the product |

The reference shapes resolve a URN to a part IRI by string functions alone:
`urn:plm:<site>:part:<local id>` becomes `https://example.com/atelier/<site>/part/<local id>`,
the id percent-encoded as R2RML does, `UK/nnnn` read as `UK-nnnn`. Both focus
the part that makes the reference (`sh:value` the reference). Existence is read
from the core graph's product memberships of every part, which the query
service merges into the data graph whatever the viewer may see, so a reference
to a part hidden from the profile is not dangling; its revision cannot be
compared either, so it raises no `staleRevision`.

Each site releases its items in its own words (Freigegeben, Publié, Liberado,
Released); only the layer holds both the canonical lifecycle (the
`atelier:Lifecycle` scheme) and the dependencies across sites, so only the layer
sees a released item over one another site, or its own, has not released or has
blocked. `lifecycleConflict` reads two dependencies: the child of an
`atelier:BomLine` whose parent is the item, and the target of one of the item's
external references, resolved by the reference shapes' resolution text, copied
character for character (a test counts the copies). The message names both items
by PLM, id and name, each site's own word and the canonical states, for example
"ES ES-3303 Tubería de vapor is Liberado (RELEASED) but references
urn:plm:de:part:D-33001, DE D-33001 Dampfzylinder, which is In Arbeit (WORKING)".
A dependency the viewer may not see keeps neither its label nor its lifecycle word
after redaction, so it raises nothing: the rule is not evaluable for that viewer,
as the reference rules are not. A dependency that is `SUPERSEDED` is the
`staleRevision` rule's concern, not this one's. Every answer that reports part
findings reads the lines from each visible item beside its references (an
interface's neighbourhood holds the lines from its parts and the label, revision
and lifecycle word of each visible child), so the finding is on the part wherever
it appears; the products answer counts each product's conflicts as
`lifecycleConflicts`, and the Bill of materials screen marks each node that
carries one.

`data/generate.py` (with `data/lifecycle.py`) applies the rule to the lines and
reference rows it writes and checks that it fires on exactly the pairs each
product lists in `seededLifecycleDefects` (`item`, `dependency`, `rule`). An
external reference links two sites, so `data/generate.py` refuses one whose URN
names a part of the referring item's own site; a URN under the own site's code
that names no part of it (the cubesat's wrong site code) stays a dangling
reference. The rover's released wheel `FR3801` over the draft hub insert
`ES-3833` is the proposed lifecycle defect the rule reports. The steam engine's
piston rod over the cylinder whose gland it runs through and the wind turbine's
controller software over the cabinet it runs on need a same-site dependency
relation the model does not carry, so the rule cannot report them and their
proposed defects say so.
The difference engine's column group `D-36301` is blocked while the plates it
references are released: a blocked item over released ones is not a conflict,
and no released item depends on the group, so the rule raises nothing there. The
glider's left covering and leading edge binding reference the blocked leading rib
of another site. The other pairs are released site kits and assemblies over a working or blocked part.

| Product | `lifecycleConflict` (item over dependency) |
|---|---|
| `ornithopter` | `CONJ-6000` over `SERV-6120`, `FR-ORN-KIT-001` over `FR-ORN-CERC-001`, `KIT-6100-L` over `HARN-6200-L` |
| `aerial-screw` | `BAUS-70000` over `VENT-70110`, `FR-VIS-KIT-001` over `FR-VIS-PCMD-001`, `KIT-7000` over `INST-7070` |
| `cart` | `UK-3100` over `UK-3103` |
| `antikythera` | none |
| `steam-engine` | `ES-3303` over `D-33001`, `UK-3300` over `UK-3305` |
| `glider` | `FR3401` over `ES-3401`, `FR3407` over `ES-3401` |
| `cubesat` | none |
| `difference-engine` | none |
| `wind-turbine` | `D-37033` over `D-37034`, `ES-3702` over `ES-3724`, `ES-3706` over `D-37014`, `ES-3718` over `D-37043`, `UK-3719` over `D-37049`, `UK-3720` over `D-37049`, `UK-3734` over `D-37043` |
| `rover` | `FR3801` over `ES-3833`, `FR3876` over `FR3818` |

An interface's `status` is `pass` or `fail` from the Violation results on its own
features only. Warnings are findings: reported on the part as
`findings: [{ rule, message, value? }]` wherever a visible part appears (`value`,
`{ plm, kind, id }`, names the PLM record the result's `sh:value` is when it is
not the part itself: the dependency part of a `lifecycleConflict`, the longer
offer of a `conflictingLeadTime`, the reference of a reference finding), counted per
rule at the top of `/query/interfaces` as `findings: { cadMissing: n, danglingReference: n, lifecycleConflict: n, massScale: n, staleRevision: n }`,
and never failing an interface. The supplier offers are read by the parts answers
(`/query/parts`, `/query/suppliers`, `export_status`), so `conflictingLeadTime`
is a finding there and not in the interface answers. A `massScale` result is reported on the part its
`sh:value` names: the medians are computed once per product, which one focus
per part would repeat for every part of the site. It needs a whole product, so
the parts answer, the interfaces answer of a product or of every product and the
products answer carry it, and a single interface's answer, whose graph holds two
parts, does not. The difference engine's French site, which writes grams into
`piece.masse_kg`, is the case: its median is 119 times the others'.

The product rules are findings of the product in `/query/products`, each once.
`massLimit` needs each item's occurrences: SPARQL cannot multiply quantities
along a path of any length, so the products answer derives
`item atelier:occurrences n` from the bill-of-materials lines it federates (the
quantities multiplied down from the site kit and summed over every path, as the
roll-up counts them) before the shapes run. An item the viewer may not see adds
nothing, so a product rule is evaluable only for a viewer who sees every item of
the product: when any item is hidden, the product's `massLimit` and `massScale`
results are dropped from every answer, as an interface with a redacted side is
not evaluated, since a hidden item could carry the mass that breaks the limit or
shift a site's median either way. The products answer states each product's
limit as `massLimit: { status, limitKg, hiddenItems }`: `pass` or `fail` over
every item, or `not-evaluable` with the number of hidden items. The cubesat fails
for the export-control officer, "cubesat weighs 2.01 kg over every site's parts,
more than its limit of 2.0 kg"; programme-cleared, which may not see its LICENSED
board, gets `not-evaluable` with one hidden item. A part without an export-control tag stays under
`policy.untagged`.

## Query service API

Behind API Gateway at `/api/query/*`; every call carries `x-atelier-profile`
and, when an agent asks, `x-atelier-actor`.

| Method and path | Answer |
|---|---|
| `GET /query/products` | `{ products: [{ key, name, frame, partCount, findings?, massLimit?, lifecycleConflicts }], provenance, sparql, timings, policy }`; `partCount` is the number of the product's parts with geometry (part type `PART`) the profile may see; `findings` lists the product rules' findings (`[{ rule, message }]`, absent when none), from a bill-of-materials federation of every product; `massLimit` is `{ status: pass, fail or not-evaluable, limitKg, hiddenItems? }` for a product with a limit (see "Rules"); `lifecycleConflicts` is the number of `lifecycleConflict` findings on the product's visible items, one per item and dependency |
| `GET /query/products/list` | `{ products: [{ key, name, frame, partCount }], provenance, sparql, timings, policy }`: the products and part counts of `GET /query/products`, without the product rules, from a federation of every product's parts that asks each PLM for their description only and runs no validation, so it answers before the rules; the web reads it first and takes the findings, mass limits and lifecycle conflicts from `GET /query/products` when that answers |
| `GET /query/parts[?product=key[&root=id \| &option=code]]` | `{ parts: [Part], provenance, sparql, timings, policy, subtree? }`; assemblies, software and documents are listed too, with `partType` `ASSEMBLY`, `SOFTWARE` or `DOCUMENT` and no `cadUrl`; with `root`, the parts of that item's subtree (below); with `option`, the parts of the product under that option (Configured answers, below) |
| `GET /query/interfaces[?product=key[&root=id]]` | `{ interfaces: [Interface], findings, provenance, sparql, timings, policy, subtree? }`, the interfaces in id order (IF-99 before IF-100), then by product; with `root`, the interfaces with a side in the subtree |
| `GET /query/interfaces/{id}[?product=key]` | `{ interface: Interface, provenance, sparql, timings, policy }` |
| `GET /query/interfaces/{id}/evidence[?product=key]` | Evidence (below) |
| `GET /query/parts/{id}/where-used` | the interfaces naming the part, its mates, feature counts per kind |
| `GET /query/bom?product=key[&root=id]` | Bom (below); `product` required; with `root`, the tree under that item across the sites |
| `GET /query/placements?product=key[&root=id \| &option=code]` | Placements (below): the world placements of the visible geometric parts; `product` required; with `root`, of the subtree's parts; with `option`, of the product under that option |
| `GET /query/stations?product=key&from=<station>&to=<station>[&side=left\|right\|both]` | Between (below): the visible parts whose span overlaps the range between two stations; all three required, `side` defaults to `both`; an unknown station or side, or a product without stations, is 400 |
| `GET /query/sections?product=key` | Sections (below): the sections with their owners, parts and foreign parts, and the parts crossing each joint |
| `GET /query/parts/{id}/used-in?product=key` | BomWhereUsed (below): the part's parents in the product's bill of materials; an item outside the product is 404 |
| `GET /query/references?product=key[&root=id]` | References (below): every external reference of the product's visible parts; `product` required; with `root`, of the subtree's parts |
| `GET /query/equivalents?product=key` | Equivalents (below): the purchased parts that are one item; `product` required |
| `GET /query/terms?q=<text>` | Terms (below): the concepts of the products' glossary a term in English, German, French or Spanish names, and the visible items of every product whose names hold them; `q` 1 to 64 letters, digits, spaces, hyphens or apostrophes, 400 otherwise |
| `GET /query/suppliers?product=key` | Suppliers (below): the suppliers of the product's parts per site, the single-source parts and the lead-time conflicts; `product` required |
| `GET /query/variants?product=key` | `{ product, groups: [{ key, name, selects, defaultOption, options: [code] }] }`: the product's variant groups, the default option first; the policy does not filter it |
| `GET /query/variant-diff?product=key&group=key&option=code` | VariantDiff (below): the option against its group's default, port by port, with the rules run on each configuration; an unknown group or option is 404 naming the product's groups and options, the default option is 400 |
| `GET /query/impact?part=<id>` or `?feature=<id>` | the interfaces and mated features a change would touch (exactly one of the two parameters) |
| `GET /query/parts/{id}/export-status` | jurisdiction, releasability, who tagged it and when, visibility, CAD availability |
| `GET /query/ontology` | the ontology as the `ontology` tool describes it |
| `GET /query/rules` | every shape described (below); the same for every profile |
| `GET /query/rules/failures[?product=key[&root=id][&option=code]]` | the records each rule fails for the profile, of one product with a count per rule of the other products where it fails, or across every product (below) |
| `POST /query/preview` | Preview (below): the rules before and after a correction's cells, written into a copy of the product's merged graph; writes nothing |
| `POST /query/mcp` | the MCP (Model Context Protocol) server, streamable HTTP; `DELETE` ends a session |
| `GET /query/mcp/tools` | `{ server, tools: [{ name, description }], count }` from the same registry |
| `GET /query/demo/changes` | the change feed (below); any profile but `unknown` |
| `GET /query/demo/health` | the event names published and subscribed, the released graph sizes, the change-log table; no data |
| `GET /query/health` | liveness |
| `GET /query/health/warmup` | `{ status, profiles: { <profile>: { status, ms, attempts, failures: [{ attempt, reason, silent? }] } } }`: the warm-up that translates every request once per profile after start: the full answer, then the profile's first screen (the product list, then the parts, placements and interfaces of the list's first product); `status` is `pending`, `running` (the first pass), `warming` (a failed profile is warmed again), `done` (every profile done, or the budget spent) or `disabled`; a profile is `done` with its answer's time, `warming` while it is warmed again, or `failed` once the budget is spent; `attempts` counts every attempt; `failures` lists every failed attempt with its reason (the failure and its root cause, a refused connection or a timeout) or, for an attempt skipped because endpoints did not answer, their names under `silent`; each failed attempt is logged at WARN. Each attempt starts when every SPARQL endpoint answers `ASK {}`; a failed one is retried after a backoff of 2 s doubling, at most 5 attempts in a round; after the first pass, a profile whose round failed gets another round, with a wait between rounds of 2 s doubling up to 60 s, until it is done or 15 minutes have passed, with no restart |

`product` scopes a listing to one product; without it a listing covers every
product. A key no interface's part belongs to is 404 `{ error: "no product <key>" }`;
a key outside `[A-Za-z0-9._-]{1,32}` is 400.

On the two single-interface routes, `product` names the product the id belongs
to: the interface is `{product}/{id}`. Without it the id must belong to exactly
one product. When several products have an interface of that id the answer is
400 `{ error, id, products: [key] }`, naming the id and the candidate product
keys and asking for `product`; an id no product has is 404
`{ error: "no interface <id>" }` (`no interface <product>/<id>` when the product
was named). The run behind a single-interface answer is the profile's federation
over every product, so `product` never changes the request texts sent.

```
Part = { id, plm, name, nameEn?,                       // nameEn: the labels graph's English name, in the parts answer
  cadFile, cadUrl, sourceFileRef,
  jurisdiction, releasableTo, taggedBy, taggedAt,      // the core tag; null on an untagged part
  supplier?,                                           // atelier:builtBy, the first by name: the supplier-built mark
  suppliers?: [{ name, role: "built" | "offered" }],   // every builtBy, then every offer's supplier ("name, town"); offers in the parts answer only
  findings?,
  revision?, lifecycle?, lifecycleState?,              // the PLM's revision and word; lifecycleState from the scheme
  mass?: { value, unit, kg },                          // as stored (KiloGM or LB) and converted to kg
  material?, partType?,                                // each attribute absent when the PLM states none
  context? }                                           // true on a subtree's far-side part, absent otherwise
Interface = { id, product,                            // id unique within the product; product is its key
  label, status: "pass" | "fail" | "not-evaluable", toleranceMm,
  parts: [Part | { redacted: true, plm }],
  features: [{ id, plm, partId, kind: "plug" | "fastener" | "coupling",
               properties: { ... },                 // class-specific values; quantities as { value, unit?, mm? | bar? }
               source: { x, y, z, unit },            // as stored, unit null if absent
               positionMm: { x, y, z } | null,       // normalised; null if a unit is missing
               matesWith: [featureId] } | { redacted: true, plm }],
  violations: [{ rule, shape, message, focusNode, features: [featureId], detail: {...} }] }
provenance = { calls: [{ endpoint: "ontop-fr" | "ontop-de" | "ontop-uk" | "ontop-es" | "ontop-core" | "neptune",
               kind: "virtual" | "materialized", requests, triples, ms,
               requestBytes, largestRequestBytes }] }  // UTF-8 bytes of the request texts sent, in total and the largest
sparql   = the federated request texts that ran
timings  = { totalMs, federationMs, validationMs }
policy   = { profile, releasable: [...], filter, untagged: [partIri], actor }
```

### Rules described

`GET /query/rules` describes every shape of `ontology/shapes.ttl` that carries
`ateliersh:rule`, from the shapes graph the validator loaded, sorted by name:

```
Rules = { rules: [{ name,                     // ateliersh:rule
  shape,                                      // the shape IRI
  severity: "Violation" | "Warning",          // sh:severity, Violation when the shape states none
  focus: "interface" | "part" | "reference" | "product",   // ateliersh:focus
  message,                                    // rdfs:comment: what the shape requires
  description,                                // sh:description: what it checks and why only the layer can, in plain English
  turtle,                                     // the shape and its constraints as Turtle, serialised from the loaded graph
  sparql: [{ role: "constraint" | "target", message | null, query }],   // each sh:sparql SELECT, and a SPARQL target's
  terms: [{ term, kind: "class" | "property" | "concept" | "other", definition | null }] }] }
                                              // the atelier: terms the shape names, its SPARQL included, with their rdfs:comment
```

Every shape carries `ateliersh:focus` and `sh:description`; the `ontology` MCP
tool lists the same rules by name, shape, severity and the first sentence of
the `rdfs:comment`.

`GET /query/rules/failures` lists, for the caller's profile, one entry per
record a rule fails, sorted by rule, product, kind and id (IF-99 before IF-100):

```
RuleFailures = { failures: [{ rule, product,  // product key
  kind: "interface" | "part" | "product",
  id,                                         // interface id, part id or product key
  plm }],                                     // the part's PLM code; null for an interface or a product
  otherProducts?: { <rule>: n },              // with product: the other products where the rule fails, a count
  provenance, sparql, timings, policy }
```

With `product`, the answer holds that product's records, scoped as the screens'
answers scope it: with `root`, the interfaces with a side in the subtree and the
subtree's parts, and no product record; with `option`, the parts of that
configuration, and the product records of the base product. `otherProducts`
counts, per rule, the other products where the rule fails for the caller,
from the caller's own every-product answer: a record the profile may not see,
or a product rule it cannot evaluate, is in no count, and no other product is
named. An unknown product is 404; `root` or `option` without `product` is 400.
The Rules screen asks for the product on screen, with its root and option;
nothing else calls the every-product answer, which stays for the API's
callers.

It is assembled from three answers over every product: the interfaces answer
(an interface a rule's violation fails), the parts answer (a part carrying a
rule's finding, under each product the core graph names it in; the reference
rules report the part that makes the reference, and the parts answer reads the
products' functional edges, so `meshModule` reports the driven gear) and the products answer (a
product rule's finding on a product, when the rule names none of the product's
parts: `massLimit`; `massScale` names the parts of the site). The counts per
product equal those of the per-product interfaces and parts answers, from three
federations instead of two per product.

### Paths through a product

`GET /query/paths?product=<key>&from=<part>&to=<part>` and
`GET /query/flow?product=<key>&from=<part>[&flow=<kind>][&direction=up]`, parts
named by native id (404 for an id that names no part of the product, 400 for an
unknown flow or direction). Both run the interfaces federation of the product
with one more link-store request, the product's functional edges and rated speeds,
then redact and validate as the interfaces answer does, so every step's interface
carries its status for the viewer's profile and every part its findings.

```
Paths = { product, from: Part | Redacted, to: Part | Redacted, maxLength: 12, maxPaths: 5,
  paths: [{ length, steps: [Step] }], notes: [string], parts: [Part], provenance, sparql, timings, policy }
Flow  = { product, from: Part | Redacted, flow?, direction: "down" | "up", maxSteps: 60, steps: [Step],
  ratio?: { to, toPlm, ratio, speedUp, stages: [Stage], text },
  checks: [{ from, fromPlm, fromRpm, to, toPlm, ratedRpm, predictedRpm, holds, text }],
  notes: [string], parts: [Part], provenance, sparql, timings, policy }
Step  = { from: { id, plm } | { redacted: true, plm }, to: (same),
  joint?: { id, label, status, rules: [rule] },          // the interface of the step; absent for an edge no interface joins
  flow?, reaction?: { id, plm } | { redacted, plm }, stage?: Stage }
Stage = { driver: Gear, driven: Gear, reaction?: Gear, ratio }   // ratio: input over output speed
Gear  = { id, plm, teeth, moduleMm }
```

A path is a chain of parts joined by interfaces (`atelier:betweenPart`): the
shortest ones, found by a breadth-first search over the merged graph, since a
SPARQL property path matches a path but cannot return it; at most 5 of at most
12 steps, in interface order. A part the viewer may not see is not passed
through: `notes` says when every path runs through one, when a shorter path
does, or when no path joins the parts; a hidden end answers its redaction marker
and no path. The cart's drive spring and its drive wheel share no interface
path, though the spring drives the wheel: the mesh between crown wheel and
lantern pinion is no interface, and the flow follows it.

A flow follows the functional edges from the part, breadth first, downstream
(driver to driven) or, with `direction=up`, upstream for root cause, of every
flow kind or of `flow` alone; each edge is a step once, so a loop shows its
closing edge and stops, and a part the viewer may not see is reached as a
redaction marker and not walked past. Downstream, the gear train is computed
along the mechanical edges: a mechanical edge between two gears is a mesh
whose `ratio` is driven over driver teeth; an edge with a `reaction` gives
sun over ring plus sun; any other mechanical edge passes the speed unchanged and
a non-mechanical edge ends the train. `ratio` is the overall ratio to the last
gear of the train with the most stages (`speedUp` its inverse, the output turns
per input turn) and the gears and sites involved; a mesh whose gears are hidden
or state no tooth count leaves the ratio beyond it uncomputed, and a note says
so. Each `check` carries the first rated speed the walk reaches through the train
to every rated speed downstream of it and compares within 1 %: the wind
turbine's hub at 12.1 rpm through 97.02 gives 1173.9 rpm against the generator
rotor's 1173.7, which holds. The MCP tools `path_between` and `flow_path` answer
the same.

### Subtree of one item

`root=<item id>` (with `product`, which it requires; 400 otherwise) scopes the
parts, interfaces, bill-of-materials and references answers to the subtree of
one item of the product: an assembly, a site kit or a part. The root is the
item of that id under each PLM's IRI that the core graph places in the product;
an id that names none is 404 `{ error: "no item <id> in <product>" }`, an id
outside `[A-Za-z0-9._ -]{1,64}` is 400. The product's own key as `root`, or no
`root`, is the product root: the product-wide answer, unchanged, without
`subtree`.

The subtree is resolved in rounds, by roots, never by parts. Each round first
reads from the core graph the tags and product memberships of its roots: a root
outside the product (the target of a dangling reference) is dropped, and a root
the viewer may not see is a redacted node its site is never asked about. Round 1
then asks the root's site for the root's tree: a structure request (`VALUES ?root`, then
`?root atelier:contains ?part` and the lines from those items, ids and
quantities only), which Ontop turns into SQL on the site's closure table with
the root as a condition, so the site's database selects the tree by an index
lookup. The core
graph then returns the tags and product memberships of the items found, and the
layer decides what the viewer may see: a hidden item is a redacted node, the
items under it are not expanded and its lines are dropped. The site is then
asked for the facts of its visible items (descriptions and attributes,
external references, and the features for the interfaces answer or the
supplier offers for the parts answer) by the roots of the trees with no hidden
item (`?root atelier:contains ?part`) and, for a visible item above a hidden
one, by that item alone (`FILTER (?part = ?root)`), so a hidden item's row
never leaves its database. Each later round asks each site that the external
references of the round before name, not yet reached, for the trees of the
referenced items, the same way. The resolution stops when a round adds nothing,
or after 8 rounds; the items still referenced then are `unresolved`, and their
memberships are read so the reference shapes can tell them from missing ones.
Every PLM request of a subtree run carries `VALUES ?root` with its round's
roots; none carries `VALUES ?part`.

Each site keeps its closure as a table of the same name (`teilestruktur`,
`nomenclature_fermeture`, `cierre_lista_materiales`, `bom_closure`): one row
per (ancestor, descendant) pair with the shortest depth, the primary key on the
pair and an index on the descendant. PostgreSQL triggers on the site's
bill-of-materials idiom (DE `bauteil.parent_id`, FR `nomenclature`, ES the
`lista_materiales` document on `pieza`, UK `bom_line`, and the part table where
it is a separate one) keep it current: a statement that inserts, moves or
deletes lines or items recomputes the pairs whose descendant lies in the
affected subtree, and no others. The seed rebuilds the whole table at its end.
A root lookup therefore reads the root's pairs from the primary key; its cost
follows the subtree, not the site's bill of materials.

The link store is asked last, once the rounds have run, with the final items:
the interfaces answer asks it for the interfaces with at least one side among
the subtree's items (`VALUES ?side`), then for the file index of the items and
the context parts (`VALUES ?part`), which the link store evaluates natively;
the parts and bill-of-materials answers hold no interface and ask it for the
file index alone. Every source's work in a subtree answer follows the subtree:
the sites read their closure tables by root, the core graph is asked about the
items the rounds reach, and the link store about the same items. Nothing in a
subtree answer is read product-wide; the product root answer reads the whole
product, as it must.

The interfaces answer holds the product's interfaces with at least one side in
the subtree; their far-side parts outside it come as context (`context: true`):
their tags, then each visible one's attributes and features, by itself. The
shapes run on the subtree's merged graph with the context parts; no finding on
a context part is stated, and the product rules (massLimit, massScale) are not
evaluated on a subtree. The bill of materials under a root is the root's tree;
where an item's external reference names an item the subtree reached, the
referenced item's tree hangs under it with the reference's quantity, and the
roll-ups count the subtree per site and in total. A root the viewer may not see
is a redacted node holding nothing.

```
subtree = { root, plm, product, name,                 // name null when the root is hidden
  redacted,                                          // true when the viewer may not see the root
  depth,                                             // rounds that added items; 1 = the root's own site
  rounds: [{ round, roots: [{ id, plm }], items }],  // the roots each round asked about, the items it added
  items,                                             // items of the subtree the viewer may see
  context,                                           // context parts in the answer (interfaces answer)
  productRules: "not-evaluable",
  unresolved?: [{ id, plm }] }                       // referenced beyond the last round; absent when complete
```

Characteristics of a subtree answer on the fixture stack (real Ontop and
PostgreSQL, `modules/query-service/fixtures/run.sh`, which prints this table
from `check_subtree.py`), for the wind turbine as `programme-cleared`: the
requests, bytes, endpoint time, triples and validation time of
`GET /query/interfaces`, the closure SQL time, and the STEP files the viewer
loads, those of the visible parts of `GET /query/parts` plus the context parts
of the interfaces answer. Each figure is the second call of a root, with
Ontop's translation cache warm. The closure SQL time is the execution time of
the root lookups on the sites' closure tables (`EXPLAIN ANALYZE`, the best of
five per lookup, summed over the rounds); the product root reads no closure.
The gearbox's tree references no other site; the hub control's references
reach the German and the French sites in a second round.

| Root | Requests per endpoint | Request bytes | Largest request (bytes) | Endpoint time (ms) | Closure SQL (ms) | Triples merged | Validation (ms) | STEP files |
|---|---|---|---|---|---|---|---|---|
| `wind-turbine` (product root) | fr 6, de 6, uk 6, es 6, core 3, neptune 2 | 189,960 | 46,212 | 171 | none | 7,234 | 95 | 170 |
| `D-37073` gearbox (DE) | de 10, uk 4, es 4, core 6, neptune 2 | 39,535 | 2,760 | 48 | 0.04 | 1,321 | 13 | 31 + 6 context |
| `UK-3776` hub control (UK, references into DE and FR) | fr 10, de 10, uk 10, core 8, neptune 2 | 48,538 | 1,664 | 83 | 0.08 | 946 | 10 | 10 + 3 context |

A product-root request lists every visible part of a site in `VALUES ?part`, so
its size grows with the product; a subtree request names its roots only, and
its size grows with the number of roots a round asks about. The link store's
requests of a subtree answer name the subtree's items and context parts, so
their size and the link store's answer grow with the subtree.

### Bill of materials

```
Bom = { product, root: BomNode, sites: [BomRollup], total: BomRollup, provenance, sparql, timings, policy, subtree? }
BomNode = { id, plm, name, nameEn?, partType,         // root: partType "PRODUCT", no plm; nameEn from the labels graph
  revision?, lifecycle?, lifecycleState?,              // the PLM's revision and word; lifecycleState from the scheme
  quantity, occurrences,                               // per parent; multiplied down from the root
  unitMassKg?, extendedMassKg?,                        // absent when no mass is known
  children: [BomNode | { redacted: true, plm, quantity, occurrences }] }
BomRollup = { plm, occurrences, massKg, withoutMass, hiddenOccurrences }   // plm null on total
BomWhereUsed = { product, part: Part | { redacted: true, plm },
  usedIn: [{ id, plm, name, partType, quantity, occurrences } | { redacted: true, plm, quantity }],
  occurrences, provenance, sparql, timings, policy }
```

The root is the product; under it is one site kit per site, in the order
`fr`, `de`, `uk`, `es`, then each site's tree, assemblies before parts. `name`
is the native name. A part's `unitMassKg` is its own mass plus that of the items
it holds, an assembly's the sum of its items'; `extendedMassKg` is the unit mass
times the occurrences. A roll-up counts part occurrences only (part type
`PART`), their mass, the part nodes without a mass (`withoutMass`) and the
occurrences of hidden items (`hiddenOccurrences`), counted apart because what
they hold is unknown. `used-in` answers each parent once, with the quantity it
uses and the part's occurrences under it, and the part's total occurrences; a
hidden part is a redaction marker with no parents. It is distinct from
`where-used`, which answers the interfaces a part sits on.

The run follows the same federation path and redaction as every answer: the
core members and tags, then four requests per PLM, for the descriptions of its
visible items, the references they make, the lines from them and the lines to them.

### Placements

```
Placements = { product, parts: [PartPlacements], occurrences, provenance, sparql, timings, policy, subtree? }
PartPlacements = { id, plm, occurrences: [[x, y, z, rx, ry, rz]] }   // mm to 3 decimals, degrees to 4
```

Every visible part of part type `PART` of the product, or of the subtree of
`root`, in id order, with the world placements of its occurrences composed down
the trees as "Placements" under "Native schemas" describes: from the identity at
each site kit or, for a subtree, at each root of its resolution, which sits at its
reference occurrence. The rotations are in the convention of the stored ones, so
a viewer applies `Rz Ry Rx` then the translation to the part's STEP. The first
occurrence of every part is the reference occurrence, the identity. A path
through an item the viewer may not see contributes nothing, and a hidden part
is not listed. `occurrences` is the number listed.

The run is the bill-of-materials run, then one more request per PLM: the
occurrences of the lines from every item its site kit contains (`VALUES ?root`
through `atelier:contains`, never a list of parts), with a `FILTER (?child NOT
IN (...))` naming the product's items of that site the viewer may not see, so
their placements stay in the database; the redaction removes any occurrence
whose child is hidden as well. A subtree run asks each site once, after its
rounds, by the visible roots it resolved.

Interfaces are per part number: their markers sit on the reference occurrence
of each part.

### Configured answers

`option=<code>` on `GET /query/parts` and `GET /query/placements` answers for
the product as one option of one of its variant groups makes it, the
configuration the variant diff validates: the run of the base answer with the
group's options from the options graph, redacted for the profile as the base
answer is, then the base without what the group's other options hold and, for
an option other than the default, without what the default option is made of,
with the option's own items, lines and occurrences. The default option's answer
is the base product's. The option codes are unique across the products, so the
code names its group. A code no group of the product holds is 404
`{ error: "no item option <code> in <product>; its variant groups: ..." }`, and
`option` with a subtree `root`, or without `product`, is 400.

The Interface check's option switch shows the configuration: the parts and
placements answers under the option draw the viewer, and the interfaces on the
screen are the base ones without the default option's, with the option's own
from the variant diff, their rules run. One option is taken at a time; taking
one puts the product's other groups back at their defaults, and opening a
subtree puts every group back at its default. The location hash carries the
option (`#/check?option=spring-return`), as it carries the subtree root and the
lens.

### Stations and sections

```
Between = { product, axis: Axis, range: { from: StationRef, to: StationRef, side, intervalsMm: [[from, to]] },
            stations: [StationRef], parts: [SpanPart], inside, crossing, notes: [string], provenance, sparql, timings, policy }
Axis = { axis, symmetric, measure }
StationRef = { id, basis }                      // printed, measured, joint or inferred
SpanPart = { id, plm, name, nameEn?, span: [from, to], unit, fromMm, toMm, position, stations: [StationRef],
             occurrenceCount?, occurrences?: [{ index, fromMm, toMm, position }] }
Sections = { product, axis: Axis, stations: [{ id, atMm, basis, source?, toleranceMm? }], sections: [Section],
             joints: [Joint], notes, provenance, sparql, timings, policy }
Section = { id, name, owner, side, part?: PartRef | Redacted, stations: [StationRef], intervalsMm,
            parts: [{ id, plm, name, fromMm, toMm }], foreign: [...] }
Joint = { station: StationRef, atMm, side, sections: [a, b], owners: [a, b], crossing: [{ id, plm, name, fromMm, toMm }] }
```

On a symmetric axis station n is at +n on the right side and -n on the left: a
range or a section on `both` sides covers both intervals, merged when they
meet at the plane of symmetry. A part is in the range when one of its
occurrences reaches more than 1 mm into it, `inside` when no occurrence in the
range reaches more than 1 mm past either end, `crossing` otherwise; `span` and
`unit` are the part's reference span as its PLM stores it (inches for the UK
site), `fromMm`, `toMm` the same in mm, and `stations` the stations it covers
within 1 mm. A part placed more than once lists `occurrenceCount` and its
occurrences in the range, numbered in the order of `/query/placements`. Parts
come inside first, then crossing, each in order from the plane of symmetry.
Every station is named with its basis, and `notes` name the inferred, measured
and joint stations the answer rests on, so an inferred station never reads as
printed.

A section's `parts` are the visible parts overlapping it by more than 1 mm,
`foreign` those owned by another site than its owner. A joint is a station two
sections share, at each signed position where both lie (the right wing root
joint at +700, the left at -700); its `crossing` parts reach more than 1 mm on
both sides of it. A section's part the viewer may not see is
`{redacted: true, plm}`.

The run is the placements run with, per PLM with a visible item, one more
request for the stored spans of its visible parts (`VALUES ?part`) and, beside
its placements request, one for the spans of the same occurrences, the hidden
children left out; the link store is asked once more for the product's
stations, sections and axis. A part the viewer may not see is never federated,
so no answer names it.

### External references

```
References = { product, references: [Reference], counts: { status: n }, provenance, sparql, timings, policy, subtree? }
Reference = { id, plm, part,                         // the reference's key and PLM, the local part that makes it
  remoteUrn,                                         // as stored
  target?: { id, plm },                              // the part the URN resolves to, when the viewer sees it
  quantity?, expectedRevision?, note?,
  currentRevision?, lifecycleState?,                 // of a visible target
  status: "ok" | "danglingReference" | "staleRevision" | "not-evaluable",
  message? }                                         // the shape's message on a failing reference
```

The status is the shapes' verdict on the reference; a reference no shape
reported is `ok` when the viewer sees its target and `not-evaluable` when the
target exists but is hidden. The run is the parts federation: per PLM, the
descriptions of its visible parts, then the references they make and the
supplier offers for them.

### Purchased items

```
Equivalents = { product, groups: [EquivalentGroup], provenance, sparql, timings, policy }
EquivalentGroup = { itemClass, classLabel,            // the class's skos:notation and label
  attributes: [{ attribute, label, mm?, concept?, text? }],   // the identifying attributes, first member's values
  shelfLifeMonths?,                                    // the shortest a member states
  stocking: { partNumbers, sites, stockLines, stockLinesOnceConfirmed },  // stockLines 1 once confirmed
  confirmed,                                           // true when owl:sameAs links every two members
  members: [{ plm, id, iri, name, standard?,           // name and standard as the site writes them
              values: [{ attribute, stored?, unit?, mm?, concept?, conceptLabel?, fromStandard? }],
              shelfLifeMonths? }] }
Suppliers = { product, suppliers: [{ name, sites: [{ plm, id, location,
                offers: [{ id, partId, partName, supplierPartNumber, leadTimeDays, preferred }] }] }],  // id: the offer row's native key
  singleSource: [{ plm, id, name, supplier, leadTimeDays }],
  conflicts: [{ plm, id, name, message }],             // the conflictingLeadTime findings
  provenance, sparql, timings, policy }
```

Entity resolution is a query, not a rule, driven by `atelier:ItemClasses`. The
visible parts of the product of one class that agree on every identifying
attribute of the class are one group: a length within the attribute's tolerance
after conversion to millimetres, a resolved text when both texts name the same
concept (and the thickness written in it within the tolerance), a legend when
it is the same text, case and spaces ignored. A value a part does not state
agrees only with another missing one, unless the size its standard designates
supplies it (`fromStandard`). A part whose text or standard names no concept is
left out. A part joins the first group of its class whose first member it
agrees with; groups of one part are not reported. The answer asks each PLM the
parts query with the class attributes, so the interface and parts answers read
none of them.

`stocking` is the consequence for the stores: an item bought under
`partNumbers` part numbers in `sites` sites is `stockLines` stock lines, each
expiring on the shelf when the item has a shelf life; once confirmed it is one.

| Product | Group | Members |
|---|---|---|
| `rover` | fastener ISO 4762, 3 x 10 mm | `D-38014` (`DIN 912`, 3 x 10 mm), `FR3811` (`NF EN ISO 4762`, 3 x 10 mm), `UK-3823` (`BS EN ISO 4762`, 0.118 x 0.394 in), `ES-3826` (`UNE-EN ISO 4762`, 0.118 x 0.394 in) |
| `rover` | fastener ISO 4762, 4 x 16 mm | `D-38015`, `ES-3829` |
| `wind-turbine` | o-ring 24.99 x 3.53 mm, NBR 70 Shore A, shelf life 84 months | `D-37056` (`ISO 3601-1 214A`), `UK-3741` (0.984 x 0.139 in); `UK-3742`, the same size in FKM, stays out |
| `wind-turbine` | o-ring 37.69 x 3.53 mm, NBR 70 Shore A, shelf life 84 months | `D-37057` (`ISO 3601-1 222A`), `ES-3742` (37.69 x 3.53 mm) |
| `steam-engine` | o-ring 50.17 x 5.33 mm, EPDM 70 Shore A steam resistant, shelf life 120 months | `D-33009` (`ISO 3601-1 332A`), `UK-3306` (1.975 x 0.210 in), `ES-3308` (50.17 x 5.33 mm) |
| `ornithopter` | cord 10 mm, raw silk, shelf life 36 months | `CPED-6040` (10 mm, seda cruda), `LACE-6165-L` (0.394 in, raw silk), `SCHN-R-61065` (10 mm, Rohseide) |
| `ornithopter` | canister 190 x 120 x 280 mm, polyethylene | `FR-ORN-BIDN-001` (two in the bay), `KANI-61140`, `CAN-6270` (7.480 x 4.724 x 11.024 in) |

A group is a proposal of the layer until a user confirms it: `confirmed` is true
when the links graph states `owl:sameAs` between every two of its visible members
(`POST /core/equivalences`, see "Freshness"). A visible member's `owl:sameAs` to a
part the viewer may not see is redacted, so a group the officer confirmed with a
hidden member reads confirmed to a profile that sees the others. The agent may
propose a group; only the user confirms it, from the Purchasing screen.

### Terms

```
Terms = { q, terms: [Term], provenance, sparql, timings, policy }
Term = { iri, match: "exact" | "word",                // a label is the text, or holds its words in order
  labels: { en, de, fr, es },                          // the concept's skos:prefLabel per language
  altLabels?: [{ lang, label }], broader?,             // further labels; the concept it is a kind of
  parts: [{ id, plm, product, name, nameEn?,           // name native; nameEn when the site writes another language
            matched: { lang, label } }] }              // the label found in the name
```

Labels and names are compared without case or accents. A concept matches when one of
its labels, in any language, is the text (`exact`, listed first) or holds its words
(`word`). An item belongs to a concept when its name in a language holds a label of
the concept, or of a concept below it by `skos:broader`, in that language: an
English label in the English names of every site, a German, French or Spanish label
in the native names of that site, the site's own word preferred as `matched`. A
German label may sit inside a word, since German writes compounds (`Zahnrad` in
`Antriebszahnrad`); a word of another language is a whole word, or that word with a
plural `s` or `es`. `Zahnrad`, `roue` (in the gear's `roue dentée`) and `gear` all
name `term/gear`, whose items are found at the German (`D-31001`, Antriebszahnrad
links), French (`FR3650`, Pignon de commande, a pinion) and British (`UK-3606`,
Spur gear) sites. The run reads the labels graph whole, then the core graph's
memberships and the tags of the items with the profile FILTER; no PLM is asked. The
names of an item the viewer may not see are redacted before any is matched, and such
an item is not listed.

Suppliers are grouped by name. Each site keeps its own list, so a supplier
two sites buy from is listed under each: the rover's ES and UK sites both buy
from Kestrelmoor Fastener Supply Ltd. A single-source part has exactly one
offer in its site, whatever the other sites buy for the same item. Both answers run the parts federation of the product, which asks each PLM
for its visible parts, the references they make, the supplier offers for them and the lines from them, four requests per PLM;
the hidden parts' offers never leave their PLM. The `sparql` tool's graph is
that of the interfaces answer, which holds no supplier offers: the suppliers of a
product are the `suppliers` tool's.

Every number returned is computed at request time; `detail` carries the
measured values (for example `{ axis: "y", deltaMm: 4.3 }`); the orphan
violation's `detail.candidateMates` lists the unmated features of the other part
at the orphan's position, unit-converted.

### Preview of a correction

`POST /query/preview` with `{ product, root?, cells: [{ plm, table, key, column, value }] }` answers what the rules
would say if the cells were released: the cells are the ones the release form builds (`proposals.ts`) and the PLM
demo update takes, in the owning site's own tables, columns and words; `value` is a JSON number, string or boolean,
or null to clear the cell. `product` is required; `root` scopes the preview to that item's subtree as on the other
answers (the product rules are then not evaluated). At most 500 cells, as in one release batch.

Each cell is checked before anything runs, against its site's catalogue (`GET /{plm}/catalogue`) as the site's update
route checks a correction: a column listing its words or units takes one of them, a column stating a form takes a
text of that form, a numeric column a number (an integer column no fraction), a flag `true` or `false`, any other
column a non-blank text; and against the site's R2RML: the row's key column, a foreign key and an unmapped column
reach no triple and are refused. A refusal is 400 `{ error: "cells[i]: <reason>" }`.

The preview then runs the product's federation for the caller's profile (the interfaces answer's requests with the
supplier offers of the visible parts, so every rule has its input), redacts it, and writes each cell into a copy of
the merged graph as the site's R2RML maps it: a text, number or flag column replaces its literal (typed as the
mapping types it); a quantity's value column replaces the `qudt:numericValue` of the row's quantity node; a unit
column replaces the `qudt:unit` of every quantity node of the row that reads it. A cell on a row the profile may not
see is refused as a row that does not exist: `cells[i]: no row <key> of <table> the <profile> profile may see`. The
shapes run on the graph as read and on the copy, and the results are compared:

```
Preview = { product, root?,
  cells: [{ plm, table, key, column, value, triples: [{ subject, predicate, before, after }] }],
  interfaces: [{ id, product, label, before, after,     // status before and after
                 fixed, stillFailing, newlyFailing }],  // each [{ rule, message, features }]
  parts: [{ id, plm, name, fixed, stillFailing, newlyFailing }],   // findings, each [{ rule, message, value? }]
  products: [{ key, fixed, stillFailing, newlyFailing }],          // massLimit, massScale
  tally: { fixed, stillFailing, newlyFailing },
  provenance, timings, policy }
```

A result is the same before and after when its rule and what it is about agree (an interface result's focus
feature, the features it names and the quantity or axis; a part finding's record; a product finding's rule),
whatever its message says of the values. An interface, part or product is listed when a result of it is fixed or
newly failing; one whose results fail before and after is listed when the cells concern it: an interface naming a
part that holds a rewritten record, a part that holds one or whose finding names one, the product when a part's
mass is rewritten. The only requests are the federation's reads and one catalogue read per site the cells name:
nothing reaches a PLM's update route, the link store or the change log.

### Variant diff

`GET /query/variant-diff?product=&group=&option=` compares one option of a
variant group with the group's default option, the base product. The federation
reads the product as the preview does, with the group's options from the options
graph: the option interfaces join the base ones, and the items only an option
holds are named as the product's parts, so the core graph tags them and their
sites describe them. The redacted graph then splits into the two configurations
(the base without what the group's other options hold, and the base without what
the default option is made of, with the option's own items, lines and
interfaces), each validated with its occurrences derived from its own trees. A
host is a part neither option lists; each interface of the group gives, for each
visible feature on a host, one side of the port the feature serves. Sides pair by
(host, port), or by (host, feature id) for a feature without a port.

```
VariantDiff = { product,
  group: { key, name, selects, defaultOption, options: [code] },
  option, against: { key, isDefault, source?, applicability?, portsNotModelled: [port] },
  ports: [{ host: { id, plm } | { redacted: true, plm }, port?,
            change: "added" | "removed" | "changed" | "same" | "not-modelled",
            differences: ["interface" | "status" | "mate" | "host.<attribute>" | "mate.<attribute>"],
            against?, option?: { interfaceId, label, status, rules: [rule],
                                 feature, mate, mateFeature } }],   // feature and mateFeature as in Interface
  removed, added: { items: [{ id, plm } | { redacted: true, plm }], interfaces: [id] },
  configuration, baseline: { option, interfaces, tally: { pass, fail, notEvaluable },
                             occurrences: { plm: n }, occurrencesTotal, massKg, productFindings: [Finding] },
  interfaces: [Interface],                                            // the option's own, with the rules run
  provenance, sparql, timings, policy }
```

A port either option lists in `portsNotModelled` is `not-modelled` whatever
the sides hold: present on the host, never compared, so an answer never reads a
port the data does not hold as matching. Two values are the same within
0.01 mm or 0.01 bar after conversion. A part the viewer may not see is a
marker in `host`, `mate`, `removed` and `added`, and the interfaces that name it
are `not-evaluable`. `occurrences` counts the parts (part type `PART`), and
`massKg` adds every item's mass times its occurrences, as `massLimit` counts
them; the product rules' findings are each configuration's own. The ornithopter's
spring return, as `programme-cleared`:

| Host | Port | Change | Default (`stirrup-return`) | Option (`spring-return`) |
|---|---|---|---|---|
| `ROOT-6180-L` (UK) | return eye | changed: interface, mate, mate.feature | `IF-157` pass, ES `CRET-L-6075`, 10 mm | `IF-180` pass, UK `SPRG-6210-L`, 0.3937 in (10 mm) |
| `WURZ-R-61080` (DE) | return eye | changed: interface, mate, mate.feature | `IF-158` pass, ES `CRET-R-6085` | `IF-182` pass, DE `FEDR-R-61110` |
| `FR-ORN-EMPL-L-001` (FR) | spring anchor | added | | `IF-181` fail (fastener), UK `SPRG-6210-L`, 0.3150 in (8.001 mm) against 10 mm |
| `FR-ORN-EMPL-R-001` (FR) | spring anchor | added | | `IF-183` pass, DE `FEDR-R-61110` |
| `PEDL-6030` (ES) | left heel eye, right heel eye | removed | `IF-159`, `IF-160` pass, the return cords | |

Configurations: 68 pass, 8 fail, 0 not evaluable on the option against 69, 7,
0 on the default; 90 part occurrences on both; 263.423 kg against 261.237 kg,
both over the 260.5 kg limit. The one-piece crank changes three ports: the
forward cross beam's left crank bearing turns from `IF-25` fail (ES 12 mm against
FR 10 mm) to `IF-184` pass, and the drum journal tie and the lantern hub keep
their values under `IF-185` and `IF-186`; 66, 6, 0 on 72 interfaces, 86 part
occurrences. `GET /query/variants?product=` lists the groups the switch on the
Interface check offers; under the offshore foundation the wind turbine's viewer
draws the transition piece and the monopile, the platform bolt kit at its 48
places (Configured answers).

## Evidence behind an answer

`GET /api/query/interfaces/{id}/evidence` returns what each component
contributed, all captured during the same request:

```
Evidence = {
  interfaceId, product,                    // the bare id and the key of its product
  sparql,                                  // the federated request as executed
  arms: [{ endpoint, kind,
           sparql,                         // the request(s) sent to this endpoint, as sent
           triples,                        // Turtle: the triples attributed to it (by IRI)
           tripleCount, requests, ms,      // measured, equal to provenance.calls[]
           sql: string | null,             // Ontop's SQL for that arm, translated offline by the
                                           // query service with the PLM's own R2RML and the extracted
                                           // database metadata; null for Neptune; "EMPTY" when the
                                           // PLM maps none of the concepts
           tables: [{ plm, table, keys: [nativeKey] }] }],   // rows the triples came from
  merged: { triples: number, turtle },     // the graph SHACL validated (interface neighbourhood)
  shacl: { shapes: [{ rule, shape, turtle }],   // only the shapes that reported for this interface
           report },                       // Turtle: the SHACL validation report
  timings, policy }
```

Native rows come from the PLM services, never from the graph:
`GET /api/{plm}/tables/{table}?keys=k1,k2` returns `{ table, keyColumn,
columns: [name], rows: [[value]] }` for that PLM's own tables only, by primary
key, read-only, rows filtered by the profile. Keys are passed URL-encoded; a key
may contain a space. Nothing in the evidence is assembled from files at request
time except the shape sources, which are the shipped `ontology/shapes.ttl`
fragments.

A bill-of-materials line is attributed to its native relation, keyed by the
child: DE `bauteil`, FR `nomenclature`, ES `linea_lista_materiales` and the
parent's `pieza` row that holds the document, UK `bom_line`. An external
reference is a row of its PLM's reference table, keyed by its `id`. An
interface's neighbourhood carries the references its parts make and the lines from
them, with the label, revision and lifecycle word of each visible target or child,
so a target's row appears in its own PLM's arm.

## PLM service API

Behind API Gateway at `/api/{plm}/*` (`fr`, `de`, `uk`, `es`) and `/api/core/*`.

| Method and path | Purpose |
|---|---|
| `GET /{plm}/parts`, `GET /{plm}/parts/{id}`, `GET /{plm}/plugs`, `GET /{plm}/fasteners`, `GET /{plm}/couplings` | the PLM's own records through its ORM, as DTOs, filtered by the profile: only the rows of parts the profile may see, features following their part, a hidden part answered 404; rows are absent, never redacted |
| `GET /{plm}/catalogue` | the ORM-generated catalogue: tables, columns, Java types, units (fixed or per-row column), descriptions, ontology terms, key columns, undescribed flags, for a column with a closed vocabulary its `accepts` (words or stored units) or `pattern`, and for a structural document column the view it is read through (`readThrough`); not filtered (it describes schemas, not rows) |
| `GET /{plm}/mapping` | the generated R2RML, `text/turtle`; not filtered |
| `GET /{plm}/tables/{table}?keys=` | native rows by key, filtered by the profile |
| `POST /{plm}/sql` | catalogue-grounded SQL, body `{ sql, purpose }` (below) |
| `POST /{plm}/demo/update` | a correction of one cell, or a batch in one transaction, in the PLM's own tables (see "Freshness") |
| `POST /{plm}/demo/events/cad` | a CAD publication event (see "Freshness") |
| `GET /{plm}/health` | liveness |
| `GET /core/tags` | the `part_tag` rows of the parts the profile may see (the export officer sees every row) |
| `GET /core/products` | `[{ key, name, frame, partCount }]`, profile-independent; `partCount` counts the product's `product_part` rows, assemblies and site kits included, since the core holds no part type (`GET /query/products` counts the parts with geometry) |
| `POST /core/links` | a mating link (see "Freshness") |
| `GET`, `POST`, `DELETE /core/changes` | the change log: read by any known profile, appended by the links loader (an array of rows, one transaction), cleared by the officer |
| `POST /core/graphs/reset`, `GET /core/graphs/health` | restore the released graphs (officer); the configured store and the released triple counts |
| `POST /core/demo/reset` | the whole reset (see "Freshness") |

The core service is not a PLM but is described like one: same annotations, same
catalogue, same generated mapping (`ontop-core`), same native rows.

**Catalogue-grounded SQL** (`POST /{plm}/sql`, body `{ "sql": ..., "purpose": ... }`,
profile header required; the MCP `sql` tool forwards to it):

- one `SELECT` statement (a `WITH` clause is part of it), parsed with JSqlParser, never a regex; anything else (update, DDL, `COPY`, `DO`, `EXPLAIN`, `SET`, function definitions, `pg_*`, `information_schema`) is refused by its kind;
- every table and column referenced must exist in the catalogue of that PLM; an unknown name is rejected with the closest catalogue entries, never answered with empty rows;
- export control is applied by rewriting each table reference into the releasability-filtered view the table policy defines for `/{plm}/tables/{table}`; tables without a key column are read as they are;
- `LIMIT 200` enforced, statement timeout 5 s; the statement runs on the service's own data source (the PLM's application login) in a transaction the driver and `SET TRANSACTION READ ONLY` both declare read-only, so read-only rests on the parser and the transaction mode, not on database privileges;
- the answer carries the SQL that actually ran (after rewriting), the rows, the row count, ms and the catalogue entries used.

## Export control

Classification lives in the integrator's own database, `atelier_core.part_tag`
(`plm, native_key, jurisdiction, releasable_to, tagged_by, tagged_at`), one row
per part, recording the PLM that tagged it and when; the sample seeds the rows
with `data/generate.py` from `data/products/*.json`. A row is keyed by the
part's native id and inherited by the part's features and CAD file. Mapped as
`atelier:jurisdiction` and `atelier:releasableTo` (literals) on `atelier:Part` by
the core mapping, which mints the same part IRI as the PLM mappings.

Jurisdiction values: `EU-DUAL-USE`, `EXPORT-LICENCE`, `US-EAR`, `NATIONAL-FR`,
`NATIONAL-DE`, `NATIONAL-UK`, `NATIONAL-ES`, `NONE`. Releasability is one token:
`ALL`, `EU`, `FR`, `DE`, `UK`, `ES`, `LICENSED`. `EXPORT-LICENCE` / `LICENSED` is a
part released only after an export-licence review, visible to the export-control
officer alone. Seed: the aerial screw's DE hydraulic motor `HMOT-70090`
`EXPORT-LICENCE` / `LICENSED`; the ornithopter's FR control post `FR-ORN-PCMD-001`
and instrumentation pod `FR-ORN-NACI-001` `NATIONAL-FR` / `FR`; the six hydraulic
items (`FR-ORN-GHYD-001`, `ACTR-6190-L`, `AKTR-R-61090`, `VENT-70110`,
`BRAK-7050`, `HPU-7060`) `EU-DUAL-USE` / `EU`. The further products tag a few
parts each in their files: `NATIONAL-<country>` parts, `EU-DUAL-USE` electronics
and hydraulics, the cubesat's `EXPORT-LICENCE` battery pack, and one `US-EAR` /
`LICENSED` part on each of the difference engine, the wind turbine and the rover.
Every other part is `NONE` / `ALL`.
Tags are illustrative.

Viewer profiles stand in for identity-provider claims and are chosen in the UI.
The browser sends `x-atelier-profile`; a missing or unknown profile is `unknown`.
The policy is one file, `ontology/policy.json`, shipped unchanged into the
query-service and PLM images:

```json
{ "profiles": {
    "fr-engineer":       { "label": "FR engineer",            "nationality": "FR", "releasable": ["ALL", "EU", "FR"] },
    "de-engineer":       { "label": "DE engineer",            "nationality": "DE", "releasable": ["ALL", "EU", "DE"] },
    "uk-engineer":       { "label": "UK engineer",            "nationality": "UK", "releasable": ["ALL", "EU", "UK"] },
    "es-engineer":       { "label": "ES engineer",            "nationality": "ES", "releasable": ["ALL", "EU", "ES"] },
    "programme-cleared": { "label": "Programme cleared",      "nationality": null, "releasable": ["ALL", "EU", "FR", "DE", "UK", "ES"] },
    "export-officer":    { "label": "Export-control officer", "nationality": null, "releasable": ["ALL", "EU", "FR", "DE", "UK", "ES", "LICENSED"] },
    "unknown":           { "label": "No profile",             "nationality": null, "releasable": ["ALL"] } } }
```

A part is visible to a profile iff its `atelier:releasableTo` is in the profile's
`releasable` list. Enforcement, in this order:

1. The query service reads the tags of every part (the parts the link store's interfaces name and the parts the core graph places in a product; for an answer scoped to one product, the product's parts, selected by the product key) from `ontop-core` with `FILTER(?rel IN (...))` on `atelier:releasableTo` pushed into that request (and so into its SQL), then asks each PLM only for the visible parts (a `VALUES ?part` list), so hidden rows never leave the PLM database; it filters the merged graph again before SHACL.
2. A PLM service applies the same policy on `/tables` and `/sql`, reading `atelier_core.part_tag` through the read-only role `core_reader`: rows of a part table are filtered on the part, rows of a feature table through the part they reference, rows of a bill-of-materials line table through its child (`atelier:child`).
3. CAD files are served only as presigned S3 URLs, valid 15 minutes, issued by the query service for visible parts of part type `PART` in `parts[].cadUrl`; there is no CloudFront `/cad/*` path. The bucket stores them gzip-encoded (`Content-Encoding: gzip`, `Content-Type: model/step`) and the browser decodes them as it reads. `cadFile` (the key) stays for display.

A part with no tag is visible to no profile but `export-officer`, and
`/query/interfaces` reports it under `policy.untagged`, so a missing tag is a
finding against that PLM.

Redaction: an interface with a hidden side has `status: "not-evaluable"`, no
violations, and the hidden features and parts appear as `{ redacted: true, plm }`
with no id, values, attributes or position. In a bill of materials a hidden
item is `{ redacted: true, plm, quantity, occurrences }` at its place, its own
items not expanded and its occurrences counted apart in the roll-ups; a hidden
parent in `used-in` is `{ redacted: true, plm, quantity }`. Hidden parts are never named: their name is
replaced by "a part not visible to your profile" in the interface label and
everywhere else an answer would spell it. Every answer and its evidence carry
`policy: { profile, releasable, filter, untagged, actor }`, where `filter` is the
SPARQL (the RDF query language) FILTER text added to the arms and `actor` is `user` or `agent`.

Every request to an Ontop endpoint is one basic graph pattern with `VALUES` and
`OPTIONAL` only: no `UNION`, `MINUS` or `BIND`, which Ontop cannot type-lift
reliably.

## Where the additional metadata lives

The integrator adds nothing to the site PLM models. Metadata the integrator
needs about a part and that the PLM cannot hold lives in two places of its own:

**Core database (`atelier_core`, Aurora, same cluster, own role `core_app`).**
`part_tag` as above, and the product structure:

```sql
product(product_key VARCHAR(32) PK, name VARCHAR(120), frame TEXT)
product_part(product_key FK, plm VARCHAR(2), native_key VARCHAR(64), PK (product_key, plm, native_key))
```

`product_part` lists every part, assembly, software and document item of each product.

The core R2RML mints `https://example.com/atelier/product/{product_key}` as
`atelier:Product` (`atelier:label` from `name`, `atelier:frame`, `atelier:massLimit` from `mass_limit_kg`) and `atelier:partOf`
from the PLM's part IRI to the product IRI. Exposed as the fifth virtual graph,
`ontop-core`. The core database also holds `demo_change`, the log of value
corrections released to the PLMs; it is outside the catalogue and the mapping
and never in a graph.

**File index (Neptune, named graph `https://example.com/atelier/graph/fileindex`).**
Where a part's files are: `<part IRI> atelier:cadFile "cad/<product key>/<file>.stp"`,
one triple per part with geometry, and `atelier:builtBy` on supplier-built parts, generated
from `data/products/*.json` and loaded with the links. Every run reads the whole
file index in one request of its own, so a part on no interface has its CAD file
too; redaction removes the entries of the parts the viewer may not see. The
layer never derives the CAD location from the PLM's own file column.

**Labels graph (Neptune, named graph `https://example.com/atelier/graph/labels`).**
What the sites do not hold about names: each item's native name and English name
and the products' glossary, the concept scheme `atelier:Terms` (see "Ontology
terms"), generated by `data/generate.py` (with `data/terms.py`) into
`data/labels.ttl` from the product files' `nameEn` and the glossary of the product
briefs. It is released like the file index: the links loader PUTs it at deploy,
the core service's image bundles it and the reset PUTs it back. The parts and
bill-of-materials answers ask the link store, beside the PLMs, for the names of
their visible items only (`VALUES ?part`), with the confirmed equivalences of the
links graph; the interfaces answer asks for none. The terms answer reads the whole
graph and redaction removes the names of the items the viewer may not see before
any is matched.

## Products

Every run of the query service reads every product membership of the core
graph (`atelier:partOf`), so a part on no interface is still a part of its
product, then the core graph for the parts the link store's interfaces name and
the parts of a product: their tags (with the profile FILTER) and their products
(each product's name and frame). With `?product=key`, the run
keeps the interfaces of the product (their own `atelier:ofProduct`, so an
interface follows its product even when another product holds its parts) and
the parts that belong to the product, and drops the rest
before any PLM is asked, so the `VALUES ?part` list of each PLM arm is the
intersection of the product's parts and the parts the profile may see. The core
arm's SPARQL and SQL in the evidence carry `partOf`; its `tables` name
`product_part` and `product` rows beside `part_tag`. `GET /query/products` lists
every product, sorted by key, each with the number
of its parts the profile may see (a product every part of which is hidden is
listed with 0).

The browser keeps the product like the profile and sends it on every listing and
in the Ask body (`forwardedProps.product = { key, name }`); the product named
by `defaultProduct` in the site's `config.json` (written by
`infra/scripts/deploy.sh`, `ornithopter` by default), or else the first listed,
is selected until the viewer picks another. The viewer loads the
product's parts with geometry and fits the camera to them: each part's STEP
once, parsed in a pool of up to four workers so a part appears as soon as its own file is parsed, drawn at every
occurrence `/query/placements` lists (one instanced mesh
per mesh of the file, the reference occurrence first; a part the answer does not
list is drawn once, as its STEP draws it). Picking any occurrence selects the
part and names the occurrence ("occurrence 3 of 6"); highlight, isolate with its
faded context, zoom, the lens, the site colours and the selection apply to every
occurrence; the interface markers sit on the reference occurrence; the legend
counts part numbers and occurrences; the product's frame
is shown under the switcher, and under it each finding of the product rules
from `/query/products`, or that the mass limit is not evaluable for the profile. The Bill of materials screen (`#/bom`) shows the
product's roll-up per site and in total above the tree of `/query/bom`, from
the product root through the site kits; a software or document item is marked
as an item without geometry, and a node whose part carries a `lifecycleConflict`
finding in `/query/parts` is marked with it, its messages in the mark's title.
The Paths screen (`#/paths`) asks `/query/flow` (follow the flow from a part,
of one kind or every kind, downstream or upstream) or `/query/paths` (the
interfaces joining two parts) for parts named in a search box or clicked in the
view; the view isolates the path's parts in their site colours with the rest of
the product faded, as the agent's `isolate_parts` does, marks each joint of the
path in its rule status, and the panel lists the steps with their sites,
interfaces and gear stages, the gear ratio and the rated-speed checks. The path
is in the hash, `#/paths/connect/<from>/<to>` or
`#/paths/flow/<from>/<kind or all>/<down or up>`, so a link opens it.

Each node shows its native name and, under it in the quiet style, the English name
of the labels graph; the interface detail's part block does the same from the parts
answer.

The Interface check viewer has a lens: a strip of filters on site, canonical
lifecycle state, supplier, part type and material, and an "As released"
toggle that keeps only the RELEASED parts at full opacity. The supplier filter
offers every supplier of the drawn parts and keeps a part when the chosen one
built it or offers it, whichever of its suppliers it is. Parts outside the lens
stay in place, faded, and the legend counts only the parts inside it, and their occurrences. The lens is
read from the parts answer, with no request of its own, and rides in the URL hash
next to the subtree root (`#/check/IF-01?root=FR-ORN-KIT-001&site=UK&released=1`),
so a shared link or a reload opens the same view; a route change keeps it, and so does a failing record
opened from the Rules screen.

## Agents on the semantic layer

**MCP server** (query service, streamable HTTP at `/query/mcp`, reached as
`/api/query/mcp` through API Gateway with the origin secret; the `x-atelier-profile`
header applies to every tool exactly as to the screens, and `x-atelier-actor` is
echoed per call). Server name `atelier-semantic-layer`. Twenty-five tools, all
read-only, each returning compact JSON with `provenance` and `policy` and
without the federated request texts (the `evidence` tool returns them when
asked):

| Tool | Arguments | Returns |
|---|---|---|
| `products` | none | key, name, frame, partCount visible to the caller's profile, the product rules' findings (massLimit, massScale), the mass-limit status (pass, fail, not-evaluable when items are hidden) and the number of lifecycle conflicts on its visible items |
| `list_interfaces` | `product` (optional), `root` (optional, with `product`) | id, label, parts, status, rulesFailing and up to three messages per failing interface; of one product when `product` names one; with `root`, the interfaces with a side in that item's subtree, far-side parts marked `context` |
| `parts` | `product` (required), `root` (optional) | the parts of `/query/parts`, of the product or of `root`'s subtree, with their attributes and findings, without the presigned `cadUrl`, the `sourceFileRef`, the `taggedAt` time and the `sparql` text |
| `interface_check` | `interface` (id or IRI), `product` (optional) | the Interface JSON of `/query/interfaces/{id}` |
| `where_used` | `part` (native id or IRI), `product` (optional) | the interfaces the part sits on with their status, its mates, feature counts per kind; of one product when `product` names one; points at `bom_where_used` for the assemblies a part is built into |
| `impact_of_change` | exactly one of `part`, `feature`; `product` (optional) | the interfaces and mated features a change would touch, with their current status; of one product when `product` names one |
| `export_status` | `part` | jurisdiction, releasability, tagged by and when, supplier, visibility, CAD availability and presigned URL |
| `bom` | `product` (required), `depth` (optional, default 3), `root` (optional) | the Bom of `/query/bom`, listing `depth` levels below the root; the roll-ups always cover the whole tree; with `root`, the tree under that item across the sites |
| `bom_where_used` | `part` (native id or IRI), `product` (both required) | the BomWhereUsed of `/query/parts/{id}/used-in`: the part's parents with quantity and occurrences, its total occurrences; points at `where_used` for the interfaces |
| `path_between` | `product`, `from`, `to` (native part ids, all required) | the Paths of `/query/paths`: the shortest interface paths between the two parts, each joint with its status, the notes on hidden parts |
| `flow_path` | `product`, `from` (required), `flow`, `direction` (optional) | the Flow of `/query/flow`: the walk along the functional edges, each step's flow, joint and gear stage, the gear ratio and the rated-speed checks |
| `variant_diff` | `product`, `group`, `option` (all required) | the VariantDiff of `/query/variant-diff`: the option against the group's default, port by port, both configurations' tallies, occurrences and mass, and the option's own interfaces; an unknown group or option is an error listing the product's groups and options |
| `external_references` | `product` (required), `root` (optional) | the References of `/query/references`: every reference of the product's visible parts (of `root`'s subtree when given) with its target, revisions and status, the counts per status |
| `equivalent_parts` | `product` (required) | the Equivalents of `/query/equivalents`: the purchased parts that are one item under different part numbers, units and words, by item class (fastener, o-ring, placard, container, tyre, wheel, brake), with the shelf life and the stocking line, each group confirmed by a user or a proposal |
| `find_term` | `term` (required) | the Terms of `/query/terms`: the glossary concepts a word in English, German, French or Spanish names, with the parts of every site whose names hold them |
| `find_parts` | `product`, `query` (both required), `root` (optional) | the parts of the product (of `root`'s subtree when given) that the query names by what they are: native and English names, glossary terms in the four languages and part types, without case, accents or plural endings, alternatives separated by commas (the model passes the word in each of the four languages); grouped by assembly (`match` `assembly`: an assembly whose name holds the query, the innermost, with every part below it; `parts`: matching parts under their parent assembly), each group with `matched` `{ via, label, lang, term, narrower }`, why its items matched (`via` `name` in the site's language, `nameEn`, `term` with the label found, the term's English label and, for an item found through a narrower term of the glossary's `skos:broader` hierarchy, that term: seal finds the O-rings, fastener the screws, nuts and washers; or `partType`), each part `{ id, plm, name, nameEn, occurrences }`, and `hidden`, the items of the scope the profile may not see, counted and never named. It searches only what the layer federates live (native names through Ontop, the bill of materials) and what it owns in the link store (the labels graph with `nameEn`, the glossary terms), redacted per profile at query time; no PLM schema, no copy of PLM data, no embedding index |
| `suppliers` | `product` (required) | the Suppliers of `/query/suppliers`: suppliers per site with lead times, single-source parts, lead-time conflicts |
| `parts_between_stations` | `product`, `from`, `to` (station ids, all required), `side` (optional: `left`, `right`, `both`) | the Between of `/query/stations`: the parts inside the range and crossing an end, with spans, stations covered and the occurrences of a placed part; every station with its basis |
| `section_joints` | `product` (required), `station` (optional) | the Sections of `/query/sections`, its joints cut to those at `station` when given |
| `evidence` | `interface` (id or IRI), `endpoint` (one of `ontop-fr`, `ontop-de`, `ontop-uk`, `ontop-es`, `ontop-core`, `neptune`), `product` (optional) | that endpoint's arm: request text, SQL (Ontop endpoints), triples, native tables |
| `ontology` | none | classes, properties with domain and range, named graphs, rules, example patterns; read before `sparql` |
| `sparql` | `query`, `product` (optional) | guarded free-form read over the profile's already-filtered merged graph |
| `preview_correction` | `product` (required), `cells` (required, at most 500), `root` (optional) | the Preview of `POST /query/preview`: the rules before and after the cells, written nowhere |
| `catalogue` | `plm` (one of `fr`, `de`, `uk`, `es`, `core`) | `GET /{plm}/catalogue` as it came |
| `sql` | `plm`, `query`, `purpose` | `POST /{plm}/sql` as it came; a rejection is an error carrying the PLM's reason and suggestions |

Every `product` argument is the key of a product the `products` tool lists. On
`bom` and `bom_where_used` it names the product whose bill of materials is read, on
`external_references` the product whose references are read, and `equivalent_parts` and `suppliers` the product whose purchased items are read. On
`list_interfaces`, `where_used`, `impact_of_change` and `sparql` it scopes the
answer to that product's interfaces. On `interface_check` and `evidence` it
names the product the interface id belongs to, as `?product=` does on the REST
routes: required when several products have an interface of that id (the error
names the candidate products), optional when one does. An `interface` argument
may also be the interface IRI, which carries its product.

Guard on `sparql`: `SELECT`, `ASK` or `CONSTRUCT` only; no `SERVICE`, no update
forms; every class and predicate IRI (including inside property paths and as
the object of `rdf:type`) must be one the ontology declares, and no function may
be called by IRI outside the `xsd:` casts; unknown terms are rejected with the
list of known ones, never answered with empty rows; `LIMIT 200` enforced; the
query runs on the profile's filtered merged graph in memory and never reaches
the sources. Every tool description lists its question templates and ends with
"Prefer a named tool; use sparql only when no named tool answers."

**Agent** (Strands with `ag-ui-strands`, FastAPI `POST /agent/invocations`,
AG-UI, the Agent-User Interaction protocol, over Server-Sent Events (SSE)) in a Fargate task reached through the CloudFront
`/agent/*` behaviour, a VPC origin and an internal Application Load Balancer on
port 8080. The edge sign-in gates it like the site; the browser sends
`x-atelier-profile` and the product on screen, and the agent forwards the
profile to every MCP call with `x-atelier-actor: agent`, so the agent sees
exactly what the viewer sees and provenance tells its calls from a person's.
The model is `BEDROCK_MODEL_ID` at deploy time, passed to the agent container as
`MODEL_ID` (an Amazon Bedrock model or inference profile).
The product on screen is named in the system prompt and becomes the default
`product` argument of every tool whose schema has one.

Frontend tools (return `None`; the browser carries them out from the AG-UI
tool-call events, and the turn shows each as a block the person can carry out
again). Part ids are the native ids the API returns, unique across products.

| Tool | The browser |
|---|---|
| `highlight_interfaces(ids, caption)` | lights the interfaces up and selects the first |
| `open_evidence(interface, endpoint, tab)` | opens the evidence drawer of the interface on the endpoint's card; `tab` one of `arm`, `sql`, `rows`, `triples`, `r2rml`, `links`, `graph`, `shapes`, `report` |
| `render_table(title, columns, rows)` | paints the table in the turn |
| `highlight_parts(ids, caption)` | outlines the parts in the 3D viewer in their site's colour, everything else unchanged; the caption shows over the viewer |
| `isolate_parts(ids, caption, context_ids)` | draws only `ids` at full opacity and `context_ids` faded, hides everything else and fits the view to `ids` |
| `zoom_to_part(id)` | fits the view to one part |
| `clear_view()` | puts back the normal view of what is loaded |
| `open_subtree(root, product)` | loads the subtree of `root` (route, breadcrumb, faded context parts), switching to `product` first when given |
| `open_product(product)` | loads the whole product, leaving subtree mode |
| `open_screen(screen)` | switches to `check`, `paths`, `bom`, `catalogue`, `flow`, `architecture` or `rules`; a screen the site does not route is no switch, and the turn says so |

The part ids of `highlight_parts`, `isolate_parts` and `zoom_to_part` resolve
against the parts of the product on screen: exactly, or else by the normalised
form (upper case, without hyphens or spaces) when exactly one part has it
(`UK3501` is `UK-3501`); an id that matches no part, or two, is left out, and the
tool's acknowledgement to the agent names it so the model can call again.

The view the agent sets lasts until `clear_view`, a product change or a subtree
change. The agent never changes the profile.

Every run input also carries what is on screen and selected as
`forwardedProps.selection`: `{ product, root, part: { id, plm, name } | null,
interface: { id } | null }`, `root` present in subtree mode only, `part` the
part clicked in the viewer or in the bill-of-materials tree (the viewer outlines it in ink and names it in a
"Selected" chip with a clear button, the tree marks its row; a second click clears it), `interface` the interface selected on the
Interface check screen. The run context names the selection; "this part", "this
interface" and "here" resolve to it, and with nothing selected the agent asks
which one is meant.

The system prompt and the tool definitions are the same bytes for every user,
product and turn; the viewer profile, the product on screen and the selection
are the run context, three `RunAgentInput.context` entries the agent sets and
writes at the head of the question. Bedrock caches the prefix: cache points
after the tool definitions, after the system prompt and after the latest user
message, so each later call of a turn reads the earlier ones from the cache.

Every turn ends with an AG-UI `CUSTOM` event `atelier.turn` carrying
`{ tools: [{ name, ms }], usage: { inputTokens, outputTokens,
cacheReadInputTokens, cacheWriteInputTokens }, estimatedUsd }`: `tools` has one
entry per tool call, in the order the calls started, each with its own duration,
the same tool as often as it ran; the usage and price are computed from the
model's usage report and the configured price per thousand tokens
(`PRICE_PER_1K_INPUT`, `PRICE_PER_1K_OUTPUT`; cache writes at 1.25 and cache
reads at 0.1 times the input price). Prompt rules: named tools
first (`bom` and `bom_where_used` among them for the bill of materials, `external_references` for the references between sites, `equivalent_parts` and `suppliers` for purchased items, `find_term` for what a word means, `find_parts` for the parts a request to show names), then `catalogue` and one `sql`, then `ontology` and one `sparql`; exact
ids only (never invent an id, a figure or a rule); cite the interface and
features behind every claim; say "not visible to your profile" when a tool
returns a redaction; a request to show, display, highlight, isolate or locate
parts is a viewer request: `find_parts` (one call per group); when the request
names one thing and one of its groups is an assembly that is that thing (the
gearbox), `open_subtree` with that assembly; for a group of parts or several
things (the O-rings, the six wheels, both wings, the gearbox and the
generator) the parts isolated, or highlighted when the user wants context,
with a caption naming each group, a short answer and no table unless asked; "class" is a seat or cabin
class when the subject is seats or the cabin and a purchased item's class only
when it is purchasing, stock or equivalence; an ambiguous request gets a
one-line question; after naming parts in another answer, outline them; for a
question about what one assembly holds, open its subtree; for a cross-site answer (a bill-of-materials
roll-up, where a part is used), isolate its parts with the far side as context;
after `impact_of_change`, isolate the impacted parts with the changed part as
context; after `parts_between_stations`, isolate the parts inside the range
with the crossing parts as context, and say when an answer rests on an
inferred station; answer in the user's language and show the owning site's
native name of a part beside it; propose an equivalence, never confirm one: only
the user confirms, on the screen.

**Desktop client.** `docs/kiro-mcp.md` gives the MCP configuration (URL,
`x-origin-verify` and `x-atelier-profile` headers) for Kiro, Claude Code or any
MCP client; the origin secret is read from Secrets Manager by the operator and
never committed.

## Freshness and the change feed

Three kinds of change, three paths. The rule behind them: the system that owns
a fact writes it in its own store and announces it as a business event; nobody
captures changes at storage level (no change-data-capture, no graph stream). The
browser calls the owning service directly through the API gateway; the query
service only reads. Who may act follows the same rule and is checked on the
owning service (403 otherwise):

| Action | Endpoint (behind `/api`) | Allowed profiles |
|---|---|---|
| value correction in a PLM | `POST /{plm}/demo/update` | that PLM's engineer or `export-officer` |
| CAD publication by a PLM | `POST /{plm}/demo/events/cad` | that PLM's engineer or `export-officer` |
| mating link (the integrator's fact) | `POST /core/links` | `programme-cleared` (the integration role) or `export-officer` |
| confirmed equivalence (the integrator's fact) | `POST /core/equivalences` | `programme-cleared` or `export-officer` |
| change list | `GET /query/demo/changes` | any profile but `unknown` |
| reset | `POST /core/demo/reset` | `export-officer` only |

The officer is the operator's seat; in production only the owning role acts.

Events on the EventBridge default bus:

| Source | Detail type | Publisher | Detail |
|---|---|---|---|
| `atelier.plm` | `part.value.corrected` | a PLM service | `{ plm, rows: [{ table, key, column, before, after }], actor, purpose, at }` |
| `atelier.plm` | `part.cad.published` | a PLM service | `{ plm, part, partIri, cadFile, at }` |
| `atelier.graph` | `interface.link.added` | the core service | `{ from, to, triples, at }` |
| `atelier.graph` | `equivalence.confirmed` | the core service | `{ parts, triples, at }` |

One rule delivers the two `atelier.plm` events to the links loader Lambda; no
rule consumes `interface.link.added` or `equivalence.confirmed`, which are published
for subscribers (revalidation, data products, audit).

**1. A value correction stays in the PLM.** `POST /{plm}/demo/update`, body
`{ table, key, column, value, purpose }` for one cell or `{ updates: [{ table, key,
column, value }], purpose }` for several (at most 500): every table and column must
exist in that PLM's catalogue and be correctable, every row is found by its table's
key column (converted to the key's type), and every update is checked before any
SQL runs. The service applies them in one transaction in its own database, each
`UPDATE` parameterised, and returns `{ plm, rows: [{ table, key, column, before,
after }], at, eventId }`, then publishes one `part.value.corrected` naming every
row. Nothing is written to any graph: the next run reads the new values through
Ontop. A correctable column is never a key or a foreign key, and is one of:

| Kind | Columns | Values taken |
|---|---|---|
| measure | numeric, mapped (`@Maps`) | a number (an integer for an integer column) |
| unit | named by a `@Unit(column = ...)` | the units the site stores there (`@Unit(stores = ...)`: UK `IN`, `PSI`; ES `MilliM`, `IN`) |
| text | connector type, fastener or coupling standard, fluid | any non-blank text |
| text with a vocabulary | lifecycle, revision, expected revision, remote URN | the site's four words (`@Accepts`), its revision form, `urn:plm:<site>:part:<local id>` |
| flag | preferred offer | `true` or `false` |

JSON `null` clears a cell, which the reset uses to put back an empty unit. The
catalogue lists each column's `accepts` (the words, in the order WORKING,
RELEASED, BLOCKED, SUPERSEDED for a lifecycle column; the stored units for a
unit column) and `pattern`. A refusal is 400 with a `reason`
(`not-correctable`, `foreign-key`, `key-column`, `value-not-in-vocabulary`,
`value-not-in-form`, `value-not-text`, `bad-key`, ...); a missing row is 404 and
nothing is written. The links loader records the event's rows in
`atelier_core.demo_change` through `POST /core/changes`, body the array of rows
(origin secret, officer profile), appended in one transaction, so the change
list shows the correction a second or two after the click. A row is appended only
in the form a PLM announces it, or the whole request is 400 `bad-field`: `plm` one
of `fr`, `de`, `uk`, `es`; `table` and `column` lower-case identifiers; `key` 1 to
64 characters without a control character, `/` or `\`; `actor` a profile name;
`purpose` at most 500 characters and each value at most 1024. The PLM never calls
the integrator.

**2. A mating link is the integrator's fact.** A cross-PLM relationship is known
to no site PLM, so the core service writes it: `POST /core/links`, body
`{ from, to }` (two feature IRIs), appends `<from> atelier:matesWith <to>` and
its inverse to the links graph with a Graph Store `POST` (never a `PUT`),
synchronously, then publishes `interface.link.added`. Response
`{ triples: { links }, eventId }`. A second mate for a feature fails its
interface (`doubleMate`) instead of passing silently.

**A confirmed equivalence is the integrator's fact too.** That part numbers of
several sites are one purchased item is no site's knowledge and no rule's verdict:
the layer proposes the groups (`/query/equivalents`), a user confirms one.
`POST /core/equivalences`, body `{ parts }` (two to sixteen distinct part IRIs,
`https://example.com/atelier/{fr|de|uk|es}/part/{id}`), appends `<a> owl:sameAs <b>`
for every ordered pair to the links graph with a Graph Store `POST`, synchronously,
then publishes `equivalence.confirmed`. Response `{ parts, triples: { links },
eventId }`. Each request is logged by the core service; the released `links.ttl`
holds no `owl:sameAs`, so the reset's PUT of it turns every confirmed group back
into a proposal. The Purchasing screen offers "Confirm equivalence" on each
proposed group to `programme-cleared` and the officer, and marks a confirmed one;
other profiles read who confirms.

**3. A CAD publication is the PLM's fact, indexed by the integrator.** The
released dataset ships the ornithopter's head hoop `FR-ORN-CERC-001` without a
file-index entry (`cadPending: true` in `data/products/ornithopter.json`: its
STEP file is in the bucket, the file index has no `atelier:cadFile` for it, the
viewer shows it as "CAD not published", the `cadMissing` shape reports it).
`POST /{plm}/demo/events/cad`, body `{ part, cadFile }`, puts one
`part.cad.published` event; the links loader sets `atelier:cadFile` on the part
IRI in the file index with a SPARQL `DELETE/INSERT` (one value per part). The
presigned URL and the 3D part appear on the next load.

**The change list is derived, not declared.** `GET /query/demo/changes` returns
`{ values: [rows of demo_change], graph: { added: [{ s, p, o, sIri, oIri, plm }],
removed: [...], triples: { links, fileindex } } }`, where `graph` is the live
named graphs diffed by value against the released `data/links.ttl` and
`data/fileindex.ttl` bundled in the query service image. The Changes strip shows
value corrections ("graph: no change, via event"), link writes ("links graph +2
triples, event emitted"), confirmed equivalences (one entry per confirmed group,
"links graph +12 triples" for four parts) and CAD publications ("file index +1
triple, via event").

**Reset is deterministic.** `POST /core/demo/reset` (officer) replays every
`demo_change` row newest first through `POST /{plm}/demo/update` (the core
service calls the PLM services through the API gateway with the origin secret
and the officer profile, purpose `reset: <id>`, the path built from the
validated PLM code), Graph Store `PUT`s the bundled
`links.ttl`, `fileindex.ttl` and `labels.ttl` over their named graphs (the head hoop
loses its entry again, every confirmed equivalence is gone), deletes the log and
returns `{ undone, graphs: { links, fileindex, labels } }`. The loader
ignores a correction whose `purpose` starts with `reset:`, so the log stays
empty. A seeded cell may hold a value outside its column's form (the French
reference `XR-0002` points at `urn:plm:es:pieza:ES-3203`); the PLM accepts such a
value from the officer's `reset:` request alone, and only when `demo_change`
records it as the before of a correction of that table, key and column (read over
the PLM's read-only connection to the core database), so the reset restores
exactly the seeded value while every other correction keeps the form. A redeploy does not reset data while the seed is unchanged (Flyway skips
a repeatable migration whose checksum it has already applied); a changed seed
replaces the whole dataset on the next start of the service.

**Every seeded defect is fixable by the site that owns the record.** The layer
finds the defect and never edits a site's data; the owning site releases a
correction in its own PLM, in its own words and units, and the rule passes on the
next read. The screen offers "Release correction in <PLM> PLM" with the
proposal pre-filled from the evidence and editable, and shows the native table,
key, column, stored value and proposed value; the proposals are built in
`modules/web/src/check/demo/proposals.ts`, the module the fixture and smoke
cycles release too.

| Rule | Who releases | Record corrected | Proposal |
|---|---|---|---|
| position | the site of the record off the joint plane (either side) | the feature's position on the failing axis | the mate's position in the site's unit |
| unit | the site of the unitless row | its unit column (UK `pos_uom`, `dia_uom`, `rating_uom`) | the unit the site stores there (UK `IN`, `PSI`) |
| connector | either side ("match the <other> record") | connector type and pin count | the mate's values |
| fastener | either side | standard, count, diameter | the mate's values, the diameter converted to the site's unit |
| hydraulic | either side | standard, dash size, fluid, rating | the mate's values, the rating converted to the site's unit |
| orphan | the integration role | a mating link in the links graph | the candidate mate at the orphan's position |
| cadMissing | the part's site | the file-index entry, by `part.cad.published` | the part's released STEP key |
| danglingReference | the referring site | the reference's URN, and its expected revision when the resolved part is at another | the URN with the code of the site holding a part of that local id; no proposal when none does |
| staleRevision | the referring site | the reference's expected revision | the target's current revision |
| lifecycleConflict | the dependency's site, or the dependent item's | the dependency's lifecycle word, or the item's | the site's released word (Freigegeben, Publié, Liberado, Released), or its blocked word |
| massLimit | the site of the part the engineer picks | that part's mass | none: the parts are listed heaviest first, the engineer chooses |
| massScale | the site storing grams | every flagged part's mass, in one release | each value divided by 1000 |
| conflictingLeadTime | the site holding the offers | the longer offer's lead time, or its preferred flag | the other offer's lead time, or not preferred |

The buttons show to the owning site's engineer and to the officer; other
profiles read "<PLM> engineer or officer only". The interface detail carries the
feature rules (the records table, one button per side), each part's findings
(lifecycleConflict) and its reference list; the bill of materials carries
lifecycleConflict on its nodes; the product findings section of the check panel
carries massLimit and massScale; Purchasing carries the lead-time conflicts.
After a release the screen waits for every row to reach the change list, reads
the answers again and the finding is gone; the Changes strip lists the rows of a
release together; the reset undoes them. The Changes strip is visible to every
profile but `unknown`; "Reset demo data" is the officer's.
The Data flow tab's "Last change" mode (officer) draws the three paths from the
change feed.
