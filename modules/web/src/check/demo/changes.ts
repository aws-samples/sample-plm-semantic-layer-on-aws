// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Reading GET /api/query/demo/changes: value corrections and graph additions as one list.
import type { ChangeResult, Changes, GraphTriple, ValueChange } from '../../api/types';
import { Atelier, plmOfIri } from '../evidence/turtle';
import { plmCode } from '../../ui/plm';

const MATES_WITH = `${Atelier}matesWith`;
const SAME_AS = 'http://www.w3.org/2002/07/owl#sameAs';

/** The feed may name a predicate in full or prefixed. */
export const isMatesWith = (p: string) => p === MATES_WITH || p === 'atelier:matesWith';
export const isCadFile = (p: string) => p.endsWith('cadFile');
export const isSameAs = (p: string) => p === SAME_AS || p === 'owl:sameAs';
/** A literal object may arrive quoted. */
const literal = (o: string) => o.replace(/^"(.*)"$/, '$1');

/** The PLM a triple's subject belongs to: as the feed names it, else read from the subject IRI. */
const subjectPlm = (t: GraphTriple) => t.plm ?? (t.sIri ? plmOfIri(t.sIri) : null);
const objectPlm = (t: GraphTriple) => (t.oIri ? plmOfIri(t.oIri) : null);

/** One side of a link: the feature's id and the PLM its IRI names. */
export interface Side {
  id: string;
  plm: string | null;
}

/** A side in words: `UK PL 6200-01`, or the id alone when the IRI is no PLM's. */
export const sideText = ({ id, plm }: Side) => (plm ? `${plmCode(plm)} ${id}` : id);

export type Entry =
  | { kind: 'value'; key: string; change: ValueChange }
  /** Several rows one release changed in one transaction: one event, logged together. */
  | { kind: 'values'; key: string; changes: ValueChange[] }
  /** A link Atelier wrote on request: the triple and its inverse, counted together. */
  | { kind: 'link'; key: string; from: Side; to: Side; n: number }
  /** Parts a user confirmed as one item: owl:sameAs between every two of them, counted together. */
  | { kind: 'equivalence'; key: string; parts: Side[]; n: number }
  /** A CAD file a PLM published by event: atelier:cadFile set on the part in the file index. */
  | { kind: 'cad'; key: string; part: string; plm: string | null; cadFile: string }
  | { kind: 'added'; key: string; triple: GraphTriple }
  | { kind: 'removed'; key: string; triple: GraphTriple };

/** Rows of one release: the same site, purpose, actor and time. */
const oneRelease = (a: ValueChange, b: ValueChange) => a.plm === b.plm && a.purpose === b.purpose && a.actor === b.actor && a.at === b.at;

/** Value rows in time order, the rows of one release as one entry. */
function valueEntries(values: ValueChange[]): Entry[] {
  const groups: ValueChange[][] = [];
  for (const v of [...values].sort((a, b) => a.at.localeCompare(b.at) || a.id - b.id)) {
    const last = groups[groups.length - 1];
    if (last && oneRelease(last[0], v)) last.push(v);
    else groups.push([v]);
  }
  return groups.map((g): Entry => (g.length === 1 ? { kind: 'value', key: `v${g[0].id}`, change: g[0] } : { kind: 'values', key: `v${g[0].id}+${g.length}`, changes: g }));
}

/**
 * Value rows in time order, then graph additions (a link and its inverse as one entry, a confirmed
 * equivalence as one, a CAD publication as one) and removals: the feed carries no time on triples, so they follow the log.
 */
export function entriesOf(c: Changes): Entry[] {
  const values = valueEntries(c.values);
  const links = new Map<string, Extract<Entry, { kind: 'link' }>>();
  const added: Entry[] = [];
  const same: GraphTriple[] = [];
  for (const t of c.graph.added) {
    if (isSameAs(t.p)) {
      same.push(t);
      continue;
    }
    if (isCadFile(t.p)) {
      added.push({ kind: 'cad', key: `c|${t.s}|${t.o}`, part: t.s, plm: subjectPlm(t), cadFile: literal(t.o) });
      continue;
    }
    if (!isMatesWith(t.p)) {
      added.push({ kind: 'added', key: `a|${t.s}|${t.p}|${t.o}`, triple: t });
      continue;
    }
    const key = `l|${[t.s, t.o].sort().join('|')}`;
    const e = links.get(key) ?? { kind: 'link', key, from: { id: t.s, plm: subjectPlm(t) }, to: { id: t.o, plm: objectPlm(t) }, n: 0 };
    e.n += 1;
    links.set(key, e);
  }
  const removed = c.graph.removed.map((t): Entry => ({ kind: 'removed', key: `r|${t.s}|${t.p}|${t.o}`, triple: t }));
  return [...values, ...links.values(), ...equivalences(same), ...added, ...removed];
}

/** The owl:sameAs triples as one entry per set of parts they join: one confirmation each. */
function equivalences(triples: GraphTriple[]): Entry[] {
  const sets: { parts: Map<string, Side>; n: number }[] = [];
  for (const t of triples) {
    const joined = sets.filter((x) => x.parts.has(t.s) || x.parts.has(t.o));
    const into = joined[0] ?? { parts: new Map<string, Side>(), n: 0 };
    if (!joined.length) sets.push(into);
    for (const other of joined.slice(1)) {
      other.parts.forEach((v, k) => into.parts.set(k, v));
      into.n += other.n;
      sets.splice(sets.indexOf(other), 1);
    }
    into.parts.set(t.s, { id: t.s, plm: subjectPlm(t) });
    into.parts.set(t.o, { id: t.o, plm: objectPlm(t) });
    into.n += 1;
  }
  return sets.map((x) => {
    const parts = [...x.parts.values()];
    return { kind: 'equivalence', key: `e|${parts.map((p) => p.id).sort().join('|')}`, parts, n: x.n };
  });
}

/** The cadFile the feed's additions hold for a part, once its publication event has reached the file index. */
export const cadLanded = (c: Changes, partId: string): string | null => {
  const t = c.graph.added.find((x) => isCadFile(x.p) && x.s === partId);
  return t ? literal(t.o) : null;
};

/** A logged value and an answered one are the same when their text is: the log keeps numbers and words, a flag as text. */
const same = (a: unknown, b: unknown) => (a === null || a === undefined ? b === null || b === undefined : b !== null && b !== undefined && String(a) === String(b));

/**
 * The log rows of a correction once its part.value.corrected event has reached Atelier, one per row the PLM's answer
 * names (table, key and column); the value it set tells each from an earlier correction of the same cell. Null until
 * every row has landed.
 */
export function correctionLanded(c: Changes, r: Pick<ChangeResult, 'plm' | 'rows'>): ValueChange[] | null {
  const plm = r.plm.toLowerCase();
  const found = r.rows.map((row) =>
    c.values.find((v) => v.plm.toLowerCase() === plm && v.table === row.table && v.key === row.key && v.column === row.column && same(v.after, row.after)));
  return found.every((v): v is ValueChange => v !== undefined) ? found : null;
}
