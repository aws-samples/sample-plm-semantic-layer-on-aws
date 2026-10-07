// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useCallback, useEffect, useState } from 'react';

export type Resource<T> =
  | { state: 'loading'; data?: undefined }
  | { state: 'ready'; data: T }
  | { state: 'error'; error: Error; data?: undefined };

/** Runs `load` whenever `key` changes or `reload` is called; a null key loads nothing. */
export function useResource<T>(key: string | null, load: () => Promise<T>): [Resource<T>, () => void] {
  const [res, setRes] = useState<Resource<T>>({ state: 'loading' });
  const [tick, setTick] = useState(0);
  useEffect(() => {
    if (key === null) return;
    let live = true;
    setRes({ state: 'loading' });
    load().then(
      (data) => live && setRes({ state: 'ready', data }),
      (error: unknown) =>
        live && setRes({ state: 'error', error: error instanceof Error ? error : new Error(String(error)) }),
    );
    return () => {
      live = false;
    };
  }, [key, tick]);
  const reload = useCallback(() => setTick((t) => t + 1), []);
  return [res, reload];
}
