// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { mayPublishCad } from '../api/profile';
import type { AppData } from '../api/store';
import type { Part } from '../api/types';
import { builtBy } from '../api/types';
import { t } from '../i18n';
import { ownerLabel, plmCode, SUPPLIER_CSS } from '../ui/plm';
import { CadUnpublished } from './CadUnpublished';
import type { Offer } from './demo/offer';
import { ReleaseButton } from './demo/ReleaseButton';
import { FindingLine } from './Findings';
import { EnglishName } from './EnglishName';
import { PartAttributes } from './PartAttributes';
import { PartReferences, type ReferenceReleases } from './PartReferences';
import { isReferenceRule } from './violations';

interface Props {
  data: AppData;
  part: Part;
  /** The part's English name from the parts answer: the interface answer carries the native name only. */
  nameEn?: string;
  product: string | null;
  /** The releases the part's findings offer, by finding rule. */
  offers: Offer[];
  references: ReferenceReleases;
  /** The open form below the parts: a release's key, or `cad|<part>` for the CAD publication. */
  open: string | null;
  onToggle: (key: string) => void;
}

export const cadKey = (part: string) => `cad|${part}`;

/** One part of the interface: its tag, attributes, references, supplier and the findings its site still owes, each with its release. */
export function PartLine({ data, part: p, nameEn, product, offers, references, open, onToggle }: Props) {
  const findings = p.findings?.filter((f) => !isReferenceRule(f.rule)) ?? [];
  const publishes = p.cadFile === null && mayPublishCad(data.profile, p.plm);
  return (
    <span>
      <b>{plmCode(p.plm)}</b> {p.name} <span className="mono quiet">{p.id}</span>
      {p.jurisdiction ? (
        <span className="release mono">
          {p.jurisdiction} · {p.releasableTo}
          {p.taggedBy ? <span className="tagged"> · tagged by {plmCode(p.taggedBy)}</span> : null}
        </span>
      ) : (
        <span className="release is-untagged">{t('check.interface-detail.noExportControlTag')}</span>
      )}
      <EnglishName name={p.name} nameEn={nameEn} />
      <PartAttributes part={p} />
      <PartReferences data={data} product={product} part={p.id} releases={references} />
      {p.supplier ? (
        <span className="built-by">
          <i className="swatch" style={{ background: SUPPLIER_CSS }} />
          built by {builtBy(p)}, integrated by {ownerLabel(p.plm)}
        </span>
      ) : null}
      {p.cadFile === null || findings.length ? (
        <span className="cad-line">
          <span className="part-warning">
            {p.cadFile === null ? <CadUnpublished plm={p.plm} /> : null}
            {findings.map((f) => <FindingLine key={f.rule} finding={f} />)}
          </span>
          {publishes ? (
            <button type="button" className="btn btn-small btn-release" aria-expanded={open === cadKey(p.id)} onClick={() => onToggle(cadKey(p.id))}>
              {t('check.interface-detail.publishCadFileFrom')} {plmCode(p.plm)} PLM
            </button>
          ) : null}
          {offers.map((o) => <ReleaseButton key={o.key} data={data} offer={o} open={open === o.key} onToggle={() => onToggle(o.key)} />)}
        </span>
      ) : null}
    </span>
  );
}
