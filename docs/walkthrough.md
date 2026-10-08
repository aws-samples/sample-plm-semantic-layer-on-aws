# Walkthrough

A tour of the running sample, screen by screen. It takes about twenty minutes
and needs a deployed stack, one Cognito user (`scripts/bootstrap-user.sh`) and
the export-control officer's profile for the reset at the end. Every figure
below is computed by the layer when the screen loads; nothing is stored in the
UI. `docs/contract.md` is the reference for every name used here.

## Before you start

Open the site and sign in. The top bar has seven tabs (Interface check, Paths, Bill of
materials, Data catalogue, Rules, Data flow, Architecture), a **Product** switcher, a **Viewing as**
profile switcher, **Run rules** (on Interface check) and **Ask** (on every tab). The site opens on the ornithopter
(`infra/scripts/deploy.sh` names it in the site's `config.json`): leave
**Ornithopter ground demonstrator** in the Product switcher and the profile on
**Programme cleared**. Click **Run
rules** once: the first answer after a deploy costs a few seconds while Ontop
translates every request, and the warm-up makes the rest of the tour quick.

## 1. What you see

Interface check, programme-cleared, ornithopter. Four site PLMs, each with
its own tables and names, read in place; the machine is assembled from each
PLM's own STEP file (ISO 10303, the CAD exchange format), one per part, coloured by owner: the French frame and
hydraulics, the British left wing, the German right wing, the Spanish crank,
pedals, cords and tail. The legend counts the parts per PLM and the
supplier-built parts, painted in their own colour. The note under the Product
switcher is the product's coordinate frame as the core database states it; a
product that breaks a product rule shows the finding under it. Pick **1U CubeSat**
as the export-control officer: "cubesat weighs 2.01 kg over every site's parts,
more than its limit of 2.0 kg", a sum over four sites that no PLM can make. The
ornithopter breaks its own: "ornithopter weighs 261.237 kg over every site's
parts, more than its limit of 260.5 kg", the rated load of its ground test
stand.

The data: 76 parts (FR 22, UK 18, DE 15, ES 21), 76 interfaces of which 25
are between parts owned by different PLMs, each with plugs, fasteners and, on
the hydraulic, ballast water and grease joints, couplings. The tally reads 69 pass,
7 fail, 0 not evaluable. The geometry follows the folio and the museum model; the interface
features, their standards, counts and positions are illustrative, as the legend
says.

## 2. The mechanism on one defect

Click **IF-13**, the right wing root: the French root carrier against the German
root fitting. The camera moves there; the detail view shows the two plugs,
`FR-ORN-EMPL-R-001-J01` and `WURZ-R-61080-X01`, 4.3 mm apart on y (640.0
against 644.3) for a 2.0 mm tolerance. Read the two source records side by
side: same unit, same connector type, same pin count, only y differs, and the
German record is marked as the outlier because the French one sits on the hinge
pin axis. The rule is a SHACL (Shapes Constraint Language) shape in `ontology/shapes.ttl`,
checked by a SHACL validator, deterministic, and the message is the shape's own text with the
measured values filled in.

## 3. Units are the real problem

Click **IF-05**, the British left wing root fitting against the British left
inner spar. The UK PLM stores positions in inches with a per-row unit column;
the fastener `HL 6180-02` has no value in `pos_uom`, so its position cannot be
converted and the `unit` rule fires instead of silently comparing an inch figure
with a millimetre one. A missing unit is a data-quality violation, not a
rounding error.

Then **IF-02**, the French hydraulic power pack against the British wing
actuator, two coupling pairs. One pair passes: 3000 psi against 206.8 bar,
converted through QUDT (Quantities, Units, Dimensions and Types) before the
comparison. The other fails: `HC 6190-01` is
rated 5000 psi against the same 206.8 bar. R2RML (RDB to RDF Mapping Language) cannot convert;
it maps the value and the unit, and the rule converts. A unit column cannot catch
everything: on **IF-154**, the French ballast water tank against the Spanish
tail trim bottle, the Spanish coupling `DEPO-6130-A01` holds 43.5 in its
`presion_bar` column, the psi figure of a 3.0 bar data sheet, and the rule fails
it against the French 3.0 bar.

## 4. Where the answer came from

The strip at the bottom, **Answer path**, lists the sources the answer was
assembled from: five virtual graphs (the four site PLMs and the core
database), the materialised graph, then the SHACL rules, each with its triples
and milliseconds. With IF-05 selected, click **ontop-uk**:

- the request sent to that PLM, restricted to the parts this profile may see (a `VALUES` list of visible part IRIs);
- the SQL Ontop generated for it over `fastener` and `component`, with those keys in the `WHERE`: what actually ran on the database;
- the native rows of the UK table as they are, with the empty `pos_uom` outlined;
- the R2RML, generated from the object-relational mapping (ORM) annotations, never hand-written.

Click **neptune**: only interfaces and `matesWith` links live here; the graph
tab draws them. Click **SHACL rules**: the shape that fired and the validation
report. The materialised graph holds only what connects the systems; everything
else stays in the PLMs.

## 5. The ORM is the catalogue

**Data catalogue** tab. Open the UK PLM, table `fastener`: the unit columns
`pos_uom`, `dia_uom` and `grip_uom` are declared on the entity with `@Unit`, and
the catalogue and the R2RML are derived from the same annotations; a CI job
regenerates the mappings and fails on a difference, so a drift between the
virtual graph and the code is caught before it merges. Open the ES PLM, table `conector`: one column,
`obsoleto`, has no `@Describe` and is flagged undescribed.

Then the fifth root, the core database: `part_tag`, the export-control tag per
part, tagged by which PLM and when; `product` and `product_part`, the product
structure. The site tables are exactly as the PLM defines them; each tag records
which PLM tagged the part and when, the integrator stores the tags in its own
database (seeded by `data/generate.py` in the sample), and the file index in the
graph says where each CAD file is. Nothing was added
to a site schema.

## 6. Export control

Top right, **Viewing as**. Switch to **DE engineer**: the French control post
and instrumentation pod, tagged NATIONAL-FR, disappear from the machine; the
five interfaces that touch them (IF-03, IF-15, IF-30, IF-43, IF-44) become "not
evaluable", and the tally moves to 66 pass, 5 fail, 5 not evaluable. Open one
of them: the hidden part is "a part not visible to your profile", never named.
Open the evidence for **ontop-core**: the releasability filter is pushed into
the SQL over `part_tag`, and the PLM arms were then asked only for the visible
parts, so hidden rows never left their PLM. A part without a tag would be hidden
from everyone but the officer and listed as a finding against its PLM.

Switch to **Export-control officer**: on this product the officer and the
programme-cleared profile see the same 69 / 7 / 0; the product where they differ
is the aerial screw (section 10). The profile stands in for identity-provider claims; the
enforcement point is the query, in the layer and in the SQL, not the screen.

## 7. Data flow

**Data flow** tab, mode **Last click**: the request you just ran drawn on the
architecture, with the measured triples and milliseconds per hop, from the
browser through CloudFront and API Gateway to the query service, the five Ontop
endpoints, Neptune and the SHACL validator. Click a node for its facts. Three
points to take from it: the ontology, mappings and shapes are files under
version control and the stores are replaceable; adding a feature type is
annotating an entity and writing a shape; at programme scale the same validation
runs on the business events and viewers read persisted reports.

## 8. Agents on the layer

**Architecture** tab. Each layer of a PLM reference architecture on the left,
the component that plays it here on the right, with a live health dot; the last
two rows are the agent and the MCP (Model Context Protocol) tool interface, with the tool count read from
the server. Expand the tool list: the twenty-five tools and what each answers, served
by the same registry the MCP server exposes, which is what any agent platform
sees with `tools/list`. `catalogue` and `ontology` are there so the agent reads
the ORM's catalogue before writing SQL and the ontology before writing SPARQL (the RDF query
language).

