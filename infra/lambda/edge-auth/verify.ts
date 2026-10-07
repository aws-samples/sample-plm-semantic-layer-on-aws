// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { CognitoJwtVerifier } from 'aws-jwt-verify';
import type { EdgeAuthConfig } from './config.ts';

// The ID token names the app client in `aud`; the verifier derives the issuer from the
// pool id and checks signature (against the pool's JWKS), issuer, audience, token use
// and expiry.
const create = (cfg: EdgeAuthConfig) =>
  CognitoJwtVerifier.create({ userPoolId: cfg.userPoolId, clientId: cfg.clientId, tokenUse: 'id' });

let verifier: ReturnType<typeof create> | undefined;

/** One verifier per container; it keeps the pool's JWKS after the first fetch. */
export function verifierFor(cfg: EdgeAuthConfig): ReturnType<typeof create> {
  verifier ??= create(cfg);
  return verifier;
}

/** True for a token this pool issued to this client that has not expired; false for anything else. */
export async function isValidIdToken(cfg: EdgeAuthConfig, token: string | undefined): Promise<boolean> {
  if (!token) return false;
  try {
    await verifierFor(cfg).verify(token);
    return true;
  } catch {
    return false;
  }
}
