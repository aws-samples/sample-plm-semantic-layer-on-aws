// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Response shapes of the query service and PLM service APIs (docs/contract.md).

export type Rule = 'unit' | 'position' | 'connector' | 'orphan' | 'fastener' | 'hydraulic' | 'kind' | 'doubleMate';

/** The reference rules: sh:Warning findings on the part that makes an external reference, never an interface verdict. */
export type ReferenceRule = 'danglingReference' | 'staleRevision';

/** A reference the shapes passed is `ok`; one whose target the profile may not see is `not-evaluable`. */
export type ReferenceStatus = ReferenceRule | 'ok' | 'not-evaluable';

export type Status = 'pass' | 'fail' | 'not-evaluable';

export type FeatureKind = 'plug' | 'fastener' | 'coupling';

/** The rule of a released item that depends on a working or blocked one: a finding on the item, never an interface verdict. */
export const LIFECYCLE_CONFLICT = 'lifecycleConflict';

/** The rule of a gear driven by a gear of another module: a finding on the driven gear, never an interface verdict. */
export const MESH_MODULE = 'meshModule';

/** A PLM record a finding is about beyond its focus: the rule's sh:value, e.g. a lifecycle dependency, an offer, a reference. */
export interface FindingValue {
  /** Lower-case PLM code. */
  plm: string;
  /** The kind segment of the record's IRI: part, offer, ref. */
  kind: string;
  /** Native key of the record in its PLM. */
  id: string;
}

/** A release finding on a part, from a sh:Warning shape or from policy; it never fails an interface. */
export interface PartFinding {
  rule: string;
  message: string;
  /** The record the result names besides the part; absent when it names none. */
  value?: FindingValue;
}

export interface Part {
  redacted?: false;
  id: string;
  plm: string;
  name: string;
  /** English name from the layer's labels graph, in the parts answer; the native name for a UK part. */
  nameEn?: string;
  /** S3 key from the file index (atelier:cadFile), kept for display; null until the owning PLM publishes the file. */
  cadFile: string | null;
  /** Presigned S3 URL, valid 15 minutes; null when no file is issued for this part. */
  cadUrl: string | null;
  /** The PLM's own file reference for the part (atelier:sourceFileRef), as its table spells it. */
  sourceFileRef?: string | null;
  /** Export-control tag from atelier_core.part_tag; absent on an untagged part. */
  jurisdiction?: string;
  releasableTo?: string;
  /** PLM code that wrote the tag when it published the part. */
  taggedBy?: string;
  /** Supplier that built the part, from atelier:builtBy in the file index (the first by name when several did); absent when the owning PLM builds it. */
  supplier?: string;
  /** Every supplier that built the part, then every supplier with an offer for it (parts answers only), as "name, town"; absent when none. */
  suppliers?: PartSupplier[];
  /** Release findings on the part (e.g. `cadMissing`); absent when none. */
  findings?: PartFinding[];
  /** Revision in the owning PLM's own form (01, A, 1, C1); absent when the PLM states none. */
  revision?: string;
  /** Lifecycle state in the owning PLM's own word (Freigegeben, Publié, Liberado, Released). */
  lifecycle?: string;
  /** Canonical state of `lifecycle` from the ontology's lifecycle scheme: WORKING, RELEASED, BLOCKED or SUPERSEDED. */
  lifecycleState?: string;
  /** Mass as stored (KiloGM, or LB in the British PLM) with `kg` converted. */
  mass?: Quantity;
  material?: string;
  /** PART, SOFTWARE or DOCUMENT. */
  partType?: string;
  /** A gear's tooth count; absent for a part that is no gear or a cluster of several gears. */
  toothCount?: number;
  /** A gear's module as stored (MilliM, or IN in the British PLM) with `mm` converted. */
  gearModule?: Quantity;
  /** Outside the subtree an answer was asked for: the far side of one of its interfaces, shown for context only. */
  context?: true;
}

/** A part the viewer's profile may not see: no id, name or file. */
export interface RedactedPart {
  redacted: true;
  plm: string;
}

export type PartEntry = Part | RedactedPart;

export interface Vec3 {
  x: number;
  y: number;
  z: number;
}

