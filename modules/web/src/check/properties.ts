// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Reading a feature's class-specific properties without knowing the class.
import type { Feature, PropertyValue, Quantity } from '../api/types';

export const isQuantity = (v: PropertyValue | undefined): v is Quantity => typeof v === 'object' && v !== null && 'value' in v;

/** Ontology local name to a row label: fastenerStandard becomes "Fastener standard". */
export const propertyLabel = (key: string) =>
  key.replace(/([a-z0-9])([A-Z])/g, '$1 $2').toLowerCase().replace(/^./, (c) => c.toUpperCase());

/** The normalised value of a quantity and its unit, when the service could convert it. */
export function converted(q: Quantity): { value: number; unit: 'mm' | 'bar' } | null {
  if (typeof q.mm === 'number') return { value: q.mm, unit: 'mm' };
  if (typeof q.bar === 'number') return { value: q.bar, unit: 'bar' };
  return null;
}

/** The stored unit already is the normalised one, so the conversion adds nothing. */
export const sameUnit = (q: Quantity) => q.unit === 'MilliM' || q.unit === 'BAR';

/** Property keys of the features, in the order the first feature lists them. */
export const propertyKeys = (fs: Feature[]) => [...new Set(fs.flatMap((f) => Object.keys(f.properties)))];

/** Two features disagree on a property: by normalised value for quantities, by identity otherwise. */
export function differs(a: PropertyValue | undefined, b: PropertyValue | undefined): boolean {
  if (a === undefined || b === undefined) return a !== b;
  if (isQuantity(a) && isQuantity(b)) {
    const ca = converted(a);
    const cb = converted(b);
    return ca && cb ? ca.value !== cb.value : a.value !== b.value || a.unit !== b.unit;
  }
  return a !== b;
}

/** A violation detail entry that compares the two sides and found them different. */
export const differingPair = (value: unknown) => Array.isArray(value) && value.length === 2 && value[0] !== value[1];

/** Property names a violation's detail found different, e.g. diameterMm names diameter. */
export const detailProperties = (detail: Record<string, unknown>) =>
  new Set(Object.keys(detail).filter((k) => differingPair(detail[k])).map((k) => k.replace(/(Mm|Bar)$/, '')));
