// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { Interface } from '../api/types';
import { NotEvaluableChip, RuleChip } from './RuleChip';
import { rulesOf } from './violations';
import { t } from '../i18n';

interface Props {
  interfaces: Interface[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}

/** The interfaces that need attention: those breaking a rule, then those the profile cannot evaluate. */
export function FailList({ interfaces, selectedId, onSelect }: Props) {
  const failing = interfaces.filter((i) => i.status === 'fail');
  const hidden = interfaces.filter((i) => i.status === 'not-evaluable');
  const row = (i: Interface, chips: React.ReactNode) => (
    <li key={i.id}>
      <button
        type="button"
        className={`fail-row is-${i.status}${i.id === selectedId ? ' is-selected' : ''}`}
        aria-pressed={i.id === selectedId}
        onClick={() => onSelect(i.id)}
      >
        <span className="fail-id mono">{i.id}</span>
        <span className="fail-label">{i.label}</span>
        <span className="fail-rules">{chips}</span>
      </button>
    </li>
  );
  return (
    <>
      <h2 className="eyebrow">{t('check.fail-list.failingInterfaces')}</h2>
      {failing.length === 0 ? (
        <p className="quiet">{t('check.fail-list.every')} {hidden.length ? 'evaluable ' : ''}interface passes every rule.</p>
      ) : (
        <ul className="fail-list is-failing">{failing.map((i) => row(i, rulesOf(i).map((r) => <RuleChip key={r} rule={r} />)))}</ul>
      )}
      {hidden.length ? (
        <>
          <h2 className="eyebrow">{t('check.fail-list.notEvaluableForYourProfile')}</h2>
          <ul className="fail-list is-hidden">{hidden.map((i) => row(i, <NotEvaluableChip />))}</ul>
        </>
      ) : null}
    </>
  );
}
