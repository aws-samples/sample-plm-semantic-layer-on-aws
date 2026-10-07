# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Writes one STEP AP242 file per part of each machine into stp/<product key>/<cad key>.stp.

Products are the modules listed in PRODUCTS; each declares KEY, NAME, FRAME and PARTS (CAD key, owner PLM, label,
builder). Every shape is validated (isValid on the shape and on each body) before it is written. The bucket keys of
the files are cad/<product key>/<cad key>.stp, the cadFile values of data/products/<product key>.json. Beside them,
stp/<product key>/bounds.json holds per CAD key the exact box of the part in the product frame, in mm, and the sha256 of
its STEP file: data/generate.py computes the station span of every part from it and refuses a box whose hash is not
that of the STEP file it names, so a span never outlives its geometry.

Run with a CadQuery venv (see README.md):  .venv/bin/python generate.py
"""
import hashlib
import json
import time
from pathlib import Path

import aerial_screw
import antikythera
import cart
import cubesat
import difference_engine
import glider
import ornithopter
import rover
import steam_engine
import wind_turbine
from geometry import bbox, extent, validate, write_step

HERE = Path(__file__).parent
STP = HERE / "stp"
PRODUCTS = [ornithopter, aerial_screw, cart, antikythera, steam_engine, glider, cubesat, difference_engine, wind_turbine, rover]


def build(product):
    out = STP / product.KEY
    out.mkdir(parents=True, exist_ok=True)
    started = time.time()
    total, per_owner, bounds = 0, {}, {}
    for key, owner, label, make in product.PARTS:
        shape = make()
        validate(shape, key)
        path = out / f"{key}.stp"
        write_step(shape, key, path)
        size = path.stat().st_size
        low, high = extent(shape)
        bounds[key] = {"min": low, "max": high, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
        total += size
        per_owner[owner] = per_owner.get(owner, 0) + 1
        print(f"  {key:30s} {owner} {size:>7d} B  {len(shape.Faces()):>3d} faces  bbox {bbox(shape)}")
    lines = [f"  {json.dumps(k)}: {json.dumps(v)}" for k, v in sorted(bounds.items())]
    (out / "bounds.json").write_text("{\n" + ",\n".join(lines) + "\n}\n", encoding="utf-8")
    owners = ", ".join(f"{o} {n}" for o, n in sorted(per_owner.items()))
    print(f"{product.KEY}: {len(product.PARTS)} parts ({owners}), {total:,} bytes of STEP in {time.time() - started:.1f} s")


def main():
    for product in PRODUCTS:
        print(f"{product.NAME}; frame: {product.FRAME}")
        build(product)


if __name__ == "__main__":
    main()
