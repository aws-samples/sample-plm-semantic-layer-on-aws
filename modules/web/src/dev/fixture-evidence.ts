// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Evidence and native-row fixtures built to docs/contract.md over the seed model.
import shapesTtl from '../../../../ontology/shapes.ttl?raw';
import type {
  Cell, Envelope, Evidence, EvidenceArm, EvidenceShape, Feature, FeatureKind, Interface, Part, Policy, Quantity, Rule, TableRows, Violation,
} from '../api/types';
import { isFeature, isPart } from '../api/types';
import { PART_TAG_COLUMNS, tagOf } from './fixture-core';
import { visible } from './fixture-policy';
import { featureIri, parts as allParts, seedFeatures, type SeedFeature } from './fixture-seed';
import { FIXTURE_MARKER } from './marker';

const DATA = 'https://example.com/atelier/';
const LINKS_GRAPH = `${DATA}graph/links`;
const FILE_INDEX_GRAPH = `${DATA}graph/fileindex`;
const enc = (id: string) => encodeURIComponent(id);
export const partIri = (plm: string, id: string) => `${DATA}${plm}/part/${enc(id)}`;
const ifaceIri = (id: string) => `${DATA}interface/${id}`;
const partById = new Map(allParts.map((p) => [p.id, p]));

/** Where each source's SPARQL endpoint is, as the federator addresses it. */
const endpointUrl = (source: string) => `http://ontop-${source}:8080/sparql`;

type Table = [name: string, columns: string[]];

/** Native tables and column order per PLM (docs/contract.md); the part tables are as the PLMs define them. */
const SCHEMA: Record<string, Record<'part' | FeatureKind, Table>> = {
  fr: {
    part: ['piece', ['ref_piece', 'designation', 'fichier_cao']],
    plug: ['connecteur', ['id_connecteur', 'ref_piece', 'pos_x_mm', 'pos_y_mm', 'pos_z_mm', 'type_connecteur', 'nb_broches']],
    fastener: ['fixation', ['ref_fixation', 'ref_piece', 'pos_x_mm', 'pos_y_mm', 'pos_z_mm', 'norme', 'diametre_mm', 'nombre', 'longueur_serrage_mm']],
    coupling: ['raccord_hydraulique', ['ref_raccord', 'ref_piece', 'pos_x_mm', 'pos_y_mm', 'pos_z_mm', 'norme', 'taille_dash', 'pression_bar', 'fluide']],
  },
  de: {
    part: ['bauteil', ['teil_nr', 'benennung', 'cad_datei']],
    plug: ['stecker', ['stecker_id', 'teil_nr', 'pos_x_mm', 'pos_y_mm', 'pos_z_mm', 'typ', 'polzahl']],
    fastener: ['befestiger', ['befestiger_id', 'teil_nr', 'pos_x_mm', 'pos_y_mm', 'pos_z_mm', 'norm', 'durchmesser_mm', 'anzahl', 'klemmlaenge_mm']],
    coupling: ['hydraulikkupplung', ['kupplung_id', 'teil_nr', 'pos_x_mm', 'pos_y_mm', 'pos_z_mm', 'norm', 'dash_groesse', 'nenndruck_bar', 'fluid']],
  },
  uk: {
    part: ['component', ['comp_id', 'name', 'cad_file']],
    plug: ['harness_connector', ['conn_ref', 'comp_id', 'pos_x', 'pos_y', 'pos_z', 'pos_uom', 'shell_type', 'pin_qty']],
    fastener: ['fastener', ['fast_ref', 'comp_id', 'pos_x', 'pos_y', 'pos_z', 'pos_uom', 'standard', 'dia', 'dia_uom', 'qty', 'grip', 'grip_uom']],
    coupling: ['hyd_coupling', ['cplg_ref', 'comp_id', 'pos_x', 'pos_y', 'pos_z', 'pos_uom', 'standard', 'dash', 'rating', 'rating_uom', 'fluid']],
  },
  es: {
    part: ['pieza', ['cod_pieza', 'denominacion', 'fichero_cad']],
    plug: ['conector', ['cod_conector', 'cod_pieza', 'pos_x_mm', 'pos_y_mm', 'pos_z_mm', 'tipo', 'num_contactos', 'obsoleto']],
    fastener: ['remache', ['cod_remache', 'cod_pieza', 'pos_x_mm', 'pos_y_mm', 'pos_z_mm', 'norma', 'diametro_mm', 'cantidad', 'longitud_apriete_mm']],
    coupling: ['acoplamiento', ['cod_acoplamiento', 'cod_pieza', 'pos_x_mm', 'pos_y_mm', 'pos_z_mm', 'norma', 'tamano_dash', 'presion_bar', 'fluido']],
  },
};
const KINDS: FeatureKind[] = ['plug', 'fastener', 'coupling'];
export const PLMS = Object.keys(SCHEMA);

