# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Purchased items and their suppliers, for data/generate.py.

A purchased part states what it is in `extended.purchased`, as its site describes it:

    {"class": "o-ring", "standard": "...", "unit": "MilliM" | "IN", "attributes": {...}, "shelfLifeMonths": 12}

`class` is the skos:notation of a concept of the atelier:ItemClasses scheme of ontology/atelier.ttl, and the scheme
is the only list of classes: each identifying attribute of a class names, with atelier:stagingKey, its key in
`attributes`, and its part property says what the value is (a length when the property's range is a
qudt:QuantityValue, a whole number when it is xsd:integer, a text otherwise). `standard` is the standard as the site
writes it (DIN 912, AS568-014, an ISO 3601-1 designation, an IATA container type). Numbers are text in `unit`, by
default the site's: inches in the British PLM, millimetres elsewhere. `shelfLifeMonths` is the shelf life when the
site states one.

`description` writes the block into the site's part-row columns. Which column holds a property, and in which unit,
is read from the site's R2RML mapping (modules/ontop/mappings/<plm>.r2rml.ttl, generated from the entity
annotations): a class whose attributes are properties the sites already map needs no change here. A new property
needs its column in a migration and an annotated entity field at each site, then a regenerated mapping.

`groups` finds the parts of one product that are one item from the same scheme: the identifying attributes, their
tolerances and the schemes their texts resolve in, as the query service does. An identifying attribute may be a part
attribute rather than one of the block's (the cord's atelier:material): its value is the part's own.

