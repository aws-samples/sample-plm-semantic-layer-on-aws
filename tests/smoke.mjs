// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Post-deploy smoke test: asserts the deployed system, not the code.
//
//   0. The query service's warm-up ends with every profile of ontology/policy.json warm, within its re-warm budget;
//      the statuses it went through and each profile's attempts are printed.
//   1. The site, /api/* and /agent/* answer unauthenticated viewers with the
//      Cognito sign-in (a redirect to the hosted UI for the site, 401 for API and
//      agent calls), never with content.
//   2. The execute-api hostname rejects calls without the CloudFront origin
//      secret, and with a wrong one.
//   3. With the secret and the export-officer profile, the live API returns
//      exactly the seeded defects of data/products/*.json, the catalogue
//      finding, the generated R2RML, one working presigned CAD URL per part,
//      every product's bounds.json stored as application/json and a STEP file
//      as model/step (a HEAD with the caller's AWS credentials),
//      and one Atelier core tag per part written by the owning PLM (no part
//      untagged).
//   4. Export control per profile: a national engineer gets the interfaces with
//      a hidden side as not-evaluable and the hidden parts redacted without a
//      CAD URL; the part releasable to the officer alone is redacted from
//      everyone else; no profile evaluates nothing; native rows, the plain PLM
//      routes and the tag list follow the policy although the PLM part tables
//      carry no classification columns.
//   4b. Products: GET /api/query/products lists every product of
//      data/products, and GET /api/query/products/list the same products
//      without the product rules for three profiles, its second call within 1.5 s for every profile
//      against the deployment; for each, the interfaces and parts scoped to it are a
//      subset of the whole, the failing interfaces are that product's seeded
//      defects, the part count is the one the products answer states, the
//      pass / fail / not-evaluable tallies of export-officer and
//      programme-cleared are the ones the product's data and the policy give,
//      every software and document item is listed with its part type, the
//      cubesat's findings name massLimit, and every interface belongs to one product. The rule failures scoped to a
//      product hold that product's records and count, per rule, the other products the profile sees failing. Single-interface and evidence
//      requests name their product; an id one product alone holds resolves
//      without it.
//   4c. Bill of materials: for every product, GET /api/query/bom gives per
//      site the part occurrences its data's lines give (quantities multiplied
//      down each site kit's tree), the product root holds one kit per site and
//      the export-control officer sees no redacted node.
//   4c'. Subtree: the wind turbine gearbox's parts by root are the closure of its lines in
//      the product file, and no request the answer sent to a PLM lists parts.
//   4d. External references: for every product, the failing references of
//      GET /api/query/references (danglingReference, staleRevision) seen by the
//      export-control officer are its seededReferenceDefects made by a part row
//      (a part, an assembly, a site kit, a software or a document item).
//   4e. Purchased items: for every product whose data lists suppliers, GET
//      /api/query/equivalents groups the parts its entityResolution block names
//      as one item, GET /api/query/suppliers names every supplier of its data,
//      and the conflictingLeadTime finding is on the parts with two preferred
//      offers of different lead times in its data, and on no other part; the
//      wind turbine's pitch O-ring, bought by DE under its ISO 3601-1 code and
//      by UK in inches, is one o-ring group with its shelf life.
//   4f. Lifecycle: for every product, the parts the export-control officer sees
//      carrying lifecycleConflict are the items of its seededLifecycleDefects, each
//      with one finding per seeded dependency, and the products answer counts them.
//   4h. Placements: GET /api/query/placements draws the rover's wheel six times and
//      every part of one wind turbine blade three times, the reference occurrence
//      (the identity) first, and the answer's total is the sum of its parts' lists.
//   4i. Configured answers: for every option of every variant group, the parts and placements answers under
//      option=<code> are the configuration data/generate.py builds: its items, each geometric part drawn as many times
//      as the configuration's lines place it.
//   5. The MCP server (streamable HTTP at /api/query/mcp, same secret and
//      profile header) lists exactly the twenty-five tools of docs/contract.md.
//   6. The demo controls, each on the service that owns the fact: a value
//      correction released in the PLM of the outlier plug of the position
//      defect by its own engineer makes the position interface pass and reaches
//      Atelier's change log through the event; a link is written synchronously by
//      the core service as programme-cleared (the orphan plug to the spare plug
//      at its position: two added matesWith triples, an event id); a CAD
//      publication by the PLM of the cadPending part reaches the file index
//      through the event (one added cadFile triple, then a served presigned
//      URL); the officer's reset on the core service restores the released
//      tallies; every control answers 403 to the profiles that do not own the
//      fact, and the query service accepts no write at all. Then the preview
//      cycle of tests/preview-cycle.mjs: the correction the screen proposes for
//      one seeded defect of each rule previews as fixed with nothing newly
//      failing, the stored values as still failing, and the change list stays
//      as it was. Then the fix cycle of tests/fix-cycle.mjs: for one seeded defect of each rule (unit,
//      connector, fastener, hydraulic, danglingReference, staleRevision,
//      lifecycleConflict, massLimit, massScale, conflictingLeadTime) the
//      correction the screen proposes is released in the owning site's PLM by
//      its engineer, reaches Atelier's change log through the event and the
//      links loader, makes the rule pass, and the officer's reset makes it fail
//      again; every product's failures are its seeded defects at the end.
//   7. The live agent drives the screens: asked through the site's /agent/* behaviour
//      to show the wind turbine's gearbox, it calls open_subtree with the gearbox's id
//      (read from the AG-UI event stream); asked "what is this part" with a part
//      selected in forwardedProps.selection, it names that part.
//   8. The site serves the bundle of this checkout: index.html, fetched cache-busted through the edge sign-in, is
//      modules/web/dist/index.html, and every script and stylesheet it names has the local file's bytes.
//
// Env: SITE (https://...), API (execute-api base URL), ORIGIN_SECRET, AWS credentials that may read the CAD bucket
// (AWS_PROFILE, the deploy profile); AGENT_ID_TOKEN, the
// value of a signed-in viewer's `atelier.id` cookie, for the agent checks (skipped without it).
// LOCAL_API instead: the base URL of the fixture stack's gateway (modules/query-service/fixtures/fixes.sh runs it so),
// every check against the services and the data; the edge sign-in, the execute-api secret, the presigned CAD URLs in
// S3, the event-driven demo and the agent need the deployment and are skipped, each with its reason.
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { fixCycle } from './fix-cycle.mjs';
import { previewCycle } from './preview-cycle.mjs';
import { baseItems, cadMissingAfter, configuration, demoPendingPart, groupMatches, massLimitFailing, seededItems, unitsOf } from './seeded.mjs';

// Every request has a deadline, so a stalled endpoint fails its check instead of stopping the run.
const fetchOnce = globalThis.fetch;
globalThis.fetch = (url, init = {}) => fetchOnce(url, { signal: globalThis.AbortSignal.timeout(300_000), ...init });
const LOCAL = Boolean(process.env.LOCAL_API);
const SITE = process.env.SITE;
const API = (process.env.LOCAL_API ?? process.env.API)?.replace(/\/$/, '');
const SECRET = LOCAL ? '' : process.env.ORIGIN_SECRET;
if (!LOCAL && (!SITE || !API || !SECRET)) throw new Error('SITE, API and ORIGIN_SECRET are required, or LOCAL_API');
const skip = (what, reason) => console.log(`SKIP  ${what}: ${reason}`);

// The export-control officer is the only profile that sees every part (the
// licensed tier is releasable to the officer alone), so the full-view
// assertions run as that profile.
const CLEARED = 'export-officer';
// Every product of data/products, and the union of their parts, interfaces,
// seeded defects and spare plugs.
const productsDir = new URL('../data/products/', import.meta.url);
const datasets = readdirSync(productsDir).filter((f) => f.endsWith('.json')).sort()
  .map((f) => JSON.parse(readFileSync(new URL(f, productsDir))));
if (datasets.length === 0) throw new Error('no product in data/products');
// An interface id is unique within its product only, so every interface and seeded defect is
// keyed by product and id; the query service names the product of each interface it returns.
const inProductKey = (product) => (x) => ({ ...x, product });
// The assemblies and site kits of each site's bill of materials are rows of the PLM part tables too,
// with their own Atelier core tag: the items of a product are its parts and its assemblies.
const assembliesOf = (d) => (d.extended?.assemblies ?? []).map((a) => ({ ...a, classification: a.classification ?? { jurisdiction: 'NONE', releasableTo: 'ALL' } }));
// Software and documents are part rows of their site without geometry; the PLM keys the local id UK/3511 as UK-3511.
const nonGeometricOf = (d) => (d.extended?.nonGeometricItems ?? []).map((i) => ({ id: i.localId.replaceAll('/', '-'), plm: i.plm, type: i.type,
  classification: i.classification ?? { jurisdiction: 'NONE', releasableTo: 'ALL' } }));
const dataset = {
  parts: datasets.flatMap((d) => d.parts),
  // Every row the seeds write: parts, assemblies, software and documents, and every option's own parts and assemblies.
  items: datasets.flatMap(seededItems),
  // The items of the default configurations, which the layer's answers list.
  baseItems: datasets.flatMap(baseItems),
  interfaces: datasets.flatMap((d) => d.interfaces.map(inProductKey(d.product.key))),
  seededDefects: datasets.flatMap((d) => (d.seededDefects ?? []).map(inProductKey(d.product.key))),
  sparePlugs: datasets.flatMap((d) => d.sparePlugs ?? []),
};
const keyOf = (i) => `${i.product}/${i.id}`;
const defectKey = (d) => `${d.product}/${d.interface}`;
const interfaceOf = (defect) => dataset.interfaces.find((i) => keyOf(i) === defectKey(defect));
// The scripted demo (a position correction, an orphan link, a CAD publication) runs on the
// ornithopter, the product docs/contract.md tells it on.
const DEMO_PRODUCT = 'ornithopter';
const datasetOf = Object.fromEntries(datasets.map((d) => [d.product.key, d]));
const profiles = JSON.parse(readFileSync(new URL('../ontology/policy.json', import.meta.url))).profiles;
console.log(`data  ${datasets.map((d) => `${d.product.key} (${d.parts.length} parts, ${d.interfaces.length} interfaces, ${(d.seededDefects ?? []).length} seeded defects)`).join('; ')}`);
// Everything below is derived from the data: the parts releasable to the officer
// alone, a UK part hidden from the unknown profile, the UK row stored without a
// unit and its native table, and the interfaces a profile cannot evaluate because
// one side is not releasable to it.
const partById = Object.fromEntries(dataset.items.map((p) => [p.id, p]));
const releasableTo = (profile, part) => profiles[profile].releasable.includes(part.classification.releasableTo);
const hiddenIn = (interfaces, profile) => interfaces
  .filter((i) => i.parts.some((id) => !releasableTo(profile, partById[id])))
  .map(keyOf).sort();
