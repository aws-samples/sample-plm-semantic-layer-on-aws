// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The correction that fixes a finding, as the owning site would release it: its table, row key and column from the
// site's own catalogue, the value as stored, and a value proposed from the evidence the answer carries. The layer
// never edits a site's data: a proposal is what the site's engineer reviews, edits and releases in the site's PLM.
// Pure functions over the answers' shapes, with type-only imports, so tests/fix-cycle.mjs runs the same proposals
// under Node as the screen shows.
import type {
  Catalogue,
  CatalogueColumn,
  CatalogueEntity,
  CellValue,
  ChangeRequest,
  ExternalReference,
  Feature,
  Part,
  PreviewRequest,
  PropertyValue,
  Quantity,
  SupplierOffer,
} from '../../api/types';

export type Axis = 'x' | 'y' | 'z';

/** How a cell's value is entered: a number, free text, one of `choices`, or a flag. */
export type CellInput = 'number' | 'text' | 'choice' | 'flag';

/** One cell a correction writes: where it is in the site's own tables, what it holds, and the value proposed. */
export interface Cell {
  table: string;
  /** The row's key: the native id of the feature, part, reference or offer. */
  key: string;
  column: string;
  /** The value stored, as the answer shows it. */
  before: CellValue;
  /** The value proposed; null leaves the entry to the engineer (no evidence names one). */
  value: CellValue;
  input: CellInput;
  /** The site's words or units a choice takes. */
  choices?: string[];
  /** The form a text value matches, from the site's catalogue. */
  pattern?: string;
  /** Unit the column stores, QUDT local name; null when the column holds no quantity. */
  unit: string | null;
  /** What the cell is, in words: "x position", "connector type". */
  label: string;
}

/** A correction one site releases in one request: every cell in its own tables, applied in one transaction. */
export interface Proposal {
  /** Lower-case code of the PLM that owns the records. */
  plm: string;
  cells: Cell[];
  /** Logged with the change, in the site's change log and in Atelier's. */
  purpose: string;
}

/** A proposal, or why the site's catalogue or the answer cannot place one. */
export type Planned = { ok: true; proposal: Proposal } | { ok: false; reason: string };

const lc = (s: string) => s.toLowerCase();
const fail = (reason: string): Planned => ({ ok: false, reason });
const planned = (plm: string, cells: Cell[], purpose: string): Planned =>
  cells.length ? { ok: true, proposal: { plm: lc(plm), cells, purpose } } : fail('the records already agree');

const FEATURE_CLASS: Record<Feature['kind'], string> = { plug: 'atelier:Plug', fastener: 'atelier:Fastener', coupling: 'atelier:HydraulicCoupling' };
const PART = 'atelier:Part';

/** The site's table of a class; for parts, the one keyed by the part id (a closure view states the class too). */
export function entityOf(catalogue: Catalogue, ontologyClass: string): CatalogueEntity | undefined {
  return catalogue.entities.find((e) => e.ontologyClass === ontologyClass
    && (ontologyClass !== PART || e.columns.some((c) => c.ontologyTerm === 'atelier:identifier')));
}

const columnOf = (entity: CatalogueEntity | undefined, term: string): CatalogueColumn | undefined =>
  entity?.columns.find((c) => c.ontologyTerm === `atelier:${term}`);

// Unit conversions to the base the rules compare in (mm, bar, kg), from ontology/units.ttl.
const TO_BASE: Record<string, number> = { MilliM: 1, IN: 25.4, BAR: 1, PSI: 0.0689475729, KiloGM: 1, LB: 0.45359237, GM: 0.001 };
const UNIT_LABEL: Record<string, string> = { MilliM: 'mm', IN: 'in', BAR: 'bar', PSI: 'psi', KiloGM: 'kg', LB: 'lb', GM: 'g' };
export const unitLabel = (unit: string | null) => (unit ? (UNIT_LABEL[unit] ?? unit) : 'no unit');

/** A value at the precision its site stores: four decimals for the inch and psi columns, three otherwise. */
const stored = (value: number, unit: string | null) => {
  const k = unit === 'IN' || unit === 'PSI' ? 1e4 : 1e3;
  return Math.round(value * k) / k;
};

/** A base value (mm, bar, kg) in `unit`; null when the unit has no known conversion. */
export const fromBase = (value: number, unit: string | null): number | null => {
  const k = unit ? TO_BASE[unit] : undefined;
  return k === undefined ? null : stored(value / k, unit);
};

/** Ontology local name to words: fastenerStandard becomes "fastener standard". */
const words = (term: string) => term.replace(/([a-z0-9])([A-Z])/g, '$1 $2').toLowerCase();

