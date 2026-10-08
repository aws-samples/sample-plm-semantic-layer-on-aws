// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { ApiError } from '../api/client';
import { firstSentence } from '../api/mcpTools';
import { ALL, useCached, type Answer, type AppData } from '../api/store';
import type { Call } from '../api/types';
import { isGeometric } from '../api/types';
import { flagCount } from '../catalogue/coverage';
import { ErrorState } from '../ui/ErrorState';
import { CORE, cssColour, dbName, isCore, ownerLabel, PLM_NAME, PLM_ORDER, plmCode } from '../ui/plm';
import { AgentFacts, QueryToolFacts, QuestionSummary } from './AgentFacts';
import { ChangeLogFacts, ChangeSummary, EventBridgeFacts, LoaderFacts } from './ChangeFacts';
import { callsOf, requestOf, type Drawn } from './drawn';
import { Fact } from './Fact';
import type { Component } from './topology';
import { t } from '../i18n';

interface Props {
  data: AppData;
  component: Component | null;
  drawn: Drawn | null;
  onClear: () => void;
}

const VARIANT_LABEL: Record<Component['variant'], string> = {
  browser: 'Browser', cloudfront: 'Edge', eventbridge: 'Event bus · the other edge', agent: 'Agent · a client like the screen', apigw: 'API', plm: 'PLM service', core: 'Atelier core service',
  ontop: 'Virtual graph', query: 'Query service', loader: 'Subscriber · Lambda', neptune: 'Link store and file index', aurora: 'Relational data', coredb: 'Atelier core database',
  changelog: 'Atelier change log', cad: 'Object storage',
};

/** Runtime facts about the selected component; the request drawn when nothing is selected. */
export function NodePanel({ data, component, drawn, onClear }: Props) {
  if (!component) {
    if (drawn?.kind === 'question') return <QuestionSummary turn={drawn.turn} />;
    if (drawn?.kind === 'change') return <ChangeSummary changes={drawn.changes} />;
    return <AnswerSummary answer={drawn?.kind === 'click' ? drawn.answer : null} />;
  }
  const c = component;
  const call = c.endpoint ? callsOf(drawn).find((x) => x.endpoint === c.endpoint) : undefined;
  const turn = drawn?.kind === 'question' ? drawn.turn : null;
  const changes = drawn?.kind === 'change' ? drawn.changes : null;
  return (
    <>
      <header className="node-head">
        <div className="node-eyebrow">
          <span className="eyebrow">{VARIANT_LABEL[c.variant]}{c.plm && !isCore(c.plm) ? ` · ${PLM_NAME[plmCode(c.plm)]}` : ''}</span>
          <button type="button" className="btn btn-small" onClick={onClear}>{t('flow.node-panel.clear')}</button>
        </div>
        <h2 className="node-title">
          {c.plm ? <b style={{ color: cssColour(c.plm) }}>{plmCode(c.plm)}</b> : null}
          {c.title}
          {c.endpoint ? <span className="mono node-endpoint">{c.endpoint}</span> : null}
        </h2>
        <p className="node-does">{c.does}</p>
      </header>
      {c.variant === 'browser' ? <BrowserFacts data={data} drawn={drawn} /> : null}
      {c.variant === 'cloudfront' ? <CloudFrontFacts data={data} /> : null}
      {c.variant === 'eventbridge' ? <EventBridgeFacts data={data} changes={changes} /> : null}
      {c.variant === 'loader' ? <LoaderFacts data={data} changes={changes} /> : null}
      {c.variant === 'changelog' ? <ChangeLogFacts data={data} changes={changes} /> : null}
      {c.variant === 'agent' ? <AgentFacts data={data} turn={turn} /> : null}
      {c.variant === 'apigw' ? <ApiFacts data={data} drawn={drawn} /> : null}
      {c.variant === 'plm' ? <PlmFacts data={data} plm={c.plm!} /> : null}
      {c.variant === 'core' ? <><PlmFacts data={data} plm={CORE} /><TagFacts data={data} /></> : null}
      {c.variant === 'ontop' ? <OntopFacts data={data} plm={c.plm!} call={call} drawn={drawn} /> : null}
      {c.variant === 'query' ? (
        <>
          {drawn?.kind === 'question' ? <QueryToolFacts turn={drawn.turn} /> : <QueryFacts answer={drawn?.kind === 'click' ? drawn.answer : null} />}
          <McpToolFacts data={data} />
        </>
      ) : null}
      {c.variant === 'neptune' ? <NeptuneFacts data={data} call={call} drawn={drawn} /> : null}
      {c.variant === 'aurora' ? <AuroraFacts data={data} /> : null}
      {c.variant === 'coredb' ? <><Database data={data} plm={CORE} /><TagFacts data={data} /></> : null}
      {c.variant === 'cad' ? <CadFacts data={data} /> : null}
    </>
  );
}

