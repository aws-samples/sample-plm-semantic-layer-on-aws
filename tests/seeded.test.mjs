// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// node --test tests/seeded.test.mjs: the expectations of the smoke test and the fix cycle, on the product files and on
// fixture products with fictional ids that carry the cases the files may not.
import { strict as assert } from 'node:assert';
import { readdirSync, readFileSync } from 'node:fs';
import { test } from 'node:test';
import { execFileSync } from 'node:child_process';
import { cadMissingAfter, cadMissingParts, configuration, demoPendingPart, groupMatches, massLimitFailing, productFindings, seededItems, unitsOf } from './seeded.mjs';


const dir = new URL('../data/products/', import.meta.url);
const datasets = readdirSync(dir).filter((f) => f.endsWith('.json')).map((f) => JSON.parse(readFileSync(new URL(f, dir))));


/** A fixture product: two sites, a kit each, parts with masses and quantities. */
const fixture = ({ key = 'fixture', limit, frMass = '1.0', dePending = false } = {}) => ({
  product: { key },
  parts: [
    ...Array.from({ length: 5 }, (_, i) => ({ id: `FX-FR-${i}`, plm: 'FR', cadFile: `cad/${key}/fr-${i}.stp`, extended: { mass: frMass } })),
    ...Array.from({ length: 5 }, (_, i) => ({ id: `FX-DE-${i}`, plm: 'DE', cadFile: `cad/${key}/de-${i}.stp`, extended: { mass: '1.0' }, ...(i === 0 && dePending ? { cadPending: true } : {}) })),
    { id: 'FX-UK-DOC', plm: 'UK', cadFile: null, extended: { type: 'DOCUMENT' } },
  ],
  extended: {
    ...(limit === undefined ? {} : { massLimitKg: limit }),
    assemblies: [{ id: 'FX-FR-KIT', plm: 'FR', kind: 'SITE_KIT' }, { id: 'FX-DE-KIT', plm: 'DE', kind: 'SITE_KIT' }],
    bomLines: [
      ...Array.from({ length: 5 }, (_, i) => ({ parent: 'FX-FR-KIT', child: `FX-FR-${i}`, quantity: 2 })),
      ...Array.from({ length: 5 }, (_, i) => ({ parent: 'FX-DE-KIT', child: `FX-DE-${i}`, quantity: 1 })),
    ],
  },
});

test('a product whose occurrences weigh more than its mass limit carries massLimit; one within it does not', () => {
  // 5 FR parts of 1 kg twice and 5 DE parts of 1 kg once: 15 kg.
  assert.deepEqual(productFindings(fixture({ limit: '14.9' })), ['massLimit']);
  assert.deepEqual(productFindings(fixture({ limit: '15.0' })), []);
  assert.deepEqual(productFindings(fixture()), []);
});

test('a site whose median mass is more than 100 times the others carries massScale', () => {
  assert.deepEqual(productFindings(fixture({ frMass: '150' })), ['massScale']);
  assert.deepEqual(productFindings(fixture({ frMass: '99' })), []);
  assert.deepEqual(productFindings(fixture({ frMass: '150', limit: '10' })), ['massLimit', 'massScale']);
});

test('every released part with geometry and no file-index entry is a cadMissing part, not only the first', () => {
  const two = [fixture({ dePending: true }), { ...fixture({ key: 'second', dePending: true }), parts: fixture({ key: 'second', dePending: true }).parts.map((p) => ({ ...p, id: p.id.replace('FX', 'SX') })) }];
  assert.deepEqual(cadMissingParts(two), ['FX-DE-0', 'SX-DE-0']);
  assert.deepEqual(cadMissingParts(datasets), datasets.flatMap((d) => d.parts.filter((p) => p.cadPending).map((p) => p.id)).sort());
  assert.deepEqual(cadMissingAfter(two, 'FX-DE-0'), ['SX-DE-0'], 'publishing one pending part clears its finding only');
});

test('the demo publishes the pending part of its own product, whichever product file comes first', () => {
  const first = { ...fixture({ key: 'first', dePending: true }), parts: fixture({ key: 'first', dePending: true }).parts.map((p) => ({ ...p, id: p.id.replace('FX', 'AX') })) };
  const scripted = fixture({ key: 'scripted', dePending: true });
  const files = [first, scripted];
  assert.equal(demoPendingPart(files, 'scripted')?.id, 'FX-DE-0');
  assert.equal(demoPendingPart(files, 'first')?.id, 'AX-DE-0');
  assert.deepEqual(cadMissingAfter(files, demoPendingPart(files, 'scripted').id), ['AX-DE-0'], 'the other product keeps its finding');
});

