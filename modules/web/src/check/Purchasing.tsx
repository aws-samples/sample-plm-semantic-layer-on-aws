// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { ALL, useCached, type AppData } from '../api/store';
import type { EquivalentGroup, EquivalentMember, GroupAttribute, MemberValue, SuppliersResponse } from '../api/types';
import { isPart } from '../api/types';
import { plmCode } from '../ui/plm';
import { ConfirmEquivalence } from './demo/ConfirmEquivalence';
import { rereadSites } from './demo/reread';
import { EnglishName } from './EnglishName';
import { LeadTimeConflicts } from './LeadTimeConflicts';
import { t } from '../i18n';

const unitText = (unit?: string) => (unit === 'IN' ? 'in' : unit === 'MilliM' ? 'mm' : unit ?? '');

/** A length in mm to the micrometre: 0.984 in is 24.994 mm. */
const mmText = (mm: number) => `${Number(mm.toFixed(3))}\u00a0mm`;

/** A group's value of one attribute: "24.99 mm", "ISO 4762", "Polycarbonate film 0.25 mm". */
const attributeText = (a: GroupAttribute) => [a.text, a.mm !== undefined ? mmText(a.mm) : undefined].filter(Boolean).join(' ');

/**
 * A member's value as its site writes it, with its unit: "0.489 in", "12.42 mm (AS568-014)" when the size of the
 * standard supplies it, the text of a compound or a legend; the length in mm under it when the stored text differs.
 */
function ValueCell({ v, standard }: { v?: MemberValue; standard?: string }) {
  if (!v) return <td className="quiet">{t('check.purchasing.notStated')}</td>;
  const stored = v.unit ? `${v.stored}\u00a0${unitText(v.unit)}` : v.stored;
  const converted = v.mm !== undefined && !(stored ?? '').includes(String(Number(v.mm.toFixed(3)))) ? mmText(v.mm) : null;
  return (
    <td title={v.conceptLabel}>
      {stored}
      {v.fromStandard && standard ? <span className="equivalent-mm">({standard})</span> : null}
      {converted ? <span className="equivalent-mm">{converted}</span> : null}
    </td>
  );
}

const MemberRow = ({ m, attributes, nameEn }: { m: EquivalentMember; attributes: GroupAttribute[]; nameEn?: string }) => (
  <tr>
    <td className="equivalent-part">
      <b>{plmCode(m.plm)}</b> <span className="mono">{m.id}</span>
      <span className="equivalent-name">{m.name}</span>
      <EnglishName name={m.name} nameEn={nameEn} />
    </td>
    {attributes.map((a) => <ValueCell key={a.attribute} v={m.values.find((v) => v.attribute === a.attribute)} standard={m.standard} />)}
  </tr>
);

/** "4 part numbers in 3 sites; one stock line once confirmed", or "one stock line" for a confirmed group. */
function stockingText(g: EquivalentGroup) {
  const lines = (n: number) => (n === 1 ? t('check.purchasing.oneStockLine') : `${n} ${t('check.purchasing.stockLines')}`);
  if (g.confirmed) return lines(g.stocking.stockLines);
  const { partNumbers, sites } = g.stocking;
  return `${partNumbers} ${t('check.purchasing.partNumbersIn')} ${sites} ${t(sites === 1 ? 'check.purchasing.site' : 'check.purchasing.sites')}; ${lines(
    g.stocking.stockLinesOnceConfirmed,
  )} ${t('check.purchasing.onceConfirmed')}`;
}

interface GroupProps {
  g: EquivalentGroup;
  data: AppData;
  /** English name of a part, by `plm|id`, from the parts answer. */
  english: Map<string, string>;
  onConfirmed: () => void;
}

const Group = ({ g, data, english, onConfirmed }: GroupProps) => (
  <li className={`equivalent${g.confirmed ? ' is-confirmed' : ''}`}>
    <span className="equivalent-item">
      <b>{g.classLabel}</b>
      {g.attributes.map((a) => (
        <span key={a.attribute} className="equivalent-value">
          <span className="quiet">{a.label}</span> {attributeText(a)}
        </span>
      ))}
    </span>
    <span className="purchasing-native">
      {g.shelfLifeMonths !== undefined ? `${t('check.purchasing.shelfLife')} ${g.shelfLifeMonths} ${t('check.purchasing.months')} · ` : ''}
      {stockingText(g)}
    </span>
    <div className="equivalent-members">
      <table className="equivalent-table">
        <thead>
          <tr>
            <th>{t('check.purchasing.part')}</th>
            {g.attributes.map((a) => <th key={a.attribute}>{a.label}</th>)}
          </tr>
        </thead>
        <tbody>
          {g.members.map((m) => <MemberRow key={`${m.plm}|${m.id}`} m={m} attributes={g.attributes} nameEn={english.get(`${m.plm}|${m.id}`)} />)}
        </tbody>
      </table>
    </div>
    <ConfirmEquivalence data={data} group={g} onConfirmed={onConfirmed} />
  </li>
);

