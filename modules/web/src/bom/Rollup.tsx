// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { BomRollup } from '../api/types';
import { cssColour, plmCode } from '../ui/plm';
import { count, mass } from './format';
import { t } from '../i18n';

/** Part occurrences and mass of each site, then of the product; the notes say what the figures leave out. */
/** On a subtree the cells count the subtree's items: a site's share of it, not its kit, and the subtree, not the product. */
export function Rollup({ sites, total, subtree }: { sites: BomRollup[]; total: BomRollup; subtree: boolean }) {
  return (
    <section className="bom-rollup" aria-label={t('bom.rollup.rollUpPerSiteAndIn')}>
      {sites.map((s) => <Cell key={s.plm} r={s} subtree={subtree} />)}
      <Cell r={total} subtree={subtree} />
    </section>
  );
}

function Cell({ r, subtree }: { r: BomRollup; subtree: boolean }) {
  const site = r.plm;
  return (
    <div className={`bom-cell${site ? '' : ' is-total'}`} style={site ? { borderTopColor: cssColour(site) } : undefined}>
      <div className="bom-cell-head">
        {site ? <i className="swatch" style={{ background: cssColour(site) }} /> : null}
        <span className="bom-cell-code">{site ? plmCode(site) : subtree ? t('bom.rollup.subtree') : t('bom.rollup.product')}</span>
        <span className="quiet">{site ? (subtree ? t('bom.rollup.siteItems') : t('bom.rollup.siteKit')) : t('bom.rollup.allSites')}</span>
      </div>
      <div className="bom-cell-figures">
        <span className="figure">
          <span className="figure-n">{count(r.occurrences)}</span>
          <span className="figure-label">{r.occurrences === 1 ? t('bom.rollup.part') : t('bom.rollup.parts')}</span>
        </span>
        <span className="bom-cell-mass">{mass(r.massKg)}</span>
      </div>
      <div className="bom-cell-notes">
        {r.withoutMass > 0 ? (
          <span className="bom-note is-flag">
            {count(r.withoutMass)} {r.withoutMass === 1 ? t('bom.rollup.partWithoutMass') : t('bom.rollup.partsWithoutMass')}
          </span>
        ) : null}
        {r.hiddenOccurrences > 0 ? (
          <span className="bom-note is-redacted">
            {count(r.hiddenOccurrences)} {r.hiddenOccurrences === 1 ? t('bom.rollup.occurrenceHiddenByExportControl') : t('bom.rollup.occurrencesHiddenByExportControl')}
          </span>
        ) : null}
        {r.withoutMass === 0 && r.hiddenOccurrences === 0 ? <span className="bom-note quiet">{t('bom.rollup.everyPartWeighed')}</span> : null}
      </div>
    </div>
  );
}
