// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Unfiltered fixture model of six products of data/products/*.json, the files the PLM seeds and
// the link store are generated from (docs/contract.md, "Detailed product structure"): the parts with
// their export classification and supplier, every plug, fastener and coupling pair as each PLM
// stores it, and the rule results over them. Nothing about the products is written here; the part
// and interface counts are whatever the files hold. Profiles are applied later.
import ornithopter from '../../../../data/products/ornithopter.json';
import aerialScrew from '../../../../data/products/aerial-screw.json';
import windTurbine from '../../../../data/products/wind-turbine.json';
import rover from '../../../../data/products/rover.json';
import cubesat from '../../../../data/products/cubesat.json';
import differenceEngine from '../../../../data/products/difference-engine.json';
import type { CandidateMate, Feature, FeatureKind, Interface, Part, PartFinding, PropertyValue, Quantity, Vec3, Violation } from '../api/types';
import type { JsonCoupling, JsonFastener, JsonPart, JsonPlug, JsonProduct, JsonRow } from './fixture-json';
import { byId } from '../ui/ids';

export const files: JsonProduct[] = [ornithopter, aerialScrew, windTurbine, rover, cubesat, differenceEngine];

export interface SeedProduct {
  key: string;
  name: string;
  frame: string;
  /** Native ids of the product's parts: what `?product=` scopes a listing to. */
  partIds: Set<string>;
}

/** The products in file order, the first being the one the viewer opens on. */
export const products: SeedProduct[] = files.map((d) => ({
  key: d.product.key, name: d.product.name, frame: d.frame, partIds: new Set(d.parts.map((p) => p.id)),
}));

interface SeedBase {
  id: string;
  part: string;
  plm: string;
  /** Position as the PLM stores it, in `unit`. */
  pos: Vec3;
  unit: string | null;
}

export type SeedFeature = SeedBase & (
  | { kind: 'plug'; connectorType: string; pinCount: number }
  | { kind: 'fastener'; standard: string; dia: number; diaUnit: string; count: number; grip: number; gripUnit: string }
  | { kind: 'coupling'; standard: string; dash: number; rating: number; ratingUnit: string; fluid: string }
);

const MM_PER: Record<string, number> = { MilliM: 1, IN: 25.4 };
const BAR_PER: Record<string, number> = { BAR: 1, PSI: 0.06894757293168 };
const r4 = (v: number) => Math.round(v * 1e4) / 1e4;
const enc = (id: string) => encodeURIComponent(id);
const SHAPES = 'https://example.com/atelier/shapes#';

export const featureIri = (f: { plm: string; kind: FeatureKind; id: string }) => `https://example.com/atelier/${f.plm}/${f.kind}/${enc(f.id)}`;

/** The ateliersh:PartCadShape warning (docs/contract.md, "Interface features beyond plugs") on a part the file index names no file for. */
export const CAD_MISSING: PartFinding = { rule: 'cadMissing', message: 'no CAD file published in the file index for this part' };

/** Canonical lifecycle state of each PLM's word, as the atelier:Lifecycle scheme of ontology/atelier.ttl states it. */
export const LIFECYCLE_STATE: Record<string, string> = Object.fromEntries(Object.entries({
  WORKING: ['In Arbeit', 'En cours', 'Borrador', 'Draft'], RELEASED: ['Freigegeben', 'Publié', 'Liberado', 'Released'],
  BLOCKED: ['Gesperrt', 'Bloqué', 'Bloqueado', 'Frozen'], SUPERSEDED: ['Ersetzt', 'Remplacé', 'Sustituido', 'Superseded'],
}).flatMap(([state, words]) => words.map((w) => [w, state])));
export const KG_PER: Record<string, number> = { KiloGM: 1, LB: 0.45359237 };

/** The attributes a PLM states about a part: the mass in the PLM's storage unit (pounds in the British PLM), as written. */
function attributesOf(p: JsonPart): Partial<Part> {
  const e = p.extended;
  if (!e) return {};
  const unit = p.plm === 'UK' ? 'LB' : 'KiloGM';
  const value = e.mass === undefined ? undefined : Number(String(e.mass).replace(',', '.'));
  return {
    ...(e.revision !== undefined ? { revision: String(e.revision) } : {}),
    ...(e.lifecycle ? { lifecycle: e.lifecycle, lifecycleState: LIFECYCLE_STATE[e.lifecycle] } : {}),
    ...(value !== undefined ? { mass: { value, unit, kg: r4(value * KG_PER[unit]) } } : {}),
    ...(e.material ? { material: e.material } : {}),
    partType: e.type ?? 'PART',
  };
}

/**
 * Every part carries its tag in atelier_core.part_tag, written by its own PLM; the CAD file is served by
 * the dev server. A part the released file index has no entry for has no key and no URL, and carries
 * the cadMissing finding, until a fixture-mode publication sets them; the PLM's own file reference
 * names the file all the same.
 */
