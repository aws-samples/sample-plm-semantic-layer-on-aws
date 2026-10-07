// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { ReactNode } from 'react';
import type { RuleInfo } from '../api/types';
import type { Resource } from '../api/useResource';
import { TurtleView } from '../check/evidence/TurtleView';
import { ErrorState, Loading } from '../ui/ErrorState';
import { t } from '../i18n';
import { prefixed } from './prefixed';
import { RuleFailing, type FailingRow } from './RuleFailing';
import { RuleSeeded } from './RuleSeeded';
import { RuleTerms } from './RuleTerms';
import { seededFor } from './seeded';
import { SeverityChip } from './SeverityChip';
import { SparqlView } from './SparqlView';

interface Props {
  rule: RuleInfo;
  /** The failing rows of this rule, per product; a resource so the section can show it is still reading. */
  failing: Resource<FailingRow[]>;
  /** The number of other products where the rule fails for the profile; their records are not listed. */
  elsewhere: number;
  /** The product on screen, whose records and seeded defects are listed; null lists every product. */
  product: string | null;
  onRetryFailing: () => void;
  profileLabel: string;
  mapped: Set<string> | null;
  root: string | null;
  onOpenRecord: (productKey: string, hash: string) => void;
}

const Section = ({ title, children }: { title: ReactNode; children: ReactNode }) => (
  <section className="rules-section">
    <h3 className="eyebrow">{title}</h3>
    {children}
  </section>
);

/** One rule: what it checks in plain words, the shape as loaded, the terms it reads, and where it fires. */
export function RuleDetail({ rule, failing, elsewhere, product, onRetryFailing, profileLabel, mapped, root, onOpenRecord }: Props) {
  return (
    <article className={`rules-detail${rule.severity === 'Warning' ? ' is-warning' : ''}`} aria-label={rule.name}>
      <header className="rules-head">
        <div className="rules-title">
          <h2 className="mono">{rule.name}</h2>
          <SeverityChip severity={rule.severity} />
        </div>
        <span className="mono quiet rules-shape" title={rule.shape}>{prefixed(rule.shape)}</span>
      </header>
      <p className="rules-lead">{rule.description}</p>
      <p className="rules-requires"><span className="eyebrow">{t('rules.rule-detail.requires')}</span> {rule.message}</p>
      <p className="rules-focus">
        <span className="eyebrow">{t('rules.rule-detail.attachesTo')}</span> {t(`rules.attaches.${rule.focus}`)}
        <span className="quiet"> · {t(rule.severity === 'Warning' ? 'rules.severity.warningMeans' : 'rules.severity.violationMeans')}</span>
      </p>

      <div className="rules-body">
        <div className="rules-main">
          <Section title={t('rules.rule-detail.theShapeAsLoaded')}>
            <div className="rules-code"><TurtleView text={rule.turtle.trimEnd()} /></div>
          </Section>
          {rule.sparql.map((q, i) => (
            <Section key={i} title={<>{t(`rules.sparql-role.${q.role}`)}{rule.sparql.length > 1 ? ` ${i + 1}/${rule.sparql.length}` : ''}</>}>
              {q.message ? <p className="rules-message"><span className="quiet">{t('rules.rule-detail.reports')}</span> <span className="mono">{q.message}</span></p> : null}
              <div className="rules-code"><SparqlView query={q.query} /></div>
            </Section>
          ))}
          <Section title={t('rules.rule-detail.ontologyTermsItReads')}>
            <RuleTerms terms={rule.terms} mapped={mapped} root={root} />
          </Section>
        </div>
        <aside className="rules-aside">
          <Section title={`${t('rules.rule-detail.failingNowAs')} ${profileLabel}`}>
            {failing.state === 'error' ? (
              <ErrorState title={t('rules.rule-detail.theFailuresCouldNotBeRead')} error={failing.error} onRetry={onRetryFailing} />
            ) : failing.state === 'loading' ? (
              <Loading what={t('rules.rule-detail.runningTheRules')} />
            ) : (
              <RuleFailing rows={failing.data} onOpenRecord={onOpenRecord} />
            )}
            {failing.state === 'ready' && elsewhere > 0 ? (
              <p className="quiet rules-elsewhere">{t('rules.rule-detail.alsoFailingIn')} {elsewhere} {t(elsewhere === 1 ? 'rules.rule-detail.otherProduct' : 'rules.rule-detail.otherProducts')}</p>
            ) : null}
          </Section>
          <Section title={t('rules.rule-detail.seededInTheProductFiles')}>
            <RuleSeeded seeded={seededFor(rule.name, product)} />
          </Section>
        </aside>
      </div>
    </article>
  );
}
