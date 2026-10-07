// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The detail view: the selected dimension seen square to its axis by an orthographic camera,
// rendered into the loupe's rectangle of the canvas, and its drafting overlay.
import * as THREE from 'three';
import type { Vec3 } from '../api/types';
import { sideOn, toV } from './framing';
import { drawLoupe, type Dimension, type Marker, type Project } from './overlay';

export interface Rect {
  left: number;
  top: number;
  width: number;
  height: number;
}

export const mid = (d: Dimension): Vec3 => ({ x: (d.a.x + d.b.x) / 2, y: (d.a.y + d.b.y) / 2, z: (d.a.z + d.b.z) / 2 });
const samePoint = (a: Vec3, b: Vec3) => a.x === b.x && a.y === b.y && a.z === b.z;

/** Screen position of a point seen by `camera` in the viewport at (ox, oy) of size w x h, in host pixels. */
export function projector(camera: THREE.Camera, ox: number, oy: number, w: number, h: number): Project {
  const v = new THREE.Vector3();
  return (p) => {
    v.set(p.x, p.y, p.z).project(camera);
    return { x: ox + ((v.x + 1) / 2) * w, y: oy + ((1 - v.y) / 2) * h, front: v.z > -1 && v.z < 1 };
  };
}

/** Renders the detail of `dim` into `rect` (host pixels of a host `h` high) and returns its overlay. */
export function renderLoupe(renderer: THREE.WebGLRenderer, scene: THREE.Scene, detail: THREE.OrthographicCamera,
  rect: Rect, h: number, dim: Dimension, markers: Marker[]): SVGElement[] {
  const centre = toV(mid(dim));
  const fieldMm = Math.max(dim.deltaMm * 4.2, (dim.toleranceMm ?? 0) * 5, 12);
  const aspect = rect.height / rect.width;
  detail.left = -fieldMm / 2;
  detail.right = fieldMm / 2;
  detail.top = (fieldMm * aspect) / 2;
  detail.bottom = -(fieldMm * aspect) / 2;
  detail.position.copy(centre).addScaledVector(sideOn(dim.axis), 1500);
  detail.near = 1;
  detail.far = 3000;
  detail.lookAt(centre);
  detail.updateProjectionMatrix();

  const y = h - rect.top - rect.height;
  renderer.setScissorTest(true);
  renderer.setScissor(rect.left, y, rect.width, rect.height);
  renderer.setViewport(rect.left, y, rect.width, rect.height);
  renderer.setClearColor(0xf3f5f6, 1);
  renderer.clear();
  renderer.render(scene, detail);
  renderer.setClearColor(0x000000, 0);
  renderer.setScissorTest(false);

  const project = projector(detail, rect.left, rect.top, rect.width, rect.height);
  const pair = markers.filter((m) => m.emphasis === 'selected' && (samePoint(m.pos, dim.a) || samePoint(m.pos, dim.b)));
  return drawLoupe({ rect, project, fieldMm }, dim, pair);
}
