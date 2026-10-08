// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { ALL, useCached, type AppData } from '../api/store';
import { inputProcessed } from '../ask/tools';
import type { Turn } from '../ask/types';
import { AGENT_REQUEST, plural, toolMs, toolsOf } from './drawn';
import { Fact } from './Fact';
import { t } from '../i18n';

const count = (n: number) => n.toLocaleString('en-US');
const usd = (n: number) => `$${n >= 0.01 ? n.toFixed(2) : n.toFixed(4)}`;

/** The tools a turn ran, in the order the agent called them, with their milliseconds. */
function ToolList({ turn }: { turn: Turn }) {
  const tools = toolsOf(turn);
  if (!tools.length) return <p className="quiet">{t('flow.agent-facts.theAgentAnsweredWithoutCallingA')}</p>;
  return (
    <ol className="fact-list">
      {tools.map((t, i) => (
        <li key={i}>
          <span className="mono"><b>{t.name}</b></span>
          <span className="quiet">{t.ms} ms</span>
        </li>
      ))}
    </ol>
  );
}

/** The sources the tool results said they read, summed per endpoint over the turn. */
function SourceList({ turn }: { turn: Turn }) {
  if (!turn.calls.length) return <p className="quiet">{t('flow.agent-facts.theToolResultsCarriedNoProvenance')}</p>;
  return (
    <ul className="fact-list">
      {turn.calls.map((c) => (
        <li key={c.endpoint}>
          <span className="mono"><b>{c.endpoint}</b></span>
          <span className="quiet">{c.requests === 0 ? 'not needed' : `${c.triples} triples · ${c.requests} req · ${c.ms} ms`}</span>
        </li>
      ))}
    </ul>
  );
}

/** The last question when nothing is selected: what the agent ran and what the layer read for it. */
export function QuestionSummary({ turn }: { turn: Turn }) {
  const s = turn.stats;
  return (
    <>
      <h2 className="eyebrow">{t('flow.agent-facts.lastQuestion')}</h2>
      <p className="node-q"><q>{turn.question}</q></p>
      <dl className="facts">
        <Fact label="Request"><span className="mono">{AGENT_REQUEST}</span></Fact>
        <Fact label="Profile"><span className="mono">{turn.profile}</span></Fact>
        <Fact label="Actor">{turn.actor ? <span className="mono">{turn.actor}</span> : 'not in the tool results'}</Fact>
        <Fact label="In tools"><span className="mono">{toolMs(turn)} ms</span> over {plural(toolsOf(turn).length, 'call')}</Fact>
        {s ? <Fact label="Tokens"><span className="mono">{count(inputProcessed(s.usage))} in / {count(s.usage.outputTokens)} out</span></Fact> : null}
        {s ? <Fact label="Cost">{s.estimatedUsd > 0 ? <span className="mono">{t('flow.agent-facts.about')} {usd(s.estimatedUsd)}</span> : 'price not configured'}</Fact> : null}
      </dl>
      <h3 className="eyebrow">{t('flow.agent-facts.toolsThatRan')}</h3>
      <ToolList turn={turn} />
      <h3 className="eyebrow">{t('flow.agent-facts.sourcesReadSummedOverTheTool')}</h3>
      <SourceList turn={turn} />
      <p className="quiet hint">{t('flow.agent-facts.selectAComponentToFollowIts')}</p>
    </>
  );
}

/** The agent card: how it is reached, what it forwards, and its last question. */
export function AgentFacts({ data, turn }: { data: AppData; turn: Turn | null }) {
  const health = useCached(data.agentHealth, ALL);
  return (
    <>
      <dl className="facts">
        <Fact label="Health">
          <span className="mono">{t('flow.agent-facts.getAgentHealth')}</span>{' '}
          {health.state === 'ready' ? 'answered' : health.state === 'error' ? 'did not answer' : 'waiting for the answer'}
        </Fact>
        <Fact label="Request"><span className="mono">{AGENT_REQUEST}</span>, AG-UI events over SSE</Fact>
        <Fact label="Reached through">{t('flow.agent-facts.cloudfront')} <span className="mono">/agent/*</span>, a VPC origin and an internal ALB; the Cognito sign-in gates it like the site</Fact>
        <Fact label="Profile sent"><span className="mono">x-atelier-profile: {data.profile}</span>, forwarded to every tool call</Fact>
        <Fact label="Actor added"><span className="mono">x-atelier-actor: agent</span>, echoed by the query service in each result's policy</Fact>
        <Fact label="Model">{t('flow.agent-facts.from')} <span className="mono">{t('flow.agent-facts.modelId')}</span>; a pluggable dependency of the agent, not the product</Fact>
        <Fact label="Last question">{turn ? <q>{turn.question}</q> : 'none yet: open Ask on the Interface check'}</Fact>
      </dl>
      {turn ? (
        <>
          <h3 className="eyebrow">{t('flow.agent-facts.toolsThatRan')}</h3>
          <ToolList turn={turn} />
        </>
      ) : null}
    </>
  );
}

/** The query service seen as the MCP server of a question: its tool calls and what they read. */
export function QueryToolFacts({ turn }: { turn: Turn }) {
  const asked = turn.calls.filter((c) => c.requests > 0).length;
  return (
    <>
      <dl className="facts">
        <Fact label="Request"><span className="mono">/api/query/mcp</span>, {plural(toolsOf(turn).length, 'tool call')}</Fact>
        <Fact label="In tools"><span className="mono">{toolMs(turn)} ms</span></Fact>
        <Fact label="Sources asked">{asked} of {turn.calls.length}</Fact>
      </dl>
      <h3 className="eyebrow">{t('flow.agent-facts.exportControlPolicyApplied')}</h3>
      <dl className="facts">
        <Fact label="Profile"><span className="mono">{turn.profile}</span></Fact>
        <Fact label="Actor">{turn.actor ? <span className="mono">{turn.actor}</span> : 'not in the tool results'}</Fact>
      </dl>
      <h3 className="eyebrow">{t('flow.agent-facts.toolsThatRan')}</h3>
      <ToolList turn={turn} />
      <h3 className="eyebrow">{t('flow.agent-facts.sourcesRead')}</h3>
      <SourceList turn={turn} />
      <p className="quiet source">{t('flow.agent-facts.theToolAnswersCarryProvenanceAnd')}</p>
    </>
  );
}