/** Lead days of a supplier's offers at one site, as a range. */
const leadDays = (days: number[]) => (Math.min(...days) === Math.max(...days) ? `${days[0]}` : `${Math.min(...days)}–${Math.max(...days)}`);

function SupplierRows({ s }: { s: SuppliersResponse }) {
  return (
    <ul className="purchasing-list">
      {s.suppliers.map((sup) => (
        <li key={sup.name}>
          <b className="purchasing-name">{sup.name}</b>
          {sup.sites.map((site) => (
            <span key={`${site.plm}|${site.id}`} className="purchasing-native">
              {plmCode(site.plm)} {site.id}{site.location ? `, ${site.location}` : ''}: {site.offers.length}{' '}
              {t(site.offers.length === 1 ? 'check.purchasing.part' : 'check.purchasing.parts')}, {leadDays(site.offers.map((o) => o.leadTimeDays))}{' '}
              {t('check.purchasing.days')}
            </span>
          ))}
        </li>
      ))}
    </ul>
  );
}

/**
 * What the layer knows about the product's purchased items that no site does: the parts the sites buy under other
 * numbers and units that are one item, the parts with one supplier, the conflicting lead times, and each supplier
 * across the sites that list it; each conflict offers the site's releases. Nothing for a product without suppliers.
 */
export function Purchasing({ data, onRerun }: { data: AppData; onRerun: () => void }) {
  const scope = data.product ? ALL : null;
  const equivalents = useCached(data.equivalents, scope);
  const suppliers = useCached(data.suppliers, scope);
  const parts = useCached(data.parts, scope);
  // A site has released an offer's lead time or preference: the suppliers are read again and the rules run again.
  const onReleased = () => {
    rereadSites(data);
    onRerun();
  };
  // A user confirmed a group: the links graph holds it, so the equivalents are read again and the change feed with the run.
  const onConfirmed = () => {
    data.equivalents.retry(ALL);
    onRerun();
  };
  if (equivalents.state !== 'ready' || suppliers.state !== 'ready') return null;
  const english = new Map(parts.state === 'ready' ? parts.data.parts.filter(isPart).flatMap((p) => (p.nameEn ? [[`${p.plm}|${p.id}`, p.nameEn] as const] : [])) : []);
  const groups = equivalents.data.groups;
  const s = suppliers.data;
  if (groups.length === 0 && s.suppliers.length === 0) return null;
  return (
    <section className="purchasing" aria-label={t('check.purchasing.suppliers')}>
      <h2 className="eyebrow">{t('check.purchasing.suppliers')}</h2>
      <p className="purchasing-figures">
        <span><b>{s.suppliers.length}</b> {t('check.purchasing.suppliersAcrossTheSites')}</span>
        <span><b>{s.singleSource.length}</b> {t('check.purchasing.singleSourceParts')}</span>
        <span><b>{groups.length}</b> {t('check.purchasing.itemsUnderSeveralNumbers')}</span>
      </p>
      {s.conflicts.length ? <LeadTimeConflicts data={data} suppliers={s} onReleased={onReleased} /> : null}
      {groups.length ? (
        <>
          <h3 className="purchasing-sub">{t('check.purchasing.oneItemSeveralPartNumbers')}</h3>
          <ul className="purchasing-list">
            {groups.map((g) => <Group key={`${g.itemClass}|${g.members.map((m) => `${m.plm}|${m.id}`).join(' ')}`} g={g} data={data} english={english} onConfirmed={onConfirmed} />)}
          </ul>
        </>
      ) : null}
      {s.singleSource.length ? (
        <details className="purchasing-more">
          <summary>{t('check.purchasing.singleSourceParts')} ({s.singleSource.length})</summary>
          <ul className="purchasing-list">
            {s.singleSource.map((p) => (
              <li key={`${p.plm}|${p.id}`}>
                <span><b>{plmCode(p.plm)}</b> {p.name} <span className="mono quiet">{p.id}</span></span>
                <span className="purchasing-native">{p.supplier}, {p.leadTimeDays} {t('check.purchasing.days')}</span>
              </li>
            ))}
          </ul>
        </details>
      ) : null}
      {s.suppliers.length ? (
        <details className="purchasing-more">
          <summary>{t('check.purchasing.suppliersBySite')} ({s.suppliers.length})</summary>
          <SupplierRows s={s} />
        </details>
      ) : null}
    </section>
  );
}