function featureRow(s: SeedFeature): Cell[] {
  const uk = s.plm === 'uk';
  const head: Cell[] = [s.id, s.part, s.pos.x, s.pos.y, s.pos.z, ...(uk ? [s.unit] : [])];
  switch (s.kind) {
    case 'plug': return [...head, s.connectorType, s.pinCount, ...(s.plm === 'es' ? [false] : [])];
    case 'fastener': return uk ? [...head, s.standard, s.dia, s.diaUnit, s.count, s.grip, s.gripUnit] : [...head, s.standard, s.dia, s.count, s.grip];
    case 'coupling': return uk ? [...head, s.standard, s.dash, s.rating, s.ratingUnit, s.fluid] : [...head, s.standard, s.dash, s.rating, s.fluid];
  }
}
const partRow = (p: Part): Cell[] => [p.id, p.name, p.cadFile];
const tagRow = (p: Part): Cell[] | null => {
  const t = tagOf(p);
  return t ? [t.plm, t.nativeKey, t.jurisdiction, t.releasableTo, t.taggedBy, t.taggedAt] : null;
};

/** GET /api/core/tables/part_tag?keys=...: the tags of the visible parts among the keys. */
export function coreTableRows(table: string, keys: string[], pol: Policy): TableRows {
  if (table !== 'part_tag') throw new Error(`atelier_core has no table ${table}`);
  const rows = keys.flatMap((k) => {
    const p = partById.get(k);
    const row = p && visible(pol, p) ? tagRow(p) : null;
    return row ? [row] : [];
  });
  return { table, keyColumn: 'native_key', columns: PART_TAG_COLUMNS, rows };
}

/** GET /api/{plm}/tables/{table}?keys=...: rows of hidden parts, and rows of features on them, are filtered out. */
export function tableRows(plm: string, table: string, keys: string[], pol: Policy): TableRows {
  const schema = SCHEMA[plm];
  if (schema.part[0] === table) {
    const rows = keys.flatMap((k) => {
      const p = partById.get(k);
      return p && p.plm === plm && visible(pol, p) ? [partRow(p)] : [];
    });
    return { table, keyColumn: schema.part[1][0], columns: schema.part[1], rows };
  }
  const kind = KINDS.find((k) => schema[k][0] === table);
  if (!kind) throw new Error(`${plm} has no table ${table}`);
  const rows = keys.flatMap((k) => {
    const s = seedFeatures.find((f) => f.id === k && f.plm === plm && f.kind === kind);
    return s && visible(pol, partById.get(s.part)!) ? [featureRow(s)] : [];
  });
  return { table, keyColumn: schema[kind][1][0], columns: schema[kind][1], rows };
}

const PREFIXES = `@prefix atelier:  <https://example.com/atelier/ontology#> .
@prefix qudt: <http://qudt.org/schema/qudt/> .
@prefix unit: <http://qudt.org/vocab/unit/> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .
`;
const CLASS: Record<FeatureKind, string> = { plug: 'atelier:Plug', fastener: 'atelier:Fastener', coupling: 'atelier:HydraulicCoupling' };
const lit = (v: unknown) => (typeof v === 'string' ? `"${v}"` : String(v));

interface Block { turtle: string; count: number }
const block = (subject: string, predicates: string[]): Block => ({ turtle: `<${subject}> ${predicates.join(' ;\n    ')} .`, count: predicates.length });
const quantityNode = (iri: string, q: { value: number | null; unit: string | null }): Block =>
  block(iri, [`a qudt:QuantityValue`, `qudt:numericValue ${q.value}`, ...(q.unit ? [`qudt:unit unit:${q.unit}`] : [])]);

