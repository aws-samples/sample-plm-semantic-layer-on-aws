// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useEffect, useRef, useState } from 'react';
import { getJson, postJson } from '../../api/client';
import type { AppData } from '../../api/store';
import type { ChangeRequest, ChangeResult, Changes } from '../../api/types';
import { correctionLanded } from './changes';

/** The change list is read every 2 s until every row of the correction has reached it, for at most 30 s. */
const POLL_MS = 2000;
export const MAX_MS = 30_000;
const toError = (e: unknown) => (e instanceof Error ? e : new Error(String(e)));

export type Post =
  | { kind: 'idle' }
  | { kind: 'posting' }
  /** The PLM has answered: its UPDATEs are done, and the change list is read until the event has logged every row. */
  | { kind: 'waiting'; result: ChangeResult; since: number; elapsed: number }
  | { kind: 'done'; result: ChangeResult }
  | { kind: 'timeout'; result: ChangeResult }
  | { kind: 'error'; error: Error };

/**
 * Posts a correction to the owning PLM and follows it to Atelier. The PLM never calls Atelier: its
 * part.value.corrected event reaches the change list a second or two later, and `onReleased` runs once it has.
 */
export function useRelease(data: AppData, plm: string, onReleased: () => void) {
  const [post, setPost] = useState<Post>({ kind: 'idle' });
  const released = useRef(onReleased);
  released.current = onReleased;

  const release = async (body: ChangeRequest) => {
    setPost({ kind: 'posting' });
    try {
      const result = await postJson<ChangeResult>(data.config, `/${plm}/demo/update`, data.profile, body);
      setPost({ kind: 'waiting', result, since: Date.now(), elapsed: 0 });
    } catch (e) {
      setPost({ kind: 'error', error: toError(e) });
    }
  };

  useEffect(() => {
    if (post.kind !== 'waiting') return;
    const { result, since } = post;
    let live = true;
    const timer = setTimeout(async () => {
      let landed = false;
      try {
        landed = correctionLanded(await getJson<Changes>(data.config, '/query/demo/changes', data.profile), result) !== null;
      } catch {
        // A failed poll is not a failed event: keep waiting.
      }
      if (!live) return;
      if (landed) {
        setPost({ kind: 'done', result });
        released.current();
      } else if (Date.now() - since >= MAX_MS) {
        setPost({ kind: 'timeout', result });
      } else {
        setPost({ kind: 'waiting', result, since, elapsed: Math.round((Date.now() - since) / 1000) });
      }
    }, POLL_MS);
    return () => {
      live = false;
      clearTimeout(timer);
    };
  }, [post, data.config, data.profile]);

  const keepWaiting = (result: ChangeResult) => setPost({ kind: 'waiting', result, since: Date.now(), elapsed: 0 });
  return { post, release, keepWaiting };
}
