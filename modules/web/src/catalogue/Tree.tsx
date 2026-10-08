// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { Catalogue } from '../api/types';
import type { Resource } from '../api/useResource';
import { cssColour, isCore, PLM_NAME, plmCode } from '../ui/plm';
import { flagCount } from './coverage';
import { t } from '../i18n';

export interface Selection {
  plm: string;
  entity: string;
}

interface Props {
  plms: string[];
  catalogues: Record<string, Resource<Catalogue> | undefined>;
  selection: Selection | null;
  onSelect: (s: Selection) => void;
}

export function Tree({ plms, catalogues, selection, onSelect }: Props) {
  return (
    <nav className="tree" aria-label="Sources and entities">
      {plms.map((plm) => {
        const res = catalogues[plm];
        const code = plmCode(plm);
        return (
          <div key={plm} className={`tree-plm${isCore(plm) ? ' is-atelier' : ''}`}>
            <div className="tree-plm-head">
              <i className="swatch" style={{ background: cssColour(plm) }} />
              <span className="tree-plm-code">{code}</span>
              <span className="quiet">{isCore(plm) ? 'core store, not a PLM' : `${PLM_NAME[code]} PLM`}</span>
              {res?.state === 'ready' && flagCount(res.data) > 0 ? (
                <span className="flag-count" title="Flagged columns">{flagCount(res.data)}</span>
              ) : null}
            </div>
            {res?.state === 'error' ? (
              <p className="tree-error">/api/{plm}/catalogue failed: {res.error.message}</p>
            ) : res?.state !== 'ready' ? (
              <p className="quiet tree-loading">{t('catalogue.tree.readingApi')}{plm}/catalogue</p>
            ) : (
              <ul>
                {res.data.entities.map((e) => {
                  const on = selection?.plm === plm && selection.entity === e.entity;
                  return (
                    <li key={e.entity}>
                      <button type="button" className={`tree-entity${on ? ' is-selected' : ''}`} aria-pressed={on}
                        onClick={() => onSelect({ plm, entity: e.entity })}>
                        <span className="mono">{e.table}</span>
                        <span className="tree-class mono">{e.ontologyClass ?? 'unmapped'}</span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        );
      })}
    </nav>
  );
}
