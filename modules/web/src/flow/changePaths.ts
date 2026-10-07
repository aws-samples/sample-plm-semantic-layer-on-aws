// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The three paths of the change feed laid on the topology (docs/contract.md, "Freshness and the
// change feed"): the browser calls the owning service through the gateway. A value correction is
// the PLM's UPDATE, announced as part.value.corrected and logged by the links loader through the
// core service; a link is the core service's write, announced as interface.link.added; a CAD
// publication is the PLM's event, delivered to the loader, which writes the file index. The query
// service is on no write path: the browser only reads the feed through it. A hop is lit only once
// the feed holds an entry that took it; a reset leaves no entry, so it draws nothing. The PLM of
// each side of a link only names the features in a label: the link path stays Atelier core's.
import type { Changes } from '../api/types';
import { entriesOf, sideText, type Entry } from '../check/demo/changes';
import { CORE, cssColour } from '../ui/plm';
import { plural } from './drawn';
import type { WireData } from './edges';
import type { Topology } from './topology';

/** Requests enter like any click: ink. What Atelier then does is slate; what a PLM does is its colour. */
const INK = '#15202a';
const SLATE = cssColour(CORE);
/** Lines on a stacked tag before the rest is counted. */
const LINES = 3;
const FEED = 'GET /query/demo/changes';

export type LitWire = Pick<WireData, 'label' | 'labelKind'> & {
  colour: string;
  /** A read of the feed, not a write: drawn thinner than the paths the changes took. */
  read?: boolean;
};

export interface ChangePaths {
  wires: Map<string, LitWire>;
  /** Components on a lit hop. */
  nodes: Set<string>;
  /** PLMs a value was corrected in: their database changed and no graph did. */
  corrected: string[];
  counts: { values: number; links: number; cad: number };
}

type Value = Extract<Entry, { kind: 'value' }>;
type Link = Extract<Entry, { kind: 'link' }>;
type Cad = Extract<Entry, { kind: 'cad' }>;

const times = (n: number) => (n > 1 ? ` ×${n}` : '');

function stack(lines: string[]): string {
  const shown = lines.slice(0, LINES);
  if (lines.length > LINES) shown.push(`+${lines.length - LINES} more`);
  return shown.join('\n');
}

function groupBy<T>(items: T[], key: (t: T) => string): Map<string, T[]> {
  const out = new Map<string, T[]>();
  for (const it of items) {
    const k = key(it);
    out.set(k, [...(out.get(k) ?? []), it]);
  }
  return out;
}

export function changePaths(changes: Changes, topo: Topology): ChangePaths {
  const entries = entriesOf(changes);
  const values = entries.filter((e): e is Value => e.kind === 'value');
  const links = entries.filter((e): e is Link => e.kind === 'link');
  const cads = entries.filter((e): e is Cad => e.kind === 'cad');
  const counts = { values: values.length, links: links.length, cad: cads.length };
  const wires = new Map<string, LitWire>();
  const nodes = new Set<string>();
  /** Lights a hop; a hop two paths share stacks their tags (two events stay an event tag). */
  const light = (id: string, colour: string, label: string, labelKind: LitWire['labelKind'] = 'live', read = false) => {
    const link = topo.links.find((l) => l.id === id);
    if (!link) return;
    nodes.add(link.from);
    nodes.add(link.to);
    const prior = wires.get(id);
    if (!prior) {
      wires.set(id, { colour, label, labelKind, ...(read ? { read } : {}) });
      return;
    }
    const kind = prior.labelKind === 'event' && labelKind === 'event' ? 'event' : 'stack';
    wires.set(id, { colour, label: `${prior.label}\n${label}`, labelKind: kind });
  };
  const total = counts.values + counts.links + counts.cad;
  // The log spells the owner as the service sent it; topology ids are lowercase.
  const byPlm = groupBy(values, (v) => v.change.plm.toLowerCase());
  if (total === 0) return { wires, nodes, corrected: [], counts };

  light('browser>cloudfront', INK, stack([`${plural(total, 'POST')} · x-atelier-profile`, FEED]), 'stack');
  light('cloudfront>apigw', INK, 'origin secret', 'protocol');
  // The feed itself: a read, through the query service, which writes nothing.
  light('apigw>query', INK, `${FEED} · read`, 'live', true);

  // 1. A value correction: the PLM's own UPDATE, then its event; the loader logs it through the core service.
  for (const [plm, vs] of byPlm) {
    const colour = cssColour(plm);
    light(`apigw>plm-${plm}`, colour, stack(vs.map((v) => `demo/update · ${v.change.table}.${v.change.column} ${String(v.change.before)} -> ${String(v.change.after)}`)), 'stack');
    light(`plm-${plm}>aurora`, colour, `UPDATE${times(vs.length)}`);
    light(`plm-${plm}>eventbridge`, colour, `part.value.corrected${times(vs.length)}`, 'event');
  }
  if (counts.values) {
    light(`loader>plm-${CORE}`, SLATE, `POST /core/changes${times(counts.values)}`);
    light(`plm-${CORE}>demo-change`, SLATE, `INSERT${times(counts.values)}`);
  }

  // 2. A link: the core service appends the triple and its inverse, then announces it.
  if (counts.links) {
    const triples = links.reduce((s, l) => s + l.n, 0);
    light(`apigw>plm-${CORE}`, SLATE, stack(links.map((l) => `/core/links · ${sideText(l.from)} matesWith ${sideText(l.to)}`)), 'stack');
    light(`plm-${CORE}>neptune`, SLATE, `links graph +${plural(triples, 'triple')} · Graph Store POST`);
    light(`plm-${CORE}>eventbridge`, SLATE, `interface.link.added${times(counts.links)}`, 'event');
  }

  // 3. A CAD publication: the publishing PLM's event, delivered to the loader, which sets the file-index entry.
  for (const [plm, cs] of groupBy(cads, (c) => c.plm ?? '?')) {
    const colour = cssColour(plm);
    light(`apigw>plm-${plm}`, colour, stack(cs.map((c) => `demo/events/cad · ${c.part}`)), 'stack');
    light(`plm-${plm}>eventbridge`, colour, `part.cad.published${times(cs.length)}`, 'event');
  }

  // The rules deliver every PLM event to the loader.
  const delivered = counts.values + counts.cad;
  if (delivered) light('eventbridge>loader', SLATE, `rules · ${plural(delivered, 'event')} delivered`);
  if (counts.cad) light('loader>neptune', SLATE, `file index +${plural(counts.cad, 'triple')} · atelier:cadFile`);
  return { wires, nodes, corrected: [...byPlm.keys()], counts };
}

/** The caption's sentence: what the feed holds, in the strip's words. */
export const countsText = ({ values, links, cad }: ChangePaths['counts']) =>
  values + links + cad === 0
    ? 'released dataset, no changes'
    : `${plural(values, 'value correction')}, ${links} ${links === 1 ? 'link' : 'links'} written, ${cad} CAD ${cad === 1 ? 'publication' : 'publications'}`;
