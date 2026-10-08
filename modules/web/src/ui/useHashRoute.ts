// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useCallback, useEffect, useState } from 'react';
import { keepQuery, routeOf } from '../subtree/rootHash';
import { pathOf, pathHash, type PathQuery } from '../paths/pathRoute';

/** The screens this site routes; an open_screen naming another one is no switch. */
export const ROUTED_SCREENS: readonly string[] = ['check', 'paths', 'bom', 'catalogue', 'flow', 'architecture', 'rules'];

export type Route =
  | { screen: 'check'; interfaceId: string | null }
  | { screen: 'paths'; query: PathQuery | null }
  | { screen: 'bom' }
  | { screen: 'catalogue'; term?: string | null }
  | { screen: 'flow' }
  | { screen: 'architecture' }
  | { screen: 'rules'; ruleName: string | null };

function parse(hash: string): Route {
  const [, screen, id, ...rest] = routeOf(hash).replace(/^#/, '').split('/');
  if (screen === 'paths') return { screen: 'paths', query: pathOf([id, ...rest]) };
  if (screen === 'bom') return { screen: 'bom' };
  if (screen === 'catalogue') return { screen: 'catalogue', term: id ? decodeURIComponent(id) : null };
  if (screen === 'flow') return { screen: 'flow' };
  if (screen === 'architecture') return { screen: 'architecture' };
  if (screen === 'rules') return { screen: 'rules', ruleName: id ? decodeURIComponent(id) : null };
  return { screen: 'check', interfaceId: id ? decodeURIComponent(id) : null };
}

export function toHash(r: Route): string {
  if (r.screen === 'paths') return pathHash(r.query);
  if (r.screen === 'bom') return '#/bom';
  if (r.screen === 'catalogue') return r.term ? `#/catalogue/${encodeURIComponent(r.term)}` : '#/catalogue';
  if (r.screen === 'flow') return '#/flow';
  if (r.screen === 'architecture') return '#/architecture';
  if (r.screen === 'rules') return r.ruleName ? `#/rules/${encodeURIComponent(r.ruleName)}` : '#/rules';
  return r.interfaceId ? `#/check/${encodeURIComponent(r.interfaceId)}` : '#/check';
}

export function useHashRoute(): [Route, (r: Route) => void] {
  const [route, setRoute] = useState(() => parse(location.hash));
  useEffect(() => {
    const on = () => setRoute(parse(location.hash));
    window.addEventListener('hashchange', on);
    return () => window.removeEventListener('hashchange', on);
  }, []);
  // A route change keeps the hash's parameters: the screens all show the same subtree, and the lens stays.
  const go = useCallback((r: Route) => {
    location.hash = keepQuery(toHash(r), location.hash);
  }, []);
  return [route, go];
}
