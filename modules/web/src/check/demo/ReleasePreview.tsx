// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { PreviewResponse, PreviewResult, Rule } from '../../api/types';
import { t } from '../../i18n';
import { plmCode } from '../../ui/plm';
import { RuleChip } from '../RuleChip';
import { RULE_LABEL, STATUS_LABEL } from '../violations';

const chip = (rule: string) => (rule in RULE_LABEL ? <RuleChip rule={rule as Rule} /> : <span className="chip is-finding">{rule}</span>);

/** The results of one subject, by how the cells change them: fixed in green, newly failing in red, still failing quiet. */
function Results({ fixed, stillFailing, newlyFailing }: { fixed: PreviewResult[]; stillFailing: PreviewResult[]; newlyFailing: PreviewResult[] }) {
  const line = (r: PreviewResult, i: number, kind: 'fixed' | 'still' | 'newly') => (
    <p key={`${kind}-${i}`} className="release-note">
      <span className={kind === 'fixed' ? 'status is-pass' : kind === 'newly' ? 'status is-fail' : 'quiet'}>
        {t(`check.release-preview.${kind}`)}
      </span>{' '}
      {chip(r.rule)} <span className="finding-message">{r.message}</span>
    </p>
  );
  return (
    <>
      {fixed.map((r, i) => line(r, i, 'fixed'))}
      {newlyFailing.map((r, i) => line(r, i, 'newly'))}
      {stillFailing.map((r, i) => line(r, i, 'still'))}
    </>
  );
}

/** What the rules would say if the form's cells were released: per interface, part and product, the results the cells change. */
export function ReleasePreview({ answer }: { answer: PreviewResponse }) {
  const { tally } = answer;
  const nothing = answer.interfaces.length + answer.parts.length + answer.products.length === 0;
  return (
    <div className="release-batch" role="status">
      <p className="release-where">
        {t('check.release-preview.ifReleased')} <b>{tally.fixed}</b> {t('check.release-preview.fixed')},{' '}
        <b>{tally.newlyFailing}</b> {t('check.release-preview.newly')}, <b>{tally.stillFailing}</b> {t('check.release-preview.still')}.{' '}
        <span className="quiet">{t('check.release-preview.nothingWritten')}</span>
      </p>
      {nothing ? <p className="release-note quiet">{t('check.release-preview.noRuleChanges')}</p> : null}
      {answer.interfaces.map((i) => (
        <div key={`${i.product}/${i.id}`}>
          <p className="release-where mono">
            {i.id} · {i.before ? STATUS_LABEL[i.before] : '-'} {t('check.release-correction.to')} {i.after ? STATUS_LABEL[i.after] : '-'}
          </p>
          <Results fixed={i.fixed} stillFailing={i.stillFailing} newlyFailing={i.newlyFailing} />
        </div>
      ))}
      {answer.parts.map((p) => (
        <div key={`${p.plm}/${p.id}`}>
          <p className="release-where mono">{plmCode(p.plm)} {p.id}</p>
          <Results fixed={p.fixed} stillFailing={p.stillFailing} newlyFailing={p.newlyFailing} />
        </div>
      ))}
      {answer.products.map((p) => (
        <div key={p.key}>
          <p className="release-where mono">{p.key}</p>
          <Results fixed={p.fixed} stillFailing={p.stillFailing} newlyFailing={p.newlyFailing} />
        </div>
      ))}
    </div>
  );
}