const hiddenFor = (profile) => hiddenIn(dataset.interfaces, profile);
const officerOnly = dataset.parts.filter((p) => !releasableTo('programme-cleared', p));
if (officerOnly.length === 0) throw new Error('no part is releasable to the officer alone');
const ukPart = dataset.parts.find((p) => p.plm === 'UK' && releasableTo('de-engineer', p) && !releasableTo('unknown', p));
if (!ukPart) throw new Error('no UK part is visible to de-engineer and hidden from the unknown profile');
const unitDefect = dataset.seededDefects.find((d) => d.rule === 'unit');
const unitFeature = unitDefect.features[0];
// The UK table of a feature kind; the kind of the unit defect's feature is where
// its id appears in its interface.
const UK_TABLES = { pairs: 'harness_connector', fasteners: 'fastener', couplings: 'hyd_coupling' };
const unitInterface = interfaceOf(unitDefect);
const unitKind = Object.keys(UK_TABLES).find((kind) => (unitInterface[kind] ?? []).some((pair) => pair.some((f) => f.id === unitFeature)));
if (!unitKind) throw new Error(`${unitFeature} is not a feature of ${unitDefect.interface}`);
const unitTable = UK_TABLES[unitKind];
// An interface id is unique within a product only: a request for one interface or its evidence
// names the product of data/products that holds the id.
const inProduct = (defect) => `?product=${encodeURIComponent(defect.product)}`;
console.log(`data  officer-only part(s) ${officerOnly.map((p) => p.id).join(',')}; UK part ${ukPart.id}; unit defect ${unitFeature} in UK table ${unitTable}`);
let failures = 0;
const check = (ok, label, extra = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${extra ? `  (${extra})` : ''}`);
  if (!ok) failures++;
};
const canon = (obj) => JSON.stringify(Object.fromEntries(Object.entries(obj).sort()));

// 0. Wait for the API to be ready: a deploy that just finished may still be
// replacing tasks, and Cloud Map routes to a task the moment it runs.
const ready = async (path) => {
  for (let i = 0; i < 40; i++) {
    const res = await fetch(API + path, { headers: { 'x-origin-verify': SECRET } }).catch(() => null);
    if (res?.ok) return true;
    await new Promise((r) => setTimeout(r, 15000));
  }
  return false;
};
for (const path of ['/api/query/health', '/api/fr/health', '/api/de/health', '/api/uk/health', '/api/es/health', '/api/core/health']) {
  check(await ready(path), `${path} ready`);
}

// The query service's warm-up has every profile's request texts translated before a first click: it ends with every
// profile of ontology/policy.json done, each after a bounded number of attempts. A profile whose round failed is warmed
// again for up to 15 minutes (Warmup.REWARM_BUDGET), so the wait covers that budget and a last round.
{
  let warmup;
  const statuses = [];
  const started = Date.now();
  while (Date.now() - started < 17 * 60_000) {
    const res = await fetch(`${API}/api/query/health/warmup`, { headers: LOCAL ? {} : { 'x-origin-verify': SECRET } }).catch(() => null);
    warmup = res?.ok ? await res.json() : undefined;
    if (warmup?.status && statuses.at(-1) !== warmup.status) statuses.push(warmup.status);
    if (warmup?.status === 'done') break;
    await new Promise((r) => setTimeout(r, 15000));
  }
  console.log(`data  warm-up statuses ${statuses.join(' -> ') || 'none'}, settled after ${Math.round((Date.now() - started) / 1000)} s of waiting`);
  const outcomes = Object.entries(warmup?.profiles ?? {});
  check(warmup?.status === 'done' && JSON.stringify(outcomes.map(([p]) => p).sort()) === JSON.stringify(Object.keys(profiles).sort())
    && outcomes.every(([, o]) => o.status === 'done'), `the query warm-up has every one of the ${Object.keys(profiles).length} profiles warm`,
  outcomes.map(([p, o]) => `${p} ${o.status} after ${o.attempts} attempt(s)${(o.failures ?? []).map((f) => `; attempt ${f.attempt}: ${f.reason}${f.silent ? ` ${f.silent.join(', ')}` : ''}`).join('')}`).join('; ') || JSON.stringify(warmup));
}

// 1. Edge sign-in on every behaviour: the Cognito edge function sends a document to
// the hosted UI and answers API and agent calls with 401.
if (LOCAL) skip('edge sign-in and execute-api secret', 'CloudFront, Cognito and API Gateway are the deployment\'s');
for (const path of LOCAL ? [] : ['/', '/api/query/interfaces', '/agent/health']) {
  const res = await fetch(SITE + path, { redirect: 'manual' });
  const gated = path === '/'
    ? res.status === 302 && (res.headers.get('location') ?? '').includes('/oauth2/authorize')
    : res.status === 401;
  check(gated, `site ${path} is Cognito-gated`, `status ${res.status}`);
}

// 2. Direct execute-api access.
for (const [label, headers] of LOCAL ? [] : [['no secret', {}], ['wrong secret', { 'x-origin-verify': 'x'.repeat(SECRET.length) }]]) {
  const res = await fetch(`${API}/api/fr/parts`, { headers });
  check(res.status === 401 || res.status === 403, `execute-api rejects ${label}`, `status ${res.status}`);
}

// 3. Live results through the API with the origin secret. `profile` null sends
// no x-atelier-profile header at all.
const headers = (profile) => ({ 'x-origin-verify': SECRET, ...(profile ? { 'x-atelier-profile': profile } : {}) });
// A rolling task replacement can still answer 502/503 for a short while after
// the readiness probes pass; retry those, fail on anything else.
const get = async (path, profile = CLEARED) => {
  let res;
  for (let attempt = 0; attempt < 8; attempt++) {
    res = await fetch(API + path, { headers: headers(profile) });
    if (![502, 503, 504].includes(res.status)) break;
    await new Promise((r) => setTimeout(r, 10000));
  }
  if (!res.ok) throw new Error(`${path} [${profile ?? 'no profile'}]: ${res.status} ${await res.text()}`);
  return res.headers.get('content-type')?.includes('json') ? res.json() : res.text();
};

const { interfaces, policy } = await get('/api/query/interfaces');
check(interfaces.length === dataset.interfaces.length, 'all interfaces returned', `${interfaces.length}/${dataset.interfaces.length}`);
check(Array.isArray(policy?.untagged) && policy.untagged.length === 0, 'export-officer finds no untagged part', JSON.stringify(policy?.untagged));
check(interfaces.every((i) => i.status === 'pass' || i.status === 'fail'), 'export-officer evaluates every interface',
  interfaces.filter((i) => i.status === 'not-evaluable').map(keyOf).join(','));
const rules = (violations) => [...new Set(violations.map((v) => v.rule))].sort().join(',');
const failing = Object.fromEntries(interfaces.filter((i) => i.status === 'fail').map((i) => [keyOf(i), rules(i.violations)]));
const expectedFailing = (defects) => Object.fromEntries(
  [...new Set(defects.map(defectKey))].map((key) => [key, rules(defects.filter((d) => defectKey(d) === key))]),
);
const expected = expectedFailing(dataset.seededDefects);
check(canon(failing) === canon(expected), 'failing interfaces are exactly the seeded defects, grouped by interface', JSON.stringify(failing));
// The position defect: the pair of plugs it names, the axis on which their
// coordinates differ and by how much (mm; a coordinate stored in inches converted).
const mm = (feature, axis) => Number(feature.position[axis]) * (feature.unit === 'IN' ? 25.4 : 1);
const positionDefect = dataset.seededDefects.find((d) => d.product === DEMO_PRODUCT && d.rule === 'position');
const positionInterface = interfaceOf(positionDefect);
const positionPair = positionInterface.pairs.find((pair) => pair.every((f) => positionDefect.features.includes(f.id)));
const [axis, deltaMm] = ['x', 'y', 'z'].map((a) => [a, Math.abs(mm(positionPair[0], a) - mm(positionPair[1], a))]).sort((a, b) => b[1] - a[1])[0];
console.log(`data  position defect ${positionDefect.interface}: ${positionPair.map((f) => f.id).join(' / ')} differ by ${deltaMm.toFixed(3)} mm on ${axis}`);
const position = interfaces.filter((i) => keyOf(i) === defectKey(positionDefect)).flatMap((i) => i.violations).find((v) => v.rule === 'position');
check(position && position.detail.axis === axis && Math.abs(position.detail.deltaMm - deltaMm) < 0.01,
  `position offset is measured, ${deltaMm.toFixed(1)} mm on ${axis}`, `${position?.detail?.deltaMm} on ${position?.detail?.axis}`);

const parts = (await Promise.all(['fr', 'de', 'uk', 'es'].map((p) => get(`/api/${p}/parts`)))).flat();
check(parts.length === dataset.items.length, 'parts and assemblies across the four PLMs', `${parts.length}/${dataset.items.length}`);

// Atelier core: one part_tag row per part and assembly, written by the PLM that owns it.
const tags = await get('/api/core/tags');
const tagOf = Object.fromEntries(tags.map((t) => [t.nativeKey, t]));
check(tags.length === dataset.items.length
  && dataset.items.every((p) => tagOf[p.id]?.taggedBy?.toUpperCase() === p.plm.toUpperCase()),
  'Atelier core holds one tag per part and assembly, tagged by the owning PLM',
  `${tags.length}/${dataset.items.length}; ${dataset.items.filter((p) => tagOf[p.id]?.taggedBy?.toUpperCase() !== p.plm.toUpperCase()).map((p) => `${p.id}:${tagOf[p.id]?.taggedBy ?? 'untagged'}`).join(',') || 'all match'}`);

// 3b. The plain PLM routes and the tag list follow the profile like every other
// path: a national engineer asking a PLM for its parts does not receive a part
// released to another nation only, the PLM's features follow their part, and the
// unknown profile is shown the tags of parts released to all and no other.
const hiddenFrParts = dataset.parts.filter((p) => p.plm === 'FR' && !releasableTo('de-engineer', p));
if (hiddenFrParts.length === 0) throw new Error('no FR part is hidden from de-engineer');
const frPartsAsDe = await get('/api/fr/parts', 'de-engineer');
check(frPartsAsDe.length === dataset.items.filter((p) => p.plm === 'FR' && releasableTo('de-engineer', p)).length
  && !frPartsAsDe.some((p) => hiddenFrParts.some((h) => h.id === p.id)),
  `de-engineer calling GET /api/fr/parts does not receive ${hiddenFrParts.map((p) => p.id).join(',')}`, frPartsAsDe.map((p) => p.id).join(','));
const frFeaturesAsDe = (await Promise.all(['plugs', 'fasteners', 'couplings'].map((kind) => get(`/api/fr/${kind}`, 'de-engineer')))).flat();
check(frFeaturesAsDe.length > 0 && frFeaturesAsDe.every((f) => releasableTo('de-engineer', partById[f.partId])),
  'FR features served to de-engineer all belong to parts de-engineer may see', frFeaturesAsDe.filter((f) => !releasableTo('de-engineer', partById[f.partId])).map((f) => f.id).join(','));
const tagsAsUnknown = await get('/api/core/tags', 'unknown');
check(tagsAsUnknown.length === dataset.items.filter((p) => releasableTo('unknown', p)).length
  && tagsAsUnknown.every((t) => t.releasableTo === 'ALL' && !t.jurisdiction.startsWith('NATIONAL') && t.jurisdiction !== 'EXPORT-LICENCE'),
  'GET /api/core/tags as unknown returns no NATIONAL or EXPORT-LICENCE tag', tagsAsUnknown.filter((t) => t.releasableTo !== 'ALL').map((t) => `${t.nativeKey}:${t.jurisdiction}`).join(',') || `${tagsAsUnknown.length} tag(s), all ALL`);

const es = await get('/api/es/catalogue');
const undescribed = es.entities.flatMap((e) => e.columns.filter((c) => c.undescribed).map((c) => c.column));
check(JSON.stringify(undescribed) === '["obsoleto"]', 'catalogue flags the undescribed ES column', undescribed.join(','));

const rows = await get(`/api/uk/tables/${unitTable}?keys=${encodeURIComponent(unitFeature)}`);
const uom = rows.columns.indexOf('pos_uom');
check(rows.rows.length === 1 && uom >= 0 && rows.rows[0][uom] === null, `native UK ${unitTable} row ${unitFeature} shows pos_uom NULL`, JSON.stringify(rows.rows[0]));
const forbidden = await fetch(`${API}/api/uk/tables/pg_user?keys=postgres`, { headers: headers(CLEARED) });
check(forbidden.status === 404, 'native rows refuse tables outside the PLM metamodel', `status ${forbidden.status}`);

const evidence = await get(`/api/query/interfaces/${unitDefect.interface}/evidence${inProduct(unitDefect)}`);
const ukArm = evidence.arms.find((a) => a.endpoint === 'ontop-uk');
check(ukArm && ukArm.tables.some((t) => t.table === unitTable && t.keys.includes(unitFeature)), `evidence traces ${unitDefect.interface} to the UK ${unitTable} row`, JSON.stringify(ukArm?.tables));
check(evidence.shacl.shapes.some((sh) => sh.rule === 'unit') && evidence.shacl.report.includes('sh:ValidationResult'), 'evidence carries the unit shape and a SHACL report');
check(evidence.arms.every((a) => typeof a.triples === 'string' && a.triples.length > 0), 'every arm returns its triples as Turtle');

const ukMapping = await get('/api/uk/mapping');
check(ukMapping.includes('http://qudt.org/vocab/unit/{pos_uom}'), 'UK R2RML reads the per-row unit column');

// Presigned CAD URLs: direct S3, no redirect, STEP content type, one per visible
// part. The part released without a file-index entry (cadPending in
// data/products) has none until section 6 publishes it.
const { parts: visibleItems } = await get('/api/query/parts');
check(visibleItems.length === dataset.baseItems.length && visibleItems.every((p) => !p.redacted), 'export-officer sees every part and assembly', `${visibleItems.length}/${dataset.baseItems.length}`);
// Only a part with geometry has a CAD file: an assembly or a site kit has none and no URL.
const geometric = (p) => (p.partType ?? 'PART') === 'PART';
const assemblies = visibleItems.filter((p) => !geometric(p));
check(assemblies.length === dataset.baseItems.length - dataset.parts.length && assemblies.every((p) => p.cadUrl == null),
  'assemblies carry no CAD URL', assemblies.filter((p) => p.cadUrl != null).map((p) => p.id).join(',') || `${assemblies.length} assemblies`);
const visible = visibleItems.filter(geometric);
const servesStep = async (cadUrl) => {
  const direct = typeof cadUrl === 'string' && new URL(cadUrl).hostname.endsWith('.amazonaws.com');
  // Hundreds of downloads in a row: a connection reset from S3 is retried, it is not a CAD defect.
  let res;
  for (let attempt = 1; direct && !res; attempt++) {
    res = await fetch(cadUrl, { redirect: 'manual' }).catch((e) => { if (attempt >= 3) throw e; return undefined; });
  }
  await res?.body?.cancel();
  const type = res?.headers.get('content-type') ?? '';
  const encoding = res?.headers.get('content-encoding') ?? 'identity';
  return { ok: direct && res.status === 200 && type.startsWith('model/step') && encoding === 'gzip', detail: `status ${res?.status ?? '-'} ${type}, ${encoding}` };
};
if (LOCAL) skip('presigned CAD URLs, the stored CAD content types and the bucket\'s CORS', 'the fixture stack signs no URL: the CAD bucket is the deployment\'s');
for (const part of LOCAL ? [] : visible) {
  if (part.cadUrl == null && partById[part.id]?.cadPending) {
    console.log(`skip  ${part.id} has no CAD URL: cadPending in data/products`);
    continue;
  }
  const served = await servesStep(part.cadUrl);
  check(served.ok, `presigned CAD URL serves ${part.cadFile ?? part.id} gzip-encoded`, served.detail);
}
// No URL serves a product's bounds.json, so the stored metadata of the CAD objects is read with a HEAD under the
// caller's AWS credentials (AWS_PROFILE): the deployment's sync can skip an object it takes as unchanged, and the
// object then keeps the content type it was first stored with. One STEP object is read the same way.
{
  const sample = LOCAL ? undefined : visible.find((p) => p.cadUrl);
  if (sample) {
    const { hostname } = new URL(sample.cadUrl);
    const [bucket, rest] = hostname.split('.s3.');
    const region = rest.split('.')[0];
    const head = (key) => {
      try {
        return JSON.parse(execFileSync('aws', ['s3api', 'head-object', '--bucket', bucket, '--key', key, '--region', region, '--output', 'json'],
          { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }));
      } catch (e) {
        return { error: String(e.stderr ?? e.message).trim() };
      }
    };
    const stored = (h) => h.error ?? `${h.ContentType}, ${h.ContentEncoding ?? 'identity'}`;
    const step = head(sample.cadFile);
    check(step.ContentType === 'model/step' && step.ContentEncoding === 'gzip', `${sample.cadFile} is stored as model/step, gzip-encoded`, stored(step));
    const cadDir = new URL('../modules/cad/stp/', import.meta.url);
    for (const key of readdirSync(cadDir).filter((k) => readdirSync(new URL(`${k}/`, cadDir)).includes('bounds.json')).sort()) {
      const bounds = head(`cad/${key}/bounds.json`);
      check(bounds.ContentType === 'application/json' && bounds.ContentEncoding === 'gzip', `cad/${key}/bounds.json is stored as application/json, gzip-encoded`, stored(bounds));
    }
  }
}
// The browser reads those URLs from the site origin, so the bucket must answer the
// CORS preflight for it; a server-side fetch cannot see a missing CORS rule.
{
  const sample = visible.find((p) => p.cadUrl);
  if (sample && !LOCAL) {
    const res = await fetch(sample.cadUrl, { method: 'OPTIONS', headers: { Origin: new URL(SITE).origin, 'Access-Control-Request-Method': 'GET' } });
    const allow = res.headers.get('access-control-allow-origin');
    check(allow === new URL(SITE).origin || allow === '*', 'CAD bucket allows the site origin (CORS)', `status ${res.status}, allow-origin ${allow}`);
  }
}

// 4. Export control per profile.
// programme-cleared sees everything except the officer-only parts, so only their interfaces are hidden.
const clearedHidden = hiddenFor('programme-cleared');
const cleared = (await get('/api/query/interfaces', 'programme-cleared')).interfaces;
check(JSON.stringify(cleared.filter((i) => i.status === 'not-evaluable').map(keyOf).sort()) === JSON.stringify(clearedHidden)
  && cleared.filter((i) => i.status === 'fail').length === Object.keys(expected).filter((key) => !clearedHidden.includes(key)).length,
  `programme-cleared: only ${clearedHidden.join(',')} not evaluable`, cleared.map((i) => `${keyOf(i)}:${i.status}`).join(' '));

const de = (await get('/api/query/interfaces', 'de-engineer')).interfaces;
const deHidden = de.filter((i) => i.status === 'not-evaluable');
check(JSON.stringify(deHidden.map(keyOf).sort()) === JSON.stringify(hiddenFor('de-engineer')), `de-engineer cannot evaluate ${hiddenFor('de-engineer').join(',')}`, deHidden.map(keyOf).join(','));
check(deHidden.length > 0 && deHidden.every((i) => i.violations.length === 0 && i.features.some((f) => f.redacted === true && typeof f.plm === 'string' && f.id === undefined)),
  'not-evaluable interfaces carry redacted features and no violations');

const { parts: deParts } = await get('/api/query/parts', 'de-engineer');
const redacted = deParts.filter((p) => p.redacted === true);
check(redacted.length > 0 && !deParts.some((p) => officerOnly.some((o) => o.id === p.id)) && redacted.every((p) => p.cadUrl === undefined && p.id === undefined),
  `de-engineer gets the officer-only part ${officerOnly.map((p) => p.id).join(',')} redacted, with no CAD URL`, JSON.stringify(redacted));

const ukRow = await get(`/api/uk/tables/component?keys=${encodeURIComponent(ukPart.id)}`, 'de-engineer');
check(ukRow.rows.length === 1, `native UK component row ${ukPart.id} is visible to de-engineer`, `${ukRow.rows.length} row(s)`);
check(!ukRow.columns.some((c) => c === 'export_class' || c === 'releasable_to'), 'the UK part table carries no classification columns', ukRow.columns.join(','));
const ukRowUnknown = await get(`/api/uk/tables/component?keys=${encodeURIComponent(ukPart.id)}`, 'unknown');
check(ukRowUnknown.rows.length === 0, `native UK component row ${ukPart.id} is hidden from the unknown profile`, `${ukRowUnknown.rows.length} row(s)`);

// 4b. Products. The tallies of the whole deployment hold per product: scoped to
// a product, the answers cover that product's interfaces and parts only, the
// failing ones are its seeded defects, the pass / fail / not-evaluable counts
// per profile are the ones its data and the policy give, and the PLMs are asked
// about the product's parts only (the part count is the one the products answer
// states). Every interface belongs to exactly one product.
const tally = (list, status) => list.filter((i) => i.status === status).length;
const tallies = (list) => `${tally(list, 'pass')}/${tally(list, 'fail')}/${tally(list, 'not-evaluable')}`;
const expectedTallies = (source, profile) => {
  const hidden = new Set(hiddenIn(source.interfaces, profile));
  const defective = new Set((source.seededDefects ?? []).map(defectKey));
  const notEvaluable = source.interfaces.filter((i) => hidden.has(keyOf(i))).length;
  const fail = source.interfaces.filter((i) => !hidden.has(keyOf(i)) && defective.has(keyOf(i))).length;
  return `${source.interfaces.length - notEvaluable - fail}/${fail}/${notEvaluable}`;
};
// Without a profile the viewer is `unknown`: it sees the unrestricted parts only, so the
// interfaces with a restricted side are not evaluable and the rest are judged as for anyone.
const anonymous = (await get('/api/query/interfaces', null)).interfaces;
check(anonymous.length === dataset.interfaces.length && tallies(anonymous) === expectedTallies(dataset, 'unknown'),
  `without a profile the tallies are ${expectedTallies(dataset, 'unknown')} (pass/fail/not-evaluable)`, tallies(anonymous));
const { products } = await get('/api/query/products');
check(Array.isArray(products) && JSON.stringify(products.map((p) => p.key).sort()) === JSON.stringify(Object.keys(datasetOf).sort()),
  'the products listed are those of data/products', JSON.stringify(products?.map((p) => p.key)));
// The product list the screens wait for is the products answer without the product rules, for every profile.
for (const profile of [CLEARED, 'programme-cleared', 'de-engineer']) {
  const listing = (await get('/api/query/products/list', profile)).products;
  const ruled = profile === CLEARED ? products : (await get('/api/query/products', profile)).products;
  const plain = (list) => JSON.stringify((list ?? []).map(({ key, name, frame, partCount }) => ({ key, name, frame, partCount })));
  check(plain(listing) === plain(ruled) && (listing ?? []).every((p) => !('findings' in p) && !('massLimit' in p)),
    `${profile}: the product list is the products answer without the product rules`, `${listing?.length} product(s)`);
}
// The product list is the first answer every screen waits for: once warm, it answers within 1.5 s for every profile.
// Against the fixture stack on a laptop the times are printed only.
for (const profile of Object.keys(profiles)) {
  const timed = async () => {
    const t = Date.now();
    await get('/api/query/products/list', profile);
    return Date.now() - t;
  };
  const [first, second] = [await timed(), await timed()];
  if (LOCAL) console.log(`data  ${profile}: the product list answered in ${first} ms, then ${second} ms`);
  else check(second < 1500, `${profile}: the product list answers within 1.5 s once warm`, `${first} ms, then ${second} ms`);
}
const allKeys = new Set(interfaces.map(keyOf));
const seen = new Map();
const productsOfId = new Map();
for (const product of products ?? []) {
  const data = datasetOf[product.key];
  if (!data) {
    check(false, `product ${product.key} is in data/products`);
    continue;
  }
  const source = { ...data, interfaces: data.interfaces.map(inProductKey(product.key)),
    seededDefects: (data.seededDefects ?? []).map(inProductKey(product.key)) };
  const scopedPath = `?product=${encodeURIComponent(product.key)}`;
  const { interfaces: own } = await get(`/api/query/interfaces${scopedPath}`);
  const { parts: ownParts } = await get(`/api/query/parts${scopedPath}`);
  const ownKeys = own.map(keyOf);
  check(ownKeys.length > 0 && ownKeys.every((key) => allKeys.has(key)) && own.every((i) => i.product === product.key),
    `product ${product.key}: its interfaces are among the whole deployment's and name it`, `${ownKeys.length} interface(s)`);
  check(JSON.stringify([...ownKeys].sort()) === JSON.stringify(source.interfaces.map(keyOf).sort()), `product ${product.key}: its interfaces are the ${source.interfaces.length} of its data`, `${ownKeys.length} interface(s)`);
  for (const key of ownKeys) seen.set(key, [...(seen.get(key) ?? []), product.key]);
  for (const i of own) productsOfId.set(i.id, [...(productsOfId.get(i.id) ?? []), product.key]);
  const ownFailing = Object.fromEntries(own.filter((i) => i.status === 'fail').map((i) => [keyOf(i), rules(i.violations)]));
  check(canon(ownFailing) === canon(expectedFailing(source.seededDefects ?? [])), `product ${product.key}: failing interfaces are its seeded defects`, JSON.stringify(ownFailing));
  check(own.every((i) => i.status === 'pass' || i.status === 'fail'), `product ${product.key}: export-officer evaluates every interface`);
  check(tallies(own) === expectedTallies(source, CLEARED), `product ${product.key}: export-officer tallies ${expectedTallies(source, CLEARED)} (pass/fail/not-evaluable)`, tallies(own));
  const { interfaces: ownCleared } = await get(`/api/query/interfaces${scopedPath}`, 'programme-cleared');
  check(tallies(ownCleared) === expectedTallies(source, 'programme-cleared'), `product ${product.key}: programme-cleared tallies ${expectedTallies(source, 'programme-cleared')} (pass/fail/not-evaluable)`, tallies(ownCleared));
  const ownPartIds = new Set(ownParts.filter((p) => !p.redacted).map((p) => p.id));
  const named = new Set(own.flatMap((i) => i.parts.filter((p) => !p.redacted).map((p) => p.id)));
  const ownGeometric = ownParts.filter((p) => !p.redacted && (p.partType ?? 'PART') === 'PART');
  check(ownGeometric.length === product.partCount && product.partCount === source.parts.length && [...named].every((id) => ownPartIds.has(id))
    && ownParts.length === source.parts.length + assembliesOf(data).length + nonGeometricOf(data).length,
    `product ${product.key}: ${product.partCount} part(s) as the products answer states and its data holds, covering its interfaces' parts, with its ${assembliesOf(data).length} assemblies and ${nonGeometricOf(data).length} software and documents`,
    `${ownGeometric.length} part(s) of ${ownParts.length} item(s), ${named.size} named, ${source.parts.length} in data`);
  const typeOf = Object.fromEntries(ownParts.filter((p) => !p.redacted).map((p) => [p.id, p.partType]));
  const untyped = nonGeometricOf(data).filter((i) => typeOf[i.id] !== i.type);
  check(untyped.length === 0, `product ${product.key}: every software and document item is listed with its part type`,
    untyped.map((i) => `${i.id}:${typeOf[i.id] ?? 'absent'}`).join(',') || `${nonGeometricOf(data).length} item(s)`);
  check(typeof product.name === 'string' && product.name.length > 0, `product ${product.key} has a name`, product.name);
}
// No PLM holds the product, so only the layer can weigh it: the cubesat's sites together exceed its 1U limit.
const cubesat = (products ?? []).find((p) => p.key === 'cubesat');
check((cubesat?.findings ?? []).some((f) => f.rule === 'massLimit') && cubesat?.massLimit?.status === 'fail',
  'the cubesat\'s product findings name massLimit and its mass limit fails', JSON.stringify({ findings: cubesat?.findings, massLimit: cubesat?.massLimit }));
