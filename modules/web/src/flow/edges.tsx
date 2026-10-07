// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { CSSProperties } from 'react';
import { BaseEdge, EdgeLabelRenderer, type Edge, type EdgeProps } from '@xyflow/react';
import type { Route } from './topology';

export type WireTone = 'plain' | 'on' | 'off' | 'live';

export interface WireData extends Record<string, unknown> {
  route: Route;
  tone: WireTone;
  colour: string;
  label?: string;
  /** `stack` is a list one item per line (the tools an agent turn ran, the corrections a PLM released); `event` is a business event announced on the bus. */
  labelKind?: 'protocol' | 'live' | 'stack' | 'event';
}
export type WireEdge = Edge<WireData, 'wire'>;

interface Pt {
  x: number;
  y: number;
}

function points(route: Route, sx: number, sy: number, tx: number, ty: number): Pt[] {
  switch (route.kind) {
    case 'straight':
      return [{ x: sx, y: sy }, { x: tx, y: ty }];
    case 'bus':
      return [{ x: sx, y: sy }, { x: sx, y: route.y }, { x: tx, y: route.y }, { x: tx, y: ty }];
    case 'gutter': {
      const gx = route.side === 'right' ? Math.max(sx, tx) + route.offset : Math.min(sx, tx) - route.offset;
      return [{ x: sx, y: sy }, { x: gx, y: sy }, { x: gx, y: ty }, { x: tx, y: ty }];
    }
    case 'elbow':
      return [{ x: sx, y: sy }, { x: tx, y: sy }, { x: tx, y: ty }];
  }
}

/** Orthogonal polyline with rounded corners. */
function roundedPath(pts: Pt[], r: number): string {
  let d = `M ${pts[0].x} ${pts[0].y}`;
  for (let i = 1; i < pts.length - 1; i++) {
    const p = pts[i - 1];
    const c = pts[i];
    const n = pts[i + 1];
    const rr = Math.min(r, Math.hypot(c.x - p.x, c.y - p.y) / 2, Math.hypot(n.x - c.x, n.y - c.y) / 2);
    const a = towards(c, p, rr);
    const b = towards(c, n, rr);
    d += ` L ${a.x} ${a.y} Q ${c.x} ${c.y} ${b.x} ${b.y}`;
  }
  const last = pts[pts.length - 1];
  return `${d} L ${last.x} ${last.y}`;
}

function towards(from: Pt, to: Pt, d: number): Pt {
  const len = Math.hypot(to.x - from.x, to.y - from.y) || 1;
  return { x: from.x + ((to.x - from.x) / len) * d, y: from.y + ((to.y - from.y) / len) * d };
}

/** Where the tag sits: on the bus just past its corner, along the vertical run of a gutter or elbow, else mid-way. */
function placeTag(route: Route, pts: Pt[]): { x: number; y: number; transform: string } {
  const first = pts[0];
  const last = pts[pts.length - 1];
  if (route.kind === 'bus') {
    const atSource = route.tag === 'source';
    const corner = atSource ? pts[1] : pts[2];
    const goesLeft = first.x > last.x;
    // Along the bus, away from its corner: towards the source from the target's corner, towards the target from the source's.
    const dir = atSource === goesLeft ? -1 : 1;
    return { x: corner.x + 10 * dir, y: corner.y, transform: dir > 0 ? 'translateY(-50%)' : 'translate(-100%, -50%)' };
  }
  if (route.kind === 'gutter') return { x: pts[1].x, y: (pts[1].y + pts[2].y) / 2, transform: 'translate(-50%, -50%) rotate(-90deg)' };
  if (route.kind === 'elbow') return { x: last.x, y: (first.y + last.y) / 2, transform: 'translate(-50%, -50%) rotate(-90deg)' };
  const turned = route.kind === 'straight' && route.tag === 'rotated';
  return { x: (first.x + last.x) / 2, y: (first.y + last.y) / 2, transform: turned ? 'translate(-50%, -50%) rotate(-90deg)' : 'translate(-50%, -50%)' };
}

export function WireEdge({ id, sourceX, sourceY, targetX, targetY, data, markerEnd }: EdgeProps<WireEdge>) {
  const d = data!;
  const pts = points(d.route, sourceX, sourceY, targetX, targetY);
  const tag = d.label ? placeTag(d.route, pts) : null;
  return (
    <>
      <BaseEdge id={id} path={roundedPath(pts, 7)} markerEnd={markerEnd} style={{ stroke: d.colour }} />
      {tag && d.label ? (
        <EdgeLabelRenderer>
          <div
            className={`wire-tag is-${d.labelKind} tone-${d.tone}`}
            style={{ left: tag.x, top: tag.y, transform: tag.transform, '--plm': d.colour } as CSSProperties}
          >
            {d.label}
          </div>
        </EdgeLabelRenderer>
      ) : null}
    </>
  );
}
