// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// three.js scene of the product: each visible part's STEP file drawn at every occurrence of the
// placements answer (instances.ts) and painted by owner and view (parts.ts), features drawn by the
// SVG overlay from API positions on the reference occurrence, the occurrence under the pointer
// labelled, and an optional orthographic detail view rendered into a second viewport (loupe.ts). The
// agent and the person drive what is drawn: parts outlined in their owner's colour, a set isolated
// with its context faded, the view fitted to one part, the part the person clicked outlined in ink.
// The Paths screen isolates a path's parts the same way, with the rest of the product as their faded context.
import * as THREE from 'three';
import type { Part, PartEntry, Placements, Vec3 } from '../api/types';
import { builtBy } from '../api/types';
import { SUPPLIER_CSS, cssColour, plmCode } from '../ui/plm';
import { toV } from './framing';
import { occurrencesById } from './instances';
import { decode, type Pool, issued, recall } from './loading';
import { mid, projector, renderLoupe } from './loupe';
import { defs, drawMain, drawPartTag, el, type Dimension, type Marker } from './overlay';
import { outlineResolution } from './outline';
import { glow } from './paint';
import { DrawnParts } from './parts';
import { pickAt, type Hit } from './picking';
import { listenPointer, type Hover } from './pointer';
import { exposeProbe } from './probe';
import { Rig } from './rig';

export interface LoadProgress {
  loaded: number;
  total: number;
  failed: { cadFile: string; message: string }[];
}

export interface Focus {
  points: Vec3[];
  dimension: Dimension | null;
}

export class ProductScene {
  readonly canvas = document.createElement('canvas');
  private readonly svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  private readonly renderer = new THREE.WebGLRenderer({ canvas: this.canvas, antialias: true, alpha: true });
  private readonly scene = new THREE.Scene();
  private readonly detail = new THREE.OrthographicCamera(-1, 1, 1, -1, 1, 4000);
  private readonly rig: Rig;
  private readonly product = new THREE.Group();
  private readonly parts: DrawnParts;
  private decoding: Pool | null = null;
  /** Occurrence under the pointer and where the pointer is, in host pixels. */
  private hover: Hover | null = null;
  private markers: Marker[] = [];
  private focusState: Focus | null = null;
  private loupeEl: HTMLElement | null = null;
  private frameQueued = false;
  /** Frames rendered, which the headless frame-rate check counts. */
  private frames = 0;
  private readonly resizeObserver: ResizeObserver;

  onPlugClick: (interfaceId: string) => void = () => {};
  /** A click on a drawn part, with the occurrence clicked. */
  onPartClick: (hit: Hit) => void = () => {};

  constructor(private readonly host: HTMLElement) {
    this.canvas.className = 'viewer-canvas';
    this.svg.setAttribute('class', 'viewer-overlay');
    host.append(this.canvas, this.svg);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.setClearColor(0x000000, 0);
    this.renderer.autoClear = false;

    this.scene.add(new THREE.HemisphereLight(0xffffff, 0x7d8a96, 1.7));
    const sun = new THREE.DirectionalLight(0xffffff, 1.6);
    sun.position.set(-0.4, -0.7, 1);
    this.scene.add(sun, this.product);
    this.detail.up.set(0, 0, 1);
    this.parts = new DrawnParts(this.product, host);
    this.rig = new Rig(this.canvas, host, () => this.requestFrame());

    this.svg.addEventListener('click', (e) => {
      const g = (e.target as Element).closest('[data-interface]');
      if (g) this.onPlugClick(g.getAttribute('data-interface')!);
    });
    listenPointer(host, {
      pick: (ndc) => pickAt(this.rig.camera, ndc, this.parts.solids()),
      onHover: (h) => this.setHover(h),
      onClick: (hit) => this.onPartClick(hit),
    });
    this.resizeObserver = new ResizeObserver(() => this.resize());
    this.resizeObserver.observe(host);
    this.resize();
    this.home(false);
    if (import.meta.env.DEV) {
      exposeProbe({
        host, camera: this.rig.camera, drawings: this.parts.drawings, solids: () => this.parts.solids(), frames: () => this.frames,
        memory: () => ({ ...this.renderer.info.memory }),
        isolate: (ids, context) => this.isolateParts(ids, context), zoom: (id) => this.zoomToPart(id), clear: () => this.clearView(),
      });
    }
  }

