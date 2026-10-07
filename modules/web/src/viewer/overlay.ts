// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Draws feature markers, the detail callout and the dimension line as SVG over the
// WebGL canvas, so glyphs keep a constant screen size at every zoom.
import type { FeatureKind, Vec3 } from '../api/types';
import { occurrenceLabel } from './view';

export type Project = (p: Vec3) => { x: number; y: number; front: boolean };

export type MarkerStatus = 'pass' | 'fail' | 'not-evaluable';

export interface Marker {
  id: string;
  interfaceId: string;
  kind: FeatureKind;
  status: MarkerStatus;
  pos: Vec3;
  emphasis: 'none' | 'selected' | 'dim';
  label: string | null;
}

export interface Dimension {
  a: Vec3;
  b: Vec3;
  aId: string;
  axis: 'x' | 'y' | 'z';
  deltaMm: number;
  toleranceMm: number | null;
}

export interface LoupeView {
  rect: { left: number; top: number; width: number; height: number };
  project: Project;
  fieldMm: number;
}

const PASS = '#12a15e';
const FAIL = '#e0322a';
const INK = '#15202a';
const REDACT = '#8b959e';

const f = (n: number) => n.toFixed(1);

const SVG_NS = 'http://www.w3.org/2000/svg';
export type Attrs = Record<string, string | number>;

/** One SVG shape as data, so the scene overlay builds it as a DOM node and the React legend renders it as JSX. */
export interface Shape {
  name: 'circle' | 'rect' | 'path';
  attrs: Attrs;
}

/** An SVG element with its attributes set and its children appended; text children become text nodes. */
export function el(name: string, attrs: Attrs = {}, ...children: (Node | string)[]): SVGElement {
  const e = document.createElementNS(SVG_NS, name);
  for (const [k, v] of Object.entries(attrs)) e.setAttribute(k, String(v));
  e.append(...children);
  return e;
}

/** Drafting hatch that marks what the profile may not see: material cut away from the drawing. */
export const hatch: { pattern: Attrs; rect: Attrs; path: Attrs } = {
  pattern: { width: 4, height: 4, patternUnits: 'userSpaceOnUse', patternTransform: 'rotate(45)' },
  rect: { width: 4, height: 4, fill: '#eef1f3' },
  path: { d: 'M0 0V4', stroke: REDACT, 'stroke-width': 1.1 },
};
export const hatchPattern = (id: string) => el('pattern', { id, ...hatch.pattern }, el('rect', hatch.rect), el('path', hatch.path));

/** The three feature classes as drafting symbols: circle plug, square fastener, diamond coupling. */
function shape(kind: FeatureKind, x: number, y: number, r: number, attrs: Attrs): Shape {
  if (kind === 'plug') return { name: 'circle', attrs: { cx: f(x), cy: f(y), r: f(r), ...attrs } };
  if (kind === 'fastener') return { name: 'rect', attrs: { x: f(x - r), y: f(y - r), width: f(2 * r), height: f(2 * r), ...attrs } };
  const d = r * 1.28;
  return { name: 'path', attrs: { d: `M${f(x)} ${f(y - d)}L${f(x + d)} ${f(y)}L${f(x)} ${f(y + d)}L${f(x - d)} ${f(y)}Z`, ...attrs } };
}

/** White ring and coloured core; a redacted side gets a dashed ring and a hatched core, an outline no core colour. */
function symbol(kind: FeatureKind, status: MarkerStatus | 'outline', x: number, y: number, r: number, hatch: string): Shape[] {
  const redacted = status === 'not-evaluable';
  const ring: Attrs = redacted
    ? { fill: '#fff', stroke: REDACT, 'stroke-width': 0.9, 'stroke-dasharray': '2 1.5' }
    : { fill: '#fff', stroke: INK, 'stroke-width': 0.75 };
  const core: Attrs = redacted
    ? { fill: `url(#${hatch})`, stroke: REDACT, 'stroke-width': 0.6 }
    : status === 'outline' ? { fill: '#fff', stroke: INK, 'stroke-width': 0.9 } : { fill: status === 'fail' ? FAIL : PASS };
  return [shape(kind, x, y, r + 1.5, ring), shape(kind, x, y, r - 0.5, core)];
}

