// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useState, type CSSProperties } from 'react';
import { postJson } from '../../api/client';
import { mayReset } from '../../api/profile';
import { ALL, useCached, type AppData } from '../../api/store';
import type { ValueChange } from '../../api/types';
import { cssColour, plmCode } from '../../ui/plm';
import { localName } from '../violations';
import { entriesOf, type Entry, type Side } from './changes';
import { valueText } from './offer';
import { t } from '../../i18n';

const toError = (e: unknown) => (e instanceof Error ? e : new Error(String(e)));
const time = (at: string) => {
  const d = new Date(at);
  return Number.isNaN(d.getTime()) ? at : d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
};

interface Props {
  data: AppData;
  /** Refetches every answer, as Run rules does. */
  onReset: () => void;
}

/** Above the answer path, for every profile: what changed since the released dataset, as the service derives it; the reset is the officer's. */
export function ChangesStrip({ data, onReset }: Props) {
  const changes = useCached(data.changes, ALL);
  const [confirming, setConfirming] = useState(false);
  const [reset, setReset] = useState<{ busy: boolean; error: Error | null }>({ busy: false, error: null });
  const entries = changes.data ? entriesOf(changes.data) : [];
  const n = entries.length;
  const run = async () => {
    setReset({ busy: true, error: null });
    try {
      await postJson(data.config, '/core/demo/reset', data.profile, {});
      setConfirming(false);
      setReset({ busy: false, error: null });
      onReset();
    } catch (e) {
      setReset({ busy: false, error: toError(e) });
    }
  };
  return (
    <section className="changes" aria-label="Changes since the released dataset">
      <header className="changes-head">
        <span className="eyebrow">{t('check.changes-strip.changesSinceTheReleasedDataset')}</span>
        {changes.data && !confirming ? (
          <span className="changes-live">
            {t('check.changes-strip.liveGraphs')} <b className="mono">{changes.data.graph.triples.links}</b> links · <b className="mono">{changes.data.graph.triples.fileindex}</b> file index triples
          </span>
        ) : null}
        {!mayReset(data.profile) ? null : confirming ? (
          <span className="changes-confirm" role="alertdialog" aria-label="Reset demo data">
            <span>{t('check.changes-strip.undo')} {n} {n === 1 ? 'change' : 'changes'} and put the released graphs back?</span>
            <button type="button" className="btn btn-small btn-ink" onClick={() => void run()} disabled={reset.busy}>
              {reset.busy ? 'Resetting...' : 'Reset'}
            </button>
            <button type="button" className="btn btn-small" onClick={() => setConfirming(false)} disabled={reset.busy}>{t('check.changes-strip.keep')}</button>
            {reset.error ? <span className="release-error">{reset.error.message}</span> : null}
          </span>
        ) : (
          <button type="button" className="btn btn-small" onClick={() => setConfirming(true)} disabled={n === 0}>{t('check.changes-strip.resetDemoData')}</button>
        )}
      </header>
      {changes.state === 'error' ? (
        <p className="changes-empty release-error">
          {t('check.changes-strip.theChangeFeedCouldNotBe')} {changes.error.message}{' '}
          <button type="button" className="btn btn-small" onClick={() => data.changes.retry(ALL)}>{t('check.changes-strip.tryAgain')}</button>
        </p>
      ) : !changes.data ? (
        <p className="changes-empty quiet">{t('check.changes-strip.readingTheChangeFeed')}</p>
      ) : n === 0 ? (
        <p className="changes-empty quiet">{t('check.changes-strip.releasedDatasetNoChanges')}</p>
      ) : (
        <ol className="changes-list">
          {entries.map((e) => <Row key={e.key} entry={e} />)}
        </ol>
      )}
    </section>
  );
}

