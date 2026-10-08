// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useCallback } from 'react';
import { ALL, useCached, type AppData } from '../../api/store';
import { isPart, LIFECYCLE_CONFLICT, type Part, type PartFinding } from '../../api/types';
import type { Offer } from './offer';
import { leadTimeOffers, lifecycleOffers } from './offers';

export const LEAD_TIME = 'conflictingLeadTime';

/**
 * The releases a part finding offers: a lifecycleConflict's dependency is looked up in the parts answer, a
 * conflictingLeadTime's offers in the suppliers answer. None until the answer it needs is in.
 */
export function usePartOffers(data: AppData): (part: Part, finding: PartFinding) => Offer[] {
  const parts = useCached(data.parts, ALL);
  const suppliers = useCached(data.suppliers, data.product ? ALL : null);
  const listed = parts.state === 'ready' ? parts.data.parts : null;
  const offers = suppliers.state === 'ready' ? suppliers.data : null;
  return useCallback((part: Part, finding: PartFinding) => {
    if (finding.rule === LIFECYCLE_CONFLICT && listed) return lifecycleOffers(part, finding, listed.filter(isPart));
    if (finding.rule === LEAD_TIME && offers) return leadTimeOffers(offers, part.plm, part.id, finding.value?.id);
    return [];
  }, [listed, offers]);
}
