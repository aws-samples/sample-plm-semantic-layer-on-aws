// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The subtree root rides in the location hash after the route (`#/bom?root=FR-ORN-KIT-001`), so a
// link or a reload opens the same subtree and every tab keeps it. The viewer lens rides next to it
// (lens/lensHash.ts); each writer replaces its own parameters and keeps the others.
import { ROOT_PARAM } from '../api/subtree';

/** The route part of a hash, without its parameters. */
export const routeOf = (hash: string) => hash.split('?')[0];

/** The parameters of a hash. */
export const queryOf = (hash: string) => new URLSearchParams(hash.split('?')[1] ?? '');

/** The route with the parameters, or the bare route when there are none. */
export function withQuery(route: string, query: URLSearchParams): string {
  const q = query.toString();
  return q ? `${routeOf(route)}?${q}` : routeOf(route);
}

/** The root a hash names; null when it names none. */
export function rootOf(hash: string): string | null {
  return queryOf(hash).get(ROOT_PARAM);
}

/** The hash with its root replaced by `root`, or removed when `root` is null; its other parameters stay. */
export function withRoot(hash: string, root: string | null): string {
  const query = queryOf(hash);
  if (root) query.set(ROOT_PARAM, root);
  else query.delete(ROOT_PARAM);
  return withQuery(hash, query);
}

/** The route `to` with every parameter of the hash `from`: a route change keeps the subtree root and the lens. */
export const keepQuery = (to: string, from: string) => withQuery(to, queryOf(from));
