// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The release, pass, reset, fail cycle of every rule. The layer finds a defect and never edits a site's data: the
// site that owns the record releases a correction in its own PLM, the layer sees it on its next read and the rule
// passes; the officer's reset replays Atelier's change log through each PLM and the defect fails again. For one
// seeded defect of each rule the cycle reads the answers, builds the correction with the screen's own proposals
// (modules/web/src/check/demo/proposals.ts), releases it as the owning site's engineer, asserts the rule passes,
// resets as the officer and asserts it fails again; it ends with every product's failures equal to its seeded
// defects. Run by modules/query-service/fixtures/fixes.sh against the fixture stack and by tests/smoke.mjs against
// the deployment.
//
// `base` serves /query/..., /<plm>/... and /core/...; `headers` go on every request (the deployment's origin
// secret); `check(ok, label, detail)` reports. `loader` is how a release reaches Atelier's change log: 'stand-in'
// posts the answer's rows to /core/changes as the links loader does from the PLM's part.value.corrected event (a
// stack without EventBridge), 'live' waits up to 60 s for GET /query/demo/changes to show them.
import { readdirSync, readFileSync } from 'node:fs';
import {
  danglingProposal, leadTimeProposal, lifecycleProposal, massProposal, massScaleProposal, matchProposal, requestOf,
  staleProposal, unitProposal, valid,
} from '../modules/web/src/check/demo/proposals.ts';
import { productFindings } from './seeded.mjs';

const OFFICER = 'export-officer';
const productsDir = new URL('../data/products/', import.meta.url);
const datasets = readdirSync(productsDir).filter((f) => f.endsWith('.json')).sort()
  .map((f) => JSON.parse(readFileSync(new URL(f, productsDir))));
const datasetOf = Object.fromEntries(datasets.map((d) => [d.product.key, d]));
const seeded = (product, rule) => datasetOf[product].seededDefects.find((d) => d.rule === rule);
const seededRef = (product, part) => datasetOf[product].seededReferenceDefects.find((d) => d.localPart === part);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const show = (v) => (typeof v === 'string' ? JSON.stringify(v) : String(v));
const same = (a, b) => (typeof b === 'number' ? Number(a) === b : a === b);

/** The answers and services under `base`: `send` retries a rolling task replacement, `get` throws on a refusal. */
export function client(base, headers = {}) {
  const send = async (method, path, body, profile = OFFICER, extra = {}) => {
    let res;
    // A rolling task replacement answers 502/503/504 for a short while; anything else is the answer.
    for (let attempt = 0; attempt < 6; attempt++) {
      res = await fetch(base + path, {
        method,
        headers: { ...headers, 'x-atelier-profile': profile, ...extra, ...(body ? { 'content-type': 'application/json' } : {}) },
        body: body ? JSON.stringify(body) : undefined,
        signal: AbortSignal.timeout(300_000),
      });
      if (![502, 503, 504].includes(res.status)) break;
      await sleep(10_000);
    }
    const text = await res.text();
    let parsed;
    try { parsed = JSON.parse(text); } catch { parsed = text; }
    return { status: res.status, ok: res.ok, body: parsed };
  };
  const get = async (path, profile = OFFICER) => {
    const res = await send('GET', path, undefined, profile);
    if (!res.ok) throw new Error(`GET ${path}: HTTP ${res.status} ${JSON.stringify(res.body)}`);
    return res.body;
  };
  return { send, get };
}

/**
 * One seeded defect of each rule: its name, the product, where the preview reports it (`target`: an interface, a part or
 * the product, and the rule), `state()` (whether it fails now) and `plan()` (the screen's proposal for it).
 */