`extended.suppliers` and `extended.supplierParts` hold each site's supplier list and its supplier offers in the
site's own column names; `Suppliers` checks them and writes them into the site's tables. Costs stay in the files:
the tables carry none.
"""

import re
import unicodedata
from decimal import Decimal, InvalidOperation
from functools import cache
from pathlib import Path

import ttl

ROOT = Path(__file__).resolve().parent.parent
ONTOLOGY = ROOT / "ontology" / "atelier.ttl"
MAPPINGS = ROOT / "modules" / "ontop" / "mappings"
ONT = "https://example.com/atelier/ontology#"
SKOS = "http://www.w3.org/2004/02/skos/core#"
QUDT = "http://qudt.org/schema/qudt/"
UNIT = "http://qudt.org/vocab/unit/"
RR = "http://www.w3.org/ns/r2rml#"
RDFS = "http://www.w3.org/2000/01/rdf-schema#"
XSD = "http://www.w3.org/2001/XMLSchema#"

LENGTH, COUNT, TEXT = "length", "count", "text"
# Properties a purchased part states besides its class's attributes.
BLOCK_PROPERTIES = ("standard", "itemClass", "shelfLifeMonths")
# Part attributes an item class may compare, the property's local name to its key in the part's `extended`: the part
# row's own column, which the query service reads as the same property.
PART_PROPERTIES = {"material": "material"}
MM_PER_UNIT = {"MilliM": Decimal("1"), "IN": Decimal("25.4")}
UNIT_WORDS = {"mm": "MilliM", "in": "IN"}
TEXT_LENGTH = 120  # VARCHAR(120) text columns of the purchased item
STANDARD_LENGTH = 64  # VARCHAR(64) standard column
THICKNESS = re.compile(r"^(.*?)[\s,]*(\d+(?:[.,]\d+)?)\s*(mm|in)\s*$")
# Decimal places of each site's purchased-item length columns (NUMERIC(8, 3), NUMERIC(8, 4) at UK).
SCALE = {"FR": 3, "DE": 3, "UK": 4, "ES": 3}
DEFAULT_UNIT = {"FR": "MilliM", "DE": "MilliM", "UK": "IN", "ES": "MilliM"}

# Per site: the supplier table and the offer table, each as (table, insert columns, staging keys in column order).
# An offer's id numbers the site's offers across the products; its part is the site's part key.
TABLES = {
    "FR": (("fournisseur", "code_fournisseur, raison_sociale, ville", ("code_fournisseur", "raison_sociale", "ville")),
           ("article_fournisseur", "id, ref_piece, code_fournisseur, reference_fournisseur, delai_jours, prefere",
            ("reference", "code_fournisseur", "reference_fournisseur", "delai_jours", "prefere"))),
    "DE": (("lieferant", "id, name, ort", ("lieferant_nr", "name", "ort")),
           ("lieferantenteil", "id, teil_nr, lieferant_id, lieferanten_teilenummer, lieferzeit_tage, bevorzugt",
            ("teil_nr", "lieferant_nr", "lieferanten_teilenummer", "lieferzeit_tage", "bevorzugt"))),
    "UK": (("supplier", "supplier_id, name, town", ("supplier_id", "name", "town")),
           ("supplier_part", "id, comp_id, supplier_id, supplier_part_no, lead_time_days, preferred",
            ("part_no", "supplier_id", "supplier_part_no", "lead_time_days", "preferred"))),
    "ES": (("proveedor", "cod_proveedor, nombre, ciudad", ("cod_proveedor", "nombre", "ciudad")),
           ("pieza_proveedor", "id, cod_pieza, cod_proveedor, referencia_proveedor, plazo_dias, preferido",
            ("referencia", "cod_proveedor", "referencia_proveedor", "plazo_dias", "preferido"))),
}


def number(text):
    return Decimal(str(text).replace(",", "."))


def key(text):
    """A text as the schemes compare it: case, spaces and hyphens ignored."""
    return re.sub(r"[\s\-\u2010-\u2015]+", "", unicodedata.normalize("NFC", text)).casefold()


def legend_key(text):
    return re.sub(r"\s+", "", unicodedata.normalize("NFC", text)).casefold()


class Graph:
    """Single-valued and multi-valued reads over a list of triples."""

    def __init__(self, triples):
        self.triples = triples
        self.objects = {}
        for s, p, o in triples:
            self.objects.setdefault((s, p), []).append(o)

    def subjects(self, p, o):
        return sorted({s for (s, q), objects in self.objects.items() if q == p and o in objects})

    def all(self, s, p):
        return self.objects.get((s, p), [])

    def one(self, s, p):
        objects = self.all(s, p)
        return objects[0] if objects else None


class ItemClasses(Graph):
    """The item classes of the ontology: per class notation, its identifying attributes in order, each as (property,
    tolerance in mm or None, scheme or None), the scheme its standards designate sizes in, and its staging keys, each
    to (property local name, LENGTH, COUNT or TEXT)."""

    def __init__(self, triples):
        super().__init__(triples)
        self.classes, self.staging = {}, {}
        for concept in self.subjects(SKOS + "inScheme", ONT + "ItemClasses"):
            notation = self.one(concept, SKOS + "notation").text
            attributes = sorted(self.all(concept, ONT + "identifiedBy"),
                                key=lambda a: int(self.one(a, ONT + "attributeOrder").text))
            spec, staging = [], {}
            for a in attributes:
                prop, tolerance = self.one(a, ONT + "onAttribute"), self.one(a, ONT + "matchToleranceMm")
                spec.append((prop, None if tolerance is None else Decimal(tolerance.text), self.one(a, ONT + "resolvedIn")))
                staging_key = self.one(a, ONT + "stagingKey")
                if staging_key is not None:
                    staging[staging_key.text] = (prop[len(ONT):], self.kind(prop))
            self.classes[notation] = (concept, spec, self.one(concept, ONT + "standardScheme"))
            self.staging[notation] = staging
        self.labels = {}
        for s, p, o in triples:
            if p in (SKOS + "prefLabel", SKOS + "altLabel"):
                for scheme in self.all(s, SKOS + "inScheme"):
                    self.labels[(scheme, key(o.text))] = s

    def kind(self, prop):
        range_ = self.one(prop, RDFS + "range")
        return LENGTH if range_ == QUDT + "QuantityValue" else COUNT if range_ == XSD + "integer" else TEXT

    def properties(self):
        """Every property a purchased part may state: the block's, then each class's attributes."""
        out = list(BLOCK_PROPERTIES)
        for staging in self.staging.values():
            out += [prop for prop, _ in staging.values() if prop not in out]
        return out

    def resolve(self, scheme, text):
        return self.labels.get((scheme, key(text)))

    def label(self, concept):
        labels = self.all(concept, SKOS + "prefLabel")
        return next((l.text for l in labels if l.lang in (None, "en")), labels[0].text if labels else concept)

    def size_mm(self, concept, prop):
        """The length a size concept states for a property, in mm, or None."""
        node = self.one(concept, ONT + prop)
        if node is None:
            return None
        return number(self.one(node, QUDT + "numericValue").text) * MM_PER_UNIT[self.one(node, QUDT + "unit")[len(UNIT):]]


@cache
def item_classes():
    return ItemClasses(ttl.parse(ONTOLOGY.read_text(encoding="utf-8")))


class SiteColumns:
    """Where one site keeps the purchased-item properties on its part rows, read from its R2RML mapping: per property
    its column, and per length the unit it is stored in (a QUDT local name) or the column holding the unit per row."""

    def __init__(self, plm, classes):
        g = Graph(ttl.parse((MAPPINGS / f"{plm.lower()}.r2rml.ttl").read_text(encoding="utf-8")))
        by_template = {}
        part_map = None
        for m in {s for (s, p) in g.objects if p == RR + "subjectMap"}:
            subject = g.one(m, RR + "subjectMap")
            by_template[g.one(subject, RR + "template").text] = m
            if g.one(subject, RR + "class") == ONT + "Part":
                part_map = m
        wanted = {ONT + p: p for p in classes.properties()}
        self.columns, self.units, self.unit_column = {}, {}, None
        for pom in g.all(part_map, RR + "predicateObjectMap"):
            prop = wanted.get(g.one(pom, RR + "predicate"))
            if prop is None:
                continue
            obj = g.one(pom, RR + "objectMap")
            column = g.one(obj, RR + "column")
            if column is not None:
                self.columns[prop] = column.text
                continue
            value_map = by_template[g.one(obj, RR + "template").text]
            for vpom in g.all(value_map, RR + "predicateObjectMap"):
                predicate = g.one(vpom, RR + "predicate")
                if predicate == QUDT + "numericValue":
                    self.columns[prop] = g.one(g.one(vpom, RR + "objectMap"), RR + "column").text
                elif predicate == QUDT + "unit":
                    fixed = g.one(vpom, RR + "object")
                    if fixed is not None:
                        self.units[prop] = fixed[len(UNIT):]
                    else:
                        template = g.one(g.one(vpom, RR + "objectMap"), RR + "template").text
                        self.unit_column = re.search(r"\{(\w+)\}", template).group(1)
        self.order = list(self.columns) + ([None] if self.unit_column else [])

    def names(self):
        return ", ".join(self.columns[p] if p else self.unit_column for p in self.order)


@cache
def site_columns(plm):
    return SiteColumns(plm, item_classes())


def part_columns(plm):
    """The site's part-row columns of the purchased-item description, in the order `description` writes them."""
    return site_columns(plm).names()