/** A measured property as stored, with its normalised value when a unit was known. */
export interface Quantity {
  value: number;
  unit: string | null;
  mm?: number | null;
  bar?: number | null;
  kg?: number | null;
}

export type PropertyValue = Quantity | string | number | boolean | null;

export interface Feature {
  redacted?: false;
  id: string;
  plm: string;
  partId: string;
  kind: FeatureKind;
  /** Class-specific values, keyed by ontology local name (connectorType, diameter, ...). */
  properties: Record<string, PropertyValue>;
  source: { x: number | null; y: number | null; z: number | null; unit: string | null };
  positionMm: Vec3 | null;
  matesWith: string[];
}

/** A feature on a hidden part: no id, values or position. */
export interface RedactedFeature {
  redacted: true;
  plm: string;
}

export type FeatureEntry = Feature | RedactedFeature;

/** An unmated feature of the other part at an orphan's position, offered as the mate to publish. */
export interface CandidateMate {
  id: string;
  iri: string;
  plm: string;
  /** Native id of the part the candidate sits on. */
  part?: string;
  /** Position as stored, in its unit. */
  position?: Feature['source'];
  connectorType?: string;
  pinCount?: number;
}

export interface Violation {
  rule: Rule;
  shape: string;
  message: string;
  focusNode: string;
  features: string[];
  detail: Record<string, unknown>;
  /** Orphan rule only: candidates for "Publish link", nearest first; empty when none is visible at that position. */
  candidateMates?: CandidateMate[];
}

export interface Interface {
  id: string;
  /** Key of the product the interface belongs to; its id is unique within that product only. */
  product?: string;
  label: string;
  status: Status;
  toleranceMm: number | null;
  parts: PartEntry[];
  features: FeatureEntry[];
  violations: Violation[];
}

export interface PartSupplier {
  name: string;
  role: 'built' | 'offered';
}

export const isPart = (p: PartEntry): p is Part => !p.redacted;
/** The suppliers that built the part, as one phrase; null when its owning PLM builds it. */
export const builtBy = (p: Part): string | null => (p.suppliers ?? []).filter((s) => s.role === 'built').map((s) => s.name).join(' and ') || null;
/** A visible part with geometry: the parts listing also names assemblies and site kits, which have no CAD file; a part without a type is a PART. */
export const isGeometric = (p: PartEntry): p is Part => isPart(p) && (p.partType ?? 'PART') === 'PART';
export const isFeature = (f: FeatureEntry): f is Feature => !f.redacted;

export interface Call {
  endpoint: string;
  kind: 'virtual' | 'materialized';
  requests: number;
  triples: number;
  ms: number;
  /** Total bytes of the request texts sent to the endpoint. */
  requestBytes?: number;
  largestRequestBytes?: number;
}

export interface Timings {
  totalMs: number;
  federationMs: number;
  validationMs: number;
}

/** The export-control policy the service applied to this answer. */
export interface Policy {
  profile: string;
  releasable: string[];
  /** SPARQL FILTER text added to every SERVICE arm. */
  filter: string;
  /** IRIs of parts with no tag in atelier_core: visible to the export-control officer only. */
  untagged?: string[];
  /** Who called: `agent` when the call carried x-atelier-actor, `user` otherwise. */
  actor?: string;
}

/**
 * The subtree an answer is rooted at (`&root=`): an assembly of the product and every item under it,
 * resolved across sites through external references. Absent on a product-wide answer.
 */
export interface Subtree {
  root: string;
  /** Lower-case code of the root's site. */
  plm: string;
  product: string;
  /** Null when the root is hidden from the profile. */
  name: string | null;
  /** The profile may not see the root: nothing under it is expanded. */
  redacted: boolean;
  /** Resolution rounds that added items; 1 is the owning site only. */
  depth: number;
  rounds: { round: number; roots: { id: string; plm: string }[]; items: number }[];
  /** Items of the subtree the profile may see. */
  items: number;
  /** Context parts in the answer; 0 outside the interfaces answer. */
  context: number;
  /** The product rules (massLimit, massScale) are not evaluated on a subtree. */
  productRules: 'not-evaluable';
}

export interface QueryEnvelope {
  provenance: { calls: Call[] };
  sparql: string;
  timings: Timings;
  policy: Policy;
}