export function seededCases(get) {
  const catalogues = {};
  const catalogueOf = async (plm) => (catalogues[plm] ??= await get(`/${plm.toLowerCase()}/catalogue`));
  const visible = (list) => (list ?? []).filter((x) => !x.redacted);
  const partsOf = async (product) => visible((await get(`/query/parts?product=${product}`)).parts);
  const productOf = async (product) => (await get('/query/products')).products.find((p) => p.key === product);

  // A seeded interface defect: its interface fails on the rule.
  const interfaceCase = (product, rule, plan) => {
    const defect = seeded(product, rule);
    const read = async () => (await get(`/query/interfaces/${defect.interface}?product=${product}`)).interface;
    return {
      name: `${rule} (${product} ${defect.interface})`,
      product,
      target: { kind: 'interface', id: defect.interface, rule },
      state: async () => {
        const i = await read();
        return { fails: i.status === 'fail' && i.violations.some((v) => v.rule === rule), detail: `${i.status} ${i.violations.map((v) => v.rule).join(',')}` };
      },
      plan: async () => plan(await read(), defect),
    };
  };
  const featuresOf = (i, defect) => defect.features.map((id) => i.features.find((f) => f.id === id));
  const matchCase = (product, rule) => interfaceCase(product, rule, async (i, defect) => {
    const [a, b] = featuresOf(i, defect);
    // Either side may take the other's values; the first that the site's vocabulary accepts is released.
    const tried = [];
    for (const [feature, mate] of [[a, b], [b, a]]) {
      const planned = matchProposal(await catalogueOf(feature.plm), rule, feature, mate, defect.interface);
      if (planned.ok && planned.proposal.cells.every((c) => valid(c, c.value))) return planned;
      tried.push(planned.ok ? `${feature.id}: a value its site does not take` : `${feature.id}: ${planned.reason}`);
    }
    return { ok: false, reason: tried.join('; ') };
  });
  // A seeded reference defect: the reference, found once by its part and URN and then by its site and row key (the
  // correction changes the URN; a row key is unique within its site only), carries the rule's status.
  const referenceCase = (product, part, plan) => {
    const defect = seededRef(product, part);
    let row;
    const read = async () => {
      const refs = (await get(`/query/references?product=${product}`)).references;
      row ??= refs.find((r) => r.part === part && r.remoteUrn === defect.remoteUrn);
      return refs.find((r) => r.plm === row?.plm && r.id === row?.id);
    };
    return {
      name: `${defect.rule} (${product} ${part} -> ${defect.remoteUrn})`,
      product,
      target: { kind: 'part', id: part, rule: defect.rule },
      state: async () => {
        const ref = await read();
        return { fails: ref?.status === defect.rule, detail: `${ref?.status} ${ref?.remoteUrn}` };
      },
      plan: async () => plan(await read()),
    };
  };
  // A part finding: the part carries the rule, on the record `value` names when it names one.
  const findingOn = async (product, part, rule) => (await partsOf(product)).find((p) => p.id === part)?.findings?.filter((f) => f.rule === rule) ?? [];
  const productCase = (product, rule, plan) => ({
    name: `${rule} (${product})`,
    product,
    target: { kind: 'product', id: product, rule },
    state: async () => {
      const p = await productOf(product);
      return { fails: (p.findings ?? []).some((f) => f.rule === rule), detail: JSON.stringify(p.massLimit ?? p.findings?.map((f) => f.rule)) };
    },
    plan,
  });

  const cases = [
    interfaceCase('ornithopter', 'unit', async (i, defect) => {
      const violation = i.violations.find((v) => v.rule === 'unit');
      const feature = i.features.find((f) => f.id === defect.features[0]);
      return unitProposal(await catalogueOf(feature.plm), feature, violation.detail.quantity, defect.interface);
    }),
    matchCase('ornithopter', 'connector'),
    matchCase('ornithopter', 'fastener'),
    matchCase('ornithopter', 'hydraulic'),
    referenceCase('cubesat', 'FR3503', async (ref) => danglingProposal(await catalogueOf(ref.plm), ref, await partsOf('cubesat'))),
    referenceCase('cubesat', 'UK-3507', async (ref) => staleProposal(await catalogueOf(ref.plm), ref)),
    (() => {
      const { item, dependency } = datasetOf.ornithopter.seededLifecycleDefects.find((d) => d.item === 'CONJ-6000');
      const conflict = async () => (await findingOn('ornithopter', item, 'lifecycleConflict')).find((f) => f.value?.id === dependency);
      return {
        name: `lifecycleConflict (ornithopter ${item} over ${dependency})`,
        product: 'ornithopter',
        target: { kind: 'part', id: item, rule: 'lifecycleConflict' },
        state: async () => ({ fails: (await conflict()) !== undefined, detail: (await conflict())?.message ?? 'no finding' }),
        plan: async () => {
          const { value } = await conflict();
          const dep = (await partsOf('ornithopter')).find((p) => p.id === value.id && p.plm.toLowerCase() === value.plm);
          return lifecycleProposal(await catalogueOf(dep.plm), dep, 'RELEASED', `${item} depends on it`);
        },
      };
    })(),
    // The part of the largest extended mass loses, per unit and in its site's unit, just over the excess.
    productCase('cubesat', 'massLimit', async () => {
      const [bom, product, parts] = [await get('/query/bom?product=cubesat'), await productOf('cubesat'), await partsOf('cubesat')];
      const nodes = [];
      const walk = (n) => { if (!n.redacted && n.partType === 'PART' && n.extendedMassKg) nodes.push(n); (n.children ?? []).forEach(walk); };
      walk(bom.root);
      const node = nodes.sort((a, b) => b.extendedMassKg - a.extendedMassKg)[0];
      const part = parts.find((p) => p.id === node.id);
      const planned = massProposal(await catalogueOf(part.plm), part, 'cubesat');
      if (!planned.ok) return planned;
      const kgPerUnit = part.mass.kg / part.mass.value;
      const raw = (node.unitMassKg - (bom.total.massKg - product.massLimit.limitKg) / node.occurrences) / kgPerUnit;
      planned.proposal.cells[0].value = (Math.ceil(raw * 1000) - 1) / 1000;
      return planned;
    }),
    productCase('difference-engine', 'massScale', async () => {
      const flagged = (await partsOf('difference-engine')).filter((p) => (p.findings ?? []).some((f) => f.rule === 'massScale'));
      return massScaleProposal(await catalogueOf(flagged[0].plm), flagged, 'difference-engine');
    }),
    (() => {
      const part = 'D-38007';
      const conflict = async () => (await findingOn('rover', part, 'conflictingLeadTime'))[0];
      return {
        name: `conflictingLeadTime (rover ${part})`,
        product: 'rover',
        target: { kind: 'part', id: part, rule: 'conflictingLeadTime' },
        state: async () => ({ fails: (await conflict()) !== undefined, detail: (await conflict())?.message ?? 'no finding' }),
        plan: async () => {
          const { value } = await conflict();
          const offers = (await get('/query/suppliers?product=rover')).suppliers.flatMap((s) => s.sites)
            .filter((site) => site.plm.toLowerCase() === value.plm).flatMap((site) => site.offers)
            .filter((o) => o.partId === part && o.preferred);
          const longer = offers.find((o) => o.id === value.id);
          const other = offers.find((o) => o !== longer);
          return leadTimeProposal(await catalogueOf(value.plm), value.plm, longer, other);
        },
      };
    })(),
  ];
  return cases;
}

