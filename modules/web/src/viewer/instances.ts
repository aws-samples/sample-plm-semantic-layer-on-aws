// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// A part drawn at every one of its occurrences: its STEP decoded once, each of the file's meshes one
// geometry drawn N times. The solids are THREE.InstancedMesh, one draw call per mesh whatever N is.
// The edges are LineSegments over an InstancedBufferGeometry whose per-instance attribute carries the
// placement: the edge vertices are uploaded once and drawn N times in one call, where baking N copies
// into a merged buffer would multiply the memory and the upload by N (a gear of the difference engine
// sits at over 300 places) and a LineSegments per occurrence would cost a draw call each.
import * as THREE from 'three';
import type { Placement, Placements } from '../api/types';
import type { ParsedMesh } from './stepWorker';

const DEG = Math.PI / 180;
const ONE = new THREE.Vector3(1, 1, 1);

/** p' = Rz.Ry.Rx.p + t, the placement convention of the answer; the identity is the STEP as authored. */
export function matrixOf([x, y, z, rx, ry, rz]: Placement): THREE.Matrix4 {
  const q = new THREE.Quaternion().setFromEuler(new THREE.Euler(rx * DEG, ry * DEG, rz * DEG, 'ZYX'));
  return new THREE.Matrix4().compose(new THREE.Vector3(x, y, z), q, ONE);
}

/** The occurrences of each part the answer lists; a part it does not list draws once, as authored. */
export const occurrencesById = (answer: Placements | null) =>
  new Map((answer?.parts ?? []).map((p) => [p.id, p.occurrences]));

/** How a part is painted, before any view changes it. */
export interface Look {
  colour: number;
  edge: number;
  translucent: boolean;
  /** Opacity of the edges in the part's own paint. */
  edgeOpacity: number;
}

export interface Drawing {
  solids: THREE.InstancedMesh[];
  edges: THREE.LineSegments[];
  count: number;
  /** The placements the drawing was built for, to tell a changed list from the same one. */
  key: string;
  look: Look;
}

/** Edges read their placement from an instanced attribute; the stock line shader does the rest. */
function placeEdges(shader: THREE.WebGLProgramParametersWithUniforms) {
  shader.vertexShader = `attribute mat4 placement;\n${shader.vertexShader}`.replace(
    '#include <begin_vertex>', '#include <begin_vertex>\n  transformed = (placement * vec4(transformed, 1.0)).xyz;');
}

export function buildDrawing(id: string, meshes: ParsedMesh[], occurrences: Placement[] | undefined, look: Look): Drawing {
  const matrices = (occurrences ?? [[0, 0, 0, 0, 0, 0]]).map(matrixOf);
  const count = matrices.length;
  const packed = new Float32Array(count * 16);
  matrices.forEach((m, i) => m.toArray(packed, i * 16));
  const solids: THREE.InstancedMesh[] = [];
  const edges: THREE.LineSegments[] = [];
  for (const m of meshes) {
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.BufferAttribute(m.position, 3));
    g.setIndex(new THREE.BufferAttribute(m.index, 1));
    if (m.normal) g.setAttribute('normal', new THREE.BufferAttribute(m.normal, 3));
    else g.computeVertexNormals();
    const solid = new THREE.InstancedMesh(g, new THREE.MeshStandardMaterial({
      color: look.colour, roughness: 0.62, metalness: 0.05, side: THREE.DoubleSide,
      transparent: look.translucent, opacity: look.translucent ? 0.28 : 1, depthWrite: !look.translucent,
    }), count);
    solid.instanceMatrix.array.set(packed);
    solid.instanceMatrix.needsUpdate = true;
    solid.computeBoundingSphere();
    const lines = new THREE.InstancedBufferGeometry();
    lines.setAttribute('position', new THREE.EdgesGeometry(g, 35).getAttribute('position'));
    lines.setAttribute('placement', new THREE.InstancedBufferAttribute(packed, 16));
    lines.instanceCount = count;
    // Culling tests the bounding sphere, which must hold every occurrence, not the authored one only.
    lines.boundingSphere = solid.boundingSphere!.clone();
    const material = new THREE.LineBasicMaterial({ color: look.edge, transparent: true, opacity: look.edgeOpacity });
    material.onBeforeCompile = placeEdges;
    const edge = new THREE.LineSegments(lines, material);
    solid.name = edge.name = id;
    solids.push(solid);
    edges.push(edge);
  }
  return { solids, edges, count, key: JSON.stringify(occurrences ?? null), look };
}

export function disposeDrawing(d: Drawing) {
  for (const o of [...d.solids, ...d.edges]) {
    o.geometry.dispose();
    (o.material as THREE.Material).dispose();
  }
}

/**
 * A sample of the drawing's vertices at every occurrence, packed x, y, z: about 2000 per mesh, spread
 * over the occurrences, and at least 8 per occurrence so a far copy of a small part still counts.
 */
export function sample(d: Drawing): Float32Array {
  const chunks: Float32Array[] = [];
  const m = new THREE.Matrix4();
  const v = new THREE.Vector3();
  for (const solid of d.solids) {
    const pos = solid.geometry.getAttribute('position').array as Float32Array;
    const n = pos.length / 3;
    const per = Math.min(n, Math.max(8, Math.floor(2000 / d.count)));
    const stride = Math.max(1, Math.floor(n / per)) * 3;
    const k = Math.ceil(pos.length / stride);
    const out = new Float32Array(k * 3 * d.count);
    let o = 0;
    for (let i = 0; i < d.count; i++) {
      solid.getMatrixAt(i, m);
      for (let j = 0; j < pos.length; j += stride, o += 3) v.fromArray(pos, j).applyMatrix4(m).toArray(out, o);
    }
    chunks.push(out);
  }
  const all = new Float32Array(chunks.reduce((n, c) => n + c.length, 0));
  let k = 0;
  for (const c of chunks) { all.set(c, k); k += c.length; }
  return all;
}
