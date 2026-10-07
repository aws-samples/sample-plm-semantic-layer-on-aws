// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The twenty-five tools of docs/contract.md ("Agents on the semantic layer"), as GET /api/query/mcp/tools
// lists them in the dev server with VITE_FIXTURES=1. Never part of a production build.
import type { McpTools } from '../api/types';
import { FIXTURE_MARKER } from './marker';

export const mcpTools: McpTools = {
  tools: [
    { name: 'products', description: 'The products the parts are assembled into: key, name, frame, the number of parts the profile may see, the findings of the product rules (massLimit, massScale) and the number of lifecycle conflicts on its visible items.' },
    { name: 'list_interfaces', description: 'Every interface visible to the profile: id, label, parts, status, rules failing. Optional argument: product, the key of one product.' },
    { name: 'parts', description: 'The parts of one product, or with root of one assembly across the sites, as the profile sees them, with their attributes and findings.' },
    { name: 'interface_check', description: 'One interface with its features, rule results and provenance.' },
    { name: 'where_used', description: 'The interfaces a part sits on, its mates, feature counts per kind.' },
    { name: 'impact_of_change', description: 'Interfaces and mated features a change to a part or feature touches.' },
    { name: 'export_status', description: 'Jurisdiction, releasability, who tagged it, visibility to the profile.' },
    { name: 'bom', description: 'The bill of materials of one product, which no PLM holds: the product root, each site\'s kit, each site\'s tree; quantities, occurrences, mass; roll-up per site and in total.' },
    { name: 'bom_where_used', description: 'Where a part is used in a product\'s bill of materials: its parents with quantity and occurrences, and its total occurrences.' },
    { name: 'path_between', description: 'The shortest paths of parts joined by interfaces between two parts of one product, each joint with its status for the profile.' },
    { name: 'flow_path', description: 'The walk along a product\'s functional edges from one part, downstream or upstream: each step\'s flow and joint status, and through meshing gears the gear ratio and the rated-speed check.' },
    { name: 'variant_diff', description: 'One option of a product\'s variant group against the default option, port by port on the parts both configurations hold: added, removed, changed, same or not-modelled ports with their attributes, and the interface rules run on each configuration.' },
    { name: 'external_references', description: 'The external references of one product\'s visible parts: the remote URN, the part it resolves to, expected and current revision, and the status (ok, danglingReference, staleRevision, not-evaluable).' },
    { name: 'equivalent_parts', description: 'Purchased parts of one product that are the same item under different part numbers at different sites. The classes it knows are those of the ontology\'s ItemClasses scheme: fastener (standard resolved in the FastenerStandard scheme, so DIN 912, NF EN ISO 4762 and BS EN ISO 4762 are one socket head cap screw, nominal diameter and length within 0.1 mm), o-ring (inner diameter and cross-section within 0.01 mm, stated or given by an AS568 dash size or ISO 3601-1 code, and the compound resolved in the Materials scheme in any language), placard (legend ignoring case and spaces, width and height within 0.5 mm, face material with its thickness and adhesive resolved in the Materials scheme), container (IATA type resolved in the ULDTypes scheme, base width, depth, height and contour width within 1 mm, shell material in the Materials scheme), tyre (outer diameter and section width within 1 mm, rim diameter within 0.5 mm, ply rating), wheel (rim diameter within 0.5 mm, width within 1 mm) and brake (heat stack diameter within 1 mm, rotors). Each group has itemClass, classLabel, attributes (the identifying values: mm, concept, text), shelfLifeMonths when a member states one, stocking (partNumbers and sites stocking it today, stockLines, and 1 once confirmed), confirmed (true once a user confirmed the members as one item on the Purchasing screen; a proposal otherwise, which only the user confirms) and its members, each with plm, id, iri, name (native), standard as the site writes it, values as stored with unit, mm and resolved concept, and shelfLifeMonths. Templates: \'Which parts of the rover are the same screw?\', \'Which O-rings of the wind turbine are one item, and how many stock lines would that save?\'. Argument: product, required.' },
    { name: 'find_term', description: 'A term in English, German, French or Spanish resolved against the products\' glossary: the concepts it names and the parts of every site whose names hold it.' },
    { name: 'find_parts', description: 'The parts of one product that a query names by what they are, by native or English name, glossary term or part type, grouped by the assembly they belong to.' },
    { name: 'suppliers', description: 'The suppliers of one product\'s parts per site with lead times, the single-source parts and the parts whose preferred offers disagree on the lead time.' },
    { name: 'parts_between_stations', description: 'What lies between two stations of one product: the visible parts whose span along the station axis overlaps the range, inside it or crossing an end, with the stations they cover and the occurrences of a placed part in the range; every station with its basis.' },
    { name: 'section_joints', description: 'The sections of one product with their owner sites, parts and foreign parts, and each joint between two sections with the parts crossing it.' },
    { name: 'evidence', description: 'The request, generated SQL, triples and tables behind one arm of an answer.' },
    { name: 'ontology', description: 'The ontology the sparql tool is checked against: classes, properties with domain and range, the named graphs, the rules, and example patterns. Read it before writing SPARQL.' },
    { name: 'sparql', description: `One read query over the profile's merged graph, predicates checked against the ontology. ${FIXTURE_MARKER}` },
    { name: 'preview_correction', description: 'What the rules would say if a correction were released: the cells written into a copy of the merged graph, the results fixed, still failing and newly failing. Writes nothing.' },
    { name: 'catalogue', description: 'The ORM-generated catalogue of one PLM: tables, columns, units, terms.' },
    { name: 'sql', description: 'One SELECT over a PLM\'s native tables, validated against the catalogue, rows filtered by the profile.' },
  ],
};
