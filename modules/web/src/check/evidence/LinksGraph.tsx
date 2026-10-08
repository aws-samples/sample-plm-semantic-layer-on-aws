// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useMemo } from 'react';
import type { Interface } from '../../api/types';
import { cssColour, plmCode } from '../../ui/plm';
import { failingFeatures } from '../violations';
import { Atelier, kindOfIri, nativeId, parseTriples, plmOfIri, RDFS_LABEL } from './turtle';
import { t } from '../../i18n';

interface Row {
  left: string | null;
  right: string | null;
}

interface Model {
  iface: string;
  label: string | null;
  tol: string | null;
  parts: string[];
  /** File index: CAD file key per part IRI, as the link store states it. */
  cadFiles: Map<string, string>;
  rows: Row[];
  mated: (a: string, b: string) => boolean;
}

/** Interface, its two parts and its features as the link store states them, mated features on one row. */
function model(turtle: string): Model | null {
  const t = parseTriples(turtle);
  // The link store declares every feature with atelier:declaresFeature; older
  // link data used atelier:declaresPlug for plugs only.
  const decl = t.filter((x) => x.p === `${Atelier}declaresFeature` || x.p === `${Atelier}declaresPlug`);
  if (decl.length === 0) return null;
  const iface = decl[0].s;
  const features = [...new Set(decl.filter((x) => x.s === iface).map((x) => x.o))];
  const parts = [...new Set(t.filter((x) => x.s === iface && x.p === `${Atelier}betweenPart`).map((x) => x.o))];
  const mates = new Map<string, string>();
  for (const x of t) if (x.p === `${Atelier}matesWith`) mates.set(x.s, x.o);
  const leftPlm = plmOfIri(parts[0] ?? features[0]);
  const seen = new Set<string>();
  const rows: Row[] = [];
  for (const f of features) {
    if (seen.has(f)) continue;
    seen.add(f);
    const m = mates.get(f) ?? null;
    if (m) seen.add(m);
    rows.push(plmOfIri(f) === leftPlm ? { left: f, right: m } : { left: m, right: f });
  }
  return {
    iface,
    label: t.find((x) => x.s === iface && x.p === RDFS_LABEL)?.o ?? null,
    tol: t.find((x) => x.s === iface && x.p === `${Atelier}toleranceMm`)?.o ?? null,
    parts,
    cadFiles: new Map(t.filter((x) => x.p === `${Atelier}cadFile`).map((x) => [x.s, x.o])),
    rows,
    mated: (a, b) => mates.get(a) === b,
  };
}

/** Presigned STEP URL of a visible part, by native id; undefined when hidden or absent. */
function cadUrlOf(itf: Interface, id: string): string | undefined {
  for (const q of itf.parts) if (!q.redacted && q.id === id) return q.cadUrl ?? undefined;
  return undefined;
}

const W = 780;
const ROW = 46;
const Y0 = 152;
const COL = { left: 90, right: 460, w: 230 };

