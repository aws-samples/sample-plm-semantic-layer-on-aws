// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The edge sign-in function against a stubbed SSM client, a generated RSA key whose
// JWKS is preloaded into the verifier, and a stubbed token endpoint:
//   node --test infra/lambda/edge-auth/index.test.mts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash, generateKeyPairSync, sign } from 'node:crypto';
import type { CloudFrontRequest, CloudFrontResultResponse } from 'aws-lambda';
import { SSMClient } from '@aws-sdk/client-ssm';

const PARAMETER = '/Atelier-test/web/edge-auth';
const REGION = 'eu-west-1';
const POOL = 'eu-west-1_TestPool1';
const CLIENT = '1example23456789abcdefghij';
const DOMAIN = 'https://atelier-test.auth.eu-west-1.amazoncognito.com';
const HOST = 'd111111abcdef8.cloudfront.net';
process.env.EDGE_AUTH_PARAMETER = PARAMETER;
process.env.EDGE_AUTH_REGION = REGION;

const parameterReads: string[] = [];
SSMClient.prototype.send = (async (command: { input: { Name?: string } }) => {
  parameterReads.push(String(command.input.Name));
  return { Parameter: { Value: JSON.stringify({ userPoolId: POOL, clientId: CLIENT, domain: DOMAIN }) } };
}) as unknown as typeof SSMClient.prototype.send;

interface Call { url: string; method: string; body: string }
const calls: Call[] = [];
const ISSUED_ID_TOKEN = 'issued.id.token';
globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
  calls.push({ url: String(url), method: init?.method ?? 'GET', body: String(init?.body) });
  return Response.json({ id_token: ISSUED_ID_TOKEN, access_token: 'a', refresh_token: 'r', expires_in: 3600, token_type: 'Bearer' });
}) as typeof fetch;

const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const KID = 'test-key';
const jwk = publicKey.export({ format: 'jwk' }) as { kty: string; n: string; e: string };
const jwks = { keys: [{ ...jwk, kid: KID, alg: 'RS256', use: 'sig' }] };

const b64 = (o: object): string => Buffer.from(JSON.stringify(o)).toString('base64url');
function idToken(claims: Record<string, unknown> = {}): string {
  const now = Math.floor(Date.now() / 1000);
  const payload = {
    sub: 'u1', aud: CLIENT, iss: `https://cognito-idp.${REGION}.amazonaws.com/${POOL}`, token_use: 'id',
    iat: now - 60, exp: now + 3600, email: 'someone@example.com', ...claims,
  };
  const data = `${b64({ alg: 'RS256', kid: KID, typ: 'JWT' })}.${b64(payload)}`;
  return `${data}.${sign('sha256', Buffer.from(data), privateKey).toString('base64url')}`;
}

const { handler, ID_COOKIE, LOGIN_COOKIE } = await import('./index.ts');
const { verifierFor } = await import('./verify.ts');
const { decodeLogin, encodeLogin, newLogin } = await import('./oauth.ts');
verifierFor({ userPoolId: POOL, clientId: CLIENT, domain: DOMAIN }).cacheJwks(jwks);

interface RequestOptions { method?: string; headers?: Record<string, string>; querystring?: string }
function request(uri: string, o: RequestOptions = {}): CloudFrontRequest {
  const headers: CloudFrontRequest['headers'] = { host: [{ key: 'Host', value: HOST }] };
  for (const [k, v] of Object.entries(o.headers ?? {})) headers[k.toLowerCase()] = [{ key: k, value: v }];
  return { uri, method: o.method ?? 'GET', querystring: o.querystring ?? '', clientIp: '192.0.2.1', headers };
}
const event = (req: CloudFrontRequest) => ({
  Records: [{ cf: { config: { distributionDomainName: HOST, distributionId: 'E1', eventType: 'viewer-request', requestId: 'r' }, request: req } }],
});
const run = async (req: CloudFrontRequest) => (await handler(event(req) as never)) as CloudFrontResultResponse;
const headerValue = (res: CloudFrontResultResponse, name: string) => res.headers?.[name]?.[0]?.value;
const setCookies = (res: CloudFrontResultResponse) => (res.headers?.['set-cookie'] ?? []).map((h) => h.value);
const cookieOf = (res: CloudFrontResultResponse, name: string) => setCookies(res).find((c) => c.startsWith(`${name}=`));

test('a request with a valid ID token cookie passes to the origin unchanged, headers included', async () => {
  const req = request('/api/parts', {
    headers: { cookie: `other=1; ${ID_COOKIE}=${idToken()}`, 'x-atelier-profile': 'fr-engineer', accept: 'application/json' },
  });
  const before = structuredClone(req);
  const result = await handler(event(req) as never);
  assert.deepEqual(result, before);
  assert.equal(calls.length, 0, 'no token endpoint call');
});

