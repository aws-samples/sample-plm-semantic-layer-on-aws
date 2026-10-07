// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { PROFILES, type ProfileId } from '../api/profile';
import type { AppData } from '../api/store';
import { useExamples } from './useExamples';
import { TurnView } from './TurnView';
import type { Ask } from './useAsk';
import { t } from '../i18n';

interface Props {
  ask: Ask;
  /** The answers the example questions are built from: the product, profile and subtree on screen. */
  data: AppData;
  profile: ProfileId;
  selectedId: string | null;
  onSelect: (id: string) => void;
  onClose: () => void;
}

/** Conversation with the agent over the semantic layer; its answers paint onto the check screen. */
export function AskPanel({ ask, data, profile, selectedId, onSelect, onClose }: Props) {
  const [draft, setDraft] = useState('');
  const examples = useExamples(data);
  const end = useRef<HTMLDivElement>(null);
  const label = PROFILES.find((p) => p.id === profile)?.label ?? profile;
  const last = ask.turns[ask.turns.length - 1];
  const blocks = last?.blocks.length ?? 0;
  const state = last?.state;
  // Follow the answer as it streams.
  useEffect(() => {
    end.current?.scrollIntoView({ block: 'end' });
  }, [ask.turns.length, blocks, state]);

  const send = (q: string) => {
    if (ask.running || !q.trim()) return;
    ask.ask(q);
    setDraft('');
  };
  const submit = (e: FormEvent) => {
    e.preventDefault();
    send(draft);
  };
  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send(draft);
    }
  };

  return (
    <aside className="ask" aria-label="Ask the semantic layer">
      <header className="ask-head">
        <div className="ask-head-row">
          <span className="ask-title">{t('ask.ask-panel.askTheSemanticLayer')}</span>
          <button type="button" className="btn btn-small" onClick={onClose}>{t('ask.ask-panel.close')}</button>
        </div>
        <div className="ask-head-row">
          <span className="ask-as">{t('ask.ask-panel.answeringAs')} <b>{label}</b></span>
          {ask.turns.length ? (
            <button type="button" className="ask-reset" onClick={ask.reset} disabled={ask.running}>{t('ask.ask-panel.newConversation')}</button>
          ) : null}
        </div>
      </header>
      <div className="ask-body">
        {ask.turns.length === 0 ? (
          <div className="ask-empty">
            <p>
              {t('ask.ask-panel.askAboutTheInterfacesPartsAnd')}
            </p>
            <span className="eyebrow">{t('ask.ask-panel.try')}</span>
            <ul className="ask-examples">
              {examples.map((q) => (
                <li key={q.kind}>
                  <button type="button" className="ask-chip" data-kind={q.kind} data-ids={q.ids.join(' ')} onClick={() => send(q.text)}>{q.text}</button>
                </li>
              ))}
            </ul>
          </div>
        ) : (
          ask.turns.map((t) => (
            <TurnView key={t.id} turn={t} selectedId={selectedId} onSelect={onSelect} onReopen={ask.reopen} onReplay={ask.replay} onRetry={() => ask.retry(t.id)} />
          ))
        )}
        <div ref={end} />
      </div>
      <form className="ask-composer" onSubmit={submit}>
        <textarea
          className="ask-input"
          rows={1}
          value={draft}
          placeholder={ask.turns.length ? 'Ask a follow-up' : 'Ask about an interface, a part or a rule'}
          aria-label="Your question"
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={onKey}
        />
        <button type="submit" className="btn" disabled={ask.running || !draft.trim()}>{t('ask.ask-panel.ask')}</button>
      </form>
    </aside>
  );
}