test('a member reports the units of every identifying value, whatever its class', () => {
  const placard = { values: [{ attribute: 'itemWidth', stored: '100', unit: 'MilliM' }, { attribute: 'itemHeight', stored: '50', unit: 'MilliM' }, { attribute: 'legend', text: 'NO STEP' }] };
  const tyre = { values: [{ attribute: 'outerDiameter', stored: '46.00', unit: 'IN' }, { attribute: 'rimDiameter', stored: '20', unit: 'IN' }] };
  const screw = { values: [{ attribute: 'nominalDiameter', stored: '3', unit: 'MilliM' }, { attribute: 'nominalLength', stored: '10', unit: 'MilliM' }] };
  assert.deepEqual(unitsOf(placard), ['MilliM']);
  assert.deepEqual(unitsOf(tyre), ['IN']);
  assert.deepEqual(unitsOf(screw), ['MilliM']);
  assert.deepEqual(unitsOf({ values: [{ attribute: 'legend', text: 'EXIT' }] }), []);
});

test('an equivalence group is the file\'s item, in two units only where its members write metric and inch', () => {
  const member = (id, unit, attributes) => ({ id, values: attributes.map((attribute) => ({ attribute, stored: '1', ...(unit ? { unit } : {}) })) });
  const placard = { parts: [{ units: 'metric' }, { units: 'metric' }, { units: 'inch' }] };
  const placardGroup = { members: [member('FX-PLQ-1', 'MilliM', ['itemWidth', 'itemHeight']), member('FX-PLQ-2', 'MilliM', ['itemWidth', 'itemHeight']), member('FX-PLQ-3', 'IN', ['itemWidth', 'itemHeight'])] };
  assert.deepEqual(groupMatches(placard, ['FX-PLQ-1', 'FX-PLQ-2', 'FX-PLQ-3'], placardGroup), { ok: true, label: '3 part numbers in metric and inch units' });
  const allMetric = { members: placardGroup.members.map((m) => ({ ...m, values: m.values.map((v) => ({ ...v, unit: 'MilliM' })) })) };
  assert.equal(groupMatches(placard, ['FX-PLQ-1', 'FX-PLQ-2', 'FX-PLQ-3'], allMetric).ok, false, 'metric and inch members reported in one unit');
  const ring = { parts: [{ units: 'dash size' }, { units: 'ISO 3601-1 code' }, { units: 'inch' }] };
  const ringGroup = { members: [member('FX-OR-1', null, ['innerDiameter']), member('FX-OR-2', 'MilliM', ['innerDiameter', 'crossSection']), member('FX-OR-3', 'IN', ['innerDiameter', 'crossSection'])] };
  assert.deepEqual(groupMatches(ring, ['FX-OR-1', 'FX-OR-2', 'FX-OR-3'], ringGroup), { ok: true, label: '3 part numbers' });
  assert.equal(groupMatches(ring, ['FX-OR-1', 'FX-OR-2'], ringGroup).ok, false, 'a group with another part number');
});

test('the seeded items are the base items and every option\'s own parts and assemblies', () => {
  const base = fixture({ key: 'optioned' });
  const data = { ...base, extended: { ...base.extended, nonGeometricItems: [{ localId: 'UK/9001', plm: 'UK', type: 'DOCUMENT' }],
    variants: { drive: { default: 'chain', options: {
      chain: { default: true, parts: ['FX-DE-0'] },
      belt: { parts: [{ id: 'FX-UK-BELT', plm: 'UK', classification: { jurisdiction: 'NONE', releasableTo: 'UK' } }],
        assemblies: [{ id: 'FX-ES-BELT-KIT', plm: 'ES', kind: 'ASSEMBLY', name: 'Belt kit' }] } } } } } };
  const items = seededItems(data);
  assert.deepEqual(items.map((i) => i.id).filter((id) => /BELT|UK-9001|KIT/.test(id)).sort(), ['FX-DE-KIT', 'FX-ES-BELT-KIT', 'FX-FR-KIT', 'FX-UK-BELT', 'UK-9001']);
  assert.equal(items.length, base.parts.length + 2 + 1 + 2, 'the option\'s id reference names a base part, not a new item');
  assert.equal(items.find((i) => i.id === 'FX-ES-BELT-KIT').classification.releasableTo, 'ALL');
  assert.equal(items.find((i) => i.id === 'FX-UK-BELT').classification.releasableTo, 'UK');
});

