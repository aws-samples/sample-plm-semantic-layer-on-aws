// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { CSSProperties } from 'react';
import { OFFICER } from '../api/profile';
import { ALL, useCached, type AppData } from '../api/store';
import type { Changes } from '../api/types';
import { entriesOf, sideText, type Entry } from '../check/demo/changes';
import { localName } from '../check/violations';
import { cssColour, plmCode } from '../ui/plm';
import { countsText, type ChangePaths } from './changePaths';
import { CHANGE_REQUEST, plural } from './drawn';
import { Fact } from './Fact';
import { t } from '../i18n';
import { valueText } from '../check/demo/offer';

const time = (at: string) => {
  const d = new Date(at);
  return Number.isNaN(d.getTime()) ? at : d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
};

const counted = (c: Changes): ChangePaths['counts'] => {
  const e = entriesOf(c);
  return { values: e.filter((x) => x.kind === 'value').length, links: e.filter((x) => x.kind === 'link').length, cad: e.filter((x) => x.kind === 'cad').length };
};

/** The change feed when nothing is selected: the three kinds, then every entry as the strip lists them. */
export function ChangeSummary({ changes }: { changes: Changes }) {
  const entries = entriesOf(changes);
  const g = changes.graph.triples;
  return (
    <>
      <h2 className="eyebrow">{t('flow.change-facts.lastChange')}</h2>
      <p className="node-does">{t('flow.change-facts.drawnFromTheChangeFeed')} {countsText(counted(changes))}. Each entry took the path of the system that owns the fact; the browser called that service directly.</p>
      <dl className="facts">
        <Fact label="Requests"><span className="mono">{CHANGE_REQUEST}</span></Fact>
        <Fact label="Feed"><span className="mono">{t('flow.change-facts.getApiQueryDemoChanges')}</span>, read through the query service, which writes nothing</Fact>
        <Fact label="Drawn for"><span className="mono">{OFFICER}</span></Fact>
        <Fact label="Live graphs"><span className="mono">{g.links}</span> links · <span className="mono">{g.fileindex}</span> file index triples</Fact>
      </dl>
      {entries.length ? (
        <>
          <h3 className="eyebrow">{t('flow.change-facts.entries')}</h3>
          <ol className="change-facts">
            {entries.map((e) => <EntryRow key={e.key} entry={e} />)}
          </ol>
        </>
      ) : (
        <p className="quiet">
          {t('flow.change-facts.releasedDatasetNothingToDrawRelease')}
        </p>
      )}
      <p className="quiet hint">{t('flow.change-facts.selectAComponentToFollowIts')}</p>
    </>
  );
}

const shown = (v: string | number | null) => valueText(v, t('check.release-cells.noValue'));

function EntryRow({ entry }: { entry: Entry }) {
  if (entry.kind === 'value') {
    const c = entry.change;
    return (
      <li className="change-fact is-value" style={{ '--plm': cssColour(c.plm) } as CSSProperties}>
        <span className="change-fact-kind"><b>{plmCode(c.plm)} PLM</b> corrected a value <span className="mono quiet">{time(c.at)}</span></span>
        <span className="mono">{c.table}.{c.column} {shown(c.before)} <span className="quiet">-&gt;</span> {shown(c.after)}</span>
        <span className="change-graph is-none">{t('flow.change-facts.graphNoChangePartValueCorrected')}</span>
      </li>
    );
  }
  if (entry.kind === 'values') {
    const c = entry.changes[0];
    const columns = [...new Set(entry.changes.map((x) => `${x.table}.${x.column}`))].join(', ');
    return (
      <li className="change-fact is-value" style={{ '--plm': cssColour(c.plm) } as CSSProperties}>
        <span className="change-fact-kind">
          <b>{plmCode(c.plm)} PLM</b> {t('check.changes-strip.corrected')} {entry.changes.length} {t('check.changes-strip.valuesInOneRelease')} <span className="mono quiet">{time(c.at)}</span>
        </span>
        <span className="mono">{columns}</span>
        <span className="change-graph is-none">{t('flow.change-facts.graphNoChangePartValueCorrected')}</span>
      </li>
    );
  }
  if (entry.kind === 'link') {
    return (
      <li className="change-fact is-link">
        <span className="change-fact-kind"><b>{t('flow.change-facts.atelierCore')}</b> wrote a link on request</span>
        <span className="mono">{sideText(entry.from)} matesWith {sideText(entry.to)}</span>
        <span className="change-graph is-plus">{t('flow.change-facts.linksGraph')}{entry.n} {entry.n === 1 ? 'triple' : 'triples'} · interface.link.added</span>
      </li>
    );
  }
  if (entry.kind === 'equivalence') {
    return (
      <li className="change-fact is-link">
        <span className="change-fact-kind"><b>{t('flow.change-facts.atelierCore')}</b> {t('flow.change-facts.confirmedAnEquivalence')}</span>
        <span className="mono">{entry.parts.map(sideText).join(' sameAs ')}</span>
        <span className="change-graph is-plus">{t('flow.change-facts.linksGraph')}{entry.n} {entry.n === 1 ? 'triple' : 'triples'} · equivalence.confirmed</span>
      </li>
    );
  }
  if (entry.kind === 'cad') {
    const plm = entry.plm;
    return (
      <li className="change-fact is-cad" style={{ '--plm': plm ? cssColour(plm) : undefined } as CSSProperties}>
        <span className="change-fact-kind"><b>{plm ? plmCode(plm) : '?'} PLM</b> published a CAD file by event</span>
        <span className="mono">{entry.part} cadFile {entry.cadFile}</span>
        <span className="change-graph is-plus">{t('flow.change-facts.fileIndex1TriplePartCad')}</span>
      </li>
    );
  }
  const triple = entry.triple;
  const plus = entry.kind === 'added';
  return (
    <li className={`change-fact is-${entry.kind}`}>
      <span className="change-fact-kind">{plus ? 'added' : 'removed'}</span>
      <span className="mono">{localName(triple.s)} {localName(triple.p)} {localName(triple.o)}</span>
      <span className={`change-graph ${plus ? 'is-plus' : 'is-minus'}`}>{t('flow.change-facts.graph')} {plus ? '+1 triple' : '-1 triple'}</span>
    </li>
  );
}

