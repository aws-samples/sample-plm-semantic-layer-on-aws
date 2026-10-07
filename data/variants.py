# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Product variants and option effectivity, for data/generate.py.

`extended.variants` holds the variant groups of a product: each group is one installation slot and the options it can
take, `{name, selects, default, options}`. The base product is the default configuration: the default option lists the
base items it is made of (`parts`, `assemblies`, `interfaces`, ids only) and, under `ports`, the port of each base
feature that a host part offers the slot (`{interface: {feature id: port}}`). A non-default option states what it
changes against the base:

  parts, assemblies, interfaces   objects in the base item shapes for the items only this option adds (an interface's
                                  features carry `port`); ids name base items the option uses unchanged
  replaces                        `{parts|assemblies|interfaces: {old: new}, removes: {parts, assemblies, interfaces}}`;
                                  an assembly may be replaced by a single part, its bill of materials going with it
  portsNotModelled                ports the host has for this option that the file does not model
  bomLines, functionalEdges, externalRefs   the option's own, in the product-level shapes; a line's `placements` go
                                  into the options graph as the sites' occurrences, translations in the site's unit
  seededDefects                   the interface rules' expected failures on the option's interfaces
  source, applicability           where the option comes from, and the model or build it applies to, in words

The items an option takes out of the base (replaced and removed) are exactly the items the default option lists, so a
configuration is the base without what the default option is made of, with the option's own items. Removing or
replacing an assembly removes its bill-of-materials lines; an item no line of the configuration reaches any more leaves
it too. A host part is a part present in both configurations; the variant diff pairs the features of the two by
(host part, port).

