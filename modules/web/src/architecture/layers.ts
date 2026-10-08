// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The layers of a generic PLM reference architecture, top to bottom,
// each with the reference component in plain words and what this sample built for it. The two
// agent rows close the table: a second client of the same stack, entering at the top.

/**
 * How a demo component's liveness is observed: a service /health answer, a call in the list answer,
 * a store read, the agent's /health on the site's origin, or the MCP server's tool list.
 */
export type Probe =
  | { kind: 'spa' }
  | { kind: 'health'; service: string }
  | { kind: 'call'; endpoint: string }
  | { kind: 'shacl' }
  | { kind: 'tags' }
  | { kind: 'cad' }
  | { kind: 'agent' }
  | { kind: 'mcp' }
  /** The event wiring's health answer from the query service. */
  | { kind: 'events' }
  /** The change log, read through the change feed. */
  | { kind: 'changes' };

export interface DemoItem {
  text: string;
  /** Identifier shown in monospace after the text. */
  code?: string;
  probe?: Probe;
  /** A quiet remark under the row about how this sample builds it. */
  note?: string;
  /** One chip per PLM on the same row, each with its own identifier and liveness. */
  perPlm?: { plms: string[]; code: (plm: string) => string; probe: (plm: string) => Probe };
}

export interface Layer {
  id: string;
  label: string;
  reference: string;
  /** The layer this demo adds on top of the reference architecture. */
  addition?: boolean;
  /** Text of a labelled break drawn above this layer in place of the connector from the layer above. */
  breakBefore?: string;
  demo: DemoItem[];
  /** What the reference architecture has here that the demo leaves out. */
  notBuilt?: string;
}

export const layers = (plms: string[]): Layer[] => [
  {
    id: 'client', label: 'Client',
    reference: 'PLM client: a web front end.',
    demo: [{ text: 'React SPA on CloudFront, Cognito sign-in.', probe: { kind: 'spa' } }],
  },
  {
    id: 'endpoint', label: 'API gateway',
    reference: 'One endpoint behind an API gateway.',
    demo: [{
      text: 'API Gateway HTTP API with an origin-secret authorizer: one endpoint for every data call; the agent stream is a second CloudFront behaviour that itself calls the endpoint.',
      probe: { kind: 'health', service: 'query' }, code: '/api/*',
    }],
  },
  {
    id: 'services', label: 'Domain services',
    reference: 'Containerised services, one per domain.',
    demo: [
      { text: 'Four PLM services on ECS Fargate: Spring Boot, Hibernate ORM, MapStruct.', perPlm: { plms, code: (plm) => `/api/${plm}/*`, probe: (plm) => ({ kind: 'health', service: plm }) } },
      { text: 'Atelier core service: Spring Boot, Hibernate ORM, MapStruct.', probe: { kind: 'health', service: 'core' }, code: '/api/core/*' },
      { text: 'Query service: Jena federation and SHACL.', probe: { kind: 'health', service: 'query' }, code: '/api/query/*' },
    ],
    notBuilt: 'the domain applications themselves',
  },
  {
    id: 'semantic', label: 'Semantic layer', addition: true,
    reference: 'No counterpart in the reference architecture: the addition, the ontology within Atelier.',
    demo: [
      { text: 'Ontology and SHACL shapes, files under version control, run on the merged graph.', probe: { kind: 'shacl' }, code: 'ontology/*.ttl' },
      { text: 'One Ontop virtual graph per PLM, its R2RML generated from the ORM annotations.', perPlm: { plms, code: (plm) => `ontop-${plm}`, probe: (plm) => ({ kind: 'call', endpoint: `ontop-${plm}` }) } },
      { text: 'Ontop virtual graph over atelier_core, minting the same part IRIs as the PLM mappings.', probe: { kind: 'call', endpoint: 'ontop-core' }, code: 'ontop-core' },
      { text: 'Federated by the query service: plain requests per source, merged, then validated by SHACL.', probe: { kind: 'health', service: 'query' } },
    ],
  },
  {
    id: 'internal', label: 'Core store and graph',
    reference: 'A store the layer owns, for its own tags and links, and a graph that indexes where each record and file lives.',
    demo: [
      { text: 'atelier_core on Aurora: the part tags (jurisdiction, releasability, tagged by which PLM and when), written by the Atelier core service.', probe: { kind: 'tags' }, code: 'atelier_core.part_tag' },
      {
        text: 'Neptune: interface links and confirmed equivalences, the file index (one atelier:cadFile per part) and the labels graph (every item\'s native and English names, the products\' glossary). Atelier core service writes the links graph; the links loader writes the file index on the PLM\'s event.',
        probe: { kind: 'call', endpoint: 'neptune' }, code: 'neptune',
        note: 'Neptune, SPARQL, because the federation speaks SPARQL; Oxigraph locally',
      },
      { text: 'atelier_core.demo_change: the log of value corrections released to the PLMs; Atelier-owned, outside the catalogue and the mapping, never in a graph.', probe: { kind: 'changes' }, code: 'atelier_core.demo_change' },
    ],
  },
  {
    id: 'pdm', label: 'Sources',
    reference: 'The sites\' PLM databases, native schemas untouched, attached through ORMs and mapped to the common data model.',
    demo: [{
      text: 'Four databases on one Aurora cluster, native schemas untouched; Hibernate entities and MapStruct.',
      perPlm: { plms, code: (plm) => `${plm}_plm`, probe: (plm) => ({ kind: 'call', endpoint: `ontop-${plm}` }) },
    }],
  },
  {
    id: 'files', label: 'Files',
    reference: 'CAD files in object storage; the graph holds the key.',
    demo: [{ text: 'STEP AP242 files in a private S3 bucket; a presigned URL per visible part; the graph holds the key.', probe: { kind: 'cad' }, code: 'atelier:cadFile' }],
  },
  {
    id: 'events', label: 'Events',
    reference: 'Each system announces its facts as business events; no change capture at storage level.',
    demo: [{
      text: 'EventBridge default bus. A PLM service publishes part.value.corrected and part.cad.published (its facts); the Atelier core service writes the links graph and publishes interface.link.added and equivalence.confirmed (Atelier\'s facts); the links loader subscribes: it writes the file-index entry and logs the correction through the core service. The query service only reads; no store is watched, and the virtual graph reads a corrected value in place.',
      probe: { kind: 'events' }, code: 'atelier.plm · atelier.graph',
    }],
  },
  {
    id: 'platform', label: 'Platform',
    reference: 'Containers; AWS here.',
    demo: [{ text: 'AWS only, eu-west-1: ECS Fargate tasks. The artefacts (ontology, mappings, shapes, policy) are the portable part.' }],
  },
  {
    id: 'agents', label: 'Agents',
    breakBefore: 'Agents on the layer: a second client, entering at the top',
    reference: 'The client with an agent asking instead of a person: same edge, same endpoint, same export control.',
    demo: [{
      text: 'Ask panel and agent runtime: Strands on Fargate, behind CloudFront. The model is read from MODEL_ID, a pluggable dependency.',
      probe: { kind: 'agent' }, code: '/agent/*',
    }],
  },
  {
    id: 'mcp', label: 'Tool interface (MCP)',
    reference: 'The single endpoint as an agent sees it: one tool per question, over the same gateway and the same profile header.',
    demo: [{
      text: 'MCP server in the query service; any MCP client.',
      probe: { kind: 'mcp' }, code: '/api/query/mcp',
    }],
  },
];

export const SUMMARY =
  'What this sample adds to the reference architecture: the semantic layer, its rules, and their tool face for agents. What it leaves out: the domain applications.';