export async function fixCycle({ base, headers = {}, check, loader }) {
  const { send, get } = client(base, headers);
  const changeList = async () => (await get('/query/demo/changes')).values;
  const cases = seededCases(get);

  // The release reaches Atelier's change log as the links loader records it.
  const record = async (answer, actor, purpose) => {
    const rows = answer.rows.map((r) => ({ plm: answer.plm.toLowerCase(), table: r.table, key: r.key, column: r.column, before: r.before, after: r.after, actor, purpose }));
    if (loader === 'stand-in') {
      const res = await send('POST', '/core/changes', rows, OFFICER, { 'x-atelier-actor': 'links-loader' });
      check(res.ok, `  ${rows.length} change row(s) posted to /core/changes as the links loader`, `HTTP ${res.status}`);
      return;
    }
    const logged = (values) => rows.every((r) => values.some((v) => v.table === r.table && v.key === r.key && v.column === r.column));
    let values = await changeList();
    for (let i = 0; i < 12 && !logged(values); i++) {
      await sleep(5000);
      values = await changeList();
    }
    check(logged(values), `  the change list shows the ${rows.length} row(s), through the event and the links loader`, `${values.length} row(s) listed`);
  };
  const release = async (proposal, values) => {
    const owner = `${proposal.plm}-engineer`;
    const res = await send('POST', `/${proposal.plm}/demo/update`, requestOf(proposal, values), owner);
    if (!res.ok) throw new Error(`${proposal.plm}/demo/update as ${owner}: HTTP ${res.status} ${JSON.stringify(res.body)}`);
    for (const r of res.body.rows) console.log(`release  ${res.body.plm} ${r.table} ${r.key} ${r.column}: ${show(r.before)} -> ${show(r.after)}`);
    check(res.body.rows.length === values.length && proposal.cells.every((c, i) => res.body.rows.some((r) => r.key === c.key && r.column === c.column && same(r.after, values[i]))),
      `  ${proposal.plm}/demo/update as ${owner} releases ${values.length} cell(s) in one answer`, proposal.purpose);
    await record(res.body, owner, proposal.purpose);
  };
  const reset = async () => {
    const res = await send('POST', '/core/demo/reset');
    const left = res.ok ? await changeList() : [];
    check(res.ok && left.length === 0, '  core/demo/reset as the officer replays the log and clears it', `HTTP ${res.status} ${JSON.stringify(res.body)}`);
  };

  if ((await changeList()).length > 0) {
    console.log('note  the change log is not empty (an earlier run stopped early); resetting first');
    await reset();
  }
  for (const c of cases) {
    let released = false;
    try {
      const before = await c.state();
      check(before.fails, `${c.name}: fails at the released state`, before.detail);
      const planned = await c.plan();
      if (!planned.ok) throw new Error(`no proposal: ${planned.reason}`);
      const values = planned.proposal.cells.map((cell) => cell.value);
      check(planned.proposal.cells.every((cell, i) => valid(cell, values[i])), `  the proposal's ${values.length} value(s) are ones the cells take`, planned.proposal.purpose);
      released = true;
      await release(planned.proposal, values);
      const after = await c.state();
      check(!after.fails, `${c.name}: passes once the owning site releases the correction`, after.detail);
      released = false;
      await reset();
      const again = await c.state();
      check(again.fails, `${c.name}: fails again after the reset`, again.detail);
    } catch (err) {
      check(false, `${c.name}: the cycle ran to the end`, String(err));
      if (released) await reset();
    }
  }
  await seededAgain(get, check);
}