// A profile that may not see every item of the cubesat cannot weigh it: the mass limit is not evaluable and raises nothing.
const cubesatCleared = (await get('/api/query/products', 'programme-cleared')).products?.find((p) => p.key === 'cubesat');
check(cubesatCleared?.massLimit?.status === 'not-evaluable' && cubesatCleared.massLimit.hiddenItems > 0 && !(cubesatCleared.findings ?? []).some((f) => f.rule === 'massLimit'),
  'programme-cleared: the cubesat\'s mass limit is not evaluable', JSON.stringify(cubesatCleared?.massLimit));
// The Rules screen's answer: one product's records, and per rule a count of the other products where it fails, each
// profile counting from its own every-product answer (the cubesat's mass limit counts for the officer alone).
const massLimitElsewhere = {};
for (const [profile, product] of [['export-officer', 'cubesat'], ['export-officer', 'wind-turbine'], ['programme-cleared', 'wind-turbine']]) {
  const every = (await get('/api/query/rules/failures', profile)).failures;
  const scopedRules = await get(`/api/query/rules/failures?product=${product}`, profile);
  const others = {};
  for (const f of every.filter((x) => x.product !== product)) (others[f.rule] ??= new Set()).add(f.product);
  const want = Object.fromEntries(Object.entries(others).map(([rule, keys]) => [rule, keys.size]));
  const own = every.filter((f) => f.product === product).map((f) => `${f.rule}|${f.kind}|${f.id}`);
  check(scopedRules.failures.length > 0 && JSON.stringify(scopedRules.failures.map((f) => `${f.rule}|${f.kind}|${f.id}`)) === JSON.stringify(own)
    && JSON.stringify(scopedRules.otherProducts) === JSON.stringify(want),
    `${profile}: GET /api/query/rules/failures?product=${product} holds its ${own.length} records and counts the other products per rule`,
    JSON.stringify({ got: scopedRules.otherProducts, want, records: scopedRules.failures.length, own: own.length }));
  if (product === 'wind-turbine') massLimitElsewhere[profile] = scopedRules.otherProducts.massLimit ?? 0;
}
// A product with an item released to the officer alone has its mass limit read by the officer alone: for the other
// profiles it is not evaluable and counts nowhere.
const wantElsewhere = Object.fromEntries(Object.keys(massLimitElsewhere).map((p) => [p, massLimitFailing(datasets, profiles[p]).filter((k) => k !== 'wind-turbine').length]));
check(JSON.stringify(massLimitElsewhere) === JSON.stringify(wantElsewhere),
  'the wind turbine\'s count of other products failing massLimit is, per profile, the products over their limit whose every item the profile sees', JSON.stringify({ got: massLimitElsewhere, want: wantElsewhere }));
