#!/usr/bin/env python3
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Every atelier: term used by the R2RML mappings and the SHACL shapes must be
declared in ontology/atelier.ttl. Exits 1 with the undeclared terms and the file
that uses each; exits 0 with a count otherwise.

Usage: check-ontology-terms.py [ontology.ttl] [file.ttl ...]
Defaults: ontology/atelier.ttl against modules/ontop/mappings/*.r2rml.ttl and
ontology/shapes.ttl.
"""
import glob
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
Atelier = "https://example.com/atelier/ontology#"

DECLARED = re.compile(r"^(?:atelier:(\w+)|<" + re.escape(Atelier) + r"(\w+)>)\s+(?:a|rdf:type)\s", re.M)
USED = re.compile(r"(?<![\w#/])atelier:(\w+)|<" + re.escape(Atelier) + r"(\w+)>")


def declared_terms(ontology: Path) -> set[str]:
    return {a or b for a, b in DECLARED.findall(ontology.read_text())}


def used_terms(file: Path) -> set[str]:
    text = re.sub(r"^\s*(?:@prefix|PREFIX)\b.*$", "", file.read_text(), flags=re.M)
    return {a or b for a, b in USED.findall(text)}


def main(argv: list[str]) -> int:
    ontology = Path(argv[1]) if len(argv) > 1 else ROOT / "ontology" / "atelier.ttl"
    files = [Path(p) for p in argv[2:]] or [
        *map(Path, sorted(glob.glob(str(ROOT / "modules" / "ontop" / "mappings" / "*.r2rml.ttl")))),
        ROOT / "ontology" / "shapes.ttl",
    ]
    declared = declared_terms(ontology)
    if not declared:
        print(f"FAIL: no atelier: declarations found in {ontology}")
        return 1
    missing: dict[str, list[str]] = {}
    checked = 0
    for file in files:
        for term in used_terms(file):
            checked += 1
            if term not in declared:
                missing.setdefault(term, []).append(str(file.relative_to(ROOT) if file.is_relative_to(ROOT) else file))
    if missing:
        for term, where in sorted(missing.items()):
            print(f"FAIL: atelier:{term} is used in {', '.join(where)} but not declared in {ontology.name}")
        return 1
    print(f"ontology terms OK: {checked} uses across {len(files)} files, {len(declared)} declared terms")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
