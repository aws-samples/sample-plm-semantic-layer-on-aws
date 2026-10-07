// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Local development fixtures, loaded only when the dev server runs with
// VITE_FIXTURES=1. Built to the contract's response shapes over six products
// of data/products/*.json (the ornithopter with its seeded interface defects,
// the aerial screw, the wind turbine with a deep bill of materials, the rover
// with its suppliers, the CubeSat over its mass limit, the difference engine
// whose French masses are entered in grams), with per-profile export control
// and the Atelier core store. Never part of a production build.
import type { Bom, Call, Catalogue, EquivalentsResponse, InterfaceResponse, InterfacesResponse, Part, PartEntry, PartsResponse, Placements, Policy, ProductListResponse, ProductsResponse, ReferencesResponse, SuppliersResponse, TagsResponse, VariantDiff, VariantGroups } from '../api/types';
import { isPart } from '../api/types';
import { assemblyParts, bomOf } from './fixture-bom';
import { catalogues } from './fixture-catalogue';
import { coreMapping, tagsFor } from './fixture-core';
import { demoChanges, demoHealth, demoMutate } from './fixture-demo';
import { coreTableRows, evidenceFor, partIri, PLMS, tableRows } from './fixture-evidence';
import { mcpTools } from './fixture-mcp-tools';
import { equivalentsOf, suppliersOf, withOffers } from './fixture-purchasing';
import { policyFor, redactInterface, redactParts } from './fixture-policy';
import { referencesOf } from './fixture-references';
import { failuresFor, productFailuresFor } from './fixture-rules';
import rules from './fixture-rules.json';
import { flowFor, pathsFor, withMeshFindings } from './fixture-paths';
import type { FlowKind } from '../api/pathTypes';
import { contextCount, interfacesIn, partsIn, rootedAt, subtreeBlock, subtreeBom, type Rooted } from './fixture-subtree';
import { interfacesOf, listing, partsOf, productsFor } from './fixture-products';
import { parts, products } from './fixture-seed';
import { configurationOf, configuredParts } from './fixture-configuration';
import { placementsOf } from './fixture-placements';
import { variantDiffOf, variantGroupsOf } from './fixture-variants';
import { FIXTURE_MARKER } from './marker';
import frMapping from '../../../ontop/mappings/fr.r2rml.ttl?raw';
import deMapping from '../../../ontop/mappings/de.r2rml.ttl?raw';
import ukMapping from '../../../ontop/mappings/uk.r2rml.ttl?raw';
import esMapping from '../../../ontop/mappings/es.r2rml.ttl?raw';

const mappings: Record<string, string> = { fr: frMapping, de: deMapping, uk: ukMapping, es: esMapping, core: coreMapping };

// SPARQL is written one line per array element: a line with a literal group pattern is a plain string.
const arm = (plm: string) => [
  '  UNION { SERVICE <cache:bulk+20:http://ontop-' + plm + ':8080/sparql> { ?f ?fp ?fo ; atelier:onPart ?part . ?part ?tp ?to .',
  '            OPTIONAL { ?f ?axis ?q . ?q ?qp ?qo } } }',
].join('\n');

const sparqlFor = (pol: Policy) => [
  `# ${FIXTURE_MARKER}`,
  'PREFIX atelier: <https://example.com/atelier/ontology#>',
  'PREFIX qudt: <http://qudt.org/schema/qudt/>',
  'CONSTRUCT { ?i ?ip ?io . ?f ?fp ?fo . ?q ?qp ?qo . ?part ?tp ?to . ?part atelier:cadFile ?cad ; atelier:builtBy ?builder }',
  'WHERE {',
  '  SERVICE <http://neptune:8182/sparql> {',
  '    GRAPH <https://example.com/atelier/graph/links> { ?i a atelier:Interface ; ?ip ?io ; atelier:declaresFeature ?f ; atelier:betweenPart ?part }',
  '    GRAPH <https://example.com/atelier/graph/fileindex> { ?part atelier:cadFile ?cad OPTIONAL { ?part atelier:builtBy ?builder } } }',
  "  # Tags first: the FILTER is pushed into atelier_core's SQL and only visible ?part survive into the PLM arms.",
  '  SERVICE <cache:http://ontop-core:8080/sparql> { ?part atelier:jurisdiction ?jur ; atelier:releasableTo ?rel ; atelier:taggedBy ?by . ' + pol.filter + ' }',
  PLMS.map(arm).join('\n').replace(/^ {2}UNION /, '  '),
  '}',
].join('\n');

