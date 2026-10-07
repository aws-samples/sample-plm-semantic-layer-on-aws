# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""The non-geometric items of the product files, for data/generate.py.

`extended.nonGeometricItems` lists what a site releases and revises like a part but that has no geometry:
flight software, a certificate, a setting manual. Each becomes a row of its site's part table with part
type SOFTWARE or DOCUMENT, no CAD file and no file-index entry, and a line under its site kit, so it is an
item of the product's bill of materials. The staging entries name items by the site's local id (UK/3511);
the part rows key the UK items as UK-3511, like the UK parts of the files. A software version is part of
the item's name; the revision stays in the site's revision form.
"""

TYPES = ("SOFTWARE", "DOCUMENT")
# Released to every profile unless the product file classifies the item, as for assemblies.
OPEN = {"jurisdiction": "NONE", "releasableTo": "ALL"}


def native_id(local_id):
    """The part-row key of a local id: the UK site writes UK/3511 where its part rows hold UK-3511."""
    return local_id.replace("/", "-")


def non_geometric(data, fail):
    """The product's non-geometric items as part rows: id, PLM, name, parent (the site kit), classification, extended."""
    out = []
    for entry in (data.get("extended") or {}).get("nonGeometricItems", []):
        if entry.get("type") not in TYPES:
            fail(f"non-geometric item {entry.get('localId')}: type {entry.get('type')!r} is not one of {', '.join(TYPES)}")
        name = f"{entry['name']} {entry['version']}" if entry.get("version") else entry["name"]
        name_en = f"{entry['nameEn']} {entry['version']}" if entry.get("version") and entry.get("nameEn") else entry.get("nameEn")
        out.append({"id": native_id(entry["localId"]), "plm": entry["plm"], "name": name, "cadFile": None,
                    "parent": native_id(entry["siteKit"]), "classification": entry.get("classification", OPEN),
                    "extended": {"revision": entry.get("revision"), "lifecycle": entry.get("lifecycle"), "type": entry["type"],
                                 "nameEn": name_en}})
    return out
