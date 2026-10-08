// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useCached, type AppData } from '../api/store';
import type { ExternalReference, ReferenceStatus, ReferencesResponse } from '../api/types';
import type { Resource } from '../api/useResource';
import { t } from '../i18n';
import { plmCode } from '../ui/plm';
import type { Offer } from './demo/offer';
import { ReleaseButton } from './demo/ReleaseButton';
import { REFERENCE_LABEL } from './violations';

/** A failing reference wears the rule chip; a hidden target the neutral chip; a reference the shapes passed, a quiet label. */
export function ReferenceChip({ status }: { status: ReferenceStatus }) {
  if (status === 'ok') return <span className="mono quiet">{REFERENCE_LABEL.ok}</span>;
  return <span className={`chip ${status === 'not-evaluable' ? 'is-not-evaluable' : `rule-${status}`}`}>{REFERENCE_LABEL[status]}</span>;
}

/** The references of one product, read once per product, profile and run. */
export const useReferences = (data: AppData, product: string | null): Resource<ReferencesResponse> =>
  useCached(data.references, product);

export const failing = (r: ExternalReference) => r.status === 'danglingReference' || r.status === 'staleRevision';

/** The releases the referring site may make from the list: which is open, and how to open one. */
export interface ReferenceReleases {
  data: AppData;
  offerOf: (r: ExternalReference) => Offer | null;
  open: string | null;
  onToggle: (key: string) => void;
}

function Line({ r, releases }: { r: ExternalReference; releases?: ReferenceReleases }) {
  const offer = releases && failing(r) ? releases.offerOf(r) : null;
  const revision = r.expectedRevision
    ? `${t('check.part-references.expects')} ${r.expectedRevision}${r.currentRevision ? `, ${t('check.part-references.at')} ${r.currentRevision}` : ''}`
    : null;
  return (
    <li className="part-reference">
      <ReferenceChip status={r.status} />
      <span className="mono">{r.remoteUrn}</span>
      {r.target ? <span className="quiet"> {plmCode(r.target.plm)} {r.target.id}</span> : null}
      {revision ? <span className="quiet"> · {revision}</span> : null}
      {r.lifecycleState ? <span className="mono quiet"> {r.lifecycleState}</span> : null}
      {r.message ? <span className="finding-message"> {r.message}</span> : null}
      {offer && releases ? (
        <ReleaseButton data={releases.data} offer={offer} open={releases.open === offer.key} onToggle={() => releases.onToggle(offer.key)} />
      ) : null}
    </li>
  );
}

/**
 * The external references a part makes, each with the layer's verdict: the site names another site's part by URN
 * and only the layer sees both ends. Nothing when the part makes none or the answer is not in yet.
 */
export function PartReferences({ data, product, part, releases }: { data: AppData; product: string | null; part: string; releases?: ReferenceReleases }) {
  const answer = useReferences(data, product);
  if (answer.state !== 'ready') return null;
  const mine = answer.data.references.filter((r) => r.part === part);
  if (mine.length === 0) return null;
  return (
    <span className="part-references">
      <span className="quiet">{t('check.part-references.title')}</span>
      <ul>{mine.map((r) => <Line key={`${r.plm}/${r.id}`} r={r} releases={releases} />)}</ul>
    </span>
  );
}

/** The product's references beside the interface tally: how many, and how many fail a reference rule. */
export function ReferencesSummary({ data, product }: { data: AppData; product: string | null }) {
  const answer = useReferences(data, product);
  if (answer.state !== 'ready' || answer.data.references.length === 0) return null;
  const refs = answer.data.references;
  const failed = refs.filter(failing).length;
  const title = Object.entries(answer.data.counts).map(([status, n]) => `${REFERENCE_LABEL[status as ReferenceStatus] ?? status} ×${n}`).join('\n');
  return (
    <span className="references-summary" title={title}>
      <b>{refs.length}</b> {t('check.part-references.summary')}
      {failed ? <>{' · '}<b className="references-failed">{failed}</b> {t('check.part-references.failing')}</> : null}
    </span>
  );
}
