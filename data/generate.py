#!/usr/bin/env python3
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Generates every seed artefact from the product files in data/products/*.json.

Each product file describes one machine in the same schema: `product` (key, name), `frame`, `parts`,
`interfaces` with their plug, fastener and hydraulic coupling pairs, `sparePlugs`, `seededDefects` and,
in `extended`, each site's bill of materials (`assemblies` and `bomLines`, see data/bom.py), its uses of other
sites' parts (`externalRefs`, see data/references.py), with `seededReferenceDefects` the references that fail and
`seededLifecycleDefects` the released items that depend on a working or blocked one (see data/lifecycle.py), its
non-geometric items (`nonGeometricItems`, software and documents, see data/items.py), when the product
has one, its mass limit (`massLimitKg`) and, when it has them, its functional edges (`functionalEdges`, what drives
what), rated speeds (`ratedSpeeds`) and gears (`gear` on a part, or `meshes`), with `seededMeshDefects` the meshes whose
modules disagree (see data/functional.py), and its stations and sections (`stations`, `sections`, see
data/stations.py).
Outputs, each covering every product:
  - the Flyway seed migration (R__products_seed.sql) of each site PLM service: it empties the part,
    feature and bill-of-materials tables and inserts every part, assembly, plug, fastener and hydraulic
    coupling the PLM holds, in the PLM's native schema and units, the spare plugs of `sparePlugs` included
    (plugs a PLM holds that no interface declares; the demo mates one of them to the orphan plug), and the
    site's bill of materials in its own idiom (DE parent column on the part row, FR nomenclature link
    table, ES lista_materiales document on the parent row, UK indented bom_line rows) with the placements of its
    lines (DE einbaulage, FR nomenclature_position, ES posiciones in the document lines, UK bom_line_placement;
    see data/placements.py), its external
    references (externer_verweis, reference_externe, referencia_externa, external_ref), one row per
    reference a part row makes, the description of each purchased item on its part row, the tooth count and module
    of each gear on its part row (see data/functional.py), the station span of each part on its part row and of each
    occurrence on its placement row (see data/stations.py) and the site's suppliers and supplier offers (see
    data/purchasing.py);
  - the Flyway seed migration (R__products_seed.sql) of atelier-core: it empties product, product_part
    and part_tag and inserts the products, the product each part and assembly belongs to and one tag row
    per part and assembly (export-control jurisdiction and releasability, tagged by the owning PLM);
  - data/links.ttl, the link-store content for the named graph https://example.com/atelier/graph/links: the
    interfaces, the functional edges (atelier:Drive, atelier:drives) and rated speeds of each product, and its
    stations and sections;
  - data/fileindex.ttl, the file index for the named graph https://example.com/atelier/graph/fileindex:
    where each part's CAD file is and, for supplier-built parts, who builds it (atelier:builtBy);
  - data/labels.ttl, the labels graph https://example.com/atelier/graph/labels: every item's native name and
    English name, and the products' glossary as the concept scheme atelier:Terms (see data/terms.py);
  - data/options.ttl, the options graph https://example.com/atelier/graph/options: the variant groups of each product
    (`extended.variants`, see data/variants.py), their options, the items and interfaces each option applies under, and
    the interfaces, lines and functional edges only an option holds. An option's own parts and features are rows of
    their sites' tables with the option's code, and every feature row carries its port; an option's items have a tag in
    the core and no product_part row.

Each service's versioned migrations are its schema and this script never touches them. The seed is a
repeatable migration: Flyway re-applies it whenever its checksum changes, after every versioned migration,
so a product added to data/products/ reaches a running deployment with an ordinary redeploy of the services.

Before writing anything it checks the data against the integration contract (docs/contract.md):
unique part ids and feature keys across products (they are keys in the workshops' databases), interface
ids unique within a product (two products may both have an IF-13; an interface is identified by its product
and its id), key length, features owned by the interface's parts, a STEP file per part under
modules/cad/stp/<product key>/, storage units per PLM, classification vocabulary, at most one position
row without a unit per product (a UK row, the seeded unit defect), and that
the interface rules (unit, position, connector, fastener, hydraulic, orphan) fail on exactly the
interfaces each product lists in `seededDefects` and nowhere else, and the reference rules
(danglingReference, staleRevision) on exactly the references it lists in `seededReferenceDefects`, the
dangling and stale references its proposed defects name, and the lifecycle rule (lifecycleConflict) on exactly
the dependencies it lists in `seededLifecycleDefects`. Output is deterministic, so a
second run changes nothing. It ends with the talk-track figures per product: parts per PLM,
supplier-built parts, interfaces and cross-PLM interfaces, features by kind, spare plugs, the
pass / fail / not-evaluable tally of every profile in ontology/policy.json and the size of the STEP files.

Standard library only:  python3 data/generate.py [--out <dir>]
`--out` writes the same files under <dir> instead of the repository (a dry run against a scratch directory).
"""

import argparse
import json
import re
import sys
from decimal import Decimal
from pathlib import Path

import bom as boms
import placements as places
import references as refs
import purchasing
import items as nongeo
import lifecycle
import functional
import terms
import stations
import variants

ROOT = Path(__file__).resolve().parent.parent
PRODUCTS = ROOT / "data" / "products"
LINKS = Path("data/links.ttl")
FILE_INDEX = Path("data/fileindex.ttl")
LABELS = Path("data/labels.ttl")
OPTIONS = Path("data/options.ttl")
STEP_DIR = ROOT / "modules" / "cad" / "stp"
POLICY = ROOT / "ontology" / "policy.json"
MIGRATIONS = Path("modules/plm-services")
CORE = MIGRATIONS / "atelier-core/src/main/resources/db/migration/R__products_seed.sql"

BASE = "https://example.com/atelier"
LINK_GRAPH = f"{BASE}/graph/links"
FILE_INDEX_GRAPH = f"{BASE}/graph/fileindex"
OPTIONS_GRAPH = f"{BASE}/graph/options"
MM_PER_UNIT = {"MilliM": Decimal("1"), "IN": Decimal("25.4")}
PA_PER_UNIT = {"BAR": Decimal("100000"), "PSI": Decimal("6894.757293168")}
DIAMETER_TOLERANCE_MM = Decimal("0.05")
RATING_TOLERANCE = Decimal("0.02")
JURISDICTIONS = {"EU-DUAL-USE", "EXPORT-LICENCE", "US-EAR", "NATIONAL-FR", "NATIONAL-DE", "NATIONAL-UK",
                 "NATIONAL-ES", "NONE"}
RELEASABILITIES = {"ALL", "EU", "FR", "DE", "UK", "ES", "LICENSED"}
KEY_LENGTH = 64  # VARCHAR(64) keys in every native schema
PART_TYPES = {"PART", "ASSEMBLY", "SOFTWARE", "DOCUMENT"}
MASS_UNITS = {"g", "kg", "lb"}  # g: a site that enters grams in a kilogram column is a seeded defect
MASS_SCALE = 3  # NUMERIC(12, 3) mass columns in every native schema
PRODUCT_KEY_LENGTH, PRODUCT_NAME_LENGTH = 32, 120  # atelier-core product(product_key, name)
TOOL = "Generated by data/generate.py"
# Licence header of every generated file, in the comment syntax of its type (the tree-wide SPDX header).
LICENCE = ("Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.", "SPDX-License-Identifier: MIT-0")
COMMENT = {".sql": "-- ", ".ttl": "# "}
HEADER = f"{TOOL} from data/products/*.json; do not edit by hand."
# Publication time recorded on every tag; the seed is a snapshot, so one instant for all parts.
TAGGED_AT = "2026-09-01T08:00:00Z"

# Feature kinds: the list they live in on an interface, and their IRI path segment.
KINDS = {"pairs": "plug", "fasteners": "fastener", "couplings": "coupling"}

# Per PLM: seed migration file and header, native tables (columns in insert order), decimal scale
# and the fixed storage units of its quantities (None = every unit is stored per row in a *_uom
# column). Position columns come right after the key and the part FK in every table. The part table
# carries the part attributes after the file reference: revision in the PLM's own form (a pattern, or
# int for an integer revision), lifecycle word from the PLM's vocabulary, mass in the PLM's storage
# unit, material, part type. `tree` marks the PLM whose bill of materials is a parent column on the part row;
# `bom` names the other PLMs' line table (columns in insert order) or, for ES, the document column; `placements` the
# table of the line placements, one row per occurrence (ES keeps them in the document).
PLMS = {
    "FR": {
        "seed": "plm-fr/src/main/resources/db/migration/R__products_seed.sql",
        "comment": "Jeu de données ornithoptère et vis aérienne : pièces, connecteurs, fixations et raccords hydrauliques",
        "part": ("piece", "ref_piece, designation, fichier_cao, indice, etat, masse_kg, matiere, type_piece"),
        "bom": ("nomenclature", "parent, enfant, quantite, repere"),
        "placements": ("nomenclature_position", "parent, enfant, rang, x_mm, y_mm, z_mm, rx_deg, ry_deg, rz_deg"),
        "refs": ("reference_externe", "id, piece, urn, quantite, indice_attendu, note"),
        "revision": r"[A-Z]", "lifecycle": ("En cours", "Publié", "Bloqué", "Remplacé"), "mass": "kg",
        "plug": ("connecteur", "id_connecteur, ref_piece, pos_x_mm, pos_y_mm, pos_z_mm, type_connecteur, nb_broches"),
        "fastener": ("fixation", "ref_fixation, ref_piece, pos_x_mm, pos_y_mm, pos_z_mm, norme, diametre_mm, nombre, longueur_serrage_mm"),
        "coupling": ("raccord_hydraulique", "ref_raccord, ref_piece, pos_x_mm, pos_y_mm, pos_z_mm, norme, taille_dash, pression_bar, fluide"),
        "scale": 3, "fixed": {"length": "MilliM", "pressure": "BAR"},
    },
    "DE": {
        "seed": "plm-de/src/main/resources/db/migration/R__products_seed.sql",
        "comment": "Datensatz Ornithopter und Luftschraube: Bauteile, Stecker, Befestiger und Hydraulikkupplungen",
        "part": ("bauteil", "teil_nr, benennung, cad_datei, revision, status, masse_kg, werkstoff, teileart, parent_id, menge"),
        "tree": True,
        "placements": ("einbaulage", "parent_id, teil_nr, lfd_nr, x_mm, y_mm, z_mm, rx_grad, ry_grad, rz_grad"),
        "refs": ("externer_verweis", "id, teil_nr, urn, menge, erwartete_revision, bemerkung"),
        "revision": r"\d{2}", "lifecycle": ("In Arbeit", "Freigegeben", "Gesperrt", "Ersetzt"), "mass": "kg",
        "plug": ("stecker", "stecker_id, teil_nr, pos_x_mm, pos_y_mm, pos_z_mm, typ, polzahl"),
        "fastener": ("befestiger", "befestiger_id, teil_nr, pos_x_mm, pos_y_mm, pos_z_mm, norm, durchmesser_mm, anzahl, klemmlaenge_mm"),
        "coupling": ("hydraulikkupplung", "kupplung_id, teil_nr, pos_x_mm, pos_y_mm, pos_z_mm, norm, dash_groesse, nenndruck_bar, fluid"),
        "scale": 3, "fixed": {"length": "MilliM", "pressure": "BAR"},
    },
    "UK": {
        "seed": "plm-uk/src/main/resources/db/migration/R__products_seed.sql",
        "comment": "Ornithopter and aerial screw dataset: components, harness connectors, fasteners and hydraulic couplings",
        "part": ("component", "comp_id, name, cad_file, revision, lifecycle, mass_lb, material, part_type"),
        "bom": ("bom_line", "line_no, level, parent_part_no, child_part_no, qty"),
        "placements": ("bom_line_placement", "parent_part_no, child_part_no, occurrence_no, x_in, y_in, z_in, rx_deg, ry_deg, rz_deg"),
        "refs": ("external_ref", "id, part_no, remote_urn, qty, expected_revision, note"),
        "revision": r"[PC]\d+", "lifecycle": ("Draft", "Released", "Frozen", "Superseded"), "mass": "lb",
        "plug": ("harness_connector", "conn_ref, comp_id, pos_x, pos_y, pos_z, pos_uom, shell_type, pin_qty"),
        "fastener": ("fastener", "fast_ref, comp_id, pos_x, pos_y, pos_z, pos_uom, standard, dia, dia_uom, qty, grip, grip_uom"),
        "coupling": ("hyd_coupling", "cplg_ref, comp_id, pos_x, pos_y, pos_z, pos_uom, standard, dash, rating, rating_uom, fluid"),
        "scale": 4, "fixed": None,
    },
    "ES": {
        "seed": "plm-es/src/main/resources/db/migration/R__products_seed.sql",
        "comment": "Conjunto de datos ornitóptero y tornillo aéreo: piezas, conectores, remaches y acoplamientos hidráulicos",
        "part": ("pieza", "cod_pieza, denominacion, fichero_cad, revision, estado, masa_kg, material, tipo, lista_materiales"),
        "document": True,
        "refs": ("referencia_externa", "id, pieza, urn, cantidad, revision_esperada, nota"),
        "revision": int, "lifecycle": ("Borrador", "Liberado", "Bloqueado", "Sustituido"), "mass": "kg",
        "plug": ("conector", "cod_conector, cod_pieza, pos_x_mm, pos_y_mm, pos_z_mm, tipo, num_contactos, obsoleto"),
        "fastener": ("remache", "cod_remache, cod_pieza, pos_x_mm, pos_y_mm, pos_z_mm, norma, diametro_mm, cantidad, longitud_apriete_mm"),
        "coupling": ("acoplamiento", "cod_acoplamiento, cod_pieza, pos_x_mm, pos_y_mm, pos_z_mm, norma, tamano_dash, presion_bar, fluido"),
        "scale": 3, "fixed": {"length": "MilliM", "pressure": "BAR"},
    },
}


def fail(message):
    sys.exit(f"generate.py: {message}")


# Per PLM: the gear columns of its part table, tooth count then module (see data/functional.py).
GEAR_COLUMNS = {"FR": "nombre_dents, module_mm", "DE": "zaehnezahl, modul_mm", "UK": "teeth, module_in",
                "ES": "numero_dientes, modulo_mm"}


# ---------------------------------------------------------------- IRIs (R2RML IRI-safe encoding)

def _iunreserved(ch):
    """RFC 3987 iunreserved: the characters R2RML leaves unencoded in template values."""
    if ch.isascii():
        return ch.isalnum() or ch in "-._~"
    cp = ord(ch)
    ranges = [(0xA0, 0xD7FF), (0xF900, 0xFDCF), (0xFDF0, 0xFFEF)]
    ranges += [(base, base + 0xFFFD) for base in range(0x10000, 0xE0001, 0x10000)]
    return any(lo <= cp <= hi for lo, hi in ranges)


def iri_safe(value):
    """R2RML 7.3: every character outside iunreserved becomes %HH of its UTF-8 bytes."""
    return "".join(ch if _iunreserved(ch) else "".join(f"%{b:02X}" for b in ch.encode("utf-8"))
                   for ch in value)


def part_iri(plm, key):
    return f"{BASE}/{plm.lower()}/part/{iri_safe(key)}"


def feature_iri(feature):
    return f"{BASE}/{feature['plm'].lower()}/{KINDS[feature['kind']]}/{iri_safe(feature['id'])}"


def product_iri(key):
    return f"{BASE}/product/{iri_safe(key)}"


def interface_iri(product_key, key):
    """An interface is identified by its product and its id: two products may both have an IF-13."""
    return f"{BASE}/interface/{iri_safe(product_key)}/{iri_safe(key)}"


# ---------------------------------------------------------------- load and check

def load():
    """Every product file, in file-name order; decimals as Decimal."""
    products = []
    for path in sorted(PRODUCTS.glob("*.json")):
        with path.open(encoding="utf-8") as fh:
            data = json.load(fh)
        data["file"] = path.name
        for iface in data["interfaces"]:
            decimals(iface)
        for plug in data["sparePlugs"]:
            plug["position"] = {axis: Decimal(v) for axis, v in plug["position"].items()}
        products.append(data)
    if not products:
        fail(f"no product file in {PRODUCTS.relative_to(ROOT)}")
    return products


def decimals(iface):
    """The interface's tolerance and its features' quantities as Decimal."""
    iface["toleranceMm"] = Decimal(iface["toleranceMm"])
    for feature in features_of(iface):
        feature["position"] = {axis: Decimal(v) for axis, v in feature["position"].items()}
        for key in ("diameter", "gripLength", "rating"):
            if key in feature:
                feature[key] = Decimal(feature[key])


def pairs_of(iface):
    """(kind, a, b) for every mated pair of the interface, plugs first."""
    return [(kind, a, b) for kind in KINDS for a, b in iface.get(kind, [])]


def features_of(iface):
    paired = [f for kind in KINDS for pair in iface.get(kind, []) for f in pair]
    return paired + iface.get("unmatedPlugs", [])


def quantities(feature):
    """(value, unit, dimension) of every measured quantity of a feature, positions first."""
    out = [(feature["position"][axis], feature["unit"], "length") for axis in "xyz"]
    if feature["kind"] == "fasteners":
        out += [(feature["diameter"], feature["diameterUnit"], "length"),
                (feature["gripLength"], feature["gripUnit"], "length")]
    elif feature["kind"] == "couplings":
        out.append((feature["rating"], feature["ratingUnit"], "pressure"))
    return out


def check(products):
    """Checks every product and the whole dataset; returns {part id: part} over all products."""
    parts, keys, feature_ids = {}, set(), set()
    for data in products:
        product = data["product"]
        interfaces, unitless = set(), []
        if product["key"] in keys or len(product["key"]) > PRODUCT_KEY_LENGTH or len(product["name"]) > PRODUCT_NAME_LENGTH:
            fail(f"{data['file']}: product key {product['key']} must be unique, at most {PRODUCT_KEY_LENGTH} characters, "
                 f"with a name of at most {PRODUCT_NAME_LENGTH}")
        keys.add(product["key"])
        own = check_parts(data, parts)
        data["gears"] = functional.gears(data, fail)
        data["items"] = check_items(data, parts)
        data["bom"] = boms.Bom(data, fail)
        places.check(data["bom"], fail)
        places.check_reference(data["bom"], fail)
        data["stations"] = stations.Stations(data, data["bom"], stations.check_bounds(data, STEP_DIR, fail), fail)
        data["assemblies"] = check_assemblies(data, parts)
        for iface in data["interfaces"]:
            if iface["id"] in interfaces:
                fail(f"{product['key']}: interface id {iface['id']} declared twice")
            interfaces.add(iface["id"])
            if len(iface["parts"]) != 2 or any(p not in own for p in iface["parts"]):
                fail(f"{iface['id']}: needs two known parts of {product['key']}")
            for kind in KINDS:
                for pair in iface.get(kind, []):
                    if len(pair) != 2 or pair[0]["part"] == pair[1]["part"]:
                        fail(f"{iface['id']}: a pair needs one {KINDS[kind]} on each part")
                    for feature in pair:
                        feature["kind"] = kind
            for plug in iface.get("unmatedPlugs", []):
                plug["kind"] = "pairs"
            for feature in features_of(iface):
                if feature["part"] not in iface["parts"]:
                    fail(f"{feature['id']}: part {feature['part']} is not on {iface['id']}")
                register(feature, parts, feature_ids)
                if feature["unit"] is None:
                    unitless.append(feature)
        for plug in data["sparePlugs"]:
            if plug["part"] not in own:
                fail(f"spare plug {plug['id']}: unknown part {plug['part']}")
            plug["kind"] = "pairs"
            register(plug, parts, feature_ids, spare=True)
        found = evaluate(data)
        declared = {(d["interface"], d["rule"], tuple(sorted(d["features"]))) for d in data["seededDefects"]}
        if found != declared:
            fail(f"{product['key']}: interface rules disagree with seededDefects:\n"
                 f"  found only:    {sorted(found - declared)}\n  declared only: {sorted(declared - found)}")
        if len(unitless) > 1 or any(f["plm"] != "UK" for f in unitless):
            fail(f"{product['key']}: at most one row, a UK row, may carry no position unit; found {[f['id'] for f in unitless]}")
        functional.check(data, data["gears"], fail)
    check_variants(products, parts, feature_ids)
    check_references(products, parts)
    check_lifecycle(products, parts)
    for data in products:
        terms.check_names(data["parts"] + data["items"] + data["assemblies"], fail)
    purchasing.check(products, fail)
    return parts


def check_references(products, parts):
    """The external references of every product, resolved against every part row of every product: the reference
    rules fail on exactly the references each product lists in `seededReferenceDefects`, which are the dangling and
    stale references its proposed defects name. A target that exists belongs to the referencing product: an answer
    scoped to a product asks the PLMs about that product's parts only."""
    superseded = {plm: spec["lifecycle"][3] for plm, spec in PLMS.items()}
    for data in products:
        key = data["product"]["key"]
        data["references"] = refs.References(data, parts, fail)
        for ref in data["references"].every():
            target = refs.resolve(ref["remoteUrn"])
            item = parts.get(target[1]) if target else None
            if item is not None and item["plm"] == target[0] and item["product"] != key:
                fail(f"{key}: {ref['localPart']} references {ref['remoteUrn']}, a part of {item['product']}")
        found = refs.evaluate(data["references"].every(), parts, superseded)
        seeded = refs.declared(data, fail)
        if found != seeded:
            fail(f"{key}: reference rules disagree with seededReferenceDefects:\n"
                 f"  found only:    {sorted(found - seeded)}\n  declared only: {sorted(seeded - found)}")
        refs.check_proposed(data, seeded, fail)


def check_lifecycle(products, parts):
    """The lifecycle rule over the written bill-of-materials lines and reference rows of every product fails on exactly
    the dependencies each product lists in `seededLifecycleDefects`."""
    vocabularies = {plm: spec["lifecycle"] for plm, spec in PLMS.items()}
    for data in products:
        found = lifecycle.evaluate(data["bom"].lines, data["references"].rows, parts, vocabularies)
        seeded = lifecycle.declared(data, fail)
        if found != seeded:
            fail(f"{data['product']['key']}: the lifecycle rule disagrees with seededLifecycleDefects:\n"
                 f"  found only:    {sorted(found - seeded)}\n  declared only: {sorted(seeded - found)}")


def check_parts(data, parts):
    """Checks the parts of one product; adds them to `parts`, returns the product's own ids."""
    key, own = data["product"]["key"], set()
    for part in data["parts"]:
        check_part(key, part, parts)
        own.add(part["id"])
    return own


def check_part(key, part, parts):
    """Checks one part of product `key` and adds it to `parts`."""
    if part["plm"] not in PLMS:
        fail(f"part {part['id']}: unknown PLM {part['plm']}")
    if part["id"] in parts:
        fail(f"duplicate part id {part['id']}")
    if len(part["id"]) > KEY_LENGTH:
        fail(f"part id {part['id']} exceeds {KEY_LENGTH} characters")
    step = STEP_DIR / key / f"{part['cadPart']}.stp"
    if part["cadFile"] != f"cad/{key}/{part['cadPart']}.stp" or not step.is_file():
        fail(f"part {part['id']}: no STEP file {step.relative_to(ROOT)} for {part['cadFile']}")
    classification = part["classification"]
    if classification["jurisdiction"] not in JURISDICTIONS:
        fail(f"part {part['id']}: unknown jurisdiction {classification['jurisdiction']}")
    if classification["releasableTo"] not in RELEASABILITIES:
        fail(f"part {part['id']}: unknown releasability {classification['releasableTo']}")
    part["attributes"] = attributes(part, PLMS[part["plm"]])
    part["product"] = key
    parts[part["id"]] = part


def check_assemblies(data, parts):
    """The assemblies and site kits of one product as rows of their site's part table; adds them to `parts`."""
    out = []
    for item in data["bom"].assemblies:
        if item["plm"] not in PLMS or item["id"] in parts or len(item["id"]) > KEY_LENGTH:
            fail(f"assembly {item['id']}: needs a known PLM and a unique id of at most {KEY_LENGTH} characters")
        item["attributes"] = attributes(item, PLMS[item["plm"]])
        item["product"] = data["product"]["key"]
        parts[item["id"]] = item
        out.append(item)
    return out


def check_items(data, parts):
    """The software and document items of one product as rows of their site's part table; adds them to `parts`."""
    out = nongeo.non_geometric(data, fail)
    for item in out:
        if item["plm"] not in PLMS or item["id"] in parts or len(item["id"]) > KEY_LENGTH:
            fail(f"non-geometric item {item['id']}: needs a known PLM and a unique id of at most {KEY_LENGTH} characters")
        item["attributes"] = attributes(item, PLMS[item["plm"]])
        item["product"] = data["product"]["key"]
        parts[item["id"]] = item
    return out


def mass_limit(data):
    """The product's mass limit in kilograms, or None; NUMERIC(12, 3) like the part masses."""
    limit = (data.get("extended") or {}).get("massLimitKg")
    if limit is None:
        return None
    limit = Decimal(str(limit))
    if limit <= 0 or -limit.as_tuple().exponent > MASS_SCALE:
        fail(f"{data['product']['key']}: mass limit {limit} must be positive with at most {MASS_SCALE} decimals")
    return limit


def attributes(part, spec):
    """The part's attributes from `extended`, checked against its PLM's idiom: (revision, lifecycle, mass,
    material, type). The mass is written as stored: a part whose massUnit differs from its PLM's storage unit
    keeps its number unconverted, which is how a mass-unit defect is seeded."""
    extended = part.get("extended", {})
    revision, lifecycle = extended.get("revision"), extended.get("lifecycle")
    pattern = spec["revision"]
    if revision is not None and not (isinstance(revision, int) and not isinstance(revision, bool) and revision >= 1
                                     if pattern is int else isinstance(revision, str) and re.fullmatch(pattern, revision)):
        fail(f"part {part['id']}: revision {revision!r} is not in the {part['plm']} form")
    if lifecycle is not None and lifecycle not in spec["lifecycle"]:
        fail(f"part {part['id']}: lifecycle {lifecycle!r} is not one of {', '.join(spec['lifecycle'])}")
    mass = extended.get("mass")
    if mass is not None:
        mass = Decimal(str(mass).replace(",", "."))
        if mass < 0 or -mass.as_tuple().exponent > MASS_SCALE:
            fail(f"part {part['id']}: mass {mass} must be non-negative with at most {MASS_SCALE} decimals")
        if extended.get("massUnit") not in MASS_UNITS:
            fail(f"part {part['id']}: mass unit {extended.get('massUnit')!r} is not one of {', '.join(sorted(MASS_UNITS))}")
    part_type = extended.get("type", "PART")
    if part_type not in PART_TYPES:
        fail(f"part {part['id']}: type {part_type!r} is not one of {', '.join(sorted(PART_TYPES))}")
    return [revision, lifecycle, mass, extended.get("material"), part_type]


def register(feature, parts, feature_ids, spare=False):
    """Keys the feature by (PLM, kind, id), unique over the whole dataset, and checks its quantities."""
    if len(feature["id"]) > KEY_LENGTH:
        fail(f"feature id {feature['id']} exceeds {KEY_LENGTH} characters")
    plm = parts[feature["part"]]["plm"]
    feature["plm"] = plm
    key = (plm, feature["kind"], feature["id"])
    if key in feature_ids:
        fail(f"{KINDS[feature['kind']]} {feature['id']} is declared twice" + (" (spare plug also on an interface)" if spare else ""))
    feature_ids.add(key)
    check_quantities(feature, PLMS[plm])


def check_quantities(feature, spec):
    for value, unit, dimension in quantities(feature):
        known = MM_PER_UNIT if dimension == "length" else PA_PER_UNIT
        if unit is not None and unit not in known:
            fail(f"{feature['id']}: unknown {dimension} unit {unit}")
        if spec["fixed"] is not None and unit != spec["fixed"][dimension]:
            fail(f"{feature['id']}: {feature['plm']} stores {dimension}s in {spec['fixed'][dimension]}, not {unit}")
        if -value.as_tuple().exponent > spec["scale"]:
            fail(f"{feature['id']}: {value} has more than {spec['scale']} decimals")


def evaluate(data):
    """Applies the contract rules to one product; returns {(interface, rule, feature ids)} of every failure."""
    failures = set()
    for iface in data["interfaces"]:
        tol = iface["toleranceMm"]
        for kind, a, b in pairs_of(iface):
            ids = tuple(sorted((a["id"], b["id"])))
            missing = tuple(sorted(f["id"] for f in (a, b) if any(u is None for _, u, _ in quantities(f))))
            if missing:
                failures.add((iface["id"], "unit", missing))
                continue
            for axis in "xyz":
                if abs(to_mm(a["position"][axis], a["unit"]) - to_mm(b["position"][axis], b["unit"])) > tol:
                    failures.add((iface["id"], "position", ids))
            if kind == "pairs":
                if a["connectorType"] != b["connectorType"] or a["pinCount"] != b["pinCount"]:
                    failures.add((iface["id"], "connector", ids))
            elif kind == "fasteners":
                delta = abs(to_mm(a["diameter"], a["diameterUnit"]) - to_mm(b["diameter"], b["diameterUnit"]))
                if a["standard"] != b["standard"] or a["count"] != b["count"] or delta > DIAMETER_TOLERANCE_MM:
                    failures.add((iface["id"], "fastener", ids))
            elif kind == "couplings":
                pa, pb = to_pa(a["rating"], a["ratingUnit"]), to_pa(b["rating"], b["ratingUnit"])
                if (a["standard"] != b["standard"] or a["dashSize"] != b["dashSize"] or a["fluid"] != b["fluid"]
                        or abs(pa - pb) > RATING_TOLERANCE * max(pa, pb)):
                    failures.add((iface["id"], "hydraulic", ids))
        for plug in iface.get("unmatedPlugs", []):
            failures.add((iface["id"], "orphan", (plug["id"],)))
    return failures


def to_mm(value, unit):
    return value * MM_PER_UNIT[unit]


def to_pa(value, unit):
    return value * PA_PER_UNIT[unit]


# ---------------------------------------------------------------- variants and options (see data/variants.py)

# Per PLM: the column of the option code on the part and feature rows that exist only under an option, and the column
# of a feature's port, the identity of the connection point a host part offers an installation slot.
OPTION_COLUMN = {"FR": "variante", "DE": "variante", "UK": "option_code", "ES": "opcion"}
PORT_COLUMN = {"FR": "point_raccordement", "DE": "anschluss", "UK": "port_name", "ES": "puerto"}
OPTION_KEY_LENGTH = 64  # VARCHAR(64) option columns


def check_variants(products, parts, feature_ids):
    """Each product's variant groups: the option items checked as base items are, the option interfaces' features
    registered (a base feature an option interface mates again is the same row), each option's rules against its
    `seededDefects`, then the configurations and the ports."""
    keys, base_features = set(), {}
    for data in products:
        for iface in data["interfaces"]:
            for feature in features_of(iface):
                base_features[(feature["plm"], feature["kind"], feature["id"])] = feature
    for data in products:
        key = data["product"]["key"]
        data["groups"] = variants.groups(data, fail)
        data["optionItems"], data["optionFeatures"] = [], []
        for group in data["groups"]:
            for option in group.options:
                if len(option.key) > OPTION_KEY_LENGTH:
                    fail(f"{option.where}: option key exceeds {OPTION_KEY_LENGTH} characters")
                for part in option.objects["parts"]:
                    check_part(key, part, parts)
                    part["option"] = option.key
                    data["optionItems"].append(part)
                for spec in option.objects["assemblies"]:
                    item = boms.assembly_item(spec, fail)
                    if item["plm"] not in PLMS or item["id"] in parts:
                        fail(f"{option.where}: assembly {item['id']} needs a known PLM and a unique id")
                    item.update(attributes=attributes(item, PLMS[item["plm"]]), product=key, option=option.key)
                    parts[item["id"]] = item
                    data["optionItems"].append(item)
                for iface in option.objects["interfaces"]:
                    check_option_interface(option, iface, parts, feature_ids, base_features, data["optionFeatures"])
                found = evaluate({"interfaces": option.objects["interfaces"]})
                declared = {(d["interface"], d["rule"], tuple(sorted(d["features"]))) for d in option.seeded}
                if found != declared:
                    fail(f"{option.where}: interface rules disagree with its seededDefects:\n"
                         f"  found only:    {sorted(found - declared)}\n  declared only: {sorted(declared - found)}")
        data["configurations"] = variants.check(data, data["groups"], keys, fail)
    port_of = variants.ports([(data, data["groups"]) for data in products], fail)
    for data in products:
        variants.check_ports(data["configurations"], port_of, fail)


def check_option_interface(option, iface, parts, feature_ids, base_features, written):
    """An option interface in the base interface shape; its features are new rows of the option, or base rows."""
    decimals(iface)
    if len(iface["parts"]) != 2 or any(p not in parts for p in iface["parts"]):
        fail(f"{option.where}: interface {iface['id']} needs two known parts")
    for kind in KINDS:
        for pair in iface.get(kind, []):
            if len(pair) != 2 or pair[0]["part"] == pair[1]["part"]:
                fail(f"{iface['id']}: a pair needs one {KINDS[kind]} on each part")
            for feature in pair:
                feature["kind"] = kind
    for plug in iface.get("unmatedPlugs", []):
        plug["kind"] = "pairs"
    for feature in features_of(iface):
        if feature["part"] not in iface["parts"]:
            fail(f"{feature['id']}: part {feature['part']} is not on {iface['id']}")
        plm = parts[feature["part"]]["plm"]
        same = base_features.get((plm, feature["kind"], feature["id"]))
        if same is not None:
            if {k: v for k, v in same.items() if k not in ("port", "plm")} != {k: v for k, v in feature.items() if k != "port"}:
                fail(f"{option.where}: {feature['id']} is a base feature; an option interface mates it as it is")
            feature["plm"] = plm
            continue
        register(feature, parts, feature_ids)
        feature["option"] = option.key
        written.append(feature)


def variant_columns(table_spec, plm, feature):
    """A part or feature table spec with the option column, and for a feature table the port column first."""
    table, columns = table_spec
    return (table, f"{columns}, {PORT_COLUMN[plm] + ', ' if feature else ''}{OPTION_COLUMN[plm]}")


class Iris:
    """The IRIs the options graph names, for data/variants.py."""

    def __init__(self, parts):
        self.parts = parts
        self.product, self.interface, self.safe = product_iri, interface_iri, iri_safe

    def item(self, key):
        return part_iri(self.parts[key]["plm"], key)

    def variant(self, product, group):
        return f"{BASE}/variant/{iri_safe(product)}/{iri_safe(group)}"

    def option(self, key):
        return f"{BASE}/option/{iri_safe(key)}"

    def interface_turtle(self, product, iface):
        return interface_turtle(product, iface, self.parts)


def options(products, parts):
    out = [f"# {HEADER}",
           f"# Content of the named graph <{OPTIONS_GRAPH}>: the variant groups of each product, their options, the items",
           "# each option applies under and the interfaces, lines and functional edges that exist only under an option.",
           *PREFIXES, "@prefix dcterms: <http://purl.org/dc/terms/> .", "@prefix qudt: <http://qudt.org/schema/qudt/> .",
           "@prefix unit: <http://qudt.org/vocab/unit/> .", ""]
    iris = Iris(parts)
    for data in products:
        if data["groups"]:
            out += variants.turtle(data, data["groups"], iris, turtle_string)
    return "\n".join(out)


def configuration_figures(data, parts):
    """Per option: its interfaces and failures, part occurrences per site and the mass of the configuration."""
    out = []
    for group in data["groups"]:
        for option in group.options:
            config = data["configurations"][group.key][option.key]
            failing = sorted({i for i, _, _ in evaluate({"interfaces": config.interfaces})})
            occurrences, mass = configuration_mass(data, config, parts)
            out.append(f"  variant {group.key}, option {option.key}{' (default)' if option.default else ''}: "
                       f"{len(config.interfaces)} interfaces, failing {len(failing)}; part occurrences {int(sum(occurrences.values())):,} "
                       f"({', '.join(f'{plm} {int(n):,}' for plm, n in sorted(occurrences.items()))}); mass {mass:.3f} kg")
    return out


def configuration_mass(data, config, parts):
    """Part occurrences per site and the mass in kilograms of one configuration, as the bill-of-materials roll-up and
    the massLimit rule count them."""
    children = {}
    for line in config.lines:
        children.setdefault(line["parent"], []).append(line)
    occurrences, mass = {}, Decimal(0)
    for plm, kit in data["bom"].kit.items():
        stack = [(kit, Decimal(1))]
        while stack:
            item, mult = stack.pop()
            for line in children.get(item, []):
                n = mult * line["quantity"]
                child = parts[line["child"]]
                if child["attributes"][4] == "PART":
                    occurrences[plm] = occurrences.get(plm, Decimal(0)) + n
                stored = child["attributes"][2]
                if stored is not None:
                    mass += n * stored * MASS_KG[PLMS[child["plm"]]["mass"]]
                stack.append((line["child"], n))
    return occurrences, mass


MASS_KG = {"kg": Decimal(1), "lb": Decimal("0.45359237")}  # a PLM's mass storage unit


# ---------------------------------------------------------------- SQL

def sql(value):
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, (int, Decimal)):
        return str(value)
    return "'" + value.replace("'", "''") + "'"


def insert(table_spec, rows):
    table, columns = table_spec
    values = ",\n".join("    (" + ", ".join(sql(v) for v in row) + ")" for row in rows)
    return f"INSERT INTO {table} ({columns}) VALUES\n{values};\n"  # nosec B608 - SQL text written to a seed migration file; nothing executes it here


def sides_of(data, plm):
    """Every feature of the PLM in one product, grouped by kind, in interface order; its spare plugs last."""
    out = {kind: [] for kind in KINDS}
    for iface in data["interfaces"]:
        for feature in features_of(iface):
            if feature["plm"] == plm:
                out[feature["kind"]].append(feature)
    out["pairs"] += [plug for plug in data["sparePlugs"] if plug["plm"] == plm]
    for feature in data["optionFeatures"]:
        if feature["plm"] == plm:
            out[feature["kind"]].append(feature)
    return out


def position_columns(feature, spec):
    xyz = [feature["position"][axis] for axis in "xyz"]
    return xyz + ([feature["unit"]] if spec["fixed"] is None else [])


def plug_row(f, spec):
    row = [f["id"], f["part"]] + position_columns(f, spec) + [f["connectorType"], f["pinCount"]]
    return row + [False] if spec["plug"][0] == "conector" else row  # ES conector.obsoleto


def fastener_row(f, spec):
    row = [f["id"], f["part"]] + position_columns(f, spec) + [f["standard"], f["diameter"]]
    if spec["fixed"] is None:
        return row + [f["diameterUnit"], f["count"], f["gripLength"], f["gripUnit"]]
    return row + [f["count"], f["gripLength"]]


def coupling_row(f, spec):
    row = [f["id"], f["part"]] + position_columns(f, spec) + [f["standard"], f["dashSize"], f["rating"]]
    return row + ([f["ratingUnit"]] if spec["fixed"] is None else []) + [f["fluid"]]


def part_rows(data, plm, spec):
    """The part-table rows of one product's PLM: the assemblies first, site kit first, then the parts, each with
    the PLM's bill-of-materials columns (DE parent and quantity, ES lista_materiales document)."""
    bom = data["bom"]
    items = sorted((a for a in data["assemblies"] if a["plm"] == plm), key=lambda a: a["kind"] != "SITE_KIT")
    items += [p for p in data["parts"] if p["plm"] == plm]
    items += [i for i in data["items"] if i["plm"] == plm]
    items += [i for i in data["optionItems"] if i["plm"] == plm]
    rows = []
    for item in items:
        row = [item["id"], item["name"], item["cadFile"]] + item["attributes"]
        if spec.get("tree"):
            row += list(bom.parent_line(item["id"]))
        if spec.get("document"):
            row.append(bom.document(item["id"], item["attributes"][0] or 1,
                                    lambda line: stations.positions(line, data["stations"], plm)))
        row += purchasing.description(item, fail)
        row += functional.gear_columns(item, data["gears"])
        row += data["stations"].part_columns(item, plm)
        row.append(item.get("option"))
        rows.append(row)
    return rows


def bom_rows(data, plm, spec, line_no):
    """The rows of the PLM's line table for one product; `line_no` numbers the UK rows across the products."""
    if spec["bom"][0] == "nomenclature":
        return [[l["parent"], l["child"], l["quantity"], l.get("findNumber")] for l in data["bom"].site_lines(plm)]
    rows = []
    for level, parent, child, quantity in data["bom"].indented(plm):
        line_no += 1
        rows.append([line_no, level, parent, child, quantity])
    return rows


def reference_rows(data, plm, ref_no):
    """The rows of the PLM's external-reference table for one product; `ref_no` numbers them across the products."""
    rows = []
    for ref in data["references"].rows:
        if ref["plm"] == plm:
            ref_no += 1
            rows.append([f"XR-{ref_no:04d}", ref["localPart"], ref["remoteUrn"], ref["quantity"], ref["expectedRevision"],
                         ref.get("note")])
    return rows


# Each site's closure rebuild, run at the end of its seed: the seed empties and refills the bill of materials, so the
# whole closure table is recomputed once (the site's triggers keep it current for every later change).
CLOSURE_REBUILD = {
    "FR": "SELECT nomenclature_fermeture_calculer(ARRAY(SELECT ref_piece FROM piece));",
    "DE": "SELECT teilestruktur_berechnen(ARRAY(SELECT teil_nr FROM bauteil));",
    "UK": "SELECT bom_closure_compute(ARRAY(SELECT comp_id FROM component));",
    "ES": "SELECT cierre_lista_materiales_calcular(ARRAY(SELECT cod_pieza FROM pieza));",
}


def plm_seed(products, plm, spec):
    """PLM seed migration: replaces the whole dataset, one block per product; the feature and line tables are
    emptied first because they reference the part table."""
    blocks = [(data, [p for p in data["parts"] if p["plm"] == plm], sides_of(data, plm)) for data in products]
    total = lambda kind: sum(len(sides[kind]) for _, _, sides in blocks)
    assemblies = sum(1 for data in products for a in data["assemblies"] if a["plm"] == plm)
    lines = sum(len(data["bom"].site_lines(plm)) for data in products)
    references = sum(1 for data in products for ref in data["references"].rows if ref["plm"] == plm)
    placed = sum(len(places.rows(data["bom"], plm)) for data in products)
    tables = [spec[t][0] for t in ("plug", "fastener", "coupling", "placements", "bom") if t in spec]
    tables.append(spec["refs"][0])
    supplier_spec, offer_spec = purchasing.TABLES[plm]
    tables = [offer_spec[0], supplier_spec[0]] + tables
    part_spec = variant_columns((spec["part"][0], f"{spec['part'][1]}, {purchasing.part_columns(plm)}, {GEAR_COLUMNS[plm]}, {stations.COLUMNS[plm]}"), plm, False)
    out = [f"-- {spec['comment']}. {HEADER}",
           f"-- {sum(len(parts) for _, parts, _ in blocks)} parts, {assemblies} assemblies, {total('pairs')} plugs, "
           + f"{total('fasteners')} fasteners, {total('couplings')} hydraulic couplings, {lines} bill-of-materials lines, "
           + f"{placed} line placements "
           + f"and {references} external references "
           + f"over {len(products)} products; the previous dataset is removed.",
           "".join(f"DELETE FROM {table};\n" for table in tables + [spec["part"][0]])]  # nosec B608 - same: migration text, table names from the spec table
    line_no = ref_no = offer_no = 0
    for data, parts, sides in blocks:
        out.append(f"-- {data['product']['name']}: {len(parts)} parts")
        out.append(insert(part_spec, part_rows(data, plm, spec)))
        suppliers = data["suppliers"]
        if suppliers.suppliers.get(plm):
            out.append(insert(supplier_spec[:2], suppliers.suppliers[plm]))
        if suppliers.offers.get(plm):
            offers = [[offer_no + i + 1] + row for i, row in enumerate(suppliers.offers[plm])]
            offer_no += len(offers)
            out.append(insert(offer_spec[:2], offers))
        for kind, table in (("pairs", "plug"), ("fasteners", "fastener"), ("couplings", "coupling")):
            if sides[kind]:
                rows = {"plug": plug_row, "fastener": fastener_row, "coupling": coupling_row}[table]
                out.append(insert(variant_columns(spec[table], plm, True),
                                  [rows(f, spec) + [f.get("port"), f.get("option")] for f in sides[kind]]))
        if "bom" in spec:
            rows = bom_rows(data, plm, spec, line_no)
            line_no += len(rows) if spec["bom"][0] == "bom_line" else 0
            if rows:
                out.append(insert(spec["bom"], rows))
        if "placements" in spec and places.rows(data["bom"], plm):
            placed_rows = [row + data["stations"].row_columns(*row[:3], plm) for row in places.rows(data["bom"], plm)]
            out.append(insert((spec["placements"][0], f"{spec['placements'][1]}, {stations.COLUMNS[plm]}"), placed_rows))
        rows = reference_rows(data, plm, ref_no)
        ref_no += len(rows)
        if rows:
            out.append(insert(spec["refs"], rows))
    out.append(f"-- The bill-of-materials closure of the whole dataset\n{CLOSURE_REBUILD[plm]}")
    return "\n".join(out)


def core_seed(products):
    """atelier-core migration: the products, the product of every part and the export-control tag of every
    part, keyed like the PLM part IRIs (plm lower case)."""
    product_rows = [[d["product"]["key"], d["product"]["name"], d["frame"], mass_limit(d)] for d in products]
    membership = [[d["product"]["key"], p["plm"].lower(), p["id"]] for d in products for p in d["assemblies"] + d["parts"] + d["items"]]
    tags = [[p["plm"].lower(), p["id"], p["classification"]["jurisdiction"], p["classification"]["releasableTo"], p["plm"], TAGGED_AT]
            for d in products for p in d["assemblies"] + d["parts"] + d["items"] + d["optionItems"]]
    return (f"-- Products with their mass limit, the product of every part and the export-control tag of every part, written by the owning PLM "  # nosec B608 - migration text, never executed here
            f"when it published the part. {HEADER}\n"
            f"-- {len(product_rows)} products, {len(tags)} parts, assemblies, software and documents tagged; the previous rows are removed.\n"
            "DELETE FROM product_part;\nDELETE FROM part_tag;\nDELETE FROM product;\n\n"
            + insert(("product", "product_key, name, frame, mass_limit_kg"), product_rows) + "\n"
            + insert(("product_part", "product_key, plm, native_key"), membership) + "\n"
            + insert(("part_tag", "plm, native_key, jurisdiction, releasable_to, tagged_by, tagged_at"), tags))


# ---------------------------------------------------------------- Turtle

PREFIXES = ["@prefix atelier:  <https://example.com/atelier/ontology#> .",
            "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .",
            "@prefix xsd:  <http://www.w3.org/2001/XMLSchema#> ."]


def turtle_string(value):
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def links(products, parts):
    out = [f"# {HEADER}",
           f"# Content of the named graph <{LINK_GRAPH}> (cross-PLM facts: the interfaces and what drives what).",
           "# atelier:declaresFeature lists every feature of an interface; atelier:declaresPlug repeats the plugs.",
           *PREFIXES,
           ""]
    for data in products:
        out += [f"# {data['product']['name']}", ""]
        for iface in data["interfaces"]:
            out += interface_turtle(data["product"]["key"], iface, parts)
        out += functional.links(data, part_iri, interface_iri, product_iri, BASE)
        out += data["stations"].links(product_iri, part_iri, data["bom"].plm_of, turtle_string, iri_safe)
    return "\n".join(out)


def interface_turtle(product, iface, parts):
    """The link-store facts of one interface: its label, product, tolerance, parts, declared features and mates."""
    between = " , ".join(f"<{part_iri(parts[p]['plm'], p)}>" for p in iface["parts"])
    features = features_of(iface)
    plugs = " ,\n        ".join(f"<{feature_iri(f)}>" for f in features if f["kind"] == "pairs")
    declared = " ,\n        ".join(f"<{feature_iri(f)}>" for f in features)
    out = [f"<{interface_iri(product, iface['id'])}> a atelier:Interface ;",
           f"    rdfs:label {turtle_string(iface['label'])} ;",
           f"    atelier:ofProduct <{product_iri(product)}> ;",
           f'    atelier:toleranceMm "{iface["toleranceMm"]}"^^xsd:decimal ;',
           f"    atelier:betweenPart {between} ;"]
    if plugs:
        out.append(f"    atelier:declaresPlug\n        {plugs} ;")
    out += [f"    atelier:declaresFeature\n        {declared} .", ""]
    for _, a, b in pairs_of(iface):
        a_iri, b_iri = feature_iri(a), feature_iri(b)
        out += [f"<{a_iri}> atelier:matesWith <{b_iri}> .",
                f"<{b_iri}> atelier:matesWith <{a_iri}> ."]
    out.append("")
    return out


def file_index(products):
    out = [f"# {HEADER}",
           f"# Content of the named graph <{FILE_INDEX_GRAPH}>: where each part's CAD file is and, for a",
           "# supplier-built part, who builds it for the owning PLM (atelier:builtBy).",
           *PREFIXES,
           ""]
    for data in products:
        out += ["", f"# {data['product']['name']}"]
        for p in data["parts"] + [i for i in data["optionItems"] if i.get("cadFile")]:
            built = f" ;\n    atelier:builtBy {turtle_string(p['supplier'])}" if p.get("supplier") else ""
            if p.get("cadPending"):
                # Released without a file-index entry: the PLM publishes it as part.cad.published during the demo.
                if built:
                    out.append(f"<{part_iri(p['plm'], p['id'])}>{built.lstrip(' ;')} .")
                continue
            out.append(f"<{part_iri(p['plm'], p['id'])}> atelier:cadFile {turtle_string(p['cadFile'])}{built} .")
    out.append("")
    return "\n".join(out)


def labels(products):
    out = [f"# {HEADER}",
           f"# Content of the named graph <{terms.GRAPH}>: the products' glossary as the concept scheme",
           "# atelier:Terms, then each item's native name (skos:prefLabel in its site's language) and English name.",
           "@prefix skos: <http://www.w3.org/2004/02/skos/core#> .",
           ""]
    with_options = [dict(data, items=data["items"] + data["optionItems"]) for data in products]
    return "\n".join(out) + terms.labels(with_options, terms.concepts(fail), lambda item: part_iri(item["plm"], item["id"]), turtle_string)


# ---------------------------------------------------------------- talk-track figures

def figures(data, parts, classes):
    key = data["product"]["key"]
    failing = {iface for iface, _, _ in evaluate(data)}
    profiles = json.loads(POLICY.read_text(encoding="utf-8"))["profiles"]
    kinds = {KINDS[kind]: sum(len(pair) for iface in data["interfaces"] for pair in iface.get(kind, [])) for kind in KINDS}
    kinds["plug"] += sum(len(iface.get("unmatedPlugs", [])) for iface in data["interfaces"]) + len(data["sparePlugs"])
    cross = [iface["id"] for iface in data["interfaces"] if len({parts[p]["plm"] for p in iface["parts"]}) == 2]
    step_bytes = sum((STEP_DIR / key / f"{p['cadPart']}.stp").stat().st_size for p in data["parts"])
    print(f"{key}: {data['product']['name']}")
    print(f"  parts: {len(data['parts'])} ({', '.join(f'{plm} {sum(1 for p in data['parts'] if p['plm'] == plm)}' for plm in PLMS)}); "
          f"supplier-built: {sum(1 for p in data['parts'] if p.get('supplier'))}")
    print(f"  interfaces: {len(data['interfaces'])}, cross-PLM: {len(cross)}")
    print(f"  features: {sum(kinds.values())} ({', '.join(f'{k} {n}' for k, n in kinds.items())}); defects: {len(data['seededDefects'])}"
          + (f" ({', '.join(f'{d['interface']} {d['rule']}' for d in data['seededDefects'])})" if data["seededDefects"] else ""))
    if data["sparePlugs"]:
        print(f"  spare plugs (declared on no interface): {', '.join(f'{p['id']} on {p['part']}' for p in data['sparePlugs'])}")
    for name, profile in profiles.items():
        visible = {p["id"] for p in data["parts"] if p["classification"]["releasableTo"] in profile["releasable"]}
        evaluable = [iface for iface in data["interfaces"] if all(p in visible for p in iface["parts"])]
        failed = sum(1 for iface in evaluable if iface["id"] in failing)
        print(f"    {name:18s} pass {len(evaluable) - failed:3d}  fail {failed:2d}  not evaluable {len(data['interfaces']) - len(evaluable):2d}")
    occurrences = data["bom"].occurrences()
    print(f"  bill of materials: {len(data['assemblies'])} assemblies, {len(data['bom'].lines)} lines; part occurrences "
          f"{int(sum(occurrences.values())):,} ({', '.join(f'{plm} {int(n):,}' for plm, n in occurrences.items())}); "
          f"drawn from placements {places.placed(data['bom']):,}")
    references = data["references"]
    failing = {(local, urn) for local, urn, _ in refs.evaluate(references.rows, parts, {plm: spec["lifecycle"][3] for plm, spec in PLMS.items()})}
    print(f"  external references: {len(references.rows)} written"
          + (f", {len(references.pending)} from items that are not rows" if references.pending else "") + "; "
          f"failing {len(failing)}" + (f" ({', '.join(f'{d['localPart']} {d['rule']}' for d in data.get('seededReferenceDefects', []) if (d['localPart'], d['remoteUrn']) in failing)})" if failing else ""))
    conflicts = sorted(lifecycle.evaluate(data["bom"].lines, references.rows, parts, {plm: spec["lifecycle"] for plm, spec in PLMS.items()}))
    print(f"  lifecycle conflicts: {len(conflicts)}" + (f" ({', '.join(f'{item} on {dependency}' for item, dependency, _ in conflicts)})" if conflicts else ""))
    suppliers = data["suppliers"]
    if suppliers.suppliers:
        offered = [row[0] for rows in suppliers.offers.values() for row in rows]
        single = sorted(p for p in set(offered) if offered.count(p) == 1)
        print(f"  suppliers: {sum(len(r) for r in suppliers.suppliers.values())}, offers {len(offered)}, "
              f"parts offered {len(set(offered))}, single-source {len(single)}")
    if data["items"]:
        print(f"  software and documents (no CAD): {', '.join(f'{i['id']} {i['attributes'][4]}' for i in data['items'])}")
    if mass_limit(data) is not None:
        print(f"  mass limit: {mass_limit(data)} kg")
    if functional.edges(data):
        flows = [e["flow"] for e in functional.edges(data)]
        print(f"  functional edges: {len(flows)} ({', '.join(f'{f} {flows.count(f)}' for f in functional.FLOWS if f in flows)}); "
              f"gears {len(data['gears'])}; mesh defects {len(data.get('seededMeshDefects', []))}")
    for line in data["stations"].figure():
        print(line)
    for line in purchasing.figure(data, classes, fail):
        print(line)
    for line in configuration_figures(data, parts):
        print(line)
    print(f"  STEP {step_bytes:,} bytes for {len(data['parts'])} files")


# ---------------------------------------------------------------- main

def write(out_root, relative, content):
    path = out_root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    content = "".join(f"{COMMENT[path.suffix]}{line}\n" for line in LICENCE) + "\n" + content
    old = path.read_text(encoding="utf-8") if path.exists() else None
    if old != content:
        path.write_text(content, encoding="utf-8", newline="\n")
    print(f"{'unchanged' if old == content else 'wrote    '} {relative}")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=ROOT, help="root directory to write under (default: the repository)")
    args = parser.parse_args()
    products = load()
    parts = check(products)
    for plm, spec in PLMS.items():
        write(args.out, MIGRATIONS / spec["seed"], plm_seed(products, plm, spec))
    write(args.out, CORE, core_seed(products))
    write(args.out, LINKS, links(products, parts))
    write(args.out, FILE_INDEX, file_index(products))
    write(args.out, LABELS, labels(products))
    write(args.out, OPTIONS, options(products, parts))
    classes = purchasing.item_classes()
    for data in products:
        figures(data, parts, classes)


if __name__ == "__main__":
    main()
