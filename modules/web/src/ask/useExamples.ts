// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useMemo } from 'react';
import { ALL, useCached, type AppData } from '../api/store';
import type { Resource } from '../api/useResource';
import { examplesFor, GENERIC, type Example } from './examples';

const settled = (r: Resource<unknown>) => r.state !== 'loading';
const dataOf = <T>(r: Resource<T>): T | null => (r.state === 'ready' ? r.data : null);

/**
 * The example questions of the product, profile and subtree on screen, from the same cached answers the screens read.
 * The generic questions stand until every answer has settled, so the list changes once.
 */
export function useExamples(data: AppData): Example[] {
  const key = data.product ? ALL : null;
  const parts = useCached(data.parts, key);
  const interfaces = useCached(data.interfaces, key);
  const bom = useCached(data.bom, key);
  const equivalents = useCached(data.equivalents, key);
  return useMemo(() => {
    if (![parts, interfaces, bom, equivalents].every(settled)) return GENERIC;
    const p = dataOf(parts);
    const i = dataOf(interfaces);
    if (!p || !i) return GENERIC;
    return examplesFor({ parts: p.parts, interfaces: i.interfaces, bom: dataOf(bom), equivalents: dataOf(equivalents)?.groups ?? null });
  }, [parts, interfaces, bom, equivalents]);
}