test('the option items are those data/variants.py reads from every product file', () => {
  const python = `
import json, glob, sys
sys.path.insert(0, "data")
import variants
def fail(message): raise SystemExit(message)
out = {}
for f in sorted(glob.glob("data/products/*.json")):
    d = json.load(open(f, encoding="utf-8"))
    out[d["product"]["key"]] = sorted(i for g in variants.groups(d, fail) for o in g.options
                                      for kind in ("parts", "assemblies") for i in o.object_ids(kind))
print(json.dumps(out))`;
  const generator = JSON.parse(execFileSync('python3', ['-c', python], { cwd: new URL('..', import.meta.url) }).toString());
  const base = (d) => new Set([...d.parts, ...(d.extended?.assemblies ?? []), ...(d.extended?.nonGeometricItems ?? [])].map((i) => i.id ?? i.localId.replaceAll('/', '-')));
  const ours = Object.fromEntries(datasets.map((d) => [d.product.key, seededItems(d).map((i) => i.id).filter((id) => !base(d).has(id)).sort()]));
  assert.deepEqual(ours, generator);
  assert.ok(Object.values(generator).flat().length > 0, 'the product files carry option items');
});

test('every option\'s configuration is the one data/generate.py builds: its items and each item\'s drawn occurrences', () => {
  const python = `
import json, sys
sys.path.insert(0, "data")
import generate as gen
products = gen.load()
gen.check(products)
out = {}
for d in products:
    for g in d["groups"]:
        for o in g.options:
            config = d["configurations"][g.key][o.key]
            children = {}
            for line in config.lines:
                children.setdefault(line["parent"], []).append(line)
            counts, stack = {}, [(kit, 1) for kit in d["bom"].kit.values()]
            while stack:
                item, n = stack.pop()
                for line in children.get(item, []):
                    m = n * len(line.get("placements") or [None])
                    counts[line["child"]] = counts.get(line["child"], 0) + m
                    stack.append((line["child"], m))
            out[o.key] = {"product": d["product"]["key"], "group": g.key, "items": sorted(config.items), "occurrences": counts}
print(json.dumps(out))`;
  const generator = JSON.parse(execFileSync('python3', ['-c', python], { cwd: new URL('..', import.meta.url), maxBuffer: 1 << 26 }).toString());
  const ours = Object.fromEntries(datasets.flatMap((d) => Object.values(d.extended?.variants ?? {}).flatMap((g) => Object.keys(g.options))
    .map((key) => {
      const c = configuration(d, key);
      return [key, { product: d.product.key, group: c.group, items: [...c.items].sort(), occurrences: Object.fromEntries(c.occurrences) }];
    })));
  assert.deepEqual(ours, generator);
  assert.ok(Object.values(generator).some((c) => Object.values(c.occurrences).some((n) => n > 1)), 'an option places an item more than once');
});

test('the products failing their mass limit for a profile are those it sees every item of: two hidden products leave the cleared profile one', () => {
  const policy = JSON.parse(readFileSync(new URL('../ontology/policy.json', import.meta.url))).profiles;
  const licensed = (data, id) => ({ ...data, parts: data.parts.map((p) => (p.id === id ? { ...p, classification: { jurisdiction: 'US', releasableTo: 'LICENSED' } } : p)) });
  // Three products of 15 kg against a 14.9 kg limit, one within its limit; in two of the heavy ones an item, a part or a
  // document, is released to the officer alone.
  const files = [licensed(fixture({ key: 'first', limit: '14.9' }), 'FX-DE-0'), licensed(fixture({ key: 'second', limit: '14.9' }), 'FX-UK-DOC'),
    fixture({ key: 'third', limit: '14.9' }), fixture({ key: 'light', limit: '15.0' })];
  assert.deepEqual(massLimitFailing(files, policy['export-officer']), ['first', 'second', 'third']);
  assert.deepEqual(massLimitFailing(files, policy['programme-cleared']), ['third'], 'a product with one hidden item is not evaluable');
  assert.equal(massLimitFailing(files, policy['export-officer']).length - massLimitFailing(files, policy['programme-cleared']).length, 2);
  assert.deepEqual(massLimitFailing(datasets, policy['export-officer']), datasets.filter((d) => productFindings(d).includes('massLimit')).map((d) => d.product.key).sort(),
    'the officer sees every item, so the officer\'s products are every product over its limit');
});