const unplaced = [...allKeys].filter((key) => !seen.has(key));
const shared = [...seen.entries()].filter(([, keys]) => keys.length > 1);
check(unplaced.length === 0 && shared.length === 0, 'every interface belongs to exactly one product', `unplaced ${unplaced.join(',') || '-'}; shared ${shared.map(([key, keys]) => `${key}:${keys.join('+')}`).join(',') || '-'}`);
// An id that one product alone holds resolves without the product parameter.
const sole = ['IF-13', ...productsOfId.keys()].find((id) => (productsOfId.get(id) ?? []).length === 1);
const bare = await fetch(`${API}/api/query/interfaces/${sole}`, { headers: headers(CLEARED) });
await bare.arrayBuffer();
check(bare.status === 200, `GET /api/query/interfaces/${sole} without product answers 200 while one product holds the id`, `status ${bare.status}, held by ${(productsOfId.get(sole) ?? []).join('+') || '-'}`);
const noProduct = await fetch(`${API}/api/query/interfaces?product=no-such-product`, { headers: headers(CLEARED) });
await noProduct.arrayBuffer();
check(noProduct.status === 404, 'an unknown product is 404', `status ${noProduct.status}`);

// 4c. Bill of materials: the layer's tree of each product against its data. Each site's part
// occurrences are the quantities of its lines multiplied down from its site kit, over the parts with
// geometry; the officer sees every node.
const occurrencesBySite = (data) => {
  const geometricIds = new Set(data.parts.filter((p) => (p.extended?.type ?? 'PART') === 'PART').map((p) => p.id));
  const children = new Map();
  for (const line of data.extended?.bomLines ?? []) children.set(line.parent, [...(children.get(line.parent) ?? []), line]);
  const bySite = {};
  for (const kit of assembliesOf(data).filter((a) => a.kind === 'SITE_KIT')) {
    let total = 0;
    const walk = (item, mult) => (children.get(item) ?? []).forEach((line) => {
      if (geometricIds.has(line.child)) total += mult * line.quantity;
      walk(line.child, mult * line.quantity);
    });
    walk(kit.id, 1);
    bySite[kit.plm.toLowerCase()] = total;
  }
  return bySite;
};
const redactedNodes = (node) => (node.redacted ? 1 : 0) + (node.children ?? []).reduce((n, c) => n + redactedNodes(c), 0);
for (const data of datasets) {
  const key = data.product.key;
  const bom = await get(`/api/query/bom?product=${encodeURIComponent(key)}`);
  const expectedSites = occurrencesBySite(data);
  const sites = Object.fromEntries((bom.sites ?? []).map((r) => [r.plm, r.occurrences]));
  check(canon(sites) === canon(expectedSites), `product ${key}: bill-of-materials occurrences per site are its data's`, `${JSON.stringify(sites)} vs ${JSON.stringify(expectedSites)}`);
  check(bom.root?.partType === 'PRODUCT' && bom.root.children.length === Object.keys(expectedSites).length
    && bom.root.children.every((k) => k.partType === 'ASSEMBLY'), `product ${key}: the root holds one site kit per site`, bom.root?.children?.map((k) => k.id).join(','));
  check(redactedNodes(bom.root) === 0 && bom.total?.hiddenOccurrences === 0, `product ${key}: export-officer sees no redacted bill-of-materials node`,
    `${redactedNodes(bom.root)} redacted, ${bom.total?.hiddenOccurrences} hidden occurrences`);
}

