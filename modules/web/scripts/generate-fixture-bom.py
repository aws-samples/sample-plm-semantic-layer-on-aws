# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Writes the computed trees of modules/web/src/dev/fixture-bom.json from data/products/<key>.json: the ornithopter,
aerial screw and wind turbine bills of materials as GET /query/bom rolls them up for programme-cleared, with one French
item of the ornithopter and of the wind turbine drawn hidden. The rover's tree is the query service's own answer over
the fixture stack and is kept as it is. Run after data/generate.py:

    python3 modules/web/scripts/generate-fixture-bom.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
FIXTURE = ROOT / "modules/web/src/dev/fixture-bom.json"
COMPUTED = ("ornithopter", "aerial-screw", "wind-turbine")
KG = {'kg': 1.0, 'g': 0.001, 'lb': 0.45359237}
VISIBLE = {'ALL', 'EU', 'FR', 'DE', 'UK', 'ES'}  # programme-cleared
SITES = ['fr', 'de', 'uk', 'es']
# Assemblies drawn hidden, as a profile without that site's clearance sees them.
HIDE = {'wind-turbine': {'FR3778'}, 'ornithopter': {'FR-ORN-NACI-001'}}
r4 = lambda v: round(v, 4)

def build(key):
    d = json.loads((ROOT / 'data/products' / f'{key}.json').read_text(encoding='utf-8'))
    e = d['extended']
    asm = {a['id']: a for a in e['assemblies']}
    parts = {p['id']: p for p in d['parts']}
    kids = {}
    for l in e['bomLines']:
        kids.setdefault(l['parent'], []).append(l)
    hide = HIDE.get(key, set())
    roll = {s: {'plm': s, 'occurrences': 0.0, 'massKg': 0.0, 'withoutMass': 0, 'hiddenOccurrences': 0.0} for s in SITES}

    def own_mass(p):
        x = p.get('extended') or {}
        m = x.get('mass')
        if m is None:
            return None
        return float(str(m).replace(',', '.')) * KG[(x.get('massUnit') or 'kg').lower()]

    def node(id_, qty, occ):
        a = asm.get(id_)
        p = parts.get(id_)
        rec = a or p
        plm = rec['plm'].lower()
        hidden = id_ in hide or (p is not None and p['classification']['releasableTo'] not in VISIBLE)
        if hidden:
            roll[plm]['hiddenOccurrences'] += occ
            return {'redacted': True, 'plm': plm, 'quantity': float(qty), 'occurrences': float(occ)}, None
        x = (p or {}).get('extended') or {}
        out = {'id': id_, 'plm': plm, 'name': rec['name'],
               'partType': 'ASSEMBLY' if a else (x.get('type') or 'PART')}
        rev = rec.get('revision') if a else x.get('revision')
        life = rec.get('lifecycle') if a else x.get('lifecycle')
        if rev is not None:
            out['revision'] = str(rev)
        if life:
            out['lifecycle'] = life
            out['lifecycleState'] = STATE.get(life)
        out['quantity'] = float(qty)
        out['occurrences'] = float(occ)
        children, mass, known = [], 0.0, False
        if p is not None:
            m = own_mass(p)
            r = roll[plm]
            r['occurrences'] += occ
            if m is None:
                r['withoutMass'] += 1
            else:
                r['massKg'] += m * occ
                mass, known = m, True
        for l in kids.get(id_, []):
            c, cm = node(l['child'], l['quantity'], occ * l['quantity'])
            children.append(c)
            if cm is not None:
                mass += cm * l['quantity']
                known = True
        if known:
            out['unitMassKg'] = r4(mass)
            out['extendedMassKg'] = r4(mass * occ)
        out['children'] = children
        return out, (mass if known else None)

    kits = sorted((a for a in e['assemblies'] if a['kind'] == 'SITE_KIT'), key=lambda a: SITES.index(a['plm'].lower()))
    children, total_mass = [], 0.0
    for k in kits:
        c, m = node(k['id'], 1, 1)
        children.append(c)
        total_mass += m or 0
    root = {'id': key, 'name': d['product']['name'], 'partType': 'PRODUCT', 'quantity': 1.0, 'occurrences': 1.0,
            'unitMassKg': r4(total_mass), 'extendedMassKg': r4(total_mass), 'children': children}
    sites = [dict(roll[s], massKg=r4(roll[s]['massKg'])) for s in SITES if any(k['plm'].lower() == s for k in kits)]
    total = {'plm': None, 'occurrences': sum(s['occurrences'] for s in sites), 'massKg': r4(sum(s['massKg'] for s in sites)),
             'withoutMass': sum(s['withoutMass'] for s in sites), 'hiddenOccurrences': sum(s['hiddenOccurrences'] for s in sites)}
    return {'product': key, 'root': root, 'sites': sites, 'total': total}

STATE = {w: s for s, ws in {
    'WORKING': ['In Arbeit', 'En cours', 'Borrador', 'Draft'], 'RELEASED': ['Freigegeben', 'Publié', 'Liberado', 'Released'],
    'BLOCKED': ['Gesperrt', 'Bloqué', 'Bloqueado', 'Frozen'], 'SUPERSEDED': ['Ersetzt', 'Remplacé', 'Sustituido', 'Superseded']}.items() for w in ws}

out = json.loads(FIXTURE.read_text(encoding='utf-8'))
out.update({k: build(k) for k in COMPUTED})
FIXTURE.write_text(json.dumps(out, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
for k in COMPUTED:
    print(k, out[k]['total'], [(s['plm'], s['occurrences'], s['massKg']) for s in out[k]['sites']])
