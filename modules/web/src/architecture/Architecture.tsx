// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { Fragment, useEffect, useMemo, useState, type CSSProperties, type ReactNode } from 'react';
import { ApiError } from '../api/client';
import { ALL, useCached, type AppData } from '../api/store';
import { isPart } from '../api/types';
import type { Resource } from '../api/useResource';
import { writerLabel } from '../flow/ChangeFacts';
import { cssColour, plmCode, sourcesOf } from '../ui/plm';
import { layers, SUMMARY, type DemoItem, type Probe } from './layers';
import { ToolTable } from './ToolTable';
import { t } from '../i18n';

type Liveness = 'ok' | 'down' | 'unknown' | 'missing';

const DOT_TITLE: Record<Liveness, string> = { ok: 'answered', down: 'did not answer', unknown: 'not observed yet', missing: 'not deployed' };

/** The sample laid against a PLM reference architecture, layer by layer, with what is live right now. */
export function Architecture({ data }: { data: AppData }) {
  const plms = data.config.plms;
  const services = useMemo(() => ['query', ...sourcesOf(plms)], [plms]);
  const { ensure } = data.health;
  useEffect(() => {
    for (const s of services) ensure(s);
  }, [ensure, services]);
  // The list answer reaches every source, so it is what lights the virtual graphs and stores.
  const list = useCached(data.interfaces, ALL);
  const parts = useCached(data.parts, ALL);
  const tags = useCached(data.tags, ALL);
  const agent = useCached(data.agentHealth, ALL);
  const mcp = useCached(data.mcpTools, ALL);
  const events = useCached(data.demoHealth, ALL);
  const changes = useCached(data.changes, ALL);
  // The tool register under the MCP row: closed on arrival, left as the viewer set it while the tab is open.
  const [toolsOpen, setToolsOpen] = useState(false);

  const state = (r: Resource<unknown> | undefined): Liveness => (r?.state === 'ready' ? 'ok' : r?.state === 'error' ? 'down' : 'unknown');
  // An endpoint that answers 404 is not deployed, which is not the same as a deployed one failing.
  const deployed = (r: Resource<unknown>): Liveness => (r.state === 'error' && r.error instanceof ApiError && r.error.status === 404 ? 'missing' : state(r));
  const live = (p: Probe): Liveness => {
    switch (p.kind) {
      case 'spa': return 'ok';
      case 'health': return state(data.health.get(p.service));
      case 'call': {
        if (list.state !== 'ready') return state(list);
        const c = list.data.provenance.calls.find((x) => x.endpoint === p.endpoint);
        return c && c.requests > 0 ? 'ok' : 'unknown';
      }
      case 'shacl': return state(list);
      case 'tags': return state(tags);
      case 'cad': return parts.state === 'ready' ? (parts.data.parts.filter(isPart).some((x) => x.cadUrl) ? 'ok' : 'unknown') : state(parts);
      case 'agent': return deployed(agent);
      case 'mcp': return deployed(mcp);
      case 'events': return deployed(events);
      case 'changes': return state(changes);
    }
  };
  /** What the server said beside a probe, when the row carries a figure: the tool count (which opens the register) or the graph's writer, read, never assumed. */
  const note = (p: Probe): ReactNode => {
    if (p.kind === 'mcp') {
      if (mcp.state === 'ready') {
        return (
          <button type="button" className="arch-count arch-toggle" aria-expanded={toolsOpen} aria-controls="arch-mcp-tools" onClick={() => setToolsOpen((o) => !o)}>
            <b>{mcp.data.tools.length}</b> tools listed <i className="arch-caret" aria-hidden="true" />
          </button>
        );
      }
      if (mcp.state === 'error') return <span className="arch-count is-off">{deployed(mcp) === 'missing' ? 'tool list not deployed' : 'tool list did not answer'}</span>;
      return <span className="arch-count is-off">{t('architecture.architecture.readingTheToolList')}</span>;
    }
    if (p.kind === 'events') {
      if (events.state === 'ready') {
        const h = events.data;
        const n = h.subscriptions.length;
        return (
          <>
            <span className="arch-count" title={h.publishes.join(', ')}>{t('architecture.architecture.writer')} <b>{writerLabel(h.writer)}</b></span>
            <span className="arch-count" title={h.subscriptions.join('\n')}><b>{n}</b> {n === 1 ? 'subscription' : 'subscriptions'}</span>
          </>
        );
      }
      if (events.state === 'error') return <span className="arch-count is-off">{deployed(events) === 'missing' ? 'event wiring not deployed' : 'health did not answer'}</span>;
      return <span className="arch-count is-off">{t('architecture.architecture.readingTheHealth')}</span>;
    }
    return null;
  };
  const anyMissing = [agent, mcp, events].some((r) => deployed(r) === 'missing');

  return (
    <div className="arch">
      <div className="arch-inner">
        <header className="arch-head">
          <div>
            <span className="eyebrow">{t('architecture.architecture.architectureLayerByLayer')}</span>
            <h1 className="arch-title">{t('architecture.architecture.thisSampleAgainstAPlmReference')}</h1>
          </div>
          <p className="arch-legend">
            <Dot live="ok" /> answered <Dot live="down" /> did not answer <Dot live="unknown" /> not observed yet
            {anyMissing ? <><Dot live="missing" /> not deployed</> : null}
            <span className="quiet"> · from this session's health, catalogue and answer calls</span>
          </p>
        </header>
        <div className="arch-cols">
          <span />
          <span className="eyebrow">{t('architecture.architecture.referenceArchitecture')}</span>
          <span className="eyebrow">{t('architecture.architecture.inThisSample')}</span>
        </div>
        {layers(plms).map((l, i) => (
          <Fragment key={l.id}>
            {l.breakBefore ? <div className="arch-break" role="separator"><span>{l.breakBefore}</span></div> : null}
            <section className={`arch-layer${l.addition ? ' is-addition' : ''}${l.breakBefore ? ' after-break' : ''}`} aria-label={l.label}>
              {i > 0 && !l.breakBefore ? <i className="arch-link" aria-hidden="true" /> : null}
              <h2 className="arch-label">{l.label}</h2>
              <div className={`arch-reference${l.addition ? ' is-none' : ''}`}>
                <p>{l.reference}</p>
              </div>
              <div className="arch-demo">
                <ul className="arch-items">
                  {l.demo.map((d, j) => <Item key={j} item={d} live={live} note={note} />)}
                </ul>
                {l.notBuilt ? <p className="arch-gap"><span>{t('architecture.architecture.notInTheDemo')}</span> {l.notBuilt}</p> : null}
              </div>
              {l.id === 'mcp' && toolsOpen && mcp.state === 'ready' ? <ToolTable tools={mcp.data.tools} /> : null}
            </section>
          </Fragment>
        ))}
        <footer className="arch-foot">
          <span className="eyebrow">{t('architecture.architecture.addsAndLeavesOut')}</span>
          <p>{SUMMARY}</p>
        </footer>
      </div>
    </div>
  );
}

function Item({ item, live, note }: { item: DemoItem; live: (p: Probe) => Liveness; note: (p: Probe) => ReactNode }) {
  return (
    <li>
      {item.probe ? <Dot live={live(item.probe)} /> : <i className="arch-dot is-none" aria-hidden="true" />}
      <span className="arch-text">
        {item.text}
        {item.code ? <span className="mono arch-code">{item.code}</span> : null}
        {item.probe ? note(item.probe) : null}
        {item.note ? <span className="arch-note">{item.note}</span> : null}
        {item.perPlm ? (
          <span className="arch-chips">
            {item.perPlm.plms.map((plm) => (
              <span key={plm} className="arch-chip" style={{ '--plm': cssColour(plm) } as CSSProperties}>
                <Dot live={live(item.perPlm!.probe(plm))} />
                <b>{plmCode(plm)}</b>
                <span className="mono">{item.perPlm!.code(plm)}</span>
              </span>
            ))}
          </span>
        ) : null}
      </span>
    </li>
  );
}

const Dot = ({ live }: { live: Liveness }) => <i className={`arch-dot is-${live}`} role="img" aria-label={DOT_TITLE[live]} title={DOT_TITLE[live]} />;
