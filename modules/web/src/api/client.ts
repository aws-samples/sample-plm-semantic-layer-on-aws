// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { RuntimeConfig } from '../config';
import { PROFILE_HEADER } from './profile';

export class ApiError extends Error {
  constructor(
    readonly path: string,
    readonly status: number | null,
    message: string,
  ) {
    super(message);
  }
}

const RELOAD_KEY = 'atelier.reloadedAt';

/**
 * The edge wants the viewer to sign in: the Cognito edge function answers 401 once the
 * session expired. Reload once so the edge signs the viewer in again.
 */
export function reloadToSignIn(path: string, status: number): never {
  const last = Number(sessionStorage.getItem(RELOAD_KEY) ?? 0);
  if (Date.now() - last > 15_000) {
    sessionStorage.setItem(RELOAD_KEY, String(Date.now()));
    location.reload();
  }
  throw new ApiError(path, status, 'Your session has expired. Reload the page to sign in again.');
}

type Fixtures = typeof import('../dev/fixtures');
let fixtures: Promise<Fixtures> | null = null;
if (import.meta.env.DEV && import.meta.env.VITE_FIXTURES === '1') {
  fixtures = import('../dev/fixtures');
}

/** Every API call carries the viewer's profile; the services filter on it. */
const headers = (accept: string, profile: string) => ({ accept, [PROFILE_HEADER]: profile });

/** The JSON body of an answer; a body that is not JSON, or a failure status, is an error naming the URL. */
async function readJson(res: Response, path: string, url: string): Promise<unknown> {
  // A 401 is the edge asking for a sign-in; a 403 is the server refusing the action.
  if (res.status === 401) reloadToSignIn(path, res.status);
  const text = await res.text();
  let body: unknown;
  try {
    body = JSON.parse(text);
  } catch {
    throw new ApiError(path, res.status, `${url} answered HTTP ${res.status} with a body that is not JSON.`);
  }
  if (!res.ok) {
    const reason = (body as { error?: string } | null)?.error ?? `HTTP ${res.status}`;
    throw new ApiError(path, res.status, `${url}: ${reason}`);
  }
  return body;
}

export async function getJson<T>(config: RuntimeConfig, path: string, profile: string): Promise<T> {
  if (import.meta.env.DEV && fixtures) return (await (await fixtures).respond(path, profile)) as T;
  const url = `${config.apiBase}${path}`;
  let res: Response;
  try {
    res = await fetch(url, { headers: headers('application/json', profile), credentials: 'same-origin' });
  } catch {
    throw new ApiError(path, null, `The request to ${url} did not reach the server.`);
  }
  return (await readJson(res, path, url)) as T;
}

/** Sends a JSON body and reads the JSON answer; the officer-only demo controls are its callers. */
export async function postJson<T>(config: RuntimeConfig, path: string, profile: string, body: unknown): Promise<T> {
  if (import.meta.env.DEV && fixtures) return (await (await fixtures).mutate(path, profile, body)) as T;
  const url = `${config.apiBase}${path}`;
  let res: Response;
  try {
    res = await fetch(url, {
      method: 'POST',
      headers: { ...headers('application/json', profile), 'content-type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify(body),
    });
  } catch {
    throw new ApiError(path, null, `The request to ${url} did not reach the server.`);
  }
  return (await readJson(res, path, url)) as T;
}

/** Fetches a Turtle document from the API; an HTML page in its place is an error, not a mapping. */
export async function getTurtle(config: RuntimeConfig, path: string, profile: string): Promise<string> {
  if (import.meta.env.DEV && fixtures) return (await (await fixtures).respond(path, profile)) as string;
  const url = `${config.apiBase}${path}`;
  let res: Response;
  try {
    res = await fetch(url, { headers: headers('text/turtle', profile), credentials: 'same-origin' });
  } catch {
    throw new ApiError(path, null, `The request to ${url} did not reach the server.`);
  }
  if (res.status === 401) reloadToSignIn(path, res.status);
  const text = await res.text();
  if (!res.ok) throw new ApiError(path, res.status, `${url} answered HTTP ${res.status}.`);
  if (!(res.headers.get('content-type') ?? '').includes('turtle') || /^\s*</.test(text)) {
    throw new ApiError(path, res.status, `${url} did not answer with text/turtle.`);
  }
  return text;
}

/**
 * Fetches a STEP file from the presigned S3 URL the query service issued. S3 answers
 * 403 once the URL has expired; the caller refreshes the parts and retries once.
 */
export async function getCad(cadUrl: string, signal?: AbortSignal): Promise<ArrayBuffer> {
  let res: Response;
  try {
    res = await fetch(cadUrl, { signal });
  } catch (e) {
    if (signal?.aborted) throw e;
    throw new ApiError(cadUrl, null, 'the request did not reach the CAD store');
  }
  if (!res.ok) throw new ApiError(cadUrl, res.status, `the CAD store answered HTTP ${res.status}`);
  return res.arrayBuffer();
}