export function LinksGraph({ turtle, itf }: { turtle: string; itf: Interface }) {
  const g = useMemo(() => model(turtle), [turtle]);
  if (!g) return <p className="drawer-note">{t('check.links-graph.thisArmHasNoAtelierDeclaresfeature')}</p>;
  const failing = failingFeatures(itf);
  const H = Y0 + g.rows.length * ROW + 16;
  const feature = (iri: string, side: 'left' | 'right', cy: number) => {
    const x = COL[side];
    const colour = cssColour(plmOfIri(iri) ?? '');
    const id = nativeId(iri);
    return (
      <g key={iri}>
        <rect className="lg-box" x={x} y={cy - 17} width={COL.w} height={34} rx={2} style={{ stroke: colour }} />
        <rect x={x} y={cy - 17} width={3} height={34} fill={colour} />
        <text className="lg-plm" x={x + 12} y={cy - 4}>{plmCode(plmOfIri(iri) ?? '')} {kindOfIri(iri) ?? 'feature'}</text>
        <text className="lg-id" x={x + 12} y={cy + 10}>{id}</text>
        {failing.has(id) ? <circle className="lg-fail" cx={x + COL.w - 12} cy={cy} r={4.5} /> : null}
      </g>
    );
  };
  return (
    <figure className="links-figure">
      <svg className="links-graph" viewBox={`0 0 ${W} ${H}`} role="img" aria-label={`Links graph of ${nativeId(g.iface)}`}>
        <rect className="lg-box lg-iface" x={W / 2 - 150} y={10} width={300} height={46} rx={2} />
        <text className="lg-title" x={W / 2} y={30} textAnchor="middle">{g.label ?? nativeId(g.iface)}</text>
        <text className="lg-id" x={W / 2} y={47} textAnchor="middle">
          {nativeId(g.iface)}{g.tol !== null ? ` · toleranceMm ${g.tol}` : ''}
        </text>
        {g.parts.slice(0, 2).map((p, i) => {
          const x = i === 0 ? COL.left : COL.right;
          const colour = cssColour(plmOfIri(p) ?? '');
          return (
            <g key={p}>
              <line className="lg-wire" x1={W / 2 + (i === 0 ? -100 : 100)} y1={56} x2={x + COL.w / 2} y2={72} />
              <rect className="lg-box" x={x} y={72} width={COL.w} height={54} rx={2} style={{ stroke: colour }} />
              <rect x={x} y={72} width={3} height={54} fill={colour} />
              <text className="lg-plm" x={x + 12} y={86}>{plmCode(plmOfIri(p) ?? '')} part · betweenPart</text>
              <text className="lg-id" x={x + 12} y={101}>{nativeId(p)}</text>
              {g.cadFiles.has(p) ? (
                <a href={cadUrlOf(itf, nativeId(p))} target="_blank" rel="noreferrer">
                  <text className="lg-cad" x={x + 12} y={118}>{t('check.links-graph.cadfile')} {g.cadFiles.get(p)}</text>
                </a>
              ) : (
                <text className="lg-cad lg-cad-missing" x={x + 12} y={118}>{t('check.links-graph.noCadfileInTheFileIndex')}</text>
              )}
            </g>
          );
        })}
        {g.rows.map((row, i) => {
          const cy = Y0 + i * ROW + 17;
          return (
            <g key={i}>
              {[row.left, row.right].map((p, side) =>
                p ? (
                  <path
                    key={p}
                    className="lg-declares"
                    d={`M ${W / 2} 56 C ${W / 2} ${(56 + cy) / 2} ${COL[side ? 'right' : 'left'] + COL.w / 2} ${(56 + cy) / 2} ${COL[side ? 'right' : 'left'] + COL.w / 2} ${cy - 17}`}
                  />
                ) : null,
              )}
              {row.left && row.right && g.mated(row.left, row.right) ? (
                <g>
                  <line className="lg-wire" x1={COL.left + COL.w} y1={cy} x2={COL.right} y2={cy} />
                  <circle className="lg-pin" cx={COL.left + COL.w} cy={cy} r={3} />
                  <circle className="lg-pin" cx={COL.right} cy={cy} r={3} />
                  <text className="lg-label" x={W / 2} y={cy - 6} textAnchor="middle">{t('check.links-graph.mateswith')}</text>
                </g>
              ) : (
                <g>
                  <line className="lg-wire lg-orphan" x1={row.left ? COL.left + COL.w : COL.right} y1={cy} x2={row.left ? COL.left + COL.w + 60 : COL.right - 60} y2={cy} />
                  <text className="lg-orphan-text" x={W / 2} y={cy + 4} textAnchor="middle">{t('check.links-graph.noMateswith')}</text>
                </g>
              )}
              {row.left ? feature(row.left, 'left', cy) : null}
              {row.right ? feature(row.right, 'right', cy) : null}
            </g>
          );
        })}
      </svg>
      <figcaption className="lg-legend">
        <span><i className="lg-key lg-key-wire" /> atelier:matesWith, stated both ways</span>
        <span><i className="lg-key lg-key-declares" /> atelier:declaresFeature</span>
        <span><i className="lg-key lg-key-cad" /> atelier:cadFile from the file index, opens the STEP file</span>
        <span><i className="lg-key lg-key-fail" /> feature named by a violation</span>
      </figcaption>
    </figure>
  );
}
