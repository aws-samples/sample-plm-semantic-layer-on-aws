// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Products are what the parts are assembled into (docs/contract.md, "Products"). The selected
// product scopes every listing of parts and interfaces the way the profile does: the query service
// asks the PLMs about the product's parts only. The list comes from GET /api/query/products/list, with
// the number of parts the profile may see, and the product rules' results from GET /api/query/products after it; the
// first product listed is selected until the viewer
// picks another; the deployment names the one to open on in config.json (defaultProduct). A null
// selection means every product, which is how the API answers without the parameter.
import type { Product, ProductListing } from './types';

/** Query parameter the query service scopes a listing with. */
export const PRODUCT_PARAM = 'product';

/** The path with `?product=key` appended; the path itself when every product is meant. */
export const scoped = (path: string, product: string | null) =>
  product ? `${path}${path.includes('?') ? '&' : '?'}${PRODUCT_PARAM}=${encodeURIComponent(product)}` : path;

/** The product to show: the selected one when it is listed, else the first listed, else none. */
export function pickProduct(products: Product[], selected: string | null): Product | null {
  return products.find((p) => p.key === selected) ?? products[0] ?? null;
}

/** The listed products, each with the product rules' results of the same product when that answer holds it. */
export function withRules(listed: ProductListing[], ruled: Product[]): Product[] {
  const byKey = new Map(ruled.map((p) => [p.key, p]));
  return listed.map((p) => {
    const r = byKey.get(p.key);
    return r ? { ...p, findings: r.findings, massLimit: r.massLimit, lifecycleConflicts: r.lifecycleConflicts } : p;
  });
}
