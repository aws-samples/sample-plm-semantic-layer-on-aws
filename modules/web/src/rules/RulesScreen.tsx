// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useEffect, useMemo } from 'react';
import { PROFILES } from '../api/profile';
import { ALL, useCached, type AppData } from '../api/store';
import type { Product, RuleFailure, RuleInfo } from '../api/types';
import type { Resource } from '../api/useResource';
import { ErrorState, Loading } from '../ui/ErrorState';
import { sourcesOf } from '../ui/plm';
import { t } from '../i18n';
import { FOCUS_ORDER } from './prefixed';
import { RuleDetail } from './RuleDetail';
import type { FailingRow } from './RuleFailing';
import { RuleList } from './RuleList';
import { seededFor } from './seeded';

interface Props {
  data: AppData;
  /** The products the App lists, for their names. */
  products: Product[];
  ruleName: string | null;
  onOpenRecord: (productKey: string, hash: string) => void;
}

/** What the semantic layer checks and how: every shape, its Turtle and SPARQL, the terms it reads, and where it fires. */
export function RulesScreen({ data, products, ruleName, onOpenRecord }: Props) {
  const rules = useCached(data.rules, ALL);
  const failures = useCached(data.ruleFailures, ALL);
  const mapped = useMappedTerms(data);
  const listed = useMemo(
    () => (rules.state === 'ready' ? FOCUS_ORDER.flatMap((f) => rules.data.rules.filter((r) => r.focus === f)) : []),
    [rules],
  );
  const failing = useMemo(() => {
    if (failures.state !== 'ready') return null;
    const n = new Map<string, number>();
    for (const f of failures.data.failures) n.set(f.rule, (n.get(f.rule) ?? 0) + 1);
    return n;
  }, [failures]);

  if (rules.state === 'error') {
    return <div className="stage-error"><ErrorState title={t('rules.rules-screen.theRulesCouldNotBeRead')} error={rules.error} onRetry={() => data.rules.retry(ALL)} /></div>;
  }
  if (rules.state === 'loading') return <div className="stage-error"><Loading what={t('rules.rules-screen.readingTheRules')} /></div>;
  const rule = listed.find((r) => r.name === ruleName) ?? listed[0] ?? null;
  const product = data.product?.key ?? null;
  const profileLabel = PROFILES.find((p) => p.id === data.profile)?.label ?? data.profile;
  return (
    <div className="rules">
      <RuleList rules={listed} selected={rule?.name ?? null} failing={failing} root={data.root} />
      <div className="rules-pane">
        {rule ? (
          <RuleDetail
            key={rule.name}
            rule={rule}
            failing={rowsOf(rule, failures, products, product)}
            elsewhere={failures.state === 'ready' ? failures.data.otherProducts?.[rule.name] ?? 0 : 0}
            product={product}
            onRetryFailing={() => data.ruleFailures.retry(ALL)}
            profileLabel={profileLabel}
            mapped={mapped}
            root={data.root}
            onOpenRecord={onOpenRecord}
          />
        ) : (
          <p className="quiet hint">{t('rules.rules-screen.theQueryServiceListsNoRules')}</p>
        )}
      </div>
    </div>
  );
}

/**
 * Per product, the rule's failing records, of the product on screen when one is selected; a product whose file seeds
 * the rule is listed with 0 when nothing fails for the profile, which says the records are hidden from it or corrected.
 */
function rowsOf(rule: RuleInfo, res: Resource<{ failures: RuleFailure[] }>, products: Product[], product: string | null): Resource<FailingRow[]> {
  if (res.state !== 'ready') return res;
  const seeded = seededFor(rule.name, product);
  const nameOf = (key: string) => products.find((p) => p.key === key)?.name ?? seeded.find((s) => s.product === key)?.name ?? key;
  const byProduct = new Map<string, RuleFailure[]>();
  for (const s of seeded) byProduct.set(s.product, []);
  for (const f of res.data.failures) if (f.rule === rule.name) byProduct.set(f.product, [...(byProduct.get(f.product) ?? []), f]);
  const rows = [...byProduct.entries()].map(([product, failures]) => ({ product, name: nameOf(product), failures }));
  rows.sort((a, b) => b.failures.length - a.failures.length || a.name.localeCompare(b.name));
  return { state: 'ready', data: rows };
}

/** The ontology terms some catalogue column maps; null until every catalogue has answered or failed. */
function useMappedTerms(data: AppData): Set<string> | null {
  const sources = useMemo(() => sourcesOf(data.config.plms), [data.config.plms]);
  const { ensure, get } = data.catalogues;
  useEffect(() => {
    for (const s of sources) ensure(s);
  }, [ensure, sources]);
  return useMemo(() => {
    const answers = sources.map((s) => get(s));
    if (!answers.every((r) => r && r.state !== 'loading')) return null;
    const terms = new Set<string>();
    for (const r of answers) if (r?.state === 'ready') for (const e of r.data.entities) for (const c of e.columns) if (c.ontologyTerm) terms.add(c.ontologyTerm);
    return terms;
  }, [get, sources]);
}
