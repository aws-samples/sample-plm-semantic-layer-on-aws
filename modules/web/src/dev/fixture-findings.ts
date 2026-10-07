// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The release findings of the fixture parts and the product rules, derived from the data as it stands, the way the
// sh:Warning shapes and the product rule of ontology/shapes.ttl report them: cadMissing, lifecycleConflict (over the
// bill-of-materials lines and the external references), massScale, conflictingLeadTime, and massLimit on the product.
// A correction released in fixture mode changes the data; refreshFindings derives them again. Never part of a
// production build.
import type { BomNode, MassLimit, Part, PartFinding, Policy } from '../api/types';
import { eachNode, itemFindings } from './fixture-bom';
import { visible } from './fixture-policy';
import { conflictsOf } from './fixture-purchasing';
import { referenceRows, resolve } from './fixture-references';
import { CAD_MISSING, cadPendingIds, files, LIFECYCLE_STATE, parts, products } from './fixture-seed';

const STATE_LABEL: Record<string, string> = { WORKING: 'Working', RELEASED: 'Released', BLOCKED: 'Blocked', SUPERSEDED: 'Superseded' };
const UNRELEASED = new Set(['WORKING', 'BLOCKED']);
const code = (plm: string) => plm.toUpperCase();
const stateOf = (word: string | undefined) => (word ? LIFECYCLE_STATE[word] : undefined);
const said = (item: { plm?: string; id: string; name: string; lifecycle?: string }) =>
  `${code(item.plm ?? '')} ${item.id} ${item.name}, which is ${item.lifecycle} (${STATE_LABEL[stateOf(item.lifecycle) ?? ''] ?? 'no state'})`;
const subject = (item: { plm?: string; id: string; name: string; lifecycle?: string }) =>
  `${code(item.plm ?? '')} ${item.id} ${item.name} is ${item.lifecycle} (${STATE_LABEL[stateOf(item.lifecycle) ?? ''] ?? 'no state'})`;

/** The findings derived on each item, by `plm|id`. */
type Found = Map<string, PartFinding[]>;
const add = (found: Found, plm: string, id: string, f: PartFinding) => {
  const k = `${plm}|${id}`;
  const list = found.get(k) ?? [];
  if (!list.some((x) => x.rule === f.rule && x.message === f.message)) found.set(k, [...list, f]);
};

/** A RELEASED item over a WORKING or BLOCKED one: a child of its bill-of-materials lines, or the part a reference of it resolves to. */
function lifecycleConflicts(found: Found) {
  const nodes = new Map<string, BomNode>();
  eachNode((n) => {
    if (!n.plm) return;
    nodes.set(`${n.plm}|${n.id}`, n);
    if (stateOf(n.lifecycle) !== 'RELEASED') return;
    for (const c of n.children) {
      if (c.redacted || !c.plm || !UNRELEASED.has(stateOf(c.lifecycle) ?? '')) continue;
      add(found, n.plm, n.id, { rule: 'lifecycleConflict', message: `${subject(n)} but its bill of materials holds ${said(c)}`, value: { plm: c.plm, kind: 'part', id: c.id } });
    }
  });
  for (const ref of referenceRows) {
    const local = parts.find((p) => p.plm === ref.plm && p.id === ref.part) ?? nodes.get(`${ref.plm}|${ref.part}`);
    const target = resolve(ref.remoteUrn);
    if (!local || !target || stateOf(local.lifecycle) !== 'RELEASED' || !UNRELEASED.has(stateOf(target.lifecycle) ?? '')) continue;
    add(found, ref.plm, ref.part, {
      rule: 'lifecycleConflict', message: `${subject(local)} but references ${ref.remoteUrn}, ${said(target)}`, value: { plm: target.plm, kind: 'part', id: target.id },
    });
  }
}

/** The lower median, as the shape takes it. */
const median = (vs: number[]) => [...vs].sort((a, b) => a - b)[Math.floor((vs.length - 1) / 2)];
const shown = (kg: number) => Number(kg.toPrecision(3)).toString();

