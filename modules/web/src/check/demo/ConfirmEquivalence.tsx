// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useState } from 'react';
import { postJson } from '../../api/client';
import { mayPublishLink } from '../../api/profile';
import type { AppData } from '../../api/store';
import type { ConfirmEquivalenceRequest, ConfirmEquivalenceResult, EquivalentGroup } from '../../api/types';
import { t } from '../../i18n';

type Step = { kind: 'idle' } | { kind: 'posting' } | { kind: 'done'; result: ConfirmEquivalenceResult } | { kind: 'error'; message: string };

interface Props {
  data: AppData;
  group: EquivalentGroup;
  /** Reads the equivalents and the change feed again: the links graph holds the confirmation when the response arrives. */
  onConfirmed: () => void;
}

/**
 * The layer proposes that a group's part numbers are one item; a user decides. "Confirm equivalence", for the integration
 * role and the officer, asks the core service to write owl:sameAs between every two members in the links graph, as a
 * mating link is written. A confirmed group says so; anyone else reads who may confirm it.
 */
export function ConfirmEquivalence({ data, group, onConfirmed }: Props) {
  const [step, setStep] = useState<Step>({ kind: 'idle' });
  if (group.confirmed) {
    return (
      <span className="equivalent-state is-confirmed">
        <span className="status is-pass">{t('check.confirm-equivalence.confirmed')}</span>
        <span className="quiet">{t('check.confirm-equivalence.confirmedNote')}</span>
      </span>
    );
  }
  if (!mayPublishLink(data.profile)) {
    return (
      <span className="equivalent-state">
        <span className="kind-tag">{t('check.confirm-equivalence.proposal')}</span>
        <span className="quiet">{t('check.confirm-equivalence.whoConfirms')}</span>
      </span>
    );
  }
  const send = async () => {
    setStep({ kind: 'posting' });
    const body: ConfirmEquivalenceRequest = { parts: group.members.map((m) => m.iri) };
    try {
      const result = await postJson<ConfirmEquivalenceResult>(data.config, '/core/equivalences', data.profile, body);
      setStep({ kind: 'done', result });
      onConfirmed();
    } catch (e) {
      setStep({ kind: 'error', message: e instanceof Error ? e.message : String(e) });
    }
  };
  const busy = step.kind === 'posting' || step.kind === 'done';
  const pairs = group.members.length * (group.members.length - 1);
  return (
    <span className="equivalent-state">
      <span className="kind-tag">{t('check.confirm-equivalence.proposal')}</span>
      <button type="button" className="btn btn-release is-primary" disabled={busy} onClick={() => void send()}>
        {step.kind === 'posting' ? t('check.confirm-equivalence.confirming') : t('check.confirm-equivalence.confirm')}
      </button>
      {step.kind === 'idle' ? (
        <span className="release-caption">
          <span className="mono">{t('check.confirm-equivalence.postCoreEquivalences')}</span>: {pairs} <span className="mono">{t('check.confirm-equivalence.owlSameAs')}</span> {t('check.confirm-equivalence.triplesInTheLinksGraph')}
        </span>
      ) : null}
      {step.kind === 'done' ? (
        <span className="release-done">
          {t('check.confirm-equivalence.written')} {step.result.triples.links} {t('check.confirm-equivalence.triplesEvent')}{' '}
          <span className="mono">{t('check.confirm-equivalence.equivalenceConfirmed')}</span>
        </span>
      ) : null}
      {step.kind === 'error' ? <span className="release-error">{step.message}</span> : null}
    </span>
  );
}
