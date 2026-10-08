// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { mayCorrect } from '../../api/profile';
import type { AppData } from '../../api/store';
import { t } from '../../i18n';
import { plmCode } from '../../ui/plm';
import type { Offer } from './offer';

interface Props {
  data: AppData;
  offer: Offer;
  open: boolean;
  /** The record to correct, when the finding tells it: an ink-filled button; the others stay secondary. */
  primary?: boolean;
  onToggle: () => void;
}

/** "Release correction in <PLM> PLM" with what it does underneath; to other profiles, who may release it. */
export function ReleaseButton({ data, offer, open, primary, onToggle }: Props) {
  if (!mayCorrect(data.profile, offer.plm)) {
    return <span className="quiet correction-who">{plmCode(offer.plm)} {t('check.release-button.engineerOrOfficerOnly')}</span>;
  }
  return (
    <span className="release-offer">
      <button type="button" className={`btn btn-small btn-release${primary ? ' is-primary' : ''}`} aria-expanded={open} onClick={onToggle}>
        {t('check.correctable-records.releaseCorrectionIn')} {plmCode(offer.plm)} PLM
      </button>
      {offer.caption ? <span className="release-caption">{offer.caption}</span> : null}
    </span>
  );
}