Top right, **Ask**. The panel opens beside whichever tab is showing and keeps its
conversation as the tabs change. It offers one question per kind of tool. Ask "Which
interfaces fail for my profile and why?": the answer streams, the failing
interfaces light up on the machine, and under the answer the **Agent path**
shows the tools that ran with their milliseconds, then the tokens and the
estimated cost of that one question. The prompt forbids the agent to state a figure a tool did
not return, and the Agent path shows which tools ran, so a claim can be checked
against them.

Ask "Where is FR-ORN-KEEL-001 used?" (`where_used`), then "How many UK fasteners
are stored per unit of measure?": the agent reads the UK catalogue first, writes
one `SELECT` against those tables and columns, and the PLM service validates it
against the same catalogue, rewrites every table into its releasability-filtered
view and runs it read-only. Expand **SQL that ran**: the rewritten SQL is what
executed. An invented column is rejected with the closest real ones, never
answered with empty rows, and the provenance carries `actor: agent`.

Switch to **DE engineer** and ask "Is FR-ORN-PCMD-001 releasable to me?": the
answer is "not visible to your profile", in the same words as the screen. The
same tools serve a desktop client: `docs/kiro-mcp.md` configures Kiro or Claude
Code with the MCP URL and two headers.

## 9. Three kinds of change, three paths