`check` refuses: a group without exactly one default option or whose `default` names another; an option key used twice
in the dataset (it is a code in the sites' databases); an option that replaces or removes an id the base does not hold,
or that the default option does not list; an option object whose id the base holds; an interface of a configuration
joining an item the configuration does not hold; a port named twice on one part within a configuration, or a feature
given two ports; and an option whose interfaces fail other rules than its `seededDefects` say.
"""

from decimal import Decimal

import placements as places

GROUP_KEYS = {"name", "selects", "default", "options"}
OPTION_KEYS = {"default", "source", "applicability", "parts", "assemblies", "interfaces", "ports", "portsNotModelled",
               "replaces", "bomLines", "functionalEdges", "externalRefs", "seededDefects"}
ITEM_KINDS = ("parts", "assemblies", "interfaces")
PORT_LENGTH = 64  # VARCHAR(64) port columns in every native schema


class Option:
    """One option of a group, checked against the base product."""

    def __init__(self, group, key, spec, fail):
        where = f"{group.product}: variant {group.key} option {key}"
        unknown = set(spec) - OPTION_KEYS
        if unknown:
            fail(f"{where}: unknown keys {sorted(unknown)}")
        self.group, self.key, self.where = group, key, where
        self.default = spec.get("default") is True
        self.source, self.applicability = spec.get("source"), spec.get("applicability")
        self.ids = {kind: [x for x in spec.get(kind, []) if isinstance(x, str)] for kind in ITEM_KINDS}
        self.objects = {kind: [x for x in spec.get(kind, []) if isinstance(x, dict)] for kind in ITEM_KINDS}
        self.ports = spec.get("ports", {})
        self.not_modelled = list(spec.get("portsNotModelled", []))
        replaces = spec.get("replaces", {})
        if set(replaces) - {"parts", "assemblies", "interfaces", "removes"} or set(replaces.get("removes", {})) - set(ITEM_KINDS):
            fail(f"{where}: replaces holds parts, assemblies, interfaces and removes {{parts, assemblies, interfaces}} only")
        self.replaces = {kind: dict(replaces.get(kind, {})) for kind in ITEM_KINDS}
        self.removes = {kind: list(replaces.get("removes", {}).get(kind, [])) for kind in ITEM_KINDS}
        self.lines = [dict(line, quantity=Decimal(str(line["quantity"]))) for line in spec.get("bomLines", [])]
        self.edges = list(spec.get("functionalEdges", []))
        self.refs = list(spec.get("externalRefs", []))
        self.seeded = list(spec.get("seededDefects", []))
        if self.default and (any(self.objects.values()) or replaces or self.lines or self.edges or self.refs or self.seeded):
            fail(f"{where}: the default option is the base product; it lists base ids and ports only")
        if not self.default and self.ports:
            fail(f"{where}: ports of base features belong to the default option; an option interface's features carry their port")

    def taken_out(self):
        """{kind: set of base ids} the option replaces or removes."""
        return {kind: set(self.replaces[kind]) | set(self.removes[kind]) for kind in ITEM_KINDS}

    def object_ids(self, kind):
        return [o["id"] for o in self.objects[kind]]


class Group:
    """One variant group of a product: its options, the default first."""

    def __init__(self, product, key, spec, fail):
        self.product, self.key = product, key
        if set(spec) - GROUP_KEYS or not {"name", "default", "options"} <= set(spec):
            fail(f"{product}: variant {key} needs name, default and options, and no key outside {sorted(GROUP_KEYS)}")
        self.name, self.selects = spec["name"], spec.get("selects")
        options = [Option(self, k, v, fail) for k, v in spec["options"].items()]
        defaults = [o.key for o in options if o.default]
        if len(defaults) != 1 or defaults[0] != spec["default"]:
            fail(f"{product}: variant {key} needs exactly one default option, the one `default` names ({spec['default']}); "
                 f"options marked default: {defaults}")
        self.default = next(o for o in options if o.default)
        self.options = [self.default] + [o for o in options if not o.default]


def groups(data, fail):
    """The product's variant groups in file order."""
    product = data["product"]["key"]
    spec = (data.get("extended") or {}).get("variants") or {}
    if not isinstance(spec, dict):
        fail(f"{product}: extended.variants is a map of variant groups")
    return [Group(product, key, value, fail) for key, value in spec.items()]


class Base:
    """The ids, lines and features of the base product a configuration starts from."""

    def __init__(self, data):
        self.ids = {"parts": {p["id"] for p in data["parts"]},
                    "assemblies": {a["id"] for a in data["assemblies"]} | {i["id"] for i in data["items"]},
                    "interfaces": {i["id"] for i in data["interfaces"]}}
        self.interfaces = {i["id"]: i for i in data["interfaces"]}
        self.lines = data["bom"].lines
        self.kits = set(data["bom"].kit.values())


class Configuration:
    """The items of the product under one option: the base, without what the default option is made of when the option is
    another one, with what the option adds; lines whose parent or child left go too, and so does an item no remaining
    line reaches."""

    def __init__(self, base, option, fail):
        out = {kind: set() if option.default else set(option.group.default.ids[kind]) for kind in ITEM_KINDS}
        self.option = option
        self.items = (base.ids["parts"] | base.ids["assemblies"]) - out["parts"] - out["assemblies"]
        self.items |= set(option.object_ids("parts")) | set(option.object_ids("assemblies"))
        own_lines = option.lines if not option.default else []
        lines = [l for l in base.lines if l["parent"] in self.items and l["child"] in self.items] + own_lines
        reached, frontier = set(base.kits), list(base.kits)
        children = {}
        for line in lines:
            children.setdefault(line["parent"], []).append(line["child"])
        while frontier:
            for child in children.get(frontier.pop(), []):
                if child not in reached:
                    reached.add(child)
                    frontier.append(child)
        self.unreached = sorted(self.items - reached)
        self.items &= reached
        self.lines = [l for l in lines if l["parent"] in self.items and l["child"] in self.items]
        self.interfaces = [base.interfaces[i] for i in sorted(base.ids["interfaces"] - out["interfaces"])]
        self.interfaces += option.objects["interfaces"] if not option.default else []
        for iface in self.interfaces:
            missing = [p for p in iface["parts"] if p not in self.items]
            if missing:
                fail(f"{option.where}: interface {iface['id']} joins {missing}, which the configuration does not hold; "
                     "the option removes or replaces it")


def check(data, groups_, keys, fail):
    """Checks the product's groups against its base; `keys` collects the option keys of the dataset. Returns
    {group key: {option key: Configuration}}."""
    base = Base(data)
    out = {}
    for group in groups_:
        for option in group.options:
            if option.key in keys:
                fail(f"{option.where}: option key {option.key} is used twice in the dataset; it is a code in the sites' databases")
            keys.add(option.key)
            check_ids(base, group, option, fail)
        out[group.key] = {o.key: Configuration(base, o, fail) for o in group.options}
    return out


def check_ids(base, group, option, fail):
    listed = {kind: set(group.default.ids[kind]) for kind in ITEM_KINDS}
    for kind in ITEM_KINDS:
        held = base.ids["parts"] | base.ids["assemblies"] if kind != "interfaces" else base.ids["interfaces"]
        for item in option.ids[kind]:
            if item not in held:
                fail(f"{option.where}: {kind} lists {item}, which the base does not hold")
        for obj in option.objects[kind]:
            if obj["id"] in base.ids["parts"] | base.ids["assemblies"] | base.ids["interfaces"]:
                fail(f"{option.where}: object {obj['id']} has the id of a base item; an option adds new items")
    if option.default:
        return
    new = {kind: set(option.object_ids(kind)) for kind in ITEM_KINDS}
    new["assemblies"] |= new["parts"]
    for kind in ITEM_KINDS:
        held = base.ids["parts"] | base.ids["assemblies"] if kind != "interfaces" else base.ids["interfaces"]
        for item in option.taken_out()[kind]:
            if item not in held:
                fail(f"{option.where}: replaces or removes {item}, which the base does not hold")
            if item not in listed[kind]:
                fail(f"{option.where}: replaces or removes {item}, which the default option {group.default.key} does not list")
        for old, replacement in option.replaces[kind].items():
            if replacement not in new[kind]:
                fail(f"{option.where}: replaces {old} by {replacement}, which is no {kind[:-1]} the option adds")
        missing = listed[kind] - option.taken_out()[kind]
        if missing:
            fail(f"{option.where}: takes out {sorted(option.taken_out()[kind])} of the {kind}, the default option "
                 f"{group.default.key} is made of {sorted(listed[kind])}: an option takes out what the default is made of")
    added = set(option.object_ids("parts")) | set(option.object_ids("assemblies"))
    lined = {}
    for line in option.lines:
        if line["child"] not in added | base.ids["parts"] | base.ids["assemblies"]:
            fail(f"{option.where}: line {line['parent']} -> {line['child']} names an item neither the base nor the option holds")
        lined[line["child"]] = lined.get(line["child"], 0) + 1
    for item in sorted(added):
        if lined.get(item) != 1:
            fail(f"{option.where}: {item} needs one line of the option's bill of materials, found {lined.get(item, 0)}")


def ports(products_groups, fail):
    """The port of every feature that has one, {(plm, kind, id): port}: the default options' `ports` of base features and
    the `port` an interface feature carries. A feature has one port at most."""
    out = {}

    def put(feature, port, where):
        if not isinstance(port, str) or not port or len(port) > PORT_LENGTH:
            fail(f"{where}: port {port!r} of {feature['id']} must be a name of at most {PORT_LENGTH} characters")
        key = (feature["plm"], feature["kind"], feature["id"])
        if out.setdefault(key, port) != port:
            fail(f"{where}: feature {feature['id']} has two ports, {out[key]!r} and {port!r}")
        feature["port"] = port

    for data, groups_ in products_groups:
        interfaces = {i["id"]: i for i in data["interfaces"]}
        for iface in data["interfaces"] + [i for g in groups_ for o in g.options for i in o.objects["interfaces"]]:
            for feature in features(iface):
                if "port" in feature:
                    put(feature, feature["port"], f"{data['product']['key']} {iface['id']}")
        for group in groups_:
            for iface_id, by_feature in group.default.ports.items():
                if iface_id not in interfaces:
                    fail(f"{group.default.where}: ports name {iface_id}, which is no base interface")
                on = {f["id"]: f for f in features(interfaces[iface_id])}
                for feature_id, port in by_feature.items():
                    if feature_id not in on:
                        fail(f"{group.default.where}: ports name {feature_id}, which is no feature of {iface_id}")
                    put(on[feature_id], port, group.default.where)
    return out


def check_ports(configurations, port_of, fail):
    """Within each configuration a port names at most one feature of a part."""
    for by_option in configurations.values():
        for config in by_option.values():
            held = {}
            for iface in config.interfaces:
                for feature in features(iface):
                    port = port_of.get((feature["plm"], feature["kind"], feature["id"]))
                    if port is None:
                        continue
                    seen = held.setdefault((feature["part"], port), feature["id"])
                    if seen != feature["id"]:
                        fail(f"{config.option.where}: port {port!r} of {feature['part']} is named twice, by {seen} and {feature['id']}")


def features(iface):
    return [f for kind in ("pairs", "fasteners", "couplings") for pair in iface.get(kind, []) for f in pair] + iface.get("unmatedPlugs", [])


# ---------------------------------------------------------------- Turtle of the options graph

def turtle(data, groups_, iris, string):
    """The Turtle of one product's variant groups for data/options.ttl: each group and its options, the items each option
    applies under, the option interfaces with their features and mates, the option's lines and functional edges."""
    key = data["product"]["key"]
    out = [f"# {data['product']['name']}", ""]
    for group in groups_:
        variant = iris.variant(key, group.key)
        lines = [f"<{variant}> a atelier:Variant ;", f"    rdfs:label {string(group.name)} ;"]
        if group.selects:
            lines.append(f"    rdfs:comment {string(group.selects)} ;")
        lines += [f"    atelier:ofProduct <{iris.product(key)}> ;",
                  f"    atelier:defaultOption <{iris.option(group.default.key)}> .", ""]
        out += lines
        for option in group.options:
            out += option_turtle(data, option, iris, string)
    return out


def occurrence_turtle(line, line_iri, plm, option_iri):
    """The occurrences of an option's line with placements, as the sites map theirs: translations in the site's unit."""
    unit = "unit:IN" if plm == "UK" else "unit:MilliM"
    out = []
    for n, p in enumerate(line.get("placements") or [], start=1):
        occurrence = f"{line_iri}/occurrence/{n}"
        out += [f"<{occurrence}> a atelier:Occurrence ;", f"    atelier:ofLine <{line_iri}> ;", f'    atelier:index "{n}"^^xsd:integer ;']
        out += [f"    atelier:translation{a} <{occurrence}/translation/{a.lower()}> ;" for a in "XYZ"]
        out += [f'    atelier:rotation{a} "{places.decimal(v)}"^^xsd:decimal ;' for a, v in zip("XYZ", p[3:])]
        out += [f"    atelier:appliesUnderOption {option_iri} ."]
        out += [f'<{occurrence}/translation/{a.lower()}> qudt:numericValue "{places.decimal(v)}"^^xsd:decimal ; qudt:unit {unit} .'
                for a, v in zip("XYZ", p[:3])]
    return out


def option_turtle(data, option, iris, string):
    key = data["product"]["key"]
    iri = f"<{iris.option(option.key)}>"
    lines = [f"{iri} a atelier:Option ;", f"    rdfs:label {string(option.key)} ;"]
    if option.source:
        lines.append(f"    dcterms:source {string(option.source)} ;")
    if option.applicability:
        lines.append(f"    atelier:applicability {string(option.applicability)} ;")
    for port in option.not_modelled:
        lines.append(f"    atelier:portNotModelled {string(port)} ;")
    lines.append(f"    atelier:optionOf <{iris.variant(key, option.group.key)}> .")
    out = lines + [""]
    if option.default:
        items = [iris.item(i) for kind in ("parts", "assemblies") for i in option.ids[kind]]
        out += [f"<{item}> atelier:appliesUnderOption {iri} ." for item in items]
        out += [f"<{iris.interface(key, i)}> atelier:appliesUnderOption {iri} ." for i in option.ids["interfaces"]]
        return out + [""]
    for kind in ("parts", "assemblies"):
        out += [f"<{iris.item(o['id'])}> atelier:appliesUnderOption {iri} ." for o in option.objects[kind]]
    for iface in option.objects["interfaces"]:
        out += iris.interface_turtle(key, iface) + [f"<{iris.interface(key, iface['id'])}> atelier:appliesUnderOption {iri} .", ""]
    for line in option.lines:
        line_iri = f"<{iris.option(option.key)}/line/{iris.safe(line['parent'])}/{iris.safe(line['child'])}>"
        out += [f"{line_iri} a atelier:BomLine ;",
                f"    atelier:parent <{iris.item(line['parent'])}> ;",
                f"    atelier:child <{iris.item(line['child'])}> ;",
                f'    atelier:quantity "{line["quantity"]}"^^xsd:decimal ;',
                f"    atelier:appliesUnderOption {iri} ."]
        out += occurrence_turtle(line, line_iri[1:-1], iris.parts[line["parent"]]["plm"].upper(), iri)
    for n, edge in enumerate(option.edges, 1):
        out += [f"<{iris.option(option.key)}/drive/D{n:02d}> a atelier:Drive ;",
                f"    atelier:ofProduct <{iris.product(key)}> ;",
                f"    atelier:driver <{iris.item(edge['from'])}> ;",
                f"    atelier:driven <{iris.item(edge['to'])}> ;"]
        if "via" in edge:
            out.append(f"    atelier:viaInterface <{iris.interface(key, edge['via'])}> ;")
        out += [f'    atelier:flow "{edge["flow"]}" ;', f"    atelier:appliesUnderOption {iri} ."]
    return out + [""]