def stated(part, fail, classes=None):
    """(class, standard, unit, {property: value}, shelf life) of the part's purchased block, checked; None without one.
    Lengths are Decimal in `unit`, whole numbers int, texts str."""
    classes = classes or item_classes()
    extended = part.get("extended") or {}
    block = extended.get("purchased")
    if block is None:
        return None
    pid, plm = part["id"], part["plm"]
    if extended.get("partType") != "PURCHASED":
        fail(f"part {pid}: a purchased block needs partType PURCHASED")
    item_class = block.get("class")
    if item_class not in classes.staging:
        fail(f"part {pid}: purchased class {item_class!r} is not one of {', '.join(classes.staging)}")
    unknown = set(block) - {"class", "standard", "unit", "attributes", "shelfLifeMonths"}
    attributes = block.get("attributes") or {}
    names = classes.staging[item_class]
    unknown |= {f"attributes.{k}" for k in attributes if k not in names}
    if unknown:
        fail(f"part {pid}: purchased block has unknown keys {', '.join(sorted(unknown))}")
    unit = block.get("unit", DEFAULT_UNIT[plm])
    values = {}
    for name, value in attributes.items():
        prop, kind = names[name]
        if kind == COUNT:
            if not str(value).isdigit() or int(value) <= 0:
                fail(f"part {pid}: {name} {value!r} is not a positive whole number")
            value = int(value)
        elif kind == LENGTH:
            try:
                value = number(value)
            except InvalidOperation:
                fail(f"part {pid}: {name} {value!r} is not a number")
            if value <= 0 or -value.as_tuple().exponent > SCALE[plm]:
                fail(f"part {pid}: {name} {value} must be positive with at most {SCALE[plm]} decimals")
        elif not isinstance(value, str) or not value.strip() or len(value) > TEXT_LENGTH:
            fail(f"part {pid}: {name} must be a text of at most {TEXT_LENGTH} characters")
        values[prop] = value
    standard = block.get("standard")
    if standard is not None and (not isinstance(standard, str) or len(standard) > STANDARD_LENGTH):
        fail(f"part {pid}: standard {standard!r} must be a text of at most {STANDARD_LENGTH} characters")
    shelf = block.get("shelfLifeMonths")
    if shelf is not None and (not isinstance(shelf, int) or isinstance(shelf, bool) or shelf <= 0):
        fail(f"part {pid}: shelfLifeMonths must be a positive number of months")
    return item_class, standard, unit, values, shelf


