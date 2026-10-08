// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The parts drawn and their paint under the view: owner colours (supplier-built parts in primer,
// edged in their owner's colour), the lens's ghosts, the agent's outlines and isolation with its
// faded context, and the person's selection in ink. Each applies to every occurrence of a part.
import * as THREE from 'three';
import type { Part, Placement } from '../api/types';
import { CORE_COLOUR, PLM_COLOUR, SUPPLIER_COLOUR, isCore, plmCode } from '../ui/plm';
import { corners } from './framing';
import { buildDrawing, disposeDrawing, sample, type Drawing } from './instances';
import type { Issued } from './loading';
import { Outlines, paintDrawing } from './paint';
import type { ParsedMesh } from './stepWorker';

/** Parts whose skin hides joint-face features (the ornithopter's linen panels, the aerial screw's sail segments); drawn translucent. */
const TRANSLUCENT = /linen|sail-segment/;
/** Ink of the part the person selected; the agent's outlines take the owner's colour. */
const SELECTED_COLOUR = 0x15202a;

export class DrawnParts {
  readonly drawings = new Map<string, Drawing>();
  partsById = new Map<string, Part>();
  /** The occurrences of each part from the placements answer; a part it does not list draws once. */
  occurrences = new Map<string, Placement[]>();
  /** Parts the agent outlined, the set it isolated with its context, and the part the person clicked. */
  highlighted = new Set<string>();
  isolated: { ids: Set<string>; context: Set<string> } | null = null;
  selected: string | null = null;
  /** Whether a part is inside the lens; null when no lens is set. */
  lens: ((part: Part) => boolean) | null = null;
  /** Ids of the context parts drawn faded. */
  private readonly faded = new Set<string>();
  private readonly outlines: Outlines;

  constructor(private readonly group: THREE.Group, private readonly host: HTMLElement) {
    this.outlines = new Outlines(group);
  }

  /** The parts drawn that an answer of `byId` removes, turns context or no longer context, or moves. */
  stale(byId: Map<string, Part>): string[] {
    return [...this.drawings].filter(([id, d]) => !byId.has(id) || Boolean(byId.get(id)!.context) !== this.faded.has(id)
      || d.key !== JSON.stringify(this.occurrences.get(id) ?? null)).map(([id]) => id);
  }

  /** A context part, outside the subtree on screen, is drawn as faint as a translucent skin. */
  add(part: Issued, meshes: ParsedMesh[]) {
    const translucent = TRANSLUCENT.test(part.cadFile) || part.context === true;
    if (part.context) this.faded.add(part.id);
    const owner = PLM_COLOUR[plmCode(part.plm)] ?? 0x8b959e;
    const d = buildDrawing(part.id, meshes, this.occurrences.get(part.id), {
      colour: part.supplier ? SUPPLIER_COLOUR : owner,
      edge: part.supplier ? owner : 0x223040,
      translucent,
      edgeOpacity: part.supplier ? 1 : translucent ? 0.55 : 0.8,
    });
    this.group.add(...d.solids, ...d.edges);
    this.drawings.set(part.id, d);
    this.paint(part.id);
    this.publish();
  }

  remove(id: string) {
    this.outlines.set(id, undefined, null);
    const d = this.drawings.get(id);
    if (d) {
      this.group.remove(...d.solids, ...d.edges);
      disposeDrawing(d);
    }
    this.drawings.delete(id);
    this.faded.delete(id);
    this.publish();
  }

  outside(id: string): boolean {
    const part = this.partsById.get(id);
    return this.lens !== null && part !== undefined && !this.lens(part);
  }

  /**
   * The host lists the ids of the parts drawn (`data-parts`), of those faded by the lens
   * (`data-lens-faded`) and each part's number of occurrences drawn (`data-instances`, `id:n`), for
   * the headless checks.
   */
  publish() {
    const ids = [...this.drawings.keys()].sort();
    this.host.dataset.parts = ids.join(' ');
    this.host.dataset.lensFaded = ids.filter((id) => this.outside(id)).join(' ');
    this.host.dataset.instances = ids.map((id) => `${id}:${this.drawings.get(id)!.count}`).join(' ');
  }

  repaint() {
    for (const id of this.drawings.keys()) this.paint(id);
  }

  /** A part's paint under the current view: hidden outside an isolated set, faded as its context, outlined when highlighted or selected. */
  paint(id: string) {
    const d = this.drawings.get(id);
    if (!d) return;
    const iso = this.isolated;
    const shown = !iso || iso.ids.has(id) || iso.context.has(id);
    paintDrawing(d, { shown, faded: iso ? !iso.ids.has(id) : null, out: this.outside(id) });
    const part = this.partsById.get(id);
    const colour = id === this.selected ? SELECTED_COLOUR
      : this.highlighted.has(id) && part ? (isCore(part.plm) ? CORE_COLOUR : PLM_COLOUR[plmCode(part.plm)] ?? 0x8b959e)
        : null;
    this.outlines.set(id, d, shown ? colour : null);
  }

  /** The solids drawn, every occurrence of a part in one InstancedMesh per mesh of its file. */
  solids(): THREE.InstancedMesh[] {
    return [...this.drawings.values()].flatMap((d) => d.solids).filter((m) => m.visible);
  }

  /**
   * A sample of the vertices of the parts drawn, or of the parts of `ids`, at every occurrence, so the
   * view is framed on what is drawn rather than on the box around it; a default hull the size of the
   * larger product before any part has loaded, and nothing for `ids` none of which is drawn.
   */
  silhouette(ids?: Set<string>): Float32Array {
    const drawn = [...this.drawings].filter(([id, d]) => (!ids || ids.has(id)) && d.solids.some((m) => m.visible)).map(([, d]) => d);
    if (ids && drawn.length === 0) return new Float32Array(0);
    if (drawn.length === 0) return corners(new THREE.Box3(new THREE.Vector3(-2500, -5500, -600), new THREE.Vector3(5000, 5500, 3600)));
    const chunks = drawn.map(sample);
    const all = new Float32Array(chunks.reduce((n, c) => n + c.length, 0));
    let k = 0;
    for (const c of chunks) { all.set(c, k); k += c.length; }
    return all;
  }
}
