// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The viewer lens: per attribute, at most one value a part must carry to stay at full opacity. Parts
// outside the lens are faded in the scene, never hidden, and the legend counts only the parts inside.
import { isGeometric, isPart, type PartEntry } from '../api/types';
import { plmCode } from '../ui/plm';

/** The attributes the lens filters on, in the strip's order. */
export const LENS_KEYS = ['site', 'state', 'supplier', 'type', 'material'] as const;
export type LensKey = (typeof LENS_KEYS)[number];

export interface Lens {
  values: Partial<Record<LensKey, string>>;
  /** Only RELEASED parts at full opacity, on top of the values. */
  released: boolean;
}

export const NO_LENS: Lens = { values: {}, released: false };

export const RELEASED = 'RELEASED';

/** A part the scene and the legend count: assemblies and site kits are listed with the parts but have no geometry. */
export const inScene = (p: PartEntry) => p.redacted || isGeometric(p);

export const isActive = (lens: Lens) => lens.released || Object.keys(lens.values).length > 0;

/**
 * The part's values for `key`: the owning site's code, the canonical lifecycle state, every supplier that built or
 * offers it, its type and material. A redacted part shows only its site; its other values are unknown.
 */
export function valuesOf(p: PartEntry, key: LensKey): string[] {
  if (key === 'site') return [plmCode(p.plm)];
  if (!isPart(p)) return [];
  if (key === 'supplier') return [...new Set((p.suppliers ?? []).map((s) => s.name))];
  const value = key === 'state' ? p.lifecycleState : key === 'type' ? p.partType ?? 'PART' : p.material;
  return value ? [value] : [];
}

/** Whether the part is inside the lens: it carries every value set (a supplier among its suppliers), and is RELEASED under "As released". */
export function passes(p: PartEntry, lens: Lens): boolean {
  if (lens.released && !valuesOf(p, 'state').includes(RELEASED)) return false;
  return LENS_KEYS.every((k) => lens.values[k] === undefined || valuesOf(p, k).includes(lens.values[k]));
}

/** The values the parts carry for `key`, sorted, with the lens's own value kept so a link from another product still shows it. */
export function optionsOf(parts: PartEntry[], key: LensKey, current: string | undefined): string[] {
  const values = new Set(parts.flatMap((p) => valuesOf(p, key)));
  if (current !== undefined) values.add(current);
  return [...values].sort((a, b) => a.localeCompare(b));
}
