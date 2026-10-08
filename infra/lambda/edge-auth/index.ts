// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Viewer-request Lambda@Edge function on every behaviour of the web distribution.
//
// A request carrying a valid Cognito ID token in its cookie passes to the origin
// unchanged, headers included, so x-atelier-profile still reaches the services. Without
// one, a browser document request is sent to the hosted UI (authorization code flow
// with PKCE) and comes back through /callback, which exchanges the code, sets the
// cookies and returns the viewer to the path they asked for; an API or agent call,
// or anything else a script fetches, gets a 401 JSON answer the web client turns into
// a page reload. /signout clears the cookie and ends the hosted-UI session.
import type { CloudFrontRequest, CloudFrontRequestEvent, CloudFrontRequestResult } from 'aws-lambda';
import { loadConfig, type EdgeAuthConfig } from './config.ts';
import { clearCookie, cookies, header, redirect, setCookie, textResponse, unauthorizedJson } from './http.ts';
import { authorizeUrl, decodeLogin, encodeLogin, exchangeCode, logoutUrl, newLogin } from './oauth.ts';
import { isValidIdToken } from './verify.ts';

/** The ID token; its Max-Age is the token's lifetime, so an expired cookie is gone before it is rejected. */
export const ID_COOKIE = 'atelier.id';
/** The sign-in in flight (PKCE verifier, state, return path); lives long enough to type a password. */
export const LOGIN_COOKIE = 'atelier.login';
export const CALLBACK_PATH = '/callback';
export const SIGNOUT_PATH = '/signout';
const LOGIN_TTL_SECONDS = 300;

export async function handler(event: CloudFrontRequestEvent): Promise<CloudFrontRequestResult> {
  const request = event.Records[0].cf.request;
  const cfg = await loadConfig();
  // Redirect URIs are derived from the Host header: the distribution's domain is not
  // known when this bundle is built, and a custom domain answers on the same distribution.
  const origin = `https://${header(request, 'host')}`;
  const jar = cookies(request);

  if (request.uri === CALLBACK_PATH) return callback(cfg, request, origin, jar[LOGIN_COOKIE]);
  if (request.uri === SIGNOUT_PATH) return redirect(logoutUrl(cfg, `${origin}/`), [clearCookie(ID_COOKIE)]);

  if (await isValidIdToken(cfg, jar[ID_COOKIE])) return request;

  if (isScriptRequest(request)) return unauthorizedJson('Sign in required');
  const login = newLogin(request.querystring ? `${request.uri}?${request.querystring}` : request.uri);
  return redirect(authorizeUrl(cfg, `${origin}${CALLBACK_PATH}`, login), [
    setCookie(LOGIN_COOKIE, encodeLogin(login), LOGIN_TTL_SECONDS),
  ]);
}

/** An API or agent call, or any fetch that wants JSON or carries the profile header: answered, never redirected. */
function isScriptRequest(request: CloudFrontRequest): boolean {
  return (
    request.uri.startsWith('/api/') ||
    request.uri.startsWith('/agent/') ||
    (header(request, 'accept') ?? '').includes('application/json') ||
    header(request, 'x-atelier-profile') !== undefined
  );
}

async function callback(
  cfg: EdgeAuthConfig,
  request: CloudFrontRequest,
  origin: string,
  loginCookie: string | undefined,
): Promise<CloudFrontRequestResult> {
  const query = new URLSearchParams(request.querystring);
  const error = query.get('error');
  if (error) return textResponse('401', `Sign-in failed: ${error}. ${query.get('error_description') ?? ''}`.trim());
  const login = decodeLogin(loginCookie);
  const code = query.get('code');
  if (!login || !code || query.get('state') !== login.state) {
    return textResponse('400', 'This sign-in cannot be completed; open the site again.');
  }
  const tokens = await exchangeCode(cfg, `${origin}${CALLBACK_PATH}`, code, login.verifier);
  return redirect(`${origin}${login.returnTo}`, [
    setCookie(ID_COOKIE, tokens.idToken, tokens.expiresIn),
    clearCookie(LOGIN_COOKIE),
  ]);
}