function featureBlocks(f: Feature): Block[] {
  const iri = featureIri(f);
  const p = f.properties;
  const own: Record<FeatureKind, string[]> = {
    plug: [`atelier:connectorType ${lit(p.connectorType)}`, `atelier:pinCount ${lit(p.pinCount)}`],
    fastener: [`atelier:fastenerStandard ${lit(p.fastenerStandard)}`, `atelier:diameter <${iri}/diameter>`, `atelier:fastenerCount ${lit(p.fastenerCount)}`, `atelier:gripLength <${iri}/grip>`],
    coupling: [`atelier:couplingStandard ${lit(p.couplingStandard)}`, `atelier:dashSize ${lit(p.dashSize)}`, `atelier:pressureRating <${iri}/rating>`, `atelier:fluid ${lit(p.fluid)}`],
  };
  const quantities: Record<FeatureKind, [string, string][]> = {
    plug: [], fastener: [['diameter', 'diameter'], ['gripLength', 'grip']], coupling: [['pressureRating', 'rating']],
  };
  return [
    block(iri, [
      `a ${CLASS[f.kind]}`, `atelier:ownedBy "${f.plm.toUpperCase()}"`, `atelier:identifier "${f.id}"`, `atelier:onPart <${partIri(f.plm, f.partId)}>`,
      ...own[f.kind], `atelier:positionX <${iri}/position/x>`, `atelier:positionY <${iri}/position/y>`, `atelier:positionZ <${iri}/position/z>`,
    ]),
    ...(['x', 'y', 'z'] as const).map((axis) => quantityNode(`${iri}/position/${axis}`, { value: f.source[axis], unit: f.source.unit })),
    ...quantities[f.kind].map(([prop, seg]) => quantityNode(`${iri}/${seg}`, p[prop] as Quantity)),
  ];
}

/** What a PLM's virtual graph says about a part: identity, label and its own file reference. */
const partBlock = (part: Part): Block => block(partIri(part.plm, part.id), [
  'a atelier:Part', `atelier:ownedBy "${part.plm.toUpperCase()}"`, `atelier:identifier "${part.id}"`, `atelier:label "${part.name}"`, `atelier:sourceFileRef "${part.sourceFileRef ?? part.cadFile}"`,
]);

/** What ontop-core says about the same part IRI: its export-control tag. */
const tagBlock = (part: Part): Block => {
  const t = tagOf(part)!;
  return block(partIri(part.plm, part.id), [
    `atelier:jurisdiction "${t.jurisdiction}"`, `atelier:releasableTo "${t.releasableTo}"`, `atelier:taggedBy "${t.taggedBy}"`, `atelier:taggedAt "${t.taggedAt}"^^xsd:dateTime`,
  ]);
};

const join = (blocks: Block[]): Block => ({ turtle: [PREFIXES, ...blocks.map((b) => b.turtle)].join('\n\n'), count: blocks.reduce((n, b) => n + b.count, 0) });

const ontopTurtle = (features: Feature[], parts: Part[]) => join([...features.flatMap(featureBlocks), ...parts.map(partBlock)]);

function linksTurtle(itf: Interface, features: Feature[]): Block {
  const mates = features.flatMap((f) => f.matesWith.map((m) => `<${featureIri(f)}> atelier:matesWith <${featureIri(features.find((x) => x.id === m)!)}> .`));
  const parts = itf.parts.filter(isPart);
  const turtle = `${PREFIXES}
# GRAPH <${LINKS_GRAPH}>
<${ifaceIri(itf.id)}> a atelier:Interface ;
    rdfs:label "${itf.label}" ;
    atelier:toleranceMm "${itf.toleranceMm?.toFixed(1)}"^^xsd:decimal ;
    atelier:betweenPart ${parts.map((p) => `<${partIri(p.plm, p.id)}>`).join(' , ')} ;
    atelier:declaresFeature
${features.map((f) => `        <${featureIri(f)}>`).join(' ,\n')} .

${mates.join('\n')}

# GRAPH <${FILE_INDEX_GRAPH}>
${parts.flatMap((p) => (p.cadFile === null ? [] : [`<${partIri(p.plm, p.id)}> atelier:cadFile "${p.cadFile}"${p.supplier ? ` ;\n    atelier:builtBy "${p.supplier}"` : ''} .`])).join('\n')}
`;
  const built = parts.filter((p) => p.supplier).length;
  const withCad = parts.filter((p) => p.cadFile !== null).length;
  return { turtle, count: 3 + parts.length + features.length + mates.length + withCad + built };
}

