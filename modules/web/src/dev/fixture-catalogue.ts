// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Catalogue fixtures mirroring the annotated JPA entities of modules/plm-services and atelier-core.
import type { Catalogue, CatalogueColumn, CatalogueEntity } from '../api/types';
import { coreCatalogue } from './fixture-core';

/** The words or units a column takes, or the form its text matches. */
type Vocabulary = { accepts: string[] } | { pattern: string };
type Col = [field: string, column: string, javaType: string, description: string | null, term: string | null, unit?: string, unitColumn?: string, vocabulary?: Vocabulary];

const col = ([field, column, javaType, description, term, unit, unitColumn, vocabulary]: Col): CatalogueColumn => ({
  field, column, javaType, description, unit: unit ?? null, unitColumn: unitColumn ?? null, ontologyTerm: term,
  undescribed: description === null,
  unitMissing: !unit && !unitColumn && term !== null && term.startsWith('atelier:position') && javaType !== 'String',
  ...vocabulary,
});

/** A unit column: the units the British PLM stores, as QUDT local names. */
const units = (...accepts: string[]): Vocabulary => ({ accepts });
/** A lifecycle column: the site's words, in the order WORKING, RELEASED, BLOCKED, SUPERSEDED. */
const words = (...accepts: string[]): Vocabulary => ({ accepts });
const form = (pattern: string): Vocabulary => ({ pattern });
const URN_FORM = form('urn:plm:(de|fr|es|uk):part:.+');
/** A revision as the remote site writes it: a letter (FR), two digits (DE), P1 or C1 (UK), an integer (ES). */
const ANY_REVISION = form('[A-Z]|[0-9]{1,2}|[PC][0-9]+');

/** A site's external references: the part that uses another site's part, named by URN at an expected revision. */
const references = (e: string, table: string, desc: string, cols: [part: string, urn: string, qty: string, expected: string, note: string]) =>
  entity(e, table, 'atelier:ExternalReference', desc, [
    ['id', 'id', 'String', "Id: the reference's surrogate key", null],
    [cols[0], cols[0], 'String', 'The part that uses the remote part', 'atelier:fromPart'],
    [cols[1], cols[1], 'String', "URN: the remote part's key, urn:plm:<site>:part:<part number>, as entered", 'atelier:remoteUrn', undefined, undefined, URN_FORM],
    [cols[2], cols[2], 'BigDecimal', 'How many of the remote part the part uses', 'atelier:quantity'],
    [cols[3], cols[3], 'String', "The remote part's revision the part was designed against, in the remote site's form", 'atelier:expectedRevision', undefined, undefined, ANY_REVISION],
    [cols[4], cols[4], 'String', 'Free-text note on the use', 'rdfs:comment'],
  ]);

/** A site's supplier offers: the part offered, the supplier, its own reference, the lead time and whether the site orders from it first. */
const offers = (e: string, table: string, desc: string, cols: [part: string, supplier: string, ref: string, days: string, preferred: string]) =>
  entity(e, table, 'atelier:SupplierOffer', desc, [
    ['id', 'id', 'Integer', "The offer's key", null],
    [cols[0], cols[0], 'String', 'The part offered', 'atelier:offersPart'],
    [cols[1], cols[1], 'String', 'The supplier making the offer', 'atelier:fromSupplier'],
    [cols[2], cols[2], 'String', "The supplier's own reference for the item", 'atelier:supplierPartNumber'],
    [cols[3], cols[3], 'Integer', 'The lead time in days', 'atelier:leadTimeDays'],
    [cols[4], cols[4], 'Boolean', 'Whether the site orders from this offer first', 'atelier:preferred'],
  ]);

const entity = (e: string, table: string, cls: string, description: string, cols: Col[]): CatalogueEntity => ({
  entity: e, table, ontologyClass: cls, description, columns: cols.map(col),
});

