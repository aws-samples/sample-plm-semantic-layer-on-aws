// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useState } from 'react';
import { ALL, useCached, type AppData } from '../api/store';
import { isPart, type LeadTimeConflict, type SuppliersResponse } from '../api/types';
import { plmCode } from '../ui/plm';
import { leadTimeOffers } from './demo/offers';
import { OfferRelease } from './demo/OfferRelease';
import { ReleaseButton } from './demo/ReleaseButton';
import { LEAD_TIME } from './demo/usePartOffers';

interface Props {
  data: AppData;
  suppliers: SuppliersResponse;
  onReleased: () => void;
}

/**
 * The parts whose preferred offers disagree on the lead time, each with the site's two releases: align the longer
 * offer with the other, or stop preferring it. The longer offer is the one the part's finding names when the parts
 * answer is in.
 */
export function LeadTimeConflicts({ data, suppliers, onReleased }: Props) {
  const [open, setOpen] = useState<string | null>(null);
  const parts = useCached(data.parts, ALL);
  const named = (c: LeadTimeConflict) => parts.state === 'ready'
    ? parts.data.parts.filter(isPart).find((p) => p.id === c.id && p.plm.toLowerCase() === c.plm.toLowerCase())?.findings?.find((f) => f.rule === LEAD_TIME)?.value?.id
    : undefined;
  const rows = suppliers.conflicts.map((c) => ({ c, offers: leadTimeOffers(suppliers, c.plm, c.id, named(c)) }));
  const offer = rows.flatMap((r) => r.offers).find((o) => o.key === open);
  return (
    <>
      <ul className="purchasing-list is-conflicts">
        {rows.map(({ c, offers }) => (
          <li key={`${c.plm}|${c.id}`}>
            <span><b>{plmCode(c.plm)}</b> {c.name} <span className="mono quiet">{c.id}</span></span>
            <span className="purchasing-native">{c.message}</span>
            {offers.length ? (
              <span className="purchasing-offers">
                {offers.map((o) => (
                  <ReleaseButton key={o.key} data={data} offer={o} open={open === o.key} onToggle={() => setOpen((k) => (k === o.key ? null : o.key))} />
                ))}
              </span>
            ) : null}
          </li>
        ))}
      </ul>
      {offer ? <OfferRelease key={offer.key} data={data} offer={offer} onClose={() => setOpen(null)} onReleased={onReleased} /> : null}
    </>
  );
}
