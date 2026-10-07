// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { createHash, randomBytes } from 'crypto';
import type { EdgeAuthConfig } from './config.ts';

const SCOPES = 'openid email';

/** What a sign-in in flight needs when the hosted UI comes back to /callback. */
export interface Login {
  /** PKCE code verifier; its S256 challenge went with the authorization request. */
  readonly verifier: string;
  /** Random state echoed by the hosted UI; a mismatch is a forged callback. */
  readonly state: string;
  /** Same-origin path (with query) the viewer asked for before signing in. */
  readonly returnTo: string;
}

const random = (): string => randomBytes(32).toString('base64url');
const challenge = (verifier: string): string => createHash('sha256').update(verifier).digest('base64url');

export function newLogin(returnTo: string): Login {
  return { verifier: random(), state: random(), returnTo };
}

/** The login as a cookie value: base64url JSON, opaque to the browser. */
export function encodeLogin(login: Login): string {
  return Buffer.from(JSON.stringify(login)).toString('base64url');
}

/** The login a /callback cookie carries, or null when absent, unreadable or pointing off-site. */
export function decodeLogin(cookie: string | undefined): Login | null {
  if (!cookie) return null;
  try {
    const login = JSON.parse(Buffer.from(cookie, 'base64url').toString()) as Partial<Login>;
    if (!login.verifier || !login.state || typeof login.returnTo !== 'string') return null;
    if (!login.returnTo.startsWith('/') || login.returnTo.startsWith('//')) return null;
    return login as Login;
  } catch {
    return null;
  }
}

/** The hosted UI's authorization endpoint for the code flow with PKCE (public client, no secret). */
export function authorizeUrl(cfg: EdgeAuthConfig, redirectUri: string, login: Login): string {
  const query = new URLSearchParams({
    response_type: 'code',
    client_id: cfg.clientId,
    redirect_uri: redirectUri,
    scope: SCOPES,
    state: login.state,
    code_challenge: challenge(login.verifier),
    code_challenge_method: 'S256',
  });
  return `${cfg.domain}/oauth2/authorize?${query}`;
}

/** Ends the hosted UI's own session too, then returns to the site. */
export function logoutUrl(cfg: EdgeAuthConfig, logoutUri: string): string {
  const query = new URLSearchParams({ client_id: cfg.clientId, logout_uri: logoutUri });
  return `${cfg.domain}/logout?${query}`;
}

export interface Tokens {
  readonly idToken: string;
  /** Lifetime of the tokens in seconds, as the token endpoint states it. */
  readonly expiresIn: number;
}

/** Exchanges the authorization code at the token endpoint, proving possession of the PKCE verifier. */
export async function exchangeCode(cfg: EdgeAuthConfig, redirectUri: string, code: string, verifier: string): Promise<Tokens> {
  const res = await fetch(`${cfg.domain}/oauth2/token`, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: cfg.clientId,
      code,
      redirect_uri: redirectUri,
      code_verifier: verifier,
    }).toString(),
  });
  if (!res.ok) throw new Error(`token endpoint answered HTTP ${res.status}: ${await res.text()}`);
  const body = (await res.json()) as { id_token?: string; expires_in?: number };
  if (!body.id_token) throw new Error('token endpoint answered without an id_token');
  return { idToken: body.id_token, expiresIn: body.expires_in ?? 3600 };
}
