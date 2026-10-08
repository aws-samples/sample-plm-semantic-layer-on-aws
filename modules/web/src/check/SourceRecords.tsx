// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { ReactNode } from 'react';
import type { Feature, Interface, PropertyValue } from '../api/types';
import { isPart } from '../api/types';
import { cssColour, plmCode } from '../ui/plm';
import { KindGlyph } from '../viewer/KindGlyph';
import { CadUnpublished } from './CadUnpublished';
import { FindingLine } from './Findings';
import { converted, detailProperties, differs, isQuantity, propertyKeys, propertyLabel, sameUnit } from './properties';
import { featureById, type Finding } from './violations';
import { t } from '../i18n';

const AXES = ['x', 'y', 'z'] as const;
const fmt = (v: number | null) => (v === null ? '-' : v.toLocaleString('en-GB', { maximumFractionDigits: 4, useGrouping: false }));

export interface RecordRow {
  key: string;
  label: string;
  cell: (f: Feature) => ReactNode;
  flagged?: boolean;
}

interface Props {
  itf: Interface;
  finding: Finding;
  /** One more row after the records, e.g. an action per feature. */
  extra?: RecordRow;
  /** A column singled out, e.g. the record off the joint plane: a tag under its header, a rule along its cells. */
  mark?: { id: string; label: string };
}

/** Both features' records as their PLMs store them, side by side; class-specific properties as the service returns them. */
export function SourceRecords({ itf, finding, extra, mark }: Props) {
  const fs = finding.features.map((id) => featureById(itf, id)).filter((f): f is Feature => !!f);
  if (fs.length === 1 && fs[0].matesWith[0]) {
    const mate = featureById(itf, fs[0].matesWith[0]);
    if (mate) fs.push(mate);
  }
  const first = finding.violations[0];
  const axis = typeof first.detail.axis === 'string' ? first.detail.axis : null;
  const badAxis = finding.rule === 'position' ? axis : null;
  const measured = detailProperties(first.detail);
  const pair = fs.length === 2;
  const bad = (row: string) =>
    (finding.rule === 'position' && row === 'mm') ||
    (finding.rule === 'unit' && (row === 'unit' || row === 'mm')) ||
    (finding.rule === 'kind' && row === 'kind');
  const cad = (f: Feature) => itf.parts.filter(isPart).find((x) => x.id === f.partId);

  const rows: RecordRow[] = [
    { key: 'id', label: 'Native id', cell: (f) => f.id },
    ...(finding.rule === 'kind' ? [{ key: 'kind', label: 'Class', cell: (f: Feature) => f.kind }] : []),
    { key: 'part', label: 'On part', cell: (f) => f.partId },
    { key: 'stored', label: 'Stored', cell: (f) => <Triple v={f.source} bad={badAxis} /> },
    { key: 'unit', label: 'Stored unit', cell: (f) => (f.source.unit ? f.source.unit : <span className="is-missing">{t('check.source-records.noUnit')}</span>) },
    { key: 'mm', label: 'In mm', cell: (f) => (f.positionMm ? <Triple v={f.positionMm} bad={badAxis} /> : <span className="is-missing">{t('check.source-records.notComputable')}</span>) },
    ...propertyKeys(fs).map((key): RecordRow => ({
      key: `p:${key}`,
      label: propertyLabel(key),
      cell: (f) => <Property value={f.properties[key]} />,
      flagged: measured.has(key) || (pair && differs(fs[0].properties[key], fs[1].properties[key])),
    })),
    {
      key: 'cad',
      label: 'CAD file',
      cell: (f) => {
        const p = cad(f);
        if (!p) return '-';
        return (
          <>
            {p.cadFile ?? <CadUnpublished plm={p.plm} />}
            {p.findings?.map((x) => <FindingLine key={x.rule} finding={x} />)}
          </>
        );
      },
    },
    ...(extra ? [extra] : []),
  ];

  return (
    <table className="records">
      <thead>
        <tr>
          <th scope="col" className="axes-note">x, y, z</th>
          {fs.map((f) => (
            <th key={f.plm + f.id} scope="col" className={mark?.id === f.id ? 'is-marked' : undefined}>
              <i className="swatch" style={{ background: cssColour(f.plm) }} />
              {plmCode(f.plm)} PLM
              <span className="th-kind"><KindGlyph kind={f.kind} status="outline" />{f.kind}</span>
              {mark?.id === f.id ? <span className="th-mark">{mark.label}</span> : null}
            </th>
          ))}
          {fs.length === 1 ? <th scope="col" className="is-missing">{t('check.source-records.matingFeature')}</th> : null}
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => (
          <tr key={r.key} className={(r.flagged ?? bad(r.key)) ? 'is-flagged' : undefined}>
            <th scope="row">{r.label}</th>
            {fs.map((f) => (
              <td key={f.plm + f.id} className={mark?.id === f.id ? 'mono is-marked' : 'mono'}>{r.cell(f)}</td>
            ))}
            {fs.length === 1 ? <td className="is-missing">{r.key === 'id' ? 'none in any PLM' : ''}</td> : null}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/** Value, stored unit and, when the unit is not already the normalised one, the converted value. */
function Property({ value }: { value: PropertyValue | undefined }) {
  if (value === undefined) return <span className="quiet">-</span>;
  if (!isQuantity(value)) return <>{String(value)}</>;
  const c = converted(value);
  return (
    <span className="qty">
      <span>{fmt(value.value)}</span>
      {value.unit ? <span className="unit">{value.unit}</span> : <span className="is-missing">{t('check.source-records.noUnit')}</span>}
      {c && !sameUnit(value) ? <span className="conv">= {fmt(c.value)} {c.unit}</span> : null}
      {!c ? <span className="is-missing">{t('check.source-records.notComputable')}</span> : null}
    </span>
  );
}

function Triple({ v, bad }: { v: { x: number | null; y: number | null; z: number | null }; bad: string | null }) {
  return (
    <span className="triple">
      {AXES.map((a) => (
        <span key={a} className={a === bad ? 'axis-bad' : undefined}>{fmt(v[a])}</span>
      ))}
    </span>
  );
}
