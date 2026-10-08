// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { Interface } from '../api/types';
import { plmCode } from '../ui/plm';
import { KindGlyph } from '../viewer/KindGlyph';
import { features } from './violations';
import { t } from '../i18n';

/** The visible side of an interface the profile cannot evaluate, each feature facing its hidden mate. */
export function RedactedPairs({ itf }: { itf: Interface }) {
  const shown = features(itf);
  const hidden = itf.features.filter((f) => f.redacted);
  const hiddenPlm = [...new Set(hidden.map((f) => plmCode(f.plm)))].join(', ');
  return (
    <section className="finding" aria-label="Redacted side">
      <p className="message is-redacted">
        {t('check.redacted-pairs.the')} {hiddenPlm} side of this interface is hidden for your profile by export control. Its rows never left
        the {hiddenPlm} database, so no rule could compare the two sides.
      </p>
      {shown.length === 0 ? (
        <p className="quiet">{t('check.redacted-pairs.all')} {hidden.length} features are redacted for your profile.</p>
      ) : (
        <table className="records">
          <thead>
            <tr>
              <th scope="col" className="axes-note">{t('check.redacted-pairs.feature')}</th>
              <th scope="col">{plmCode(shown[0].plm)} PLM</th>
              <th scope="col" className="is-redacted-head">{hiddenPlm} PLM</th>
            </tr>
          </thead>
          <tbody>
            {shown.map((f) => (
              <tr key={f.id}>
                <th scope="row"><KindGlyph kind={f.kind} status="not-evaluable" />{f.kind}</th>
                <td className="mono">{f.id}</td>
                <td className="is-redacted">{t('check.redacted-pairs.notVisibleToYourProfile')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
