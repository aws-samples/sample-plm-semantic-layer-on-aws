// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Writes the Neptune named graphs and feeds Atelier's change log.
//
// Three callers:
//   - the CloudFormation custom resource (deploy): a Graph Store PUT replaces
//     each named graph with the generated Turtle file bundled next to this
//     handler (GRAPHS maps file name to graph IRI);
//   - the EventBridge rule (runtime), `part.cad.published` from a PLM: sets
//     `atelier:cadFile` on the part in the file index with a SPARQL DELETE/INSERT,
//     one value per part;
//   - the EventBridge rule (runtime), `part.value.corrected` from a PLM: appends
//     the rows of the correction to `atelier_core.demo_change` in one
//     POST ${API_BASE}/core/changes, each row with the event's PLM, actor and
//     purpose, with the origin secret and the officer profile. A correction whose
//     purpose starts with `reset:` is the reset undoing an earlier one and is not
//     logged.
// Nothing else at runtime: the links graph is written synchronously by the core
// service.
//
// With NEPTUNE_IAM_AUTH=true every Neptune request (Graph Store PUT, SPARQL query
// and update) is signed with SigV4 for the service `neptune-db` under the function
// role; the calls to the API are never signed. Without the switch the requests are
// sent as they are (a store without IAM authentication).
import * as fs from 'fs';
import * as path from 'path';
import { Sha256 } from '@aws-crypto/sha256-js';
import { GetSecretValueCommand, SecretsManagerClient } from '@aws-sdk/client-secrets-manager';
import { defaultProvider } from '@aws-sdk/credential-provider-node';
import { SignatureV4 } from '@smithy/signature-v4';
import type { AwsCredentialIdentity, Provider } from '@smithy/types';
import type { CloudFormationCustomResourceEvent, EventBridgeEvent } from 'aws-lambda';

const GSP_URL = process.env.GSP_URL!;
const GRAPHS = JSON.parse(process.env.GRAPHS!) as Record<string, string>;
const SPARQL_URL = GSP_URL.replace(/gsp\/$/, '');
const FILE_INDEX_GRAPH = GRAPHS['fileindex.ttl'];

/** A graph file is a bare file name bundled next to this handler; anything else is a configuration error. */
const sanitizeGraphFile = (name: string): string => {
  if (!/^[\w.-]+$/.test(name)) throw new Error(`Graph file is not a bare file name: ${name}`);
  return name;
};
const CAD_FILE = 'https://example.com/atelier/ontology#cadFile';
/** The API Gateway base the browser uses (<endpoint>/api). */
const API_BASE = process.env.API_BASE!;
const ORIGIN_SECRET_ARN = process.env.ORIGIN_SECRET_ARN!;

export const CAD_PUBLISHED = 'part.cad.published';
/** Detail of the `atelier.plm` / `part.cad.published` event. */
export interface CadPublished {
  plm: string;
  part: string;
  /** The part IRI as the R2RML templates mint it: https://example.com/atelier/<plm>/part/<id>. */
  partIri: string;
  /** Bucket key of the STEP file, the value of atelier:cadFile. */
  cadFile: string;
  at: string;
}

export const VALUE_CORRECTED = 'part.value.corrected';
/** One corrected cell of a `part.value.corrected` event. */
export interface CorrectedRow {
  table: string;
  key: string;
  column: string;
  before: unknown;
  after: unknown;
}
/** Detail of the `atelier.plm` / `part.value.corrected` event: the cells one PLM request corrected. */
export interface ValueCorrected {
  plm: string;
  rows: CorrectedRow[];
  /** The profile that released the correction. */
  actor: string;
  purpose: string;
  at: string;
}

type PlmEvent = EventBridgeEvent<typeof CAD_PUBLISHED, CadPublished> | EventBridgeEvent<typeof VALUE_CORRECTED, ValueCorrected>;

