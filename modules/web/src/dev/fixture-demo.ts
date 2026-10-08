// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Fixture-mode demo controls (docs/contract.md, "Freshness and the change feed"), each on the
// owning service's route: corrections written in the fixture model itself (one cell or a batch, in
// one go) so the next run reads them, one log row per changed row arriving a moment later as the
// event would; links written to the graph on request; CAD publications that reach the file index a
// few seconds after their event.
import type { CellUpdate, CellValue, ChangeRequest, ChangeResult, ChangedRow, Changes, ConfirmEquivalenceRequest, DemoHealth, GraphTriple, PublishCadRequest, PublishLinkRequest, ValueChange } from '../api/types';
import { catalogues } from './fixture-catalogue';
import { cellAt, refusal } from './fixture-cells';
import { partIri } from './fixture-evidence';
import { refreshFindings } from './fixture-findings';
import { confirmEquivalence, resetEquivalences, resetOffers } from './fixture-purchasing';
import { resetReferences } from './fixture-references';
import { cadPendingIds, featureIri, parts, publishedLinks, rebuildInterfaces, seedFeatures } from './fixture-seed';
import { FIXTURE_MARKER } from './marker';
import { policyFor } from './fixture-policy';
import { plmOfIri } from '../check/evidence/turtle';

const OFFICER = 'export-officer';
// Predicates as the query service shortens them in the change feed.
const MATES_WITH = 'atelier:matesWith';
const CAD_FILE = 'atelier:cadFile';
const SAME_AS = 'owl:sameAs';
const PART_IRI = /^https:\/\/example\.com\/atelier\/(fr|de|uk|es)\/part\/[^/\s<>"]+$/;
/** Triples of the released links.ttl and fileindex.ttl in this fixture. */
const RELEASED = { links: 1187, fileindex: 90 };
/** How long a PLM's event takes to reach Atelier here: a correction's log row in a second or two, a CAD publication long enough to see the waiting state. */
const LOG_MS = 2500;
const LANDING_MS = 4000;
const KEY = /^cad\/[a-z0-9-]+\/[a-z0-9-]+\.stp$/;

const values: ValueChange[] = [];
/** Every cell written, with its value before, in order: the reset writes them back in reverse. */
const undo: (CellUpdate & { plm: string })[] = [];
const pendingValues: { row: ValueChange; lands: number }[] = [];
const added: GraphTriple[] = [];
const pendingCad: { plm: string; part: string; cadFile: string; lands: number }[] = [];
let nextId = 1;
let nextEvent = 1;

// Who may act, as the services check it (docs/contract.md, "Freshness and the change feed").
const refuse = (why: string): never => {
  throw new Error(`HTTP 403: ${why} (${FIXTURE_MARKER})`);
};
/** A PLM's fact: its engineer or the officer. */
const plmOwner = (profile: string, plm: string, what: string) => {
  if (profile !== OFFICER && profile !== `${plm}-engineer`) refuse(`${what} is the ${plm.toUpperCase()} engineer's or the export-control officer's`);
};
/** Atelier's link: the integration role or the officer. */
const integrator = (profile: string) => {
  if (profile !== OFFICER && profile !== 'programme-cleared') refuse("publishing a link is programme-cleared's or the export-control officer's");
};
/** The change list: any profile but unknown. */
const known = (profile: string) => {
  if (policyFor(profile).profile === 'unknown') refuse('the change list needs a profile');
};
const officerOnly = (profile: string) => {
  if (profile !== OFFICER) refuse("the reset is the export-control officer's");
};
const nativeId = (iri: string) => decodeURIComponent(iri.replace(/^.*\//, ''));
const count = (p: string) => added.filter((t) => t.p === p).length;
/** A triple as the query service reports it: terms shortened to native ids, the IRIs kept beside, the subject's PLM. */
const matesWith = (from: string, to: string): GraphTriple =>
  ({ s: nativeId(from), p: MATES_WITH, o: nativeId(to), sIri: from, oIri: to, plm: plmOfIri(from) });
const cadFileOf = (plm: string, part: string, cadFile: string): GraphTriple =>
  ({ s: part, p: CAD_FILE, o: cadFile, sIri: partIri(plm, part), oIri: null, plm });
const sameAs = (a: string, b: string): GraphTriple => ({ s: nativeId(a), p: SAME_AS, o: nativeId(b), sIri: a, oIri: b, plm: plmOfIri(a) });
const triples = () => ({ links: RELEASED.links + count(MATES_WITH) + count(SAME_AS), fileindex: RELEASED.fileindex + count(CAD_FILE) });

/** The change log keeps numbers and words; a flag is logged as its text. */
const logged = (v: CellValue): string | number | null => (typeof v === 'boolean' ? String(v) : v);

/**
 * 1. POST /{plm}/demo/update: one UPDATE, or several in one transaction, in the PLM's own tables, then one event; a log
 * row per changed row follows the event. Every cell is checked before any is written. No graph is written.
 */
function change(profile: string, plm: string, body: ChangeRequest): ChangeResult {
  plmOwner(profile, plm, 'a correction');
  const updates: CellUpdate[] = 'updates' in body ? body.updates : [{ table: body.table, key: body.key, column: body.column, value: body.value }];
  if (updates.length === 0) throw new Error('updates names no cell');
  const cells = updates.map((u) => {
    const cell = cellAt(plm, u.table, u.key, u.column);
    const why = refusal(plm, u.table, u.column, cell.type, u.value);
    if (why) throw new Error(`HTTP 400: ${plm}_plm.${u.table} ${u.key}: ${why} (${FIXTURE_MARKER})`);
    return { u, cell };
  });
  const rows: ChangedRow[] = cells.map(({ u, cell }) => {
    const before = cell.get();
    cell.set(u.value);
    undo.push({ plm, table: u.table, key: u.key, column: u.column, value: before });
    return { table: u.table, key: u.key, column: u.column, before, after: cell.get() };
  });
  rebuildInterfaces();
  refreshFindings();
  const at = new Date().toISOString();
  const lands = Date.now() + LOG_MS;
  for (const r of rows) {
    pendingValues.push({ row: { id: nextId++, plm, table: r.table, key: r.key, column: r.column, before: logged(r.before), after: logged(r.after), actor: profile, purpose: body.purpose, at }, lands });
  }
  return { plm, rows, at };
}

/** 2. POST /core/links: a mating link is Atelier's fact, appended to the links graph on request, then the event is emitted. */
function publishLink(profile: string, body: PublishLinkRequest) {
  integrator(profile);
  const { from, to } = body;
  if (!seedFeatures.some((s) => featureIri(s) === from)) throw new Error(`${from} is not a feature of any PLM`);
  if (!/^https?:\/\/\S+$/.test(to)) throw new Error('to must be a feature IRI');
  added.push(matesWith(from, to), matesWith(to, from));
  publishedLinks.push([nativeId(from), nativeId(to)]);
  rebuildInterfaces();
  return { triples: { links: triples().links }, eventId: `atelier.graph/interface.link.added#${nextEvent++}` };
}

/** POST /core/equivalences: a user confirms that parts of several sites are one item; Atelier writes owl:sameAs between every two. */
function confirmItem(profile: string, body: ConfirmEquivalenceRequest) {
  if (profile !== OFFICER && profile !== 'programme-cleared') refuse("confirming an equivalence is programme-cleared's or the export-control officer's");
  const iris = body.parts ?? [];
  if (iris.length < 2 || iris.length > 16 || iris.some((iri) => !PART_IRI.test(iri)) || new Set(iris).size !== iris.length) {
    throw new Error(`HTTP 400: parts must name 2 to 16 distinct part IRIs (${FIXTURE_MARKER})`);
  }
  confirmEquivalence(iris);
  for (const a of iris) for (const b of iris) if (a !== b && !added.some((t) => t.p === SAME_AS && t.sIri === a && t.oIri === b)) added.push(sameAs(a, b));
  return { parts: iris, triples: { links: triples().links }, eventId: `atelier.graph/equivalence.confirmed#${nextEvent++}` };
}

/** 3. POST /{plm}/demo/events/cad: a CAD publication is the PLM's fact, one event, indexed into the file index once it is delivered. */
function publishCad(profile: string, plm: string, body: PublishCadRequest) {
  const { part, cadFile } = body;
  plmOwner(profile, plm, 'a CAD publication');
  const p = parts.find((x) => x.plm === plm && x.id === part);
  if (!p) throw new Error(`${part} is not a part of the ${plm.toUpperCase()} PLM`);
  if (typeof cadFile !== 'string' || !KEY.test(cadFile)) throw new Error('cadFile must be a bucket key of the form cad/<product>/<part>.stp');
  pendingCad.push({ plm, part, cadFile, lands: Date.now() + LANDING_MS });
  return { plm, part, cadFile, event: 'atelier.plm/part.cad.published', at: new Date().toISOString() };
}

/**
 * Events whose delivery time has passed reach Atelier: a correction's row lands in the log; a CAD publication puts
 * atelier:cadFile on the part, gives it its key and URL, and clears its cadMissing finding.
 */
function land() {
  const now = Date.now();
  const logged = pendingValues.filter((e) => e.lands <= now);
  values.push(...logged.map((e) => e.row));
  pendingValues.splice(0, pendingValues.length, ...pendingValues.filter((e) => e.lands > now));
  const landed = pendingCad.filter((e) => e.lands <= now);
  if (landed.length === 0) return;
  for (const e of landed) {
    const p = parts.find((x) => x.plm === e.plm && x.id === e.part)!;
    p.cadFile = e.cadFile;
    p.cadUrl = `/${e.cadFile}`;
    added.push(cadFileOf(e.plm, e.part, e.cadFile));
  }
  pendingCad.splice(0, pendingCad.length, ...pendingCad.filter((e) => e.lands > now));
  refreshFindings();
}

/** GET /api/query/demo/health: public, configuration and released counts only, as the query service reports them. */
export const demoHealth = (): DemoHealth => ({
  eventBus: null,
  writer: 'atelier-core',
  publishes: ['atelier.plm/part.value.corrected', 'atelier.plm/part.cad.published', 'atelier.graph/interface.link.added', 'atelier.graph/equivalence.confirmed'],
  subscriptions: ['atelier.plm/part.value.corrected -> links loader -> POST /core/changes -> atelier_core.demo_change', 'atelier.plm/part.cad.published -> links loader -> file index'],
  releasedGraphs: { ...RELEASED },
  changeLog: `atelier_core.demo_change`,
});

/** GET /api/query/demo/changes. */
export function demoChanges(profile: string): Changes {
  known(profile);
  land();
  return { values: [...values], graph: { added: [...added], removed: [], triples: triples() } };
}

/** POST /core/demo/reset: the core service replays the log in reverse through the PLMs and puts the released graphs back. */
function reset(profile: string) {
  officerOnly(profile);
  for (const u of [...undo].reverse()) cellAt(u.plm, u.table, u.key, u.column).set(u.value);
  undo.length = 0;
  resetReferences();
  resetOffers();
  resetEquivalences();
  // The released file index comes back: a part it never named loses its key and URL again, and its finding returns.
  for (const p of parts) {
    if (cadPendingIds.has(p.id)) {
      p.cadFile = null;
      p.cadUrl = null;
    }
  }
  const undone = { values: values.length + pendingValues.length, triples: added.length, pendingEvents: pendingCad.length };
  values.length = 0;
  pendingValues.length = 0;
  added.length = 0;
  pendingCad.length = 0;
  publishedLinks.length = 0;
  rebuildInterfaces();
  refreshFindings();
  return { undone, triples: { ...RELEASED } };
}

/** The demo controls, each on its owning service's route. */
export function demoMutate(path: string, profile: string, body: unknown): unknown {
  const plmRoute = /^\/([a-z]+)\/demo\/(update|events\/cad)$/.exec(path);
  if (plmRoute) {
    const [, plm, action] = plmRoute;
    if (!catalogues[plm]) throw new Error(`fixture has no PLM ${plm}`);
    return action === 'update' ? change(profile, plm, body as ChangeRequest) : publishCad(profile, plm, body as PublishCadRequest);
  }
  if (path === '/core/links') return publishLink(profile, body as PublishLinkRequest);
  if (path === '/core/equivalences') return confirmItem(profile, body as ConfirmEquivalenceRequest);
  if (path === '/core/demo/reset') return reset(profile);
  throw new Error(`fixture has no route for POST ${path}`);
}