export interface PartsResponse extends QueryEnvelope {
  parts: PartEntry[];
  subtree?: Subtree;
}

/**
 * One placement of a part: [x, y, z, rx, ry, rz], translations in millimetres in the product frame,
 * rotations in degrees about its fixed x, y and z axes applied in that order (p' = Rz.Ry.Rx.p + t).
 * The identity is the part's STEP as authored.
 */
export type Placement = number[];

/** GET /api/query/placements: the world placements of every visible geometric part of the scope, reference occurrence first. */
export interface Placements extends QueryEnvelope {
  product: string;
  parts: { id: string; plm: string; occurrences: Placement[] }[];
  /** Occurrences listed across the parts. */
  occurrences: number;
  subtree?: Subtree;
}

export interface InterfacesResponse extends QueryEnvelope {
  interfaces: Interface[];
  /** Release findings over the visible parts, counted per rule (`{ cadMissing: n }`); `{}` when none. */
  findings?: Record<string, number>;
  subtree?: Subtree;
}

export interface InterfaceResponse extends QueryEnvelope {
  interface: Interface;
}

export type Envelope = QueryEnvelope;

/** One product as GET /api/query/products/list lists it; `partCount` is the number of its parts the profile may see. */
export interface ProductListing {
  key: string;
  name: string;
  /** The coordinate frame of the product's CAD and feature positions; null when the core states none. */
  frame: string | null;
  partCount: number;
}

export interface ProductListResponse extends QueryEnvelope {
  products: ProductListing[];
}

/**
 * One product as GET /api/query/products lists it: the listing with the product rules' results. The screens hold the
 * listing first; the rules' fields are absent until that answer arrives.
 */
export interface Product extends ProductListing {
  /** Findings of the product rules over every site's parts (massLimit, massScale), each once; absent when none. */
  findings?: PartFinding[];
  /** The product's mass limit and its status for the profile; absent when the product states no limit. */
  massLimit?: MassLimit;
  /** lifecycleConflict findings on the visible items: a released item over a working or blocked dependency, once per pair. */
  lifecycleConflicts?: number;
}

/** `not-evaluable` when `hiddenItems` of the product's items are hidden from the profile: a partial view raises no violation. */
export interface MassLimit {
  status: 'pass' | 'fail' | 'not-evaluable';
  limitKg: number;
  hiddenItems?: number;
}

export interface ProductsResponse extends QueryEnvelope {
  products: Product[];
}

/** One item of the virtual bill of materials the profile may see. */
export interface BomNode {
  redacted?: false;
  id: string;
  /** Lower-case PLM code; absent on the product root, which no PLM holds. */
  plm?: string;
  /** Name in the owning PLM's own language. */
  name: string;
  /** English name from the layer's labels graph; absent on the product root. */
  nameEn?: string;
  /** PRODUCT for the root, ASSEMBLY for site kits and assemblies, SOFTWARE or DOCUMENT for an item without geometry, else PART. */
  partType: string;
  revision?: string;
  /** Lifecycle state in the owning PLM's own word. */
  lifecycle?: string;
  /** Canonical state of `lifecycle`: WORKING, RELEASED, BLOCKED or SUPERSEDED. */
  lifecycleState?: string;
  /** Per parent. */
  quantity: number;
  /** In the whole product: the quantities multiplied down the tree. */
  occurrences: number;
  unitMassKg?: number;
  /** `unitMassKg` times `occurrences`. */
  extendedMassKg?: number;
  children: BomItem[];
}

/** An item the profile may not see: its PLM and count only, never expanded. */
export interface BomHidden {
  redacted: true;
  plm: string;
  quantity: number;
  occurrences: number;
}

export type BomItem = BomNode | BomHidden;

/** Occurrences and mass of the part nodes of one site (`plm`), or of the product when `plm` is null. */
export interface BomRollup {
  plm: string | null;
  occurrences: number;
  massKg: number;
  /** Part nodes that state no mass. */
  withoutMass: number;
  hiddenOccurrences: number;
}