function CallFacts({ call, drawn }: { call: Call | undefined; drawn: Drawn | null }) {
  if (!drawn) return <Fact label="Last call">{t('flow.node-panel.noAnswerLoadedYet')}</Fact>;
  if (!call) return <Fact label="Last call">{t('flow.node-panel.notPartOf')} <span className="mono">{requestOf(drawn)}</span></Fact>;
  if (call.requests === 0) return <Fact label="Last call">{t('flow.node-panel.notNeededFor')} <span className="mono">{requestOf(drawn)}</span>: 0 requests</Fact>;
  return (
    <>
      <Fact label="Last call"><span className="mono">{call.triples} triples · {call.requests} req · {call.ms} ms</span></Fact>
      <Fact label="Graph">{call.kind}</Fact>
    </>
  );
}

function AnswerSummary({ answer }: { answer: Answer | null }) {
  if (!answer) {
    return (
      <>
        <h2 className="eyebrow">{t('flow.node-panel.lastAnswer')}</h2>
        <p className="node-does">{t('flow.node-panel.noAnswerLoadedYetOpenThe')} <a href="#/check">{t('flow.node-panel.interfaceCheck')}</a>, or ask the agent a question there: the calls behind the answer are drawn on this diagram with their measured triples, requests and milliseconds.</p>
        <p className="quiet hint">{t('flow.node-panel.selectAComponentToReadWhat')}</p>
      </>
    );
  }
  const timings = answer.envelope.timings;
  return (
    <>
      <h2 className="eyebrow">{t('flow.node-panel.lastAnswer')}</h2>
      <dl className="facts">
        <Fact label="Request"><span className="mono">{t('flow.node-panel.get')} {answer.request}</span></Fact>
        <Fact label="Profile"><span className="mono">{answer.envelope.policy.profile}</span></Fact>
        <Fact label="Total"><span className="mono">{timings.totalMs} ms</span></Fact>
        <Fact label="Federation"><span className="mono">{timings.federationMs} ms</span></Fact>
        <Fact label="Validation"><span className="mono">{timings.validationMs} ms</span></Fact>
      </dl>
      <ul className="fact-list">
        {answer.envelope.provenance.calls.map((c) => (
          <li key={c.endpoint}>
            <span className="mono"><b>{c.endpoint}</b></span>
            <span className="quiet">{c.requests === 0 ? 'not needed' : `${c.triples} triples · ${c.requests} req · ${c.ms} ms`}</span>
          </li>
        ))}
      </ul>
      <p className="quiet hint">{t('flow.node-panel.selectAComponentToFollowIts')}</p>
    </>
  );
}

function BrowserFacts({ data, drawn }: { data: AppData; drawn: Drawn | null }) {
  return (
    <dl className="facts">
      <Fact label="Environment"><span className="mono">{data.config.envName}</span></Fact>
      <Fact label="API base"><span className="mono">{data.config.apiBase}</span></Fact>
      <Fact label="PLMs">{data.config.plms.map(plmCode).join(', ')}</Fact>
      <Fact label="Profile sent"><span className="mono">x-atelier-profile: {data.profile}</span></Fact>
      <Fact label="Last request">{drawn ? <span className="mono">{requestOf(drawn)}</span> : 'none yet'}</Fact>
    </dl>
  );
}

