// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Export-control policy of the fixtures: ontology/policy.json applied as docs/contract.md
// describes it. A part is visible iff its releasability is in the profile's list; an interface
// with a hidden side is not evaluable and its hidden features and parts are redacted.
import policyJson from '../../../../ontology/policy.json';
import { PROFILES } from '../api/profile';
import { isFeature, isPart, type Interface, type Part, type PartEntry, type Policy } from '../api/types';

interface ProfileDef {
  label: string;
  nationality: string | null;
  releasable: string[];
}

const profiles = policyJson.profiles as Record<string, ProfileDef>;

// The UI hard-codes the six profile labels; this copy of the policy must agree with them.
for (const p of PROFILES) {
  if (profiles[p.id]?.label !== p.label) throw new Error(`profile ${p.id}: UI label "${p.label}" differs from ontology/policy.json`);
}

/** A missing or unknown profile is `unknown`, which sees only parts releasable to ALL. */
export function policyFor(profile: string): Policy {
  const id = profile in profiles ? profile : 'unknown';
  const releasable = profiles[id].releasable;
  return { profile: id, releasable, filter: `FILTER(?rel IN (${releasable.map((r) => `"${r}"`).join(', ')}))` };
}

export const visible = (pol: Policy, part: Part) => pol.releasable.includes(part.releasableTo ?? '');

export const redactParts = (parts: Part[], pol: Policy): PartEntry[] =>
  parts.map((p) => (visible(pol, p) ? p : { redacted: true, plm: p.plm }));

const HIDDEN = 'a part not visible to your profile';

/** The label names the parts in their order, "A / B", with an optional bracketed note at the end; a hidden part's name gives way. */
function redactLabel(label: string, parts: PartEntry[]): string {
  const m = /^(.*?)(\s\([^()]*\))?$/.exec(label);
  const segments = (m?.[1] ?? label).split(' / ');
  if (!m || segments.length !== parts.length) return label;
  return segments.map((s, i) => (parts[i].redacted ? HIDDEN : s)).join(' / ') + (m[2] ?? '');
}

export function redactInterface(itf: Interface, pol: Policy): Interface {
  const hiddenParts = new Set(itf.parts.filter(isPart).filter((p) => !visible(pol, p)).map((p) => p.id));
  if (hiddenParts.size === 0) return itf;
  const hiddenFeatures = new Set(itf.features.filter(isFeature).filter((f) => hiddenParts.has(f.partId)).map((f) => f.id));
  const parts: PartEntry[] = itf.parts.map((p) => (isPart(p) && hiddenParts.has(p.id) ? { redacted: true, plm: p.plm } : p));
  return {
    ...itf,
    label: redactLabel(itf.label, parts),
    status: 'not-evaluable',
    violations: [],
    parts,
    features: itf.features.map((f) => {
      if (!isFeature(f)) return f;
      if (hiddenParts.has(f.partId)) return { redacted: true, plm: f.plm };
      // A visible feature must not name its hidden mate.
      return { ...f, matesWith: f.matesWith.filter((m) => !hiddenFeatures.has(m)) };
    }),
  };
}
