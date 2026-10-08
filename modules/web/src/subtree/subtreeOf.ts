// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { ALL, type AppData } from '../api/store';
import type { Subtree } from '../api/types';

/** The subtree block of whichever rooted answer has arrived: each screen reads a different one. */
export function subtreeOf(data: AppData): Subtree | null {
  if (!data.root) return null;
  for (const cache of [data.bom, data.parts, data.interfaces]) {
    const r = cache.get(ALL);
    if (r?.state === 'ready' && r.data.subtree) return r.data.subtree;
  }
  return null;
}