function glyph(x: number, y: number, m: Marker): SVGElement {
  const r = m.emphasis === 'selected' ? 6.5 : 4.5;
  const opacity = m.emphasis === 'dim' ? 0.35 : 1;
  const cross = m.emphasis === 'selected'
    ? [el('path', { d: `M${f(x - r - 5)} ${f(y)}h${r * 2 + 10}M${f(x)} ${f(y - r - 5)}v${r * 2 + 10}`, stroke: INK, 'stroke-width': 1 })]
    : [];
  return el('g', { class: 'plug', 'data-interface': m.interfaceId, opacity },
    el('circle', { cx: f(x), cy: f(y), r: r + 7, fill: 'transparent' }), ...cross,
    ...symbol(m.kind, m.status, x, y, r, 'hatch-redacted').map((s) => el(s.name, s.attrs)));
}

/** The same symbol as the two shapes of a 16 x 16 glyph for legends and tables; its hatch lives in the page's shared defs. */
export const glyphShapes = (kind: FeatureKind, status: MarkerStatus | 'outline') => symbol(kind, status, 8, 8, 4.5, 'hatch-redacted-ui');

export function drawMain(project: Project, markers: Marker[], callout: Vec3 | null): SVGElement[] {
  const out: SVGElement[] = [];
  const placed: { x: number; y: number }[] = [];
  const ordered = [...markers].sort((a, b) => rank(a) - rank(b));
  for (const m of ordered) {
    const p = project(m.pos);
    if (!p.front) continue;
    out.push(glyph(p.x, p.y, m));
    if (m.label) {
      let ly = p.y - 24;
      while (placed.some((q) => Math.abs(q.y - ly) < 15 && Math.abs(q.x - p.x) < 150)) ly -= 15;
      placed.push({ x: p.x, y: ly });
      out.push(el('path', { class: 'plug-leader', d: `M${f(p.x + 6)} ${f(p.y - 6)}L${f(p.x + 14)} ${f(ly + 3)}h4` }));
      out.push(el('text', { class: 'plug-tag', x: f(p.x + 20), y: f(ly + 7) }, m.label));
    }
  }
  if (callout) {
    const c = project(callout);
    if (c.front) {
      out.push(el('g', { class: 'callout' },
        el('circle', { cx: f(c.x), cy: f(c.y), r: 26 }),
        el('path', { d: `M${f(c.x - 18)} ${f(c.y + 18)}l-14 14h-22` }),
        el('text', { x: f(c.x - 66), y: f(c.y + 38) }, 'A')));
    }
  }
  return out;
}

export interface PartTag {
  x: number;
  y: number;
  viewWidth: number;
  owner: string;
  name: string;
  supplier: string | null;
  /** What the part is painted in the scene, and its owner's colour. */
  paint: string;
  ownerColour: string;
  /** Which of the part's occurrences is under the pointer; null for a part drawn once. */
  occurrence: { index: number; count: number } | null;
}

/** Label of the part under the pointer: a swatch of its paint edged in its owner's colour, which occurrence it is, then who built and who integrates it. */
export function drawPartTag(t: PartTag): SVGElement {
  const flip = t.x > t.viewWidth - 300;
  const x = flip ? t.x - 16 : t.x + 16;
  const y = t.y - 12;
  const tx = flip ? x - 14 : x + 14;
  const swatch = el('rect', { x: f(flip ? x - 9 : x), y: f(y - 9), width: 9, height: 9, fill: t.paint, stroke: t.ownerColour, 'stroke-width': 1.5 });
  const text = el('text', { x: f(tx), y: f(y), ...(flip ? { 'text-anchor': 'end' } : {}) }, el('tspan', {}, `${t.owner} ${t.name}`));
  if (t.occurrence) text.append(el('tspan', { class: 'part-tag-sub', x: f(tx), dy: 14 }, occurrenceLabel(t.occurrence.index, t.occurrence.count)));
  if (t.supplier) text.append(el('tspan', { class: 'part-tag-sub', x: f(tx), dy: 14 }, `built by ${t.supplier}, integrated by ${t.owner} PLM`));
  return el('g', { class: 'part-tag' }, swatch, text);
}

const STATUS_RANK: Record<MarkerStatus, number> = { pass: 0, 'not-evaluable': 0.5, fail: 1 };
const rank = (m: Marker) => (m.emphasis === 'selected' ? 3 : 0) + STATUS_RANK[m.status] - (m.emphasis === 'dim' ? 2 : 0);