// 4c'. Subtree of one assembly: the wind turbine's gearbox, asked by root. Its parts are the closure of its lines in the
// product file (the gearbox's tree references no other site), and every request the answer sent to a PLM names the
// root (VALUES ?root through atelier:contains), none a list of parts (VALUES ?part).
{
  const SUBTREE_PRODUCT = 'wind-turbine';
  const GEARBOX = 'D-37073';
  const lines = datasetOf[SUBTREE_PRODUCT].extended.bomLines;
  const closure = new Set([GEARBOX]);
  for (let grew = true; grew;) {
    grew = false;
    for (const line of lines) if (closure.has(line.parent) && !closure.has(line.child)) { closure.add(line.child); grew = true; }
  }
  const answer = await get(`/api/query/parts?product=${SUBTREE_PRODUCT}&root=${GEARBOX}`);
  const ids = (answer.parts ?? []).filter((p) => !p.redacted).map((p) => p.id).sort();
  check(JSON.stringify(ids) === JSON.stringify([...closure].sort()), `subtree ${GEARBOX}: its parts are the closure of its lines in the product file`,
    `${ids.length} vs ${closure.size}`);
  const requests = (answer.sparql ?? '').split(/\n\n(?=PREFIX)/);
  const plmRequests = requests.filter((r) => !r.includes('GRAPH <') && !r.includes('atelier:taggedBy') && !r.includes('atelier:partOf'));
  check(plmRequests.length > 0 && plmRequests.every((r) => r.includes('VALUES ?root') && !r.includes('VALUES ?part')),
    `subtree ${GEARBOX}: every PLM request names the root, none lists parts`, `${plmRequests.length} PLM requests`);
  check(answer.subtree?.root === GEARBOX && answer.subtree?.depth === 1 && answer.subtree?.productRules === 'not-evaluable',
    `subtree ${GEARBOX}: resolved in one round on its own site, product rules not evaluated`, JSON.stringify(answer.subtree?.rounds));
}

// 4d. External references. A site names another site's part by URN; the layer alone
// tells a reference to nothing or to a moved-on revision. The officer sees every part,
// so every reference is evaluable and the failing ones are exactly the seeded ones.
const REFERENCE_RULES = new Set(['danglingReference', 'staleRevision']);
for (const data of datasets) {
  const key = data.product.key;
  const rows = new Set([...data.parts, ...assembliesOf(data), ...nonGeometricOf(data)].map((i) => i.id));
  const expectedRefs = (data.seededReferenceDefects ?? []).filter((d) => rows.has(d.localPart)).map((d) => `${d.localPart}|${d.remoteUrn}|${d.rule}`).sort();
  const answer = await get(`/api/query/references?product=${encodeURIComponent(key)}`, CLEARED);
  const failingRefs = (answer.references ?? []).filter((r) => REFERENCE_RULES.has(r.status)).map((r) => `${r.part}|${r.remoteUrn}|${r.status}`).sort();
  check(JSON.stringify(failingRefs) === JSON.stringify(expectedRefs), `product ${key}: failing external references are its ${expectedRefs.length} seeded reference defect(s)`,
    JSON.stringify(failingRefs));
}

// 4e. Purchased items and suppliers, against each product's supplier lists, supplier offers and entity-resolution block.
// An offer names its part by the site's own id (localId); its preferred flag and lead time are in the site's columns.
const PREFERRED = ['bevorzugt', 'prefere', 'preferido', 'preferred'];
const LEAD_DAYS = ['lieferzeit_tage', 'delai_jours', 'plazo_dias', 'lead_time_days'];
const field = (row, names) => row[names.find((n) => n in row)];
const conflictParts = new Set();
for (const data of datasets.filter((d) => d.extended?.suppliers)) {
  const key = data.product.key;
  const idOf = Object.fromEntries(data.parts.flatMap((p) => [[p.extended?.localId ?? p.id, p.id], [p.id, p.id]]));
  const preferredDays = {};
  for (const block of Object.values(data.extended.supplierParts ?? {})) {
    for (const row of block.rows) {
      if (!field(row, PREFERRED)) continue;
      const id = idOf[Object.values(row)[0]];
      preferredDays[id] = [...(preferredDays[id] ?? []), field(row, LEAD_DAYS)];
    }
  }
  const conflicts = Object.entries(preferredDays).filter(([, days]) => new Set(days).size > 1).map(([id]) => id).sort();
  conflicts.forEach((id) => conflictParts.add(id));
  const names = [...new Set(Object.values(data.extended.suppliers).flatMap((b) => b.rows.map((r) => Object.values(r)[1])))].sort();
  const suppliers = await get(`/api/query/suppliers?product=${encodeURIComponent(key)}`);
  const listedNames = (suppliers.suppliers ?? []).map((x) => x.name).sort();
  check(JSON.stringify(listedNames) === JSON.stringify(names), `product ${key}: the suppliers answer names every supplier of its data`, listedNames.join(', '));
  const answered = [...new Set((suppliers.conflicts ?? []).map((c) => c.id))].sort();
  check(JSON.stringify(answered) === JSON.stringify(conflicts), `product ${key}: lead-time conflicts are its data's (${conflicts.join(', ')})`, answered.join(','));
  const equivalents = await get(`/api/query/equivalents?product=${encodeURIComponent(key)}`);
  for (const item of data.extended.entityResolution ?? []) {
    const ids = item.parts.map((m) => idOf[m.localId]).sort();
    const group = (equivalents.groups ?? []).find((g) => ids.every((id) => g.members.some((m) => m.id === id)));
    const { ok, label } = groupMatches(item, ids, group);
    check(ok, `product ${key}: ${item.item} is one group of ${label}`, JSON.stringify(group?.members?.map((m) => [m.id, unitsOf(m)])));
  }
}
// The wind turbine's pitch O-ring: the German site names it by its ISO 3601-1 code, the British one by its inch size.
{
  const { groups } = await get('/api/query/equivalents?product=wind-turbine');
  const ring = (groups ?? []).find((g) => g.itemClass === 'o-ring' && g.members.some((m) => m.id === 'D-37056'));
  check(ring !== undefined && JSON.stringify(ring.members.map((m) => m.id)) === JSON.stringify(['D-37056', 'UK-3741'])
    && ring.shelfLifeMonths === 84 && ring.stocking.partNumbers === 2 && ring.stocking.sites === 2 && ring.stocking.stockLinesOnceConfirmed === 1,
  'product wind-turbine: the pitch O-ring is one o-ring group of 2 part numbers in 2 sites, shelf life 84 months', JSON.stringify(ring));
}
const flagged = visibleItems.filter((p) => (p.findings ?? []).some((f) => f.rule === 'conflictingLeadTime')).map((p) => p.id).sort();
check(flagged.length > 0 && JSON.stringify(flagged) === JSON.stringify([...conflictParts].sort()),
  'conflictingLeadTime fires on the parts whose data has conflicting preferred lead times and nowhere else', flagged.join(','));

// 4f. Lifecycle propagation. Each site releases in its own words; only the layer sees a released item over a
// dependency another site, or its own, has not released. The officer sees every item, so every seeded conflict fires.
for (const data of datasets) {
  const key = data.product.key;
  const seeded = data.seededLifecycleDefects ?? [];
  const expected = [...new Set(seeded.map((d) => d.item))].sort();
  const { parts: items } = await get(`/api/query/parts?product=${encodeURIComponent(key)}`);
  const carrying = (items ?? []).filter((p) => !p.redacted).map((p) => [p.id, (p.findings ?? []).filter((f) => f.rule === 'lifecycleConflict').length])
    .filter(([, n]) => n > 0);
  check(JSON.stringify(carrying.map(([id]) => id).sort()) === JSON.stringify(expected)
    && carrying.every(([id, n]) => n === seeded.filter((d) => d.item === id).length),
  `product ${key}: the parts carrying lifecycleConflict are its ${expected.length} seeded item(s)`, JSON.stringify(carrying));
  const counted = (products ?? []).find((p) => p.key === key)?.lifecycleConflicts;
  check(counted === seeded.length, `product ${key}: the products answer counts its ${seeded.length} lifecycle conflict(s)`, String(counted));
}

// 4g. Paths through a product. The drives relation is the layer's own: the cart's left drive spring reaches both drive
// wheels, across the mesh no interface records and through the seeded interfaces, which carry their rule status; the wind
// turbine's gear train from the hub gives the design ratio its product file records from the tooth counts, and the
// high-speed pinion recorded with another module is a meshModule finding.
{
  const cart = datasets.find((d) => d.product.key === 'cart');
  const flow = await get('/api/query/flow?product=cart&from=FR3101');
  const reached = (flow.parts ?? []).map((p) => p.id);
  check(['ES-3102', 'ES-3103'].every((w) => reached.includes(w)), "the cart's left drive spring drives both wheels", reached.join(','));
  const seeded = new Set(cart.seededDefects.map((d) => d.interface));
  const crossed = (flow.steps ?? []).filter((s) => s.joint && seeded.has(s.joint.id));
  check(crossed.length > 0 && crossed.every((s) => s.joint.status === 'fail'),
    'the flow crosses the seeded interfaces, each failing', crossed.map((s) => `${s.joint.id} ${s.joint.status}`).join(','));
  const turbine = datasets.find((d) => d.product.key === 'wind-turbine');
  const design = Number(turbine.extended.drivetrainChain.gearboxRatio.ratioFromTeeth);
  const train = await get('/api/query/flow?product=wind-turbine&from=ES-3701&flow=mechanical');
  check(Math.abs((train.ratio?.speedUp ?? 0) - design) < 0.005, "the wind turbine's gear ratio from the hub is the design ratio",
    `${train.ratio?.speedUp} vs ${design}`);
  const pinion = (train.parts ?? []).find((p) => p.id === 'D-37031');
  check((pinion?.findings ?? []).some((f) => f.rule === 'meshModule'), 'the high-speed pinion carries its meshModule finding',
    JSON.stringify(pinion?.findings));
  const paths = await get('/api/query/paths?product=cart&from=D-31003&to=UK-3102');
  check((paths.paths ?? []).length === 2 && paths.paths.every((p) => p.length === 5), 'two shortest interface paths join the pinion and the nameplate',
    JSON.stringify((paths.paths ?? []).map((p) => p.steps.map((s) => s.joint?.id))));
}

