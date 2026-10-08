# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""The labels graph of the layer, for data/generate.py: the products' glossary and every item's labels.

The sites keep their parts' names in their own language and hold no other. The layer publishes, in the named
graph https://example.com/atelier/graph/labels, what no site holds:

  - each item's native name as `skos:prefLabel` in its site's language and, where the site does not write
    English, its English name (`nameEn` of the product file) as `skos:altLabel "..."@en`; a UK item's native
    name is its English name;
  - the glossary of the product briefs as the concept scheme `atelier:Terms`, one `skos:Concept` per term
    (minted as https://example.com/atelier/term/<slug>) with one `skos:prefLabel` in each of en, de, fr and es.
    A glossary that writes a term otherwise than another (the difference engine's frame is a Gestell and a bâti,
    the ornithopter's a Rahmen and a châssis) adds `skos:altLabel`s; a term that is a kind of another names it
    with `skos:broader`.
"""

LANGUAGES = ("en", "de", "fr", "es")
SITE_LANGUAGE = {"FR": "fr", "DE": "de", "UK": "en", "ES": "es"}
SCHEME = "https://example.com/atelier/ontology#Terms"
TERM = "https://example.com/atelier/term/"
GRAPH = "https://example.com/atelier/graph/labels"

# The products' glossary: English term, then its German, French and Spanish labels, the further labels a language
# writes the term with, and the term it is a kind of.
GLOSSARY = [
    ("aerial screw", "Luftschraube", "vis aérienne", "tornillo aéreo"),
    ("mast", "Mast", "mât", "mástil"),
    ("spoke", "Speiche", "rayon", "radio"),
    ("crown gear", "Kronenrad", "roue de couronne", "rueda de corona", {}, "gear"),
    ("hand crank", "Handkurbel", "manivelle", "manivela", {}, "crank"),
    ("base platform", "Bodenplattform", "plateforme de base", "plataforma base"),
    ("ornithopter", "Ornithopter", "ornithoptère", "ornitóptero"),
    ("wing", "Flügel", "aile", "ala"),
    ("rib", "Rippe", "nervure", "costilla"),
    ("crank", "Kurbel", "manivelle", "manivela"),
    ("connecting rod", "Pleuelstange", "bielle", "biela"),
    ("pilot harness", "Gurtzeug", "harnais", "arnés"),
    ("frame", "Rahmen", "châssis", "bastidor", {"de": ("Gestell",), "fr": ("bâti",)}),
    ("self-propelled cart", "selbstfahrender Wagen", "chariot automoteur", "carro automotor"),
    ("leaf spring", "Blattfeder", "ressort à lame", "ballesta"),
    # A gear is a toothed wheel: French writes it engrenage and roue dentée.
    ("gear", "Zahnrad", "engrenage", "engranaje", {"fr": ("roue dentée",)}),
    ("cam", "Nocke", "came", "leva"),
    ("axle", "Achse", "essieu", "eje"),
    ("steering lever", "Lenkhebel", "levier de direction", "palanca de dirección"),
    ("pinion", "Ritzel", "pignon", "piñón", {}, "gear"),
    ("dial", "Zifferblatt", "cadran", "esfera"),
    ("pointer", "Zeiger", "aiguille", "aguja"),
    ("casing", "Gehäuse", "boîtier", "carcasa"),
    ("boiler", "Kessel", "chaudière", "caldera"),
    ("cylinder", "Zylinder", "cylindre", "cilindro"),
    ("piston", "Kolben", "piston", "pistón"),
    ("beam", "Balancier", "balancier", "balancín"),
    ("flywheel", "Schwungrad", "volant", "volante"),
    ("governor", "Fliehkraftregler", "régulateur centrifuge", "regulador centrífugo"),
    ("condenser", "Kondensator", "condenseur", "condensador"),
    ("glider", "Gleiter", "planeur", "planeador"),
    ("spar", "Holm", "longeron", "larguero"),
    ("fabric covering", "Stoffbespannung", "entoilage", "tela de recubrimiento"),
    ("tail", "Leitwerk", "empennage", "empenaje"),
    ("bracing wire", "Abspanndraht", "hauban", "tirante"),
    ("structure", "Struktur", "structure", "estructura"),
    ("solar panel", "Solarpanel", "panneau solaire", "panel solar"),
    ("battery", "Batterie", "batterie", "batería"),
    ("antenna", "Antenne", "antenne", "antena"),
    ("on-board computer", "Bordrechner", "calculateur de bord", "ordenador de a bordo"),
    ("thermal coating", "thermische Beschichtung", "revêtement thermique", "recubrimiento térmico"),
    ("flight software", "Flugsoftware", "logiciel de vol", "software de vuelo"),
    ("difference engine", "Differenzmaschine", "machine à différences", "máquina diferencial"),
    ("figure wheel", "Zahlenrad", "roue à chiffres", "rueda de cifras", {}, "gear"),
    ("column", "Säule", "colonne", "columna"),
    ("rack", "Zahnstange", "crémaillère", "cremallera"),
    ("carry", "Übertrag", "retenue", "acarreo"),
    ("printing", "Druck", "impression", "impresión"),
    ("wind turbine", "Windkraftanlage", "éolienne", "aerogenerador"),
    ("blade", "Rotorblatt", "pale", "pala"),
    ("hub", "Nabe", "moyeu", "buje"),
    ("gearbox", "Getriebe", "multiplicateur", "multiplicadora"),
    ("main shaft", "Hauptwelle", "arbre principal", "eje principal"),
    ("tower", "Turm", "mât", "torre"),
    ("generator", "Generator", "génératrice", "generador"),
    ("nacelle", "Gondel", "nacelle", "góndola"),
    ("windlass", "Haspel", "treuil", "molinete", {"de": ("Winde",), "es": ("torno",)}),
    ("pulley", "Seilrolle", "poulie", "polea", {"de": ("Rolle",)}),
    ("stirrup", "Steigbügel", "étrier", "estribo"),
    ("cord", "Schnur", "cordon", "cuerda", {"fr": ("corde",), "es": ("cordón",)}),
    ("lantern pinion", "Laternenrad", "lanterne", "linterna", {"de": ("Triebstockrad",)}, "pinion"),
    ("peg wheel", "Kammrad", "rouet", "rueda de clavijas", {"fr": ("roue à chevilles",)}, "gear"),
    ("return spring", "Rückstellfeder", "ressort de rappel", "muelle de retorno"),
    ("ballast tank", "Ballasttank", "réservoir de lest", "depósito de lastre"),
    ("rib shoe", "Rippenschuh", "sabot de nervure", "zapata de costilla"),
    ("access flap", "Wartungsklappe", "trappe de visite", "trampilla de acceso"),
    ("ballast can", "Ballastkanister", "bidon de lest", "bidón de lastre"),
    ("tyre", "Reifen", "pneumatique", "neumático", {"fr": ("pneu",)}),
    # Kinds of part the products name under another word: an O-ring is a seal, a landing skid a landing gear, a screw a
    # fastener. A German label is looked for inside compounds, so a word that is also a syllable of other parts' names
    # (Scheibe in Bremsscheibe, Bolzen in Planetenbolzen) is not a label: the English names find those kinds.
    ("seal", "Dichtung", "joint d'étanchéité", "junta"),
    ("O-ring", "O-Ring", "joint torique", "junta tórica", {}, "seal"),
    ("gasket", "Flachdichtung", "joint plat", "junta plana", {}, "seal"),
    ("landing gear", "Fahrwerk", "train d'atterrissage", "tren de aterrizaje"),
    ("landing skid", "Landekufe", "patin d'atterrissage", "patín de aterrizaje", {}, "landing gear"),
    ("fastener", "Befestigungselement", "fixation", "elemento de fijación"),
    ("screw", "Schraube", "vis", "tornillo", {}, "fastener"),
    ("bolt", "Sechskantschraube", "boulon", "perno", {}, "fastener"),
    ("nut", "Mutter", "écrou", "tuerca", {}, "fastener"),
    ("washer", "Unterlegscheibe", "rondelle", "arandela", {}, "fastener"),
    ("rivet", "Niet", "rivet", "remache", {}, "fastener"),
]


def slug(term):
    return term.replace(" ", "-")


def concepts(fail):
    """The glossary as concepts: slug, labels by language, further labels by language, broader slug."""
    out = {}
    for entry in GLOSSARY:
        en, de, fr, es = entry[:4]
        alt = entry[4] if len(entry) > 4 else {}
        broader = entry[5] if len(entry) > 5 else None
        if slug(en) in out:
            fail(f"glossary term {en!r} is listed twice")
        out[slug(en)] = {"labels": dict(zip(LANGUAGES, (en, de, fr, es))), "alt": alt, "broader": broader and slug(broader)}
    for key, concept in out.items():
        if concept["broader"] and concept["broader"] not in out:
            fail(f"glossary term {key!r}: broader term {concept['broader']!r} is not in the glossary")
    return out


def english_name(item):
    """The English name the product file gives an item (`nameEn`), None when it gives none."""
    return (item.get("extended") or {}).get("nameEn")


def check_names(items, fail):
    """Every item of a site that does not write English needs its English name."""
    for item in items:
        if item["plm"] != "UK" and not english_name(item):
            fail(f"item {item['id']} ({item['plm']}): no English name (nameEn)")


def labels(products, terms, iri_of, string):
    """Turtle of the labels graph: the concepts of `terms` (see `concepts`), then each product's items."""
    out = ["", "# The products' glossary"]
    for key, concept in terms.items():
        names = " , ".join(f"{string(concept['labels'][lang])}@{lang}" for lang in LANGUAGES)
        alt = [f"{string(label)}@{lang}" for lang in LANGUAGES for label in concept["alt"].get(lang, ())]
        out += [f"<{TERM}{key}> a skos:Concept ;", f"    skos:inScheme <{SCHEME}> ;", f"    skos:prefLabel {names}"]
        if alt:
            out[-1] += " ;"
            out.append(f"    skos:altLabel {' , '.join(alt)}")
        if concept["broader"]:
            out[-1] += " ;"
            out.append(f"    skos:broader <{TERM}{concept['broader']}>")
        out[-1] += " ."
    for data in products:
        out += ["", f"# {data['product']['name']}"]
        for item in data["parts"] + data["items"] + data["assemblies"]:
            lang = SITE_LANGUAGE[item["plm"]]
            line = f"<{iri_of(item)}> skos:prefLabel {string(item['name'])}@{lang}"
            english = english_name(item)
            if lang != "en" and english != item["name"]:
                line += f" ;\n    skos:altLabel {string(english)}@en"
            out.append(line + " .")
    out.append("")
    return "\n".join(out)