/** Every product's failures are its seeded defects: interfaces by rule, references, lifecycle conflicts, product findings. */
async function seededAgain(get, check) {
  const { products } = await get('/query/products');
  for (const data of datasets) {
    const key = data.product.key;
    try {
      const rulesOf = (pairs) => Object.fromEntries([...pairs.reduce((m, [k, r]) => m.set(k, [...(m.get(k) ?? []), r]), new Map())]
        .map(([k, rs]) => [k, [...new Set(rs)].sort().join(',')]).sort());
      const { interfaces } = await get(`/query/interfaces?product=${key}`);
      const failing = rulesOf(interfaces.filter((i) => i.status === 'fail').flatMap((i) => i.violations.map((v) => [i.id, v.rule])));
      const expected = rulesOf((data.seededDefects ?? []).map((d) => [d.interface, d.rule]));
      check(JSON.stringify(failing) === JSON.stringify(expected), `${key}: the failing interfaces are its seeded defects again`, JSON.stringify(failing));
      const rows = new Set([...data.parts, ...(data.extended?.assemblies ?? []), ...(data.extended?.nonGeometricItems ?? []).map((i) => ({ id: i.localId.replaceAll('/', '-') }))].map((i) => i.id));
      const refs = (await get(`/query/references?product=${key}`)).references.filter((r) => r.status === 'danglingReference' || r.status === 'staleRevision');
      const refKeys = refs.map((r) => `${r.part}|${r.remoteUrn}|${r.status}`).sort();
      const seededRefs = (data.seededReferenceDefects ?? []).filter((d) => rows.has(d.localPart)).map((d) => `${d.localPart}|${d.remoteUrn}|${d.rule}`).sort();
      check(JSON.stringify(refKeys) === JSON.stringify(seededRefs), `${key}: the failing references are its seeded reference defects again`, JSON.stringify(refKeys));
      const parts = (await get(`/query/parts?product=${key}`)).parts.filter((p) => !p.redacted);
      const conflicts = parts.flatMap((p) => (p.findings ?? []).filter((f) => f.rule === 'lifecycleConflict').map((f) => `${p.id}>${f.value?.id}`)).sort();
      const seededConflicts = (data.seededLifecycleDefects ?? []).map((d) => `${d.item}>${d.dependency}`).sort();
      check(JSON.stringify(conflicts) === JSON.stringify(seededConflicts), `${key}: the lifecycle conflicts are its seeded ones again`, JSON.stringify(conflicts));
      const findings = [...new Set((products.find((p) => p.key === key)?.findings ?? []).map((f) => f.rule))].sort();
      // The product rules have no seeded list: the product file's masses, quantities and mass limit give them.
      const seededFindings = productFindings(data);
      check(JSON.stringify(findings) === JSON.stringify(seededFindings), `${key}: the product findings are ${seededFindings.join(',') || 'none'} again`, findings.join(','));
    } catch (err) {
      check(false, `${key}: its seeded defects were read back`, String(err));
    }
  }
}