def description(part, fail, classes=None, columns=None):
    """The part-row values of the description in the site's columns; NULLs for a part that is not purchased. A
    property the site does not map is refused, as is a unit its fixed-unit columns cannot hold."""
    plm = part["plm"]
    columns = columns or site_columns(plm)
    block = stated(part, fail, classes)
    if block is None:
        return [None] * len(columns.order)
    item_class, standard, unit, values, shelf = block
    for prop in values:
        if prop not in columns.columns:
            fail(f"part {part['id']}: {plm} maps no column to atelier:{prop}")
        fixed = columns.units.get(prop)
        if fixed is not None and fixed != unit:
            fail(f"part {part['id']}: {plm} stores atelier:{prop} in {fixed}, not {unit}")
    values = dict(values, standard=standard, itemClass=item_class, shelfLifeMonths=shelf)
    lengths = any(isinstance(v, Decimal) for v in values.values())
    return [values.get(p) if p else (unit if lengths else None) for p in columns.order]



def identity(part, classes, fail):
    """(class, [value per identifying attribute]) of a purchased part, or None when it cannot be compared: a length is
    in mm, a resolved text its concept (with the length written inside it, in mm), a whole number or any other text its
    legend key; a missing value is None and agrees only with another missing one. A text that resolves to no concept,
    or a standard of a size scheme that names no size, leaves the part out."""
    block = stated(part, fail, classes)
    if block is None:
        return None
    item_class, standard, unit, values, _ = block
    _, spec, size_scheme = classes.classes[item_class]
    size = None
    if size_scheme is not None and standard is not None:
        size = classes.resolve(size_scheme, standard)
        if size is None:
            return None
    out = []
    for prop, tolerance, scheme in spec:
        name = prop[len(ONT):]
        value = standard if name == "standard" else values.get(name)
        if value is None and name in PART_PROPERTIES:
            value = (part.get("extended") or {}).get(PART_PROPERTIES[name])
        if value is None and size is not None:
            out.append(classes.size_mm(size, name))
        elif value is None:
            out.append(None)
        elif scheme is not None:
            text, thickness = value, None
            m = THICKNESS.match(value) if tolerance is not None else None
            if m:
                text, thickness = m.group(1), number(m.group(2)) * MM_PER_UNIT[UNIT_WORDS[m.group(3)]]
            concept = classes.resolve(scheme, text)
            if concept is None:
                return None
            out.append((concept, thickness))
        elif classes.kind(prop) == LENGTH:
            out.append(value * MM_PER_UNIT[unit])
        else:
            out.append(legend_key(str(value)))
    return item_class, out


def agree(spec, a, b):
    for (_, tolerance, _), x, y in zip(spec, a, b):
        if x is None or y is None:
            if x is not y:
                return False
        elif isinstance(x, tuple):
            if x[0] != y[0] or (x[1] is None) != (y[1] is None) or (x[1] is not None and abs(x[1] - y[1]) > tolerance):
                return False
        elif isinstance(x, Decimal):
            if abs(x - y) > tolerance:
                return False
        elif x != y:
            return False
    return True


