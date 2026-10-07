// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The releases each finding offers, by the site that owns the record: one per side of a mismatched pair, the two ways
// out of a lifecycle conflict, the two of a lead-time conflict, the referring site's correction of a reference.
import type { ExternalReference, Feature, Interface, Part, PartFinding, SuppliersResponse, SupplierOffer } from '../../api/types';
import { t } from '../../i18n';
import { plmCode } from '../../ui/plm';
import type { Offer } from './offer';
import {
  danglingProposal, leadTimeProposal, lifecycleProposal, massProposal, massScaleProposal, matchProposal, positionProposal, preferredProposal,
  staleProposal, unitProposal, type Axis, type MatchRule,
} from './proposals';

const lc = (s: string) => s.toLowerCase();

export const positionOffer = (itf: Interface, f: Feature, mate: Feature, axis: Axis): Offer => ({
  key: `position|${f.plm}|${f.id}|${axis}`, plm: lc(f.plm), plan: (c) => positionProposal(c, f, mate, axis, itf.id),
});

/** `quantity` is the violation's ontology local name: positionX, diameter. */
export const unitOffer = (itf: Interface, f: Feature, quantity: string): Offer => ({
  key: `unit|${f.plm}|${f.id}|${quantity}`, plm: lc(f.plm), caption: t('check.offers.stateTheUnitItStores'),
  plan: (c) => unitProposal(c, f, quantity, itf.id),
});

/** `f`'s site takes the values of `mate`'s record. */
export const matchOffer = (itf: Interface, rule: MatchRule, f: Feature, mate: Feature): Offer => ({
  key: `${rule}|${f.plm}|${f.id}`, plm: lc(f.plm), caption: `${t('check.offers.matchThe')} ${plmCode(mate.plm)} ${t('check.offers.record')}`,
  plan: (c) => matchProposal(c, rule, f, mate, itf.id),
});

/** The dependency a lifecycleConflict names, as the parts answer has it; its key and site alone when the answer has it not. */
const dependencyOf = (finding: PartFinding, parts: Part[]): Part | null => {
  const v = finding.value;
  if (!v) return null;
  return parts.find((p) => p.id === v.id && lc(p.plm) === lc(v.plm)) ?? { id: v.id, plm: lc(v.plm), name: v.id, cadFile: null, cadUrl: null };
};

/** The dependency's site releases it in its released word, or the dependent item's site blocks the item. */
export function lifecycleOffers(item: Part, finding: PartFinding, parts: Part[]): Offer[] {
  const dep = dependencyOf(finding, parts);
  if (!dep) return [];
  const conflict = `lifecycleConflict ${plmCode(item.plm)} ${item.id} over ${plmCode(dep.plm)} ${dep.id}`;
  return [
    {
      key: `release|${dep.plm}|${dep.id}|${item.id}`, plm: lc(dep.plm),
      caption: `${t('check.offers.release')} ${dep.id}${dep.lifecycle ? `, ${t('check.offers.now')} ${dep.lifecycle}` : ''}`,
      plan: (c) => lifecycleProposal(c, dep, 'RELEASED', conflict),
    },
    {
      key: `block|${item.plm}|${item.id}|${dep.id}`, plm: lc(item.plm),
      caption: `${t('check.offers.block')} ${item.id}${item.lifecycle ? `, ${t('check.offers.now')} ${item.lifecycle}` : ''}`,
      plan: (c) => lifecycleProposal(c, item, 'BLOCKED', conflict),
    },
  ];
}

interface Held {
  offer: SupplierOffer;
  supplier: string;
}

/** The preferred offers a site holds for a part, with the supplier of each. */
export function preferredOffers(s: SuppliersResponse, plm: string, partId: string): Held[] {
  return s.suppliers.flatMap((sup) => sup.sites.filter((site) => lc(site.plm) === lc(plm))
    .flatMap((site) => site.offers.filter((o) => o.partId === partId && o.preferred).map((offer) => ({ offer, supplier: sup.name }))));
}

/**
 * A conflictingLeadTime: the site aligns the longer offer (the finding's value, else the longest) with the other's
 * lead time, or stops preferring it. Nothing when the answer shows fewer than two preferred offers.
 */
export function leadTimeOffers(s: SuppliersResponse, plm: string, partId: string, longerId?: string): Offer[] {
  const held = preferredOffers(s, plm, partId);
  if (held.length < 2) return [];
  const byDays = [...held].sort((a, b) => b.offer.leadTimeDays - a.offer.leadTimeDays);
  const longer = held.find((h) => h.offer.id === longerId) ?? byDays[0];
  const other = byDays.filter((h) => h !== longer).at(-1)!;
  const site = lc(plm);
  return [
    {
      key: `lead|${site}|${longer.offer.id}`, plm: site,
      caption: `${t('check.offers.align')} ${longer.supplier} (${longer.offer.leadTimeDays} ${t('check.purchasing.days')}) ${t('check.offers.with')} ${other.supplier} (${other.offer.leadTimeDays} ${t('check.purchasing.days')})`,
      plan: (c) => leadTimeProposal(c, site, longer.offer, other.offer),
    },
    {
      key: `preferred|${site}|${longer.offer.id}`, plm: site,
      caption: `${t('check.offers.stopPreferring')} ${longer.supplier}`,
      plan: (c) => preferredProposal(c, site, longer.offer),
    },
  ];
}

/** The referring site corrects its own reference row. */
export const referenceOffer = (ref: ExternalReference, parts: Part[]): Offer => ({
  key: `ref|${ref.plm}|${ref.id}`, plm: lc(ref.plm),
  caption: ref.status === 'staleRevision' && ref.currentRevision !== undefined
    ? `${t('check.offers.expectRevision')} ${ref.currentRevision}` : t('check.offers.correctTheUrn'),
  plan: (c) => (ref.status === 'staleRevision' ? staleProposal(c, ref) : danglingProposal(c, ref, parts)),
});

export const massOffer = (part: Part, product: string): Offer => ({
  key: `mass|${part.plm}|${part.id}`, plm: lc(part.plm), plan: (c) => massProposal(c, part, product),
});

/** One release for every flagged part of the site. */
export const massScaleOffer = (parts: Part[], product: string): Offer => ({
  key: `massScale|${parts[0]?.plm}|${parts.length}`, plm: lc(parts[0]?.plm ?? ''), plan: (c) => massScaleProposal(c, parts, product),
});