function CloudFrontFacts({ data }: { data: AppData }) {
  return (
    <dl className="facts">
      <Fact label="/"> {t('flow.node-panel.theSpaBundle')}</Fact>
      <Fact label={`${data.config.apiBase}/*`}>{t('flow.node-panel.apiGatewayWithTheOriginSecret')}</Fact>
      <Fact label="/agent/*">{t('flow.node-panel.theAgentSInternalAlbThrough')} <span className="mono">x-atelier-profile</span> forwarded</Fact>
      <Fact label="STEP files">{t('flow.node-panel.notServedHereTheBrowserFetches')}</Fact>
    </dl>
  );
}

function ApiFacts({ data, drawn }: { data: AppData; drawn: Drawn | null }) {
  return (
    <>
      <dl className="facts">
        <Fact label="Last request">{drawn ? <span className="mono">{requestOf(drawn)}</span> : 'none yet'}</Fact>
      </dl>
      <h3 className="eyebrow">{t('flow.node-panel.routes')}</h3>
      <ul className="fact-list">
        <li><span className="mono">{data.config.apiBase}/query/*</span><span className="quiet">{t('flow.node-panel.queryService')}</span></li>
        <li><span className="mono">{data.config.apiBase}/query/mcp</span><span className="quiet">{t('flow.node-panel.mcpServerInTheQueryService')}</span></li>
        {data.config.plms.map((plm) => (
          <li key={plm}><span className="mono">{data.config.apiBase}/{plm}/*</span><span className="quiet">{plmCode(plm)} PLM service</span></li>
        ))}
        <li><span className="mono">{data.config.apiBase}/{CORE}/*</span><span className="quiet">{t('flow.node-panel.atelierCoreService')}</span></li>
      </ul>
    </>
  );
}

/** What a source's service publishes about its schema: entities, columns, annotation coverage. */
function PlmFacts({ data, plm }: { data: AppData; plm: string }) {
  const cat = useCached(data.catalogues, plm);
  if (cat.state === 'error') return <ErrorState title={`/api/${plm}/catalogue did not answer`} error={cat.error} onRetry={() => data.catalogues.retry(plm)} />;
  if (cat.state === 'loading') return <p className="quiet">{t('flow.node-panel.readingApi')}{plm}/catalogue</p>;
  const cols = cat.data.entities.flatMap((e) => e.columns);
  return (
    <>
      <dl className="facts">
        <Fact label="Entities">{cat.data.entities.length}</Fact>
        <Fact label="Columns">{cols.length}, {cols.filter((c) => !c.undescribed).length} described</Fact>
        <Fact label="Flagged">{flagCount(cat.data)}</Fact>
      </dl>
      <ul className="fact-list">
        {cat.data.entities.map((e) => (
          <li key={e.entity}>
            <span className="mono">{e.table}</span>
            <span className="quiet">{e.columns.length} columns · {e.ontologyClass ?? 'unmapped'}</span>
          </li>
        ))}
      </ul>
      <p className="quiet source">{t('flow.node-panel.from')} <span className="mono">{t('flow.node-panel.getApi')}{plm}/catalogue</span></p>
    </>
  );
}

/** The export-control tags the profile may see, counted per tagging PLM. */
function TagFacts({ data }: { data: AppData }) {
  const tags = useCached(data.tags, ALL);
  if (tags.state === 'error') return <ErrorState title={`/api/${CORE}/tags did not answer`} error={tags.error} onRetry={() => data.tags.retry(ALL)} />;
  if (tags.state === 'loading') return <p className="quiet">{t('flow.node-panel.readingApi')}{CORE}/tags</p>;
  const perPlm = new Map<string, number>();
  for (const tag of tags.data.tags) perPlm.set(plmCode(tag.taggedBy), (perPlm.get(plmCode(tag.taggedBy)) ?? 0) + 1);
  const order = (plm: string) => PLM_ORDER.indexOf(plm as (typeof PLM_ORDER)[number]);
  return (
    <>
      <h3 className="eyebrow">{t('flow.node-panel.tagsInPartTag')}</h3>
      <dl className="facts">
        <Fact label="Rows">{tags.data.tags.length} visible to <span className="mono">{data.profile}</span></Fact>
      </dl>
      <ul className="fact-list">
        {[...perPlm.entries()].sort(([a], [b]) => order(a) - order(b)).map(([plm, n]) => (
          <li key={plm}>
            <span><i className="swatch" style={{ background: cssColour(plm) }} />tagged by <b>{plm}</b></span>
            <span className="quiet">{n} {n === 1 ? 'part' : 'parts'}</span>
          </li>
        ))}
      </ul>
      <p className="quiet source">{t('flow.node-panel.from')} <span className="mono">{t('flow.node-panel.getApi')}{CORE}/tags</span></p>
    </>
  );
}

function OntopFacts({ data, plm, call, drawn }: { data: AppData; plm: string; call: Call | undefined; drawn: Drawn | null }) {
  const mapping = useCached(data.mappings, plm);
  const maps = mapping.state === 'ready' ? [...mapping.data.matchAll(/^map:(\S+)\s+a\s+rr:TriplesMap/gm)].map((m) => m[1]) : null;
  return (
    <>
      <dl className="facts">
        <Fact label="TriplesMaps">
          {maps ? maps.length : mapping.state === 'error' ? 'mapping not readable' : `reading /api/${plm}/mapping`}
        </Fact>
        <Fact label="Database"><span className="mono">{dbName(plm)}</span></Fact>
        <CallFacts call={call} drawn={drawn} />
      </dl>
      {maps ? (
        <ul className="fact-list">
          {maps.map((m) => <li key={m}><span className="mono">{t('flow.node-panel.map')}{m}</span></li>)}
        </ul>
      ) : mapping.state === 'error' ? (
        <ErrorState title={`/api/${plm}/mapping did not answer`} error={mapping.error} onRetry={() => data.mappings.retry(plm)} />
      ) : null}
    </>
  );
}

function QueryFacts({ answer }: { answer: Answer | null }) {
  if (!answer) return <p className="quiet">{t('flow.node-panel.noAnswerLoadedYetRunThe')}</p>;
  const timings = answer.envelope.timings;
  const pol = answer.envelope.policy;
  const asked = answer.envelope.provenance.calls.filter((c) => c.requests > 0).length;
  return (
    <>
      <dl className="facts">
        <Fact label="Request"><span className="mono">{t('flow.node-panel.get')} {answer.request}</span></Fact>
        <Fact label="Total"><span className="mono">{timings.totalMs} ms</span></Fact>
        <Fact label="Federation"><span className="mono">{timings.federationMs} ms</span>, {asked} of {answer.envelope.provenance.calls.length} endpoints asked</Fact>
        <Fact label="Validation"><span className="mono">{timings.validationMs} ms</span></Fact>
      </dl>
      <h3 className="eyebrow">{t('flow.node-panel.exportControlPolicyApplied')}</h3>
      <dl className="facts">
        <Fact label="Profile"><span className="mono">{pol.profile}</span></Fact>
        <Fact label="Releasable to"><span className="mono">{pol.releasable.join(', ')}</span></Fact>
        <Fact label="Filter"><span className="mono">{pol.filter}</span></Fact>
        {pol.untagged?.length ? <Fact label="Untagged">{pol.untagged.length} {pol.untagged.length === 1 ? 'part' : 'parts'} without a tag in atelier_core</Fact> : null}
      </dl>
      <h3 className="eyebrow">{t('flow.node-panel.executedSparql')}</h3>
      <pre className="code mono">{answer.envelope.sparql}</pre>
    </>
  );
}

/** The MCP server's tool registry, the list the Architecture tab opens; the query service may not serve one. */
function McpToolFacts({ data }: { data: AppData }) {
  const tools = useCached(data.mcpTools, ALL);
  const missing = tools.state === 'error' && tools.error instanceof ApiError && tools.error.status === 404;
  return (
    <>
      <h3 className="eyebrow">{t('flow.node-panel.mcpTools')}</h3>
      {tools.state === 'ready' ? (
        <>
          <ul className="fact-list is-stacked">
            {tools.data.tools.map((tool) => (
              <li key={tool.name}>
                <span className="mono"><b>{tool.name}</b></span>
                <span className="quiet">{firstSentence(tool.description)}</span>
              </li>
            ))}
          </ul>
          <p className="quiet source">{t('flow.node-panel.from')} <span className="mono">{t('flow.node-panel.getApiQueryMcpTools')}</span>, what any MCP client reads with <span className="mono">{t('flow.node-panel.toolsList')}</span></p>
        </>
      ) : missing ? (
        <p className="quiet">{t('flow.node-panel.notDeployed')} <span className="mono">{t('flow.node-panel.getApiQueryMcpTools')}</span> answered 404</p>
      ) : tools.state === 'error' ? (
        <ErrorState title="/api/query/mcp/tools did not answer" error={tools.error} onRetry={() => data.mcpTools.retry(ALL)} />
      ) : (
        <p className="quiet">{t('flow.node-panel.readingApiQueryMcpTools')}</p>
      )}
    </>
  );
}

function NeptuneFacts({ data, call, drawn }: { data: AppData; call: Call | undefined; drawn: Drawn | null }) {
  const list = data.interfaces.get(ALL);
  const parts = data.parts.get(ALL);
  return (
    <dl className="facts">
      <Fact label="Links graph"><span className="mono">{t('flow.node-panel.httpsExampleComAtelierGraphLinks')}</span></Fact>
      <Fact label="Interfaces">{list?.state === 'ready' ? `${list.data.interfaces.length} in the last list answer` : 'no list answer yet'}</Fact>
      <Fact label="File index"><span className="mono">{t('flow.node-panel.httpsExampleComAtelierGraphFileindex')}</span></Fact>
      <Fact label="Entries">
        {parts?.state === 'ready'
          ? `${parts.data.parts.filter((p) => isGeometric(p) && p.cadFile !== null).length} parts with an atelier:cadFile the profile may see`
          : 'one atelier:cadFile per part; no parts answer yet'}
      </Fact>
      <CallFacts call={call} drawn={drawn} />
    </dl>
  );
}

function AuroraFacts({ data }: { data: AppData }) {
  return (
    <>
      {data.config.plms.map((plm) => <Database key={plm} data={data} plm={plm} />)}
      <Database data={data} plm={CORE} />
    </>
  );
}

function Database({ data, plm }: { data: AppData; plm: string }) {
  const cat = useCached(data.catalogues, plm);
  return (
    <section className="db-block">
      <h3 className="db-name">
        <i className="swatch" style={{ background: cssColour(plm) }} />
        <span className="mono">{dbName(plm)}</span>
        <span className="quiet db-owner">{ownerLabel(plm)}</span>
      </h3>
      {cat.state === 'ready' ? (
        <ul className="fact-list">
          {cat.data.entities.map((e) => (
            <li key={e.table}><span className="mono">{e.table}</span><span className="quiet">{e.columns.length} columns</span></li>
          ))}
        </ul>
      ) : cat.state === 'error' ? (
        <p className="quiet">{t('flow.node-panel.tablesUnknownApi')}{plm}/catalogue did not answer</p>
      ) : (
        <p className="quiet">{t('flow.node-panel.readingApi2')}{plm}/catalogue</p>
      )}
    </section>
  );
}

/** The files the current answer issued a URL for; hidden parts have none. */
function CadFacts({ data }: { data: AppData }) {
  const parts = useCached(data.parts, ALL);
  if (parts.state === 'error') return <ErrorState title="The parts could not be loaded" error={parts.error} onRetry={() => data.parts.retry(ALL)} />;
  if (parts.state === 'loading') return <p className="quiet">{t('flow.node-panel.readingApiQueryParts')}</p>;
  const issued = parts.data.parts.filter(isGeometric).filter((p) => p.cadUrl !== null);
  const hidden = parts.data.parts.filter((p) => p.redacted).length;
  return (
    <>
      <dl className="facts">
        <Fact label="Presigned URLs">{issued.length} issued for <span className="mono">{data.profile}</span>, valid 15 minutes</Fact>
        <Fact label="Hidden">{hidden ? `${hidden} ${hidden === 1 ? 'part' : 'parts'} by export control: no URL issued` : 'none for this profile'}</Fact>
        <Fact label="Key from">{t('flow.node-panel.theFileIndexInNeptune')}<span className="mono">{t('flow.node-panel.atelierCadfile')}</span>), never from a PLM column</Fact>
      </dl>
      <ul className="fact-list">
        {issued.map((p) => (
          <li key={p.id}>
            <span className="mono"><i className="swatch" style={{ background: cssColour(p.plm) }} />{p.cadFile}</span>
            <span className="quiet">{p.name}</span>
          </li>
        ))}
      </ul>
    </>
  );
}