const inList = (values: string[]) => values.map((v) => `'${v}'`).join(', ');

/** Ontop's SQL for a PLM arm: the visible parts arrive as the part-key predicate, so no policy column is needed. */
function ontopSql(plm: string, features: Feature[], parts: Part[]): string {
  const s = SCHEMA[plm];
  const [partTable, partCols] = s.part;
  const partKey = partCols[0];
  const q = (alias: string, c: string) => `${alias}."${c}"`;
  const kinds = KINDS.filter((k) => features.some((f) => f.kind === k));
  const blocks = kinds.map((k) => {
    const [table, cols] = s[k];
    return `SELECT ${cols.map((c) => q('v1', c)).join(', ')}
FROM "${table}" v1
WHERE ${q('v1', cols[0])} IN (${inList(features.filter((f) => f.kind === k).map((f) => f.id))})
  AND ${q('v1', cols[1])} IN (${inList(parts.map((p) => p.id))});`;
  });
  blocks.push(`SELECT ${partCols.map((c) => q('v2', c)).join(', ')}
FROM "${partTable}" v2
WHERE ${q('v2', partKey)} IN (${inList(parts.map((p) => p.id))});`);
  return `-- ${FIXTURE_MARKER}: SQL Ontop generates for the SERVICE arm, one query per logical table.
-- The part keys come from the VALUES the query service bound after reading atelier_core: rows of hidden parts are never requested.
${blocks.join('\n\n')}`;
}

/** Ontop's SQL for the tag arm: the profile's FILTER is pushed down as the releasable_to predicate. */
const coreSql = (parts: Part[], pol: Policy) => `-- ${FIXTURE_MARKER}: SQL Ontop generates for the tag arm over atelier_core.
-- The profile's FILTER is pushed down as the releasable_to predicate, so the tags of hidden parts never leave the database.
SELECT v1."plm", v1."native_key", v1."jurisdiction", v1."releasable_to", v1."tagged_by", v1."tagged_at"
FROM "part_tag" v1
WHERE (v1."plm", v1."native_key") IN (${parts.map((p) => `('${p.plm}', '${p.id}')`).join(', ')})
  AND v1."releasable_to" IN (${inList(pol.releasable)});`;

const valuesClause = (parts: Part[]) => `VALUES ?part { ${parts.map((p) => `<${partIri(p.plm, p.id)}>`).join(' ')} }`;

// SPARQL is written one line per array element: a line with a literal group pattern is a plain string.
const ontopArmSparql = (plm: string, features: Feature[], parts: Part[]) => [
  `# ${FIXTURE_MARKER}`,
  `# Two SERVICE clauses reach ontop-${plm}: the batched feature arm and the part arm. Both bind ?part to the`,
  '# parts the tag arm found visible, so Ontop reads only their rows.',
  'SERVICE <cache:bulk+20:' + endpointUrl(plm) + '> {',
  `  ${valuesClause(parts)}`,
  '  ?f a ?class ; atelier:onPart ?part ; atelier:positionX ?qx ; atelier:positionY ?qy ; atelier:positionZ ?qz .',
  '  OPTIONAL { ?f atelier:connectorType ?type ; atelier:pinCount ?pins }',
  '  OPTIONAL { ?f atelier:fastenerStandard ?std ; atelier:diameter ?qd ; atelier:fastenerCount ?n ; atelier:gripLength ?qg }',
  '  OPTIONAL { ?f atelier:couplingStandard ?cstd ; atelier:dashSize ?dash ; atelier:pressureRating ?qr ; atelier:fluid ?fluid }',
  '  ?qx qudt:numericValue ?x . OPTIONAL { ?qx qudt:unit ?ux }',
  '  ?qy qudt:numericValue ?y . OPTIONAL { ?qy qudt:unit ?uy }',
  '  ?qz qudt:numericValue ?z . OPTIONAL { ?qz qudt:unit ?uz }',
  '}',
  '# ?f bound by the link store to:',
  features.map((f) => `#   <${featureIri(f)}>`).join('\n'),
  '',
  'SERVICE <cache:' + endpointUrl(plm) + '> {',
  `  ${valuesClause(parts)}`,
  '  ?part a atelier:Part ; atelier:label ?partName ; atelier:sourceFileRef ?ref .',
  '}',
].join('\n');

