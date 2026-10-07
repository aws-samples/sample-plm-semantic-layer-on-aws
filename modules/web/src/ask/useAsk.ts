// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { AgentSubscriber, HttpAgent, HttpAgentFetchFn } from '@ag-ui/client';
import { useCallback, useMemo, useRef, useState } from 'react';
import { reloadToSignIn } from '../api/client';
import { PROFILE_HEADER, type ProfileId } from '../api/profile';
import type { Product } from '../api/types';
import { conversationalHistory } from './history';
import type { Selection } from '../viewer/view';
import { agentAction, evidenceBlock, highlightBlock, mergeCalls, redactionIn, sqlRun, tableBlock, toolProvenance, toRequest, turnStats, viewBlock } from './tools';
import type { AgentAction, Block, EvidenceBlock, EvidenceRequest, Turn, ViewBlock } from './types';

/** Same origin as the site: CloudFront routes /agent/* to the agent and the sign-in cookie rides along. */
const AGENT_PATH = '/agent/invocations';
/** One id per conversation keeps its turns on one server-side session. */
const SESSION_HEADER = 'x-atelier-session';

// A fake AG-UI stream when the dev server runs with VITE_FIXTURES=1; never part of a production build.
let fixtureFetch: Promise<HttpAgentFetchFn> | null = null;
if (import.meta.env.DEV && import.meta.env.VITE_FIXTURES === '1') {
  fixtureFetch = import('../dev/fixture-agent').then((m) => m.fixtureAgentFetch);
}

export interface AskActions {
  /** Selects an interface on the Interface check screen. */
  onSelect: (id: string) => void;
  /** Opens the evidence drawer on a card and tab. */
  onEvidence: (req: EvidenceRequest) => void;
  /** Carries out a viewer or loading tool: what the viewer shows, the subtree, the product or the screen. */
  onAction: (action: AgentAction) => void;
}

export interface Ask {
  turns: Turn[];
  running: boolean;
  ask: (question: string) => void;
  /** Asks a failed turn's question again. */
  retry: (turnId: string) => void;
  /** Carries out an `open_evidence` block again. */
  reopen: (block: EvidenceBlock) => void;
  /** Carries out a viewer or loading block again. */
  replay: (block: ViewBlock) => void;
  reset: () => void;
}

interface Conversation {
  profile: ProfileId;
  http: HttpAgent;
}

/**
 * @param product the product on screen, sent with every run as `forwardedProps.product` so the agent answers for it; null sends none
 * @param selection what is on screen and selected, sent with every run as `forwardedProps.selection` so "this part" resolves
 */