/** A drafting dimension between the mated pair, with the tolerance zone around the first feature. */
export function drawLoupe(view: LoupeView, dim: Dimension, markers: Marker[]): SVGElement[] {
  const { rect, project } = view;
  const clip = el('clipPath', { id: 'loupe-clip' }, el('rect', { x: rect.left, y: rect.top, width: rect.width, height: rect.height }));
  const pa = project(dim.a);
  const pb = project(dim.b);
  const dx = pb.x - pa.x;
  const dy = pb.y - pa.y;
  const len = Math.hypot(dx, dy) || 1;
  const u = { x: dx / len, y: dy / len };
  let n = { x: -u.y, y: u.x };
  if (n.y > 0) n = { x: -n.x, y: -n.y };
  const off = Math.min(58, rect.height * 0.28);
  const at = (p: { x: number; y: number }, k: number) => `${f(p.x + n.x * k)} ${f(p.y + n.y * k)}`;
  const parts: SVGElement[] = [];

  if (dim.toleranceMm !== null) {
    const axisUnit = { x: 0, y: 0, z: 0, [dim.axis]: dim.toleranceMm };
    const lo = project({ x: dim.a.x - axisUnit.x, y: dim.a.y - axisUnit.y, z: dim.a.z - axisUnit.z });
    const hi = project({ x: dim.a.x + axisUnit.x, y: dim.a.y + axisUnit.y, z: dim.a.z + axisUnit.z });
    const h = 22;
    parts.push(el('path', { class: 'tol-zone', d: `M${at(lo, h)}L${at(hi, h)}L${at(hi, -h)}L${at(lo, -h)}Z` }));
    parts.push(el('text', { class: 'tol-text', x: f(lo.x + n.x * -h - 2), y: f(lo.y - n.y * h + 16) }, `tolerance ±${dim.toleranceMm.toFixed(1)} mm`));
  }

  parts.push(el('path', { class: 'dim-ext', d: `M${at(pa, 9)}L${at(pa, off + 8)}M${at(pb, 9)}L${at(pb, off + 8)}` }));
  parts.push(el('path', { class: 'dim-line', d: `M${at(pa, off)}L${at(pb, off)}`, 'marker-start': 'url(#arrow)', 'marker-end': 'url(#arrow)' }));
  const mid = { x: (pa.x + pb.x) / 2, y: (pa.y + pb.y) / 2 };
  parts.push(el('text', { class: 'dim-value', x: f(mid.x + n.x * (off + 14)), y: f(mid.y + n.y * (off + 14)) }, dim.deltaMm.toFixed(1)));
  parts.push(el('text', { class: 'dim-axis', x: f(mid.x + n.x * (off + 14)), y: f(mid.y + n.y * (off + 14) + 14) }, `mm on ${dim.axis}`));

  for (const m of markers) {
    const p = project(m.pos);
    parts.push(glyph(p.x, p.y, { ...m, emphasis: 'selected' }));
  }
  parts.push(scaleBar(view));
  return [el('defs', {}, clip), el('g', { 'clip-path': 'url(#loupe-clip)' }, ...parts)];
}

function scaleBar({ rect, fieldMm }: LoupeView): SVGElement {
  const pxPerMm = rect.width / fieldMm;
  const nice = [1, 2, 5, 10, 20, 50].find((s) => s * pxPerMm >= 48) ?? 50;
  const w = nice * pxPerMm;
  const x = rect.left + 14;
  const y = rect.top + rect.height - 16;
  return el('g', { class: 'scale-bar' },
    el('path', { d: `M${f(x)} ${f(y - 4)}v4h${f(w)}v-4` }),
    el('text', { x: f(x + w + 6), y: f(y + 1) }, `${nice} mm`));
}

/** The scene overlay's shared definitions: the dimension arrowhead, the pass hatch and the redaction hatch. */
export const defs = () =>
  el('defs', {},
    el('marker', { id: 'arrow', viewBox: '0 0 10 10', refX: 10, refY: 5, markerWidth: 9, markerHeight: 9, orient: 'auto-start-reverse' },
      el('path', { d: 'M0 1.5L10 5L0 8.5Z', fill: INK })),
    el('pattern', { id: 'hatch', width: 6, height: 6, patternUnits: 'userSpaceOnUse', patternTransform: 'rotate(45)' },
      el('path', { d: 'M0 0V6', stroke: PASS, 'stroke-width': 1.2, opacity: 0.55 })),
    hatchPattern('hatch-redacted'));
