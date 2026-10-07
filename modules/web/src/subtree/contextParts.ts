// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { isPart, type Interface, type PartEntry } from '../api/types';

/**
 * The parts the viewer draws: the listed parts, then each context part the interfaces name that the
 * listing does not. Only a rooted interfaces answer carries context parts, so a product-wide view
 * draws the listing alone.
 */
export function withContext(parts: PartEntry[] | undefined, interfaces: Interface[] | undefined): PartEntry[] | undefined {
  if (!parts || !interfaces) return parts;
  const seen = new Set(parts.filter(isPart).map((p) => p.id));
  const context: PartEntry[] = [];
  for (const p of interfaces.flatMap((i) => i.parts)) {
    if (!isPart(p) || !p.context || seen.has(p.id)) continue;
    seen.add(p.id);
    context.push(p);
  }
  return context.length === 0 ? parts : [...parts, ...context];
}
