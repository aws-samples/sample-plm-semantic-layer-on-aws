// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Purchased items and suppliers of the fixture products, as GET /api/query/equivalents?product= and GET
// /api/query/suppliers?product= answer them in the dev server with VITE_FIXTURES=1. fixture-purchasing.json holds the
// answers of the query service over the fixture stack (modules/query-service/fixtures) to the default profile,
// programme-cleared, without the envelope: the rover's fasteners and suppliers (the licensed camera module is hidden
// from the profile, so it is in no offer and no group), and the O-rings of the wind turbine and the beam engine, whose
// sites list no suppliers. Each offer gets its row key in its site's table, numbered per site; the lead-time conflicts
// are derived from the offers as they stand, so a correction released in fixture mode clears them. Each group member
// carries its part IRI, and a group is confirmed once every two members are confirmed equivalents (POST
// /core/equivalences in fixture-demo.ts), and is then stocked on one line. A product with no purchased items answers
// empty lists. Never part of a production build.
import type { EquivalentsResponse, LeadTimeConflict, PartEntry, SupplierOffer, SuppliersResponse } from '../api/types';
import { isPart } from '../api/types';
import { partIri } from './fixture-evidence';
import purchasingJson from './fixture-purchasing.json';

type Suppliers = Pick<SuppliersResponse, 'product' | 'suppliers' | 'singleSource' | 'conflicts'>;
type Answers = { equivalents: Pick<EquivalentsResponse, 'product' | 'groups'>; suppliers: Suppliers };
const answers = structuredClone(purchasingJson) as unknown as Record<string, Answers>;

for (const a of Object.values(answers)) {
  const next = new Map<string, number>();
  for (const site of a.suppliers.suppliers.flatMap((s) => s.sites)) {
    for (const offer of site.offers) {
      const n = (next.get(site.plm) ?? 0) + 1;
      next.set(site.plm, n);
      offer.id = String(n);
    }
  }
}

/** Every offer as its site holds it, with the supplier's name. */
export function offersOf(product: string): { plm: string; supplier: string; offer: SupplierOffer }[] {
  return (answers[product]?.suppliers.suppliers ?? []).flatMap((s) => s.sites.flatMap((site) => site.offers.map((offer) => ({ plm: site.plm, supplier: s.name, offer }))));
}

/** The parts with the suppliers that offer them appended to their suppliers, "name, town" as the file index names a builder, as the parts answers carry them. */
export function withOffers(product: string | null, parts: PartEntry[]): PartEntry[] {
  const offered = new Map<string, Set<string>>();
  for (const s of (product === null ? Object.values(answers) : [answers[product]]).flatMap((a) => a?.suppliers.suppliers ?? [])) {
    for (const site of s.sites) {
      for (const o of site.offers) {
        const key = `${site.plm}|${o.partId}`;
        offered.set(key, (offered.get(key) ?? new Set()).add(site.location ? `${s.name}, ${site.location}` : s.name));
      }
    }
  }
  return parts.map((p) => {
    const names = isPart(p) ? offered.get(`${p.plm}|${p.id}`) : undefined;
    if (!isPart(p) || !names) return p;
    return { ...p, suppliers: [...(p.suppliers ?? []), ...[...names].sort().map((name) => ({ name, role: 'offered' as const }))] };
  });
}

/** The offer row `id` of a site's offer table, in any product. */
export const offerById = (plm: string, id: string): SupplierOffer | undefined =>
  Object.keys(answers).flatMap(offersOf).find((o) => o.plm === plm && o.offer.id === id)?.offer;

const released = new Map(Object.keys(answers).flatMap(offersOf).map((o) => [`${o.plm}|${o.offer.id}`, { ...o.offer }]));
/** The officer's reset: every offer back to its released lead time and preference. */
export function resetOffers() {
  for (const o of Object.keys(answers).flatMap(offersOf)) Object.assign(o.offer, released.get(`${o.plm}|${o.offer.id}`));
}

/** A part whose preferred offers at one site disagree on the lead time, with the longer offer's key: conflictingLeadTime. */
export interface Conflict extends LeadTimeConflict {
  longer: string;
}

export function conflictsOf(product: string): Conflict[] {
  const byPart = new Map<string, ReturnType<typeof offersOf>>();
  for (const o of offersOf(product).filter((x) => x.offer.preferred)) {
    const k = `${o.plm}|${o.offer.partId}`;
    byPart.set(k, [...(byPart.get(k) ?? []), o]);
  }
  return [...byPart.values()].filter((os) => os.length > 1 && new Set(os.map((o) => o.offer.leadTimeDays)).size > 1).map((os) => {
    const sorted = [...os].sort((a, b) => a.offer.leadTimeDays - b.offer.leadTimeDays);
    return {
      plm: os[0].plm, id: os[0].offer.partId, name: os[0].offer.partName, longer: sorted[sorted.length - 1].offer.id,
      message: `preferred offers disagree on the lead time: ${sorted.map((o) => `${o.offer.leadTimeDays} days from ${o.supplier}`).join(', ')}`,
    };
  });
}

/** The owl:sameAs pairs users confirmed, `<a> <b>` in both directions, as the links graph holds them. */
const sameAs = new Set<string>();

/** POST /core/equivalences: every ordered pair of the parts; the number of triples it added. */
export function confirmEquivalence(iris: string[]): number {
  const before = sameAs.size;
  for (const a of iris) for (const b of iris) if (a !== b) sameAs.add(`${a} ${b}`);
  return sameAs.size - before;
}

/** The officer's reset: the released links graph holds no equivalence. */
export function resetEquivalences() {
  sameAs.clear();
}

export function equivalentsOf(product: string | null): Pick<EquivalentsResponse, 'product' | 'groups'> {
  const answer = answers[product ?? '']?.equivalents ?? { product: product ?? '', groups: [] };
  const groups = answer.groups.map((g) => {
    const members = g.members.map((m) => ({ ...m, iri: partIri(m.plm, m.id) }));
    const confirmed = members.every((a) => members.every((b) => a === b || sameAs.has(`${a.iri} ${b.iri}`)));
    return { ...g, members, confirmed, stocking: { ...g.stocking, stockLines: confirmed ? 1 : g.stocking.partNumbers } };
  });
  return { ...answer, groups };
}

export function suppliersOf(product: string | null): Suppliers {
  const a = answers[product ?? ''];
  if (!a) return { product: product ?? '', suppliers: [], singleSource: [], conflicts: [] };
  return { ...a.suppliers, conflicts: conflictsOf(a.suppliers.product).map((c) => ({ plm: c.plm, id: c.id, name: c.name, message: c.message })) };
}