/** GET /api/query/bom?product=: the product's bill of materials, which no PLM holds whole. */
/**
 * An external reference a visible part makes: another site's part named by URN with the revision the part expects.
 * `target` is absent when the URN resolves to no part or to one the profile may not see.
 */
export interface ExternalReference {
  id: string;
  plm: string;
  /** Native id of the local part that makes the reference. */
  part: string;
  remoteUrn: string;
  target?: { id: string; plm: string };
  quantity?: number;
  expectedRevision?: string;
  currentRevision?: string;
  lifecycleState?: string;
  status: ReferenceStatus;
  /** The shape's message on a failing reference. */
  message?: string;
  note?: string;
}

/** GET /api/query/references?product=: every reference of the product's visible parts and how many have each status. */
export interface ReferencesResponse extends QueryEnvelope {
  product: string;
  references: ExternalReference[];
  counts: Partial<Record<ReferenceStatus, number>>;
  subtree?: Subtree;
}

export interface Bom extends QueryEnvelope {
  product: string;
  root: BomNode;
  /** In the order of the root's children: fr, de, uk, es. */
  sites: BomRollup[];
  total: BomRollup;
  subtree?: Subtree;
}

export interface CatalogueColumn {
  field: string;
  column: string;
  javaType: string;
  description: string | null;
  unit: string | null;
  unitColumn: string | null;
  ontologyTerm: string | null;
  undescribed: boolean;
  unitMissing: boolean;
  /**
   * The words a text column takes (a lifecycle column lists the site's words in the order WORKING, RELEASED, BLOCKED,
   * SUPERSEDED), or the units a unit column takes; absent when the column has no closed vocabulary.
   */
  accepts?: string[];
  /** A regular expression a text column's whole value matches: a revision form, the URN syntax. */
  pattern?: string;
}

export interface CatalogueEntity {
  entity: string;
  table: string;
  ontologyClass: string | null;
  description: string | null;
  columns: CatalogueColumn[];
}

export interface Catalogue {
  /** Owner code: a PLM code, or the Atelier core store's. */
  plm: string;
  entities: CatalogueEntity[];
}

/** One row of atelier_core.part_tag as GET /api/core/tags returns it. */
export interface PartTag {
  plm: string;
  nativeKey: string;
  jurisdiction: string;
  releasableTo: string;
  taggedBy: string;
  taggedAt: string;
}

export interface TagsResponse {
  tags: PartTag[];
}

/** GET /api/{source}/health: any JSON answer means the service is up. */
export type Health = Record<string, unknown>;

/** GET /api/query/mcp/tools: the tools the MCP server in the query service publishes. */
export interface McpTools {
  tools: { name: string; description: string }[];
}

/** GET /api/query/demo/health (public, no data): how the change feed is wired, as the query service reports it. */
export interface DemoHealth {
  /** Null: the query service publishes nothing itself. */
  eventBus: string | null;
  /** The service that writes the links graph and publishes Atelier's event: `atelier-core`. */
  writer: string;
  /** `source/detail-type` of each event published, by the PLM services and by the core service. */
  publishes: string[];
  /** One line per subscription, from the event to what it reaches. */
  subscriptions: string[];
  /** Triples of each released graph as bundled in the image. */
  releasedGraphs: { links: number; fileindex: number };
  /** Table the core service logs value corrections in. */
  changeLog: string;
}

// Demo controls by role (docs/contract.md, "Freshness and the change feed"): the browser calls the owning service.

/** One row of atelier_core.demo_change: a value corrected in a PLM's own table, logged when its part.value.corrected event reaches Atelier. */
export interface ValueChange {
  id: number;
  plm: string;
  table: string;
  key: string;
  column: string;
  before: string | number | null;
  after: string | number | null;
  actor: string;
  purpose: string;
  at: string;
}

/** One triple of the graph diff: terms shortened as the answers name them, the IRIs the shortening dropped kept beside. */
export interface GraphTriple {
  s: string;
  p: string;
  o: string;
  /** Full IRI of the subject; null for a blank node. */
  sIri: string | null;
  /** Full IRI of the object; null for a literal or blank node. */
  oIri: string | null;
  /** Lower-case PLM code of the subject IRI (`/atelier/{plm}/`); null when the subject is not a PLM's part or feature. */
  plm: string | null;
}