const coreArmSparql = (parts: Part[], pol: Policy) => `# ${FIXTURE_MARKER}
# Read first. The profile's FILTER is pushed into atelier_core's SQL; the ?part it leaves bound become the
# VALUES of every PLM arm, so rows of hidden parts are never requested from a PLM.
SERVICE <cache:${endpointUrl('core')}> {
  ${valuesClause(parts)}
  ?part atelier:jurisdiction ?jur ; atelier:releasableTo ?rel ; atelier:taggedBy ?by ; atelier:taggedAt ?at .
  ${pol.filter}
}`;

const neptuneArmSparql = (itf: Interface) => [
  `# ${FIXTURE_MARKER}`,
  'SERVICE <http://neptune:8182/sparql> {',
  `  GRAPH <${LINKS_GRAPH}> {`,
  '    ?if atelier:toleranceMm ?tol ; atelier:betweenPart ?bp ; atelier:declaresFeature ?f .',
  '    OPTIONAL { ?f atelier:matesWith ?mate }',
  '    OPTIONAL { ?if rdfs:label ?ifLabel }',
  `    FILTER (?if = <${ifaceIri(itf.id)}>)`,
  '  }',
  '  GRAPH <' + FILE_INDEX_GRAPH + '> { ?bp atelier:cadFile ?cad }',
  '}',
].join('\n');

const RULES: Rule[] = ['unit', 'position', 'connector', 'orphan', 'fastener', 'hydraulic', 'kind'];

/** The shape blocks of ontology/shapes.ttl, by the rule each declares. */
function shapeBlocks(): Map<Rule, string> {
  const blocks = shapesTtl.split(/\n\s*\n/);
  const prefixes = shapesTtl.split('\n').filter((l) => l.startsWith('@prefix')).join('\n');
  const out = new Map<Rule, string>();
  const rule = new RegExp(`ateliersh:rule "(${RULES.join('|')})"`);
  for (const b of blocks) {
    const m = rule.exec(b);
    if (m) out.set(m[1] as Rule, `${prefixes}\n\n${b.replace(/^(#[^\n]*\n)+/, '')}`);
  }
  return out;
}
const SHAPES = shapeBlocks();
const shapeText = (rule: Rule) => SHAPES.get(rule) ?? `# ${FIXTURE_MARKER}: ontology/shapes.ttl ships no shape for rule "${rule}" yet`;

const RESULT_PATH: Partial<Record<Rule, string>> = { fastener: 'atelier:diameter', hydraulic: 'atelier:pressureRating' };

function resultBlock(features: Feature[], itf: Interface, v: Violation): string {
  const focus = features.find((f) => f.id === v.features[0])!;
  const mate = v.features[1] ? features.find((f) => f.id === v.features[1]) : undefined;
  const axis = typeof v.detail.axis === 'string' ? v.detail.axis : null;
  const lines = [
    '    a sh:ValidationResult ;',
    '    sh:resultSeverity sh:Violation ;',
    `    sh:sourceShape ateliersh:${v.shape.replace(/^.*#/, '')} ;`,
    '    sh:sourceConstraintComponent sh:SPARQLConstraintComponent ;',
    `    sh:focusNode <${featureIri(focus)}> ;`,
  ];
  if (axis) lines.push(`    sh:resultPath atelier:position${axis.toUpperCase()} ;`);
  else if (RESULT_PATH[v.rule]) lines.push(`    sh:resultPath ${RESULT_PATH[v.rule]} ;`);
  if (v.rule === 'unit') lines.push(`    sh:value <${featureIri(focus)}/position/${axis}> ;`);
  else if (v.rule === 'orphan') lines.push(`    sh:value <${ifaceIri(itf.id)}> ;`);
  else if (mate) lines.push(`    sh:value <${featureIri(mate)}> ;`);
  lines.push(`    sh:resultMessage "${v.message}"`);
  return `  [\n${lines.join('\n')}\n  ]`;
}

