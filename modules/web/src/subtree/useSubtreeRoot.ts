// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useCallback, useEffect, useState } from 'react';
import { rootOf, withRoot } from './rootHash';

/** The subtree root the hash names, and a setter that writes it there; null is the whole product. */
export function useSubtreeRoot(): [string | null, (root: string | null) => void] {
  const [root, setState] = useState(() => rootOf(location.hash));
  useEffect(() => {
    const on = () => setState(rootOf(location.hash));
    window.addEventListener('hashchange', on);
    return () => window.removeEventListener('hashchange', on);
  }, []);
  const setRoot = useCallback((next: string | null) => {
    const hash = withRoot(location.hash || '#/check', next);
    if (hash !== location.hash) location.hash = hash;
  }, []);
  return [root, setRoot];
}
