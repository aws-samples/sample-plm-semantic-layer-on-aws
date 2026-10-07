// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useState } from 'react';
import { mayPublishLink } from '../api/profile';
import { ALL, useCached, type AppData } from '../api/store';
import type { Envelope, Feature, FeatureKind, Interface } from '../api/types';
import { isPart } from '../api/types';
import type { Resource } from '../api/useResource';
import { CORE, plmCode } from '../ui/plm';
import { referenceOffer } from './demo/offers';
import { OfferRelease } from './demo/OfferRelease';
import { PublishCad } from './demo/PublishCad';
import { PublishLink } from './demo/PublishLink';
import { rereadSites } from './demo/reread';
import { usePartOffers } from './demo/usePartOffers';
import { FindingRecords } from './FindingRecords';
import { cadKey, PartLine } from './PartLine';
import { failing, useReferences, type ReferenceReleases } from './PartReferences';
import { RedactedPairs } from './RedactedPairs';
import { RuleChip } from './RuleChip';
import { features, findings, localName, STATUS_LABEL } from './violations';
import { t } from '../i18n';

interface Props {
  itf: Interface;
  detail: Resource<Envelope>;
  data: AppData;
  /** Runs the rules again after a demo control wrote to a PLM or the graph. */
  onRerun: () => void;
}

interface ProvenanceProps {
  itf: Interface;
  detail: Resource<Envelope>;
}

const LINK_TERMS = 'atelier:betweenPart, atelier:declaresFeature, atelier:matesWith, atelier:toleranceMm, atelier:cadFile';
const TAG_TERMS = 'atelier:jurisdiction, atelier:releasableTo, atelier:taggedBy';
const KINDS: FeatureKind[] = ['plug', 'fastener', 'coupling'];

export function InterfaceDetail({ itf, detail, data, onRerun }: Props) {
  const found = findings(itf);
  const product = itf.product ?? data.product?.key ?? null;
  /** The form open below the parts: a release's key, or the CAD publication of a part. */
  const [open, setOpen] = useState<string | null>(null);
  const toggle = (key: string) => setOpen((o) => (o === key ? null : key));
  const offersOf = usePartOffers(data);
  const listed = useCached(data.parts, ALL);
  const refs = useReferences(data, product);
  const visible = listed.state === 'ready' ? listed.data.parts.filter(isPart) : [];
  const references: ReferenceReleases = { data, offerOf: (r) => referenceOffer(r, visible), open, onToggle: toggle };
  const shown = itf.parts.filter(isPart);
  const offers = new Map(shown.map((p) => [p.id, (p.findings ?? []).flatMap((f) => offersOf(p, f))]));
  const refOffers = refs.state === 'ready' ? refs.data.references.filter((r) => failing(r) && shown.some((p) => p.id === r.part)).map(references.offerOf) : [];
  const offer = [...[...offers.values()].flat(), ...refOffers].find((o) => o?.key === open);
  const publishingPart = shown.find((p) => open === cadKey(p.id));
  // The file index names the file, or a site has released a value: the sites are read again and the rules run again.
  const released = () => {
    rereadSites(data);
    onRerun();
  };
  return (
    <article className="detail" aria-label={`Interface ${itf.id}`}>
      <header className="detail-head">
        <span className="mono detail-id">{itf.id}</span>
        <span className="detail-label">{itf.label}</span>
        <span className={`status is-${itf.status}`}>{STATUS_LABEL[itf.status]}</span>
      </header>
      <div className="detail-parts">
        {itf.parts.map((p, i) =>
          isPart(p) ? (
            <PartLine key={p.id} data={data} part={p} nameEn={visible.find((v) => v.id === p.id && v.plm === p.plm)?.nameEn} product={product} offers={offers.get(p.id) ?? []} references={references} open={open} onToggle={toggle} />
          ) : (
            <span key={i} className="is-redacted"><b>{plmCode(p.plm)}</b> a part not visible to your profile</span>
          ),
        )}
      </div>
      {publishingPart ? (
        <PublishCad key={publishingPart.id} data={data} part={publishingPart} onClose={() => setOpen(null)} onPublished={released} />
      ) : null}
      {offer ? <OfferRelease key={offer.key} data={data} offer={offer} onClose={() => setOpen(null)} onReleased={released} /> : null}
      {itf.status === 'not-evaluable' ? <RedactedPairs itf={itf} /> : null}
      {itf.status === 'pass' ? <p className="quiet">{passText(features(itf), itf.toleranceMm)}</p> : null}
      {found.map((f) => (
        <section key={f.key} className="finding">
          <div className="finding-head">
            <RuleChip rule={f.rule} />
            <span className="mono shape" title={f.shape}>{localName(f.shape)}</span>
            {f.violations.length > 1 ? <span className="quiet">{f.violations.length} results</span> : null}
          </div>
          <p className="message">{f.violations[0].message}</p>
          <FindingRecords data={data} itf={itf} finding={f} onReleased={released} />
          {f.rule === 'orphan' && mayPublishLink(data.profile) ? <PublishLink data={data} itf={itf} finding={f} onPublished={onRerun} /> : null}
        </section>
      ))}
      <Provenance itf={itf} detail={detail} />
    </article>
  );
}

function passText(fs: Feature[], tol: number | null): string {
  const pairs = KINDS.flatMap((k) => {
    const n = fs.filter((f) => f.kind === k).length / 2;
    return n ? [`${n} ${k} ${n === 1 ? 'pair' : 'pairs'}`] : [];
  });
  return `All ${fs.length} features mate within ${tol ?? '-'} mm and agree on every compared property (${pairs.join(', ')}).`;
}

function Provenance({ itf, detail }: ProvenanceProps) {
  if (detail.state === 'error') return <p className="quiet">{t('check.interface-detail.provenanceUnavailable')} {detail.error.message}</p>;
  if (!detail.data) return <p className="quiet">{t('check.interface-detail.loadingProvenance')}</p>;
  const calls = detail.data.provenance.calls;
  const plms = [...new Set(itf.features.map((f) => f.plm.toLowerCase()))];
  const core = calls.find((c) => c.endpoint === `ontop-${CORE}`);
  return (
    <section className="provenance">
      <h3 className="eyebrow">{t('check.interface-detail.provenance')}</h3>
      <ul>
        <li>
          <span className="mono">{TAG_TERMS}</span> from <span className="mono">{t('check.interface-detail.ontop')}{CORE}</span>
          {core ? <>, {core.kind} graph, {core.triples} triples, read first</> : ', not in this response'}
        </li>
        {plms.map((plm) => {
          const c = calls.find((x) => x.endpoint === `ontop-${plm}`);
          return (
            <li key={plm}>
              <b>{plm.toUpperCase()}</b> feature and part records from <span className="mono">{c?.endpoint ?? `ontop-${plm}`}</span>
              {c ? <>, {c.kind} graph, {c.triples} triples</> : ', not in this response'}
            </li>
          );
        })}
        {calls.filter((c) => c.kind === 'materialized').map((c) => (
          <li key={c.endpoint}>
            <span className="mono">{LINK_TERMS}</span> from <span className="mono">{c.endpoint}</span>, materialized graph, {c.triples} triples
          </li>
        ))}
      </ul>
    </section>
  );
}
