// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// GET /api/query/placements of the fixtures, computed from the bill-of-materials lines of
// data/products/*.json as the query service composes them (docs/contract.md): per site, from the
// site kit (or the subtree root, at its reference occurrence) down, M = M_parent . M_line, the
// parents in id order and the occurrences parent-major; a line without placements is one identity.
// Translations of a UK line are in inches. Never part of a production build.
import * as THREE from 'three';
import type { Placement, Placements, Policy } from '../api/types';
import type { Configured } from './fixture-configuration';
import { visible } from './fixture-policy';
import { parts as seedParts, files } from './fixture-seed';
import { FIXTURE_MARKER } from './marker';

interface Line {
  parent: string;
  child: string;
  quantity: number;
  placements?: Placement[];
}
interface Extended {
  assemblies?: { id: string; plm: string; kind: string }[];
  bomLines?: Line[];
}

const MM_PER_INCH = 25.4;
const DEG = Math.PI / 180;
const round = (v: number, places: number) => {
  const r = Math.round(v * 10 ** places) / 10 ** places;
  return r === 0 ? 0 : r;
};

function matrixOf([x, y, z, rx, ry, rz]: Placement, mm: number): THREE.Matrix4 {
  const q = new THREE.Quaternion().setFromEuler(new THREE.Euler(rx * DEG, ry * DEG, rz * DEG, 'ZYX'));
  return new THREE.Matrix4().compose(new THREE.Vector3(x * mm, y * mm, z * mm), q, new THREE.Vector3(1, 1, 1));
}

function placementOf(m: THREE.Matrix4): Placement {
  const t = new THREE.Vector3().setFromMatrixPosition(m);
  const e = new THREE.Euler().setFromRotationMatrix(m, 'ZYX');
  return [round(t.x, 3), round(t.y, 3), round(t.z, 3), round(e.x / DEG, 4), round(e.y / DEG, 4), round(e.z / DEG, 4)];
}

/** The world matrices of every item reached from `starts`, each start at the identity, parent-major. */
function compose(lines: Line[], starts: string[], mmOf: (id: string) => number): Map<string, THREE.Matrix4[]> {
  const byChild = new Map<string, Line[]>();
  for (const l of lines) byChild.set(l.child, [...(byChild.get(l.child) ?? []), l]);
  const memo = new Map<string, THREE.Matrix4[]>(starts.map((s) => [s, [new THREE.Matrix4()]]));
  const reached = new Set<string>();
  const below = (id: string) => {
    reached.add(id);
    for (const l of lines) if (l.parent === id && !reached.has(l.child)) below(l.child);
  };
  starts.forEach(below);
  const occurrences = (id: string): THREE.Matrix4[] => {
    const known = memo.get(id);
    if (known) return known;
    const into = (byChild.get(id) ?? []).filter((l) => reached.has(l.parent)).sort((a, b) => (a.parent < b.parent ? -1 : a.parent > b.parent ? 1 : 0));
    const out = into.flatMap((l) => {
      const own = (l.placements ?? [[0, 0, 0, 0, 0, 0]]).map((p) => matrixOf(p, mmOf(l.child)));
      return occurrences(l.parent).flatMap((m) => own.map((o) => new THREE.Matrix4().multiplyMatrices(m, o)));
    });
    memo.set(id, out);
    return out;
  };
  return new Map([...reached].map((id) => [id, occurrences(id)]));
}

/**
 * The answer for a product, or for the subtree of `root`, or for the product under an option (`configured`: the lines
 * from and to the items it takes out leave, its own lines join); the parts the profile may not see are left out.
 */
export function placementsOf(product: string, root: string | null, pol: Policy, configured: Configured | null = null): Pick<Placements, 'product' | 'parts' | 'occurrences'> {
  const file = files.find((f) => f.product.key === product);
  if (!file) throw new Error(`HTTP 404: no product ${product} (${FIXTURE_MARKER})`);
  const ext = (file as unknown as { extended?: Extended }).extended ?? {};
  const out = configured?.removed ?? new Set<string>();
  const lines = [...(ext.bomLines ?? []).filter((l) => !out.has(l.parent) && !out.has(l.child)), ...(configured?.lines ?? [])];
  const site = new Map<string, string>([...[...file.parts, ...(ext.assemblies ?? [])].map((p): [string, string] => [p.id, p.plm.toUpperCase()]),
    ...(configured?.sites ?? [])]);
  const kits = (ext.assemblies ?? []).filter((a) => a.kind === 'SITE_KIT').map((a) => a.id);
  const world = compose(lines, root ? [root] : kits, (id) => (site.get(id) === 'UK' ? MM_PER_INCH : 1));
  const shown = new Map([...seedParts, ...(configured?.parts ?? [])].filter((p) => visible(pol, p)).map((p) => [p.id, p]));
  const candidates = [...file.parts.filter((p) => (p.extended?.type ?? 'PART') === 'PART').map((p) => ({ id: p.id, plm: p.plm })),
    ...(configured?.parts ?? []).filter((p) => (p.partType ?? 'PART') === 'PART')];
  const geometric = candidates.filter((p) => shown.has(p.id) && world.get(p.id)?.length);
  const list = geometric.map((p) => ({ id: p.id, plm: p.plm.toLowerCase(), occurrences: world.get(p.id)!.map(placementOf) }))
    .sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  return { product, parts: list, occurrences: list.reduce((n, p) => n + p.occurrences.length, 0) };
}