// 4h. Placements. The rover's wheel is one part number used at six places along both sides; the wind turbine's blade
// FR3771 is used three times on the hub, so each part one blade holds once is drawn three times.
{
  const WHEEL = 'FR3801';
  const BLADE = 'FR3771';
  const occurrences = (answer, id) => (answer.parts ?? []).find((p) => p.id === id)?.occurrences ?? [];
  const identityFirst = (list) => list.length > 0 && list[0].every((v) => v === 0);
  const rover = await get('/api/query/placements?product=rover');
  const wheels = occurrences(rover, WHEEL);
  check(wheels.length === 6 && identityFirst(wheels), `the rover's wheel ${WHEEL} has six occurrences, the reference first`, JSON.stringify(wheels));
  check(rover.occurrences === (rover.parts ?? []).reduce((n, p) => n + p.occurrences.length, 0), 'the rover placements total is the sum of its parts', String(rover.occurrences));
  const lines = datasetOf['wind-turbine'].extended.bomLines;
  const onBlade = lines.filter((l) => l.parent === BLADE || lines.some((m) => m.parent === BLADE && m.child === l.parent));
  const once = onBlade.filter((l) => (l.placements?.length ?? l.quantity) === 1 && !lines.some((m) => m.parent === l.child)).map((l) => l.child);
  const turbine = await get('/api/query/placements?product=wind-turbine');
  const counts = once.map((id) => [id, occurrences(turbine, id).length]);
  check(counts.length > 0 && counts.every(([, n]) => n === 3), `every part one blade ${BLADE} holds once has three occurrences`, JSON.stringify(counts));
}

// 4i. Configured answers. For every option of every variant group other than the group's default, the parts the parts
// answer lists under option=<code> are the configuration's items, and the placements answer draws each of its
// geometric parts as many times as the configuration's lines place it: what the viewer redraws on a variant switch.
for (const [d, group, option] of datasets.flatMap((x) => Object.values(x.extended?.variants ?? {})
  .flatMap((g) => Object.keys(g.options).filter((k) => k !== g.default).map((o) => [x, g, o])))) {
  const product = d.product.key;
  const config = configuration(d, option);
  const geometric = new Set(seededItems(d).filter((i) => d.parts.includes(i) || (group.options[option].parts ?? []).includes(i))
    .filter((p) => (p.extended?.type ?? 'PART') === 'PART').map((p) => p.id));
  const listed = (await get(`/api/query/parts?product=${product}&option=${option}`)).parts.map((p) => p.id).sort();
  const expected = [...config.items].sort();
  check(JSON.stringify(listed) === JSON.stringify(expected), `${product} under ${option}: the parts answer lists the configuration's ${expected.length} items`,
    `only listed ${listed.filter((id) => !config.items.has(id)).join(',') || '-'}; missing ${expected.filter((id) => !listed.includes(id)).join(',') || '-'}`);
  const placed = Object.fromEntries((await get(`/api/query/placements?product=${product}&option=${option}`)).parts.map((p) => [p.id, p.occurrences.length]));
  const drawn = Object.fromEntries(expected.filter((id) => geometric.has(id)).map((id) => [id, config.occurrences.get(id)]));
  const wrong = [...new Set([...Object.keys(placed), ...Object.keys(drawn)])].filter((id) => placed[id] !== drawn[id]);
  check(wrong.length === 0 && Object.keys(drawn).length > 0,
    `${product} under ${option}: the placements answer draws its ${Object.keys(drawn).length} geometric parts at ${Object.values(drawn).reduce((a, b) => a + b, 0)} occurrences`,
    wrong.slice(0, 8).map((id) => `${id} ${placed[id] ?? 0} against ${drawn[id] ?? 0}`).join(', '));
}

// 5. MCP over streamable HTTP: initialize, then tools/list. A server answers a
// request as JSON or as one SSE event; a notification gets an empty 202.
const MCP_TOOLS = ['bom', 'bom_where_used', 'catalogue', 'equivalent_parts', 'evidence', 'export_status', 'external_references', 'find_parts', 'find_term', 'flow_path',
  'impact_of_change', 'interface_check', 'list_interfaces', 'ontology', 'parts', 'parts_between_stations', 'path_between', 'preview_correction', 'products', 'section_joints', 'sparql', 'sql',
  'suppliers', 'variant_diff', 'where_used'];
let mcpSession;
const mcp = async (method, params, id) => {
  const res = await fetch(`${API}/api/query/mcp`, {
    method: 'POST',
    headers: {
      ...headers(CLEARED),
      'content-type': 'application/json',
      accept: 'application/json, text/event-stream',
      ...(mcpSession ? { 'mcp-session-id': mcpSession } : {}),
    },
    body: JSON.stringify({ jsonrpc: '2.0', method, params, ...(id === undefined ? {} : { id }) }),
  });
  mcpSession = res.headers.get('mcp-session-id') ?? mcpSession;
  const text = await res.text();
  if (!res.ok) throw new Error(`mcp ${method}: ${res.status} ${text}`);
  if (id === undefined) return undefined;
  const messages = res.headers.get('content-type')?.includes('text/event-stream')
    ? text.split('\n').filter((l) => l.startsWith('data:')).map((l) => JSON.parse(l.slice(5)))
    : [JSON.parse(text)];
  return messages.find((m) => m.id === id);
};
const init = await mcp('initialize', { protocolVersion: '2025-06-18', capabilities: {}, clientInfo: { name: 'smoke', version: '0' } }, 1);
check(init?.result?.serverInfo !== undefined, 'MCP initialize answers with server info', JSON.stringify(init?.error ?? init?.result?.serverInfo));
await mcp('notifications/initialized', {});
const listed = await mcp('tools/list', {}, 2);
const toolNames = (listed?.result?.tools ?? []).map((t) => t.name).sort();
check(JSON.stringify(toolNames) === JSON.stringify(MCP_TOOLS), 'MCP tools/list returns the twenty-five contract tools', toolNames.join(',') || JSON.stringify(listed?.error));

