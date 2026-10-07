// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The questions offered while the conversation is empty: one per kind of tool, each naming records of the product on
// screen as the profile sees it. A pure function of the answers, so the same answers give the same questions; where
// several records qualify, the lowest id wins.
import type { Bom, BomItem, BomNode, EquivalentGroup, Feature, Interface, Part, PartEntry } from '../api/types';
import { isFeature, isPart } from '../api/types';
import { RULE_LABEL } from '../check/violations';
import { byId } from '../ui/ids';
import { plmCode } from '../ui/plm';

export type ExampleKind =
  | 'failing' | 'whereUsed' | 'impact' | 'releasable' | 'evidence' | 'perUnit' | 'pins' | 'connectorTypes' | 'subtree'
  | 'equivalents' | 'path' | 'finding' | 'whatIf';

export interface Example {
  kind: ExampleKind;
  text: string;
  /** The records of the product the question names. */
  ids: string[];
}

/** The answers the questions are built from; null for one that failed, which leaves its kinds out. */
export interface ExampleData {
  parts: PartEntry[];
  interfaces: Interface[];
  bom: Bom | null;
  equivalents: EquivalentGroup[] | null;
}

/** Offered before the product's answers arrive: the same kinds, naming no record. */
export const GENERIC: Example[] = [
  { kind: 'failing', text: 'Which interfaces fail for my profile and why?', ids: [] },
  { kind: 'whereUsed', text: 'Which parts are used in more than one assembly?', ids: [] },
  { kind: 'impact', text: 'What would a change to a plug on a cross-site interface impact?', ids: [] },
  { kind: 'releasable', text: 'Which parts of this product are releasable to me?', ids: [] },
  { kind: 'evidence', text: 'Show me the SQL behind one site of a cross-site interface', ids: [] },
  { kind: 'pins', text: 'Which connectors have the most pins?', ids: [] },
  { kind: 'connectorTypes', text: 'Which connector types are used on parts of more than one PLM, and how many plugs of each?', ids: [] },
  { kind: 'subtree', text: 'Which assemblies make up this product?', ids: [] },
];

const lowest = <T>(items: T[], id: (t: T) => string): T | undefined => [...items].sort((a, b) => byId(id(a), id(b)))[0];

/** The rules a correction of one feature's record can fix, as the release form proposes it. */
const CORRECTABLE = new Set(['unit', 'position', 'connector', 'fastener', 'hydraulic']);

const features = (interfaces: Interface[]): Feature[] => interfaces.flatMap((i) => i.features).filter(isFeature);
const plugs = (interfaces: Interface[]) => features(interfaces).filter((f) => f.kind === 'plug');

/** An interface whose two sides the profile sees and two sites own. */
const crossSite = (i: Interface) => i.parts.every(isPart) && new Set(i.parts.filter(isPart).map((p) => plmCode(p.plm))).size > 1;

function failing(interfaces: Interface[]): Example | null {
  const itf = lowest(interfaces.filter((i) => i.status === 'fail'), (i) => i.id);
  return itf ? { kind: 'failing', text: `Why does ${itf.id} fail for my profile?`, ids: [itf.id] } : null;
}

function parentsOf(root: BomNode): Map<string, Set<string>> {
  const parents = new Map<string, Set<string>>();
  const walk = (node: BomNode) => {
    for (const c of node.children) {
      if (c.redacted) continue;
      parents.set(c.id, (parents.get(c.id) ?? new Set()).add(node.id));
      walk(c);
    }
  };
  walk(root);
  return parents;
}

function whereUsed(bom: Bom | null): Example | null {
  if (!bom) return null;
  const shared = [...parentsOf(bom.root)].filter(([, p]) => p.size > 1).map(([id]) => id);
  const id = lowest(shared, (x) => x);
  return id ? { kind: 'whereUsed', text: `Where is ${id} used?`, ids: [id] } : null;
}

function impact(interfaces: Interface[]): Example | null {
  const plug = lowest(plugs(interfaces.filter(crossSite)), (f) => f.id);
  return plug ? { kind: 'impact', text: `What would a change to plug ${plug.id} impact?`, ids: [plug.id] } : null;
}

function releasable(parts: Part[]): Example | null {
  const part = lowest(parts.filter((p) => p.jurisdiction), (p) => p.id) ?? lowest(parts, (p) => p.id);
  return part ? { kind: 'releasable', text: `Is ${part.id} releasable to me?`, ids: [part.id] } : null;
}

function evidence(interfaces: Interface[]): Example | null {
  const itf = lowest(interfaces.filter(crossSite), (i) => i.id);
  if (!itf) return null;
  const site = itf.parts.filter(isPart).map((p) => plmCode(p.plm)).sort()[0];
  return { kind: 'evidence', text: `Show me the SQL behind the ${site} arm of ${itf.id}`, ids: [itf.id] };
}

/** A site that stores fastener positions in inches: the count per unit shows the layer converting them. */
function perUnit(interfaces: Interface[]): Example | null {
  const sites = features(interfaces).filter((f) => f.kind === 'fastener' && f.source.unit === 'IN').map((f) => plmCode(f.plm));
  const site = lowest(sites, (s) => s);
  return site ? { kind: 'perUnit', text: `How many ${site} fasteners are stored per unit of measure?`, ids: [] } : null;
}