function report(itf: Interface, features: Feature[]): string {
  const head = `@prefix sh:    <http://www.w3.org/ns/shacl#> .
@prefix atelier:   <https://example.com/atelier/ontology#> .
@prefix ateliersh: <https://example.com/atelier/shapes#> .

[] a sh:ValidationReport ;`;
  if (itf.violations.length === 0) return `${head}\n  sh:conforms true .\n`;
  return `${head}\n  sh:conforms false ;\n  sh:result\n${itf.violations.map((v) => resultBlock(features, itf, v)).join(' ,\n')} .\n`;
}

/** The arm of a PLM none of whose parts is on the interface: listed, never asked. */
const idleArm = (plm: string): EvidenceArm => ({
  endpoint: `ontop-${plm}`, kind: 'virtual', sparql: `# ${FIXTURE_MARKER}\n# No request: none of the visible parts belongs to ${plm.toUpperCase()}.`,
  triples: '', sql: null, tables: [], tripleCount: 0, requests: 0, ms: 0,
});

/** GET /api/query/interfaces/{id}/evidence, for the interface as already redacted for the profile. */
export function evidenceFor(itf: Interface, envelope: Envelope): Evidence {
  const pol = envelope.policy;
  const parts = itf.parts.filter(isPart);
  const features = itf.features.filter(isFeature);
  const measured = (endpoint: string) => envelope.provenance.calls.find((c) => c.endpoint === endpoint);

  const tags = join(parts.filter((p) => tagOf(p)).map(tagBlock));
  const coreCall = measured('ontop-core');
  const arms: EvidenceArm[] = [{
    endpoint: 'ontop-core', kind: 'virtual', sparql: coreArmSparql(parts, pol), triples: tags.turtle, sql: coreSql(parts, pol),
    tripleCount: tags.count, requests: coreCall?.requests ?? 1, ms: (coreCall?.ms ?? 18) + 2,
    tables: [{ plm: 'core', table: 'part_tag', keys: parts.map((p) => p.id) }],
  }];
  for (const plm of PLMS) {
    const own = features.filter((f) => f.plm === plm);
    const ownParts = parts.filter((p) => p.plm === plm);
    if (ownParts.length === 0) {
      arms.push(idleArm(plm));
      continue;
    }
    const { turtle, count } = ontopTurtle(own, ownParts);
    const m = measured(`ontop-${plm}`);
    arms.push({
      endpoint: `ontop-${plm}`, kind: 'virtual', sparql: ontopArmSparql(plm, own, ownParts), triples: turtle, sql: ontopSql(plm, own, ownParts),
      tripleCount: count, requests: m?.requests ?? 2, ms: (m?.ms ?? 40) + 3,
      tables: [
        ...KINDS.filter((k) => own.some((f) => f.kind === k)).map((k) => ({ plm, table: SCHEMA[plm][k][0], keys: own.filter((f) => f.kind === k).map((f) => f.id) })),
        { plm, table: SCHEMA[plm].part[0], keys: ownParts.map((p) => p.id) },
      ],
    });
  }
  const links = linksTurtle(itf, features);
  const neptuneCall = measured('neptune');
  arms.push({
    endpoint: 'neptune', kind: 'materialized', sparql: neptuneArmSparql(itf), triples: links.turtle, sql: null, tables: [],
    tripleCount: links.count, requests: neptuneCall?.requests ?? 1, ms: (neptuneCall?.ms ?? 23) + 2,
  });
  const rules = [...new Set(itf.violations.map((v) => v.rule))];
  const shapes: EvidenceShape[] = rules.map((rule) => ({ rule, shape: itf.violations.find((v) => v.rule === rule)!.shape, turtle: shapeText(rule) }));
  return {
    interfaceId: itf.id, sparql: envelope.sparql, arms,
    merged: { triples: arms.reduce((n, a) => n + a.tripleCount, 0), turtle: arms.filter((a) => a.requests > 0).map((a) => a.triples).join('\n\n') },
    shacl: { shapes, report: report(itf, features) },
    timings: envelope.timings,
    policy: pol,
  };
}
