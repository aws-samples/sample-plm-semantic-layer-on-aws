// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// A part's paint under the current view, applied to every occurrence at once: the occurrences of a
// mesh share one material, so the view changes the material and never an instance.
import * as THREE from 'three';
import type { Drawing } from './instances';
import { disposeOutline, outlineOf } from './outline';

/** Opacity of a faded part, a context part or a translucent skin. */
export const FADED = 0.28;
/** Opacity of a part outside the lens: still in place, read as a ghost behind the parts inside. */
const LENS_OPACITY = 0.1;
const HOVER_GLOW = 0x2c2c2c;

export interface Viewed {
  /** Drawn at all: false outside an isolated set. */
  shown: boolean;
  /** Faded as context of an isolated set, full as one of its parts, null for the part's own paint. */
  faded: boolean | null;
  /** Outside the lens: a ghost whatever the view, drawn behind the parts inside it. */
  out: boolean;
}

export function paintDrawing(d: Drawing, { shown, faded, out }: Viewed) {
  const own = d.look.translucent ? FADED : 1;
  const viewed = faded === null ? own : faded ? FADED : 1;
  const opacity = out ? Math.min(viewed, LENS_OPACITY) : viewed;
  for (const mesh of d.solids) {
    const material = mesh.material as THREE.MeshStandardMaterial;
    if (material.transparent !== opacity < 1) material.needsUpdate = true;
    mesh.visible = shown;
    material.opacity = opacity;
    material.transparent = opacity < 1;
    material.depthWrite = opacity === 1;
    mesh.renderOrder = out ? 3 : opacity < 1 ? 2 : 1;
  }
  const edgeViewed = faded ? Math.min(d.look.edgeOpacity, 0.55) : d.look.edgeOpacity;
  for (const edges of d.edges) {
    edges.visible = shown;
    (edges.material as THREE.LineBasicMaterial).opacity = out ? Math.min(edgeViewed, LENS_OPACITY) : edgeViewed;
    edges.renderOrder = out ? 3 : 0;
  }
}

/** The part under the pointer glows at every occurrence. */
export function glow(d: Drawing | undefined, on: boolean) {
  for (const m of d?.solids ?? []) (m.material as THREE.MeshStandardMaterial).emissive.setHex(on ? HOVER_GLOW : 0);
}

/** The outlines drawn, by part id, in the group the parts are drawn in. */
export class Outlines {
  private readonly drawn = new Map<string, THREE.Object3D[]>();

  constructor(private readonly group: THREE.Group) {}

  /** Outlines every occurrence of the part in `colour`; null removes its outline. */
  set(id: string, d: Drawing | undefined, colour: number | null) {
    const old = this.drawn.get(id);
    if (old) {
      this.group.remove(...old);
      disposeOutline(old);
      this.drawn.delete(id);
    }
    if (colour === null || !d) return;
    const objects = d.solids.flatMap((m) => outlineOf(m, colour));
    if (objects.length === 0) return;
    this.group.add(...objects);
    this.drawn.set(id, objects);
  }
}