/** The health answer beside a fact, in the words the query service used. */
function useHealth(data: AppData) {
  return useCached(data.demoHealth, ALL);
}

const healthState = (h: ReturnType<typeof useHealth>) =>
  h.state === 'ready' ? 'answered' : h.state === 'error' ? 'did not answer' : 'waiting for the answer';

/** The writer the health answer names, in the words of the diagram. */
export const writerLabel = (writer: string) => (writer === 'atelier-core' ? 'Atelier core service' : writer);

const RELEASED_EVENTS = ['atelier.plm/part.value.corrected', 'atelier.plm/part.cad.published', 'atelier.graph/interface.link.added', 'atelier.graph/equivalence.confirmed'];

/** The EventBridge card: who publishes what on the bus, who listens, and the events the feed holds. */
export function EventBridgeFacts({ data, changes }: { data: AppData; changes: Changes | null }) {
  const health = useHealth(data);
  const h = health.state === 'ready' ? health.data : null;
  const counts = changes ? counted(changes) : null;
  const writer = writerLabel(h?.writer ?? 'atelier-core');
  // An event's source names its publisher: atelier.plm is the PLM service that owns the fact, atelier.graph is the graph's writer.
  const publisher = (event: string) => (event.startsWith('atelier.plm/') ? 'a PLM service, its fact' : `the ${writer}, after the write`);
  return (
    <>
      <dl className="facts">
        <Fact label="Health"><span className="mono">{t('flow.change-facts.getApiQueryDemoHealth')}</span> {healthState(health)}</Fact>
        <Fact label="Writer">{writer}: writes the links graph, then publishes; the query service only reads</Fact>
        <Fact label="Rules">{t('flow.change-facts.deliver')} <span className="mono">{t('flow.change-facts.partValueCorrected')}</span> and <span className="mono">{t('flow.change-facts.partCadPublished')}</span> to the links loader</Fact>
        <Fact label="Not watched">{t('flow.change-facts.anyStoreAPlmAnnouncesIts')}</Fact>
      </dl>
      <h3 className="eyebrow">{t('flow.change-facts.published')}</h3>
      <ul className="fact-list is-stacked">
        {(h?.publishes ?? RELEASED_EVENTS).map((p) => <li key={p}><span className="mono">{p}</span><span className="quiet">{publisher(p)}</span></li>)}
      </ul>
      {h ? (
        <>
          <h3 className="eyebrow">{t('flow.change-facts.subscriptions')}</h3>
          <ul className="fact-list">
            {h.subscriptions.map((s) => <li key={s}><span className="mono">{s}</span></li>)}
          </ul>
        </>
      ) : null}
      {counts ? (
        <>
          <h3 className="eyebrow">{t('flow.change-facts.eventsInTheChangeFeed')}</h3>
          <ul className="fact-list">
            <li><span className="mono"><b>{t('flow.change-facts.partValueCorrected')}</b></span><span className="quiet">{plural(counts.values, 'event')}</span></li>
            <li><span className="mono"><b>{t('flow.change-facts.interfaceLinkAdded')}</b></span><span className="quiet">{plural(counts.links, 'event')}</span></li>
            <li><span className="mono"><b>{t('flow.change-facts.partCadPublished')}</b></span><span className="quiet">{plural(counts.cad, 'event')}</span></li>
          </ul>
        </>
      ) : null}
    </>
  );
}