  setLoupeElement(el: HTMLElement | null) {
    this.loupeEl = el;
    this.requestFrame();
  }

  /**
   * Loads the parts that come with a presigned URL, each at its occurrences in `placements` (null:
   * every part once, as authored); redacted parts and parts without a published file are skipped.
   * Parts already drawn stay, parts gone from the answer are removed and only new ones are fetched,
   * so a part published while the scene is up appears where it belongs and the camera moves only when
   * the first parts arrive or parts disappear. A part that turns context, stops being context or moves
   * is drawn again in its new paint and places. A new part whose file was decoded before, by this
   * scene or an earlier one, is drawn from memory and counts as loaded.
   */
  loadParts(parts: PartEntry[], placements: Placements | null, onProgress: (p: LoadProgress) => void, refetch: () => Promise<PartEntry[]>) {
    this.decoding?.cancel();
    this.setHover(null);
    const files = parts.filter(issued);
    const byId = new Map(files.map((p) => [p.id, p]));
    this.parts.occurrences = occurrencesById(placements);
    const gone = this.parts.stale(byId);
    this.parts.partsById = byId;
    for (const id of gone) this.parts.remove(id);
    const reframe = gone.length > 0 || this.parts.drawings.size === 0;
    const todo = files.filter((p) => !this.parts.drawings.has(p.id));
    const settle = () => {
      if (!reframe) this.requestFrame();
      else if (this.focusState) this.focus(this.focusState);
      else if (this.parts.isolated) this.fit(this.parts.isolated.ids);
      else this.home(false);
    };
    const progress: LoadProgress = { loaded: 0, total: todo.length, failed: [] };
    const missing = todo.filter((part) => {
      const meshes = recall(part.cadFile);
      if (!meshes) return true;
      this.parts.add(part, meshes);
      progress.loaded++;
      return false;
    });
    onProgress(progress);
    if (missing.length === 0) {
      settle();
      return;
    }
    this.requestFrame();
    const done = () => {
      onProgress({ ...progress, failed: [...progress.failed] });
      if (progress.loaded + progress.failed.length === progress.total) settle();
      this.requestFrame();
    };
    this.decoding = decode(missing, refetch, {
      onPart: (part, meshes) => {
        this.parts.add(part, meshes);
        progress.loaded++;
        done();
      },
      onError: (part, message) => {
        progress.failed.push({ cadFile: part.cadFile, message });
        done();
      },
    });
  }

  /** Fades the parts outside the lens, null for none. */
  setLens(inside: ((part: Part) => boolean) | null) {
    this.parts.lens = inside;
    this.repaint();
    this.parts.publish();
  }

  /** Outlines these parts in their owner's colour; an empty list removes the outlines. */
  highlightParts(ids: string[]) {
    this.parts.highlighted = new Set(ids);
    this.repaint();
  }

  /** Draws only `ids` at full opacity and `contextIds` faded, hides the rest, and fits the view to `ids`. */
  isolateParts(ids: string[], contextIds: string[] = []) {
    this.parts.isolated = { ids: new Set(ids), context: new Set(contextIds) };
    this.repaint();
    this.fit(this.parts.isolated.ids);
  }

  /** Fits the view to every occurrence of one part. */
  zoomToPart(id: string) {
    this.fit(new Set([id]));
  }

  /** Back to the normal view of what is loaded: no outline, nothing isolated, everything in view. */
  clearView() {
    this.parts.highlighted = new Set();
    this.parts.isolated = null;
    this.repaint();
    this.home(true);
  }

  /** Outlines the part the person selected in ink; null clears it. */
  selectPart(id: string | null) {
    const prev = this.parts.selected;
    this.parts.selected = id;
    if (prev) this.parts.paint(prev);
    if (id) this.parts.paint(id);
    this.requestFrame();
  }

  private repaint() {
    this.parts.repaint();
    this.requestFrame();
  }

  /** Frames the parts of `ids` that are drawn; nothing happens while none of them is. */
  private fit(ids: Set<string>) {
    const points = this.parts.silhouette(ids);
    if (points.length === 0) return;
    this.focusState = null;
    this.rig.framePoints(points, true);
  }