/** GET /api/query/demo/changes: the log of value corrections, and the live named graphs diffed against the released files. */
export interface Changes {
  values: ValueChange[];
  graph: {
    added: GraphTriple[];
    removed: GraphTriple[];
    triples: { links: number; fileindex: number };
  };
}

/** A value a correction writes: a number, a text, a flag, or null to clear the cell. */
export type CellValue = string | number | boolean | null;

/** One cell of a correction: the native table, the row's key, the column and its new value. */
export interface CellUpdate {
  table: string;
  key: string;
  column: string;
  value: CellValue;
}

/**
 * POST /api/{plm}/demo/update: one correction, or several (`updates`) applied in one transaction, in that PLM's own
 * tables; the service then publishes one part.value.corrected event naming every row.
 */
export type ChangeRequest = (CellUpdate & { purpose: string }) | { updates: CellUpdate[]; purpose: string };

/** One row a correction changed, with the value before and the value the database holds after it. */
export interface ChangedRow {
  table: string;
  key: string;
  column: string;
  before: CellValue;
  after: CellValue;
}

export interface ChangeResult {
  plm: string;
  rows: ChangedRow[];
  at: string;
}

/** POST /api/core/links: the core service appends the link and its inverse to the links graph, then emits interface.link.added. */
export interface PublishLinkRequest {
  from: string;
  to: string;
}

export interface PublishLinkResult {
  triples: { links: number };
  eventId: string;
}

/** POST /api/core/equivalences: the part IRIs a user confirms as one item. */
export interface ConfirmEquivalenceRequest {
  parts: string[];
}

export interface ConfirmEquivalenceResult {
  parts: string[];
  triples: { links: number };
  eventId: string | null;
}

/** POST /api/{plm}/demo/events/cad: one part.cad.published event from the PLM; the links loader sets atelier:cadFile in the file index. */
export interface PublishCadRequest {
  part: string;
  cadFile: string;
}

// GET /api/query/interfaces/{id}/evidence: what each component contributed to one answer.

export interface EvidenceTable {
  plm: string;
  table: string;
  keys: string[];
}

export interface EvidenceArm {
  endpoint: string;
  kind: 'virtual' | 'materialized';
  sparql: string;
  /** Turtle: exactly the triples this endpoint returned. */
  triples: string;
  /** Ontop's generated SQL for the arm, when obtainable. */
  sql: string | null;
  tables: EvidenceTable[];
  /** Measured while the evidence was captured; a source not involved in the interface has 0 requests. */
  tripleCount: number;
  requests: number;
  ms: number;
}

export interface EvidenceShape {
  rule: Rule;
  shape: string;
  turtle: string;
}

export interface Evidence {
  interfaceId: string;
  sparql: string;
  arms: EvidenceArm[];
  merged: { triples: number; turtle: string };
  shacl: { shapes: EvidenceShape[]; report: string };
  timings: Timings;
  policy: Policy;
}

export type Cell = string | number | boolean | null;

/** GET /api/{plm}/tables/{table}?keys=...: native rows by primary key, native column names. */
export interface TableRows {
  table: string;
  /** Native primary-key column; absent when the service cannot name it. */
  keyColumn?: string;
  columns: string[];
  rows: Cell[][];
}

/**
 * One identifying value of a member as its site states it: the text or the number as stored, the unit and the length in
 * mm of a length, and for a text resolved in a scheme the concept it names, with the thickness written in it in mm.
 */
export interface MemberValue {
  /** The item class's attribute, e.g. innerDiameter, compound, legend. */
  attribute: string;
  stored?: string;
  /** IN or MilliM, for a length. */
  unit?: string;
  mm?: number;
  concept?: string;
  conceptLabel?: string;
  /** The size its standard designates supplies the length: the site states only the standard (AS568-014). */
  fromStandard?: boolean;
}

/** A purchased part as its site describes it: its standard as written, its identifying values and its shelf life. */
export interface EquivalentMember {
  plm: string;
  id: string;
  /** The part IRI, as POST /api/core/equivalences takes it. */
  iri: string;
  name: string;
  standard?: string;
  values: MemberValue[];
  shelfLifeMonths?: number;
}

