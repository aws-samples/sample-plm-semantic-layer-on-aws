// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { RuleFailure } from '../api/types';
import { withRoot } from '../subtree/rootHash';
import { toHash } from '../ui/useHashRoute';
import { t } from '../i18n';

interface Row {
  product: string;
  name: string;
  failures: RuleFailure[];
}

interface Props {
  rows: Row[];
  onOpenRecord: (productKey: string, hash: string) => void;
}

/** Where a failing record opens on the Interface check screen: the interface, the part as the subtree root, or the product. */
export function recordHash(f: RuleFailure): string {
  if (f.kind === 'interface') return toHash({ screen: 'check', interfaceId: f.id });
  if (f.kind === 'part') return withRoot('#/check', f.id);
  return '#/check';
}

/** Per product, the records failing the rule for the profile, each a link to it on the Interface check screen. */
export function RuleFailing({ rows, onOpenRecord }: Props) {
  if (rows.length === 0) return <p className="quiet rules-none">{t('rules.rule-failing.noRecordFailsThisRuleFor')}</p>;
  return (
    <ul className="rules-rows">
      {rows.map((row) => (
        <li key={row.product} className={`rules-row${row.failures.length ? '' : ' is-zero'}`} data-product={row.product}>
          <span className="rules-row-name">{row.name}</span>
          <span className="rules-row-n">{row.failures.length}</span>
          <span className="rules-ids">
            {row.failures.map((f) => {
              const hash = recordHash(f);
              return (
                <a
                  key={`${f.kind}|${f.id}`}
                  className="mono rules-id is-link"
                  href={hash}
                  title={t(`rules.rule-failing.open-${f.kind}`)}
                  onClick={(e) => {
                    e.preventDefault();
                    onOpenRecord(row.product, hash);
                  }}
                >
                  {f.plm ? <b>{f.plm.toUpperCase()}</b> : null}
                  {f.id}
                </a>
              );
            })}
          </span>
        </li>
      ))}
    </ul>
  );
}

export type { Row as FailingRow };