/** The links loader card: its triggers, its writes, and what landed. */
export function LoaderFacts({ data, changes }: { data: AppData; changes: Changes | null }) {
  const health = useHealth(data);
  const h = health.state === 'ready' ? health.data : null;
  const counts = changes ? counted(changes) : null;
  return (
    <dl className="facts">
      <Fact label="Runtime">{t('flow.change-facts.lambdaInTheGraphStack')}</Fact>
      <Fact label="Triggers">{t('flow.change-facts.eventbridgeRulesOn')} <span className="mono">{t('flow.change-facts.atelierPlm')}</span> / <span className="mono">{t('flow.change-facts.partCadPublished')}</span> and <span className="mono">{t('flow.change-facts.partValueCorrected')}</span></Fact>
      <Fact label="Writes">{t('flow.change-facts.one')} <span className="mono">{t('flow.change-facts.atelierCadfile')}</span> per part into the file index, SPARQL <span className="mono">{t('flow.change-facts.deleteInsert')}</span></Fact>
      <Fact label="Logs">{t('flow.change-facts.eachCorrectionIn')} <span className="mono">{t('flow.change-facts.atelierCoreDemoChange')}</span> through <span className="mono">{t('flow.change-facts.postCoreChanges')}</span> on the core service; a reset's inverse corrections are skipped</Fact>
      <Fact label="Also">{t('flow.change-facts.loadsTheReleased')} <span className="mono">{t('flow.change-facts.linksTtl')}</span> and <span className="mono">{t('flow.change-facts.fileindexTtl')}</span> at deploy time</Fact>
      <Fact label="Wiring">{h ? h.subscriptions.map((s) => <span key={s} className="mono">{s}</span>) : `health ${healthState(health)}`}</Fact>
      <Fact label="Released"><span className="mono">{h ? h.releasedGraphs.fileindex : '?'}</span> file index triples in the image</Fact>
      {counts ? <Fact label="Landed">{plural(counts.cad, 'CAD publication')}, {plural(counts.values, 'correction')} logged</Fact> : null}
    </dl>
  );
}

/** The demo_change card: the log the core service writes, and its rows when the officer can read them. */
export function ChangeLogFacts({ data, changes }: { data: AppData; changes: Changes | null }) {
  const health = useHealth(data);
  const table = health.state === 'ready' ? health.data.changeLog : 'atelier_core.demo_change';
  const officer = data.profile === OFFICER;
  return (
    <>
      <dl className="facts">
        <Fact label="Table"><span className="mono">{table}</span></Fact>
        <Fact label="Written by">{t('flow.change-facts.theCoreServiceOn')} <span className="mono">{t('flow.change-facts.postCoreChanges')}</span>, one row per <span className="mono">{t('flow.change-facts.partValueCorrected')}</span> event the links loader delivers</Fact>
        <Fact label="Columns"><span className="mono">{t('flow.change-facts.plmTableKeyColumnBeforeAfter')}</span></Fact>
        <Fact label="Not in">{t('flow.change-facts.theCatalogueTheMappingOrAny')}</Fact>
        <Fact label="Reset"><span className="mono">{t('flow.change-facts.postCoreDemoReset')}</span>: the core service replays the rows in reverse through each PLM's <span className="mono">{t('flow.change-facts.demoUpdate')}</span> (via the API gateway), puts the released graphs back (Graph Store <span className="mono">{t('flow.change-facts.put')}</span>), then deletes them</Fact>
        <Fact label="Rows">
          {changes ? plural(changes.values.length, 'row') : officer ? 'reading the change feed' : `read through the officer's change feed; ${data.profile} does not see it`}
        </Fact>
      </dl>
      {changes && changes.values.length ? (
        <ul className="fact-list">
          {changes.values.map((v) => (
            <li key={v.id}>
              <span className="mono"><i className="swatch" style={{ background: cssColour(v.plm) }} />{v.table}.{v.column} {String(v.before)} -&gt; {String(v.after)}</span>
              <span className="quiet">{time(v.at)}</span>
            </li>
          ))}
        </ul>
      ) : null}
    </>
  );
}
