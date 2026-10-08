// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useCached, type AppData } from '../../api/store';
import { t } from '../../i18n';
import { ownerLabel } from '../../ui/plm';
import type { Offer } from './offer';
import { ReleaseCorrection } from './ReleaseCorrection';

interface Props {
  data: AppData;
  offer: Offer;
  onClose: () => void;
  onReleased: () => void;
}

/**
 * The answers name the records and their values, not the tables and columns they sit in: those come from the
 * releasing site's catalogue, read before the form mounts with its proposal.
 */
export function OfferRelease({ data, offer, onClose, onReleased }: Props) {
  const owner = ownerLabel(offer.plm);
  const catalogue = useCached(data.catalogues, offer.plm);
  if (catalogue.state === 'error') {
    return <p className="release-error">{t('check.correctable-records.the')} {owner} {t('check.offer-release.catalogueCouldNotBeRead')} {catalogue.error.message}</p>;
  }
  if (!catalogue.data) return <p className="quiet">{t('check.correctable-records.readingThe')} {owner} {t('check.offer-release.catalogueForTheTables')}</p>;
  const planned = offer.plan(catalogue.data);
  if (!planned.ok) return <p className="release-error">{t('check.offer-release.nothingToRelease')} {owner}: {planned.reason}.</p>;
  return <ReleaseCorrection key={offer.key} data={data} proposal={planned.proposal} onClose={onClose} onReleased={onReleased} />;
}
