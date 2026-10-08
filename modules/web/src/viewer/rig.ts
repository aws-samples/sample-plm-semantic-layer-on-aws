// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The main camera and its orbit controls: framing on points or on a sphere from the isometric
// direction, eased moves, and the projection shift that keeps a framed joint clear of the detail view.
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { ISO_DIR, frame, framePoints, tween } from './framing';

type Pose = { position: THREE.Vector3; target: THREE.Vector3 };

export class Rig {
  readonly camera = new THREE.PerspectiveCamera(32, 1, 10, 1e6);
  readonly controls: OrbitControls;
  private stopTween = () => {};
  private loupeShift = false;

  constructor(canvas: HTMLCanvasElement, private readonly host: HTMLElement, private readonly changed: () => void) {
    // Product frames are z-up; three.js defaults to y-up.
    this.camera.up.set(0, 0, 1);
    this.controls = new OrbitControls(this.camera, canvas);
    this.controls.enableDamping = false;
    this.controls.addEventListener('change', changed);
    this.controls.addEventListener('start', () => this.stopTween());
  }

  /** Frames every point (packed x, y, z); `animate` eases there, else the camera jumps. */
  framePoints(points: ArrayLike<number>, animate: boolean) {
    this.shiftForLoupe(false);
    const target = framePoints(this.camera, points, ISO_DIR);
    if (animate) this.moveTo(target);
    else {
      // A tween still running towards the parts drawn before would carry the camera away again.
      this.stopTween();
      this.apply(target.position, target.target);
    }
  }

  /** Eases to frame a sphere, shifted clear of the detail view when `loupe` is set. */
  frameSphere(centre: THREE.Vector3, radius: number, loupe: boolean) {
    this.shiftForLoupe(loupe);
    this.moveTo(frame(this.camera, centre, radius, ISO_DIR));
  }

  /** Moves the projection centre up and left so the framed features sit clear of the detail view. */
  private shiftForLoupe(on: boolean) {
    this.loupeShift = on;
    const { clientWidth: w, clientHeight: h } = this.host;
    if (on && w && h) this.camera.setViewOffset(w, h, Math.round(w * 0.17), Math.round(h * 0.1), w, h);
    else this.camera.clearViewOffset();
    this.changed();
  }

  private moveTo(to: Pose) {
    this.stopTween();
    this.stopTween = tween({ position: this.camera.position.clone(), target: this.controls.target.clone() }, to,
      (p, t) => this.apply(p, t));
  }

  private apply(position: THREE.Vector3, target: THREE.Vector3) {
    this.camera.position.copy(position);
    this.controls.target.copy(target);
    const dist = position.distanceTo(target);
    this.camera.near = Math.max(1, dist / 200);
    this.camera.far = dist * 20;
    this.camera.updateProjectionMatrix();
    this.controls.update();
    this.changed();
  }

  resize(w: number, h: number) {
    this.camera.aspect = w / h;
    this.shiftForLoupe(this.loupeShift);
    this.camera.updateProjectionMatrix();
  }

  dispose() {
    this.stopTween();
    this.controls.dispose();
  }
}