export const seedPart = (p: JsonPart): Part => ({
  id: p.id, plm: p.plm.toLowerCase(), name: p.name,
  cadFile: p.cadPending ? null : p.cadFile, cadUrl: p.cadPending ? null : `/${p.cadFile}`, sourceFileRef: p.cadFile,
  jurisdiction: p.classification.jurisdiction, releasableTo: p.classification.releasableTo, taggedBy: p.plm.toUpperCase(),
  ...(p.supplier ? { supplier: p.supplier, suppliers: [{ name: p.supplier, role: 'built' as const }] } : {}),
  ...(p.cadPending ? { findings: [CAD_MISSING] } : {}),
  ...attributesOf(p),
  ...englishOf(p.plm, p.name, p.extended?.nameEn),
});

export const parts: Part[] = files.flatMap((d) => d.parts).map(seedPart);

/** The English name the labels graph gives an item, as the parts answer carries it: a UK item's is its native name. */
function englishOf(plm: string, name: string, nameEn?: string): Pick<Part, 'nameEn'> {
  const en = plm.toUpperCase() === 'UK' ? name : nameEn;
  return en ? { nameEn: en } : {};
}

/** English names of the fixture products' parts and assemblies by `plm|id`, for the bill of materials. */
export const englishNames = new Map<string, string>([
  ...parts.flatMap((p) => (p.nameEn ? [[`${p.plm}|${p.id}`, p.nameEn] as [string, string]] : [])),
  ...files.flatMap((f) => ((f as { extended?: { assemblies?: { id: string; plm: string; name: string; nameEn?: string }[] } }).extended?.assemblies ?? [])
    .flatMap((a) => {
      const en = englishOf(a.plm, a.name, a.nameEn).nameEn;
      return en ? [[`${a.plm.toLowerCase()}|${a.id}`, en] as [string, string]] : [];
    })),
]);
/** Parts whose CAD file the released file index does not name. */
export const cadPendingIds = new Set(files.flatMap((d) => d.parts).filter((p) => p.cadPending).map((p) => p.id));
const partById = new Map(parts.map((p) => [p.id, p]));
const plmOf = (partId: string) => partById.get(partId)!.plm;

const seedBase = (r: JsonRow): SeedBase => ({
  id: r.id, part: r.part, plm: plmOf(r.part), unit: r.unit,
  pos: { x: Number(r.position.x), y: Number(r.position.y), z: Number(r.position.z) },
});
const plugSeed = (r: JsonPlug): SeedFeature => ({ kind: 'plug', ...seedBase(r), connectorType: r.connectorType, pinCount: r.pinCount });
const fastenerSeed = (r: JsonFastener): SeedFeature => ({
  kind: 'fastener', ...seedBase(r), standard: r.standard, dia: Number(r.diameter), diaUnit: r.diameterUnit, count: r.count,
  grip: Number(r.gripLength), gripUnit: r.gripUnit,
});
const couplingSeed = (r: JsonCoupling): SeedFeature => ({
  kind: 'coupling', ...seedBase(r), standard: r.standard, dash: r.dashSize, rating: Number(r.rating), ratingUnit: r.ratingUnit, fluid: r.fluid,
});

const quantity = (value: number, unit: string, into: 'mm' | 'bar'): Quantity => {
  const k = (into === 'mm' ? MM_PER : BAR_PER)[unit];
  return { value, unit, [into]: k === undefined ? null : r4(value * k) };
};

function propertiesOf(s: SeedFeature): Record<string, PropertyValue> {
  switch (s.kind) {
    case 'plug':
      return { connectorType: s.connectorType, pinCount: s.pinCount };
    case 'fastener':
      return { fastenerStandard: s.standard, diameter: quantity(s.dia, s.diaUnit, 'mm'), fastenerCount: s.count, gripLength: quantity(s.grip, s.gripUnit, 'mm') };
    case 'coupling':
      return { couplingStandard: s.standard, dashSize: s.dash, pressureRating: quantity(s.rating, s.ratingUnit, 'bar'), fluid: s.fluid };
  }
}

function toFeature(s: SeedFeature, matesWith: string[]): Feature {
  const k = s.unit ? MM_PER[s.unit] : null;
  return {
    id: s.id, plm: s.plm, partId: s.part, kind: s.kind, properties: propertiesOf(s),
    source: { ...s.pos, unit: s.unit },
    positionMm: k === null ? null : { x: r4(s.pos.x * k), y: r4(s.pos.y * k), z: r4(s.pos.z * k) },
    matesWith,
  };
}