// A PLM part table is as the PLM defines it: key, name, its own file reference and the part attributes in
// its own idiom. Export control lives in atelier_core.part_tag, and the CAD location in the Neptune file
// index, never in these columns.
const part = (e: string, table: string, desc: string, [id, idDesc]: [string, string], [name, nameDesc]: [string, string], [cad, cadDesc]: [string, string],
  fields: [string, string, string], attributes: Col[]) =>
  entity(e, table, 'atelier:Part', desc, [
    [fields[0], id, 'String', idDesc, 'atelier:identifier'],
    [fields[1], name, 'String', nameDesc, 'atelier:label'],
    [fields[2], cad, 'String', cadDesc, 'atelier:sourceFileRef'],
    ...attributes,
  ]);

const pos = (f: string, c: string, axis: string, what: string, unit?: string, unitColumn?: string): Col =>
  [f, c, 'BigDecimal', `${what} position along the product ${axis.toUpperCase()} axis`, `atelier:position${axis.toUpperCase()}`, unit, unitColumn];
const xyz = (fields: [string, string, string], cols: [string, string, string], what: string, unit?: string, unitColumn?: string): Col[] =>
  (['x', 'y', 'z'] as const).map((axis, i) => pos(fields[i], cols[i], axis, what, unit, unitColumn));
const MM: [string, string, string] = ['pos_x_mm', 'pos_y_mm', 'pos_z_mm'];
const MM_F: [string, string, string] = ['posXMm', 'posYMm', 'posZMm'];
const onPart = (f: string, c: string, type: string, what: string): Col => [f, c, type, `Part carrying the ${what}`, 'atelier:onPart'];