/** A site whose median part mass in a product is over 100 times the other sites': it enters grams in a kilogram column. */
function massScale(product: string, ofProduct: Part[]): PartFinding | null {
  const bySite = new Map<string, number[]>();
  for (const p of ofProduct) if ((p.partType ?? 'PART') === 'PART' && p.mass?.kg != null) bySite.set(p.plm, [...(bySite.get(p.plm) ?? []), p.mass.kg]);
  const medians = [...bySite.entries()].filter(([, vs]) => vs.length >= 5).map(([plm, vs]) => ({ plm, m: median(vs) }));
  for (const { plm, m } of medians) {
    const others = medians.filter((o) => o.plm !== plm).map((o) => o.m);
    if (others.length === 0) continue;
    const other = median(others);
    if (other > 0 && m > 100 * other) {
      return {
        rule: 'massScale', value: { plm, kind: 'site', id: code(plm) },
        message: `${code(plm)} part masses in ${product} are ${Math.round(m / other)} times the other sites' (median ${shown(m)} kg against ${shown(other)} kg): the site stores its masses in another unit than its column states`,
      };
    }
  }
  return null;
}

const partsOfProduct = (key: string) => {
  const ids = products.find((p) => p.key === key)?.partIds;
  return ids ? parts.filter((p) => ids.has(p.id)) : [];
};

/** Derives every part finding again from the data as it stands: the seed parts carry theirs, the items without geometry theirs. */
export function refreshFindings() {
  const found: Found = new Map();
  lifecycleConflicts(found);
  for (const { key } of products) {
    const scale = massScale(key, partsOfProduct(key));
    if (scale) for (const p of partsOfProduct(key).filter((x) => x.plm === scale.value?.plm && x.mass)) add(found, p.plm, p.id, { ...scale, value: { plm: p.plm, kind: 'part', id: p.id } });
    for (const c of conflictsOf(key)) add(found, c.plm, c.id, { rule: 'conflictingLeadTime', message: c.message, value: { plm: c.plm, kind: 'offer', id: c.longer } });
  }
  for (const p of parts) {
    const all = [...(p.cadFile === null && cadPendingIds.has(p.id) ? [CAD_MISSING] : []), ...(found.get(`${p.plm}|${p.id}`) ?? [])];
    if (all.length) p.findings = all;
    else delete p.findings;
    found.delete(`${p.plm}|${p.id}`);
  }
  itemFindings.clear();
  for (const [k, fs] of found) itemFindings.set(k, fs);
}
refreshFindings();

const LIMIT_KG = new Map(files.map((f) => [f.product.key, (f as { extended?: { massLimitKg?: string | number } | null }).extended?.massLimitKg]));

/**
 * The product rules over every site's parts: massLimit (the parts' masses summed against the product's limit) and
 * massScale, each once. A profile that may not see every part gets massLimit not evaluable and no product finding.
 */
export function productRules(key: string, pol: Policy): { findings?: PartFinding[]; massLimit?: MassLimit } {
  const all = partsOfProduct(key);
  const hidden = all.filter((p) => !visible(pol, p)).length;
  const raw = LIMIT_KG.get(key);
  const limitKg = raw === undefined || raw === null ? null : Number(raw);
  if (hidden) return limitKg === null ? {} : { massLimit: { status: 'not-evaluable', limitKg, hiddenItems: hidden } };
  const findings: PartFinding[] = [];
  let massLimit: MassLimit | undefined;
  if (limitKg !== null) {
    const total = all.filter((p) => (p.partType ?? 'PART') === 'PART').reduce((s, p) => s + (p.mass?.kg ?? 0), 0);
    const fails = total > limitKg;
    massLimit = { status: fails ? 'fail' : 'pass', limitKg };
    if (fails) findings.push({ rule: 'massLimit', message: `${key} weighs ${total.toFixed(3)} kg over every site's parts, more than its limit of ${limitKg.toFixed(3)} kg` });
  }
  const scale = massScale(key, all);
  if (scale) findings.push({ rule: scale.rule, message: scale.message });
  return { ...(findings.length ? { findings } : {}), ...(massLimit ? { massLimit } : {}) };
}