/** The site with the most connectors the profile sees. */
function pins(interfaces: Interface[]): Example | null {
  const count = new Map<string, number>();
  for (const f of plugs(interfaces)) if (typeof f.properties.pinCount === 'number') count.set(plmCode(f.plm), (count.get(plmCode(f.plm)) ?? 0) + 1);
  const site = [...count].sort((a, b) => b[1] - a[1] || byId(a[0], b[0]))[0]?.[0];
  return site ? { kind: 'pins', text: `Which ${site} connectors have the most pins?`, ids: [] } : null;
}

function connectorTypes(interfaces: Interface[]): Example | null {
  const sites = new Map<string, Set<string>>();
  for (const f of plugs(interfaces)) {
    const type = f.properties.connectorType;
    if (typeof type === 'string') sites.set(type, (sites.get(type) ?? new Set()).add(plmCode(f.plm)));
  }
  return [...sites.values()].some((s) => s.size > 1)
    ? { kind: 'connectorTypes', text: 'Which connector types are used on parts of more than one PLM, and how many plugs of each?', ids: [] }
    : null;
}

/** An assembly below the site kits, else a site kit; below the subtree root when one is open. */
function subtree(bom: Bom | null): Example | null {
  if (!bom) return null;
  const kits: BomNode[] = [];
  const inner: BomNode[] = [];
  const walk = (items: BomItem[], depth: number) => {
    for (const c of items) {
      if (c.redacted) continue;
      if (c.partType === 'ASSEMBLY' && c.children.length) (depth ? inner : kits).push(c);
      walk(c.children, depth + 1);
    }
  };
  // Under the product root the first level is each site's kit; a subtree's children are its own assemblies.
  walk(bom.root.children, bom.root.partType === 'PRODUCT' ? 0 : 1);
  const node = lowest(inner, (n) => n.id) ?? lowest(kits, (n) => n.id);
  return node ? { kind: 'subtree', text: `Open the subtree of ${node.id}`, ids: [node.id] } : null;
}

/** The group with the lowest member the scope holds, named by that member. */
function equivalents(groups: EquivalentGroup[] | null, parts: Part[]): Example | null {
  if (!groups) return null;
  const inScope = new Set(parts.map((p) => p.id));
  const named = groups.flatMap((g) => {
    const m = lowest(g.members.filter((x) => inScope.has(x.id)), (x) => x.id);
    return m && g.members.length > 1 ? [{ g, id: m.id }] : [];
  });
  const pick = lowest(named, (n) => n.id);
  return pick
    ? { kind: 'equivalents', text: `Which parts are the same ${pick.g.classLabel.toLowerCase()} as ${pick.id}, and how many stock lines would that save?`, ids: [pick.id] }
    : null;
}

/** The two parts furthest apart over the interfaces the profile sees, within the twelve steps path_between walks. */
function path(interfaces: Interface[]): Example | null {
  const next = new Map<string, Set<string>>();
  for (const i of interfaces) {
    const ids = i.parts.filter(isPart).map((p) => p.id);
    for (const a of ids) for (const b of ids) if (a !== b) next.set(a, (next.get(a) ?? new Set()).add(b));
  }
  let best: { from: string; to: string; d: number } | null = null;
  for (const from of [...next.keys()].sort(byId)) {
    const dist = new Map([[from, 0]]);
    const queue = [from];
    for (let q = queue.shift(); q !== undefined; q = queue.shift()) {
      for (const n of next.get(q) ?? []) if (!dist.has(n)) { dist.set(n, dist.get(q)! + 1); queue.push(n); }
    }
    for (const [to, d] of [...dist].sort((a, b) => byId(a[0], b[0]))) {
      if (d <= 12 && byId(from, to) < 0 && (!best || d > best.d)) best = { from, to, d };
    }
  }
  return best && best.d > 1 ? { kind: 'path', text: `Which interfaces join ${best.from} to ${best.to}?`, ids: [best.from, best.to] } : null;
}

/** A part's release finding; a missing CAD file only when the product seeds no other. */
function finding(parts: Part[]): Example | null {
  const flagged = parts.flatMap((p) => (p.findings ?? []).map((f) => ({ id: p.id, rule: f.rule })));
  const pick = lowest(flagged.filter((f) => f.rule !== 'cadMissing'), (f) => f.id) ?? lowest(flagged, (f) => f.id);
  return pick ? { kind: 'finding', text: `Why does ${pick.id} carry a ${pick.rule} finding?`, ids: [pick.id] } : null;
}

function whatIf(interfaces: Interface[]): Example | null {
  const fixable = interfaces.filter((i) => i.status === 'fail' && i.violations.some((v) => CORRECTABLE.has(v.rule)));
  const itf = lowest(fixable, (i) => i.id);
  const rule = itf?.violations.find((v) => CORRECTABLE.has(v.rule))?.rule;
  return itf && rule
    ? { kind: 'whatIf', text: `What would correcting the ${RULE_LABEL[rule].toLowerCase()} failure on ${itf.id} fix?`, ids: [itf.id] }
    : null;
}

/** One question per kind the product holds records for, in a fixed order. */
export function examplesFor(data: ExampleData): Example[] {
  const parts = data.parts.filter(isPart).filter((p) => !p.context);
  const { interfaces, bom } = data;
  return [
    failing(interfaces), whereUsed(bom), impact(interfaces), releasable(parts), evidence(interfaces), perUnit(interfaces),
    pins(interfaces), connectorTypes(interfaces), subtree(bom), equivalents(data.equivalents, parts), path(interfaces),
    finding(parts), whatIf(interfaces),
  ].filter((e): e is Example => e !== null);
}
