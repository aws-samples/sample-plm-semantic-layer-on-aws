// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Outlines drawn around a part: the part's back faces pushed outwards by a fixed number of screen
// pixels, so a bolt and a nacelle get the same line. A white band sits between the part and the
// coloured line, which keeps the line readable on a part painted in the same colour. Every
// occurrence of the part is outlined.
import * as THREE from 'three';
import { mergeVertices } from 'three/addons/utils/BufferGeometryUtils.js';
import FRAGMENT from './outline.frag.glsl?raw';
import VERTEX from './outline.vert.glsl?raw';

/** Size of the drawing buffer in CSS pixels; the scene sets it on every resize. */
export const outlineResolution = { value: new THREE.Vector2(1, 1) };

/** Outer edge of the coloured line and of the white band, in CSS pixels. */
const LINE_PX = 4.5;
const BAND_PX = 1.5;

/** A copy of the part at each of its occurrences: the outline objects share the part's instance matrices. */
function instanced(geometry: THREE.BufferGeometry, material: THREE.Material, of: THREE.InstancedMesh): THREE.InstancedMesh {
  const mesh = new THREE.InstancedMesh(geometry, material, of.count);
  mesh.instanceMatrix = of.instanceMatrix;
  return mesh;
}

function hull(geometry: THREE.BufferGeometry, colour: number, width: number, order: number, of: THREE.InstancedMesh): THREE.Mesh {
  const material = new THREE.ShaderMaterial({
    uniforms: { colour: { value: new THREE.Color(colour) }, width: { value: width }, resolution: outlineResolution },
    vertexShader: VERTEX,
    fragmentShader: FRAGMENT,
    side: THREE.BackSide,
    depthWrite: false,
  });
  const mesh = instanced(geometry, material, of);
  mesh.renderOrder = order;
  return mesh;
}

/**
 * The objects that outline one mesh: a depth-only copy of the part (so a translucent skin does not
 * show the line through itself), the white band, then the coloured line. STEP tessellations split
 * vertices at every edge; the hull welds them first so the line has no notch at a corner.
 */
export function outlineOf(mesh: THREE.InstancedMesh, colour: number): THREE.Object3D[] {
  const welded = mesh.geometry.clone();
  welded.deleteAttribute('normal');
  const smooth = mergeVertices(welded);
  smooth.computeVertexNormals();
  const mask = instanced(mesh.geometry, new THREE.MeshBasicMaterial({ colorWrite: false }), mesh);
  mask.renderOrder = 3;
  return [mask, hull(smooth, colour, LINE_PX, 4, mesh), hull(smooth, 0xffffff, BAND_PX, 5, mesh)];
}

export function disposeOutline(objects: THREE.Object3D[]) {
  objects.forEach((o, i) => {
    const m = o as THREE.Mesh;
    (m.material as THREE.Material).dispose();
    // The mask shares the part's geometry; the two hulls share the welded copy.
    if (i === 1) m.geometry.dispose();
  });
}