/**
 * An identifying attribute of a group with its first member's value: a length in mm, the concept a resolved text names
 * with its label as `text` (and the thickness in mm when the text states one), or the text as written.
 */
export interface GroupAttribute {
  attribute: string;
  label: string;
  mm?: number;
  concept?: string;
  text?: string;
}

/** The item stocked under `partNumbers` part numbers in `sites` sites: `stockLines` lines today, one once confirmed. */
export interface Stocking {
  partNumbers: number;
  sites: number;
  stockLines: number;
  stockLinesOnceConfirmed: number;
}

/**
 * Parts that are one purchased item: of one item class of the ontology, agreeing on every identifying attribute the
 * class names within its tolerance.
 */
export interface EquivalentGroup {
  /** Notation of the item class, e.g. fastener, o-ring, placard. */
  itemClass: string;
  classLabel: string;
  /** The identifying attributes in the class's order, with the first member's values. */
  attributes: GroupAttribute[];
  /** The shortest shelf life a member states. */
  shelfLifeMonths?: number;
  stocking: Stocking;
  /** A user confirmed the members as one item (owl:sameAs between every two in the links graph); a proposal otherwise. */
  confirmed: boolean;
  members: EquivalentMember[];
}

/** GET /api/query/equivalents?product=: the purchased items the product's parts share across the sites. */
export interface EquivalentsResponse extends QueryEnvelope {
  product: string;
  groups: EquivalentGroup[];
}

export interface SupplierOffer {
  /** Native key of the offer row in its PLM's supplier offer table. */
  id: string;
  partId: string;
  partName: string;
  supplierPartNumber: string;
  leadTimeDays: number;
  preferred: boolean;
}

/** A supplier as one site lists it, with its offers there. */
export interface SupplierSite {
  plm: string;
  id: string;
  location: string | null;
  offers: SupplierOffer[];
}

/** One supplier, by name, across the sites that list it. */
export interface Supplier {
  name: string;
  sites: SupplierSite[];
}

/** A part with exactly one supplier offer. */
export interface SingleSource {
  plm: string;
  id: string;
  name: string;
  supplier: string;
  leadTimeDays: number;
}

/** A part whose preferred offers disagree on the lead time: the conflictingLeadTime finding. */
export interface LeadTimeConflict {
  plm: string;
  id: string;
  name: string;
  message: string;
}

/** GET /api/query/suppliers?product=: the suppliers of the product's parts. */
export interface SuppliersResponse extends QueryEnvelope {
  product: string;
  suppliers: Supplier[];
  singleSource: SingleSource[];
  conflicts: LeadTimeConflict[];
}

/** One cell of a preview, as the release form builds it: the site, its native table, the row key, the column and the value. */
export interface PreviewCell {
  plm: string;
  table: string;
  key: string;
  column: string;
  value: CellValue;
}

/** POST /api/query/preview: the cells of one product, or of the subtree of `root`. */
export interface PreviewRequest {
  product: string;
  root: string | null;
  cells: PreviewCell[];
}

/** One rule result of a preview: an interface result names its features, a part finding the record it is about. */
export interface PreviewResult {
  rule: string;
  message: string;
  features?: string[];
  value?: FindingValue;
}

interface PreviewDiff {
  fixed: PreviewResult[];
  stillFailing: PreviewResult[];
  newlyFailing: PreviewResult[];
}

export interface PreviewInterface extends PreviewDiff {
  id: string;
  product: string;
  label: string | null;
  /** Status before and after: pass, fail or not-evaluable. */
  before: Status | null;
  after: Status | null;
}

export interface PreviewPart extends PreviewDiff {
  id: string;
  plm: string;
  name: string | null;
}

export interface PreviewProduct extends PreviewDiff {
  key: string;
}

/** The answer of POST /api/query/preview: the rules before and after the cells, written nowhere. */
export interface PreviewResponse {
  product: string;
  root?: string;
  cells: { plm: string; table: string; key: string; column: string; value: CellValue; triples: { subject: string; predicate: string; before: string | null; after: string | null }[] }[];
  interfaces: PreviewInterface[];
  parts: PreviewPart[];
  products: PreviewProduct[];
  tally: { fixed: number; stillFailing: number; newlyFailing: number };
  provenance: { calls: Call[] };
  timings: Timings;
  policy: Policy;
}

