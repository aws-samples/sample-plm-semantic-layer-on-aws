// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The components of this deployment and the data flows between them, laid out
// top to bottom in the order a request traverses them. Positions are canvas units.
import { CORE } from '../ui/plm';

export type Variant =
  | 'browser' | 'cloudfront' | 'eventbridge' | 'agent' | 'apigw' | 'plm' | 'core' | 'ontop' | 'query' | 'loader' | 'neptune' | 'aurora' | 'coredb' | 'changelog' | 'cad';

export interface Component {
  id: string;
  variant: Variant;
  title: string;
  /** Name the answer path or the API uses for it. */
  endpoint?: string;
  sub: string;
  /** Owning source: a PLM code, or `core` for what Atelier owns. */
  plm?: string;
  does: string;
  x: number;
  y: number;
  w: number;
  h: number;
}

export type Route =
  /** A horizontal wire between neighbours has no room under it: its tag turns along the wire. */
  | { kind: 'straight'; tag?: 'rotated' }
  /** From the source to the horizontal bus at `y` (down or up), along it, then into the target. The tag sits past the target's corner, or past the source's when several wires share the bus and its end. */
  | { kind: 'bus'; y: number; tag?: 'source' }
  /** Out of the source's side, along the gutter beside the column (left unless `side` says right), into the target's side. */
  | { kind: 'gutter'; offset: number; side?: 'right' }
  /** Out of the source's right side, then straight down into the target. */
  | { kind: 'elbow' };

export interface Link {
  id: string;
  from: string;
  fromHandle?: string;
  to: string;
  toHandle?: string;
  protocol: string;
  route: Route;
  /** The rail is shared with other links of the same event: the label is drawn on one of them only. */
  tagless?: boolean;
}

export interface Layer {
  label: string;
  y: number;
}

export interface Topology {
  components: Component[];
  links: Link[];
  layers: Layer[];
}

export const COL_W = 140;
export const COL_GAP = 28;
export const AURORA_PAD = 24;
/** Height of a database slot inside the Aurora cluster card, its offset from the card's top, and the gap to a second slot row. */
export const SLOT_H = 48;
export const SLOT_TOP = 10;
export const SLOT_GAP = 6;
/** The cluster card holds two slot rows: the databases, and under atelier_core its change log. */
export const AURORA_H = SLOT_TOP + SLOT_H + SLOT_GAP + SLOT_H + 40;
/** The agent card sits beside the gateway it calls; the gap is the wire that carries the tools it ran. */
const AGENT_W = 240;
const AGENT_H = 80;
const AGENT_GAP = 176;
export const colX = (i: number) => i * (COL_W + COL_GAP);
export const centre = (c: Component) => c.x + c.w / 2;

const Y = { browser: 0, edge: 90, api: 190, services: 400, virtual: 690, data: 822 };
const BUS_STEP = 18;

/** Bus rows for one source and its targets: the farthest target on each side takes the highest bus. */
function buses(source: Component, targets: Component[], y0: number): Map<string, Route> {
  const routes = new Map<string, Route>();
  const sides: Record<'left' | 'right', Component[]> = { left: [], right: [] };
  for (const t of targets) {
    const dx = centre(t) - centre(source);
    if (Math.abs(dx) < 4) routes.set(t.id, { kind: 'straight' });
    else sides[dx < 0 ? 'left' : 'right'].push(t);
  }
  for (const side of [sides.left, sides.right]) {
    side.sort((a, b) => Math.abs(centre(b) - centre(source)) - Math.abs(centre(a) - centre(source)));
    side.forEach((t, i) => routes.set(t.id, { kind: 'bus', y: y0 + i * BUS_STEP }));
  }
  return routes;
}

