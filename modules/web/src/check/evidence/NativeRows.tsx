// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useCached, type AppData } from '../../api/store';
import type { Cell, EvidenceArm, EvidenceTable, Interface, TableRows } from '../../api/types';
import { ErrorState } from '../../ui/ErrorState';
import { cssColour, ownerLabel } from '../../ui/plm';
import { failingFeatures } from '../violations';
import { violationColumns, type AboutColumns } from './columns';
import { t } from '../../i18n';

export const tablePath = (t: EvidenceTable) =>
  `/${t.plm}/tables/${encodeURIComponent(t.table)}?keys=${t.keys.map(encodeURIComponent).join(',')}`;

interface Props {
  data: AppData;
  itf: Interface;
  arm: EvidenceArm;
}

/** The rows the arm's triples came from, read back from the PLM service by primary key. */
export function NativeRows({ data, itf, arm }: Props) {
  // A table listed without keys had no row to read for this interface.
  const tables = arm.tables.filter((t) => t.keys.length > 0);
  if (tables.length === 0) return <p className="drawer-note">{t('check.native-rows.thisArmReadsNoNativeTable')}</p>;
  return (
    <>
      {tables.map((t) => <NativeTable key={`${t.plm}/${t.table}`} data={data} itf={itf} table={t} />)}
    </>
  );
}

function NativeTable({ data, itf, table }: { data: AppData; itf: Interface; table: EvidenceTable }) {
  const path = tablePath(table);
  const rows = useCached(data.tables, path);
  const catalogue = useCached(data.catalogues, table.plm);
  const about = violationColumns(itf, catalogue.data ?? null, table.table);
  const failing = failingFeatures(itf);
  return (
    <section className="native-table">
      <header className="native-head">
        <span className="eyebrow">
          <i className="swatch" style={{ background: cssColour(table.plm) }} />
          {ownerLabel(table.plm)} · <span className="mono">{table.table}</span>
        </span>
        <span className="mono quiet">{t('check.native-rows.getApi')}{path}</span>
      </header>
      {rows.state === 'error' ? (
        <ErrorState title={`${table.table} could not be read`} error={rows.error} onRetry={() => data.tables.retry(path)} />
      ) : rows.state === 'loading' ? (
        <p className="drawer-note">{t('check.native-rows.reading')} {table.keys.length} {table.keys.length === 1 ? 'row' : 'rows'} by primary key.</p>
      ) : (
        <NativeGrid rows={rows.data} about={about} failing={failing} />
      )}
    </section>
  );
}

function NativeGrid({ rows, about, failing }: { rows: TableRows; about: AboutColumns; failing: Set<string> }) {
  const keyIdx = rows.keyColumn ? rows.columns.indexOf(rows.keyColumn) : -1;
  /** The failing feature a row belongs to: its primary key, or without a key column any cell naming one. */
  const featureOf = (r: Cell[]): string | null => {
    const cells = keyIdx >= 0 ? [r[keyIdx]] : r;
    return (cells.find((v) => typeof v === 'string' && failing.has(v)) as string | undefined) ?? null;
  };
  return (
    <table className="native-rows mono">
      <thead>
        <tr>
          {rows.columns.map((c) => (
            <th key={c} scope="col" className={about.all.has(c) ? 'is-about' : undefined}>{c}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.rows.map((r, i) => {
          const feature = featureOf(r);
          return (
            <tr key={i} className={feature ? 'is-flagged' : undefined}>
              {r.map((v, j) => (
                <td key={j} className={feature && about.of(rows.columns[j], feature) ? 'is-about' : undefined}>
                  {v === null ? <span className="is-null">{t('check.native-rows.null')}</span> : String(v)}
                </td>
              ))}
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
