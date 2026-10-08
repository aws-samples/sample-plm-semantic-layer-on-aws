// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import * as path from 'path';
import { CfnOutput, Duration, RemovalPolicy, Stack } from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as ssm from 'aws-cdk-lib/aws-ssm';
import { buildSync } from 'esbuild';

const HANDLER_DIR = path.join(__dirname, '..', 'lambda', 'edge-auth');

export interface CognitoEdgeAuthProps {
  /** Resource-name prefix (un-prefixed for prod, <name>-<env> otherwise). */
  readonly prefix: string;
}

/**
 * Cognito sign-in at the CloudFront edge (GUARDRAIL #1: the pool admits only users an
 * administrator creates). A viewer-request Lambda@Edge function on every behaviour
 * admits a request carrying a valid ID token, sends a browser without one to the
 * hosted UI (authorization code flow with PKCE, public client) and answers 401 to an
 * API or agent call without one; `infra/lambda/edge-auth/` is the function.
 *
 * The function cannot carry environment variables, and the pool client needs the
 * distribution's domain for its callback URL while the distribution needs the
 * function. So the function reads the pool id, client id and hosted-UI domain from one
 * SSM parameter whose name is fixed when the bundle is built, the IAM grant names that
 * parameter by ARN rather than by resource, and `bind` creates the client and writes
 * the parameter once the distribution exists. The construct graph has no cycle:
 * pool, domain, function -> distribution -> client -> parameter.
 */
export class CognitoEdgeAuth extends Construct {
  public readonly userPool: cognito.UserPool;
  /** The viewer-request association every behaviour of the distribution carries. */
  public readonly edgeLambdas: cloudfront.EdgeLambda[];
  private readonly domain: cognito.UserPoolDomain;
  private readonly parameterName: string;

  constructor(scope: Construct, id: string, props: CognitoEdgeAuthProps) {
    super(scope, id);
    const stack = Stack.of(this);
    this.parameterName = `/${props.prefix}/web/edge-auth`;

    this.userPool = new cognito.UserPool(this, 'UserPool', {
      userPoolName: `${props.prefix}-web`,
      selfSignUpEnabled: false,
      signInAliases: { email: true },
      // TOTP is an optional second factor so an administrator-created demo user can sign in
      // without enrolling; production should require it (Mfa.REQUIRED).
      mfa: cognito.Mfa.OPTIONAL,
      mfaSecondFactor: { sms: false, otp: true },
      passwordPolicy: { minLength: 12, requireLowercase: true, requireUppercase: true, requireDigits: true, requireSymbols: true },
      // Demo pool: tear down with the stack.
      removalPolicy: RemovalPolicy.DESTROY,
    });
    // The hosted-UI prefix is global across all AWS accounts; the account id keeps it unique.
    this.domain = this.userPool.addDomain('Domain', {
      cognitoDomain: { domainPrefix: `${props.prefix.toLowerCase()}-${stack.account}` },
    });

    // Lambda@Edge: us-east-1 (its own stack, deployed with the web stack), x86_64,
    // 128 MB and 5 s at most on viewer-request, 1 MB zipped. The bundle is built here
    // with esbuild, no Docker; the AWS SDK ships with the runtime.
    const fn = new cloudfront.experimental.EdgeFunction(this, 'Fn', {
      stackId: `${props.prefix}-WebEdge`,
      runtime: lambda.Runtime.NODEJS_22_X,
      handler: 'index.handler',
      memorySize: 128,
      timeout: Duration.seconds(5),
      code: lambda.Code.fromAsset(HANDLER_DIR, {
        bundling: {
          image: lambda.Runtime.NODEJS_22_X.bundlingImage,
          local: {
            tryBundle: (outputDir: string) => {
              buildSync({
                entryPoints: [path.join(HANDLER_DIR, 'index.ts')],
                outfile: path.join(outputDir, 'index.js'), // nosemgrep: path-join-resolve-traversal -- outputDir is the staging directory CDK hands to the bundling hook
                bundle: true,
                minify: true,
                platform: 'node',
                target: 'node22',
                format: 'cjs',
                external: ['@aws-sdk/*'],
                define: {
                  'process.env.EDGE_AUTH_PARAMETER': JSON.stringify(this.parameterName),
                  'process.env.EDGE_AUTH_REGION': JSON.stringify(stack.region),
                },
                logLevel: 'warning',
              });
              return true;
            },
          },
        },
      }),
    });
    fn.addToRolePolicy(
      new iam.PolicyStatement({
        actions: ['ssm:GetParameter'],
        resources: [`arn:aws:ssm:${stack.region}:${stack.account}:parameter${this.parameterName}`],
      }),
    );
    this.edgeLambdas = [{ eventType: cloudfront.LambdaEdgeEventType.VIEWER_REQUEST, functionVersion: fn.currentVersion }];
  }

  /** Creates the app client for the site's URLs and publishes the function's configuration. */
  public bind(siteUrls: string[]): void {
    const client = this.userPool.addClient('Client', {
      generateSecret: false,
      preventUserExistenceErrors: true,
      oAuth: {
        flows: { authorizationCodeGrant: true },
        scopes: [cognito.OAuthScope.OPENID, cognito.OAuthScope.EMAIL],
        callbackUrls: siteUrls.map((u) => `${u}/callback`),
        logoutUrls: siteUrls.map((u) => `${u}/`),
      },
      // The edge function holds no refresh token: the ID token's lifetime is the session.
      idTokenValidity: Duration.hours(24),
      accessTokenValidity: Duration.hours(24),
    });
    const stack = Stack.of(this);
    new ssm.StringParameter(this, 'Config', {
      parameterName: this.parameterName,
      stringValue: stack.toJsonString({
        userPoolId: this.userPool.userPoolId,
        clientId: client.userPoolClientId,
        domain: this.domain.baseUrl(),
      }),
    });
    new CfnOutput(stack, 'UserPoolId', {
      value: this.userPool.userPoolId,
      exportName: `${stack.stackName}-UserPoolId`,
    });
  }
}
