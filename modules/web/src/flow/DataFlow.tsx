// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useCallback, useMemo, useState } from 'react';
import { Background, BackgroundVariant, MiniMap, ReactFlow, type EdgeTypes, type Node, type NodeTypes } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { OFFICER } from '../api/profile';
import { ALL, lastAnswer, useCached, type AppData } from '../api/store';
import type { Changes } from '../api/types';
import type { Resource } from '../api/useResource';
import { cssColour } from '../ui/plm';
import { changePaths, countsText } from './changePaths';
import { drawnOf, plural, toolMs, toolsOf, type Drawn, type Mode } from './drawn';
import { WireEdge } from './edges';
import { buildGraph } from './graph';
import { CardNode, LayerNode, NoteNode, type CardData } from './nodes';
import { NodePanel } from './NodePanel';
import { topology } from './topology';
import { t } from '../i18n';

const nodeTypes: NodeTypes = { card: CardNode, layer: LayerNode, note: NoteNode };
const edgeTypes: EdgeTypes = { wire: WireEdge };

const miniColour = (n: Node) => {
  if (n.type !== 'card') return 'rgba(0,0,0,0)';
  const c = (n.data as CardData).c;
  if (c.plm) return cssColour(c.plm);
  return c.variant === 'query' ? '#15202a' : ['aurora', 'neptune', 'cad', 'agent', 'eventbridge', 'loader'].includes(c.variant) ? '#394855' : '#8b959e';
};

/** The deployment as a wiring diagram, with the last answer's calls drawn on it: the last click, the last question, or the officer's change feed. */
export function DataFlow({ data }: { data: AppData }) {
  const [selected, setSelected] = useState<string | null>(null);
  // The request the user chose to draw; until they choose, a completed question is the most recent answer on screen and is drawn, else the click.
  const [picked, setPicked] = useState<Mode | null>(null);
  const mode: Mode = picked ?? (data.lastTurn ? 'question' : 'click');
  // The change feed answers the officer only, so only the officer sees the mode and asks for it.
  const officer = data.profile === OFFICER;
  const changes = useCached(data.changes, officer ? ALL : null);
  const shown: Mode = mode === 'change' && !officer ? 'click' : mode;
  const drawn = useMemo(() => drawnOf(data, shown), [data, shown]);
  const hasClick = lastAnswer(data) !== null;
  const topo = useMemo(() => topology(data.config.plms), [data.config.plms]);
  const { nodes, edges } = useMemo(() => buildGraph(topo, selected, drawn, data.config.plms), [topo, selected, drawn, data.config.plms]);
  const component = topo.components.find((c) => c.id === selected) ?? null;
  const onNodeClick = useCallback((_: unknown, node: Node) => {
    if (node.type === 'card') setSelected((s) => (s === node.id ? null : node.id));
  }, []);

  return (
    <div className="flow">
      <div className="flow-stage">
        <header className="flow-head">
          <span className="eyebrow">{t('flow.data-flow.dataFlow')}</span>
          {data.lastTurn || officer ? (
            <div className="flow-mode" role="group" aria-label="Request drawn">
              <button type="button" aria-pressed={shown === 'click'} disabled={!hasClick} onClick={() => setPicked('click')}>{t('flow.data-flow.lastClick')}</button>
              {data.lastTurn ? <button type="button" aria-pressed={shown === 'question'} onClick={() => setPicked('question')}>{t('flow.data-flow.lastQuestion')}</button> : null}
              {officer ? <button type="button" aria-pressed={shown === 'change'} onClick={() => setPicked('change')}>{t('flow.data-flow.lastChange')}</button> : null}
            </div>
          ) : null}
          <Caption mode={shown} drawn={drawn} changes={changes} topo={topo} />
          <span className="quiet flow-hint">{t('flow.data-flow.selectAComponentToFollowIts')}</span>
        </header>
        <div className="flow-canvas">
          <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={nodeTypes}
            edgeTypes={edgeTypes}
            onNodeClick={onNodeClick}
            onPaneClick={() => setSelected(null)}
            fitView
            fitViewOptions={{ padding: 0.05 }}
            minZoom={0.4}
            maxZoom={2}
            nodesDraggable={false}
            nodesConnectable={false}
            elementsSelectable={false}
            edgesFocusable={false}
          >
            <Background variant={BackgroundVariant.Dots} color="#cdd4d9" gap={22} size={1.2} />
            <MiniMap position="top-right" pannable nodeColor={miniColour} nodeStrokeWidth={0} style={{ background: '#e4e9ec', width: 160, height: 104 }} />
          </ReactFlow>
        </div>
      </div>
      <aside className="panel flow-panel" aria-label="Component facts">
        {shown === 'change' && !drawn && !component ? (
          <FeedState changes={changes} onRetry={() => data.changes.retry(ALL)} />
        ) : (
          <NodePanel data={data} component={component} drawn={drawn} onClear={() => setSelected(null)} />
        )}
      </aside>
    </div>
  );
}

/** Which request the wires carry. */
function Caption({ mode, drawn, changes, topo }: { mode: Mode; drawn: Drawn | null; changes: Resource<Changes>; topo: ReturnType<typeof topology> }) {
  if (mode === 'change') {
    if (drawn?.kind === 'change') return <span>{t('flow.data-flow.drawnFromTheChangeFeed')} <b>{countsText(changePaths(drawn.changes, topo).counts)}</b></span>;
    if (changes.state === 'error') return <span className="quiet">{t('flow.data-flow.theChangeFeedCouldNotBe')}</span>;
    return <span className="quiet">{t('flow.data-flow.readingTheChangeFeed')}</span>;
  }
  if (!drawn) {
    return <span className="quiet">{t('flow.data-flow.noAnswerLoadedYetRunThe')}</span>;
  }
  if (drawn.kind === 'click') {
    return (
      <span>
        {t('flow.data-flow.drawnFromTheLastClick')} <span className="mono">{t('flow.data-flow.get')} {drawn.answer.request}</span>, <b className="mono">{drawn.answer.envelope.timings.totalMs} ms</b>
      </span>
    );
  }
  if (drawn.kind === 'change') return null;
  const turn = drawn.turn;
  return (
    <span className="flow-caption">
      {t('flow.data-flow.drawnFromTheLastQuestion')} <q className="flow-q">{turn.question}</q>: {plural(toolsOf(turn).length, 'tool call')}, <b className="mono">{toolMs(turn)} ms</b> in tools
    </span>
  );
}

/** The panel while the change feed is not drawable: still reading, or not answered. */
function FeedState({ changes, onRetry }: { changes: Resource<Changes>; onRetry: () => void }) {
  return (
    <>
      <h2 className="eyebrow">{t('flow.data-flow.lastChange')}</h2>
      {changes.state === 'error' ? (
        <>
          <p className="node-does release-error">{t('flow.data-flow.theChangeFeedCouldNotBe2')} {changes.error.message}</p>
          <button type="button" className="btn btn-small" onClick={onRetry}>{t('flow.data-flow.tryAgain')}</button>
        </>
      ) : (
        <p className="node-does">{t('flow.data-flow.reading')} <span className="mono">{t('flow.data-flow.getApiQueryDemoChanges')}</span>: the value corrections, links and CAD publications since the released dataset, each drawn on the path it took.</p>
      )}
    </>
  );
}
