// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The cells a fixture-mode correction writes, found as a PLM service finds them: the table and column by the
// catalogue, the row by its key. A cell reads and writes the fixture model in place (a feature of the seed, a part
// and every bill-of-materials node of it, a reference row, a supplier offer), so the next answer reads the new value.
// Never part of a production build.
import type { CatalogueColumn, CellValue, FeatureKind, Part } from '../api/types';
import { eachNode } from './fixture-bom';
import { catalogues } from './fixture-catalogue';
import { offerById } from './fixture-purchasing';
import { referenceRows } from './fixture-references';
import { KG_PER, LIFECYCLE_STATE, parts, seedFeatures, type SeedFeature } from './fixture-seed';

export interface FixtureCell {
  /** How the column is typed: a number, a text, or a flag. */
  type: 'number' | 'text' | 'flag';
  get: () => CellValue;
  set: (v: CellValue) => void;
}

const CLASS: Record<string, FeatureKind> = { 'atelier:Plug': 'plug', 'atelier:Fastener': 'fastener', 'atelier:HydraulicCoupling': 'coupling' };
const NUMBER = new Set(['BigDecimal', 'Integer']);
const typeOf = (c: CatalogueColumn): FixtureCell['type'] => (c.javaType === 'Boolean' ? 'flag' : NUMBER.has(c.javaType) ? 'number' : 'text');
/** A field of a seed feature or row, read and written as is. */
const field = (obj: object, name: string, type: FixtureCell['type']): FixtureCell => ({
  type, get: () => ((obj as Record<string, CellValue>)[name] ?? null), set: (v) => { (obj as Record<string, CellValue>)[name] = v; },
});

/** The seed fields of each feature class, by ontology term; a unit column by the term of the column it gives the unit of. */
const FEATURE_FIELDS: Record<FeatureKind, Record<string, string>> = {
  plug: { connectorType: 'connectorType', pinCount: 'pinCount' },
  fastener: { fastenerStandard: 'standard', diameter: 'dia', 'unit:diameter': 'diaUnit', fastenerCount: 'count', gripLength: 'grip', 'unit:gripLength': 'gripUnit' },
  coupling: { couplingStandard: 'standard', dashSize: 'dash', pressureRating: 'rating', 'unit:pressureRating': 'ratingUnit', fluid: 'fluid' },
};

function featureCell(s: SeedFeature, term: string, type: FixtureCell['type']): FixtureCell | null {
  const axis = /^position([XYZ])$/.exec(term)?.[1]?.toLowerCase();
  if (axis) return field(s.pos, axis, type);
  if (term.startsWith('unit:position')) return field(s, 'unit', type);
  const name = FEATURE_FIELDS[s.kind][term];
  return name ? field(s, name, type) : null;
}

/** A part's lifecycle word is stated wherever the part is: its row and every bill-of-materials node of it. */
function lifecycleCell(plm: string, key: string, part: Part | undefined): FixtureCell {
  const nodes: { lifecycle?: string; lifecycleState?: string }[] = [];
  eachNode((n) => { if (n.plm === plm && n.id === key) nodes.push(n); });
  const all = [...(part ? [part] : []), ...nodes];
  return {
    type: 'text',
    get: () => all[0]?.lifecycle ?? null,
    set: (v) => {
      for (const x of all) {
        x.lifecycle = String(v);
        x.lifecycleState = LIFECYCLE_STATE[String(v)];
      }
    },
  };
}

function partCell(plm: string, key: string, term: string, type: FixtureCell['type']): FixtureCell | null {
  const part = parts.find((p) => p.plm === plm && p.id === key);
  let known = !!part;
  eachNode((n) => { if (n.plm === plm && n.id === key) known = true; });
  if (!known) return null;
  if (term === 'lifecycleLabel') return lifecycleCell(plm, key, part);
  if (!part) return null;
  if (term === 'mass') {
    return {
      type,
      get: () => part.mass?.value ?? null,
      set: (v) => {
        const unit = part.mass?.unit ?? (plm === 'uk' ? 'LB' : 'KiloGM');
        part.mass = { value: Number(v), unit, kg: Math.round(Number(v) * (KG_PER[unit] ?? 1) * 1e4) / 1e4 };
      },
    };
  }
  if (term === 'revision') return field(part, 'revision', type);
  return null;
}

/** The cell `column` of the row `key` in the PLM's `table`; throws as the PLM service answers a request it cannot apply. */
export function cellAt(plm: string, table: string, key: string, column: string): FixtureCell {
  const entity = catalogues[plm]?.entities.find((e) => e.table === table);
  if (!entity) throw new Error(`${plm}_plm has no table ${table}`);
  const col = entity.columns.find((c) => c.column === column);
  if (!col) throw new Error(`${plm}_plm.${table} has no column ${column}`);
  const unitOf = entity.columns.find((c) => c.unitColumn === column)?.ontologyTerm;
  const term = (col.ontologyTerm ?? (unitOf ? `unit:${unitOf}` : '')).replace(/atelier:/g, '');
  const type = typeOf(col);
  const kind = entity.ontologyClass ? CLASS[entity.ontologyClass] : undefined;
  let cell: FixtureCell | null = null;
  if (kind) {
    const s = seedFeatures.find((x) => x.plm === plm && x.kind === kind && x.id === key);
    if (!s) throw new Error(`${plm}_plm.${table} has no row with key ${key}`);
    cell = featureCell(s, term, type);
  } else if (entity.ontologyClass === 'atelier:Part') {
    cell = partCell(plm, key, term, type);
    if (!cell && !parts.some((p) => p.plm === plm && p.id === key)) throw new Error(`${plm}_plm.${table} has no row with key ${key}`);
  } else if (entity.ontologyClass === 'atelier:ExternalReference') {
    const r = referenceRows.find((x) => x.plm === plm && x.id === key);
    if (!r) throw new Error(`${plm}_plm.${table} has no row with key ${key}`);
    cell = term === 'remoteUrn' ? field(r, 'remoteUrn', type) : term === 'expectedRevision' ? field(r, 'expectedRevision', type) : null;
  } else if (entity.ontologyClass === 'atelier:SupplierOffer') {
    const o = offerById(plm, key);
    if (!o) throw new Error(`${plm}_plm.${table} has no row with key ${key}`);
    cell = term === 'leadTimeDays' ? field(o, 'leadTimeDays', type) : term === 'preferred' ? field(o, 'preferred', type) : null;
  }
  if (!cell) throw new Error(`${plm}_plm.${table}.${column} is not a column a correction may write in this fixture`);
  return cell;
}

/** Why a value cannot go into the cell, or null: the PLM's column type, and its catalogue's words or form. */
export function refusal(plm: string, table: string, column: string, type: FixtureCell['type'], v: CellValue): string | null {
  const col = catalogues[plm]?.entities.find((e) => e.table === table)?.columns.find((c) => c.column === column);
  if (type === 'number') return typeof v === 'number' && Number.isFinite(v) ? null : `${column} takes a finite number`;
  if (type === 'flag') return typeof v === 'boolean' ? null : `${column} takes true or false`;
  if (typeof v !== 'string' || v.trim() === '') return `${column} takes a text`;
  if (col?.accepts && !col.accepts.includes(v)) return `${column} takes one of ${col.accepts.join(', ')}`;
  return null;
}