const isQuantity = (v: PropertyValue | undefined): v is Quantity => typeof v === 'object' && v !== null && 'value' in v;
const baseOf = (q: Quantity) => q.mm ?? q.bar ?? q.kg ?? null;

/** A text cell of a catalogue column: a choice when the site's words are listed, free text otherwise. */
const textCell = (column: CatalogueColumn, table: string, key: string, before: CellValue, value: CellValue, label: string): Cell => ({
  table, key, column: column.column, before, value, label, unit: null,
  input: column.accepts ? 'choice' : 'text',
  ...(column.accepts ? { choices: column.accepts } : {}),
  ...(column.pattern ? { pattern: column.pattern } : {}),
});

// Position: the outlier record moves onto its mate on the failing axis.

/** The mate's position on the axis in the unit the target column stores; the mate's stored value when no conversion is known. */
export function mateValue(mate: Feature, axis: Axis, unit: string | null): number | null {
  const mm = mate.positionMm?.[axis];
  if (mm == null) return mate.source[axis];
  return fromBase(mm, unit) ?? mate.source[axis];
}

export function positionProposal(catalogue: Catalogue, feature: Feature, mate: Feature, axis: Axis, interfaceId: string): Planned {
  const entity = entityOf(catalogue, FEATURE_CLASS[feature.kind]);
  const column = columnOf(entity, `position${axis.toUpperCase()}`);
  if (!entity || !column) return fail(`the catalogue has no column annotated atelier:position${axis.toUpperCase()} for a ${feature.kind}`);
  const unit = column.unit ?? feature.source.unit;
  return planned(feature.plm, [{
    table: entity.table, key: feature.id, column: column.column, before: feature.source[axis], value: mateValue(mate, axis, unit),
    input: 'number', unit, label: `${axis} position`,
  }], `Align ${feature.id} with ${mate.id} on ${axis} (${interfaceId})`);
}

// Unit: the row's unit column gets the unit the site stores for that quantity.

/** `quantity` is the violation's ontology local name, e.g. positionX or diameter. */
export function unitProposal(catalogue: Catalogue, feature: Feature, quantity: string, interfaceId: string): Planned {
  const entity = entityOf(catalogue, FEATURE_CLASS[feature.kind]);
  const column = columnOf(entity, quantity);
  if (!entity || !column) return fail(`the catalogue has no column annotated atelier:${quantity} for a ${feature.kind}`);
  if (!column.unitColumn) return fail(`${column.column} stores the fixed unit ${column.unit ?? 'none'}: no unit column to correct`);
  const unitColumn = entity.columns.find((c) => c.column === column.unitColumn);
  const units = unitColumn?.accepts ?? [];
  if (units.length === 0) return fail(`the catalogue names no unit the ${entity.table}.${column.unitColumn} column stores`);
  const q = feature.properties[quantity];
  const before = quantity.startsWith('position') ? feature.source.unit : isQuantity(q) ? q.unit : null;
  const what = quantity.startsWith('position') ? 'position' : words(quantity);
  return planned(feature.plm, [{
    table: entity.table, key: feature.id, column: column.unitColumn, before, value: units[0], input: 'choice', choices: units,
    unit: null, label: `${what} unit`,
  }], `State the ${what} unit of ${feature.id} (${interfaceId})`);
}

// Connector, fastener, hydraulic: one side takes the other side's values for every compared property that differs.

/** The properties a rule compares, and for a quantity how far apart in mm or bar the values may be. */
const COMPARED: Record<'connector' | 'fastener' | 'hydraulic', { term: string; tolerance?: (a: number, b: number) => number }[]> = {
  connector: [{ term: 'connectorType' }, { term: 'pinCount' }],
  fastener: [{ term: 'fastenerStandard' }, { term: 'fastenerCount' }, { term: 'diameter', tolerance: () => 0.05 }],
  hydraulic: [{ term: 'couplingStandard' }, { term: 'dashSize' }, { term: 'fluid' }, { term: 'pressureRating', tolerance: (a, b) => 0.02 * Math.max(a, b) }],
};
export type MatchRule = keyof typeof COMPARED;
export const isMatchRule = (rule: string): rule is MatchRule => rule in COMPARED;

