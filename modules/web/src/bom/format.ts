// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Figures of the bill of materials. Masses span a fastener's grams to a tower's hundreds of tonnes,
// so the unit follows the magnitude and the precision stays at about three significant figures.
const grouped = (v: number, digits: number) => v.toLocaleString('en-GB', { maximumFractionDigits: digits });

/** "12 g", "5.05 kg", "520 kg", "60.78 t", "1,372.9 t". */
export function mass(kg: number): string {
  if (kg === 0) return '0 kg';
  if (kg < 1) return `${grouped(kg * 1000, kg < 0.01 ? 1 : 0)} g`;
  if (kg < 10) return `${grouped(kg, 2)} kg`;
  if (kg < 1000) return `${grouped(kg, 1)} kg`;
  const t = kg / 1000;
  return `${grouped(t, t < 100 ? 2 : 1)} t`;
}

/** A count with thousands separators; a fractional quantity (a length, a volume) keeps up to three decimals. */
export function count(n: number): string {
  return n.toLocaleString('en-GB', { maximumFractionDigits: 3 });
}
