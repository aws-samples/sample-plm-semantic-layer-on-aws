// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// External references of the fixture products, as GET /api/query/references?product= answers them in the dev server
// with VITE_FIXTURES=1: each product file's extended.externalRefs, resolved the way the reference shapes resolve a URN
// (urn:plm:<site>:part:<local id>, the British native form UK/nnnn being the part key UK-nnnn), with the verdict of
// the two reference rules and the profile's export control. Never part of a production build.
import type { ExternalReference, Part, Policy, ReferenceStatus, ReferencesResponse } from '../api/types';
import { assemblyParts } from './fixture-bom';
import { visible } from './fixture-policy';
import { files as productFiles, parts } from './fixture-seed';
import { FIXTURE_MARKER } from './marker';

interface JsonReference {
  localPart: string;
  remoteUrn: string;
  quantity: number;
  expectedRevision?: string | number | null;
  note?: string | null;
}

interface ReferenceFile {
  product: { key: string };
  extended?: { externalRefs?: JsonReference[] } | null;
}

/** A reference row as its site stores it; a correction released in fixture mode changes its URN or expected revision. */
export interface ReferenceRow {
  id: string;
  plm: string;
  product: string;
  part: string;
  remoteUrn: string;
  quantity: number;
  expectedRevision?: string;
  note?: string;
}

const files = productFiles as unknown as ReferenceFile[];
const URN = /^urn:plm:(de|fr|es|uk):part:(.+)$/;
const URN_FORM = 'urn:plm:<site>:part:<local id>';

/** Every part row by PLM and id: the parts and the assemblies and site kits of the bills of materials. */
const items = () => new Map<string, Part>([...parts, ...assemblyParts(null)].map((p) => [`${p.plm}|${p.id}`, p]));

/** The part a URN names: undefined when it names none, null when it is no urn:plm:<site>:part:<local id>. */
export function resolve(urn: string, byKey = items()): Part | null | undefined {
  const m = URN.exec(urn);
  if (!m) return null;
  const key = m[1] === 'uk' ? m[2].replace(/^UK\//, 'UK-') : m[2];
  return byKey.get(`${m[1]}|${key}`);
}

/** Every product's references, numbered once across the sites so a row's key is unique in its table. */
export const referenceRows: ReferenceRow[] = files.flatMap((file) => (file.extended?.externalRefs ?? []).flatMap((ref): ReferenceRow[] => {
  const local = parts.find((p) => p.id === ref.localPart) ?? assemblyParts(file.product.key).find((p) => p.id === ref.localPart);
  if (!local) return [];
  const expected = ref.expectedRevision === undefined || ref.expectedRevision === null ? undefined : String(ref.expectedRevision);
  return [{
    id: '', plm: local.plm, product: file.product.key, part: local.id, remoteUrn: ref.remoteUrn, quantity: ref.quantity,
    ...(expected !== undefined ? { expectedRevision: expected } : {}), ...(ref.note ? { note: ref.note } : {}),
  }];
})).map((r, i) => ({ ...r, id: `XR-${String(i + 1).padStart(4, '0')}` }));
/** The released rows, which the officer's reset puts back. */
const released = referenceRows.map((r) => ({ ...r }));
export const resetReferences = () => referenceRows.splice(0, referenceRows.length, ...released.map((r) => ({ ...r })));

function verdict(ref: ReferenceRow, target: Part | null | undefined, pol: Policy) {
  if (!target) {
    const message = target === null
      ? `${ref.part} references ${ref.remoteUrn}, which is not a ` + URN_FORM + ' key and resolves to no part'
      : `${ref.part} references ${ref.remoteUrn}, which resolves to no part of any PLM`;
    return { status: 'danglingReference' as ReferenceStatus, message };
  }
  if (target.releasableTo && !visible(pol, target)) return { status: 'not-evaluable' as ReferenceStatus };
  const expected = ref.expectedRevision;
  const moved = expected !== undefined && target.revision !== undefined && target.revision !== expected;
  if (moved || target.lifecycleState === 'SUPERSEDED') {
    return {
      status: 'staleRevision' as ReferenceStatus,
      message: `${ref.part} expects revision ${expected ?? 'none'} of ${ref.remoteUrn}, which is at revision ${target.revision ?? 'none'} (${target.lifecycleState ?? 'no lifecycle state'})`,
    };
  }
  return { status: 'ok' as ReferenceStatus };
}

/** The references of the product's part rows the profile may see; a product without a file answers 404. */
export function referencesOf(product: string | null, pol: Policy): Pick<ReferencesResponse, 'product' | 'references' | 'counts'> {
  const file = files.find((f) => f.product.key === product);
  if (!file) throw new Error(`HTTP 404: no references for ${product ?? 'every product'} (${FIXTURE_MARKER})`);
  const byKey = items();
  const references: ExternalReference[] = [];
  for (const ref of referenceRows.filter((r) => r.product === file.product.key)) {
    const local = byKey.get(`${ref.plm}|${ref.part}`);
    if (!local || (local.releasableTo && !visible(pol, local))) continue;
    const target = resolve(ref.remoteUrn, byKey);
    const v = verdict(ref, target, pol);
    const shown = target && v.status !== 'not-evaluable' ? target : undefined;
    references.push({
      id: ref.id, plm: ref.plm, part: ref.part, remoteUrn: ref.remoteUrn,
      ...(shown ? { target: { id: shown.id, plm: shown.plm } } : {}),
      quantity: ref.quantity, ...(ref.expectedRevision !== undefined ? { expectedRevision: ref.expectedRevision } : {}),
      ...(shown?.revision !== undefined ? { currentRevision: shown.revision } : {}),
      ...(shown?.lifecycleState ? { lifecycleState: shown.lifecycleState } : {}),
      status: v.status, ...('message' in v ? { message: v.message } : {}), ...(ref.note ? { note: ref.note } : {}),
    });
  }
  references.sort((a, b) => a.part.localeCompare(b.part) || a.remoteUrn.localeCompare(b.remoteUrn));
  const counts: Partial<Record<ReferenceStatus, number>> = {};
  for (const r of references) counts[r.status] = (counts[r.status] ?? 0) + 1;
  return { product: file.product.key, references, counts };
}
