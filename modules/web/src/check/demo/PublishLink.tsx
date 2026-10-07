// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useState } from 'react';
import { postJson } from '../../api/client';
import type { AppData } from '../../api/store';
import type { CandidateMate, Feature, Interface, PublishLinkRequest, PublishLinkResult } from '../../api/types';
import { CORE, cssColour, plmCode } from '../../ui/plm';
import { featureIri } from '../evidence/turtle';
import { featureById, type Finding } from '../violations';
import { t } from '../../i18n';

const IRI = /^https?:\/\/\S+$/;
const toError = (e: unknown) => (e instanceof Error ? e : new Error(String(e)));

interface Props {
  data: AppData;
  itf: Interface;
  finding: Finding;
  /** Runs the rules again, which also reads the change feed again: the link is in the graph when the response arrives. */
  onPublished: () => void;
}

/**
 * The orphan feature's "Publish link" action, for programme-cleared and the officer: the core service writes the
 * link on request and answers once it is in the graph. The mate is pre-filled from the violation's first candidate.
 */
export function PublishLink({ data, itf, finding, onPublished }: Props) {
  const orphan = featureById(itf, finding.features[0]);
  if (!orphan) return null;
  // The service reports the candidates under the violation's `detail`; fixtures at the top level.
  const violation = finding.violations[0];
  const candidate = (violation.candidateMates ?? (violation.detail?.candidateMates as CandidateMate[] | undefined))?.[0] ?? null;
  return <PublishForm key={orphan.id} data={data} orphan={orphan} candidate={candidate} onPublished={onPublished} />;
}

type Step = { kind: 'idle' } | { kind: 'posting' } | { kind: 'done'; result: PublishLinkResult } | { kind: 'error'; error: Error };

interface FormProps {
  data: AppData;
  orphan: Feature;
  candidate: CandidateMate | null;
  onPublished: () => void;
}

const describe = (c: CandidateMate) =>
  [plmCode(c.plm), c.connectorType, c.pinCount === undefined ? null : `${c.pinCount} pins`].filter(Boolean).join(', ');

function PublishForm({ data, orphan, candidate, onPublished }: FormProps) {
  const from = featureIri(orphan);
  // The mate the user typed; until they edit the field, the first candidate the service reported.
  const [typed, setTyped] = useState<string | null>(null);
  const to = typed ?? candidate?.iri ?? '';
  const [step, setStep] = useState<Step>({ kind: 'idle' });
  const busy = step.kind === 'posting' || step.kind === 'done';
  const valid = IRI.test(to.trim());

  const publish = async () => {
    if (!valid || busy) return;
    setStep({ kind: 'posting' });
    const body: PublishLinkRequest = { from, to: to.trim() };
    try {
      const result = await postJson<PublishLinkResult>(data.config, '/core/links', data.profile, body);
      setStep({ kind: 'done', result });
      onPublished();
    } catch (e) {
      setStep({ kind: 'error', error: toError(e) });
    }
  };

  return (
    <form
      className="publish"
      style={{ borderLeftColor: cssColour(CORE) }}
      aria-label="Publish link"
      onSubmit={(e) => {
        e.preventDefault();
        void publish();
      }}
    >
      <div className="release-head">
        <span className="eyebrow">{t('check.publish-link.publishLinkAtelier')}</span>
        <span className="kind-tag">{t('check.publish-link.request')}</span>
      </div>
      <p className="publish-from">
        <i className="swatch" style={{ background: cssColour(orphan.plm) }} />
        {plmCode(orphan.plm)} {orphan.kind} <span className="mono">{orphan.id}</span> <span className="mono quiet">{t('check.publish-link.atelierMateswith')}</span>
      </p>
      <label className="release-field publish-field">
        <span className="release-label">{t('check.publish-link.mate')}</span>
        <input className="release-input mono" value={to} onChange={(e) => setTyped(e.target.value)} disabled={busy} spellCheck={false} aria-invalid={!valid} />
      </label>
      <p className="release-note quiet">
        {candidate ? (
          <>
            Pre-filled with <span className="mono">{candidate.id}</span> ({describe(candidate)}): an unmated {orphan.kind} of{' '}
            {candidate.part ? <span className="mono">{candidate.part}</span> : 'the other part'} at this position.{' '}
          </>
        ) : (
          <>No unmated {orphan.kind} of the other part sits at this position for your profile; paste the mate&apos;s IRI. </>
        )}
        A mating link is Atelier&apos;s own fact: <span className="mono">{t('check.publish-link.postCoreLinks')}</span> on the core service appends the link and its inverse to the links
        graph with one Graph Store POST, then emits <span className="mono">{t('check.publish-link.interfaceLinkAdded')}</span> for subscribers.
      </p>
      <div className="release-actions">
        <button type="submit" className="btn btn-ink" disabled={busy || !valid}>
          {step.kind === 'posting' ? 'Publishing...' : 'Publish'}
        </button>
        {step.kind === 'done' ? (
          <span className="release-done">
            {t('check.publish-link.atelierWroteTheLinkLinksGraph')} {step.result.triples.links} triples, event <span className="mono">{t('check.publish-link.interfaceLinkAdded')}</span> emitted; rules running.
          </span>
        ) : null}
        {step.kind === 'error' ? <span className="release-error">{step.error.message}</span> : null}
      </div>
    </form>
  );
}