The rule: the system that owns a fact writes it in its own store and announces
a business event; nobody watches storage. The same rule decides who may act: the
owning PLM's engineer for its values and its files, the integration role for a
mating link, the officer only to reset. Every control calls the owning service
directly; the query service only reads.

**A value correction stays in the PLM.** As **DE engineer**, select **IF-13**.
The German plug is the outlier; **Release correction in DE PLM** is offered with
640 pre-filled. Release it: the German PLM corrected one value in its own table
through its own service, and the layer wrote nothing. **Run rules**: IF-13
passes and the tally moves to 67 pass, 4 fail, 5 not evaluable. The **Changes**
strip shows the correction with "graph: no change, via event" a second or two
after the click: the PLM announced `part.value.corrected` on the bus and the
loader logged it; the PLM never called the integrator. The evidence for
`ontop-de` shows the new row under the same SQL. A measurement is not a
relationship, so it never reaches the graph.

**Every defect is fixed the same way.** Each failing rule offers its owning
site's release, pre-filled from the evidence: the UK row without a unit takes
the unit the UK stores (`IN`), a connector, fastener or coupling pair offers
"match the other record" on either side (a rating converted to the site's psi
or bar), a dangling reference takes the URN of the site that holds the part, a
stale one the target's current revision, a lifecycle conflict the dependency's
released word (or blocks the item), the lead-time conflict aligns the longer
offer, the difference engine's French masses are divided by 1000 in one release
of 58 rows, and the cubesat's mass limit lists its parts heaviest first for the
engineer to choose. Each is one request to the owning PLM, one transaction, one
`part.value.corrected` event.

**A CAD publication is the PLM's fact.** Switch to **FR engineer**. The head
hoop is missing from the machine and the legend reports one part without a
published CAD file; open its detail: "CAD not published by FR PLM", with
**Publish CAD file from FR PLM**. Publish it: the PLM emits `part.cad.published`,
a rule delivers it to the loader, one file-index triple is set, and the head
hoop appears. The strip shows "file index +1 triple, via event" and the release
finding clears.

**A mating link is the integrator's fact.** Switch to **Programme cleared**,
select **IF-30**, the French control post against the Spanish tail servo: the
Spanish plug `SERV-6120-C03` mates with nothing. **Publish link** is offered,
pre-filled with the unmated French plug at the same position,
`FR-ORN-PCMD-001-J03`. A mating between two PLMs is a fact no site PLM
holds, so the integration role writes it through the core service, which lands
the two `matesWith` triples in the links graph and publishes
`interface.link.added` for whoever subscribes. The strip shows "links graph +2
triples, event emitted"; on the next run IF-30 passes. A plug mates with one
feature; a second mate would fail the interface (`doubleMate`).

**Reset.** Switch to **Export-control officer**. **Data flow**, mode **Last
change**: the three paths you just used, drawn from the change feed (the
correction into the German database with no graph change, the CAD event through
the bus and the loader into the file index, the link through the core service
into Neptune with its event out). Back on **Interface check**, **Reset demo
data**: the log is replayed in reverse through the PLM services and the
released graphs are put back; the head hoop disappears again and the tally
returns to 69 / 7 / 0.

