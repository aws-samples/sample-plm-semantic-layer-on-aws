// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// What the product files seed, for the smoke test and the fix cycle that read the deployed answers back. Every
// expectation is read from data/products, never from a product's name.

/** A part's stored mass in kg: the British site stores pounds. */
const kg = (part) => Number(String(part.extended.mass).replace(',', '.')) * (part.plm === 'UK' ? 0.45359237 : 1);
/** The lower median, as the shapes take it. */
const medianLow = (values) => [...values].sort((a, b) => a - b)[Math.floor((values.length - 1) / 2)];

/** Each item's occurrences in the product: the quantities of the lines multiplied down from the site kits. */
function occurrences(data) {
  const children = Map.groupBy(data.extended?.bomLines ?? [], (l) => l.parent);
  const out = new Map();
  const walk = (item, n) => (children.get(item) ?? []).forEach((l) => {
    out.set(l.child, (out.get(l.child) ?? 0) + n * l.quantity);
    walk(l.child, n * l.quantity);
  });
  for (const kit of (data.extended?.assemblies ?? []).filter((a) => a.kind === 'SITE_KIT')) walk(kit.id, 1);
  return out;
}

/**
 * The product findings a profile that sees the whole product reads, sorted, from the product file: massLimit when the
 * occurrences times the unit masses weigh more than extended.massLimitKg; massScale when a site's lower median part mass
 * is more than 100 times the lower median of the other sites' medians (sites of at least five parts with a mass).
 */
export function productFindings(data) {
  const out = [];
  const massed = data.parts.filter((p) => p.extended?.mass != null);
  const limit = data.extended?.massLimitKg;
  if (limit != null) {
    const occ = occurrences(data);
    if (massed.reduce((total, p) => total + kg(p) * (occ.get(p.id) ?? 0), 0) > Number(limit)) out.push('massLimit');
  }
  const sites = Map.groupBy(massed.filter((p) => (p.extended.type ?? 'PART') === 'PART'), (p) => p.plm);
  const medians = [...sites].filter(([, parts]) => parts.length >= 5).map(([site, parts]) => [site, medianLow(parts.map(kg))]);
  if (medians.some(([site, m]) => {
    const others = medians.filter(([s]) => s !== site).map(([, v]) => v);
    return others.length > 0 && m > 100 * medianLow(others);
  })) out.push('massScale');
  return out.sort();
}

/** Whether a profile of ontology/policy.json sees an item: the item's releasability is one of the profile's. An item
 * without a classification is released to all, as baseItems lists the assemblies and site kits. */
export const releasableTo = (profile, item) => profile.releasable.includes(item.classification?.releasableTo ?? 'ALL');

/**
 * The products whose mass limit a profile reads as failing, sorted: those whose occurrences outweigh their limit
 * (productFindings) among the products the profile sees every item of. The layer counts a product's hidden items over
 * its membership, the items of the default configuration; one hidden item makes the limit not evaluable, which raises
 * nothing, so a product with an item released to the officer alone fails for the officer and for no other profile.
 */
export function massLimitFailing(datasets, profile) {
  return datasets.filter((d) => productFindings(d).includes('massLimit') && baseItems(d).every((i) => releasableTo(profile, i)))
    .map((d) => d.product.key).sort();
}

/**
 * Ids of the parts that carry cadMissing, sorted: the parts with geometry the product files release without a
 * file-index entry (cadPending).
 */
export function cadMissingParts(datasets) {
  return datasets.flatMap((d) => d.parts.filter((p) => p.cadPending && (p.extended?.type ?? 'PART') === 'PART').map((p) => p.id)).sort();
}

/** The part whose CAD file the scripted demo publishes: the first pending part with geometry of `product`. */
export function demoPendingPart(datasets, product) {
  const parts = datasets.find((d) => d.product.key === product)?.parts ?? [];
  return parts.find((p) => p.cadPending && (p.extended?.type ?? 'PART') === 'PART');
}

/** The cadMissing parts once the CAD file of `published` is in the file index: the pending part's finding alone clears. */
export function cadMissingAfter(datasets, published) {
  return cadMissingParts(datasets).filter((id) => id !== published);
}

/**
 * Whether the equivalents answer's `group` is the file's entity-resolution `item`: the same part numbers (`ids`, the
 * members' site ids) and, when the file has members writing in metric and in inch, members reported in at least two
 * units. A size code (a dash size, an ISO 3601-1 code) is no unit system: such a member may state no unit.
 */
