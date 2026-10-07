// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The lens rides in the location hash next to the subtree root
// (`#/check/IF-13?root=FR-ORN-KIT-001&site=FR&state=RELEASED&released=1`), so a link or a reload
// opens the same view.
import { queryOf, withQuery } from '../subtree/rootHash';
import { LENS_KEYS, type Lens } from './lens';

const RELEASED_PARAM = 'released';

/** The lens a hash names; no values and "As released" off when it names none. */
export function lensOf(hash: string): Lens {
  const query = queryOf(hash);
  const values: Lens['values'] = {};
  for (const k of LENS_KEYS) {
    const v = query.get(k);
    if (v) values[k] = v;
  }
  return { values, released: query.get(RELEASED_PARAM) === '1' };
}

/** The hash with its lens parameters replaced by `lens`; the route and the other parameters stay. */
export function withLens(hash: string, lens: Lens): string {
  const query = queryOf(hash);
  for (const k of LENS_KEYS) {
    const v = lens.values[k];
    if (v) query.set(k, v);
    else query.delete(k);
  }
  if (lens.released) query.set(RELEASED_PARAM, '1');
  else query.delete(RELEASED_PARAM);
  return withQuery(hash, query);
}
