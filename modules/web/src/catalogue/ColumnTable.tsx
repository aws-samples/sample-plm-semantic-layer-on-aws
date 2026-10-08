// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { CatalogueEntity } from '../api/types';
import { ownerLabel } from '../ui/plm';
import { t } from '../i18n';

interface Props {
  plm: string;
  entity: CatalogueEntity;
  selectedColumn: string | null;
  onSelectColumn: (column: string) => void;
}

export function ColumnTable({ plm, entity, selectedColumn, onSelectColumn }: Props) {
  return (
    <section className="columns">
      <header className="columns-head">
        <div>
          <span className="eyebrow">{ownerLabel(plm)} · native table</span>
          <h2 className="columns-title"><span className="mono">{entity.table}</span></h2>
        </div>
        <div className="columns-meta">
          <span>{t('catalogue.column-table.jpaEntity')} <span className="mono">{entity.entity}</span></span>
          <span>{t('catalogue.column-table.ontologyClass')} <span className="mono">{entity.ontologyClass ?? 'none'}</span></span>
        </div>
      </header>
      {entity.description ? <p className="columns-desc">{entity.description}</p> : null}
      <table className="col-table">
        <thead>
          <tr>
            <th scope="col">{t('catalogue.column-table.column')}</th>
            <th scope="col">{t('catalogue.column-table.javaType')}</th>
            <th scope="col">{t('catalogue.column-table.description')}</th>
            <th scope="col">{t('catalogue.column-table.unit')}</th>
            <th scope="col">{t('catalogue.column-table.ontologyTerm')}</th>
          </tr>
        </thead>
        <tbody>
          {entity.columns.map((c) => (
            <tr key={c.column} tabIndex={0} aria-selected={c.column === selectedColumn}
              className={[c.undescribed || c.unitMissing ? 'is-flagged' : '', c.column === selectedColumn ? 'is-selected' : ''].join(' ').trim() || undefined}
              onClick={() => onSelectColumn(c.column)} onKeyDown={(e) => e.key === 'Enter' && onSelectColumn(c.column)}>
              <td className="mono">{c.column}</td>
              <td className="mono quiet">{c.javaType}</td>
              <td>{c.undescribed ? <span className="flag">{t('catalogue.column-table.undescribed')}</span> : c.description}</td>
              <td className="mono">
                {c.unit ?? (c.unitColumn ? <>per row, from <b>{c.unitColumn}</b></> : null)}
                {c.unitMissing ? <span className="flag">{t('catalogue.column-table.unitMissing')}</span> : null}
                {!c.unit && !c.unitColumn && !c.unitMissing ? <span className="quiet">-</span> : null}
              </td>
              <td className="mono">{c.ontologyTerm ?? <span className="quiet">{t('catalogue.column-table.unmapped')}</span>}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="flag-key">
        <span className="quiet">{t('catalogue.column-table.selectAColumnToSeeThe')}</span>
        <span><span className="flag">{t('catalogue.column-table.undescribed')}</span>the field has no @Describe, so the catalogue cannot say what it holds.</span>
        <span><span className="flag">{t('catalogue.column-table.unitMissing')}</span>a numeric position mapped to atelier:position has no @Unit.</span>
      </div>
    </section>
  );
}
