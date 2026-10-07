// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { RuleInfo } from '../api/types';
import { withRoot } from '../subtree/rootHash';
import { toHash } from '../ui/useHashRoute';
import { t } from '../i18n';
import { FOCUS_ORDER } from './prefixed';
import { SeverityChip } from './SeverityChip';

interface Props {
  rules: RuleInfo[];
  selected: string | null;
  /** Failing records per rule for the profile; absent while the failures are read. */
  failing: Map<string, number> | null;
  root: string | null;
}

/** The rules grouped by what they attach to, each a link to its detail. */
export function RuleList({ rules, selected, failing, root }: Props) {
  return (
    <nav className="rules-list" aria-label={t('rules.rule-list.rules')}>
      {FOCUS_ORDER.map((focus) => {
        const group = rules.filter((r) => r.focus === focus);
        if (group.length === 0) return null;
        return (
          <section key={focus} className="rules-group">
            <h2 className="eyebrow">{t(`rules.focus.${focus}`)}</h2>
            <ul>
              {group.map((r) => {
                const n = failing?.get(r.name) ?? 0;
                return (
                  <li key={r.name}>
                    <a
                      className={`rules-item${r.name === selected ? ' is-selected' : ''}${r.severity === 'Warning' ? ' is-warning' : ''}`}
                      aria-current={r.name === selected ? 'true' : undefined}
                      href={withRoot(toHash({ screen: 'rules', ruleName: r.name }), root)}
                    >
                      <span className="mono rules-item-name">{r.name}</span>
                      {failing ? <span className={`rules-count${n ? '' : ' is-zero'}`} title={t('rules.rule-list.failingRecordsForThisProfile')}>{n}</span> : null}
                      <SeverityChip severity={r.severity} />
                    </a>
                  </li>
                );
              })}
            </ul>
          </section>
        );
      })}
    </nav>
  );
}
