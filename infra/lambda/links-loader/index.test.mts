// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The EventBridge branches of the links loader, against a stubbed fetch and a
// stubbed Secrets Manager client, and the SigV4 signing of its Neptune requests
// against a signature computed here from the specification:
//   node --test infra/lambda/links-loader/index.test.mts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash, createHmac } from 'node:crypto';
import { SecretsManagerClient } from '@aws-sdk/client-secrets-manager';

const LINKS = 'https://example.com/atelier/graph/links';
const FILE_INDEX = 'https://example.com/atelier/graph/fileindex';
const SPARQL = 'https://neptune.test:8182/sparql/';
const API_BASE = 'https://api.test/api';
const SECRET_ARN = 'arn:aws:secretsmanager:eu-west-1:111111111111:secret:origin-AbCdEf'; // pragma: allowlist secret
const SECRET = 'origin-header-value'; // pragma: allowlist secret
process.env.GSP_URL = `${SPARQL}gsp/`;
process.env.GRAPHS = JSON.stringify({ 'links.ttl': LINKS, 'fileindex.ttl': FILE_INDEX });
process.env.API_BASE = API_BASE;
process.env.ORIGIN_SECRET_ARN = SECRET_ARN;
// The signing path resolves the function role's credentials through the default provider chain, which reads these
// first; a fixed pair, so a signature can be recomputed here. Off at import: the switch is set per test.
const REGION = 'eu-west-1';
const ACCESS_KEY_ID = 'AKIDLOADERTESTKEY0001'; // gitleaks:allow (a test value, not a credential) pragma: allowlist secret
const SECRET_ACCESS_KEY = 'loader-test-secret-key-not-a-credential'; // gitleaks:allow (a test value, not a credential) pragma: allowlist secret
process.env.AWS_REGION = REGION;
process.env.AWS_ACCESS_KEY_ID = ACCESS_KEY_ID;
process.env.AWS_SECRET_ACCESS_KEY = SECRET_ACCESS_KEY;
delete process.env.AWS_SESSION_TOKEN;
delete process.env.AWS_PROFILE;
delete process.env.NEPTUNE_IAM_AUTH;

interface Call { url: string; method: string; headers: Record<string, string>; body: string }
const calls: Call[] = [];
let changesStatus = 200;
globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
  const headers = (init?.headers ?? {}) as Record<string, string>;
  const body = String(init?.body);
  calls.push({ url: String(url), method: init?.method ?? 'GET', headers, body });
  if (body.startsWith('query=')) return Response.json({ results: { bindings: [{ n: { value: '28' } }] } });
  if (String(url).endsWith('/core/changes')) return new Response(changesStatus === 200 ? body : 'refused', { status: changesStatus });
  return new Response('', { status: 200 });
}) as typeof fetch;

const secretIds: string[] = [];
SecretsManagerClient.prototype.send = (async (command: { input: { SecretId?: string } }) => {
  secretIds.push(String(command.input.SecretId));
  return { SecretString: SECRET };
}) as unknown as typeof SecretsManagerClient.prototype.send;

const { handler, signNeptuneRequest, CAD_PUBLISHED, VALUE_CORRECTED } = await import('./index.ts');

const event = (detailType: string, detail: object) => ({
  'detail-type': detailType,
  source: 'atelier.plm',
  detail,
  id: '1', version: '0', account: '0', time: '', region: '', resources: [],
});
const partIri = 'https://example.com/atelier/fr/part/FR-RAD-001';
const cad = { plm: 'fr', part: 'FR-RAD-001', partIri, cadFile: 'cad/ornithopter/fr-radome.stp', at: '2026-01-01T00:00:00Z' };
const corrected = {
  plm: 'FR',
  rows: [
    { table: 'connecteur', key: 'FR-J07', column: 'pos_x_mm', before: 12674.3, after: 12670 },
    { table: 'piece', key: 'FR-ORN-NACI-001', column: 'etat', before: null, after: 'Publié' }, // gitleaks:allow (a native record key, not a credential)
  ],
  actor: 'fr-engineer', purpose: 'smoke: FR PLM releases J07 onto the joint plane', at: '2026-01-01T00:00:00Z',
};
const form = (c: Call) => Object.fromEntries(new URLSearchParams(c.body));
const touchesNeptune = (c: Call) => c.url.startsWith(SPARQL);
const signed = (c: Call) => Object.keys(c.headers).some((h) => h.toLowerCase() === 'authorization');

