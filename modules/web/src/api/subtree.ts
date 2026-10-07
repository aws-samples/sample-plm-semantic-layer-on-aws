// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// A subtree root narrows the parts, interfaces, bill of materials and references listings to one
// assembly of the product and everything under it (docs/contract.md). The root always travels
// with the product: a root without a product names nothing.
import { scoped } from './product';

/** Query parameter the query service roots a listing with. */
export const ROOT_PARAM = 'root';

/** The path scoped to the product, then rooted at `root` when one is set. */
export function rooted(path: string, product: string | null, root: string | null): string {
  const p = scoped(path, product);
  return product && root ? `${p}&${ROOT_PARAM}=${encodeURIComponent(root)}` : p;
}
