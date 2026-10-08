// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// GET /query/rules/failures in the dev server with VITE_FIXTURES=1: one entry per failing record, derived from the
// fixture answers the Interface check screen shows for the profile (the interfaces' violations, the visible parts'
// findings, the failing external references and the product rules), of every product or of one product with the count of
// the other products per rule. Never part of a production build.
import type { Policy, RuleFailure } from '../api/types';
import { isPart } from '../api/types';
import { assemblyParts } from './fixture-bom';
import { productRules } from './fixture-findings';
import { withMeshFindings } from './fixture-paths';
import { redactInterface, redactParts } from './fixture-policy';
import { interfacesOf, partsOf } from './fixture-products';
import { referencesOf } from './fixture-references';
import { products } from './fixture-seed';
import { byId } from '../ui/ids';
import { configuredParts, type Configured } from './fixture-configuration';
import { interfacesIn, type Rooted } from './fixture-subtree';

/** What a scoped answer reads: the subtree of a root, the parts of an option's configuration; null for the product as it is. */
interface Scope {
  rooted: Rooted | null;
  configured: Configured | null;
}

const WHOLE: Scope = { rooted: null, configured: null };

/** The failing records of one product for the profile, within the scope: no product record under a subtree root. */
function productFailures(key: string, pol: Policy, { rooted, configured }: Scope): RuleFailure[] {
  const out = new Map<string, RuleFailure>();
  const add = (f: RuleFailure) => out.set(`${f.rule}|${f.product}|${f.kind}|${f.id}`, f);
  const within = <T extends { id: string }>(xs: T[]) => (rooted ? xs.filter((x) => rooted.ids.has(x.id)) : xs);
  const interfaces = rooted ? interfacesIn(rooted, interfacesOf(key)) : interfacesOf(key);
  for (const itf of interfaces.map((i) => redactInterface(i, pol))) {
    for (const v of itf.violations) add({ rule: v.rule, product: key, kind: 'interface', id: itf.id, plm: null });
  }
  const parts = withMeshFindings(key, redactParts(within(configuredParts(partsOf(key), configured)), pol), pol).filter(isPart);
  for (const p of [...parts, ...within(assemblyParts(key))]) {
    for (const f of p.findings ?? []) add({ rule: f.rule, product: key, kind: 'part', id: p.id, plm: p.plm });
  }
  for (const r of referencesOf(key, pol).references) {
    if ((r.status === 'danglingReference' || r.status === 'staleRevision') && (!rooted || rooted.ids.has(r.part))) {
      add({ rule: r.status, product: key, kind: 'part', id: r.part, plm: r.plm });
    }
  }
  if (!rooted) {
    // A product rule's finding is a product record only when it names none of the product's parts (massLimit; massScale names the site's parts).
    const onParts = new Set([...out.values()].filter((f) => f.kind === 'part').map((f) => f.rule));
    for (const f of (productRules(key, pol).findings ?? []).filter((x) => !onParts.has(x.rule))) add({ rule: f.rule, product: key, kind: 'product', id: key, plm: null });
  }
  return [...out.values()];
}

const ordered = (fs: RuleFailure[]) =>
  fs.sort((a, b) => a.rule.localeCompare(b.rule) || a.product.localeCompare(b.product) || a.kind.localeCompare(b.kind) || byId(a.id, b.id));

/** Every product's failing records for the profile. */
export function failuresFor(pol: Policy): RuleFailure[] {
  return ordered(products.flatMap(({ key }) => productFailures(key, pol, WHOLE)));
}

/** One product's failing records within the scope, and per rule the number of other products where it fails for the profile. */
export function productFailuresFor(pol: Policy, product: string, scope: Scope): { failures: RuleFailure[]; otherProducts: Record<string, number> } {
  const elsewhere = new Map<string, Set<string>>();
  for (const f of failuresFor(pol)) if (f.product !== product) elsewhere.set(f.rule, (elsewhere.get(f.rule) ?? new Set()).add(f.product));
  return {
    failures: ordered(productFailures(product, pol, scope)),
    otherProducts: Object.fromEntries([...elsewhere].sort(([a], [b]) => a.localeCompare(b)).map(([rule, keys]) => [rule, keys.size])),
  };
}