def groups(data, classes, fail):
    """The items of one product bought under several part numbers, as (class, [parts]) in part order; a part joins the
    first group of its class whose first member it agrees with. Also returns the items bought under one number."""
    found = {}
    for part in data["parts"]:
        ident = identity(part, classes, fail)
        if ident is None:
            continue
        item_class, values = ident
        spec = classes.classes[item_class][1]
        bucket = found.setdefault(item_class, [])
        group = next((g for g in bucket if agree(spec, g[0][1], values)), None)
        if group is None:
            bucket.append([(part, values)])
        else:
            group.append((part, values))
    return [(c, [p for p, _ in g]) for c, bucket in found.items() for g in bucket]


def figure(data, classes, fail):
    """The talk-track lines of a product's purchased items: per class, items and part numbers, then each item bought
    under several part numbers with its sites, shelf life and stocking line."""
    found = groups(data, classes, fail)
    if not found:
        return []
    lines = []
    for item_class in dict.fromkeys(c for c, _ in found):
        items = [g for c, g in found if c == item_class]
        numbers = sum(len(g) for g in items)
        lines.append(f"  purchased {item_class}: {numbers} part number{'s' if numbers > 1 else ''}, {len(items)} "
                     f"item{'s' if len(items) > 1 else ''}, {sum(1 for g in items if len(g) > 1)} under several numbers")
        for group in (g for g in items if len(g) > 1):
            sites = sorted({p["plm"] for p in group})
            shelves = [stated(p, fail, classes)[4] for p in group if stated(p, fail, classes)[4] is not None]
            lines.append(f"    {item_class} {', '.join(p['id'] for p in group)}: {len(group)} part numbers in "
                         f"{len(sites)} sites ({', '.join(sites)})"
                         + (f", shelf life {min(shelves)} months" if shelves else "")
                         + "; one stock line once confirmed")
    return lines



class Suppliers:
    """One product's suppliers and supplier offers per site, checked against its parts."""

    def __init__(self, data, fail):
        key = data["product"]["key"]
        extended = data.get("extended") or {}
        self.suppliers, self.offers = {}, {}
        local = {(p["plm"], (p.get("extended") or {}).get("localId", p["id"])): p["id"] for p in data["parts"]}
        local.update({(p["plm"], p["id"]): p["id"] for p in data["parts"]})
        for plm, block in (extended.get("suppliers") or {}).items():
            spec = TABLES[plm][0]
            if block["table"] != spec[0]:
                fail(f"{key}: {plm} suppliers are in table {block['table']}, not {spec[0]}")
            self.suppliers[plm] = [[row[k] for k in spec[2]] for row in block["rows"]]
        for plm, block in (extended.get("supplierParts") or {}).items():
            spec = TABLES[plm][1]
            if block["table"] != spec[0]:
                fail(f"{key}: {plm} supplier offers are in table {block['table']}, not {spec[0]}")
            known = {row[0] for row in self.suppliers.get(plm, [])}
            rows = []
            for row in block["rows"]:
                values = [row[k] for k in spec[2]]
                part = local.get((plm, values[0]))
                if part is None:
                    fail(f"{key}: {plm} offer {values[2]} names {values[0]}, which is no {plm} part of the product")
                if values[1] not in known:
                    fail(f"{key}: {plm} offer {values[2]} names supplier {values[1]}, which {plm} does not list")
                if not isinstance(values[3], int) or values[3] < 0 or not isinstance(values[4], bool):
                    fail(f"{key}: {plm} offer {values[2]} needs a lead time in days and a preferred flag")
                rows.append([part] + values[1:])
            self.offers[plm] = rows


def check(products, fail):
    """Attaches each product's Suppliers; supplier keys are unique per site across the products."""
    seen = {}
    for data in products:
        data["suppliers"] = Suppliers(data, fail)
        for plm, rows in data["suppliers"].suppliers.items():
            for row in rows:
                if (plm, row[0]) in seen:
                    fail(f"{plm} supplier {row[0]} is listed by {seen[(plm, row[0])]} and {data['product']['key']}")
                seen[(plm, row[0])] = data["product"]["key"]