/**
 * Signature Version 4 as the specification states it, computed here independently of the signer under test: the
 * canonical request (method, path, sorted query string encoded per RFC 3986, lowercased sorted headers, signed header
 * names, hex SHA-256 of the body), the string to sign, and the HMAC chain over the date, region, service and
 * terminator with the fixed secret key.
 */
function expectedSigV4(method: string, url: string, headers: Record<string, string>, body: string, signingDate: Date, secretKey = SECRET_ACCESS_KEY) {
  const u = new URL(url);
  const amzDate = signingDate.toISOString().replace(/[-:]|\.\d{3}/g, '');
  const day = amzDate.slice(0, 8);
  const sha256 = (s: string) => createHash('sha256').update(s, 'utf8').digest('hex');
  const canonicalHeaders = Object.entries({ ...headers, host: u.host, 'x-amz-date': amzDate, 'x-amz-content-sha256': sha256(body) })
    .map(([name, value]) => [name.toLowerCase(), value.trim()] as const)
    .sort(([a], [b]) => (a < b ? -1 : 1));
  const signedHeaders = canonicalHeaders.map(([name]) => name).join(';');
  const encode = (s: string) => encodeURIComponent(s).replace(/[!'()*]/g, (c) => `%${c.charCodeAt(0).toString(16).toUpperCase()}`);
  const canonicalQuery = [...u.searchParams].map(([k, v]) => `${encode(k)}=${encode(v)}`).sort().join('&');
  const canonicalRequest = [method, u.pathname, canonicalQuery, `${canonicalHeaders.map(([n, v]) => `${n}:${v}`).join('\n')}\n`, signedHeaders, sha256(body)].join('\n');
  const scope = `${day}/${REGION}/neptune-db/aws4_request`;
  const stringToSign = ['AWS4-HMAC-SHA256', amzDate, scope, sha256(canonicalRequest)].join('\n');
  const hmac = (key: string | Buffer, data: string) => createHmac('sha256', key).update(data, 'utf8').digest();
  const signingKey = hmac(hmac(hmac(hmac(`AWS4${secretKey}`, day), REGION), 'neptune-db'), 'aws4_request');
  return { amzDate, scope, signedHeaders, signature: createHmac('sha256', signingKey).update(stringToSign, 'utf8').digest('hex') };
}

test('part.cad.published sets atelier:cadFile on the part in the file index with one SPARQL DELETE/INSERT and returns the count', async () => {
  calls.length = 0;
  const result = await handler(event(CAD_PUBLISHED, cad) as never);

  assert.deepEqual(result, { graph: FILE_INDEX, triples: 28 });
  assert.equal(calls.length, 2, 'one update, one count');
  assert.equal(calls.filter((c) => c.method !== 'POST').length, 0, 'never a PUT or a GET');
  assert.equal(calls.filter((c) => c.headers['Content-Type'] === 'text/turtle').length, 0, 'never a Graph Store write');
  assert.equal(calls.filter((c) => c.url.includes(LINKS) || decodeURIComponent(c.body).includes(LINKS)).length, 0, 'never touches the links graph');
  assert.equal(calls.filter((c) => c.url.startsWith(API_BASE)).length, 0, 'never calls the API');
  assert.equal(calls.filter(signed).length, 0, 'nothing is signed while NEPTUNE_IAM_AUTH is off');

  const [update, count] = calls;
  assert.equal(update.url, SPARQL);
  assert.equal(update.headers['Content-Type'], 'application/x-www-form-urlencoded');
  assert.deepEqual(Object.keys(form(update)), ['update']);
  const pattern = `GRAPH <${FILE_INDEX}> { <${partIri}> <https://example.com/atelier/ontology#cadFile> ?old }`;
  assert.equal(form(update).update,
    `DELETE { ${pattern} } INSERT { GRAPH <${FILE_INDEX}> { <${partIri}> <https://example.com/atelier/ontology#cadFile> "cad/ornithopter/fr-radome.stp" } } WHERE { OPTIONAL { ${pattern} } }`);

  assert.equal(count.url, SPARQL);
  assert.match(form(count).query, new RegExp(`^SELECT \\(COUNT\\(\\*\\) AS \\?n\\) WHERE \\{ GRAPH <${FILE_INDEX}> `));
});

test('a part IRI outside https://example.com/atelier/ or that could break out of the update is refused before any request', async () => {
  for (const bad of [
    'https://evil.example/atelier/fr/part/FR-RAD-001',
    'http://example.com/atelier/fr/part/FR-RAD-001',
    'https://example.com/atelier/fr/part/FR-RAD-001> <b> <c',
    'https://example.com/atelier/fr/part/FR RAD',
    'urn:not-http',
  ]) {
    calls.length = 0;
    await assert.rejects(handler(event(CAD_PUBLISHED, { ...cad, partIri: bad }) as never), /partIri must be an https IRI under https:\/\/example\.com\/atelier\//, bad);
    assert.equal(calls.length, 0, bad);
  }
});

test('a CAD key outside cad/<a-z0-9->.stp is refused before any request', async () => {
  for (const bad of ['cad/../secret.stp', 'cad/fr-radome.stp', 'cad/ornithopter/FR-Radome.stp', 'cad/ornithopter/fr-radome.step', 'other/ornithopter/fr-radome.stp', 'cad/ornithopter/fr radome.stp', 'cad/ornithopter/fr-radome.stp" . <x> <y> "z']) {
    calls.length = 0;
    await assert.rejects(handler(event(CAD_PUBLISHED, { ...cad, cadFile: bad }) as never), /cadFile must match/, bad);
    assert.equal(calls.length, 0, bad);
  }
});

test('part.value.corrected appends every row of the correction to the change log in one POST /core/changes with the origin secret, as the officer, naming itself as actor', async () => {
  calls.length = 0;
  secretIds.length = 0;
  const result = await handler(event(VALUE_CORRECTED, corrected) as never);

  assert.deepEqual(result, { logged: true });
  assert.equal(calls.length, 1, 'one POST, nothing else');
  assert.equal(calls.filter(touchesNeptune).length, 0, 'never touches Neptune');
  const [post] = calls;
  assert.equal(post.url, `${API_BASE}/core/changes`);
  assert.equal(post.method, 'POST');
  assert.deepEqual(post.headers, {
    'Content-Type': 'application/json',
    'x-origin-verify': SECRET,
    'x-atelier-profile': 'export-officer',
    'x-atelier-actor': 'links-loader',
  });
  assert.deepEqual(JSON.parse(post.body), [
    { plm: 'fr', table: 'connecteur', key: 'FR-J07', column: 'pos_x_mm', before: 12674.3, after: 12670, actor: 'fr-engineer', purpose: corrected.purpose },
    { plm: 'fr', table: 'piece', key: 'FR-ORN-NACI-001', column: 'etat', before: null, after: 'Publié', actor: 'fr-engineer', purpose: corrected.purpose }, // gitleaks:allow (a native record key, not a credential)
  ], 'the rows as the PLM announced them, in order, each with the owner code lowercased, the actor and the purpose, the event time left out');
  assert.deepEqual(secretIds, [SECRET_ARN], 'the origin secret is read from Secrets Manager by ARN');
});

test('the origin secret is read once per container', async () => {
  calls.length = 0;
  secretIds.length = 0;
  await handler(event(VALUE_CORRECTED, corrected) as never);
  await handler(event(VALUE_CORRECTED, { ...corrected, rows: [{ ...corrected.rows[0], key: 'FR-J08' }] }) as never);
  assert.equal(calls.length, 2);
  assert.equal(secretIds.length, 0, 'cached from the first delivery');
});

test('a correction whose purpose starts with reset: (the reset undoing an earlier one) is not logged and makes no request', async () => {
  for (const purpose of ['reset: undo smoke: FR PLM releases J07 onto the joint plane', 'reset:']) {
    calls.length = 0;
    const result = await handler(event(VALUE_CORRECTED, { ...corrected, purpose }) as never);
    assert.deepEqual(result, { logged: false }, purpose);
    assert.equal(calls.length, 0, purpose);
  }
  calls.length = 0;
  assert.deepEqual(await handler(event(VALUE_CORRECTED, { ...corrected, purpose: 'undo of a reset: still a correction' }) as never), { logged: true });
  assert.equal(calls.length, 1, 'only the prefix is the reset marker');
});

test('a change log write the core service refuses fails the delivery', async () => {
  calls.length = 0;
  changesStatus = 403;
  try {
    await assert.rejects(handler(event(VALUE_CORRECTED, corrected) as never), /POST \/core\/changes 403: refused/);
  } finally {
    changesStatus = 200;
  }
  assert.equal(calls.length, 1);
});

test('any other detail-type is refused before any request', async () => {
  calls.length = 0;
  await assert.rejects(
    handler(event('interface.link.published', { plm: 'fr', from: partIri, to: partIri, at: '' }) as never),
    /Unexpected detail-type interface\.link\.published/,
  );
  assert.equal(calls.length, 0);
});

const SIGNING_DATE = new Date('2026-01-01T00:00:00Z');
const FIXED = { credentials: { accessKeyId: ACCESS_KEY_ID, secretAccessKey: SECRET_ACCESS_KEY }, region: REGION, signingDate: SIGNING_DATE };
const FORM = { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/sparql-results+json' };

test('a SPARQL POST is signed for neptune-db over its method, path, host with port, headers, date and body: the signature the specification gives for the fixed credentials and clock', async () => {
  const body = `query=${encodeURIComponent('ASK { GRAPH <' + LINKS + '> { ?s ?p ?o } }')}`;
  const headers = await signNeptuneRequest({ method: 'POST', url: SPARQL, headers: FORM, body }, FIXED);
  const expected = expectedSigV4('POST', SPARQL, FORM, body, SIGNING_DATE);

  assert.equal(headers['x-amz-date'], '20260101T000000Z');
  assert.equal(expected.signedHeaders, 'accept;content-type;host;x-amz-content-sha256;x-amz-date');
  assert.equal(headers['x-amz-content-sha256'], createHash('sha256').update(body, 'utf8').digest('hex'));
  assert.equal(headers.authorization,
    `AWS4-HMAC-SHA256 Credential=${ACCESS_KEY_ID}/20260101/${REGION}/neptune-db/aws4_request, SignedHeaders=${expected.signedHeaders}, Signature=${expected.signature}`);
  assert.equal('host' in headers, false, 'the host is signed but left to fetch, which derives it from the URL');
  assert.equal(headers['Content-Type'], FORM['Content-Type']);
  assert.equal(headers.Accept, FORM.Accept);
  assert.deepEqual(Object.keys(headers).sort(), ['Accept', 'Content-Type', 'authorization', 'x-amz-content-sha256', 'x-amz-date']);

  const other = await signNeptuneRequest({ method: 'POST', url: SPARQL, headers: FORM, body: `${body}.` }, FIXED);
  assert.notEqual(other.authorization, headers.authorization, 'the body is part of the signature');
  assert.equal(other.authorization.split('Signature=')[1], expectedSigV4('POST', SPARQL, FORM, `${body}.`, SIGNING_DATE).signature);
});

test('a Graph Store PUT is signed over its query string (the graph IRI) and its Turtle body; session credentials add the token to the signed headers', async () => {
  const url = `${SPARQL}gsp/?graph=${encodeURIComponent(LINKS)}`;
  const turtle = '<https://example.com/atelier/fr/plug/P1> <https://example.com/atelier/ontology#matesWith> <https://example.com/atelier/de/plug/P2> .\n';
  const headers = await signNeptuneRequest({ method: 'PUT', url, headers: { 'Content-Type': 'text/turtle' }, body: turtle }, FIXED);
  const expected = expectedSigV4('PUT', url, { 'Content-Type': 'text/turtle' }, turtle, SIGNING_DATE);
  assert.equal(headers.authorization,
    `AWS4-HMAC-SHA256 Credential=${ACCESS_KEY_ID}/20260101/${REGION}/neptune-db/aws4_request, SignedHeaders=content-type;host;x-amz-content-sha256;x-amz-date, Signature=${expected.signature}`);

  const session = await signNeptuneRequest({ method: 'PUT', url, headers: { 'Content-Type': 'text/turtle' }, body: turtle },
    { ...FIXED, credentials: { ...FIXED.credentials, sessionToken: 'session-token-test-value' } }); // gitleaks:allow (a test value) pragma: allowlist secret
  assert.equal(session['x-amz-security-token'], 'session-token-test-value'); // gitleaks:allow (a test value) pragma: allowlist secret
  const withToken = expectedSigV4('PUT', url, { 'Content-Type': 'text/turtle', 'x-amz-security-token': 'session-token-test-value' }, turtle, SIGNING_DATE); // gitleaks:allow (a test value) pragma: allowlist secret
  assert.equal(session.authorization,
    `AWS4-HMAC-SHA256 Credential=${ACCESS_KEY_ID}/20260101/${REGION}/neptune-db/aws4_request, SignedHeaders=content-type;host;x-amz-content-sha256;x-amz-date;x-amz-security-token, Signature=${withToken.signature}`);
});

test('with NEPTUNE_IAM_AUTH=true every Neptune request of a delivery carries a signature under the function role for neptune-db in the region, and the API call carries none', async () => {
  process.env.NEPTUNE_IAM_AUTH = 'true';
  try {
    calls.length = 0;
    const before = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    await handler(event(CAD_PUBLISHED, cad) as never);
    const after = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    assert.equal(calls.length, 2);
    for (const call of calls) {
      assert.ok(signed(call), `${call.url} is signed`);
      const match = /^AWS4-HMAC-SHA256 Credential=([^/]+)\/(\d{8})\/([a-z0-9-]+)\/([a-z-]+)\/aws4_request, SignedHeaders=accept;content-type;host;x-amz-content-sha256;x-amz-date, Signature=[0-9a-f]{64}$/
        .exec(call.headers.authorization);
      assert.ok(match, call.headers.authorization);
      assert.equal(match[1], ACCESS_KEY_ID);
      assert.ok([before, after].includes(match[2]), 'the credential scope is dated today');
      assert.equal(match[3], REGION);
      assert.equal(match[4], 'neptune-db');
      assert.match(call.headers['x-amz-date'], /^\d{8}T\d{6}Z$/);
      // The signature is the one the specification gives for the bytes actually passed to fetch.
      const plain = { ...call.headers };
      delete plain.authorization;
      const amzDate = plain['x-amz-date'];
      delete plain['x-amz-date'];
      delete plain['x-amz-content-sha256'];
      const expected = expectedSigV4(call.method, call.url, plain, call.body, new Date(amzDate.replace(/^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})Z$/, '$1-$2-$3T$4:$5:$6Z')));
      assert.equal(call.headers.authorization.split('Signature=')[1], expected.signature);
    }
    assert.notEqual(calls[0].headers.authorization, calls[1].headers.authorization, 'the update and the count have different bodies, so different signatures');

    calls.length = 0;
    await handler(event(VALUE_CORRECTED, corrected) as never);
    assert.equal(calls.length, 1);
    assert.equal(signed(calls[0]), false, 'the API Gateway call is never signed');
  } finally {
    delete process.env.NEPTUNE_IAM_AUTH;
  }
});
