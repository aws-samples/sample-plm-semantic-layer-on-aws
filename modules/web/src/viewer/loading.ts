// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// STEP files fetched from their presigned URLs and decoded off the main thread in the shared workers, each file once.
import { getCad } from '../api/client';
import type { Part, PartEntry } from '../api/types';
import { isPart } from '../api/types';
import { open } from './decoders';
import type { ParsedMesh } from './stepWorker';

/** A part the file index names a file for and the service issued a URL for. */
export type Issued = Part & { cadFile: string; cadUrl: string };
export const issued = (p: PartEntry): p is Issued => isPart(p) && p.cadFile !== null && p.cadUrl !== null;

/**
 * The worker's meshes by bucket key, kept across scenes: a scene built again draws every file decoded
 * before with no download and no tessellation. A publication names a new key, so a changed file is
 * never served from here. The least recently drawn file goes first past the bound.
 */
const DECODED_MAX = 300;
const decoded = new Map<string, ParsedMesh[]>();
function remember(cadFile: string, meshes: ParsedMesh[]) {
  decoded.delete(cadFile);
  decoded.set(cadFile, meshes);
  if (decoded.size > DECODED_MAX) decoded.delete(decoded.keys().next().value!);
}

/** The meshes of a file decoded before, or undefined; a hit counts as a use. */
export function recall(cadFile: string): ParsedMesh[] | undefined {
  const meshes = decoded.get(cadFile);
  if (meshes) remember(cadFile, meshes);
  return meshes;
}

export interface Decoding {
  onPart: (part: Issued, meshes: ParsedMesh[]) => void;
  onError: (part: Issued, message: string) => void;
}

/** The decoding of one load; a newer load, or the scene's disposal, cancels it. */
export interface Pool {
  cancel: () => void;
}

/**
 * Fetches the parts' files and decodes them in the shared workers (decoders.ts), each file to the worker with the
 * fewest files waiting, so a part is drawn as soon as its own file is parsed. The URLs expire together, so the first
 * fetch failure refetches the parts once and every failing part retries with its fresh URL. A file whose worker died
 * fails, so the load still settles and names it. Cancelling aborts the downloads still running and drops the answers
 * still to come.
 */
export function decode(parts: Issued[], refetch: () => Promise<PartEntry[]>, { onPart, onError }: Decoding): Pool {
  const byId = new Map(parts.map((p) => [p.id, p]));
  const aborted = new AbortController();
  const decoders = open((id, reply, message) => {
    const part = byId.get(id)!;
    if (!reply) onError(part, message ?? 'the decoding worker stopped');
    else if (reply.type === 'error') onError(part, reply.message);
    else {
      remember(part.cadFile, reply.meshes);
      onPart(part, reply.meshes);
    }
  });
  let fresh: Promise<Map<string, string | null>> | null = null;
  const freshUrl = (id: string) => {
    fresh ??= refetch().then((list) => new Map(list.filter(isPart).map((p) => [p.id, p.cadUrl])));
    return fresh.then((m) => m.get(id) ?? null);
  };
  void Promise.all(parts.map(async (part) => {
    try {
      let buffer: ArrayBuffer;
      try {
        buffer = await getCad(part.cadUrl, aborted.signal);
      } catch (first) {
        if (aborted.signal.aborted) return;
        const url = await freshUrl(part.id);
        if (!url) throw first;
        buffer = await getCad(url, aborted.signal);
      }
      if (!aborted.signal.aborted) decoders.post(part.id, buffer);
    } catch (e) {
      if (!aborted.signal.aborted) onError(part, e instanceof Error ? e.message : String(e));
    }
  }));
  return {
    cancel: () => {
      aborted.abort();
      decoders.cancel();
    },
  };
}