function Row({ entry }: { entry: Entry }) {
  if (entry.kind === 'value') return <ValueRow change={entry.change} />;
  if (entry.kind === 'values') return <ValuesRow changes={entry.changes} />;
  if (entry.kind === 'link') {
    return (
      <li className="change is-link">
        <span className="change-at quiet">{t('check.changes-strip.request')}</span>
        <span className="change-text">
          <b>{t('check.changes-strip.atelierCore')}</b> wrote the link <Feature side={entry.from} /> <span className="mono quiet">{t('check.changes-strip.mateswith')}</span> <Feature side={entry.to} /> (request), event{' '}
          <span className="mono">{t('check.changes-strip.interfaceLinkAdded')}</span> emitted
        </span>
        <span className="change-graph is-plus">{t('check.changes-strip.linksGraph')}{entry.n} {entry.n === 1 ? 'triple' : 'triples'}</span>
      </li>
    );
  }
  if (entry.kind === 'equivalence') {
    return (
      <li className="change is-link">
        <span className="change-at quiet">{t('check.changes-strip.request')}</span>
        <span className="change-text">
          <b>{t('check.changes-strip.atelierCore')}</b> {t('check.changes-strip.confirmedOneItem')}{' '}
          {entry.parts.map((p, i) => <span key={p.id}>{i > 0 ? ', ' : null}<Feature side={p} /></span>)} <span className="mono quiet">{t('check.changes-strip.owlSameAs')}</span> (request), event{' '}
          <span className="mono">{t('check.changes-strip.equivalenceConfirmed')}</span> emitted
        </span>
        <span className="change-graph is-plus">{t('check.changes-strip.linksGraph')}{entry.n} {entry.n === 1 ? 'triple' : 'triples'}</span>
      </li>
    );
  }
  if (entry.kind === 'cad') {
    return (
      <li className="change is-cad">
        <span className="change-at quiet">{t('check.changes-strip.event')}</span>
        <span className="change-text">
          <span className="mono">{t('check.changes-strip.partCadPublished')}</span> from <b>{entry.plm ? plmCode(entry.plm) : '?'} PLM</b>: <span className="mono">{entry.part}</span>{' '}
          <span className="mono quiet">{t('check.changes-strip.cadfile')}</span> <span className="mono">{entry.cadFile}</span>
        </span>
        <span className="change-graph is-plus">{t('check.changes-strip.fileIndex1TripleCadfile')}</span>
      </li>
    );
  }
  const triple = entry.triple;
  const plus = entry.kind === 'added';
  return (
    <li className={`change is-${entry.kind}`}>
      <span className="change-at quiet">{plus ? 'added' : 'removed'}</span>
      <span className="change-text mono">{localName(triple.s)} {localName(triple.p)} {localName(triple.o)}</span>
      <span className={`change-graph ${plus ? 'is-plus' : 'is-minus'}`}>{t('check.changes-strip.graph')} {plus ? '+1 triple' : '-1 triple'}</span>
    </li>
  );
}

function ValueRow({ change: c }: { change: ValueChange }) {
  return (
    <li className="change is-value" style={{ '--plm': cssColour(c.plm) } as CSSProperties}>
      <span className="change-at mono">{time(c.at)}</span>
      <span className="change-text">
        <b>{plmCode(c.plm)} PLM</b> corrected a value: <span className="mono">{c.table}</span> <span className="mono">{c.key}</span>{' '}
        <span className="mono">{c.column}</span>{' '}
        <Delta change={c} />{' '}
        <span className="quiet">({c.actor})</span>, event <span className="mono">{t('check.changes-strip.partValueCorrected')}</span> logged
      </span>
      <span className="change-graph is-none">{t('check.changes-strip.graphNoChange')}</span>
    </li>
  );
}

const value = (v: ValueChange['before']) => valueText(v, t('check.release-cells.noValue'));

function Delta({ change: c }: { change: ValueChange }) {
  return (
    <span className="mono change-delta">
      {value(c.before)} <span className="quiet">-&gt;</span> {value(c.after)}
    </span>
  );
}

/** Rows shown in full before the rest of a release is counted; every row is in the title. */
const SHOWN = 2;

/** One release of several rows: the site, the table and columns once, the first rows' deltas, the count of the rest. */
function ValuesRow({ changes }: { changes: ValueChange[] }) {
  const c = changes[0];
  const tables = [...new Set(changes.map((x) => `${x.table} ${x.column}`))];
  const rest = changes.length - SHOWN;
  const all = changes.map((x) => `${x.table} ${x.key} ${x.column} ${value(x.before)} -> ${value(x.after)}`).join('\n');
  return (
    <li className="change is-value" style={{ '--plm': cssColour(c.plm) } as CSSProperties} title={all}>
      <span className="change-at mono">{time(c.at)}</span>
      <span className="change-text">
        <b>{plmCode(c.plm)} PLM</b> {t('check.changes-strip.corrected')} {changes.length} {t('check.changes-strip.valuesInOneRelease')}{' '}
        <span className="mono">{tables.join(', ')}</span>
        {changes.slice(0, SHOWN).map((x) => (
          <span key={x.id}>, <span className="mono">{x.key}</span> <Delta change={x} /></span>
        ))}
        {rest > 0 ? <span className="quiet">, +{rest} {t('check.changes-strip.more')}</span> : null}{' '}
        <span className="quiet">({c.actor})</span>, {t('check.changes-strip.event')} <span className="mono">{t('check.changes-strip.partValueCorrected')}</span> {t('check.changes-strip.logged')}
      </span>
      <span className="change-graph is-none">{t('check.changes-strip.graphNoChange')}</span>
    </li>
  );
}

function Feature({ side }: { side: Side }) {
  return (
    <span className="mono">
      {side.plm ? <b>{plmCode(side.plm)} </b> : null}
      {side.id}
    </span>
  );
}
