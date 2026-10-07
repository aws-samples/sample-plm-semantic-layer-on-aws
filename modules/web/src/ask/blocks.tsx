// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { ReactNode } from 'react';
import { ROUTED_SCREENS } from '../ui/useHashRoute';
import { TAB_LABEL } from './tools';
import type { AgentAction, Block, EvidenceBlock, ViewBlock } from './types';
import { t } from '../i18n';

/** Inline `code` and **bold**, the two marks a model's prose tends to carry; nothing else is interpreted. */
export function inline(text: string): ReactNode[] {
  return text.split(/(`[^`]+`|\*\*[^*]+\*\*)/g).map((part, i) => {
    if (part.startsWith('`') && part.endsWith('`')) return <span key={i} className="mono">{part.slice(1, -1)}</span>;
    if (part.startsWith('**') && part.endsWith('**')) return <b key={i}>{part.slice(2, -2)}</b>;
    return part;
  });
}

export function TableBlock({ block }: { block: Extract<Block, { kind: 'table' }> }) {
  return (
    <div className="ask-block">
      {block.title ? <span className="eyebrow">{block.title}</span> : null}
      <table className="records ask-table">
        <thead>
          <tr>{block.columns.map((c, i) => <th key={i} scope="col">{c}</th>)}</tr>
        </thead>
        <tbody>
          {block.rows.map((r, i) => (
            <tr key={i}>{r.map((c, j) => <td key={j}>{c === null ? <span className="quiet">-</span> : String(c)}</td>)}</tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

interface HighlightProps {
  block: Extract<Block, { kind: 'highlight' }>;
  selectedId: string | null;
  onSelect: (id: string) => void;
}

/** The interfaces the agent pointed at; the viewer shows one at a time, so the first is selected and each is a chip. */
export function HighlightBlock({ block, selectedId, onSelect }: HighlightProps) {
  return (
    <div className="ask-block">
      <div className="ask-block-head">
        <span className="eyebrow">{t('ask.blocks.onTheProduct')}</span>
        {block.caption ? <span className="ask-caption">{block.caption}</span> : null}
      </div>
      <div className="ask-ids">
        {block.ids.map((id) => (
          <button key={id} type="button" className={`ask-id${id === selectedId ? ' is-selected' : ''}`} aria-pressed={id === selectedId} onClick={() => onSelect(id)}>
            {id}
          </button>
        ))}
      </div>
    </div>
  );
}

export function EvidenceBlockView({ block, onReopen }: { block: EvidenceBlock; onReopen: (b: EvidenceBlock) => void }) {
  return (
    <div className="ask-block ask-evidence">
      <div className="ask-block-head">
        <span className="eyebrow">{t('ask.blocks.evidenceOpened')}</span>
        <span className="ask-caption">
          <span className="mono">{block.card}</span>
          {block.tab ? ` · ${TAB_LABEL[block.tab]}` : ''} · {block.interfaceId}
        </span>
      </div>
      <button type="button" className="btn btn-small" onClick={() => onReopen(block)}>{t('ask.blocks.openAgain')}</button>
    </div>
  );
}

/** The eyebrow of a viewer or loading block, by tool. */
const VIEW_LABEL: Record<string, string> = {
  highlight_parts: 'ask.blocks.partsOutlined',
  isolate_parts: 'ask.blocks.partsIsolated',
  zoom_to_part: 'ask.blocks.viewFittedTo',
  clear_view: 'ask.blocks.normalView',
  open_subtree: 'ask.blocks.subtreeOpened',
  open_product: 'ask.blocks.productOpened',
  open_screen: 'ask.blocks.screenOpened',
};

/** What a viewer or loading block names: the parts, the assembly, the product or the screen. */
function viewSubject(a: AgentAction): { caption: string; ids: string[]; context: string[] } {
  if (a.kind === 'subtree') return { caption: a.product ? `${t('ask.blocks.inProduct')} ${a.product}` : '', ids: [a.root], context: [] };
  if (a.kind === 'product') return { caption: '', ids: [a.product], context: [] };
  if (a.kind === 'screen') return { caption: ROUTED_SCREENS.includes(a.screen) ? '' : t('ask.blocks.screenNotOnThisSite'), ids: [a.screen], context: [] };
  const c = a.command;
  if (c.kind === 'highlight') return { caption: c.caption, ids: c.ids, context: [] };
  if (c.kind === 'isolate') return { caption: c.caption, ids: c.ids, context: c.contextIds };
  if (c.kind === 'zoom') return { caption: '', ids: [c.id], context: [] };
  return { caption: '', ids: [], context: [] };
}

/** A viewer or loading tool the agent called: what it showed, and the way to show it again. */
export function ViewBlockView({ block, onReplay }: { block: ViewBlock; onReplay: (b: ViewBlock) => void }) {
  const { caption, ids, context } = viewSubject(block.action);
  return (
    <div className="ask-block ask-view">
      <div className="ask-block-head">
        <span className="eyebrow">{t(VIEW_LABEL[block.tool] ?? 'ask.blocks.onTheProduct')}</span>
        {caption ? <span className="ask-caption">{caption}</span> : null}
      </div>
      {ids.length || context.length ? (
        <div className="ask-ids">
          {ids.map((id) => <span key={id} className="ask-id is-static">{id}</span>)}
          {context.map((id) => <span key={`c:${id}`} className="ask-id is-static is-context" title={t('ask.blocks.contextPart')}>{id}</span>)}
        </div>
      ) : null}
      <button type="button" className="btn btn-small" onClick={() => onReplay(block)}>{t('ask.blocks.showAgain')}</button>
    </div>
  );
}
