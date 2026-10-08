// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import * as fs from 'fs';
import * as path from 'path';
import * as crypto from 'crypto';
import { CustomResource, Duration } from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as events from 'aws-cdk-lib/aws-events';
import * as targets from 'aws-cdk-lib/aws-events-targets';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as nodejs from 'aws-cdk-lib/aws-lambda-nodejs';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as cr from 'aws-cdk-lib/custom-resources';
import { NEPTUNE_WRITE_ACTIONS } from './graph-stack';

const DATA_DIR = path.join(__dirname, '..', '..', 'data');

/** A graph file is a bare file name under data/; anything else is a configuration error. */
const sanitizeGraphFile = (name: string): string => {
  if (!/^[\w.-]+$/.test(name)) throw new Error(`Graph file is not a bare file name: ${name}`);
  return name;
};
/** The Turtle files under data/ and the Neptune named graph each one replaces. */
export const GRAPHS: Record<string, string> = {
  'links.ttl': 'https://example.com/atelier/graph/links',
  'fileindex.ttl': 'https://example.com/atelier/graph/fileindex',
  'labels.ttl': 'https://example.com/atelier/graph/labels',
  'options.ttl': 'https://example.com/atelier/graph/options',
};

export interface LinksLoaderProps {
  readonly prefix: string;
  readonly vpc: ec2.IVpc;
  /** Neptune SPARQL endpoint (https://host:port/sparql). */
  readonly neptuneSparqlUrl: string;
  /** The Neptune cluster's security group; the loader's ingress rule is added to it here. */
  readonly neptuneSecurityGroup: ec2.ISecurityGroup;
  /** The cluster's data-access ARN (resource id followed by /*); the loader's neptune-db actions are granted on it here. */
  readonly neptuneDataArn: string;
  /** API Gateway endpoint; the loader records corrections at <endpoint>/api/core/changes. */
  readonly apiEndpoint: string;
  /** The header value CloudFront sends to the API origin; the loader presents it too. */
  readonly originSecret: secretsmanager.ISecret;
}

/**
 * The one Lambda that writes the Neptune named graphs and feeds Atelier's change
 * log from the PLMs' business events:
 *
 *   - at deploy, a custom resource replaces each named graph with the released
 *     Turtle file bundled next to the handler (reloaded when a file changes);
 *   - at runtime, one EventBridge rule on the default bus delivers the PLMs'
 *     `atelier.plm` events: `part.cad.published` sets the part's `atelier:cadFile` in
 *     the file index; `part.value.corrected` is appended to `atelier_core.demo_change`
 *     through `POST /core/changes` on the API, with the origin secret and the
 *     officer profile (a correction whose purpose starts with `reset:` is the
 *     reset undoing an earlier one and is not logged).
 *
 * Retries are bounded so a failed delivery cannot land a change long after the
 * officer has reset the demo data. Links are written synchronously by the core
 * service, which then publishes `atelier.graph` / `interface.link.added` for
 * subscribers; no rule consumes it here.
 *
 * Every Neptune request is signed with SigV4 under the function role
 * (NEPTUNE_IAM_AUTH=true), which holds the neptune-db read and write actions on
 * the cluster's data ARN.
 */
export class LinksLoader extends Construct {
  constructor(scope: Construct, id: string, props: LinksLoaderProps) {
    super(scope, id);

    const sg = new ec2.SecurityGroup(this, 'Sg', { vpc: props.vpc, description: 'Links loader' });
    props.neptuneSecurityGroup.addIngressRule(sg, ec2.Port.tcp(8182), 'Loader to Neptune');

    const files = Object.keys(GRAPHS);
    const sha256 = (file: string) =>
      crypto.createHash('sha256').update(fs.readFileSync(path.join(DATA_DIR, sanitizeGraphFile(file)))).digest('hex'); // nosemgrep: detect-non-literal-fs-filename -- file is a key of GRAPHS, a bare file name checked by sanitizeGraphFile, read at synth time
    // An event the loader still fails after the retries lands here instead of being dropped: EventBridge
    // writes a failed invocation, Lambda a failed asynchronous run. SSE-SQS, because EventBridge can only
    // write to a KMS-encrypted queue whose key policy grants it, and the AWS-managed key's policy cannot be edited.
    const dlq = new sqs.Queue(this, 'Dlq', {
      encryption: sqs.QueueEncryption.SQS_MANAGED,
      enforceSSL: true,
      retentionPeriod: Duration.days(14),
    });
    const fn = new nodejs.NodejsFunction(this, 'Fn', {
      entry: path.join(__dirname, '..', 'lambda', 'links-loader', 'index.ts'),
      runtime: lambda.Runtime.NODEJS_22_X,
      architecture: lambda.Architecture.ARM_64,
      timeout: Duration.minutes(3),
      deadLetterQueue: dlq,
      vpc: props.vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      securityGroups: [sg],
      bundling: {
        externalModules: ['@aws-sdk/*'],
        commandHooks: {
          beforeBundling: () => [],
          beforeInstall: () => [],
          afterBundling: (_in: string, out: string) => files.map((f) => `cp "${path.join(DATA_DIR, sanitizeGraphFile(f))}" "${out}/${f}"`),
        },
      },
      environment: {
        GSP_URL: `${props.neptuneSparqlUrl}/gsp/`,
        NEPTUNE_IAM_AUTH: 'true',
        GRAPHS: JSON.stringify(GRAPHS),
        API_BASE: `${props.apiEndpoint}/api`,
        ORIGIN_SECRET_ARN: props.originSecret.secretArn,
      },
    });
    props.originSecret.grantRead(fn);
    fn.addToRolePolicy(new iam.PolicyStatement({ actions: NEPTUNE_WRITE_ACTIONS, resources: [props.neptuneDataArn] }));

    const provider = new cr.Provider(this, 'Provider', { onEventHandler: fn });
    new CustomResource(this, 'Load', {
      serviceToken: provider.serviceToken,
      // Reloads whenever one of the generated files changes.
      properties: Object.fromEntries(files.map((f) => [`Sha256-${f}`, sha256(f)])),
    });

    new events.Rule(this, 'PlmEvents', {
      description: `${props.prefix} atelier.plm part.cad.published and part.value.corrected to the links loader`,
      eventPattern: { source: ['atelier.plm'], detailType: ['part.cad.published', 'part.value.corrected'] },
      targets: [new targets.LambdaFunction(fn, { retryAttempts: 2, maxEventAge: Duration.minutes(5), deadLetterQueue: dlq })],
    });
  }
}