export function topology(plms: string[]): Topology {
  // Column i holds one source's service, virtual graph and database slot; the Atelier core store is the last column.
  const coreCol = plms.length;
  const rightX = colX(coreCol + 1) + 18;
  const front = (id: string, variant: Variant, title: string, sub: string, does: string, y: number, w: number, h: number): Component => ({
    id, variant, title, sub, does, y, w, h, x: (rightX + 230) / 2 - w / 2,
  });
  const browser = front('browser', 'browser', 'Browser', 'this SPA', 'Loads /config.json at boot and calls the API through CloudFront with the viewer profile in x-atelier-profile; no endpoint is baked into the bundle.', Y.browser, 200, 52);
  const cloudfront = front('cloudfront', 'cloudfront', 'CloudFront', 'Cognito sign-in at the edge', 'Serves the SPA and fronts the API. A Lambda@Edge function checks the Cognito session before any request reaches an origin.', Y.edge, 240, 58);
  // The other edge: where systems announce their facts, level with the edge people and agents enter through.
  const eventBridge: Component = {
    id: 'eventbridge', variant: 'eventbridge', title: 'EventBridge', endpoint: 'default bus', sub: 'business events · atelier.plm · atelier.graph',
    does: 'The default event bus. A PLM service puts part.value.corrected after it corrects a value in its own table and part.cad.published when it publishes a CAD file (the PLM\'s facts); the Atelier core service, the ORM in front of Atelier\'s own stores, puts interface.link.added after it writes a link to the links graph (Atelier\'s fact). Rules deliver each event to the links loader. The query service only reads. No store is watched: a correction is announced, never copied, and the virtual graph reads the new value in place.',
    x: rightX, y: Y.edge, w: 230, h: 86,
  };
  const apigw = front('apigw', 'apigw', 'API Gateway', 'one endpoint · origin-secret authorizer', 'One REST endpoint. Its authorizer accepts a request only when it carries the secret CloudFront adds, so the services cannot be called directly.', Y.api, 240, 58);
  const agent: Component = {
    id: 'agent', variant: 'agent', title: 'Agent', endpoint: '/agent/*', sub: 'Strands on Fargate · AG-UI over SSE',
    does: 'A client of the semantic layer like the screen: answers a question by calling the query service\'s MCP tools with the caller\'s profile, adding x-atelier-actor: agent so provenance tells its calls from a person\'s. A Fargate task behind CloudFront /agent/* (VPC origin, internal ALB); the model is read from MODEL_ID and is a pluggable dependency.',
    x: apigw.x - AGENT_W - AGENT_GAP, y: Y.api, w: AGENT_W, h: AGENT_H,
  };
  const plmSvc = plms.map((plm, i): Component => ({
    id: `plm-${plm}`, variant: 'plm', plm, title: 'PLM service', endpoint: `/api/${plm}/*`,
    sub: 'Spring Boot · Hibernate ORM · MapStruct',
    does: `Owns the ${plm.toUpperCase()} parts and their features in their native schema; its entity annotations publish the catalogue and generate the R2RML. Its row reads return only parts whose tag in atelier_core the profile may see. POST /${plm}/demo/update runs one UPDATE in its own table and publishes part.value.corrected; POST /${plm}/demo/events/cad publishes part.cad.published. It never calls Atelier.`,
    x: colX(i), y: Y.services, w: COL_W, h: 80,
  }));
  const coreSvc: Component = {
    id: `plm-${CORE}`, variant: 'core', plm: CORE, title: 'Core service', endpoint: `/api/${CORE}/*`,
    sub: 'Spring Boot · Hibernate ORM · MapStruct',
    does: 'The ORM in front of Atelier\'s own stores, and their single writer. Owns the Atelier core schema: part_tag holds each part\'s export-control tag (jurisdiction, releasability, which PLM tagged it and when), written by the owning PLM when it publishes the part. Writes the links graph: POST /core/links appends a mating link and its inverse with a Graph Store POST, then publishes interface.link.added. POST /core/changes logs a correction the links loader delivers in demo_change. POST /core/demo/reset replays that log in reverse through the PLM services (via the API gateway) and puts the released graphs back with a Graph Store PUT. Not a PLM, but described like one: its annotations publish the catalogue and generate the R2RML of ontop-core.',
    x: colX(coreCol), y: Y.services, w: COL_W, h: 80,
  };
  const query: Component = {
    id: 'query', variant: 'query', title: 'Query service', endpoint: '/api/query/*', sub: 'Jena federation · SHACL · policy',
    does: 'Reads the tags of the parts it needs from ontop-core with the profile\'s releasability FILTER pushed into that source\'s SQL; the visible part IRIs become the VALUES of every PLM arm, so rows of hidden parts are never requested. Federates one CONSTRUCT over the virtual graphs and the link store, filters the merged graph again, then runs the SHACL shapes. Presigns the CAD URLs of the parts the profile may see. Serves the change feed (GET /query/demo/changes) by reading demo_change and diffing the live graphs; it is on no write path.',
    x: rightX, y: Y.services, w: 230, h: 92,
  };
  const ontop = plms.map((plm, i): Component => ({
    id: `ontop-${plm}`, variant: 'ontop', plm, title: 'Ontop', endpoint: `ontop-${plm}`, sub: 'virtual graph · R2RML',
    does: `Rewrites each SPARQL SERVICE arm into SQL over ${plm}_plm through the R2RML generated from the ${plm.toUpperCase()} annotations; nothing is copied, and only the parts bound by the tag arm are read.`,
    x: colX(i), y: Y.virtual, w: COL_W, h: 66,
  }));
  const ontopCore: Component = {
    id: `ontop-${CORE}`, variant: 'ontop', plm: CORE, title: 'Ontop core', endpoint: `ontop-${CORE}`, sub: 'virtual graph · R2RML over atelier_core',
    does: 'Rewrites the tag arm, FILTER included, into SQL over atelier_core.part_tag. Its R2RML mints the same part IRI as the PLM mappings, so atelier:jurisdiction, atelier:releasableTo, atelier:taggedBy and atelier:taggedAt attach to the PLM\'s own part.',
    x: colX(coreCol), y: Y.virtual, w: COL_W, h: 66,
  };
  const loader: Component = {
    id: 'loader', variant: 'loader', title: 'Links loader', endpoint: 'Lambda', sub: 'file index · change log',
    does: 'Lambda the EventBridge rules deliver the PLM events to. For part.cad.published it sets atelier:cadFile on the part IRI in the file index with one SPARQL DELETE/INSERT, one value per part; the presigned URL and the 3D part follow on the next load. For part.value.corrected it records the correction in atelier_core.demo_change through POST /core/changes on the core service, skipping the inverse corrections a reset publishes. The same function loads the released links and file index at deploy time.',
    x: rightX + 230 + 40, y: Y.virtual, w: 150, h: 86,
  };
  const aurora: Component = {
    id: 'aurora', variant: 'aurora', title: 'Aurora PostgreSQL', sub: 'one cluster · one database per PLM, native schemas · atelier_core under its own role',
    does: 'One cluster holding one database per PLM, each in the schema its PLM was built with, and atelier_core, the Atelier-owned database, under its own role.',
    x: -AURORA_PAD, y: Y.data, w: AURORA_PAD * 2 + colX(coreCol + 1) - COL_GAP, h: AURORA_H,
  };
  const coreDb: Component = {
    id: 'atelier-core', variant: 'coredb', plm: CORE, title: 'atelier_core', sub: 'Atelier core database · part_tag',
    does: 'Atelier\'s own database on the PLM cluster, under its own role. Table part_tag(plm, native_key, jurisdiction, releasable_to, tagged_by, tagged_at): one row per published part, written by the owning PLM. The PLM services read it through the read-only core_reader role to filter their row reads.',
    x: colX(coreCol), y: Y.data + SLOT_TOP, w: COL_W, h: SLOT_H,
  };
  const changeLog: Component = {
    id: 'demo-change', variant: 'changelog', plm: CORE, title: 'demo_change', sub: 'corrections log',
    does: 'Table atelier_core.demo_change: one row per value corrected in a PLM (plm, table, key, column, before, after, actor, purpose, at), written by the core service on POST /core/changes when the links loader delivers the PLM\'s part.value.corrected event. Atelier-owned, outside the catalogue and the mapping: a correction never enters a graph. Reset replays the rows in reverse.',
    x: colX(coreCol), y: Y.data + SLOT_TOP + SLOT_H + SLOT_GAP, w: COL_W, h: SLOT_H,
  };
  const neptune: Component = {
    id: 'neptune', variant: 'neptune', title: 'Neptune', endpoint: 'neptune', sub: 'links: interfaces · matesWith · sameAs\nfile index: cadFile · labels: names',
    does: 'Holds only the cross-PLM facts no single PLM owns: the interfaces, the features they declare, which feature mates with which and which parts a user confirmed as one item (links graph), the file index, one atelier:cadFile triple per part saying where its CAD file is, and the labels graph, each item\'s native and English names and the products\' glossary in four languages. Written by the Atelier core service (Graph Store POST for a link or an equivalence, PUT to restore the released graphs) and, for file-index entries, by the links loader; the query service only reads.',
    x: rightX, y: Y.data, w: 230, h: 104,
  };
  const cad: Component = {
    id: 'cad', variant: 'cad', title: 'CAD bucket', sub: 'STEP AP242 · presigned URLs',
    does: 'Private S3 bucket. The query service presigns a 15-minute URL for each part the profile may see and the browser fetches the STEP file from it directly; a hidden part gets no URL.',
    x: rightX + 230 + 40, y: Y.data, w: 150, h: 80,
  };
  const components = [browser, cloudfront, eventBridge, agent, apigw, ...plmSvc, coreSvc, query, ...ontop, ontopCore, loader, aurora, coreDb, changeLog, neptune, cad];

  const services = [...plmSvc, coreSvc];
  const graphs = [...ontop, ontopCore];
  // The bus rows start below the agent card, which is taller than the gateway beside it.
  const apiRoutes = buses(apigw, [...services, query], Y.api + AGENT_H + 8);
  // The loader's change-log call climbs to the core service on the row directly under the services; the Ontop rails fan
  // out below it, above the graphs.
  const logY = Y.services + query.h + 30;
  const queryRoutes = buses(query, graphs, Y.services + query.h + 8 + plms.length * 20 + 6);
  // Two event rails above the services row: the PLMs' fact on the higher one, entering the bus card on its left, Atelier's
  // on the lower one, entering on its right. The PLM rail is labelled once, above the first publisher.
  const plmEventY = Y.services - 34;
  const coreEventY = Y.services - 16;
  // The core service's graph write descends on its own row under the query service's fan-out to the virtual graphs.
  const writeY = Y.virtual - 16;
  const links: Link[] = [
    { id: 'browser>cloudfront', from: 'browser', to: 'cloudfront', protocol: 'HTTPS', route: { kind: 'straight' } },
    { id: 'browser>cad', from: 'browser', fromHandle: 'right', to: 'cad', toHandle: 'right', protocol: 'presigned URL · 15 min · visible parts only', route: { kind: 'gutter', offset: 22, side: 'right' } },
    { id: 'cloudfront>apigw', from: 'cloudfront', to: 'apigw', protocol: 'origin secret', route: { kind: 'straight' } },
    { id: 'cloudfront>agent', from: 'cloudfront', fromHandle: 'left', to: 'agent', protocol: '/agent/*', route: { kind: 'elbow' } },
    { id: 'agent>apigw', from: 'agent', fromHandle: 'right', to: 'apigw', toHandle: 'left', protocol: 'MCP · origin secret', route: { kind: 'straight' } },
    { id: 'apigw>query', from: 'apigw', to: 'query', protocol: 'REST', route: apiRoutes.get('query')! },
    ...services.map((s): Link => ({ id: `apigw>${s.id}`, from: 'apigw', to: s.id, protocol: 'REST', route: apiRoutes.get(s.id)! })),
    ...graphs.map((o): Link => ({ id: `query>${o.id}`, from: 'query', to: o.id, protocol: 'SPARQL SERVICE', route: queryRoutes.get(o.id)! })),
    // Along its long vertical, clear of the Ontop rails' tags beside it.
    { id: 'query>neptune', from: 'query', to: 'neptune', protocol: 'SPARQL', route: { kind: 'straight', tag: 'rotated' } },
    ...ontop.map((o): Link => ({ id: `${o.id}>aurora`, from: o.id, to: 'aurora', toHandle: `top-${o.plm}`, protocol: 'SQL', route: { kind: 'straight' } })),
    { id: `${ontopCore.id}>atelier-core`, from: ontopCore.id, to: 'atelier-core', protocol: 'SQL', route: { kind: 'straight' } },
    ...plmSvc.map((s): Link => ({ id: `${s.id}>aurora`, from: s.id, fromHandle: 'left', to: 'aurora', toHandle: `left-${s.plm}`, protocol: 'JDBC', route: { kind: 'gutter', offset: COL_GAP / 2 } })),
    { id: `${coreSvc.id}>atelier-core`, from: coreSvc.id, fromHandle: 'left', to: 'atelier-core', toHandle: 'left', protocol: 'JDBC', route: { kind: 'gutter', offset: COL_GAP / 2 } },
    { id: `${coreSvc.id}>demo-change`, from: coreSvc.id, fromHandle: 'left', to: 'demo-change', toHandle: 'left', protocol: 'JDBC', route: { kind: 'gutter', offset: COL_GAP / 2 } },
    // The event wiring (docs/contract.md, "Freshness and the change feed"): each PLM service publishes its own facts, the
    // core service publishes Atelier's after writing the links graph, the rules deliver to the loader, the loader writes the
    // file index and logs corrections through the core service.
    ...plmSvc.map((s, i): Link => ({ id: `${s.id}>eventbridge`, from: s.id, fromHandle: 'top-right', to: 'eventbridge', toHandle: 'in-left', protocol: 'PutEvents · part.value.corrected · part.cad.published', route: { kind: 'bus', y: plmEventY, tag: 'source' }, tagless: i > 0 })),
    { id: `${coreSvc.id}>eventbridge`, from: coreSvc.id, fromHandle: 'top-right', to: 'eventbridge', toHandle: 'in-right', protocol: 'PutEvents · interface.link.added', route: { kind: 'bus', y: coreEventY, tag: 'source' } },
    { id: `${coreSvc.id}>neptune`, from: coreSvc.id, fromHandle: 'out-right', to: 'neptune', toHandle: 'in-left', protocol: 'Graph Store POST / PUT', route: { kind: 'bus', y: writeY } },
    { id: 'eventbridge>loader', from: 'eventbridge', fromHandle: 'right', to: 'loader', protocol: 'rules · part.value.corrected · part.cad.published', route: { kind: 'elbow' } },
    { id: 'loader>neptune', from: 'loader', to: 'neptune', toHandle: 'in-right', protocol: 'SPARQL UPDATE', route: { kind: 'bus', y: Y.data - 30 } },
    { id: `loader>${coreSvc.id}`, from: 'loader', fromHandle: 'top-left', to: coreSvc.id, toHandle: 'bottom', protocol: 'REST · POST /core/changes', route: { kind: 'bus', y: logY, tag: 'source' } },
  ];

  const layers: Layer[] = [
    { label: 'Browser', y: Y.browser + 18 },
    { label: 'Edge', y: Y.edge + 21 },
    { label: 'API', y: Y.api + 21 },
    { label: 'Services', y: Y.services + 32 },
    { label: 'Virtual graphs', y: Y.virtual + 25 },
    { label: 'Data', y: Y.data + 68 },
  ];
  return { components, links, layers };
}
