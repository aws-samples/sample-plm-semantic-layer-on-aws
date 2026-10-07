// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { ALL, useCached, type AppData } from '../api/store';
import type { Feature, FeatureEntry, VariantConfiguration, VariantDiff, VariantGroup, VariantPart, VariantPort, VariantSide } from '../api/types';
import { isFeature } from '../api/types';
import { t } from '../i18n';
import { ErrorState, Loading } from '../ui/ErrorState';
import { plmCode } from '../ui/plm';
import { converted, isQuantity, propertyLabel } from './properties';
import { RuleChip } from './RuleChip';
import { STATUS_LABEL } from './violations';

/** "UK SPRG-6210-L", or the marker of a part the profile may not see. */
const partText = (p?: VariantPart) => (!p ? '' : 'redacted' in p && p.redacted ? `${plmCode(p.plm)} ${t('check.variants.hiddenPart')}` : `${plmCode(p.plm)} ${'id' in p ? p.id : ''}`);

/** A feature's identifying values as its site stores them, the converted length or pressure beside: "ISO 2341 0.3937 in (10 mm)". */
function featureText(f?: FeatureEntry) {
  if (!f) return '';
  if (!isFeature(f)) return t('check.variants.hiddenFeature');
  const values = Object.entries((f as Feature).properties).filter(([k]) => k !== 'port' && k !== 'gripLength').map(([k, v]) => {
    if (k === 'fastenerCount') return `\u00d7${v}`;
    if (k === 'pinCount') return `${v} ${t('check.variants.pins')}`;
    if (!isQuantity(v)) return String(v);
    const c = converted(v);
    const stored = `${v.value} ${v.unit === 'IN' ? 'in' : v.unit === 'MilliM' ? 'mm' : (v.unit ?? '')}`;
    return c && !(v.unit === 'MilliM' || v.unit === 'BAR') ? `${stored} (${Number(c.value.toFixed(2))} ${c.unit})` : stored;
  });
  return `${f.id}: ${values.join(' \u00b7 ')}`;
}

/** One configuration's side of a port: its interface and status, the mated part and its feature, and the host's own feature the mate sits against. */
const Side = ({ side }: { side?: VariantSide }) =>
  !side ? (
    <td className="quiet">{t('check.variants.noMate')}</td>
  ) : (
    <td>
      <span className="mono">{side.interfaceId}</span> <span className={`status is-${side.status}`}>{STATUS_LABEL[side.status]}</span>
      {side.rules.map((r) => <RuleChip key={r} rule={r} />)}
      <span className="variant-mate">{partText(side.mate)}</span>
      <span className="variant-feature">{featureText(side.mateFeature)}</span>
      {side.feature ? <span className="variant-feature variant-host-feature">{t('check.variants.against')} {plmCode(side.feature.plm)} {featureText(side.feature)}</span> : null}
    </td>
  );

const CHANGE_LABEL: Record<VariantPort['change'], string> = {
  added: 'check.variants.added',
  removed: 'check.variants.removed',
  changed: 'check.variants.changed',
  same: 'check.variants.same',
  'not-modelled': 'check.variants.notModelled',
};

const PortRow = ({ p }: { p: VariantPort }) => (
  <tr className={`variant-port is-${p.change}`}>
    <td>
      <b>{partText(p.host)}</b>
      <span className="variant-port-name">{p.port ?? t('check.variants.noPort')}</span>
      <span className="variant-change">{t(CHANGE_LABEL[p.change])}</span>
      {p.differences.length ? <span className="quiet variant-differences">{p.differences.map(propertyLabel).join(', ')}</span> : null}
    </td>
    <Side side={p.against} />
    <Side side={p.option} />
  </tr>
);

const tallyText = (c: VariantConfiguration) =>
  `${c.tally.pass} ${STATUS_LABEL.pass} · ${c.tally.fail} ${STATUS_LABEL.fail} · ${c.tally.notEvaluable} ${STATUS_LABEL['not-evaluable']}`;

const Figures = ({ diff }: { diff: VariantDiff }) => (
  <dl className="variant-figures">
    {[diff.configuration, diff.baseline].map((c) => (
      <div key={c.option}>
        <dt>{c.option}</dt>
        <dd>
          {c.interfaces} {t('check.variants.interfaces')}: {tallyText(c)}; {Math.round(c.occurrencesTotal).toLocaleString('en')} {t('check.variants.occurrences')},{' '}
          {c.massKg.toLocaleString('en', { maximumFractionDigits: 1 })} kg
        </dd>
      </div>
    ))}
  </dl>
);

const Items = ({ label, items }: { label: string; items: VariantDiff['added'] }) =>
  items.items.length || items.interfaces.length ? (
    <p className="variant-items">
      <span className="quiet">{label}</span> {items.items.map((p) => partText(p)).join(', ')}
      {items.interfaces.length ? ` · ${items.interfaces.join(', ')}` : ''}
    </p>
  ) : null;

function DiffView({ data, group, option }: { data: AppData; group: VariantGroup; option: string }) {
  const diff = useCached(data.variantDiff, `${group.key}|${option}`);
  if (diff.state === 'error') {
    return <ErrorState title={t('check.variants.diffFailed')} error={diff.error} onRetry={() => data.variantDiff.retry(`${group.key}|${option}`)} />;
  }
  if (diff.state !== 'ready') return <Loading what={t('check.variants.running')} />;
  const d = diff.data;
  return (
    <div className="variant-diff">
      {d.option.applicability ? <p className="quiet">{d.option.applicability}</p> : null}
      <Figures diff={d} />
      <table className="variant-ports">
        <thead>
          <tr>
            <th>{t('check.variants.hostPort')}</th>
            <th>{d.against.key}</th>
            <th>{d.option.key}</th>
          </tr>
        </thead>
        <tbody>{d.ports.map((p, i) => <PortRow key={i} p={p} />)}</tbody>
      </table>
      <Items label={t('check.variants.onlyTheDefaultHolds')} items={d.removed} />
      <Items label={t('check.variants.onlyTheOptionHolds')} items={d.added} />
    </div>
  );
}

/**
 * The product's variant groups, each with an option switch. The default options make the base product; taking another
 * option shows the product as that option makes it, on the screen and in the viewer, with its diff against the default,
 * port by port, the rules run on each configuration. One option is taken at a time: taking one puts the other groups
 * back at their defaults. Nothing when the product has no variant group.
 */
export function Variants({ data, onOption }: { data: AppData; onOption: (option: string | null) => void }) {
  const groups = useCached(data.variants, data.product ? ALL : null);
  if (!data.product || groups.state !== 'ready' || groups.data.groups.length === 0) return null;
  return (
    <section className="variants" aria-label={t('check.variants.variants')}>
      <h2 className="eyebrow">{t('check.variants.variants')}</h2>
      {groups.data.groups.map((g) => {
        const option = data.option !== null && g.options.includes(data.option) ? data.option : g.defaultOption;
        return (
          <div key={g.key} className="variant-group">
            <label className="variant-switch">
              <span>{g.name ?? g.key}</span>
              <select value={option} onChange={(e) => onOption(e.target.value === g.defaultOption ? null : e.target.value)}>
                {g.options.map((o) => (
                  <option key={o} value={o}>
                    {o === g.defaultOption ? `${o} (${t('check.variants.base')})` : o}
                  </option>
                ))}
              </select>
            </label>
            {g.selects ? <p className="quiet variant-selects">{g.selects}</p> : null}
            {option !== g.defaultOption ? <DiffView data={data} group={g} option={option} /> : null}
          </div>
        );
      })}
    </section>
  );
}
