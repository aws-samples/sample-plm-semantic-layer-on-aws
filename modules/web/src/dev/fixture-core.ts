// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Fixtures of the Atelier core service (/api/core/*): the part_tag catalogue, the R2RML its
// annotations generate, and the tags themselves (docs/contract.md, "Where the additional metadata lives").
import type { Catalogue, Part, PartTag, Policy } from '../api/types';
import { visible } from './fixture-policy';
import { FIXTURE_MARKER } from './marker';

export const CORE_PLM = 'Atelier';
export const PART_TAG_COLUMNS = ['plm', 'native_key', 'jurisdiction', 'releasable_to', 'tagged_by', 'tagged_at'];

export const coreCatalogue: Catalogue = {
  plm: CORE_PLM,
  entities: [
    {
      entity: 'PartTag', table: 'part_tag', ontologyClass: 'atelier:Part',
      description: 'Part tag: the export-control tag Atelier holds for a published part; one row per part, written by the owning PLM when it publishes the part',
      columns: [
        ['plm', 'plm', 'String', 'PLM code that owns the part (fr, de, uk, es); with native_key it forms the part IRI', null],
        ['nativeKey', 'native_key', 'String', "The part's key in its PLM; with plm it forms the part IRI", null],
        ['jurisdiction', 'jurisdiction', 'String', 'Export-control jurisdiction of the part', 'atelier:jurisdiction'],
        ['releasableTo', 'releasable_to', 'String', 'Who the part may be released to, one token', 'atelier:releasableTo'],
        ['taggedBy', 'tagged_by', 'String', 'PLM that wrote the tag when it published the part', 'atelier:taggedBy'],
        ['taggedAt', 'tagged_at', 'Instant', 'When the owning PLM wrote the tag', 'atelier:taggedAt'],
      ].map(([field, column, javaType, description, term]) => ({
        field: field!, column: column!, javaType: javaType!, description, unit: null, unitColumn: null, ontologyTerm: term,
        undescribed: false, unitMissing: false,
      })),
    },
  ],
};

/** The mapping atelier-core generates from its PartTag annotations: the same part IRI as the PLM mappings mint. */
export const coreMapping = [
  `# ${FIXTURE_MARKER}: the mapping atelier-core generates from the JPA entity annotations of PartTag.`,
  '# Do not edit: change the annotations and regenerate with modules/ontop/scripts/generate-mappings.sh.',
  '',
  '@prefix rr: <http://www.w3.org/ns/r2rml#> .',
  '@prefix atelier: <https://example.com/atelier/ontology#> .',
  '@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .',
  '@prefix map: <https://example.com/atelier/core/mapping#> .',
  '',
  'map:PartTag a rr:TriplesMap ;',
  '  rr:logicalTable [ rr:tableName "part_tag" ] ;',
  '  rr:subjectMap [ rr:template "https://example.com/atelier/{plm}/part/{native_key}" ; rr:class atelier:Part ] ;',
  '  rr:predicateObjectMap [ rr:predicate atelier:jurisdiction ; rr:objectMap [ rr:column "jurisdiction" ] ] ;',
  '  rr:predicateObjectMap [ rr:predicate atelier:releasableTo ; rr:objectMap [ rr:column "releasable_to" ] ] ;',
  '  rr:predicateObjectMap [ rr:predicate atelier:taggedBy ; rr:objectMap [ rr:column "tagged_by" ] ] ;',
  '  rr:predicateObjectMap [ rr:predicate atelier:taggedAt ; rr:objectMap [ rr:column "tagged_at" ; rr:datatype xsd:dateTime ] ] .',
  '',
].join('\n');

/** When each PLM published its parts in the seed: one instant per PLM, so a part's tag date follows its owner. */
const TAGGED_AT: Record<string, string> = {
  fr: '2026-09-01T08:12:00Z',
  de: '2026-09-02T09:40:00Z',
  uk: '2026-09-03T10:05:00Z',
  es: '2026-09-04T07:55:00Z',
};
export const taggedAt = (plm: string) => TAGGED_AT[plm.toLowerCase()];

/** The row of part_tag for a tagged part; an untagged part has none. */
export const tagOf = (p: Part): PartTag | null =>
  p.jurisdiction && p.releasableTo && p.taggedBy
    ? { plm: p.plm, nativeKey: p.id, jurisdiction: p.jurisdiction, releasableTo: p.releasableTo, taggedBy: p.taggedBy, taggedAt: taggedAt(p.plm) }
    : null;

/** GET /api/core/tags: the tags of the parts the profile may see. */
export const tagsFor = (parts: Part[], pol: Policy): PartTag[] =>
  parts.filter((p) => visible(pol, p)).flatMap((p) => (tagOf(p) ? [tagOf(p)!] : []));
