#!/usr/bin/env node
// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import * as path from 'path';
import * as cdk from 'aws-cdk-lib';
import { AwsSolutionsChecks } from 'cdk-nag';
import { EcrStack } from '../lib/ecr-stack';
import { NetworkStack } from '../lib/network-stack';
import {
  DataStack,
  LAMBDA_BASIC_EXECUTION_REASON,
  LAMBDA_BASIC_EXECUTION_ROLE,
  LAMBDA_RUNTIME_REASON,
  Plm,
  PLMS,
} from '../lib/data-stack';
import { ServicesStack } from '../lib/services-stack';
import { WebStack, WebCertStack } from '../lib/web-stack';
import { GraphStack } from '../lib/graph-stack';
import { imageTags as computeImageTags } from '../lib/image-tags';

const app = new cdk.App();
// The cdk-nag AwsSolutions rule pack validates every synth (CDK policy validation; the report lands in
// cdk.out next to the templates). A rule either holds or is acknowledged with its reason in the stack
// that owns the resource.
cdk.Validations.of(app).addPlugins(new AwsSolutionsChecks(app));

// env context multiplexes parallel stacks in one account (dev|prod|...).
const env: string = app.node.tryGetContext('env') ?? 'prod';
if (!/^[a-z0-9-]+$/.test(env) || env.length > 16) {
  throw new Error(`Invalid env "${env}": must match ^[a-z0-9-]+$ and be <= 16 chars.`);
}
const prefix = env === 'prod' ? 'Atelier' : `Atelier-${env}`;

// Account and region come from context (deploy.sh passes both explicitly) with the
// CDK defaults as fallback. They are validated because CDK_DEFAULT_REGION follows the
// caller's profile, which would otherwise move the whole deployment to that region.
const account = String(app.node.tryGetContext('account') ?? process.env.CDK_DEFAULT_ACCOUNT ?? '');
const region = String(app.node.tryGetContext('region') ?? process.env.CDK_DEFAULT_REGION ?? '');
if (!/^\d{12}$/.test(account) || !/^[a-z]{2}-[a-z]+-\d$/.test(region)) {
  throw new Error(`account "${account}" and region "${region}" must be given with -c account=... -c region=... (or CDK_DEFAULT_ACCOUNT/CDK_DEFAULT_REGION)`);
}
const stackEnv: cdk.Environment = { account, region };

// The content-addressed tag of each service image, computed from the working tree as build-images.sh computes it.
const imageTags = computeImageTags(path.resolve(__dirname, '..', '..'));
// PLM services to run, e.g. -c plms=fr,de. Each needs its image in ECR.
const plms = String(app.node.tryGetContext('plms') ?? PLMS.join(','))
  .split(',')
  .filter(Boolean) as Plm[];
for (const p of plms) if (!PLMS.includes(p)) throw new Error(`Unknown PLM "${p}"`);

interface DomainConfig {
  fqdn: string;
  hostedZoneId: string;
  zoneName: string;
}
const domains = (app.node.tryGetContext('domains') ?? {}) as Record<string, DomainConfig | undefined>;
// cdk.json carries a REPLACE_AFTER_ZONE_CREATE placeholder until the child zone
// exists; treat that as "no custom domain" (default *.cloudfront.net domain).
const rawDomain = domains[env];
const domain = rawDomain && !rawDomain.hostedZoneId.startsWith('REPLACE') ? rawDomain : undefined;

new EcrStack(app, `${prefix}-Ecr`, { env: stackEnv, prefix });
const network = new NetworkStack(app, `${prefix}-Network`, { env: stackEnv, prefix });
const data = new DataStack(app, `${prefix}-Data`, {
  env: stackEnv,
  prefix,
  vpc: network.vpc,
  siteOrigin: domain ? `https://${domain.fqdn}` : undefined,
});
const graph = new GraphStack(app, `${prefix}-Graph`, { env: stackEnv, prefix, vpc: network.vpc });
// -c semantic=true once the Ontop, query and agent images are in ECR.
const semantic = String(app.node.tryGetContext('semantic') ?? 'false') === 'true';
// The Bedrock model or inference profile the agent calls; deploy.sh passes BEDROCK_MODEL_ID.
const modelId: string = app.node.tryGetContext('modelId') ?? 'eu.anthropic.claude-sonnet-5';
// USD per 1k tokens behind the agent's cost estimate: Claude Sonnet 5 on Bedrock in eu-west-1, standard on-demand
// (input 2.20 and output 11.00 USD per million tokens; the agent prices cache reads at 0.1 and writes at 1.25 times
// the input rate, as the price list does).
const pricePer1kInput = String(app.node.tryGetContext('pricePer1kInput') ?? '0.0022');
const pricePer1kOutput = String(app.node.tryGetContext('pricePer1kOutput') ?? '0.011');
const services = new ServicesStack(app, `${prefix}-Services`, {
  env: stackEnv,
  prefix,
  vpc: network.vpc,
  cluster: data.cluster,
  dbSecurityGroup: data.dbSecurityGroup,
  plmSecrets: data.plmSecrets,
  coreSecret: data.coreSecret,
  coreReaderSecret: data.coreReaderSecret,
  cadBucket: data.cadBucket,
  logBucket: data.logBucket,
  plms,
  imageTags,
  graph: { sparqlUrl: graph.sparqlUrl, securityGroup: graph.securityGroup, clusterResourceId: graph.clusterResourceId },
  semantic,
  modelId,
  pricePer1kInput,
  pricePer1kOutput,
});

// CloudFront needs a us-east-1 certificate, created in a sibling stack.
const webCert = domain
  ? new WebCertStack(app, `${prefix}-WebCert`, {
      env: { account: stackEnv.account, region: 'us-east-1' },
      crossRegionReferences: true,
      fqdn: domain.fqdn,
      hostedZoneId: domain.hostedZoneId,
      zoneName: domain.zoneName,
    })
  : undefined;

// The web stack sits behind the Cognito sign-in at the edge (lib/web-edge-auth.ts), which
// gates every behaviour; without it the distribution would be a public path to the API.
new WebStack(app, `${prefix}-Web`, {
  env: stackEnv,
  crossRegionReferences: true,
  envName: env,
  prefix,
  domain,
  certificate: webCert?.certificate,
  api: services.api,
  originSecret: services.originSecret,
  agentLoadBalancer: services.agentLoadBalancer,
  logBucket: data.logBucket,
});
// The sign-in function is a Lambda@Edge, so it lives in its own us-east-1 stack beside the web stack.
cdk.Validations.of(app.node.findChild(`${prefix}-WebEdge`)).acknowledge(
  { id: LAMBDA_BASIC_EXECUTION_ROLE, reason: LAMBDA_BASIC_EXECUTION_REASON },
  { id: 'AwsSolutions-L1', reason: LAMBDA_RUNTIME_REASON },
);

// Tags on every taggable resource so demos sharing the account can be told
// apart, cost-attributed and torn down by `Demo=<slug>`.
cdk.Tags.of(app).add('Demo', 'plm-semantic-layer-sample');
cdk.Tags.of(app).add('Project', 'plm-semantic-layer-sample');
cdk.Tags.of(app).add('Environment', env);