// A part IRI under the Atelier namespace with none of the characters that would
// close or escape the `<...>` in the update; a CAD key of the released shape,
// `cad/<product>/<file>.stp`.
const PART_IRI = /^https:\/\/example\.com\/atelier\/[^\s<>"{}|\\^`]+$/;
const CAD_KEY = /^cad\/[a-z0-9-]+\/[a-z0-9-]+\.stp$/;

const graphUrl = (graph: string) => `${GSP_URL}?graph=${encodeURIComponent(graph)}`;

/** The signing service of Amazon Neptune's data plane. */
export const NEPTUNE_SIGNING_SERVICE = 'neptune-db';

/** One Neptune request before signing: method, URL, the headers to send and the body exactly as it is sent. */
export interface NeptuneRequest {
  method: string;
  url: string;
  headers: Record<string, string>;
  body?: string;
}

export interface SigningOptions {
  credentials: AwsCredentialIdentity | Provider<AwsCredentialIdentity>;
  region: string;
  /** The signing time; the current time when absent. */
  signingDate?: Date;
}

/**
 * The headers of `request` with its SigV4 signature for `neptune-db`: `authorization`, `x-amz-date`,
 * `x-amz-content-sha256` (the hash of the body) and, with session credentials, `x-amz-security-token`, over the method,
 * path, query string, host (with the port, as fetch sends it), the headers given and the hash of the body. The host
 * header is signed but not returned: fetch derives it from the URL.
 */
export async function signNeptuneRequest(request: NeptuneRequest, options: SigningOptions): Promise<Record<string, string>> {
  const url = new URL(request.url);
  const signer = new SignatureV4({ service: NEPTUNE_SIGNING_SERVICE, region: options.region, credentials: options.credentials, sha256: Sha256 });
  const signed = await signer.sign({
    method: request.method,
    protocol: url.protocol,
    hostname: url.hostname,
    port: url.port ? Number(url.port) : undefined,
    path: url.pathname,
    query: Object.fromEntries(url.searchParams),
    headers: { ...request.headers, host: url.host },
    body: request.body,
  }, { signingDate: options.signingDate });
  const headers = { ...signed.headers };
  delete headers.host;
  return headers;
}

/** The region of the cluster: the function's own, or the one in the endpoint's host name. */
function neptuneRegion(url: string): string {
  const region = process.env.AWS_REGION ?? /\.([a-z]{2}-[a-z]+-\d)\.neptune\.amazonaws\.com$/.exec(new URL(url).hostname)?.[1];
  if (!region) throw new Error(`NEPTUNE_IAM_AUTH is on but neither AWS_REGION nor the host of ${url} names a region`);
  return region;
}

const credentials = defaultProvider();
/** A request to Neptune, signed under the function role when NEPTUNE_IAM_AUTH is true. */
async function neptuneFetch(url: string, method: string, headers: Record<string, string>, body: string): Promise<Response> {
  const sent = process.env.NEPTUNE_IAM_AUTH === 'true'
    ? await signNeptuneRequest({ method, url, headers, body }, { credentials, region: neptuneRegion(url) })
    : headers;
  return fetch(url, { method, headers: sent, body });
}

async function sparql(kind: 'query' | 'update', text: string): Promise<Response> {
  const res = await neptuneFetch(SPARQL_URL, 'POST',
    { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/sparql-results+json' },
    `${kind}=${encodeURIComponent(text)}`);
  if (!res.ok) throw new Error(`Neptune SPARQL ${kind} ${res.status}: ${await res.text()}`);
  return res;
}

async function countTriples(graph: string): Promise<number> {
  const res = await sparql('query', 'SELECT (COUNT(*) AS ?n) WHERE { GRAPH <' + graph + '> { ?s ?p ?o } }');
  return Number((await res.json()).results.bindings[0].n.value);
}

async function replaceGraph(file: string, graph: string): Promise<number> {
  // nosemgrep: detect-non-literal-fs-filename -- file is a key of GRAPHS, a bare file name checked by sanitizeGraphFile; no request input
  const body = fs.readFileSync(path.join(__dirname, sanitizeGraphFile(file)), 'utf8');
  for (let attempt = 1; ; attempt++) {
    try {
      const res = await neptuneFetch(graphUrl(graph), 'PUT', { 'Content-Type': 'text/turtle' }, body);
      if (!res.ok) throw new Error(`Neptune GSP ${res.status}: ${await res.text()}`);
      break;
    } catch (err) {
      // A just-created instance can take a few minutes to accept connections.
      if (attempt >= 10) throw err;
      await new Promise((r) => setTimeout(r, 10000));
    }
  }
  return countTriples(graph);
}

async function setCadFile({ partIri, cadFile }: CadPublished): Promise<number> {
  if (!PART_IRI.test(partIri)) throw new Error(`${CAD_PUBLISHED}: partIri must be an https IRI under https://example.com/atelier/`);
  if (!CAD_KEY.test(cadFile)) throw new Error(`${CAD_PUBLISHED}: cadFile must match ${CAD_KEY}`);
  const pattern = `GRAPH <${FILE_INDEX_GRAPH}> { <${partIri}> <${CAD_FILE}> ?old }`;
  await sparql('update',
    `DELETE { ${pattern} } INSERT { GRAPH <${FILE_INDEX_GRAPH}> { <${partIri}> <${CAD_FILE}> "${cadFile}" } } WHERE { OPTIONAL { ${pattern} } }`);
  return countTriples(FILE_INDEX_GRAPH);
}

const secrets = new SecretsManagerClient({});
let originSecret: string | undefined;
/** The header value CloudFront sends to the API origin, read once per container. */
async function originSecretValue(): Promise<string> {
  if (originSecret === undefined) {
    const { SecretString } = await secrets.send(new GetSecretValueCommand({ SecretId: ORIGIN_SECRET_ARN }));
    if (!SecretString) throw new Error(`origin secret ${ORIGIN_SECRET_ARN} has no string value`);
    originSecret = SecretString;
  }
  return originSecret;
}

async function recordCorrection(detail: ValueCorrected): Promise<{ logged: boolean }> {
  if (String(detail.purpose ?? '').startsWith('reset:')) return { logged: false };
  const { plm, rows, actor, purpose } = detail;
  const owner = plm.toLowerCase();
  const res = await fetch(`${API_BASE}/core/changes`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-origin-verify': await originSecretValue(),
      'x-atelier-profile': 'export-officer',
      'x-atelier-actor': 'links-loader',
    },
    body: JSON.stringify(rows.map(({ table, key, column, before, after }) => ({ plm: owner, table, key, column, before, after, actor, purpose }))),
  });
  if (!res.ok) throw new Error(`POST /core/changes ${res.status}: ${await res.text()}`);
  return { logged: true };
}

export async function handler(event: CloudFormationCustomResourceEvent | PlmEvent) {
  if ('detail-type' in event) {
    switch (event['detail-type']) {
      case CAD_PUBLISHED:
        return { graph: FILE_INDEX_GRAPH, triples: await setCadFile(event.detail) };
      case VALUE_CORRECTED:
        return recordCorrection(event.detail);
      default:
        throw new Error(`Unexpected detail-type ${(event as EventBridgeEvent<string, unknown>)['detail-type']}`);
    }
  }
  if (event.RequestType === 'Delete') return { PhysicalResourceId: event.PhysicalResourceId };
  const triples: Record<string, number> = {};
  for (const [file, graph] of Object.entries(GRAPHS)) triples[graph] = await replaceGraph(file, graph);
  return { PhysicalResourceId: 'atelier-graphs', Data: { Triples: JSON.stringify(triples) } };
}
