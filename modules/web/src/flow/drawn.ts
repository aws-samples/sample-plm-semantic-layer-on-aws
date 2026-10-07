// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Which request the Data flow tab draws: the last click on the Interface check screen, the last
// question the Ask panel completed, or the change feed. All come from the store; nothing is kept here.
import { ALL, lastAnswer, type Answer, type AppData } from '../api/store';
import type { Call, Changes } from '../api/types';
import type { Turn } from '../ask/types';

export type Mode = 'click' | 'question' | 'change';

export type Drawn = { kind: 'click'; answer: Answer } | { kind: 'question'; turn: Turn } | { kind: 'change'; changes: Changes };

export const AGENT_REQUEST = 'POST /agent/invocations';
/** The demo controls, each on its owning service. */
export const CHANGE_REQUEST = 'POST /api/{plm}/demo/update · /api/core/links · /api/{plm}/demo/events/cad';

/** The request of `mode` when the store has it; a question asked for falls back to the click, the change feed to nothing. */
export function drawnOf(data: AppData, mode: Mode): Drawn | null {
  if (mode === 'change') {
    const feed = data.changes.get(ALL);
    return feed?.state === 'ready' ? { kind: 'change', changes: feed.data } : null;
  }
  if (mode === 'question' && data.lastTurn) return { kind: 'question', turn: data.lastTurn };
  const answer = lastAnswer(data);
  return answer ? { kind: 'click', answer } : null;
}

/** Per-endpoint calls behind what is drawn: the answer's provenance, or the sums over the turn's tool results. */
export const callsOf = (d: Drawn | null): Call[] =>
  d?.kind === 'click' ? d.answer.envelope.provenance.calls : d?.kind === 'question' ? d.turn.calls : [];

export const requestOf = (d: Drawn) => (d.kind === 'click' ? `GET ${d.answer.request}` : d.kind === 'question' ? AGENT_REQUEST : CHANGE_REQUEST);

export const toolsOf = (t: Turn) => t.stats?.tools ?? [];

/** Milliseconds the agent spent inside its tools, as `atelier.turn` reported them. */
export const toolMs = (t: Turn) => toolsOf(t).reduce((sum, x) => sum + x.ms, 0);

export const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`;
