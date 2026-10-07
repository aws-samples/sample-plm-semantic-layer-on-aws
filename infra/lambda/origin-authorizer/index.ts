// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Allows a request only when it carries the secret header CloudFront adds on
// the /api/* behaviour, so the API is reachable through the Cognito-gated
// distribution and not through its execute-api hostname.
import { timingSafeEqual } from 'crypto';
import { SecretsManagerClient, GetSecretValueCommand } from '@aws-sdk/client-secrets-manager';
import type { APIGatewayRequestSimpleAuthorizerHandlerV2 } from 'aws-lambda';

const client = new SecretsManagerClient({});
let expected: Buffer | undefined;

async function secret(): Promise<Buffer> {
  if (!expected) {
    const res = await client.send(new GetSecretValueCommand({ SecretId: process.env.SECRET_ARN }));
    expected = Buffer.from(res.SecretString ?? '');
  }
  return expected;
}

export const handler: APIGatewayRequestSimpleAuthorizerHandlerV2 = async (event) => {
  const presented = Buffer.from(event.headers?.['x-origin-verify'] ?? '');
  const want = await secret();
  const ok = want.length > 0 && presented.length === want.length && timingSafeEqual(presented, want);
  return { isAuthorized: ok };
};
