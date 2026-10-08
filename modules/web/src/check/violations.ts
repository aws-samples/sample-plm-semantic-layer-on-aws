// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { Feature, Interface, ReferenceRule, ReferenceStatus, Rule, Status, Violation } from '../api/types';
import { isFeature } from '../api/types';

/** Violations of one rule on one set of features, e.g. the two symmetric results of a mated pair. */
export interface Finding {
  key: string;
  rule: Rule;
  shape: string;
  features: string[];
  violations: Violation[];
}

export const RULE_LABEL: Record<Rule, string> = {
  unit: 'Unit',
  position: 'Position',
  connector: 'Connector',
  orphan: 'Orphan',
  fastener: 'Fastener',
  hydraulic: 'Hydraulic',
  kind: 'Kind',
  doubleMate: 'Double mate',
};

export const REFERENCE_LABEL: Record<ReferenceStatus, string> = {
  ok: 'ok',
  danglingReference: 'Dangling reference',
  staleRevision: 'Stale revision',
  'not-evaluable': 'not evaluable',
};

/** Whether a part finding comes from a reference rule; the part's reference list shows those beside the reference. */
export const isReferenceRule = (rule: string): rule is ReferenceRule => rule === 'danglingReference' || rule === 'staleRevision';

export const STATUS_LABEL: Record<Status, string> = {
  pass: 'pass',
  fail: 'fail',
  'not-evaluable': 'not evaluable',
};

export function findings(itf: Interface): Finding[] {
  const byKey = new Map<string, Finding>();
  for (const v of itf.violations) {
    const ids = [...v.features].sort();
    const key = `${v.rule}|${ids.join('|')}`;
    const f = byKey.get(key) ?? { key, rule: v.rule, shape: v.shape, features: v.features, violations: [] };
    f.violations.push(v);
    byKey.set(key, f);
  }
  return [...byKey.values()];
}

export const rulesOf = (itf: Interface): Rule[] => [...new Set(itf.violations.map((v) => v.rule))];

/** Ids of features named by any violation of the interface. */
export const failingFeatures = (itf: Interface) => new Set(itf.violations.flatMap((v) => v.features));

/** The features the profile may see. */
export const features = (itf: Interface): Feature[] => itf.features.filter(isFeature);

export const featureById = (itf: Interface, id: string): Feature | undefined => features(itf).find((f) => f.id === id);

export const localName = (iri: string) => iri.replace(/^.*[#/]/, '');

export function num(v: unknown): number | null {
  return typeof v === 'number' && Number.isFinite(v) ? v : null;
}
