// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useState } from 'react';
import { ALL, useCached, type AppData } from '../api/store';
import { isPart, type BomItem, type Part, type Product } from '../api/types';
import { mass } from '../bom/format';
import { t } from '../i18n';
import { plmCode } from '../ui/plm';
import { valueText } from './demo/offer';
import { massOffer, massScaleOffer } from './demo/offers';
import { OfferRelease } from './demo/OfferRelease';
import { unitLabel } from './demo/proposals';
import { ReleaseButton } from './demo/ReleaseButton';
import { rereadSites } from './demo/reread';

const MASS_LIMIT = 'massLimit';
const MASS_SCALE = 'massScale';

/** The extended mass of every item of the tree, by `plm|id`: its unit mass times its occurrences in the product. */
function extendedMasses(item: BomItem, into = new Map<string, number>()): Map<string, number> {
  if (item.redacted) return into;
  if (item.plm && item.extendedMassKg !== undefined && !into.has(`${item.plm}|${item.id}`)) into.set(`${item.plm}|${item.id}`, item.extendedMassKg);
  for (const c of item.children) extendedMasses(c, into);
  return into;
}

interface Props {
  data: AppData;
  onRerun: () => void;
}

/**
 * The findings of the product rules, over every site's parts: massLimit (the sites' parts weigh more than the
 * product's limit) and massScale (a site stores its masses in another unit than its column states), each with the
 * release that answers it. Nothing when the product has none.
 */
export function ProductFindings({ data, onRerun }: Props) {
  const product = data.product;
  const findings = product?.findings ?? [];
  const parts = useCached(data.parts, findings.length ? ALL : null);
  const bom = useCached(data.bom, findings.some((f) => f.rule === MASS_LIMIT) ? ALL : null);
  if (!product || findings.length === 0) return null;
  const visible = parts.state === 'ready' ? parts.data.parts.filter(isPart) : [];
  const extended = bom.state === 'ready' ? extendedMasses(bom.data.root) : new Map<string, number>();
  const released = () => {
    rereadSites(data);
    onRerun();
  };
  return (
    <section className="product-rules" aria-label={t('check.product-findings.productFindings')}>
      <h2 className="eyebrow">{t('check.product-findings.productFindings')}</h2>
      {findings.map((f) => (
        <div key={`${f.rule}|${f.message}`} className="product-rule">
          <p className="product-rule-head">
            <span className="chip is-finding">{f.rule}</span> <span className="finding-message">{f.message}</span>
          </p>
          {f.rule === MASS_LIMIT ? <MassLimit data={data} product={product} parts={visible} extended={extended} onReleased={released} /> : null}
          {f.rule === MASS_SCALE ? <MassScale data={data} product={product} parts={visible} onReleased={released} /> : null}
        </div>
      ))}
    </section>
  );
}

interface MassLimitProps {
  data: AppData;
  product: Product;
  parts: Part[];
  extended: Map<string, number>;
  onReleased: () => void;
}

/** The limit is a design budget: the engineer picks the part to lighten, heaviest first, and enters its mass. */
function MassLimit({ data, product, parts, extended, onReleased }: MassLimitProps) {
  const weight = (p: Part) => extended.get(`${p.plm}|${p.id}`) ?? p.mass?.kg ?? 0;
  const candidates = parts.filter((p) => p.mass && (p.partType ?? 'PART') === 'PART').sort((a, b) => weight(b) - weight(a));
  const [chosen, setChosen] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const part = candidates.find((p) => `${p.plm}|${p.id}` === chosen) ?? candidates[0];
  if (!part) return <p className="quiet">{t('check.product-findings.noVisiblePartStatesAMass')}</p>;
  const offer = massOffer(part, product.name);
  return (
    <div className="product-rule-release">
      <label className="release-field">
        <span className="release-label">{t('check.product-findings.part')}</span>
        <select
          className="release-input product-part-picker"
          value={`${part.plm}|${part.id}`}
          onChange={(e) => {
            setChosen(e.target.value);
            setOpen(false);
          }}
        >
          {candidates.map((p) => {
            const ext = extended.get(`${p.plm}|${p.id}`);
            return (
              <option key={`${p.plm}|${p.id}`} value={`${p.plm}|${p.id}`}>
                {plmCode(p.plm)} {p.id} {p.name} · {valueText(p.mass!.value, '')} {unitLabel(p.mass!.unit)}
                {ext !== undefined ? ` · ${mass(ext)} ${t('check.product-findings.inTheProduct')}` : ''}
              </option>
            );
          })}
        </select>
      </label>
      {product.massLimit ? (
        <span className="quiet">{t('check.product-findings.limit')} {mass(product.massLimit.limitKg)} · {candidates.length} {t('check.product-findings.partsStateAMassHeaviestFirst')}</span>
      ) : null}
      <ReleaseButton data={data} offer={offer} open={open} onToggle={() => setOpen((o) => !o)} />
      {open ? <OfferRelease key={offer.key} data={data} offer={offer} onClose={() => setOpen(false)} onReleased={onReleased} /> : null}
    </div>
  );
}

interface MassScaleProps {
  data: AppData;
  product: Product;
  parts: Part[];
  onReleased: () => void;
}

/** One release for every part of the site the finding flags: each mass divided into the unit its column states. */
function MassScale({ data, product, parts, onReleased }: MassScaleProps) {
  const [open, setOpen] = useState(false);
  const flagged = parts.filter((p) => p.findings?.some((f) => f.rule === MASS_SCALE));
  if (flagged.length === 0) return <p className="quiet">{t('check.product-findings.noVisiblePartCarriesTheFinding')}</p>;
  const offer = { ...massScaleOffer(flagged, product.name), caption: `${flagged.length} ${t('check.product-findings.partMassesInOneRelease')}` };
  return (
    <div className="product-rule-release">
      <ReleaseButton data={data} offer={offer} open={open} onToggle={() => setOpen((o) => !o)} />
      {open ? <OfferRelease key={offer.key} data={data} offer={offer} onClose={() => setOpen(false)} onReleased={onReleased} /> : null}
    </div>
  );
}
