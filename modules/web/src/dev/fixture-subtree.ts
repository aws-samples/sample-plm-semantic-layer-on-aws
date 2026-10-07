// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Subtree answers of the fixtures (`&root=<assembly id>`), computed from the fixture bill of
// materials: the assembly and every item under it, its occurrences multiplied down from the
// assembly, the roll-ups over its parts, and the subtree block. The fixture sites resolve in one
// round, so every subtree has depth 1. Never part of a production build.
import type { Bom, BomItem, BomNode, BomRollup, Interface, Part, Subtree } from '../api/types';
import { isPart } from '../api/types';
import { bomOf } from './fixture-bom';
import { FIXTURE_MARKER } from './marker';

export interface Rooted {
  product: string;
  node: BomNode;
  /** Ids of the items the profile may see under the root, the root included. */
  ids: Set<string>;
}

const SITES = ['fr', 'de', 'uk', 'es'];

function find(item: BomItem, id: string): BomNode | null {
  if (item.redacted) return null;
  if (item.id === id) return item;
  for (const c of item.children) {
    const hit = find(c, id);
    if (hit) return hit;
  }
  return null;
}

const idsUnder = (item: BomItem): string[] => (item.redacted ? [] : [item.id, ...item.children.flatMap(idsUnder)]);

/** The subtree a root names (an assembly, a site kit or a part); null when the root is absent or is the product, which answers product-wide. */
export function rootedAt(product: string | null, root: string | null): Rooted | null {
  if (!product || !root || root === product) return null;
  const node = find(bomOf(product).root, root);
  if (!node) throw new Error(`HTTP 404: no item ${root} in ${product} (${FIXTURE_MARKER})`);
  return { product, node, ids: new Set(idsUnder(node)) };
}

export function subtreeBlock(r: Rooted, context: number): Subtree {
  const root = { id: r.node.id, plm: r.node.plm! };
  return {
    root: root.id, plm: root.plm, product: r.product, name: r.node.name, redacted: false, depth: 1,
    rounds: [{ round: 1, roots: [root], items: r.ids.size }], items: r.ids.size, context, productRules: 'not-evaluable',
  };
}

/** The item with its occurrences counted from the subtree root down. */
function reroot(item: BomItem, occurrences: number): BomItem {
  if (item.redacted) return { ...item, occurrences };
  return {
    ...item, occurrences,
    ...(item.unitMassKg === undefined ? {} : { extendedMassKg: item.unitMassKg * occurrences }),
    children: item.children.map((c) => reroot(c, occurrences * c.quantity)),
  };
}

function rollup(plm: string | null, items: BomItem[]): BomRollup {
  const r: BomRollup = { plm, occurrences: 0, massKg: 0, withoutMass: 0, hiddenOccurrences: 0 };
  for (const i of items) {
    if (i.redacted) r.hiddenOccurrences += i.occurrences;
    else if (i.partType === 'PART') {
      r.occurrences += i.occurrences;
      if (i.extendedMassKg === undefined) r.withoutMass++;
      else r.massKg += i.extendedMassKg;
    }
  }
  return r;
}

const flatten = (item: BomItem): BomItem[] => (item.redacted ? [item] : [item, ...item.children.flatMap(flatten)]);

/** The bill of materials rooted at the assembly, with a roll-up per site that holds items of it. */
export function subtreeBom(r: Rooted): Pick<Bom, 'product' | 'root' | 'sites' | 'total' | 'subtree'> {
  const root = reroot(r.node, 1) as BomNode;
  const all = flatten(root);
  const sites = SITES.filter((s) => all.some((i) => i.plm === s)).map((s) => rollup(s, all.filter((i) => i.plm === s)));
  return { product: r.product, root, sites, total: rollup(null, all), subtree: subtreeBlock(r, 0) };
}

/** The parts of the subtree. */
export const partsIn = (r: Rooted, parts: Part[]) => parts.filter((p) => r.ids.has(p.id));

/** The interfaces with a side in the subtree; a part on the far side is flagged as context. */
export function interfacesIn(r: Rooted, interfaces: Interface[]): Interface[] {
  return interfaces
    .filter((i) => i.parts.some((p) => isPart(p) && r.ids.has(p.id)))
    .map((i) => ({ ...i, parts: i.parts.map((p) => (isPart(p) && !r.ids.has(p.id) ? { ...p, context: true as const } : p)) }));
}

/** Distinct context parts the interfaces name. */
export const contextCount = (interfaces: Interface[]) =>
  new Set(interfaces.flatMap((i) => i.parts).filter((p) => isPart(p) && p.context).map((p) => (p as Part).id)).size;