/** `feature` takes the values of `mate`: the record of `feature`'s site is the one corrected. */
export function matchProposal(catalogue: Catalogue, rule: MatchRule, feature: Feature, mate: Feature, interfaceId: string): Planned {
  const entity = entityOf(catalogue, FEATURE_CLASS[feature.kind]);
  if (!entity) return fail(`the catalogue has no table for a ${feature.kind}`);
  const cells: Cell[] = [];
  for (const { term, tolerance } of COMPARED[rule]) {
    const mine = feature.properties[term];
    const theirs = mate.properties[term];
    const column = columnOf(entity, term);
    if (theirs === undefined || theirs === null || !column) continue;
    if (tolerance && isQuantity(theirs)) {
      const a = isQuantity(mine) ? baseOf(mine) : null;
      const b = baseOf(theirs);
      if (b === null || (a !== null && Math.abs(a - b) <= tolerance(a, b))) continue;
      const unit = column.unit ?? (isQuantity(mine) ? mine.unit : null);
      const value = fromBase(b, unit);
      if (value === null) continue;
      cells.push({ table: entity.table, key: feature.id, column: column.column, before: isQuantity(mine) ? mine.value : null, value,
        input: 'number', unit, label: words(term) });
    } else if (mine !== theirs && !isQuantity(theirs)) {
      const before = isQuantity(mine) || mine === undefined ? null : mine;
      cells.push(typeof theirs === 'number'
        ? { table: entity.table, key: feature.id, column: column.column, before, value: theirs, input: 'number', unit: null, label: words(term) }
        : textCell(column, entity.table, feature.id, before, String(theirs), words(term)));
    }
  }
  return planned(feature.plm, cells, `Match ${feature.id} to ${mate.id} (${rule}, ${interfaceId})`);
}

// External references: the referring site corrects its own reference row.

