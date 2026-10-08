// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { Catalogue } from '../api/types';

export interface Coverage {
  described: number;
  total: number;
}

export function coverage(catalogues: Catalogue[]): Coverage {
  const cols = catalogues.flatMap((c) => c.entities.flatMap((e) => e.columns));
  return { described: cols.filter((c) => !c.undescribed).length, total: cols.length };
}

export const flagCount = (c: Catalogue) =>
  c.entities.flatMap((e) => e.columns).filter((col) => col.undescribed || col.unitMissing).length;