## 10. Switch product

Product switcher, **Aerial screw working replica**. Every screen, every tool
answer and the agent's default scope now cover the second machine: 36 parts
(FR 11, DE 11, UK 7, ES 7), 52 interfaces (IF-48 to IF-99) of which 38 are
between parts of different PLMs, 72 plugs, 90 fasteners, 10 hydraulic
couplings, and no defect. The frame note under the switcher changes with the
product.

As **Programme cleared** the tally reads 49 pass, 0 fail, 3 not evaluable: the
German hydraulic motor `HMOT-70090` is tagged EXPORT-LICENCE, releasable only
after a licence review, so its three interfaces (IF-89 the reducer flange,
IF-90 the manifold lines, IF-99 the motor mount) are not evaluable and the
motor is "a part not visible to your profile". Ask "Is HMOT-70090 releasable to
me?": not visible. Switch to **Export-control officer**: 52 / 0 / 0, the motor
appears, and the same question answers with its jurisdiction, releasability and
who tagged it. This is the product on which the officer and the
programme-cleared profile differ.

Nothing about either machine is written into the layer: the ontology, the
shapes, the mappings, the twenty-five tools and the screens are the same for both.
A third product is a third file under `data/products/` and its STEP files.

## 11. The bill of materials no site holds

As **Export-control officer**, switch to the **5 MW reference-class wind
turbine** and open **Bill of materials**. No PLM holds this list: the product
root exists only in the layer, and under it each workshop's own kit, the French
rotor and cover kit, the German drivetrain kit, the British electrical and
control kit, the Spanish structure kit, each read from its own idiom (a parent
column on the German part row, the French link table, the Spanish JSON document
on the parent row, the British indented rows). The roll-up strip counts 3,738
part occurrences, DE 273, FR 1,414, ES 1,811, UK 240, from 58 assemblies and
239 lines: quantities multiply down the tree, masses add up from the parts, and
a part without a mass is noted and left out of the total. Expand a kit: every
node carries its native name and id, the quantity its parent uses, its
occurrences, its unit mass and its mass.

Switch to **Programme cleared**: the British SCADA server cabinet `UK-3737`,
tagged US-EAR, becomes "an item not visible to your profile" at its place in
the British kit, not expanded, and the strip counts its occurrences apart as
hidden by export control. Ask "Which assemblies use D-37002?"
(`bom_where_used`), then "Where is D-37002 used?" (`where_used`): the first
answers the assembly the main shaft flange is built into, the second the
interfaces it sits on.

Nobody asks about a whole aircraft at once: an engineer asks about one
assembly. On the British **Hub control** `UK-3776`, choose **Open as subtree**.
The layer asks the British PLM for that assembly's tree, which its database
reads from its closure table, follows the tree's external references to
the German main shaft and pitch cylinder and the French blade root sensor
mount, and asks each of those sites for their trees in turn: two rounds, three
sites, twelve items, and no request lists a part. The breadcrumb shows the
root; the interface check and the viewer then hold only the subtree, the far
side of each interface drawn faded as context, and the product mass rules say
they are not evaluated on a subtree. **Whole product** returns to the product.

## 12. Paths through the product

A bill of materials says what contains what; function says where power goes.
Switch to the **Self-propelled cart**, open **Paths**, keep **Follow the flow**
and type `FR3101`, the French left drive spring: the view isolates the path in
the site colours over a ghost of the cart. The spring turns the German crown
wheel through `IF-100`, the crown wheel meshes the lantern pinion inside the
German site (no interface, a gear stage of 30 teeth over 6, ×5), the pinion's output
shaft reaches both Spanish stub axles and both drive wheels, and the step through
`IF-103` is red: the seeded fastener defect sits on the drive line. The gear
ratio card reads ×5. Switch to **Joined by interfaces** and ask from `FR3101` to
`ES-3104`: no interface path joins them, though the spring drives the axle, since
a gear mesh is no interface.

