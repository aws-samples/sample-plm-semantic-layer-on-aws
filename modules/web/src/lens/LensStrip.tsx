// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useMemo } from 'react';
import type { PartEntry } from '../api/types';
import { t } from '../i18n';
import { LENS_KEYS, NO_LENS, inScene, isActive, optionsOf, passes, type Lens, type LensKey } from './lens';

interface Props {
  /** The parts listed for the scene; the options are the values the drawn ones carry. */
  parts: PartEntry[] | undefined;
  lens: Lens;
  onLens: (lens: Lens) => void;
}

const LABEL: Record<LensKey, string> = {
  site: 'lens.strip.site',
  state: 'lens.strip.lifecycleState',
  supplier: 'lens.strip.supplier',
  type: 'lens.strip.partType',
  material: 'lens.strip.material',
};

/** The lens over the 3D view: one select per attribute and "As released"; the parts outside it are faded. */
export function LensStrip({ parts, lens, onLens }: Props) {
  const all = useMemo(() => parts?.filter(inScene) ?? [], [parts]);
  const inside = all.filter((p) => passes(p, lens)).length;
  const set = (key: LensKey, value: string) => {
    const values = { ...lens.values };
    if (value) values[key] = value;
    else delete values[key];
    onLens({ ...lens, values });
  };
  return (
    <div className="lens" role="group" aria-label={t('lens.strip.ariaLabel')}>
      <span className="eyebrow">{t('lens.strip.lens')}</span>
      {LENS_KEYS.map((key) => {
        const current = lens.values[key];
        const options = optionsOf(all, key, current);
        return (
          <label key={key} className={`lens-field${current ? ' is-set' : ''}`}>
            <span className="lens-label" title={key === 'supplier' ? t('lens.strip.supplierTitle') : undefined}>{t(LABEL[key])}</span>
            <select
              className="lens-select"
              data-lens={key}
              value={current ?? ''}
              disabled={!current && options.length < 2}
              title={current}
              onChange={(e) => set(key, e.target.value)}
            >
              <option value="">{t('lens.strip.any')}</option>
              {options.map((o) => <option key={o} value={o}>{o}</option>)}
            </select>
          </label>
        );
      })}
      <button
        type="button"
        className="btn btn-small lens-released"
        aria-pressed={lens.released}
        onClick={() => onLens({ ...lens, released: !lens.released })}
      >
        {t('lens.strip.asReleased')}
      </button>
      {isActive(lens) ? (
        <>
          <span className="lens-count">
            <b>{inside}</b> {t('lens.strip.of')} {all.length} {t('lens.strip.partsInTheLens')}
          </span>
          <button type="button" className="lens-clear" onClick={() => onLens(NO_LENS)}>{t('lens.strip.clear')}</button>
        </>
      ) : null}
    </div>
  );
}
