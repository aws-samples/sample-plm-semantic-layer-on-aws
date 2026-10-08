// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The headless checks' view of the scene, on `window.viewerProbe` in development only
// (scripts/check-placements.mjs): the frames rendered, each part's occurrences, whether they are
// drawn and where their centres sit in the product frame, where on screen (client pixels) a click
// picks an occurrence, the geometries and textures the renderer holds, and the view commands.
import * as THREE from 'three';
import type { Drawing } from './instances';
import { pointOn } from './picking';

export interface ProbeSource {
  host: HTMLElement;
  camera: THREE.Camera;
  drawings: Map<string, Drawing>;
  solids: () => THREE.InstancedMesh[];
  frames: () => number;
  memory: () => { geometries: number; textures: number };
  isolate: (ids: string[], contextIds?: string[]) => void;
  zoom: (id: string) => void;
  clear: () => void;
}

export function exposeProbe(s: ProbeSource) {
  const toClient = (ndc: THREE.Vector2) => {
    const r = s.host.getBoundingClientRect();
    return { x: r.left + ((ndc.x + 1) / 2) * s.host.clientWidth, y: r.top + ((1 - ndc.y) / 2) * s.host.clientHeight };
  };
  Object.assign(window, {
    viewerProbe: {
      frames: s.frames,
      memory: s.memory,
      parts: () => Object.fromEntries([...s.drawings].map(([id, d]) => [id, { count: d.count, shown: d.solids.some((m) => m.visible) }])),
      centres: (id: string) => {
        const solid = s.drawings.get(id)?.solids[0];
        if (!solid) return null;
        solid.geometry.computeBoundingBox();
        const c = solid.geometry.boundingBox!.getCenter(new THREE.Vector3());
        const m = new THREE.Matrix4();
        return Array.from({ length: solid.count }, (_, i) => {
          solid.getMatrixAt(i, m);
          return c.clone().applyMatrix4(m).toArray();
        });
      },
      screenPoint: (id: string, occurrence: number) => {
        const d = s.drawings.get(id);
        const ndc = d ? pointOn(s.camera, d.solids, occurrence, s.solids()) : null;
        return ndc ? toClient(ndc) : null;
      },
      isolate: s.isolate,
      zoom: s.zoom,
      clear: s.clear,
    },
  });
}