// 6. Demo controls, each on the service that owns the fact (docs/contract.md,
// "Freshness and the change feed"): one value correction inside the PLM of the
// outlier plug of the position defect as its own engineer (the plug moves onto
// the joint plane; the PLM publishes part.value.corrected and the links loader
// records it in Atelier's change log), one link written synchronously by the core
// service as programme-cleared (the orphan plug to the spare plug of the other
// part of its interface), one CAD publication by the PLM of the cadPending part
// reaching the file index through the event, one equivalence confirmed by the core
// service as programme-cleared (owl:sameAs between the part numbers of one purchased
// item), the derived change list, and the officer's reset on the core service back
// to the released tallies. Every control refuses the other profiles before doing
// anything, and the query service accepts no write.
if (LOCAL) {
  skip('demo controls', 'their events reach the change log and the file index through EventBridge and the loader Lambda');
} else {
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const send = async (method, path, body, profile = CLEARED) => {
    let res;
    for (let attempt = 0; attempt < 8; attempt++) {
      res = await fetch(API + path, {
        method,
        headers: { ...headers(profile), ...(body ? { 'content-type': 'application/json' } : {}) },
        body: body ? JSON.stringify(body) : undefined,
      });
      if (![502, 503, 504].includes(res.status)) break;
      await sleep(10000);
    }
    const text = await res.text();
    let parsed;
    try { parsed = JSON.parse(text); } catch { parsed = text; }
    return { status: res.status, ok: res.ok, body: parsed };
  };
  const plmOf = (feature) => partById[feature.part].plm.toLowerCase();
  // Feature IRIs as the R2RML templates mint them: https://example.com/atelier/<plm>/plug/<id>.
  const featureIri = (feature) => `https://example.com/atelier/${plmOf(feature)}/plug/${encodeURIComponent(feature.id)}`;
  const PLMS = ['fr', 'de', 'uk', 'es'];
  const otherOwner = (plm) => `${PLMS.find((p) => p !== plm)}-engineer`;

  // The two plugs of the position defect. The outlier is the one off its own part's
  // other features on the defect axis (they sit on the joint plane); its mate's
  // coordinate is the value to release.
  const offPlane = (plug) => {
    const own = ['pairs', 'fasteners', 'couplings'].flatMap((kind) => positionInterface[kind] ?? []).flat()
      .filter((f) => f.part === plug.part && f.id !== plug.id);
    return own.some((f) => Math.abs(mm(f, axis) - mm(plug, axis)) > 0.01);
  };
  const outlier = positionPair.find(offPlane) ?? positionPair[1];
  const joint = positionPair.find((f) => f !== outlier);
  if (!positionPair.some(offPlane)) console.log(`note  neither plug of ${positionDefect.interface} is off its part's other features; taking ${outlier.id} as the outlier`);
  // Hidden parts are never named: an interface whose parts are hidden from the profile
  // carries no part name in its label.
  const deView = (await get('/api/query/interfaces', 'de-engineer')).interfaces;
  for (const iface of dataset.interfaces.filter((i) => hiddenFor('de-engineer').includes(keyOf(i)))) {
    const live = deView.find((i) => keyOf(i) === keyOf(iface));
    const hiddenNames = iface.parts.map((id) => partById[id]).filter((p) => !releasableTo('de-engineer', p)).map((p) => p.name);
    const leaks = hiddenNames.filter((name) => String(live?.label ?? '').includes(name));
    check(live !== undefined && leaks.length === 0, `${iface.id} label names no hidden part for de-engineer`, `${live?.label} leaks ${JSON.stringify(leaks)}`);
  }

  // The PLM of the outlier plug releases the correction, as its own engineer, at
  // its own POST /{plm}/demo/update (the PLM is the path, not the body). Its plug
  // table and position columns, mm: FR connecteur, DE stecker, ES conector.
  const PLUG_TABLES = { fr: 'connecteur', de: 'stecker', es: 'conector' };
  const PLM = plmOf(outlier);
  if (!PLUG_TABLES[PLM]) throw new Error(`no mm plug table for the ${PLM.toUpperCase()} PLM of the outlier ${outlier.id}`);
  const correction = { table: PLUG_TABLES[PLM], key: outlier.id, column: `pos_${axis}_mm`, value: joint.position[axis], purpose: `smoke: ${PLM.toUpperCase()} PLM releases ${outlier.id} onto the joint plane` };
  const OWNER = `${PLM}-engineer`;
  console.log(`data  correction: ${PLM.toUpperCase()} ${correction.table}.${correction.column} of ${correction.key} ${outlier.position[axis]} -> ${correction.value} (the ${plmOf(joint).toUpperCase()} mate ${joint.id})`);
  const rowValue = async () => {
    const table = await get(`/api/${PLM}/tables/${correction.table}?keys=${encodeURIComponent(correction.key)}`);
    return table.rows[0]?.[table.columns.indexOf(correction.column)];
  };

  // The orphan plug; its candidate mate is the unmated plug of the other part at the
  // same position, released as a spare in data/products and reported by the query
  // service on the orphan violation.
  const orphanDefect = dataset.seededDefects.find((d) => d.product === DEMO_PRODUCT && d.rule === 'orphan');
  const orphanInterface = interfaceOf(orphanDefect);
  const orphanPlug = orphanInterface.unmatedPlugs.find((f) => f.id === orphanDefect.features[0]);
  const sparePlug = dataset.sparePlugs.find((f) => orphanInterface.parts.includes(f.part) && f.part !== orphanPlug.part);
  if (!sparePlug) throw new Error(`no spare plug on the other part of ${orphanDefect.interface}`);
  const link = { from: featureIri(orphanPlug), to: featureIri(sparePlug) };
  console.log(`data  link: ${link.from} -> ${link.to}`);

  // The equivalence to confirm: a proposed group of the demo product (part numbers of several sites that are one
  // purchased item, not yet confirmed), or of the first product that has one; its members' IRIs as the web posts them.
  const equivalentsOf = async (product) => (await get(`/api/query/equivalents?product=${encodeURIComponent(product)}`)).groups ?? [];
  let equivalenceProduct;
  let proposed;
  for (const key of [DEMO_PRODUCT, ...datasets.map((d) => d.product.key).filter((k) => k !== DEMO_PRODUCT)]) {
    proposed = (await equivalentsOf(key)).find((g) => !g.confirmed && g.members.length >= 2);
    if (proposed) { equivalenceProduct = key; break; }
  }
  if (!proposed) throw new Error('no proposed equivalence group in any product');
  const equivalence = { parts: proposed.members.map((m) => m.iri) };
  const sameMembers = (g) => proposed.members.every((m) => g.members.some((x) => x.id === m.id));
  const pairs = equivalence.parts.length * (equivalence.parts.length - 1);
  console.log(`data  equivalence: ${equivalenceProduct} ${proposed.itemClass} ${proposed.members.map((m) => m.id).join(' = ')} (${pairs} owl:sameAs triples once confirmed)`);

  // The part released without a file-index entry, and the CAD publication that adds it.
  const pending = demoPendingPart(datasets, DEMO_PRODUCT);
  if (!pending) throw new Error(`no cadPending part in the ${DEMO_PRODUCT} product file`);
  const cad = { plm: pending.plm.toLowerCase(), part: pending.id, cadFile: pending.cadFile };
  const CAD_OWNER = `${cad.plm}-engineer`;
  console.log(`data  CAD publication: ${cad.plm.toUpperCase()} ${cad.part} ${cad.cadFile} as ${CAD_OWNER}`);
  const pendingPart = async () => (await get('/api/query/parts')).parts.find((p) => p.id === cad.part);

  const changes = () => get('/api/query/demo/changes', OWNER);
  const graphTallies = (t) => JSON.stringify({ links: Number(t?.links), fileindex: Number(t?.fileindex) });
  const dirty = (c) => c.values.length > 0 || c.graph.added.length > 0 || c.graph.removed.length > 0;

  // Who may act follows who owns the fact: refused profiles per endpoint.
  const cadBody = { part: cad.part, cadFile: cad.cadFile };
  for (const [method, path, body, profile] of [
    ['POST', `/api/${PLM}/demo/update`, correction, otherOwner(PLM)],
    ['POST', `/api/${PLM}/demo/update`, correction, 'programme-cleared'],
    ['POST', `/api/${cad.plm}/demo/events/cad`, cadBody, otherOwner(cad.plm)],
    ['POST', '/api/core/links', link, OWNER],
    ['POST', '/api/core/equivalences', equivalence, OWNER],
    ['POST', '/api/core/demo/reset', undefined, 'programme-cleared'],
    ['DELETE', '/api/core/changes', undefined, 'programme-cleared'],
    ['POST', '/api/core/graphs/reset', undefined, 'programme-cleared'],
  ]) {
    const res = await send(method, path, body, profile);
    check(res.status === 403, `${method} ${path} refuses ${profile}`, `status ${res.status}`);
  }
  // A body naming one part is the core service's 400 (bad-parts): the route reaches the service.
  const onePart = await send('POST', '/api/core/equivalences', { parts: equivalence.parts.slice(0, 1) }, 'programme-cleared');
  check(onePart.status === 400 && onePart.body?.reason === 'bad-parts', 'POST /api/core/equivalences refuses a single part with the service\'s 400', `status ${onePart.status} ${JSON.stringify(onePart.body)}`);
  // The query service only reads: it has no write control, even for the officer.
  for (const [path, body] of [
    ['/api/query/demo/change', { plm: PLM, ...correction }],
    ['/api/query/demo/publish-link', link],
    ['/api/query/demo/publish-cad', cad],
    ['/api/query/demo/reset', undefined],
  ]) {
    const res = await send('POST', path, body);
    check([404, 405].includes(res.status), `POST ${path} does not exist`, `status ${res.status}`);
  }
  for (const [method, path] of [['GET', '/api/query/demo/changes'], ['GET', '/api/core/changes']]) {
    const res = await send(method, path, undefined, 'unknown');
    check(res.status === 403, `${method} ${path} refuses the unknown profile`, `status ${res.status}`);
  }

  // Both images bundle the released graphs: the query service to diff the live
  // graphs, the core service to restore them. Their health endpoints (no role
  // check, no data) must agree on the released links tally.
  const queryHealth = await send('GET', '/api/query/demo/health', undefined, 'programme-cleared');
  const coreHealth = await send('GET', '/api/core/graphs/health', undefined, 'programme-cleared');
  check(queryHealth.ok && coreHealth.ok && Number(queryHealth.body?.releasedGraphs?.links) > 0
    && Number(coreHealth.body?.releasedGraphs?.links) === Number(queryHealth.body?.releasedGraphs?.links),
    'core graphs/health reports the released links tally of the query health',
    `core ${coreHealth.status} ${JSON.stringify(coreHealth.body?.releasedGraphs)} vs query ${queryHealth.status} ${JSON.stringify(queryHealth.body?.releasedGraphs)}`);

  let initial = await changes();
  if (dirty(initial)) {
    console.log('note  demo data was not at the released state (an earlier run stopped early); resetting first');
    await send('POST', '/api/core/demo/reset');
    initial = await changes();
  }
  check(!dirty(initial), 'demo change list starts empty', JSON.stringify(initial));
  const released = initial.graph.triples;
  check(Number(released?.links) > 0 && Number(released?.fileindex) > 0, 'released graph tallies are counted', graphTallies(released));
  check(Number(await rowValue()) === Number(outlier.position[axis]), `native ${PLM.toUpperCase()} row ${correction.key} starts at ${axis} ${outlier.position[axis]}`);
  const pendingBefore = await pendingPart();
  check(pendingBefore !== undefined && pendingBefore.cadUrl == null, `${cad.part} starts without a CAD URL (cadPending)`, JSON.stringify(pendingBefore));
  const orphanBefore = await get(`/api/query/interfaces/${orphanDefect.interface}${inProduct(orphanDefect)}`);
  const orphanViolation = (orphanBefore.violations ?? orphanBefore.interface?.violations ?? []).find((v) => v.rule === 'orphan');
  const candidates = orphanViolation?.candidateMates ?? orphanViolation?.detail?.candidateMates ?? [];
  check(candidates.length === 1 && candidates[0].iri === link.to,
    `${orphanDefect.interface} reports the spare plug ${sparePlug.id} as the orphan's only candidate mate`, JSON.stringify(candidates));

  try {
    // The PLM runs the UPDATE in its own database and announces it; the response
    // carries the id of the part.value.corrected event.
    const changed = await send('POST', `/api/${PLM}/demo/update`, correction, OWNER);
    const [row] = changed.body?.rows ?? [];
    check(changed.ok && Number(row?.before) === Number(outlier.position[axis]) && Number(row?.after) === Number(correction.value)
      && typeof changed.body?.eventId === 'string' && changed.body.eventId.length > 0,
      `${PLM}/demo/update as ${OWNER} moves ${PLM.toUpperCase()} ${correction.table}.${correction.column} of ${correction.key} from ${outlier.position[axis]} to ${correction.value} and publishes the event`, `status ${changed.status} ${JSON.stringify(changed.body)}`);
    check(Number(await rowValue()) === Number(correction.value), `native ${PLM.toUpperCase()} row shows the corrected ${axis}`);
    const corrected = (await get('/api/query/interfaces')).interfaces.find((i) => keyOf(i) === defectKey(positionDefect));
    check(corrected?.status === 'pass', `${positionDefect.interface} passes after the correction`, `${corrected?.status} ${JSON.stringify(corrected?.violations)}`);
    // The correction reaches Atelier's change log through EventBridge and the links
    // loader (POST /core/changes): asynchronous, up to 60 s.
    const isCorrection = (v) => v.key === correction.key && v.column === correction.column;
    let list = await changes();
    for (let i = 0; i < 12 && !list.values.some(isCorrection); i++) {
      await sleep(5000);
      list = await changes();
    }
    const recorded = list.values.find(isCorrection);
    check(recorded !== undefined && recorded.actor === OWNER, `the change list shows the correction with ${OWNER} as actor, via the event`, JSON.stringify(list.values));

    // The link is written by the core service before it answers: the response
    // carries the new links tally and the id of the event published.
    const published = await send('POST', '/api/core/links', link, 'programme-cleared');
    check(published.ok && Number(published.body?.triples?.links) === Number(released.links) + 2
      && typeof published.body?.eventId === 'string' && published.body.eventId.length > 0,
      `core/links as programme-cleared writes ${orphanPlug.id} -> ${sparePlug.id} synchronously and returns the links tally and an event id`, `status ${published.status} ${JSON.stringify(published.body)}`);
    // The change list shortens IRIs to native ids; compare on the decoded last path segment.
    const idOf = (x) => decodeURIComponent(String(x).split('/').pop());
    const linkPairs = (added) => new Set(added.filter((t) => String(t.p).endsWith('matesWith')).map((t) => `${idOf(t.s)} ${idOf(t.o)}`));
    const hasLink = (added) => linkPairs(added).has(`${idOf(link.from)} ${idOf(link.to)}`) && linkPairs(added).has(`${idOf(link.to)} ${idOf(link.from)}`);
    list = await changes();
    check(list.graph.added.length === 2 && hasLink(list.graph.added), 'the published link shows at once as the two added matesWith triples', JSON.stringify(list.graph.added));
    const linked = (await get('/api/query/interfaces')).interfaces.find((i) => keyOf(i) === defectKey(orphanDefect));
    check(linked?.status === 'pass', `${orphanDefect.interface} passes once the spare plug mates the orphan`, `${linked?.status} ${JSON.stringify(linked?.violations)}`);

    const cadPublished = await send('POST', `/api/${cad.plm}/demo/events/cad`, cadBody, CAD_OWNER);
    check(cadPublished.ok && typeof cadPublished.body?.eventId === 'string' && cadPublished.body.eventId.length > 0,
      `${cad.plm}/demo/events/cad as ${CAD_OWNER} publishes ${cad.part} ${cad.cadFile}`, `status ${cadPublished.status} ${JSON.stringify(cadPublished.body)}`);
    // The event goes through EventBridge and the loader Lambda: asynchronous, up to 60 s.
    const isCadTriple = (t) => String(t.p).endsWith('cadFile') && String(t.o) === cad.cadFile && idOf(t.s) === cad.part;
    for (let i = 0; i < 12 && !list.graph.added.some(isCadTriple); i++) {
      await sleep(5000);
      list = await changes();
    }
    const added = list.graph.added;
    check(added.some(isCadTriple), `the CAD publication shows as an added cadFile triple for ${cad.part}`, JSON.stringify(added));
    check(added.length === 3 && hasLink(added), 'the change list holds exactly the two link triples and the one CAD triple', JSON.stringify(added));
    check(list.graph.removed.length === 0, 'no triple removed from the graphs', JSON.stringify(list.graph.removed));
    check(Number(list.graph.triples.links) === Number(released.links) + 2 && Number(list.graph.triples.fileindex) === Number(released.fileindex) + 1,
      'the links tally grew by two and the file index by one', `${graphTallies(released)} -> ${graphTallies(list.graph.triples)}`);
    check(list.values.length === 1 && list.values[0].key === correction.key && list.values[0].column === correction.column && list.values[0].actor === OWNER,
      'the change list holds the one value correction', JSON.stringify(list.values));
    const pendingAfter = await pendingPart();
    const served = await servesStep(pendingAfter?.cadUrl);
    check(served.ok, `${cad.part} now carries a presigned CAD URL that serves ${cad.cadFile}`, `${served.detail} ${JSON.stringify(pendingAfter)}`);
    const partsAfter = (await get('/api/query/parts')).parts;
    // Only the published part's finding clears: any other part the files release without a CAD file keeps its own.
    const listed = new Set(partsAfter.filter((p) => !p.redacted).map((p) => p.id));
    const missingAfter = partsAfter.filter((p) => (p.findings ?? []).some((f) => f.rule === 'cadMissing')).map((p) => p.id).sort();
    const stillPending = cadMissingAfter(datasets, cad.part).filter((id) => listed.has(id));
    check(JSON.stringify(missingAfter) === JSON.stringify(stillPending),
      `${cad.part}'s cadMissing finding clears once its CAD is published; ${stillPending.length ? stillPending.join(', ') : 'no other part'} keeps one`, missingAfter.join(', '));

    // The equivalence is written by the core service before it answers: owl:sameAs between every two of the parts
    // in the links graph, the new links tally and the id of the event published; the query service reads the
    // group back as confirmed and the change list shows the triples.
    const confirmed = await send('POST', '/api/core/equivalences', equivalence, 'programme-cleared');
    check(confirmed.ok && Number(confirmed.body?.triples?.links) === Number(released.links) + 2 + pairs
      && typeof confirmed.body?.eventId === 'string' && confirmed.body.eventId.length > 0,
      `core/equivalences as programme-cleared confirms ${proposed.members.map((m) => m.id).join(' = ')} synchronously (${pairs} sameAs triples) and returns the links tally and an event id`,
      `status ${confirmed.status} ${JSON.stringify(confirmed.body)}`);
    const groupConfirmed = (await equivalentsOf(equivalenceProduct)).find(sameMembers);
    check(groupConfirmed?.confirmed === true, `product ${equivalenceProduct}: the ${proposed.itemClass} group reads back as confirmed`, JSON.stringify(groupConfirmed));
    list = await changes();
    // The change list writes owl:sameAs as a full IRI in angle brackets and the sample's own predicates as prefixed names.
    const sameAs = list.graph.added.filter((t) => /sameAs>?$/.test(String(t.p)));
    check(sameAs.length === pairs && list.graph.added.length === 3 + pairs && Number(list.graph.triples.links) === Number(released.links) + 2 + pairs,
      `the change list holds the ${pairs} sameAs triples beside the link and CAD triples, and the links tally grew by them`, JSON.stringify(list.graph.added));
  } catch (err) {
    check(false, 'demo sequence ran to the end', String(err));
  } finally {
    // The core service replays the logged corrections through the PLM services
    // (their inverse events carry a reset: purpose the loader ignores), restores
    // the released graphs and deletes the log.
    const reset = await send('POST', '/api/core/demo/reset');
    check(reset.ok, 'core/demo/reset accepted (export-officer)', `status ${reset.status} ${JSON.stringify(reset.body)}`);
    const final = await changes();
    check(!dirty(final) && graphTallies(final.graph.triples) === graphTallies(released), 'after reset the change list is empty and the tallies are the released ones',
      `${graphTallies(final.graph.triples)} vs released ${graphTallies(released)}; ${JSON.stringify({ values: final.values, added: final.graph.added, removed: final.graph.removed })}`);
    await sleep(5000);
    const settled = (await changes()).values;
    check(settled.length === 0, 'the inverse corrections of the reset are not logged (purpose reset:)', JSON.stringify(settled));
    check(Number(await rowValue()) === Number(outlier.position[axis]), `native ${PLM.toUpperCase()} row ${correction.key} is back at ${axis} ${outlier.position[axis]}`);
    const pendingReset = await pendingPart();
    check(pendingReset !== undefined && pendingReset.cadUrl == null, `after reset ${cad.part} has no CAD URL again`, JSON.stringify(pendingReset));
    // The released links hold no owl:sameAs: the reset's PUT of them removes the confirmation.
    const groupReset = (await equivalentsOf(equivalenceProduct)).find(sameMembers);
    check(groupReset !== undefined && groupReset.confirmed === false, `after reset the ${proposed.itemClass} group of ${equivalenceProduct} is a proposal again`, JSON.stringify(groupReset));
    const again = (await get('/api/query/interfaces')).interfaces;
    const failingAgain = Object.fromEntries(again.filter((i) => i.status === 'fail').map((i) => [keyOf(i), rules(i.violations)]));
    check(canon(failingAgain) === canon(expected), 'after reset the failing interfaces are the seeded defects again', JSON.stringify(failingAgain));
  }
}

