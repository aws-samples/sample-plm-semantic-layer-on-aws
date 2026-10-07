// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { t } from '../i18n';
import type { Seeded } from './seeded';

/** The products whose files seed a defect of the rule: the number of seeded entries and the records they name. */
export function RuleSeeded({ seeded }: { seeded: Seeded[] }) {
  if (seeded.length === 0) return <p className="quiet rules-none">{t('rules.rule-seeded.noProductFileSeedsThisRule')}</p>;
  return (
    <ul className="rules-rows">
      {seeded.map((s) => (
        <li key={s.product} className="rules-row" data-product={s.product}>
          <span className="rules-row-name">{s.name}</span>
          <span className="rules-row-n">{s.ids.length}</span>
          <span className="rules-ids">
            {[...new Set(s.ids)].map((id) => <span key={id} className="mono rules-id">{id}</span>)}
          </span>
        </li>
      ))}
    </ul>
  );
}
