// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The STEP decoding workers, started on the first load and shared by every load of every scene for the page's life:
// each worker compiles the 7 MB occt WebAssembly module and grows its own heap, which a pool per load would do again on
// every product or option switch. A worker that dies after answering is replaced and the files it held fail; a worker
// that dies before its first answer means the decoder cannot start, and every file fails with the reason.
import type { WorkerReply } from './stepWorker';

/** Workers that tessellate in parallel: one core is left to the page, and past four the downloads, not the parses, pace a load. */
const SIZE = Math.min(4, Math.max(1, (navigator.hardwareConcurrency || 2) - 1));

/** What a load hears back for each of its files: the worker's reply, or null with the reason when the worker died. */
export type Listener = (id: string, reply: WorkerReply | null, reason?: string) => void;

interface Slot {
  worker: Worker;
  /** `load|id` of each file posted and not answered yet, a cancelled load's included: the worker still parses them. */
  waiting: Set<string>;
  answered: boolean;
}

const slots: Slot[] = [];
const listeners = new Map<number, Listener>();
let loads = 0;
/** Why the decoder cannot start, once a worker has died before its first answer. */
let broken: string | null = null;

const keyOf = (load: number, id: string) => `${load}|${id}`;

function start(): Slot {
  const slot: Slot = { worker: new Worker(new URL('./stepWorker.ts', import.meta.url), { type: 'module' }), waiting: new Set(), answered: false };
  slot.worker.onmessage = ({ data }: MessageEvent<WorkerReply & { load: number }>) => {
    slot.answered = true;
    slot.waiting.delete(keyOf(data.load, data.id));
    listeners.get(data.load)?.(data.id, data);
  };
  slot.worker.onerror = (e) => {
    e.preventDefault();
    slot.worker.terminate();
    const reason = `the decoding worker stopped: ${e.message || 'no message'}`;
    if (slot.answered) slots[slots.indexOf(slot)] = start();
    else {
      broken = reason;
      slots.splice(slots.indexOf(slot), 1);
    }
    for (const key of slot.waiting) {
      const bar = key.indexOf('|');
      listeners.get(Number(key.slice(0, bar)))?.(key.slice(bar + 1), null, reason);
    }
  };
  return slot;
}

/** A new load: the files it posts are answered to `listener` until it is cancelled. */
export function open(listener: Listener): { post: (id: string, buffer: ArrayBuffer) => void; cancel: () => void } {
  const load = ++loads;
  listeners.set(load, listener);
  return {
    post: (id, buffer) => {
      if (broken) return listener(id, null, broken);
      while (slots.length < SIZE) slots.push(start());
      const slot = slots.reduce((a, b) => (b.waiting.size < a.waiting.size ? b : a));
      slot.waiting.add(keyOf(load, id));
      slot.worker.postMessage({ load, id, buffer }, [buffer]);
    },
    cancel: () => listeners.delete(load),
  };
}
