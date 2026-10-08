// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useEffect, useRef, useState } from 'react';
import { FLOWS, type FlowKind } from '../api/pathTypes';
import type { Part } from '../api/types';
import { t } from '../i18n';
import { pathHash, type PathQuery } from './pathRoute';

interface Props {
  query: PathQuery | null;
  /** The parts of the product the profile may see, for the search boxes. */
  parts: Part[];
  /** The part last clicked in the view, with a counter so the same part clicked twice counts twice. */
  picked: { id: string; n: number } | null;
  onQuery: (q: PathQuery) => void;
}

type Mode = PathQuery['mode'];

/** The five boxes of the form. */
interface Fields {
  mode: Mode;
  from: string;
  to: string;
  flow: FlowKind | null;
  up: boolean;
}

/** The boxes a query fills; a blank form, asking for a flow, when there is none. */
const fieldsOf = (q: PathQuery | null): Fields => ({
  mode: q?.mode ?? 'flow',
  from: q?.from ?? '',
  to: q?.mode === 'connect' ? q.to : '',
  flow: q?.mode === 'flow' ? q.flow : null,
  up: q?.mode === 'flow' ? q.up : false,
});

const LIST_ID = 'path-parts';

/**
 * Two questions about one product: which interfaces join two parts, or where power and motion go from one part. The
 * parts are named in a search box over the product's parts or by a click in the view, which fills the next empty box
 * and, once the question is complete, asks it.
 */
export function PathForm({ query, parts, picked, onQuery }: Props) {
  // The boxes show the query, under the person's edits. The edits belong to the query they were typed against, so
  // a link or the agent changing the path drops them and the boxes follow the path.
  const [edits, setEdits] = useState<{ of: string; fields: Partial<Fields> }>({ of: '', fields: {} });
  const of = pathHash(query);
  const { mode, from, to, flow, up }: Fields = { ...fieldsOf(query), ...(edits.of === of ? edits.fields : {}) };
  const edit = (f: Partial<Fields>) => setEdits((e) => ({ of, fields: { ...(e.of === of ? e.fields : {}), ...f } }));

  const known = new Set(parts.map((p) => p.id));
  const ask = (q: Partial<Fields> = {}) => {
    const next = { mode, from, to, flow, up, ...q };
    if (!known.has(next.from)) return;
    if (next.mode === 'connect') {
      if (known.has(next.to)) onQuery({ mode: 'connect', from: next.from, to: next.to });
    } else onQuery({ mode: 'flow', from: next.from, flow: next.flow, up: next.up });
  };

  // A click in the view fills the start, then the end of a connection.
  const handled = useRef(-1);
  useEffect(() => {
    if (!picked || handled.current === picked.n) return;
    handled.current = picked.n;
    if (mode === 'connect' && from && known.has(from) && picked.id !== from) {
      edit({ to: picked.id });
      ask({ to: picked.id });
    } else if (mode === 'flow') {
      edit({ from: picked.id });
      ask({ from: picked.id });
    } else edit({ from: picked.id, to: '' });
  }, [picked]);

  return (
    <form className="path-form" onSubmit={(e) => { e.preventDefault(); ask(); }}>
      <div className="path-modes" role="radiogroup" aria-label={t('paths.path-form.question')}>
        <button type="button" role="radio" aria-checked={mode === 'flow'} className={`path-mode${mode === 'flow' ? ' is-on' : ''}`} onClick={() => edit({ mode: 'flow' })}>
          {t('paths.path-form.followTheFlow')}
        </button>
        <button type="button" role="radio" aria-checked={mode === 'connect'} className={`path-mode${mode === 'connect' ? ' is-on' : ''}`} onClick={() => edit({ mode: 'connect' })}>
          {t('paths.path-form.joinedByInterfaces')}
        </button>
      </div>
      <p className="path-lede">{mode === 'flow' ? t('paths.path-form.flowLede') : t('paths.path-form.connectLede')}</p>
      <datalist id={LIST_ID}>
        {parts.map((p) => <option key={`${p.plm}|${p.id}`} value={p.id} label={`${p.plm.toUpperCase()} · ${p.name}`} />)}
      </datalist>
      <label className="path-field">
        <span className="eyebrow">{mode === 'flow' ? t('paths.path-form.startFrom') : t('paths.path-form.from')}</span>
        <input className="path-input mono" list={LIST_ID} value={from} placeholder={t('paths.path-form.partIdOrClick')} onChange={(e) => edit({ from: e.target.value.trim() })} />
      </label>
      {mode === 'connect' ? (
        <label className="path-field">
          <span className="eyebrow">{t('paths.path-form.to')}</span>
          <input className="path-input mono" list={LIST_ID} value={to} placeholder={t('paths.path-form.partIdOrClick')} onChange={(e) => edit({ to: e.target.value.trim() })} />
        </label>
      ) : (
        <div className="path-row">
          <label className="path-field">
            <span className="eyebrow">{t('paths.path-form.flow')}</span>
            <select className="path-input" value={flow ?? ''} onChange={(e) => { const f = (e.target.value || null) as FlowKind | null; edit({ flow: f }); ask({ flow: f }); }}>
              <option value="">{t('paths.path-form.everyKind')}</option>
              {FLOWS.map((f) => <option key={f} value={f}>{f}</option>)}
            </select>
          </label>
          <label className="path-field">
            <span className="eyebrow">{t('paths.path-form.direction')}</span>
            <select className="path-input" value={up ? 'up' : 'down'} onChange={(e) => { const u = e.target.value === 'up'; edit({ up: u }); ask({ up: u }); }}>
              <option value="down">{t('paths.path-form.downstream')}</option>
              <option value="up">{t('paths.path-form.upstream')}</option>
            </select>
          </label>
        </div>
      )}
      <div className="path-actions">
        <button type="submit" className="btn btn-small" disabled={!known.has(from) || (mode === 'connect' && !known.has(to))}>
          {mode === 'flow' ? t('paths.path-form.followFlow') : t('paths.path-form.findPaths')}
        </button>
        <span className="quiet path-hint">{t('paths.path-form.clickAPartInTheView')}</span>
      </div>
    </form>
  );
}
