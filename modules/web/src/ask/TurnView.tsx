// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { ErrorState } from '../ui/ErrorState';
import { EvidenceBlockView, HighlightBlock, inline, TableBlock, ViewBlockView } from './blocks';
import { ToolsRow } from './ToolsRow';
import type { Block, EvidenceBlock, Turn, ViewBlock } from './types';
import { t } from '../i18n';

interface Props {
  turn: Turn;
  selectedId: string | null;
  onSelect: (id: string) => void;
  onReopen: (b: EvidenceBlock) => void;
  onReplay: (b: ViewBlock) => void;
  onRetry: () => void;
}

/** One question and everything the agent streamed back for it. */
export function TurnView({ turn, selectedId, onSelect, onReopen, onReplay, onRetry }: Props) {
  return (
    <article className={`ask-turn is-${turn.state}${turn.redacted ? ' is-redacted' : ''}`}>
      <h3 className="ask-q">{turn.question}</h3>
      <div className="ask-answer">
        {turn.blocks.map((b) => <BlockView key={b.id} block={b} selectedId={selectedId} onSelect={onSelect} onReopen={onReopen} onReplay={onReplay} />)}
        {turn.state === 'running' ? <span className="loading-bar ask-streaming" role="status" aria-label="The agent is answering" /> : null}
        {turn.state === 'error' ? <ErrorState title="The agent did not answer" error={new Error(turn.error)} onRetry={onRetry} /> : null}
        {turn.redacted ? <span className="chip is-not-evaluable ask-redacted">{t('ask.turn-view.notVisibleToYourProfile')}</span> : null}
      </div>
      {turn.stats || turn.sqlRuns.length ? <ToolsRow stats={turn.stats} sqlRuns={turn.sqlRuns} /> : null}
    </article>
  );
}

type BlockProps = Omit<Props, 'turn' | 'onRetry'> & { block: Block };

function BlockView({ block, selectedId, onSelect, onReopen, onReplay }: BlockProps) {
  switch (block.kind) {
    case 'prose':
      return <p className="ask-prose">{inline(block.text)}</p>;
    case 'table':
      return <TableBlock block={block} />;
    case 'highlight':
      return <HighlightBlock block={block} selectedId={selectedId} onSelect={onSelect} />;
    case 'evidence':
      return <EvidenceBlockView block={block} onReopen={onReopen} />;
    case 'view':
      return <ViewBlockView block={block} onReplay={onReplay} />;
  }
}
