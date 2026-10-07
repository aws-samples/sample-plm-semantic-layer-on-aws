// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import * as THREE from 'three';
import type { Vec3 } from '../api/types';

export const ISO_DIR = new THREE.Vector3(-0.55, -0.78, 0.42).normalize();

export const toV = (p: Vec3) => new THREE.Vector3(p.x, p.y, p.z);

/** Camera position and target that frame a sphere of `radius` around `centre` from `dir`. */
export function frame(camera: THREE.PerspectiveCamera, centre: THREE.Vector3, radius: number, dir = ISO_DIR) {
  const halfFov = THREE.MathUtils.degToRad(camera.fov / 2);
  const fit = Math.min(halfFov, Math.atan(Math.tan(halfFov) * camera.aspect));
  const dist = radius / Math.sin(fit);
  return { position: centre.clone().addScaledVector(dir, dist), target: centre.clone() };
}

/**
 * Camera position and target from `dir` that keep every point in view: the target is the centre
 * of the points' extent across the view, and the distance the largest any point needs to sit
 * inside the horizontal and vertical half-angles. Points are packed x, y, z.
 */
export function framePoints(camera: THREE.PerspectiveCamera, points: ArrayLike<number>, dir = ISO_DIR, margin = 1.12) {
  const forward = dir.clone().negate();
  const right = new THREE.Vector3().crossVectors(forward, new THREE.Vector3(0, 0, 1)).normalize();
  const up = new THREE.Vector3().crossVectors(right, forward).normalize();
  const tanV = Math.tan(THREE.MathUtils.degToRad(camera.fov / 2));
  const tanH = tanV * camera.aspect;
  const p = new THREE.Vector3();
  let minR = Infinity, maxR = -Infinity, minU = Infinity, maxU = -Infinity;
  const centre = new THREE.Vector3();
  const n = points.length / 3;
  for (let i = 0; i < points.length; i += 3) {
    p.set(points[i], points[i + 1], points[i + 2]);
    centre.add(p);
    const r = p.dot(right);
    const u = p.dot(up);
    minR = Math.min(minR, r); maxR = Math.max(maxR, r); minU = Math.min(minU, u); maxU = Math.max(maxU, u);
  }
  centre.divideScalar(n || 1);
  // Move the target across the view so the extent is centred, keeping its depth.
  centre.addScaledVector(right, (minR + maxR) / 2 - centre.dot(right)).addScaledVector(up, (minU + maxU) / 2 - centre.dot(up));
  let dist = 0;
  for (let i = 0; i < points.length; i += 3) {
    p.set(points[i], points[i + 1], points[i + 2]).sub(centre);
    const along = p.dot(dir);
    dist = Math.max(dist, (Math.abs(p.dot(right)) * margin) / tanH + along, (Math.abs(p.dot(up)) * margin) / tanV + along);
  }
  return { position: centre.clone().addScaledVector(dir, dist), target: centre };
}

/** The eight corners of a box, packed x, y, z. */
export function corners(box: THREE.Box3): Float32Array {
  const out = new Float32Array(24);
  let k = 0;
  for (const x of [box.min.x, box.max.x]) for (const y of [box.min.y, box.max.y]) for (const z of [box.min.z, box.max.z]) {
    out.set([x, y, z], k);
    k += 3;
  }
  return out;
}

/** A viewing direction perpendicular to `axis`, looking slightly down, for a true-length dimension. */
export function sideOn(axis: 'x' | 'y' | 'z'): THREE.Vector3 {
  if (axis === 'x') return new THREE.Vector3(0, -1, 0.25).normalize();
  if (axis === 'y') return new THREE.Vector3(-1, 0, 0.25).normalize();
  return new THREE.Vector3(-0.3, -1, 0).normalize();
}

/** Eased tween of camera position and orbit target; resolves immediately when motion is reduced. */
export function tween(
  from: { position: THREE.Vector3; target: THREE.Vector3 },
  to: { position: THREE.Vector3; target: THREE.Vector3 },
  apply: (position: THREE.Vector3, target: THREE.Vector3) => void,
  ms = 750,
): () => void {
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (reduced) {
    apply(to.position, to.target);
    return () => {};
  }
  const t0 = performance.now();
  let raf = 0;
  const p = new THREE.Vector3();
  const t = new THREE.Vector3();
  const step = (now: number) => {
    const k = Math.min(1, (now - t0) / ms);
    const e = 1 - Math.pow(1 - k, 3);
    apply(p.lerpVectors(from.position, to.position, e), t.lerpVectors(from.target, to.target, e));
    if (k < 1) raf = requestAnimationFrame(step);
  };
  raf = requestAnimationFrame(step);
  return () => cancelAnimationFrame(raf);
}