  private setHover(next: Hover | null) {
    const prev = this.hover?.id ?? null;
    this.hover = next;
    if (prev !== (next?.id ?? null)) {
      glow(this.parts.drawings.get(prev ?? ''), false);
      glow(this.parts.drawings.get(next?.id ?? ''), true);
    }
    this.requestFrame();
  }

  private partTag(w: number): SVGElement | null {
    const part = this.hover ? this.parts.partsById.get(this.hover.id) : undefined;
    if (!part || !this.hover) return null;
    const owner = plmCode(part.plm);
    return drawPartTag({
      x: this.hover.x, y: this.hover.y, viewWidth: w, owner, name: part.name, supplier: part.supplier ? builtBy(part) : null,
      paint: part.supplier ? SUPPLIER_CSS : cssColour(owner), ownerColour: cssColour(owner),
      occurrence: this.hover.count > 1 ? { index: this.hover.occurrence, count: this.hover.count } : null,
    });
  }

  setMarkers(markers: Marker[]) {
    this.markers = markers;
    this.requestFrame();
  }

  /** Frames the camera on the focus points; null returns to the whole product. */
  focus(focus: Focus | null) {
    this.focusState = focus;
    if (!focus || focus.points.length === 0) {
      this.home(true);
      return;
    }
    const box = new THREE.Box3().setFromPoints(focus.points.map(toV));
    const centre = box.getCenter(new THREE.Vector3());
    // A joint is framed no tighter than a fifth of the product, so a selection keeps the product's shape in view.
    const radius = Math.max(box.getBoundingSphere(new THREE.Sphere()).radius * 1.8, this.extent() * 0.2);
    this.rig.frameSphere(centre, radius, focus.dimension !== null);
  }

  /** Radius of the sphere around the parts drawn (the default hull before any has loaded). */
  private extent(): number {
    const box = new THREE.Box3();
    const points = this.parts.silhouette();
    const p = new THREE.Vector3();
    for (let i = 0; i < points.length; i += 3) box.expandByPoint(p.set(points[i], points[i + 1], points[i + 2]));
    return box.getBoundingSphere(new THREE.Sphere()).radius;
  }

  home(animate = true) {
    this.rig.framePoints(this.parts.silhouette(), animate);
  }

  private resize() {
    const { clientWidth: w, clientHeight: h } = this.host;
    if (!w || !h) return;
    this.renderer.setSize(w, h, false);
    this.rig.resize(w, h);
    this.svg.setAttribute('viewBox', `0 0 ${w} ${h}`);
    outlineResolution.value.set(w, h);
    this.requestFrame();
  }

  private requestFrame() {
    if (this.frameQueued) return;
    this.frameQueued = true;
    requestAnimationFrame(() => {
      this.frameQueued = false;
      this.render();
    });
  }

  private render() {
    this.frames++;
    const { clientWidth: w, clientHeight: h } = this.host;
    this.renderer.setViewport(0, 0, w, h);
    this.renderer.setScissorTest(false);
    this.renderer.clear();
    this.renderer.render(this.scene, this.rig.camera);

    const dim = this.focusState?.dimension ?? null;
    const r = dim && this.loupeEl ? this.loupeRect() : null;
    const loupe = dim && r ? renderLoupe(this.renderer, this.scene, this.detail, r, h, dim, this.markers) : [];
    const main = drawMain(projector(this.rig.camera, 0, 0, w, h), this.markers, dim ? mid(dim) : null);
    // The mask hides the main overlay under the detail view and its 4 px margin.
    const mask = r
      ? [el('mask', { id: 'main-mask' },
          el('rect', { width: w, height: h, fill: '#fff' }),
          el('rect', { x: r.left - 4, y: r.top - 4, width: r.width + 8, height: r.height + 8, fill: '#000' }))]
      : [];
    const tag = this.partTag(w);
    this.svg.replaceChildren(defs(), el('defs', {}, ...mask), el('g', r ? { mask: 'url(#main-mask)' } : {}, ...main), ...loupe, ...(tag ? [tag] : []));
  }

  private loupeRect() {
    const host = this.host.getBoundingClientRect();
    const r = this.loupeEl!.getBoundingClientRect();
    return { left: r.left - host.left, top: r.top - host.top, width: r.width, height: r.height };
  }

  dispose() {
    this.rig.dispose();
    this.resizeObserver.disconnect();
    this.decoding?.cancel();
    this.renderer.dispose();
    this.canvas.remove();
    this.svg.remove();
  }
}
