// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Parses STEP AP242 files with occt-import-js (WASM) off the main thread.
import occtimportjs from 'occt-import-js';
import wasmUrl from 'occt-import-js/dist/occt-import-js.wasm?url';

export interface ParsedMesh {
  position: Float32Array;
  normal: Float32Array | null;
  index: Uint32Array;
}

export type WorkerReply =
  | { type: 'part'; id: string; meshes: ParsedMesh[] }
  | { type: 'error'; id: string; message: string };

const occtReady = occtimportjs({ locateFile: () => wasmUrl });

const PARAMS = {
  linearUnit: 'millimeter',
  linearDeflectionType: 'bounding_box_ratio',
  linearDeflection: 0.001,
  angularDeflection: 0.5,
};

self.onmessage = async ({ data: { load, id, buffer } }: MessageEvent<{ load: number; id: string; buffer: ArrayBuffer }>) => {
  try {
    const occt = await occtReady;
    const result = occt.ReadStepFile(new Uint8Array(buffer), PARAMS);
    if (!result.success) throw new Error('the STEP file could not be parsed');
    const meshes: ParsedMesh[] = result.meshes.map((m) => ({
      position: new Float32Array(m.attributes.position.array),
      normal: m.attributes.normal ? new Float32Array(m.attributes.normal.array) : null,
      index: new Uint32Array(m.index.array),
    }));
    const transfer = meshes.flatMap((m) => [m.position.buffer, m.index.buffer, ...(m.normal ? [m.normal.buffer] : [])]);
    (self as unknown as Worker).postMessage({ type: 'part', load, id, meshes } satisfies WorkerReply & { load: number }, transfer);
  } catch (e) {
    (self as unknown as Worker).postMessage({ type: 'error', load, id, message: e instanceof Error ? e.message : String(e) } satisfies WorkerReply & { load: number });
  }
};
