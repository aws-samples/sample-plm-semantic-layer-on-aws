// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useCallback, useEffect, useState } from 'react';
import type { Lens } from './lens';
import { lensOf, withLens } from './lensHash';

/** The lens the hash names, and a setter that writes it there. */
export function useLens(): [Lens, (lens: Lens) => void] {
  const [lens, setState] = useState(() => lensOf(location.hash));
  useEffect(() => {
    const on = () => setState(lensOf(location.hash));
    window.addEventListener('hashchange', on);
    return () => window.removeEventListener('hashchange', on);
  }, []);
  const setLens = useCallback((next: Lens) => {
    const hash = withLens(location.hash || '#/check', next);
    if (hash !== location.hash) location.hash = hash;
  }, []);
  return [lens, setLens];
}