Back on the ornithopter, follow the flow from the left crank arm `MANV-L-6012`: the
arm turns the crank shaft through `IF-163`, the shaft the Spanish lantern pinion
through `IF-150`, the pinion meshes the
French peg wheel `FR-ORN-ROUE-001` (a gear mesh between the two sites with no
interface declared, a gear stage of 8 staves over 32 pegs, ×0.25), the peg wheel
turns the windlass drum through `IF-151`, and the drum's
two drive cords reach both outer spars and both outer rib sets. The card reads
×0.25, and the check line carries the pinion's 40 rpm to 10.0 rpm at the drum
against its rated 10. The peg wheel carries a `meshModule` finding: the French
site recorded a 12.7 mm module against the pinion's 12 mm.

Switch to the **5 MW reference-class wind turbine** and follow the mechanical
flow from the hub `ES-3701`: the planetary stage (sun against the fixed ring),
the two parallel stages, the coupling and the generator rotor. The card reads
×97.02, the design ratio, and the check line carries the hub's 12.1 rpm through
the train to 1173.9 rpm against the generator's rated 1173.7: within 1 %. The
high-speed pinion `D-37031` carries a `meshModule` finding: the German site
recorded it at 18 mm against the 20 mm wheel it meshes. As **French engineer**
the walk stops at the German sun gear, a part not visible to that profile, and
the ratio is not computed. Ask the agent "What does the cart's drive spring
drive?" (`flow_path`): it answers the same steps, each with its joint and status.

## 13. One slot, two options

Back on the **Ornithopter** as **Programme cleared**, scroll the Interface check
panel to **Variants**: two groups, the wing return at its default (return cords
from the stirrups, Leonardo's preference in folio 74v) and the crank build at
its default (the crank assembly of five parts). Take **spring-return**. The
rules run on both configurations and the panel lists the ports of the parts
both hold: each root fitting's return eye changes its mate from the ES cord to
the UK or the DE spring and passes, the UK pin written 0.3937 in, 10 mm after
conversion; the left root carrier's spring anchor is added and fails the
fastener rule, the UK spring's 0.3150 in pin against the FR 10 mm bore; the
stirrup's heel eyes lose their mates. The option reads 68 pass, 8 fail against
69, 7 on the default. Take **one-piece** on the crank build: the forward cross
beam's left crank bearing turns from `IF-25` fail to `IF-184` pass, since the
forged crank is drilled 10 mm as the FR beam. The `variant_diff` tool answers
the same ports to the agent and to any MCP client, for a question such as "What
changes on the mating interfaces if the ornithopter takes the spring return?".

## Figures to have in mind

Computed by `python3 data/generate.py` from the product files and
`ontology/policy.json`:

| | Ornithopter | Aerial screw |
|---|---|---|
| Parts | 76 (FR 22, UK 18, DE 15, ES 21), 6 supplier-built | 36 (FR 11, DE 11, UK 7, ES 7), 2 supplier-built |
| Interfaces | 76 (IF-01 to IF-47, IF-150 to IF-178), cross-PLM 25 | 52 (IF-48 to IF-99), cross-PLM 38 |
| Features | 190: 36 plugs, 126 fasteners, 28 couplings | 172: 72 plugs, 90 fasteners, 10 couplings |
| Seeded defects | IF-13 position, IF-03 connector, IF-05 unit, IF-02 hydraulic, IF-25 fastener, IF-30 orphan, IF-154 hydraulic | none |
| Officer, programme-cleared, FR engineer | 69 / 7 / 0 | officer 52 / 0 / 0; others 49 / 0 / 3 |
| DE, UK, ES engineers | 66 / 5 / 5 | 49 / 0 / 3 |
| No profile | 60 / 4 / 12 | 44 / 0 / 8 |
| STEP files | 76 files, 1,820 KB | 36 files, 541 KB |

Spare plug (declared on no interface): `FR-ORN-PCMD-001-J03` on the control
post, the candidate mate of the orphan. Part released without a published CAD
file: `FR-ORN-CERC-001`, the head hoop. Export control: `FR-ORN-PCMD-001` and
`FR-ORN-NACI-001` NATIONAL-FR; the six hydraulic items EU-DUAL-USE;
`HMOT-70090` EXPORT-LICENCE.
