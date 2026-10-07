// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { Fragment, useRef, useState } from 'react';
import type { AppData } from '../../api/store';
import { postJson } from '../../api/client';
import type { ChangeResult, PreviewResponse } from '../../api/types';
import { t } from '../../i18n';
import { cssColour, dbName, ownerLabel } from '../../ui/plm';
import { valueText } from './offer';
import { previewOf, requestOf, type Cell, type Proposal } from './proposals';
import { CellBatch, CellField, entryOf, isValid, valueOf, type Entry } from './ReleaseCells';
import { ReleasePreview } from './ReleasePreview';
import { useRelease } from './useRelease';

interface Props {
  data: AppData;
  proposal: Proposal;
  onClose: () => void;
  /** Runs once every row has reached the change list: the screen reads the site again and the rules run. */
  onReleased: () => void;
}

/** More rows of one column than this show as a scrolling table rather than one field each. */
const BATCH = 3;
const isBatch = (cells: Cell[]) => cells.length > BATCH && cells.every((c) => c.table === cells[0].table && c.column === cells[0].column);

/** Consecutive cells of one row, so the table and key are said once per row. */
function byRow(cells: Cell[]): { table: string; key: string; at: number[] }[] {
  const rows: { table: string; key: string; at: number[] }[] = [];
  cells.forEach((c, i) => {
    const last = rows[rows.length - 1];
    if (last && last.table === c.table && last.key === c.key) last.at.push(i);
    else rows.push({ table: c.table, key: c.key, at: [i] });
  });
  return rows;
}

/** The preview of the values entered: none yet, being computed, the rules' answer, or why it failed. */
type Preview = { kind: 'idle' } | { kind: 'loading' } | { kind: 'done'; answer: PreviewResponse } | { kind: 'error'; error: Error };

const none = () => t('check.release-cells.noValue');
const one = (r: ChangeResult) => (r.rows.length === 1 ? r.rows[0] : null);

/**
 * The correction as the owning site releases it, in its own PLM: each cell's native table, key and column, the value
 * stored and the value proposed, which the engineer may edit. One POST to the site's own service applies every cell in
 * one transaction; the site then announces part.value.corrected and the layer reads the new values on its next run.
 */