export function useAsk(profile: ProfileId, product: Product | null, selection: Selection, actions: AskActions): Ask {
  const [turns, setTurns] = useState<Turn[]>([]);
  const [running, setRunning] = useState(false);
  const busy = useRef(false);
  const conv = useRef<Conversation | null>(null);
  const seq = useRef(0);
  const act = useRef(actions);
  act.current = actions;
  const onScreen = useRef(product);
  onScreen.current = product;
  const selected = useRef(selection);
  selected.current = selection;

  const run = useCallback(async (question: string) => {
    const text = question.trim();
    if (!text || busy.current) return;
    busy.current = true;
    setRunning(true);
    const turnId = `turn-${seq.current++}`;
    const patch = (fn: (t: Turn) => Turn) => setTurns((ts) => ts.map((t) => (t.id === turnId ? fn(t) : t)));
    setTurns((ts) => [...ts, { id: turnId, question: text, profile, blocks: [], state: 'running', stats: null, sqlRuns: [], calls: [], actor: null, redacted: false }]);

    // One agent per conversation and profile: a profile change starts a fresh session, so no
    // history built under another profile is replayed. The AG-UI client only downloads here,
    // on the first question, so the screens do not carry it.
    if (conv.current?.profile !== profile) {
      const { HttpAgent } = await import('@ag-ui/client');
      conv.current = {
        profile,
        http: new HttpAgent({
          url: `${location.origin}${AGENT_PATH}`,
          headers: { [PROFILE_HEADER]: profile, [SESSION_HEADER]: `atelier-ask-${crypto.randomUUID()}` },
          fetch: fixtureFetch ? await fixtureFetch : undefined,
        }),
      };
    }
    const http = conv.current.http;
    const history = conversationalHistory(http.messages);
    http.setMessages(history);
    http.addMessage({ id: `u-${Date.now()}`, role: 'user', content: text });

    const add = (b: Block) => patch((t) => ({ ...t, blocks: [...t.blocks, b] }));
    const toolNames = new Map<string, string>();
    const prose = new Map<string, string>();
    let failed = false;
    const fail = (message: string) => {
      failed = true;
      patch((t) => ({ ...t, state: 'error', error: message }));
    };
    const subscriber: AgentSubscriber = {
      // Prose is keyed by message id: each delta extends that message's block in place.
      onTextMessageContentEvent: ({ event }) => {
        const id = `prose:${event.messageId}`;
        const text = (prose.get(id) ?? '') + event.delta;
        prose.set(id, text);
        patch((t) => ({
          ...t,
          blocks: t.blocks.some((b) => b.id === id)
            ? t.blocks.map((b) => (b.id === id ? { ...b, text } : b))
            : [...t.blocks, { kind: 'prose', id, text }],
        }));
      },
      onToolCallStartEvent: ({ event }) => {
        toolNames.set(event.toolCallId, event.toolCallName);
      },
      // Frontend tools return nothing; the browser paints them from the call.
      onToolCallEndEvent: ({ event, toolCallName, toolCallArgs }) => {
        const id = `tool:${event.toolCallId}`;
        const action = agentAction(toolCallName, toolCallArgs);
        if (action) {
          add(viewBlock(id, toolCallName, action));
          act.current.onAction(action);
        } else if (toolCallName === 'render_table') add(tableBlock(id, toolCallArgs));
        else if (toolCallName === 'highlight_interfaces') {
          const b = highlightBlock(id, toolCallArgs);
          add(b);
          if (b.ids[0]) act.current.onSelect(b.ids[0]);
        } else if (toolCallName === 'open_evidence') {
          const b = evidenceBlock(id, toolCallArgs);
          add(b);
          if (b.interfaceId) {
            act.current.onSelect(b.interfaceId);
            act.current.onEvidence(toRequest(b, seq.current++));
          }
        }
      },
      // Every semantic-layer tool answers with the provenance of the sources it read; the sql
      // tool also with the statement that actually ran. A redaction is what a result says, never what the prose says.
      onToolCallResultEvent: ({ event }) => {
        const p = toolProvenance(event.content);
        if (p) patch((t) => ({ ...t, calls: mergeCalls(t.calls, p.calls), actor: p.actor ?? t.actor }));
        if (redactionIn(event.content, toolNames.get(event.toolCallId))) patch((t) => ({ ...t, redacted: true }));
        if (toolNames.get(event.toolCallId) !== 'sql') return;
        const r = sqlRun(event.content);
        if (r) patch((t) => ({ ...t, sqlRuns: [...t.sqlRuns, r] }));
      },
      onCustomEvent: ({ event }) => {
        if (event.name === 'atelier.turn') patch((t) => ({ ...t, stats: turnStats(event.value) }));
      },
      onRunErrorEvent: ({ event }) => fail(event.message || 'The agent stopped with an error.'),
    };

    try {
      // A RUN_ERROR event resolves the run; a transport failure rejects it. The product on screen rides
      // along as forwardedProps.product, the agent's default for every tool that takes one, and the
      // selection as forwardedProps.selection.
      const p = onScreen.current;
      const forwardedProps = { ...(p ? { product: { key: p.key, name: p.name } } : {}), selection: selected.current };
      await http.runAgent({ forwardedProps }, subscriber);
      if (!failed) patch((t) => ({ ...t, state: 'done' }));
    } catch (e) {
      if (!failed) fail(describe(e));
    } finally {
      // A failed run leaves nothing worth replaying.
      if (failed) http.setMessages(history);
      busy.current = false;
      setRunning(false);
    }
  }, [profile]);

  const ask = useCallback((q: string) => void run(q), [run]);
  const retry = useCallback((turnId: string) => {
    const t = turns.find((x) => x.id === turnId);
    if (!t || busy.current) return;
    setTurns((ts) => ts.filter((x) => x.id !== turnId));
    void run(t.question);
  }, [turns, run]);
  const reopen = useCallback((b: EvidenceBlock) => {
    act.current.onSelect(b.interfaceId);
    act.current.onEvidence(toRequest(b, seq.current++));
  }, []);
  const replay = useCallback((b: ViewBlock) => act.current.onAction(b.action), []);
  const reset = useCallback(() => {
    if (busy.current) return;
    conv.current = null;
    setTurns([]);
  }, []);

  return useMemo(() => ({ turns, running, ask, retry, reopen, replay, reset }), [turns, running, ask, retry, reopen, replay, reset]);
}

/** The most recent turn that finished with its `atelier.turn` summary, asked under `profile`; what the Data flow tab draws. */
export function lastCompletedTurn(turns: readonly Turn[], profile: ProfileId): Turn | null {
  for (let i = turns.length - 1; i >= 0; i--) {
    const t = turns[i];
    if (t.state === 'done' && t.stats && t.profile === profile) return t;
  }
  return null;
}

/** A 401 from the Cognito edge is an expired sign-in: reload once so the edge signs the viewer in again. */
function describe(e: unknown): string {
  const status = (e as { status?: unknown } | null)?.status;
  if (status === 401) {
    try {
      reloadToSignIn(AGENT_PATH, status);
    } catch (err) {
      return (err as Error).message;
    }
  }
  return e instanceof Error ? e.message : String(e);
}
