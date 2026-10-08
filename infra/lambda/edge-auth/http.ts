// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { CloudFrontRequest, CloudFrontResultResponse } from 'aws-lambda';

/** The first value of a viewer header; CloudFront lower-cases the names. */
export function header(request: CloudFrontRequest, name: string): string | undefined {
  return request.headers[name]?.[0]?.value;
}

/** The viewer's cookies by name; a browser may spread them over several Cookie headers. */
export function cookies(request: CloudFrontRequest): Record<string, string> {
  const jar: Record<string, string> = {};
  for (const h of request.headers.cookie ?? []) {
    for (const part of h.value.split(';')) {
      const eq = part.indexOf('=');
      if (eq < 0) continue;
      const name = part.slice(0, eq).trim();
      if (name) jar[name] = part.slice(eq + 1).trim();
    }
  }
  return jar;
}

/**
 * A Set-Cookie value. HttpOnly and Secure always; SameSite=Lax so the hosted UI's
 * redirect back to /callback, a top-level navigation, still carries the cookie.
 */
export function setCookie(name: string, value: string, maxAge: number): string {
  return `${name}=${value}; Path=/; Max-Age=${maxAge}; Secure; HttpOnly; SameSite=Lax`;
}

export const clearCookie = (name: string): string => setCookie(name, '', 0);

function withCookies(headers: CloudFrontResultResponse['headers'], setCookies: string[]): CloudFrontResultResponse['headers'] {
  return setCookies.length ? { ...headers, 'set-cookie': setCookies.map((value) => ({ key: 'Set-Cookie', value })) } : headers;
}

export function redirect(location: string, setCookies: string[] = []): CloudFrontResultResponse {
  return {
    status: '302',
    statusDescription: 'Found',
    headers: withCookies(
      {
        location: [{ key: 'Location', value: location }],
        'cache-control': [{ key: 'Cache-Control', value: 'no-store' }],
      },
      setCookies,
    ),
  };
}

/** The answer to a script's request without a session: JSON, never a redirect the script cannot follow. */
export function unauthorizedJson(error: string): CloudFrontResultResponse {
  return {
    status: '401',
    statusDescription: 'Unauthorized',
    headers: {
      'content-type': [{ key: 'Content-Type', value: 'application/json' }],
      'cache-control': [{ key: 'Cache-Control', value: 'no-store' }],
    },
    body: JSON.stringify({ error }),
  };
}

export function textResponse(status: string, text: string): CloudFrontResultResponse {
  return {
    status,
    headers: {
      'content-type': [{ key: 'Content-Type', value: 'text/plain; charset=utf-8' }],
      'cache-control': [{ key: 'Cache-Control', value: 'no-store' }],
    },
    body: text,
  };
}