/** What a rule attaches to: an interface feature, a part, an external reference or a product. */
export type RuleFocus = 'interface' | 'part' | 'reference' | 'product';

/** One SPARQL query of a shape: a constraint (sh:sparql) or a target (sh:target with sh:select). */
export interface RuleSparql {
  role: 'constraint' | 'target';
  message: string | null;
  query: string;
}

/** An ontology term a shape reads, as a prefixed name, with its definition from the ontology. */
export interface RuleTerm {
  term: string;
  kind: 'class' | 'property' | 'concept' | 'other';
  definition: string | null;
}

/** One shape of ontology/shapes.ttl as GET /api/query/rules describes it. */
export interface RuleInfo {
  /** ateliersh:rule, the name the query service reports. */
  name: string;
  /** IRI of the shape. */
  shape: string;
  /** Violation fails an interface or a product; Warning is a finding. */
  severity: 'Violation' | 'Warning';
  focus: RuleFocus;
  /** What the shape requires: its rdfs:comment. */
  message: string;
  /** Plain-language explanation: its sh:description. */
  description: string;
  /** The shape as Turtle, as loaded. */
  turtle: string;
  sparql: RuleSparql[];
  terms: RuleTerm[];
}

/** GET /api/query/rules: every rule, sorted by name; the policy does not filter it. */
export interface RulesResponse {
  rules: RuleInfo[];
}

/** One failing record of one rule for the profile: an interface, a part (a reference rule fails the part that makes it) or a product. */
export interface RuleFailure {
  rule: string;
  product: string;
  kind: 'interface' | 'part' | 'product';
  id: string;
  /** Lower-case PLM code of a part; null for an interface or a product. */
  plm: string | null;
}

/** GET /api/query/rules/failures[?product=]: one entry per failing record, sorted by rule, product, kind, id. */
export interface RuleFailuresResponse extends QueryEnvelope {
  failures: RuleFailure[];
  /** With a product: per rule, the number of other products where it fails for the profile. */
  otherProducts?: Record<string, number>;
}

/** A variant group of a product: one installation slot, its options, the default (the base product) first. */
export interface VariantGroup {
  key: string;
  name: string | null;
  selects: string | null;
  defaultOption: string;
  options: string[];
}

/** GET /api/query/variants?product=: the product's variant groups. */
export interface VariantGroups {
  product: string;
  groups: VariantGroup[];
}

export interface VariantOption {
  key: string;
  isDefault: boolean;
  source?: string;
  applicability?: string;
  portsNotModelled: string[];
}

/** A part named by its PLM and native id, or a marker for one the profile may not see. */
export type VariantPart = { id: string; plm: string; supplier?: string } | RedactedPart;

/** One configuration's use of a port: its interface and status, the host's feature, the mated part and its feature. */
export interface VariantSide {
  interfaceId: string;
  label: string;
  status: Status;
  rules: Rule[];
  feature?: FeatureEntry;
  mate?: VariantPart;
  mateFeature?: FeatureEntry;
}

export type PortChange = 'added' | 'removed' | 'changed' | 'same' | 'not-modelled';

/** A port of a host part, the default configuration's side against the option's. */
export interface VariantPort {
  host: VariantPart;
  port?: string;
  change: PortChange;
  differences: string[];
  against?: VariantSide;
  option?: VariantSide;
}

export interface VariantConfiguration {
  option: string;
  interfaces: number;
  tally: { pass: number; fail: number; notEvaluable: number };
  occurrences: Record<string, number>;
  occurrencesTotal: number;
  massKg: number;
  productFindings: { rule: string; message: string }[];
}

/** GET /api/query/variant-diff?product=&group=&option=: the option against its group's default, port by port. */
export interface VariantDiff extends QueryEnvelope {
  product: string;
  group: VariantGroup;
  option: VariantOption;
  against: VariantOption;
  ports: VariantPort[];
  removed: { items: VariantPart[]; interfaces: string[] };
  added: { items: VariantPart[]; interfaces: string[] };
  configuration: VariantConfiguration;
  baseline: VariantConfiguration;
  interfaces: Interface[];
}