/** One call per source, the tag arm first; a PLM with no visible part on the request is listed with 0 requests. */
export function calls(involved: string[], scale: number): Call[] {
  const core: Call = { endpoint: 'ontop-core', kind: 'virtual', requests: 1, triples: Math.round(40 * scale), ms: 18 };
  const virtual = PLMS.map((plm, i): Call =>
    involved.includes(plm)
      ? { endpoint: `ontop-${plm}`, kind: 'virtual', requests: 1, triples: Math.round((260 + i * 37) * scale), ms: 40 + i * 9 }
      : { endpoint: `ontop-${plm}`, kind: 'virtual', requests: 0, triples: 0, ms: 0 });
  return [core, ...virtual, { endpoint: 'neptune', kind: 'materialized', requests: 1, triples: Math.round(302 * scale), ms: 23 }];
}

/** Parts with no row in part_tag, reported so the missing tag is a finding against their PLM. */
const untagged = (all: Part[]) => all.filter((p) => !p.jurisdiction).map((p) => partIri(p.plm, p.id));

/** The findings on the parts the profile may see, counted per rule; `{}` when none. */
function findingsOf(visibleParts: PartEntry[]): Record<string, number> {
  const out: Record<string, number> = {};
  for (const p of visibleParts.filter(isPart)) for (const f of p.findings ?? []) out[f.rule] = (out[f.rule] ?? 0) + 1;
  return out;
}

function envelope(c: Call[], pol: Policy) {
  const federationMs = Math.max(...c.map((x) => x.ms)) + 12;
  const policy: Policy = { ...pol, untagged: untagged(parts) };
  return { provenance: { calls: c }, sparql: sparqlFor(pol), timings: { totalMs: federationMs + 31, federationMs, validationMs: 27 }, policy };
}

const delay = <T,>(v: T) => new Promise<T>((r) => setTimeout(() => r(structuredClone(v)), 250));
const later = <T,>(v: T) => new Promise<T>((r) => setTimeout(() => r(structuredClone(v)), 1500));
/** A fixture that throws answers as a rejected request would. */
const attempt = <T,>(f: () => T) => {
  try {
    return delay(f());
  } catch (e) {
    return Promise.reject(e);
  }
};

/** The rooted variants of the parts, interfaces, bill of materials and references listings; null for any other path. */
function subtreeAnswer(path: string, r: Rooted, pol: Policy): Promise<unknown> | null {
  if (path === '/query/parts') {
    const own = withOffers(r.product, withMeshFindings(r.product, redactParts(partsIn(r, partsOf(r.product)), pol), pol));
    return delay<PartsResponse>({ parts: [...own, ...partsIn(r, assemblyParts(r.product))], subtree: subtreeBlock(r, 0), ...envelope(calls(PLMS, 0.1), pol) });
  }
  if (path === '/query/interfaces') {
    const list = interfacesIn(r, interfacesOf(r.product)).map((i) => redactInterface(i, pol));
    return delay<InterfacesResponse>({
      interfaces: list, findings: findingsOf(redactParts(partsIn(r, partsOf(r.product)), pol)), subtree: subtreeBlock(r, contextCount(list)), ...envelope(calls(PLMS, 0.5), pol),
    });
  }
  if (path === '/query/bom') return attempt<Bom>(() => ({ ...subtreeBom(r), ...envelope(calls(PLMS, 0.3), pol) }));
  if (path === '/query/references') {
    return attempt<ReferencesResponse>(() => {
      const all = referencesOf(r.product, pol);
      const references = all.references.filter((x) => r.ids.has(x.part));
      const counts: ReferencesResponse['counts'] = {};
      for (const x of references) counts[x.status] = (counts[x.status] ?? 0) + 1;
      return { product: all.product, references, counts, subtree: subtreeBlock(r, 0), ...envelope(calls(PLMS, 0.2), pol) };
    });
  }
  return null;
}

