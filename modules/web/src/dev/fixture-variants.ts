// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The variant groups of the ornithopter and the wind turbine and the diff of each of their options, as
// GET /api/query/variants?product= and GET /api/query/variant-diff?product=&group=&option= answer them in the dev
// server with VITE_FIXTURES=1. fixture-variants.json holds the answers of the fixture stack
// (modules/query-service/fixtures/run.sh) for the programme-cleared profile, without their request texts, and every
// profile receives them: the parts each diff names and the configurations' tallies are programme-cleared's.
import type { VariantDiff, VariantGroups } from '../api/types';
import variants from './fixture-variants.json';

const data = variants as unknown as { groups: Record<string, VariantGroups>; diffs: Record<string, Omit<VariantDiff, 'provenance' | 'sparql' | 'timings' | 'policy'>> };

/** The product's variant groups; none for a product the fixture gives none. */
export const variantGroupsOf = (product: string): VariantGroups => data.groups[product] ?? { product, groups: [] };

/** The diff of one option, or an error naming the product's groups, as the service answers an unknown option. */
export function variantDiffOf(product: string, group: string, option: string) {
  const diff = data.diffs[`${product}|${group}|${option}`];
  if (diff) return diff;
  const groups = variantGroupsOf(product).groups.map((g) => `${g.key} (${g.options.join(', ')})`).join('; ');
  throw new Error(`HTTP 404: no item variant ${group} option ${option} in ${product}; its variant groups: ${groups}`);
}