test('a document request without a token is redirected to the hosted UI with PKCE, the login cookie holding the verifier', async () => {
  const res = await run(request('/parts', { querystring: 'plm=fr', headers: { accept: 'text/html,application/xhtml+xml' } }));
  assert.equal(res.status, '302');
  const location = new URL(headerValue(res, 'location')!);
  assert.equal(`${location.origin}${location.pathname}`, `${DOMAIN}/oauth2/authorize`);
  const q = location.searchParams;
  assert.equal(q.get('response_type'), 'code');
  assert.equal(q.get('client_id'), CLIENT);
  assert.equal(q.get('redirect_uri'), `https://${HOST}/callback`);
  assert.equal(q.get('scope'), 'openid email');
  assert.equal(q.get('code_challenge_method'), 'S256');

  const cookie = cookieOf(res, LOGIN_COOKIE)!;
  assert.match(cookie, /; Path=\/; Max-Age=300; Secure; HttpOnly; SameSite=Lax$/);
  const login = decodeLogin(cookie.slice(LOGIN_COOKIE.length + 1, cookie.indexOf(';')))!;
  assert.equal(q.get('state'), login.state, 'the state in the URL is the one in the cookie');
  assert.equal(q.get('code_challenge'), createHash('sha256').update(login.verifier).digest('base64url'));
  assert.equal(login.returnTo, '/parts?plm=fr');
  assert.equal(cookieOf(res, ID_COOKIE), undefined, 'no session cookie yet');
  assert.equal(calls.length, 0);
});

test('a script request without a token gets 401 JSON, never a redirect', async () => {
  for (const req of [
    request('/api/parts'),
    request('/agent/invocations', { method: 'POST' }),
    request('/config.json', { headers: { accept: 'application/json' } }),
    request('/', { headers: { 'x-atelier-profile': 'fr-engineer' } }),
  ]) {
    const res = await run(req);
    assert.equal(res.status, '401', req.uri);
    assert.equal(headerValue(res, 'content-type'), 'application/json', req.uri);
    assert.equal(headerValue(res, 'location'), undefined, req.uri);
    assert.equal(typeof (JSON.parse(res.body!) as { error: unknown }).error, 'string', req.uri);
  }
  assert.equal(calls.length, 0);
});

test('an expired token, or one issued to another client, is refused on /api/* with 401', async () => {
  const now = Math.floor(Date.now() / 1000);
  for (const token of [idToken({ exp: now - 10 }), idToken({ aud: 'another-client' }), 'not.a.jwt']) {
    const res = await run(request('/api/parts', { headers: { cookie: `${ID_COOKIE}=${token}` } }));
    assert.equal(res.status, '401');
  }
});

test('/callback with the code and matching state exchanges the code with the PKCE verifier and sets the session cookie', async () => {
  calls.length = 0;
  const login = newLogin('/parts?plm=fr');
  const res = await run(request('/callback', {
    querystring: `code=abc123&state=${login.state}`,
    headers: { cookie: `${LOGIN_COOKIE}=${encodeLogin(login)}` },
  }));

  assert.equal(calls.length, 1);
  const [exchange] = calls;
  assert.equal(exchange.url, `${DOMAIN}/oauth2/token`);
  assert.equal(exchange.method, 'POST');
  assert.deepEqual(Object.fromEntries(new URLSearchParams(exchange.body)), {
    grant_type: 'authorization_code', client_id: CLIENT, code: 'abc123',
    redirect_uri: `https://${HOST}/callback`, code_verifier: login.verifier,
  });

  assert.equal(res.status, '302');
  assert.equal(headerValue(res, 'location'), `https://${HOST}/parts?plm=fr`);
  assert.equal(cookieOf(res, ID_COOKIE), `${ID_COOKIE}=${ISSUED_ID_TOKEN}; Path=/; Max-Age=3600; Secure; HttpOnly; SameSite=Lax`);
  assert.equal(cookieOf(res, LOGIN_COOKIE), `${LOGIN_COOKIE}=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Lax`);
});

test('/callback whose state does not match the login cookie is refused before any token request', async () => {
  calls.length = 0;
  const login = newLogin('/');
  for (const [querystring, cookie] of [
    [`code=abc123&state=forged`, `${LOGIN_COOKIE}=${encodeLogin(login)}`],
    [`code=abc123&state=${login.state}`, ''],
    [`code=abc123&state=${login.state}`, `${LOGIN_COOKIE}=${encodeLogin({ ...login, returnTo: '//evil.example/' })}`],
  ]) {
    const res = await run(request('/callback', { querystring, headers: cookie ? { cookie } : {} }));
    assert.equal(res.status, '400', querystring);
  }
  assert.equal(calls.length, 0);
});

test('/signout clears the session cookie and ends the hosted-UI session', async () => {
  const res = await run(request('/signout', { headers: { cookie: `${ID_COOKIE}=${idToken()}` } }));
  assert.equal(res.status, '302');
  assert.equal(headerValue(res, 'location'), `${DOMAIN}/logout?client_id=${CLIENT}&logout_uri=${encodeURIComponent(`https://${HOST}/`)}`);
  assert.equal(cookieOf(res, ID_COOKIE), `${ID_COOKIE}=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Lax`);
});

test('the configuration is read from SSM once per container', () => {
  assert.deepEqual(parameterReads, [PARAMETER]);
});