const qty = (f: Feature, prop: string) => f.properties[prop] as Quantity;
const plain = (f: Feature, prop: string) => f.properties[prop];
const differ = (a: Feature, b: Feature, props: string[]) => props.some((p) => plain(a, p) !== plain(b, p));
const pair = (a: Feature, b: Feature, props: string[]) => Object.fromEntries(props.map((p) => [p, [plain(a, p), plain(b, p)]]));
const barText = (q: Quantity) => (q.unit === 'BAR' ? `${q.value} bar` : `${q.value} ${q.unit} = ${q.bar?.toFixed(1)} bar`);

/** Unmated features of the other part, same class, within the tolerance of the orphan on every axis: what "Publish link" pre-fills. */
function candidatesFor(orphan: Feature, features: Feature[], tol: number): CandidateMate[] {
  const at = orphan.positionMm;
  if (!at) return [];
  return features
    .filter((c) => c.kind === orphan.kind && c.partId !== orphan.partId && c.matesWith.length === 0 && c.positionMm
      && (['x', 'y', 'z'] as const).every((a) => Math.abs(c.positionMm![a] - at[a]) <= tol))
    .map((c) => ({
      id: c.id, iri: featureIri(c), plm: c.plm, part: c.partId, position: c.source,
      ...(c.kind === 'plug' ? { connectorType: String(plain(c, 'connectorType')), pinCount: Number(plain(c, 'pinCount')) } : {}),
    }));
}

/** `spares`: unmated features of the interface's parts declared on no interface, candidates for an orphan's link. */
function violationsOf(features: Feature[], tol: number, ifId: string, spares: Feature[]): Violation[] {
  const out: Violation[] = [];
  const byId = new Map(features.map((f) => [f.id, f]));
  const result = (rule: Violation['rule'], shape: string, f: Feature, ids: string[], message: string, detail: Record<string, unknown>, extra: Partial<Violation> = {}) =>
    out.push({ rule, shape: `${SHAPES}${shape}`, focusNode: featureIri(f), features: ids, message, detail, ...extra });
  for (const f of features) {
    const who = `${f.plm.toUpperCase()} ${f.kind} ${f.id}`;
    if (f.source.unit === null) {
      for (const axis of ['x', 'y', 'z'] as const) {
        result('unit', 'UnitShape', f, [f.id],
          `${who} has no unit on its ${axis} position (stored value ${f.source[axis]}), so it cannot be converted to mm`,
          { axis, unit: null, storedValue: f.source[axis] });
      }
    }
    if (f.matesWith.length === 0) {
      result('orphan', 'OrphanShape', f, [f.id], `${who} is declared on interface ${ifId} but mates with no ${f.kind}`, { interface: ifId },
        { candidateMates: candidatesFor(f, [...features, ...spares], tol) });
    }
    // ateliersh:MateCardinalityShape: a feature mates with at most one feature; a second published mate fails the interface.
    if (f.matesWith.length > 1) {
      result('doubleMate', 'MateCardinalityShape', f, [f.id, ...f.matesWith],
        `${who} mates with ${f.matesWith.length} features (${f.matesWith.join(', ')}); a feature mates with at most one`, { matesWith: f.matesWith });
    }
    for (const mateId of f.matesWith) {
      const m = byId.get(mateId)!;
      if (f.kind !== m.kind) {
        result('kind', 'KindShape', f, [f.id, m.id], `${f.id} (${f.kind}) mates with ${m.id} (${m.kind}), a feature of another class`, { kind: [f.kind, m.kind] });
        continue;
      }
      if (f.kind === 'plug' && differ(f, m, ['connectorType', 'pinCount'])) {
        result('connector', 'ConnectorShape', f, [f.id, m.id],
          `${f.id} (${plain(f, 'connectorType')}, ${plain(f, 'pinCount')} pins) and ${m.id} (${plain(m, 'connectorType')}, ${plain(m, 'pinCount')} pins) are mated but do not match`,
          pair(f, m, ['connectorType', 'pinCount']));
      }
      if (f.kind === 'fastener') {
        const [da, db] = [qty(f, 'diameter').mm ?? NaN, qty(m, 'diameter').mm ?? NaN];
        const delta = r4(Math.abs(da - db));
        if (delta > 0.05 || differ(f, m, ['fastenerStandard', 'fastenerCount'])) {
          result('fastener', 'FastenerShape', f, [f.id, m.id],
            delta > 0.05
              ? `${f.id} (diameter ${da} mm) and ${m.id} (diameter ${db} mm) are mated but their diameters differ by ${delta.toFixed(2)} mm; tolerance 0.05 mm`
              : `${f.id} and ${m.id} are mated but their fastener standard or count do not match`,
            { ...pair(f, m, ['fastenerStandard', 'fastenerCount']), diameterMm: [da, db], deltaMm: delta, toleranceMm: 0.05 });
        }
      }
      if (f.kind === 'coupling') {
        const [ra, rb] = [qty(f, 'pressureRating').bar ?? NaN, qty(m, 'pressureRating').bar ?? NaN];
        const pct = Math.round((Math.abs(ra - rb) / Math.max(ra, rb)) * 1000) / 10;
        if (pct > 2 || differ(f, m, ['couplingStandard', 'dashSize', 'fluid'])) {
          result('hydraulic', 'HydraulicShape', f, [f.id, m.id],
            pct > 2
              ? `${f.id} (${barText(qty(f, 'pressureRating'))}) and ${m.id} (${barText(qty(m, 'pressureRating'))}) are mated but their pressure ratings differ by ${pct.toFixed(1)} %; tolerance 2 %`
              : `${f.id} and ${m.id} are mated but their coupling standard, dash size or fluid do not match`,
            { ...pair(f, m, ['couplingStandard', 'dashSize', 'fluid']), pressureRatingBar: [ra, rb], deltaPercent: pct, tolerancePercent: 2 });
        }
      }
      if (!f.positionMm || !m.positionMm) continue;
      for (const axis of ['x', 'y', 'z'] as const) {
        const delta = r4(Math.abs(f.positionMm[axis] - m.positionMm[axis]));
        if (delta > tol) {
          result('position', 'PositionShape', f, [f.id, m.id],
            `${f.id} and ${m.id} are ${delta.toFixed(1)} mm apart on ${axis}; tolerance ${tol.toFixed(1)} mm`,
            { axis, deltaMm: delta, toleranceMm: tol });
        }
      }
    }
  }
  return out;
}