const URN = /^urn:plm:(de|fr|es|uk):part:(.+)$/;
/** The part key a URN's local id names: the British native form UK/nnnn is the key UK-nnnn. */
const keyOfLocal = (local: string) => local.replace(/^UK\//, 'UK-');
/** The URN of a part: urn:plm:<site>:part:<local id>, the British key UK-nnnn written UK/nnnn. */
export const urnOf = (plm: string, id: string) => `urn:plm:${lc(plm)}:part:${lc(plm) === 'uk' ? id.replace(/^UK-/, 'UK/') : id}`;

/**
 * A dangling URN: when another site holds a part with the URN's local id, the URN with that site's code, and the
 * expected revision that part is at when the reference expects another; otherwise an empty entry, for the engineer.
 */
export function danglingProposal(catalogue: Catalogue, ref: ExternalReference, parts: Part[]): Planned {
  const entity = entityOf(catalogue, 'atelier:ExternalReference');
  const urn = columnOf(entity, 'remoteUrn');
  if (!entity || !urn) return fail('the catalogue has no column annotated atelier:remoteUrn');
  const local = ref.remoteUrn.match(URN)?.[2] ?? ref.remoteUrn.replace(/^.*:/, '');
  const holder = parts.find((p) => p.id === keyOfLocal(local) && lc(p.plm) !== lc(ref.plm));
  const cells = [textCell(urn, entity.table, ref.id, ref.remoteUrn, holder ? urnOf(holder.plm, holder.id) : null, 'remote URN')];
  const expected = columnOf(entity, 'expectedRevision');
  if (holder?.revision !== undefined && expected && String(holder.revision) !== ref.expectedRevision) {
    cells.push(textCell(expected, entity.table, ref.id, ref.expectedRevision ?? null, String(holder.revision), 'expected revision'));
  }
  return planned(ref.plm, cells, holder
    ? `Point ${ref.part}'s reference ${ref.id} at ${lc(holder.plm).toUpperCase()} ${holder.id}`
    : `Correct the URN of ${ref.part}'s reference ${ref.id}`);
}

/** A stale revision: the reference expects the revision its target is at. */
export function staleProposal(catalogue: Catalogue, ref: ExternalReference): Planned {
  const entity = entityOf(catalogue, 'atelier:ExternalReference');
  const expected = columnOf(entity, 'expectedRevision');
  if (!entity || !expected) return fail('the catalogue has no column annotated atelier:expectedRevision');
  if (ref.lifecycleState === 'SUPERSEDED') return fail('the target is superseded: no revision of it is current, the reference must name its replacement');
  if (ref.currentRevision === undefined) return fail('the target states no revision');
  return planned(ref.plm, [textCell(expected, entity.table, ref.id, ref.expectedRevision ?? null, ref.currentRevision, 'expected revision')],
    `Expect revision ${ref.currentRevision} of ${ref.remoteUrn} on ${ref.part}`);
}

// Lifecycle: the dependency's site releases it in its own word, or the dependent item's site blocks the item.

const STATE_INDEX = { RELEASED: 1, BLOCKED: 2 } as const;
export type LifecycleMove = keyof typeof STATE_INDEX;

/** `part` moves to the site's word for `state`; the catalogue lists a site's words in canonical order. */
export function lifecycleProposal(catalogue: Catalogue, part: Part, state: LifecycleMove, because: string): Planned {
  const entity = entityOf(catalogue, PART);
  const column = columnOf(entity, 'lifecycleLabel');
  const word = column?.accepts?.[STATE_INDEX[state]];
  if (!entity || !column || !word) return fail('the catalogue lists no lifecycle words for the part table');
  return planned(part.plm, [textCell(column, entity.table, part.id, part.lifecycle ?? null, word, 'lifecycle')],
    `${state === 'RELEASED' ? 'Release' : 'Block'} ${part.id} (${because})`);
}

// Mass: a part's mass in the site's unit; a site storing grams divides every flagged part's mass by 1000.

const massColumn = (catalogue: Catalogue) => {
  const entity = entityOf(catalogue, PART);
  return { entity, column: columnOf(entity, 'mass') };
};

/** No value is proposed: the limit is a design budget, the engineer chooses the part and its mass. */
export function massProposal(catalogue: Catalogue, part: Part, product: string): Planned {
  const { entity, column } = massColumn(catalogue);
  if (!entity || !column) return fail('the catalogue has no column annotated atelier:mass');
  const before = part.mass?.value ?? null;
  return planned(part.plm, [{ table: entity.table, key: part.id, column: column.column, before, value: before, input: 'number',
    unit: column.unit ?? part.mass?.unit ?? null, label: 'mass' }], `Revise the mass of ${part.id} within the ${product} limit`);
}

/** One release, every flagged part of the site: grams entered in a kilogram column become kilograms. */
export function massScaleProposal(catalogue: Catalogue, parts: Part[], product: string): Planned {
  const { entity, column } = massColumn(catalogue);
  if (!entity || !column) return fail('the catalogue has no column annotated atelier:mass');
  if (parts.length === 0) return fail('no visible part carries the finding');
  const plms = new Set(parts.map((p) => lc(p.plm)));
  if (plms.size > 1) return fail('the flagged parts belong to several sites');
  const cells = parts.filter((p) => p.mass).map((p): Cell => ({
    table: entity.table, key: p.id, column: column.column, before: p.mass!.value, value: stored(p.mass!.value / 1000, column.unit),
    input: 'number', unit: column.unit ?? p.mass!.unit, label: 'mass',
  }));
  return planned(parts[0].plm, cells, `Convert the grams of ${cells.length} ${lc(parts[0].plm).toUpperCase()} part masses to kilograms (${product})`);
}

// Lead time: the site holding the offers aligns one offer's lead time with the other, or stops preferring it.

export function leadTimeProposal(catalogue: Catalogue, plm: string, offer: SupplierOffer, other: SupplierOffer): Planned {
  const entity = entityOf(catalogue, 'atelier:SupplierOffer');
  const column = columnOf(entity, 'leadTimeDays');
  if (!entity || !column) return fail('the catalogue has no column annotated atelier:leadTimeDays');
  return planned(plm, [{ table: entity.table, key: offer.id, column: column.column, before: offer.leadTimeDays, value: other.leadTimeDays,
    input: 'number', unit: null, label: 'lead time, days' }], `Align the lead time of offer ${offer.id} (${offer.partId}) with offer ${other.id}`);
}

export function preferredProposal(catalogue: Catalogue, plm: string, offer: SupplierOffer): Planned {
  const entity = entityOf(catalogue, 'atelier:SupplierOffer');
  const column = columnOf(entity, 'preferred');
  if (!entity || !column) return fail('the catalogue has no column annotated atelier:preferred');
  return planned(plm, [{ table: entity.table, key: offer.id, column: column.column, before: offer.preferred, value: false, input: 'flag',
    unit: null, label: 'preferred' }], `Stop preferring offer ${offer.id} (${offer.partId})`);
}

// The request a proposal releases.

/** Whether an entered value is one the cell takes; the form of a text (revision, URN) is the PLM's to check, and its refusal is shown. */
export function valid(cell: Cell, value: CellValue): boolean {
  if (cell.input === 'number') return typeof value === 'number' && Number.isFinite(value);
  if (cell.input === 'flag') return typeof value === 'boolean';
  if (typeof value !== 'string' || value.trim() === '') return false;
  if (cell.input === 'choice') return (cell.choices ?? []).includes(value);
  return true;
}

/** One correction as one update, several as one batch: either way one transaction and one event in the PLM. */
export function requestOf(proposal: Proposal, values: CellValue[]): ChangeRequest {
  const updates = proposal.cells.map((c, i) => ({ table: c.table, key: c.key, column: c.column, value: values[i] }));
  return updates.length === 1 ? { ...updates[0], purpose: proposal.purpose } : { updates, purpose: proposal.purpose };
}

/** The preview of a proposal with the values entered: the same cells, for the product (and subtree) on screen. */
export function previewOf(proposal: Proposal, values: CellValue[], product: string, root: string | null = null): PreviewRequest {
  return { product, root, cells: proposal.cells.map((c, i) => ({ plm: proposal.plm, table: c.table, key: c.key, column: c.column, value: values[i] })) };
}
