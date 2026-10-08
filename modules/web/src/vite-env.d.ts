// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_FIXTURES?: string;
}

declare module 'occt-import-js' {
  interface OcctMesh {
    name: string;
    attributes: { position: { array: number[] }; normal?: { array: number[] } };
    index: { array: number[] };
  }
  interface OcctResult {
    success: boolean;
    root: { name: string; children: { name: string }[] };
    meshes: OcctMesh[];
  }
  interface Occt {
    ReadStepFile(buffer: Uint8Array, params: Record<string, unknown> | null): OcctResult;
  }
  export default function occtimportjs(opts: { locateFile: (name: string) => string }): Promise<Occt>;
}