/** Every feature of every interface, mated pairs adjacent, as the PLMs store them; positions are corrected in place. */
export const seedFeatures: SeedFeature[] = [];

interface Seeded {
  id: string;
  label: string;
  tol: number;
  partIds: string[];
  pairs: SeedFeature[][];
  unmated: SeedFeature[];
}

const seeded: Seeded[] = files.flatMap((d) => d.interfaces).map((itf) => {
  const pairs: SeedFeature[][] = [
    ...(itf.pairs ?? []).map((p) => p.map(plugSeed)),
    ...(itf.fasteners ?? []).map((p) => p.map(fastenerSeed)),
    ...(itf.couplings ?? []).map((p) => p.map(couplingSeed)),
  ];
  const unmated = (itf.unmatedPlugs ?? []).map(plugSeed);
  seedFeatures.push(...pairs.flat(), ...unmated);
  return { id: itf.id, label: itf.label, tol: Number(itf.toleranceMm), partIds: itf.parts, pairs, unmated };
});

/** Plugs declared on no interface and mated to nothing, by native id; a published link brings one onto its interface. */
const spares = new Map(files.flatMap((d) => d.sparePlugs ?? []).map(plugSeed).map((s) => [s.id, s]));
seedFeatures.push(...spares.values());

/** Links published at runtime in fixture mode, by native id: one end on the interface, the other on it or a spare plug of one of its parts. */
export const publishedLinks: [from: string, to: string][] = [];

/** The rule results over the seed as it stands now, in id order as the service lists them. */
const build = (): Interface[] =>
  seeded.map(({ id, label, tol, partIds, pairs, unmated }): Interface => {
    const mates = new Map<string, string[]>();
    for (const [a, b] of pairs) {
      mates.set(a.id, [b.id]);
      mates.set(b.id, [a.id]);
    }
    for (const u of unmated) mates.set(u.id, []);
    // A spare plug of one of the interface's parts joins the interface once a published link names it from a feature on it.
    const joined: SeedFeature[] = [];
    for (const [from, to] of publishedLinks) {
      for (const [on, other] of [[from, to], [to, from]]) {
        const spare = spares.get(other);
        if (mates.has(on) && spare && partIds.includes(spare.part) && !mates.has(other)) {
          mates.set(other, []);
          joined.push(spare);
        }
      }
      if (mates.has(from) && mates.has(to)) {
        mates.get(from)!.push(to);
        mates.get(to)!.push(from);
      }
    }
    const features = [...pairs.flat(), ...unmated, ...joined].map((s) => toFeature(s, mates.get(s.id)!));
    const linked = new Set(publishedLinks.flat());
    const spareFeatures = [...spares.values()].filter((s) => partIds.includes(s.part) && !linked.has(s.id)).map((s) => toFeature(s, []));
    const violations = violationsOf(features, tol, id, spareFeatures);
    return {
      id, label, status: violations.length ? 'fail' : 'pass', toleranceMm: tol,
      parts: partIds.map((p) => partById.get(p)!), features, violations,
    };
  }).sort((a, b) => byId(a.id, b.id));

export let interfaces: Interface[] = build();

/** After a fixture-mode correction or link: the next answer reads the seed as it now stands. */
export const rebuildInterfaces = () => {
  interfaces = build();
};
