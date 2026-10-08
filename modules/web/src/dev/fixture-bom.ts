// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Bills of materials of the fixture products, as GET /api/query/bom?product= answers them in the dev
// server with VITE_FIXTURES=1. fixture-bom.json is computed from each product's assemblies, BOM
// lines, software and document items and part masses in data/products/*.json, the way the query service rolls them up; the
// rover's tree is the query service's own answer over the fixture stack. The
// trees are those the default profile sees, with one French item of the ornithopter and of the wind
// turbine hidden as well, as a profile without French clearance sees it, so the redaction marker
// renders on every product. Never part of a production build.
import type { Bom, BomItem, BomNode, Part, PartFinding } from '../api/types';
import bomJson from './fixture-bom.json';
import { englishNames } from './fixture-seed';
import { FIXTURE_MARKER } from './marker';

type Tree = Pick<Bom, 'product' | 'root' | 'sites' | 'total'>;
const boms = bomJson as unknown as Record<string, Tree>;

/** The product's tree; a product the file holds no tree for answers 404, as an undeployed endpoint does. */
export function bomOf(product: string | null): Tree {
  const b = product ? boms[product] : undefined;
  if (!b) throw new Error(`HTTP 404: no bill of materials for ${product ?? 'every product'} (${FIXTURE_MARKER})`);
  return b;
}

/** Every node of every tree: a release of an item's lifecycle word or mass reaches each place the item is used. */
export function eachNode(visit: (n: BomNode) => void) {
  const walk = (item: BomItem) => {
    if (item.redacted) return;
    visit(item);
    item.children.forEach(walk);
  };
  Object.values(boms).forEach((b) => walk(b.root));
}

// Each node carries the English name of the labels graph, as the query service's answer does.
eachNode((n) => {
  const en = n.plm ? englishNames.get(`${n.plm}|${n.id}`) : undefined;
  if (en) n.nameEn = en;
});

/** The release findings of the items without geometry (site kits, assemblies), by `plm|id`; fixture-findings.ts derives them. */
export const itemFindings = new Map<string, PartFinding[]>();

const NO_GEOMETRY = new Set(['ASSEMBLY', 'SOFTWARE', 'DOCUMENT']);
const assembliesIn = (item: BomItem): BomNode[] =>
  item.redacted ? [] : [...(NO_GEOMETRY.has(item.partType) ? [item] : []), ...item.children.flatMap(assembliesIn)];

/** The product's assemblies, site kits, software and documents as the parts listing names them: typed, with no CAD file. */
export function assemblyParts(product: string | null): Part[] {
  const trees = product ? (boms[product] ? [boms[product]] : []) : Object.values(boms);
  const seen = new Set<string>();
  return trees.flatMap((b) => assembliesIn(b.root)).filter((a) => !seen.has(a.id) && seen.add(a.id)).map((a) => ({
    id: a.id, plm: a.plm!, name: a.name, ...(a.nameEn ? { nameEn: a.nameEn } : {}), cadFile: null, cadUrl: null, partType: a.partType,
    ...(a.revision ? { revision: a.revision } : {}), ...(a.lifecycle ? { lifecycle: a.lifecycle, lifecycleState: a.lifecycleState } : {}),
    ...(itemFindings.get(`${a.plm}|${a.id}`) ? { findings: itemFindings.get(`${a.plm}|${a.id}`) } : {}),
  }));
}