export function groupMatches(item, ids, group) {
  const systems = new Set(item.parts.map((m) => m.units).filter((u) => u === 'metric' || u === 'inch')).size;
  const units = new Set((group?.members ?? []).map((m) => unitsOf(m).join('+'))).size;
  const same = group !== undefined && group.members.length === ids.length && ids.every((id) => group.members.some((m) => m.id === id));
  return { ok: same && (systems < 2 || units >= 2), label: `${ids.length} part numbers${systems > 1 ? ' in metric and inch units' : ''}` };
}

/** The units an equivalents member's identifying values state, sorted: lengths carry one, a legend or a concept none. */
export function unitsOf(member) {
  return [...new Set((member.values ?? []).flatMap((v) => (v.unit ? [v.unit] : [])))].sort();
}


const UNTAGGED = { jurisdiction: 'NONE', releasableTo: 'ALL' };

/**
 * The items of a product file's default configuration, what the layer's answers list: its parts, its assemblies and
 * site kits (released to all without a classification), and its software and document items (the local id UK/3511 is
 * the row UK-3511).
 */
export function baseItems(data) {
  return [
    ...data.parts,
    ...(data.extended?.assemblies ?? []).map((a) => ({ ...a, classification: a.classification ?? UNTAGGED })),
    ...(data.extended?.nonGeometricItems ?? []).map((i) => ({ id: i.localId.replaceAll('/', '-'), plm: i.plm, type: i.type,
      classification: i.classification ?? UNTAGGED })),
  ];
}

/**
 * Every row the seeds write for a product file, as data/generate.py writes them: the base items and the parts and
 * assemblies every option of extended.variants adds (the objects of an option's lists; its ids name base items). The
 * PLMs and the core's tags hold them all; the layer's answers, in the default configuration, the base items.
 */
export function seededItems(data) {
  const options = Object.values(data.extended?.variants ?? {}).flatMap((group) => Object.values(group.options ?? {}));
  return [
    ...baseItems(data),
    ...options.flatMap((o) => (o.parts ?? []).filter((x) => typeof x === 'object')),
    ...options.flatMap((o) => (o.assemblies ?? []).filter((x) => typeof x === 'object').map((a) => ({ ...a, classification: a.classification ?? UNTAGGED }))),
  ];
}

/**
 * The product under one variant option, as data/variants.py configures it: the base items, without what the group's
 * default option is made of when the option is another one, with the option's own parts and assemblies; the base lines
 * between the items left and the option's own lines; then only what a line reaches from the site kits. `occurrences`
 * gives each reached item's drawn occurrences: the placements of its lines (one for a line without) multiplied down
 * from the kits.
 */
export function configuration(data, optionKey) {
  const group = Object.values(data.extended?.variants ?? {}).find((g) => Object.hasOwn(g.options ?? {}, optionKey));
  const option = group.options[optionKey];
  const fallback = group.options[group.default];
  const taken = option.default ? new Set() : new Set([...(fallback.parts ?? []), ...(fallback.assemblies ?? [])]);
  const own = (kind) => (option[kind] ?? []).filter((x) => typeof x === 'object').map((x) => x.id);
  const items = new Set([...baseItems(data).map((i) => i.id).filter((id) => !taken.has(id)), ...own('parts'), ...own('assemblies')]);
  const itemLines = (data.extended?.nonGeometricItems ?? []).map((i) => ({ parent: i.siteKit.replaceAll('/', '-'), child: i.localId.replaceAll('/', '-') }));
  const lines = [...data.extended?.bomLines ?? [], ...itemLines].filter((l) => items.has(l.parent) && items.has(l.child))
    .concat(option.default ? [] : option.bomLines ?? []);
  const children = Map.groupBy(lines, (l) => l.parent);
  const occurrences = new Map();
  const walk = (item, n) => (children.get(item) ?? []).forEach((l) => {
    const m = n * (l.placements?.length || 1);
    occurrences.set(l.child, (occurrences.get(l.child) ?? 0) + m);
    walk(l.child, m);
  });
  const kits = (data.extended?.assemblies ?? []).filter((a) => a.kind === 'SITE_KIT').map((a) => a.id);
  kits.forEach((kit) => walk(kit, 1));
  return { group: Object.keys(data.extended.variants).find((k) => data.extended.variants[k] === group),
    items: new Set([...items].filter((id) => kits.includes(id) || occurrences.has(id))), occurrences };
}
