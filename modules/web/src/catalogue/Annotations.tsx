// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { Catalogue, CatalogueColumn, CatalogueEntity } from '../api/types';
import { t } from '../i18n';

interface Props {
  catalogue: Catalogue;
  entity: CatalogueEntity;
  column: CatalogueColumn;
}

const q = (s: string) => JSON.stringify(s);

/** The selected column's annotations, laid out as they sit on the JPA entity. */
export function Annotations({ catalogue, entity, column }: Props) {
  const isRelation = catalogue.entities.some((e) => e.entity === column.javaType);
  const lines: [string, string][] = [
    ['ann', '@Entity'],
    ['ann', `@Table(name = ${q(entity.table)})`],
    ...(entity.ontologyClass ? [['sem', `@OntologyClass(${q(entity.ontologyClass)})`] as [string, string]] : []),
    ['kw', `class ${entity.entity} {`],
    ['ann', `  @${isRelation ? 'JoinColumn' : 'Column'}(name = ${q(column.column)})`],
    ...(column.description ? [['sem', `  @Describe(${q(column.description)})`] as [string, string]] : []),
    ...(column.unit ? [['sem', `  @Unit(${q(column.unit)})`] as [string, string]] : []),
    ...(column.unitColumn ? [['sem', `  @Unit(column = ${q(column.unitColumn)})`] as [string, string]] : []),
    ...(column.ontologyTerm ? [['sem', `  @Maps(${q(column.ontologyTerm)})`] as [string, string]] : []),
    ['kw', `  ${column.javaType} ${column.field};`],
    ['kw', '}'],
  ];
  const missing = [
    column.undescribed ? '@Describe' : null,
    column.unitMissing ? '@Unit' : null,
    !column.ontologyTerm ? '@Maps' : null,
  ].filter(Boolean);
  return (
    <div className="annotations">
      <pre className="code mono">
        {lines.map(([kind, text], i) => (
          <span key={i} className={`code-${kind}`}>{text}{'\n'}</span>
        ))}
      </pre>
      {missing.length ? (
        <p className="annotations-missing">
          <span className="flag">{t('catalogue.annotations.notDeclared')}</span> {missing.join(', ')} on <span className="mono">{column.column}</span>
        </p>
      ) : null}
    </div>
  );
}
