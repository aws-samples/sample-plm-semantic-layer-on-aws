// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { Catalogue, CellValue } from '../../api/types';
import type { Planned } from './proposals';

/**
 * A correction a site may release from where its finding is shown: the site that owns the record, what the release
 * does in words, and the proposal, planned once that site's catalogue names the table and column.
 */
export interface Offer {
  /** Unique among the offers on screen: which form is open. */
  key: string;
  /** Lower-case code of the PLM that releases. */
  plm: string;
  /** Under the button: "match the DE record", "block FR3876". */
  caption?: string;
  plan: (catalogue: Catalogue) => Planned;
}

/** A stored or proposed value in words: numbers as stored, flags as true or false, "no value" for an empty cell. */
export const valueText = (v: CellValue | undefined, none: string) => {
  if (v === null || v === undefined) return none;
  if (typeof v === 'number') return v.toLocaleString('en-GB', { maximumFractionDigits: 4, useGrouping: false });
  return String(v);
};