export const catalogues: Record<string, Catalogue> = {
  fr: { plm: 'FR', entities: [
    entity('Connecteur', 'connecteur', 'atelier:Plug', 'Connecteur: an electrical connector carried by a part', [
      ['idConnecteur', 'id_connecteur', 'String', "Identifiant connecteur: the connector's key in the French PLM", 'atelier:identifier'],
      onPart('piece', 'ref_piece', 'Piece', 'connector'),
      ...xyz(MM_F, MM, 'Connector', 'MilliM'),
      ['typeConnecteur', 'type_connecteur', 'String', 'Type connecteur: connector standard / part number', 'atelier:connectorType'],
      ['nbBroches', 'nb_broches', 'Integer', 'Nombre de broches: pin count', 'atelier:pinCount'],
    ]),
    entity('Fixation', 'fixation', 'atelier:Fastener', 'Fixation: a fastener group joining the part to its neighbour', [
      ['refFixation', 'ref_fixation', 'String', "Référence fixation: the fastener group's key in the French PLM", 'atelier:identifier'],
      onPart('piece', 'ref_piece', 'Piece', 'fastener group'),
      ...xyz(MM_F, MM, 'Fastener', 'MilliM'),
      ['norme', 'norme', 'String', 'Norme: fastener standard', 'atelier:fastenerStandard'],
      ['diametreMm', 'diametre_mm', 'BigDecimal', 'Diamètre: nominal shank diameter', 'atelier:diameter', 'MilliM'],
      ['nombre', 'nombre', 'Integer', 'Nombre: fasteners in the group', 'atelier:fastenerCount'],
      ['longueurSerrageMm', 'longueur_serrage_mm', 'BigDecimal', 'Longueur de serrage: grip length', 'atelier:gripLength', 'MilliM'],
    ]),
    entity('RaccordHydraulique', 'raccord_hydraulique', 'atelier:HydraulicCoupling', 'Raccord hydraulique: a hydraulic line coupling at the joint', [
      ['refRaccord', 'ref_raccord', 'String', "Référence raccord: the coupling's key in the French PLM", 'atelier:identifier'],
      onPart('piece', 'ref_piece', 'Piece', 'coupling'),
      ...xyz(MM_F, MM, 'Coupling', 'MilliM'),
      ['norme', 'norme', 'String', 'Norme: coupling standard', 'atelier:couplingStandard'],
      ['tailleDash', 'taille_dash', 'Integer', 'Taille dash: tube dash size', 'atelier:dashSize'],
      ['pressionBar', 'pression_bar', 'BigDecimal', 'Pression nominale: rated working pressure', 'atelier:pressureRating', 'BAR'],
      ['fluide', 'fluide', 'String', 'Fluide: hydraulic fluid', 'atelier:fluid'],
    ]),
    part('Piece', 'piece', 'Pièce : a part (assembly or section) managed in the French PLM',
      ['ref_piece', "Référence pièce: the part's reference in the French PLM"], ['designation', 'Désignation: human-readable part name'],
      ['fichier_cao', "Fichier CAO: the French PLM's own reference to the part's CAD file, informational"],
      ['refPiece', 'designation', 'fichierCao'], [
        ['indice', 'indice', 'String', "Indice: the part's revision index in the French PLM's letter form (A, B)", 'atelier:revision'],
        ['etat', 'etat', 'String', "État: the part's lifecycle state in French (En cours, Publié, Bloqué, Remplacé)", 'atelier:lifecycleLabel', undefined, undefined, words('En cours', 'Publié', 'Bloqué', 'Remplacé')],
        ['masseKg', 'masse_kg', 'BigDecimal', "Masse: the part's mass in kilograms", 'atelier:mass', 'KiloGM'],
        ['matiere', 'matiere', 'String', "Matière: the part's material, named in French", 'atelier:material'],
        ['typePiece', 'type_piece', 'String', 'Type de pièce: what the item is, PART, SOFTWARE or DOCUMENT', 'atelier:partType'],
      ]),
    references('ReferenceExterne', 'reference_externe', 'Référence externe: a part of another site the pièce uses', ['piece', 'urn', 'quantite', 'indice_attendu', 'note']),
    offers('ArticleFournisseur', 'article_fournisseur', 'Article fournisseur: an offer of a supplier for a pièce', ['ref_piece', 'code_fournisseur', 'reference_fournisseur', 'delai_jours', 'prefere']),
  ] },
  de: { plm: 'DE', entities: [
    part('Bauteil', 'bauteil', 'Bauteil: a part (assembly or section) managed in the German PLM',
      ['teil_nr', "Teilenummer: the part's number in the German PLM"], ['benennung', 'Benennung: human-readable part name'],
      ['cad_datei', "CAD-Datei: the German PLM's own reference to the part's CAD file, informational"],
      ['teilNr', 'benennung', 'cadDatei'], [
        ['revision', 'revision', 'String', "Revision: the part's revision in the German PLM's two-digit form (01, 02)", 'atelier:revision', undefined, undefined, form('[0-9]{2}')],
        ['status', 'status', 'String', "Status: the part's lifecycle state in German (In Arbeit, Freigegeben, Gesperrt, Ersetzt)", 'atelier:lifecycleLabel', undefined, undefined, words('In Arbeit', 'Freigegeben', 'Gesperrt', 'Ersetzt')],
        ['masseKg', 'masse_kg', 'BigDecimal', "Masse: the part's mass in kilograms", 'atelier:mass', 'KiloGM'],
        ['werkstoff', 'werkstoff', 'String', "Werkstoff: the part's material, named in German", 'atelier:material'],
        ['teileart', 'teileart', 'String', 'Teileart: what the item is, PART, SOFTWARE or DOCUMENT', 'atelier:partType'],
      ]),
    entity('Stecker', 'stecker', 'atelier:Plug', 'Stecker: an electrical connector carried by a part', [
      ['steckerId', 'stecker_id', 'String', "Stecker-ID: the connector's key in the German PLM", 'atelier:identifier'],
      onPart('bauteil', 'teil_nr', 'Bauteil', 'connector'),
      ...xyz(MM_F, MM, 'Connector', 'MilliM'),
      ['typ', 'typ', 'String', 'Typ: connector standard / part number', 'atelier:connectorType'],
      ['polzahl', 'polzahl', 'Integer', 'Polzahl: pin count', 'atelier:pinCount'],
    ]),
    entity('Befestiger', 'befestiger', 'atelier:Fastener', 'Befestiger: a fastener group joining the part to its neighbour', [
      ['befestigerId', 'befestiger_id', 'String', "Befestiger-ID: the fastener group's key in the German PLM", 'atelier:identifier'],
      onPart('bauteil', 'teil_nr', 'Bauteil', 'fastener group'),
      ...xyz(MM_F, MM, 'Fastener', 'MilliM'),
      ['norm', 'norm', 'String', 'Norm: fastener standard', 'atelier:fastenerStandard'],
      ['durchmesserMm', 'durchmesser_mm', 'BigDecimal', 'Durchmesser: nominal shank diameter', 'atelier:diameter', 'MilliM'],
      ['anzahl', 'anzahl', 'Integer', 'Anzahl: fasteners in the group', 'atelier:fastenerCount'],
      ['klemmlaengeMm', 'klemmlaenge_mm', 'BigDecimal', 'Klemmlänge: grip length', 'atelier:gripLength', 'MilliM'],
    ]),
    entity('Hydraulikkupplung', 'hydraulikkupplung', 'atelier:HydraulicCoupling', 'Hydraulikkupplung: a hydraulic line coupling at the joint', [
      ['kupplungId', 'kupplung_id', 'String', "Kupplungs-ID: the coupling's key in the German PLM", 'atelier:identifier'],
      onPart('bauteil', 'teil_nr', 'Bauteil', 'coupling'),
      ...xyz(MM_F, MM, 'Coupling', 'MilliM'),
      ['norm', 'norm', 'String', 'Norm: coupling standard', 'atelier:couplingStandard'],
      ['dashGroesse', 'dash_groesse', 'Integer', 'Dash-Größe: tube dash size', 'atelier:dashSize'],
      ['nenndruckBar', 'nenndruck_bar', 'BigDecimal', 'Nenndruck: rated working pressure', 'atelier:pressureRating', 'BAR'],
      ['fluid', 'fluid', 'String', 'Fluid: hydraulic fluid', 'atelier:fluid'],
    ]),
    references('ExternerVerweis', 'externer_verweis', 'Externer Verweis: a part of another site the Bauteil uses', ['teil_nr', 'urn', 'menge', 'erwartete_revision', 'bemerkung']),
    offers('Lieferantenteil', 'lieferantenteil', 'Lieferantenteil: an offer of a supplier for a Bauteil', ['teil_nr', 'lieferant_id', 'lieferanten_teilenummer', 'lieferzeit_tage', 'bevorzugt']),
  ] },
  uk: { plm: 'UK', entities: [
    part('Component', 'component', 'Component: a part (assembly or section) managed in the British PLM',
      ['comp_id', "Component ID: the part's key in the British PLM"], ['name', 'Name: human-readable part name'],
      ['cad_file', "CAD file: the British PLM's own reference to the part's CAD file, informational"],
      ['compId', 'name', 'cadFile'], [
        ['revision', 'revision', 'String', "Revision: the component's revision in the British PLM's form (P1, P2 for prototypes, C1 once released)", 'atelier:revision', undefined, undefined, form('[PC][0-9]+')],
        ['lifecycle', 'lifecycle', 'String', "Lifecycle: the component's lifecycle state in English (Draft, Released, Frozen, Superseded)", 'atelier:lifecycleLabel', undefined, undefined, words('Draft', 'Released', 'Frozen', 'Superseded')],
        ['massLb', 'mass_lb', 'BigDecimal', "Mass: the component's mass in pounds", 'atelier:mass', 'LB'],
        ['material', 'material', 'String', "Material: the component's material", 'atelier:material'],
        ['partType', 'part_type', 'String', 'Part type: what the item is, PART, SOFTWARE or DOCUMENT', 'atelier:partType'],
      ]),
    entity('HarnessConnector', 'harness_connector', 'atelier:Plug', 'Harness connector: an electrical connector carried by a part', [
      ['connRef', 'conn_ref', 'String', "Connector reference: the connector's key in the British PLM", 'atelier:identifier'],
      onPart('component', 'comp_id', 'Component', 'connector'),
      ...xyz(['posX', 'posY', 'posZ'], ['pos_x', 'pos_y', 'pos_z'], 'Connector', undefined, 'pos_uom'),
      ['posUom', 'pos_uom', 'String', 'Unit of measure of the position columns, as a QUDT unit local name (IN = inch)', null, undefined, undefined, units('IN')],
      ['shellType', 'shell_type', 'String', 'Shell type: connector standard / part number', 'atelier:connectorType'],
      ['pinQty', 'pin_qty', 'Integer', 'Pin quantity: pin count', 'atelier:pinCount'],
    ]),
    entity('Fastener', 'fastener', 'atelier:Fastener', 'Fastener: a fastener group joining the part to its neighbour', [
      ['fastRef', 'fast_ref', 'String', "Fastener reference: the fastener group's key in the British PLM", 'atelier:identifier'],
      onPart('component', 'comp_id', 'Component', 'fastener group'),
      ...xyz(['posX', 'posY', 'posZ'], ['pos_x', 'pos_y', 'pos_z'], 'Fastener', undefined, 'pos_uom'),
      ['posUom', 'pos_uom', 'String', 'Unit of measure of the position columns, as a QUDT unit local name (IN = inch)', null, undefined, undefined, units('IN')],
      ['standard', 'standard', 'String', 'Standard: fastener standard', 'atelier:fastenerStandard'],
      ['dia', 'dia', 'BigDecimal', 'Diameter: nominal shank diameter', 'atelier:diameter', undefined, 'dia_uom'],
      ['diaUom', 'dia_uom', 'String', 'Unit of measure of dia, as a QUDT unit local name (IN = inch)', null, undefined, undefined, units('IN')],
      ['qty', 'qty', 'Integer', 'Quantity: fasteners in the group', 'atelier:fastenerCount'],
      ['grip', 'grip', 'BigDecimal', 'Grip: grip length', 'atelier:gripLength', undefined, 'grip_uom'],
      ['gripUom', 'grip_uom', 'String', 'Unit of measure of grip, as a QUDT unit local name (IN = inch)', null, undefined, undefined, units('IN')],
    ]),
    entity('HydCoupling', 'hyd_coupling', 'atelier:HydraulicCoupling', 'Hydraulic coupling: a hydraulic line coupling at the joint', [
      ['cplgRef', 'cplg_ref', 'String', "Coupling reference: the coupling's key in the British PLM", 'atelier:identifier'],
      onPart('component', 'comp_id', 'Component', 'coupling'),
      ...xyz(['posX', 'posY', 'posZ'], ['pos_x', 'pos_y', 'pos_z'], 'Coupling', undefined, 'pos_uom'),
      ['posUom', 'pos_uom', 'String', 'Unit of measure of the position columns, as a QUDT unit local name (IN = inch)', null, undefined, undefined, units('IN')],
      ['standard', 'standard', 'String', 'Standard: coupling standard', 'atelier:couplingStandard'],
      ['dash', 'dash', 'Integer', 'Dash: tube dash size', 'atelier:dashSize'],
      ['rating', 'rating', 'BigDecimal', 'Rating: rated working pressure', 'atelier:pressureRating', undefined, 'rating_uom'],
      ['ratingUom', 'rating_uom', 'String', 'Unit of measure of rating, as a QUDT unit local name (PSI)', null, undefined, undefined, units('PSI')],
      ['fluid', 'fluid', 'String', 'Fluid: hydraulic fluid', 'atelier:fluid'],
    ]),
    references('ExternalRef', 'external_ref', 'External reference: a part of another site the component uses', ['part_no', 'remote_urn', 'qty', 'expected_revision', 'note']),
    offers('SupplierPart', 'supplier_part', 'Supplier part: an offer of a supplier for a component', ['comp_id', 'supplier_id', 'supplier_part_no', 'lead_time_days', 'preferred']),
  ] },
  es: { plm: 'ES', entities: [
    entity('Conector', 'conector', 'atelier:Plug', 'Conector: an electrical connector carried by a part', [
      ['codConector', 'cod_conector', 'String', "Código de conector: the connector's code in the Spanish PLM", 'atelier:identifier'],
      onPart('pieza', 'cod_pieza', 'Pieza', 'connector'),
      ...xyz(MM_F, MM, 'Connector', 'MilliM'),
      ['tipo', 'tipo', 'String', 'Tipo: connector standard / part number', 'atelier:connectorType'],
      ['numContactos', 'num_contactos', 'Integer', 'Número de contactos: pin count', 'atelier:pinCount'],
      ['obsoleto', 'obsoleto', 'Boolean', null, null],
    ]),
    entity('Remache', 'remache', 'atelier:Fastener', 'Remache: a fastener group joining the part to its neighbour', [
      ['codRemache', 'cod_remache', 'String', "Código de remache: the fastener group's code in the Spanish PLM", 'atelier:identifier'],
      onPart('pieza', 'cod_pieza', 'Pieza', 'fastener group'),
      ...xyz(MM_F, MM, 'Fastener', 'MilliM'),
      ['norma', 'norma', 'String', 'Norma: fastener standard', 'atelier:fastenerStandard'],
      ['diametroMm', 'diametro_mm', 'BigDecimal', 'Diámetro: nominal shank diameter', 'atelier:diameter', 'MilliM'],
      ['cantidad', 'cantidad', 'Integer', 'Cantidad: fasteners in the group', 'atelier:fastenerCount'],
      ['longitudAprieteMm', 'longitud_apriete_mm', 'BigDecimal', 'Longitud de apriete: grip length', 'atelier:gripLength', 'MilliM'],
    ]),
    entity('Acoplamiento', 'acoplamiento', 'atelier:HydraulicCoupling', 'Acoplamiento: a hydraulic line coupling at the joint', [
      ['codAcoplamiento', 'cod_acoplamiento', 'String', "Código de acoplamiento: the coupling's code in the Spanish PLM", 'atelier:identifier'],
      onPart('pieza', 'cod_pieza', 'Pieza', 'coupling'),
      ...xyz(MM_F, MM, 'Coupling', 'MilliM'),
      ['norma', 'norma', 'String', 'Norma: coupling standard', 'atelier:couplingStandard'],
      ['tamanoDash', 'tamano_dash', 'Integer', 'Tamaño dash: tube dash size', 'atelier:dashSize'],
      ['presionBar', 'presion_bar', 'BigDecimal', 'Presión nominal: rated working pressure', 'atelier:pressureRating', 'BAR'],
      ['fluido', 'fluido', 'String', 'Fluido: hydraulic fluid', 'atelier:fluid'],
    ]),
    part('Pieza', 'pieza', 'Pieza: a part (assembly or section) managed in the Spanish PLM',
      ['cod_pieza', "Código de pieza: the part's code in the Spanish PLM"], ['denominacion', 'Denominación: human-readable part name'],
      ['fichero_cad', "Fichero CAD: the Spanish PLM's own reference to the part's CAD file, informational"],
      ['codPieza', 'denominacion', 'ficheroCad'], [
        ['revision', 'revision', 'Integer', "Revisión: the part's revision in the Spanish PLM, an integer (1, 2)", 'atelier:revision', undefined, undefined, form('[0-9]+')],
        ['estado', 'estado', 'String', "Estado: the part's lifecycle state in Spanish (Borrador, Liberado, Bloqueado, Sustituido)", 'atelier:lifecycleLabel', undefined, undefined, words('Borrador', 'Liberado', 'Bloqueado', 'Sustituido')],
        ['masaKg', 'masa_kg', 'BigDecimal', "Masa: the part's mass in kilograms", 'atelier:mass', 'KiloGM'],
        ['material', 'material', 'String', "Material: the part's material, named in Spanish", 'atelier:material'],
        ['tipo', 'tipo', 'String', 'Tipo: what the item is, PART, SOFTWARE or DOCUMENT', 'atelier:partType'],
      ]),
    references('ReferenciaExterna', 'referencia_externa', 'Referencia externa: a part of another site the pieza uses', ['pieza', 'urn', 'cantidad', 'revision_esperada', 'nota']),
    offers('PiezaProveedor', 'pieza_proveedor', 'Pieza proveedor: an offer of a supplier for a pieza', ['cod_pieza', 'cod_proveedor', 'referencia_proveedor', 'plazo_dias', 'preferido']),
  ] },
  core: coreCatalogue,
};
