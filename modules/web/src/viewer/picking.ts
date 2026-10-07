// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Which occurrence of which part lies under a point of the view.
import * as THREE from 'three';

export interface Hit {
  id: string;
  /** 1-based; 1 is the reference occurrence, the STEP as authored. */
  occurrence: number;
  count: number;
}

const raycaster = new THREE.Raycaster();

/** The nearest drawn occurrence at `ndc` (normalised device coordinates), or null. */
export function pickAt(camera: THREE.Camera, ndc: THREE.Vector2, solids: THREE.InstancedMesh[]): Hit | null {
  raycaster.setFromCamera(ndc, camera);
  const hit = raycaster.intersectObjects(solids, false)[0];
  if (!hit || hit.instanceId === undefined) return null;
  const mesh = hit.object as THREE.InstancedMesh;
  return { id: mesh.name, occurrence: hit.instanceId + 1, count: mesh.count };
}

/**
 * A point of the view, in normalised device coordinates, where a pick answers this occurrence of the
 * part: the centre of one of its triangles that nothing else hides; null when every one is hidden.
 */
export function pointOn(camera: THREE.Camera, part: THREE.InstancedMesh[], occurrence: number, solids: THREE.InstancedMesh[]): THREE.Vector2 | null {
  const m = new THREE.Matrix4();
  const tri = new THREE.Triangle();
  const c = new THREE.Vector3();
  for (const mesh of part) {
    mesh.getMatrixAt(occurrence - 1, m);
    const pos = mesh.geometry.getAttribute('position');
    const index = mesh.geometry.index!;
    const step = Math.max(1, Math.floor(index.count / 3 / 400)) * 3;
    for (let i = 0; i + 2 < index.count; i += step) {
      tri.setFromAttributeAndIndices(pos, index.getX(i), index.getX(i + 1), index.getX(i + 2)).getMidpoint(c);
      c.applyMatrix4(m).project(camera);
      if (Math.abs(c.x) > 0.98 || Math.abs(c.y) > 0.98 || c.z < -1 || c.z > 1) continue;
      const ndc = new THREE.Vector2(c.x, c.y);
      const hit = pickAt(camera, ndc, solids);
      if (hit?.id === mesh.name && hit.occurrence === occurrence) return ndc;
    }
  }
  return null;
}