// The preview of every rule's correction, written nowhere.
await previewCycle({ base: `${API}/api`, headers: { 'x-origin-verify': SECRET }, check });

// The release, pass, reset, fail cycle of every rule, each release reaching the change log through the event.
// Locally no event reaches a loader: the fix cycle records each release as the links loader does.
await fixCycle({ base: `${API}/api`, headers: { 'x-origin-verify': SECRET }, check, loader: LOCAL ? 'stand-in' : 'live' });

// 7. The live agent, through the edge sign-in like the browser. A run is one SSE stream of AG-UI events.
const agentRun = async (question, forwardedProps) => {
  const body = { threadId: `smoke-${Date.now()}`, runId: 'r1', messages: [{ id: 'u1', role: 'user', content: question }], tools: [], context: [], state: {}, forwardedProps };
  const res = await fetch(`${SITE}/agent/invocations`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', accept: 'text/event-stream', cookie: `atelier.id=${process.env.AGENT_ID_TOKEN}`, 'x-atelier-profile': CLEARED },
    body: JSON.stringify(body),
  });
  const events = res.ok ? (await res.text()).split('\n').filter((l) => l.startsWith('data: ')).map((l) => JSON.parse(l.slice(6))) : [];
  const args = new Map();
  for (const e of events) {
    if (e.type === 'TOOL_CALL_START') args.set(e.toolCallId, { name: e.toolCallName, json: '' });
    if (e.type === 'TOOL_CALL_ARGS') args.get(e.toolCallId).json += e.delta;
  }
  const calls = [...args.values()].map((c) => ({ name: c.name, args: c.json ? JSON.parse(c.json) : {} }));
  const text = events.filter((e) => e.type === 'TEXT_MESSAGE_CONTENT').map((e) => e.delta).join('');
  return { status: res.status, calls, text, error: events.find((e) => e.type === 'RUN_ERROR')?.message };
};
if (LOCAL) {
  skip('agent checks', 'the agent runs behind the deployment\'s edge sign-in');
} else if (!process.env.AGENT_ID_TOKEN) {
  console.log('SKIP  agent checks: AGENT_ID_TOKEN (a signed-in atelier.id cookie value) is not set');
} else {
  const turbine = datasetOf['wind-turbine'];
  const gearbox = (turbine.extended?.assemblies ?? []).find((a) => a.nameEn === 'Gearbox');
  const product = { key: turbine.product.key, name: turbine.product.name };
  const shown = await agentRun('Show me the gearbox of the wind turbine', { product });
  const opened = shown.calls.find((c) => c.name === 'open_subtree');
  check(opened?.args.root === gearbox.id, `the agent asked for the gearbox calls open_subtree with ${gearbox.id}`,
    `status ${shown.status} ${shown.error ?? ''} ${JSON.stringify(shown.calls)}`);
  const part = turbine.parts.find((p) => p.plm === 'DE');
  const selection = { product: product.key, part: { id: part.id, plm: part.plm.toLowerCase(), name: part.name }, interface: null };
  const named = await agentRun('What is this part?', { product, selection });
  check(named.text.includes(part.id) || named.text.includes(part.name), `asked "what is this part" with ${part.id} selected, the agent names it`,
    `status ${named.status} ${named.error ?? ''} ${named.text.slice(0, 200)}`);
}

// 8. The served bundle, through the edge sign-in like the browser, against the build this checkout deploys.
const dist = new URL('../modules/web/dist/', import.meta.url);
if (LOCAL) {
  skip('served bundle', 'the site is the deployment\'s');
} else if (!process.env.AGENT_ID_TOKEN) {
  skip('served bundle', 'AGENT_ID_TOKEN (a signed-in atelier.id cookie value) is not set');
} else if (!existsSync(new URL('index.html', dist))) {
  skip('served bundle', 'modules/web/dist is not built in this checkout');
} else {
  const fetchSite = (rel) => fetch(`${SITE}/${rel}?cb=${Date.now()}`, { headers: { cookie: `atelier.id=${process.env.AGENT_ID_TOKEN}` } });
  const served = await (await fetchSite('index.html')).text();
  const local = readFileSync(new URL('index.html', dist), 'utf8');
  check(served === local, 'the site serves the index.html of modules/web/dist', `${served.length} bytes served, ${local.length} local`);
  const sha = (b) => createHash('sha256').update(b).digest('hex');
  for (const asset of [...new Set([...local.matchAll(/\/(assets\/[^"']+\.(?:js|css))/g)].map((m) => m[1]))]) {
    const bytes = Buffer.from(await (await fetchSite(asset)).arrayBuffer());
    check(sha(bytes) === sha(readFileSync(new URL(asset, dist))), `the site serves ${asset} as built`, `${bytes.length} bytes served`);
  }
}

console.log(failures ? `\n${failures} check(s) failed` : '\nall checks passed');
process.exit(failures ? 1 : 0);