export function ReleaseCorrection({ data, proposal, onClose, onReleased }: Props) {
  const { plm, cells, purpose } = proposal;
  const [entries, setEntries] = useState<Entry[]>(() => cells.map(entryOf));
  const { post, release, keepWaiting } = useRelease(data, plm, onReleased);
  const [preview, setPreview] = useState<Preview>({ kind: 'idle' });
  // An answer for values since edited is dropped: each edit and each preview takes a new number.
  const asked = useRef(0);
  const owner = ownerLabel(plm);
  const valid = cells.every((c, i) => isValid(c, entries[i]));
  // A release that writes back what is stored would log a correction that changes nothing.
  const ok = valid && cells.some((c, i) => valueOf(c, entries[i]) !== c.before);
  const busy = post.kind === 'posting' || post.kind === 'waiting' || post.kind === 'done';
  const set = (i: number, e: Entry) => {
    setEntries((es) => es.map((x, j) => (j === i ? e : x)));
    asked.current += 1;
    setPreview({ kind: 'idle' });
  };
  const product = data.product?.key;
  const runPreview = async () => {
    if (!product) return;
    const mine = ++asked.current;
    setPreview({ kind: 'loading' });
    let next: Preview;
    try {
      const body = previewOf(proposal, cells.map((c, i) => valueOf(c, entries[i])), product, data.root);
      next = { kind: 'done', answer: await postJson<PreviewResponse>(data.config, '/query/preview', data.profile, body) };
    } catch (e) {
      next = { kind: 'error', error: e instanceof Error ? e : new Error(String(e)) };
    }
    if (asked.current === mine) setPreview(next);
  };
  const submit = () => {
    if (ok && !busy) void release(requestOf(proposal, cells.map((c, i) => valueOf(c, entries[i]))));
  };
  const many = cells.length > 1;
  return (
    <form
      className="release"
      style={{ borderLeftColor: cssColour(plm) }}
      aria-label={`${t('check.correctable-records.releaseCorrectionIn')} ${owner}`}
      onSubmit={(e) => {
        e.preventDefault();
        submit();
      }}
    >
      <div className="release-head">
        <span className="eyebrow">{t('check.correctable-records.releaseCorrectionIn')} {owner}</span>
        <button type="button" className="btn btn-small" onClick={onClose} disabled={post.kind === 'posting' || post.kind === 'waiting'}>
          {t('check.correctable-records.cancel')}
        </button>
      </div>
      {isBatch(cells) ? (
        <>
          <p className="release-where mono">{dbName(plm)}.{cells[0].table}</p>
          <CellBatch cells={cells} entries={entries} disabled={busy} onChange={set} />
        </>
      ) : (
        byRow(cells).map((row) => (
          <Fragment key={`${row.table}|${row.key}`}>
            <p className="release-where mono">{dbName(plm)}.{row.table} · {t('check.release-correction.key')} {row.key}</p>
            {row.at.map((i) => <CellField key={cells[i].column} cell={cells[i]} entry={entries[i]} disabled={busy} onChange={(e) => set(i, e)} />)}
          </Fragment>
        ))
      )}
      <p className="release-note quiet">
        <span className="mono">{t('check.correctable-records.post')}{plm}/demo/update</span>:{' '}
        {many ? `${cells.length} ${t('check.release-correction.updatesInOneTransaction')}` : t('check.release-correction.oneUpdate')} {t('check.release-correction.inThe')} {owner}
        {t('check.release-correction.ownTablesByItsOwnService')} <span className="mono">{t('check.correctable-records.partValueCorrected')}</span>
        {many ? ` ${t('check.release-correction.namingEveryRow')}` : ''}. {t('check.release-correction.nothingIsWrittenToAnyGraph')}{' '}
        {t(many ? 'check.release-correction.theNewValues' : 'check.release-correction.theNewValue')}{t('check.release-correction.atelierLogsTheEventAs')} &quot;{purpose}&quot;.
      </p>
      <div className="release-actions">
        <button type="submit" className="btn btn-ink" disabled={busy || !ok}>
          {post.kind === 'posting' ? t('check.release-correction.releasing') : t('check.release-correction.release')}
        </button>
        <button
          type="button"
          className="btn btn-small"
          title={t('check.release-preview.title')}
          disabled={busy || !valid || !product || preview.kind === 'loading'}
          onClick={() => void runPreview()}
        >
          {preview.kind === 'loading' ? t('check.release-preview.previewing') : t('check.release-preview.preview')}
        </button>
        {post.kind === 'waiting' ? (
          <span className="publish-wait" role="status">
            <span className="loading-bar" />
            {owner} {t('check.release-correction.set')} {setText(post.result)} · {t('check.release-correction.waitingFor')}{' '}
            <span className="mono">{t('check.correctable-records.partValueCorrected')}</span> · {post.elapsed} s
          </span>
        ) : null}
        {post.kind === 'done' ? (
          <span className="release-done">
            {t('check.correctable-records.released')} {doneText(post.result)}; {t('check.release-correction.event')}{' '}
            <span className="mono">{t('check.correctable-records.partValueCorrected')}</span> {t('check.release-correction.loggedRulesRunning')}
          </span>
        ) : null}
        {post.kind === 'timeout' ? (
          <span className="release-error">
            {owner} {t('check.release-correction.set')} {setText(post.result)}, {t('check.release-correction.butItsEventHasNotReached')}{' '}
            <button type="button" className="btn btn-small" onClick={() => keepWaiting(post.result)}>
              {t('check.correctable-records.keepWaiting')}
            </button>
          </span>
        ) : null}
        {post.kind === 'error' ? <span className="release-error">{post.error.message}</span> : null}
        {preview.kind === 'error' ? <span className="release-error">{preview.error.message}</span> : null}
      </div>
      {preview.kind === 'done' ? <ReleasePreview answer={preview.answer} /> : null}
    </form>
  );
}

/** What the PLM answered it set: the value of one row, the count of several. */
function setText(r: ChangeResult) {
  const row = one(r);
  return row ? valueText(row.after, none()) : `${r.rows.length} ${t('check.release-cells.rows')}`;
}

function doneText(r: ChangeResult) {
  const row = one(r);
  return row
    ? `${valueText(row.before, none())} ${t('check.release-correction.to')} ${valueText(row.after, none())}`
    : `${r.rows.length} ${t('check.release-cells.rows')}`;
}
