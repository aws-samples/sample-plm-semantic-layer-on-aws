// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { CellValue } from '../../api/types';
import { t } from '../../i18n';
import { valueText } from './offer';
import { unitLabel, valid, type Cell } from './proposals';

/** What the engineer has entered for a cell: text for a number, a word or a unit, a flag as is. */
export type Entry = string | boolean;

export const entryOf = (cell: Cell): Entry => (cell.input === 'flag' ? cell.value === true : cell.value === null ? '' : String(cell.value));

export function valueOf(cell: Cell, entry: Entry): CellValue {
  if (cell.input === 'flag') return entry === true;
  const s = String(entry);
  if (cell.input === 'number') return s.trim() === '' ? null : Number(s);
  return s;
}

export const isValid = (cell: Cell, entry: Entry) => valid(cell, valueOf(cell, entry));

interface InputProps {
  cell: Cell;
  entry: Entry;
  disabled: boolean;
  onChange: (e: Entry) => void;
}

/** The input a cell takes: a number, a text of the site's form, one of the site's words or units, a flag. */
export function CellInput({ cell, entry, disabled, onChange }: InputProps) {
  const invalid = !isValid(cell, entry);
  if (cell.input === 'flag') {
    return (
      <span className="release-flag">
        <input type="checkbox" checked={entry === true} disabled={disabled} onChange={(e) => onChange(e.target.checked)} />
        <span className="mono">{entry === true ? 'true' : 'false'}</span>
      </span>
    );
  }
  if (cell.input === 'choice') {
    return (
      <select className="release-input mono" value={String(entry)} disabled={disabled} aria-invalid={invalid} onChange={(e) => onChange(e.target.value)}>
        {(cell.choices ?? []).includes(String(entry)) ? null : <option value={String(entry)}>{t('check.release-cells.choose')}</option>}
        {(cell.choices ?? []).map((c) => <option key={c} value={c}>{c}</option>)}
      </select>
    );
  }
  return (
    <input
      className={`release-input mono${cell.input === 'text' ? ' is-wide' : ''}`}
      inputMode={cell.input === 'number' ? 'decimal' : undefined}
      value={String(entry)}
      title={cell.pattern ? `${t('check.release-cells.form')} ${cell.pattern}` : undefined}
      onChange={(e) => onChange(e.target.value)}
      disabled={disabled}
      aria-invalid={invalid}
    />
  );
}

/** One cell of the correction: the native column, the input, the unit and the value stored. */
export function CellField({ cell, entry, disabled, onChange }: InputProps) {
  return (
    <label className="release-field">
      <span className="mono release-column">{cell.column}</span>
      <CellInput cell={cell} entry={entry} disabled={disabled} onChange={onChange} />
      {cell.unit ? <span className="unit">{unitLabel(cell.unit)}</span> : null}
      <span className="quiet">
        {cell.label} · {t('check.release-cells.stored')} {valueText(cell.before, t('check.release-cells.noValue'))}
      </span>
    </label>
  );
}

interface BatchProps {
  cells: Cell[];
  entries: Entry[];
  disabled: boolean;
  onChange: (i: number, e: Entry) => void;
}

/** Many rows of one column in one release: key, value stored and value proposed, one line each, scrolling. */
export function CellBatch({ cells, entries, disabled, onChange }: BatchProps) {
  const first = cells[0];
  return (
    <div className="release-batch">
      <p className="release-where">
        <b>{cells.length}</b> {t('check.release-cells.rows')} · <span className="mono release-column">{first.column}</span>
        {first.unit ? <span className="unit"> {unitLabel(first.unit)}</span> : null}
      </p>
      <div className="release-batch-rows">
        <table className="records release-rows">
          <thead>
            <tr>
              <th scope="col">{t('check.release-cells.key')}</th>
              <th scope="col">{t('check.release-cells.stored')}</th>
              <th scope="col">{t('check.release-cells.proposed')}</th>
            </tr>
          </thead>
          <tbody>
            {cells.map((c, i) => (
              <tr key={`${c.key}|${c.column}`}>
                <td className="mono">{c.key}</td>
                <td className="mono">{valueText(c.before, t('check.release-cells.noValue'))}</td>
                <td><CellInput cell={c} entry={entries[i]} disabled={disabled} onChange={(e) => onChange(i, e)} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
