// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useCallback, useEffect, useState } from 'react';
import { optionOf, withOption } from './optionHash';

/** The option the hash names, and a setter that writes it there; null is the base product. */
export function useOption(): [string | null, (option: string | null) => void] {
  const [option, setState] = useState(() => optionOf(location.hash));
  useEffect(() => {
    const on = () => setState(optionOf(location.hash));
    window.addEventListener('hashchange', on);
    return () => window.removeEventListener('hashchange', on);
  }, []);
  const setOption = useCallback((next: string | null) => {
    const hash = withOption(location.hash || '#/check', next);
    if (hash !== location.hash) location.hash = hash;
  }, []);
  return [option, setOption];
}
