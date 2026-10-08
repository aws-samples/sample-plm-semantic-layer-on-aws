// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { Catalogue, CatalogueEntity, Interface } from '../../api/types';
import { differingPair } from '../properties';

export const entityOf = (catalogue: Catalogue | null, table: string): CatalogueEntity | undefined =>
  catalogue?.entities.find((e) => e.table === table);

/** Native columns the interface's violations are about, and for each the features whose rows they concern. */
export interface AboutColumns {
  all: Set<string>;
  /** Whether a violation naming `featureId` is about `column`. */
  of: (column: string, featureId: string) => boolean;
}

/**
 * Names the columns of `table` through the catalogue's ontology terms: a differing pair in a
 * violation's detail names its property (connectorType, diameterMm names atelier:diameter); the axis
 * of a unit or position result names the position column. A named column brings its per-row unit
 * column along.
 */
export function violationColumns(itf: Interface, catalogue: Catalogue | null, table: string): AboutColumns {
  const byColumn = new Map<string, Set<string>>();
  const entity = entityOf(catalogue, table);
  if (entity) {
    for (const v of itf.violations) {
      const terms = new Set<string>();
      const axis = typeof v.detail.axis === 'string' ? v.detail.axis.toUpperCase() : null;
      if ((v.rule === 'unit' || v.rule === 'position') && axis) terms.add(`atelier:position${axis}`);
      for (const [key, value] of Object.entries(v.detail)) {
        if (differingPair(value)) terms.add(`atelier:${key.replace(/(Mm|Bar)$/, '')}`);
      }
      for (const c of entity.columns) {
        if (!c.ontologyTerm || !terms.has(c.ontologyTerm)) continue;
        for (const column of [c.column, ...(c.unitColumn ? [c.unitColumn] : [])]) {
          const ids = byColumn.get(column) ?? new Set<string>();
          for (const id of v.features) ids.add(id);
          byColumn.set(column, ids);
        }
      }
    }
  }
  return { all: new Set(byColumn.keys()), of: (column, featureId) => byColumn.get(column)?.has(featureId) ?? false };
}
