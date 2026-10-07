// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The path on screen rides in the location hash, so a link opens the same path:
// `#/paths/connect/<from>/<to>` for the interfaces between two parts,
// `#/paths/flow/<from>/<flow kind or all>/<down or up>` for the walk along the functional edges.
import { FLOWS, type FlowKind } from '../api/pathTypes';

export type PathQuery =
  | { mode: 'connect'; from: string; to: string }
  | { mode: 'flow'; from: string; flow: FlowKind | null; up: boolean };

const ALL_FLOWS = 'all';

/** The query the hash segments after `#/paths` name; null when they name no complete one. */
export function pathOf(segments: (string | undefined)[]): PathQuery | null {
  const [mode, a, b, c] = segments.map((s) => (s ? decodeURIComponent(s) : ''));
  if (mode === 'connect' && a && b) return { mode, from: a, to: b };
  if (mode === 'flow' && a) {
    const flow = (FLOWS as readonly string[]).includes(b) ? (b as FlowKind) : null;
    return { mode, from: a, flow, up: c === 'up' };
  }
  return null;
}

export function pathHash(q: PathQuery | null): string {
  if (!q) return '#/paths';
  const e = encodeURIComponent;
  return q.mode === 'connect'
    ? `#/paths/connect/${e(q.from)}/${e(q.to)}`
    : `#/paths/flow/${e(q.from)}/${q.flow ?? ALL_FLOWS}/${q.up ? 'up' : 'down'}`;
}

/** The query service request of a path query, scoped to the product. */
export function pathRequest(q: PathQuery, product: string): string {
  const p = new URLSearchParams({ product, from: q.from });
  if (q.mode === 'connect') {
    p.set('to', q.to);
    return `/query/paths?${p.toString()}`;
  }
  if (q.flow) p.set('flow', q.flow);
  if (q.up) p.set('direction', 'up');
  return `/query/flow?${p.toString()}`;
}
