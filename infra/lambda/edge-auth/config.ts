// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { GetParameterCommand, SSMClient } from '@aws-sdk/client-ssm';

/** The pool, its public app client and the hosted-UI base URL the edge function signs viewers in with. */
export interface EdgeAuthConfig {
  readonly userPoolId: string;
  readonly clientId: string;
  /** Hosted-UI base URL, https://<prefix>.auth.<region>.amazoncognito.com. */
  readonly domain: string;
}

// Lambda@Edge functions carry no environment variables, so the parameter name and its
// region are fixed when the bundle is built (esbuild `define`), and the values, which
// exist only once the distribution does, are read from SSM at cold start.
const PARAMETER_NAME = process.env.EDGE_AUTH_PARAMETER ?? '';
const REGION = process.env.EDGE_AUTH_REGION ?? '';

let cached: Promise<EdgeAuthConfig> | undefined;

/** The configuration, read once per container; a failed read is retried on the next request. */
export function loadConfig(): Promise<EdgeAuthConfig> {
  cached ??= read().catch((e: unknown) => {
    cached = undefined;
    throw e;
  });
  return cached;
}

async function read(): Promise<EdgeAuthConfig> {
  const ssm = new SSMClient({ region: REGION });
  const out = await ssm.send(new GetParameterCommand({ Name: PARAMETER_NAME }));
  const value = out.Parameter?.Value;
  if (!value) throw new Error(`SSM parameter ${PARAMETER_NAME} has no value`);
  const cfg = JSON.parse(value) as Partial<EdgeAuthConfig>;
  for (const key of ['userPoolId', 'clientId', 'domain'] as const) {
    if (!cfg[key]) throw new Error(`SSM parameter ${PARAMETER_NAME} lacks ${key}`);
  }
  return cfg as EdgeAuthConfig;
}
