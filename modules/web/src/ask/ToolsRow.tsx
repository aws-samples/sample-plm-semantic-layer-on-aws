// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { inputProcessed } from './tools';
import type { SqlRun, TurnStats } from './types';
import { t } from '../i18n';

const chars = (n: number) => (n >= 1000 ? `${(n / 1000).toFixed(1)}k` : String(n));
const count = (n: number) => n.toLocaleString('en-US');
const usd = (n: number) => `$${n >= 0.01 ? n.toFixed(2) : n.toFixed(4)}`;

interface Props {
  stats: TurnStats | null;
  sqlRuns: SqlRun[];
}

/** The input the turn processed (uncached, read from the prompt cache, written to it), its output and its estimated price. */
function Cost({ stats }: { stats: TurnStats }) {
  const { inputTokens, outputTokens, cacheReadInputTokens: read, cacheWriteInputTokens: write } = stats.usage;
  const input = inputProcessed(stats.usage);
  const cached = input ? Math.round((read / input) * 100) : 0;
  return (
    <p className="ask-cost mono" title={`${count(inputTokens)} uncached, ${count(read)} read from the prompt cache, ${count(write)} written to it`}>
      {count(input)} input tokens ({count(inputTokens)} uncached, {count(read)} cache read, {count(write)} cache write; {cached}% cached)
      {' '}/ {count(outputTokens)} output tokens,{' '}
      {stats.estimatedUsd > 0 ? `about ${usd(stats.estimatedUsd)} at list price` : 'price not configured'}
    </p>
  );
}

/** The agent's own answer path: the semantic-layer tools a turn ran, the SQL that ran, its tokens and its cost. */
export function ToolsRow({ stats, sqlRuns }: Props) {
  const tools = stats?.tools ?? [];
  const longest = Math.max(1, ...tools.map((t) => t.ms));
  return (
    <footer className="ask-path">
      <div className="ask-path-head">
        <span className="eyebrow">{t('ask.tools-row.agentPath')}</span>
        {stats ? <span className="quiet">{tools.length ? `${tools.length} tool call${tools.length === 1 ? '' : 's'}` : 'answered without a tool'}</span> : null}
      </div>
      {tools.length ? (
        <ol className="ask-hops">
          {tools.map((t, i) => (
            <li key={i} className="ask-hop">
              <span className="ask-hop-name mono">{t.name}</span>
              <span className="ask-hop-ms mono">{t.ms} ms</span>
              <span className="hop-bar" style={{ width: `${(t.ms / longest) * 100}%` }} />
              {t.sparqlChars || t.sqlChars ? (
                <span className="ask-hop-sizes" title="Characters of SPARQL and SQL the tool sent">
                  {[t.sparqlChars ? `SPARQL ${chars(t.sparqlChars)}` : '', t.sqlChars ? `SQL ${chars(t.sqlChars)}` : ''].filter(Boolean).join(' · ')}
                </span>
              ) : null}
            </li>
          ))}
        </ol>
      ) : null}
      {sqlRuns.map((r, i) => (
        <details key={i} className="ask-sql">
          <summary>
            <span className="eyebrow">{t('ask.tools-row.sqlThatRan')}</span>
            <span className="quiet">{count(r.rowCount)} rows · {r.ms} ms</span>
          </summary>
          <pre className="drawer-code mono">{r.sqlExecuted}</pre>
          {r.catalogueUsed.length ? (
            <p className="ask-sql-cat">{t('ask.tools-row.catalogue')} <span className="mono">{r.catalogueUsed.join(', ')}</span></p>
          ) : null}
        </details>
      ))}
      {stats ? <Cost stats={stats} /> : null}
    </footer>
  );
}