/** The demo controls, each POSTed to the owning service's route (fixture-demo.ts). */
export function mutate(path: string, profile: string, body: unknown): Promise<unknown> {
  return attempt(() => demoMutate(path, profile, body));
}

/** Each request the screens make, with when it was made and answered in page time, for the headless timing checks. */
const requests: { path: string; profile: string; at: number; answeredAt?: number }[] = [];
(globalThis as { __fixtureRequests?: typeof requests }).__fixtureRequests = requests;

export function respond(fullPath: string, profile: string): Promise<unknown> {
  const entry: (typeof requests)[number] = { path: fullPath, profile, at: performance.now() };
  requests.push(entry);
  const out = answer(fullPath, profile);
  out.then(() => { entry.answeredAt = performance.now(); }, () => undefined);
  return out;
}

function answer(fullPath: string, profile: string): Promise<unknown> {
  const pol = policyFor(profile);
  const walk = fullPath.match(/^\/query\/(paths|flow)\?(.*)$/);
  if (walk) {
    const q = new URLSearchParams(walk[2]);
    const product = q.get('product') ?? '';
    const env = envelope(calls(PLMS, 0.8), pol);
    return attempt(() => (walk[1] === 'paths'
      ? pathsFor(product, q.get('from') ?? '', q.get('to') ?? '', pol, env)
      : flowFor(product, q.get('from') ?? '', (q.get('flow') as FlowKind | null) ?? null, q.get('direction') === 'up', pol, env)));
  }
  const variant = fullPath.match(/^\/query\/(variants|variant-diff)\?(.*)$/);
  if (variant) {
    const q = new URLSearchParams(variant[2]);
    const product = q.get('product') ?? '';
    if (variant[1] === 'variants') return delay<VariantGroups>(variantGroupsOf(product));
    return attempt<VariantDiff>(() => ({ ...variantDiffOf(product, q.get('group') ?? '', q.get('option') ?? ''), ...envelope(calls(PLMS, 0.9), pol) }));
  }
  // `option=` configures the parts and placements listings; the other listings ignore it, as the service does.
  const option = /[?&]option=([^&]*)/.exec(fullPath)?.[1];
  let path: string;
  let product: string | null;
  let rooted: Rooted | null;
  try {
    let root: string | null;
    ({ path, product, root } = listing(fullPath.replace(/&option=[^&]*/, '')));
    rooted = rootedAt(product, root);
  } catch (e) {
    return Promise.reject(e);
  }
  let configured;
  try {
    configured = configurationOf(product, option ? decodeURIComponent(option) : null);
  } catch (e) {
    return Promise.reject(e);
  }
  if (path === '/query/placements') {
    if (!product) return Promise.reject(new Error(`HTTP 400: name the product (${FIXTURE_MARKER})`));
    const r = rooted;
    return attempt<Placements>(() => ({
      ...placementsOf(product, r ? r.node.id : null, pol, configured), ...(r ? { subtree: subtreeBlock(r, 0) } : {}), ...envelope(calls(PLMS, 0.4), pol),
    }));
  }
  if (rooted) {
    const answer = subtreeAnswer(path, rooted, pol);
    if (answer) return answer;
  }
  if (path === '/query/demo/changes') return attempt(() => demoChanges(profile));
  if (path === '/query/demo/health') return delay(demoHealth());
  if (path === '/query/products/list') {
    return delay<ProductListResponse>({ products: productsFor(pol).map(({ key, name, frame, partCount }) => ({ key, name, frame, partCount })), ...envelope(calls(PLMS, 0.1), pol) });
  }
  // The product rules answer later than the list, as a federation of every product's bill of materials does.
  if (path === '/query/products') return later<ProductsResponse>({ products: productsFor(pol), ...envelope(calls(PLMS, 0.2), pol) });
  if (path === '/query/parts') {
    const own = withOffers(product, withMeshFindings(product, redactParts(configuredParts(partsOf(product), configured), pol), pol));
    return delay<PartsResponse>({ parts: [...own, ...assemblyParts(product)], ...envelope(calls(PLMS, 0.2), pol) });
  }
  if (path === '/query/bom') return attempt<Bom>(() => ({ ...bomOf(product), ...envelope(calls(PLMS, 0.6), pol) }));
  if (path === '/query/references') return attempt<ReferencesResponse>(() => ({ ...referencesOf(product, pol), ...envelope(calls(PLMS, 0.3), pol) }));
  if (path === '/query/equivalents') return delay<EquivalentsResponse>({ ...equivalentsOf(product), ...envelope(calls(PLMS, 0.3), pol) });
  if (path === '/query/suppliers') return delay<SuppliersResponse>({ ...suppliersOf(product), ...envelope(calls(PLMS, 0.3), pol) });
  if (path === '/query/interfaces') {
    return delay<InterfacesResponse>({
      interfaces: interfacesOf(product).map((i) => redactInterface(i, pol)), findings: findingsOf(redactParts(partsOf(product), pol)), ...envelope(calls(PLMS, 1), pol),
    });
  }
  // One interface is named by its id within a product; without the product, an id that several
  // products hold is refused with the candidates, as the query service answers.
  const one = path.match(/^\/query\/interfaces\/([^/]+)(\/evidence)?$/);
  if (one) {
    const id = decodeURIComponent(one[1]);
    const held = interfacesOf(product).filter((i) => i.id === id);
    if (held.length === 0) return Promise.reject(new Error(`HTTP 404: no interface ${id}${product ? ` in ${product}` : ''} (${FIXTURE_MARKER})`));
    if (held.length > 1) {
      const candidates = products.filter((p) => interfacesOf(p.key).some((i) => i.id === id)).map((p) => p.key);
      return Promise.reject(new Error(`HTTP 400: interface ${id} is held by ${candidates.join(', ')}; name the product (${FIXTURE_MARKER})`));
    }
    const [found] = held;
    const itf = redactInterface(found, pol);
    const involved = [...new Set(itf.parts.filter(isPart).map((p) => p.plm))];
    const env = envelope(calls(involved, 0.15), pol);
    if (one[2]) return delay(evidenceFor(itf, env));
    return delay<InterfaceResponse>({ interface: itf, ...env });
  }
  const table = path.match(/^\/(fr|de|uk|es|core)\/tables\/([a-z_]+)\?keys=(.*)$/);
  if (table) {
    try {
      const keys = table[3].split(',').map(decodeURIComponent);
      return delay(table[1] === 'core' ? coreTableRows(table[2], keys, pol) : tableRows(table[1], table[2], keys, pol));
    } catch (e) {
      return Promise.reject(e);
    }
  }
  if (path === '/core/tags') return delay<TagsResponse>({ tags: tagsFor(parts, pol) });
  const health = path.match(/^\/(fr|de|uk|es|core|query)\/health$/);
  if (health) return delay({ status: health[1] === 'query' ? 'ok' : 'UP' });
  // The agent answers on the site's origin, not under the API base.
  if (path === '/agent/health') return delay({ status: 'ok' });
  if (path === '/query/mcp/tools') return delay(mcpTools);
  if (path === '/query/rules') return delay(rules);
  if (path === '/query/rules/failures') {
    if (!product) return delay({ failures: failuresFor(pol), ...envelope(calls(PLMS, 1), pol) });
    return delay({ ...productFailuresFor(pol, product, { rooted, configured }), ...envelope(calls(PLMS, 1), pol) });
  }
  const cat = path.match(/^\/(fr|de|uk|es|core)\/catalogue$/);
  if (cat) return delay<Catalogue>(catalogues[cat[1]]);
  const mapping = path.match(/^\/(fr|de|uk|es|core)\/mapping$/);
  if (mapping) return delay(mappings[mapping[1]]);
  return Promise.reject(new Error(`fixture has no route for ${path}`));
}
