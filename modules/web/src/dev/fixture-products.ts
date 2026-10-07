// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The products of the fixtures (docs/contract.md, "Products"): the machines fixture-seed.ts reads from
// data/products/*.json as GET /query/products lists them, and the `?product=` scoping of the parts
// and interfaces listings. A key no product carries is refused as the service does.
import type { Interface, Part, Policy, Product } from '../api/types';
import { isPart, LIFECYCLE_CONFLICT } from '../api/types';
import { assemblyParts } from './fixture-bom';
import { productRules } from './fixture-findings';
import { redactParts } from './fixture-policy';
import { interfaces, parts, products } from './fixture-seed';
import { FIXTURE_MARKER } from './marker';

const partIds = (product: string | null) => products.find((p) => p.key === product)?.partIds;

/** The parts of a product; every part when none is named. */
export function partsOf(product: string | null): Part[] {
  const ids = partIds(product);
  return ids ? parts.filter((p) => ids.has(p.id)) : parts;
}

/** The interfaces whose parts are all the product's; every interface when none is named. */
export function interfacesOf(product: string | null): Interface[] {
  const ids = partIds(product);
  return ids ? interfaces.filter((i) => i.parts.every((p) => isPart(p) && ids.has(p.id))) : interfaces;
}

/** Each product with the number of its parts the profile may see, the product rules' findings and the lifecycleConflict findings on its parts. */
export const productsFor = (pol: Policy): Product[] =>
  products.map((p) => {
    const visible = redactParts(partsOf(p.key), pol).filter(isPart);
    const lifecycleConflicts = [...visible, ...assemblyParts(p.key)].flatMap((part) => part.findings ?? []).filter((f) => f.rule === LIFECYCLE_CONFLICT).length;
    return { key: p.key, name: p.name, frame: p.frame, partCount: visible.length, ...productRules(p.key, pol), lifecycleConflicts };
  });

/** A listing path with its optional `?product=` and `&root=` parameters split off. */
export function listing(path: string): { path: string; product: string | null; root: string | null } {
  const m = /^([^?]*)(?:\?product=([^&]*)(?:&root=([^&]*))?)?$/.exec(path);
  if (!m) return { path, product: null, root: null };
  const product = m[2] ? decodeURIComponent(m[2]) : null;
  if (product !== null && !partIds(product)) throw new Error(`HTTP 404: no product ${product} (${FIXTURE_MARKER})`);
  return { path: m[1], product, root: m[3] ? decodeURIComponent(m[3]) : null };
}
