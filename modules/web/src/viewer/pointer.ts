// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The pointer over the view: the occurrence under it while no button is held, and a click, a press
// and release close together, on an occurrence. Interface markers of the overlay take their own clicks.
import * as THREE from 'three';
import type { Hit } from './picking';

/** A click is a press and release this close together, in CSS pixels; anything longer orbits the view. */
const CLICK_SLOP = 5;

export type Hover = Hit & { x: number; y: number };

export interface PointerHandlers {
  /** The occurrence at normalised device coordinates, or null. */
  pick: (ndc: THREE.Vector2) => Hit | null;
  onHover: (hover: Hover | null) => void;
  onClick: (hit: Hit) => void;
}

export function listenPointer(host: HTMLElement, { pick, onHover, onClick }: PointerHandlers) {
  let press: { x: number; y: number } | null = null;
  const onMarker = (e: Event) => (e.target as Element).closest('[data-interface]') !== null;
  const hitAt = (e: PointerEvent) => {
    const r = host.getBoundingClientRect();
    return pick(new THREE.Vector2(((e.clientX - r.left) / host.clientWidth) * 2 - 1, -((e.clientY - r.top) / host.clientHeight) * 2 + 1));
  };
  host.addEventListener('pointermove', (e) => {
    if (e.buttons !== 0 || onMarker(e)) {
      onHover(null);
      return;
    }
    const r = host.getBoundingClientRect();
    const hit = hitAt(e);
    onHover(hit ? { ...hit, x: e.clientX - r.left, y: e.clientY - r.top } : null);
  });
  host.addEventListener('pointerleave', () => onHover(null));
  host.addEventListener('pointerdown', (e) => {
    press = e.button === 0 ? { x: e.clientX, y: e.clientY } : null;
  });
  host.addEventListener('pointerup', (e) => {
    const start = press;
    press = null;
    if (!start || Math.hypot(e.clientX - start.x, e.clientY - start.y) > CLICK_SLOP || onMarker(e)) return;
    const hit = hitAt(e);
    if (hit) onClick(hit);
  });
}
