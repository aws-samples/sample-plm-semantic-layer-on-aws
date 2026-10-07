// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// =============================================================================
// SECURITY GUARDRAILS ENFORCED IN THIS STACK
//
//   GUARDRAIL #2 — NO public S3. The web bucket is fully private:
//       BlockPublicAccess.BLOCK_ALL, publicReadAccess:false, enforceSSL:true.
//       Content is served ONLY through CloudFront using Origin Access Control
//       (S3BucketOrigin.withOriginAccessControl). No public bucket policy is
//       ever granted.
//
//   GUARDRAIL #4 — NO public load balancer, NO listener on :80. The only
//       public ingress is CloudFront, HTTPS only (REDIRECT_TO_HTTPS on the
//       site, HTTPS_ONLY on /api/* and /agent/*). The agent's ALB is internal,
//       listens on 8080 (never 80) and is reached only through a CloudFront
//       VPC origin.
//
// Do not weaken these: no public bucket policy, no Function URL on an origin,
// no public ALB, no plaintext HTTP. Serve everything via CloudFront + OAC.
// =============================================================================
import * as path from 'path';
import * as fs from 'fs';
import {
  Stack,
  StackProps,
  CfnOutput,
  Duration,
  RemovalPolicy,
  Validations,
} from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as s3deploy from 'aws-cdk-lib/aws-s3-deployment';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import * as acm from 'aws-cdk-lib/aws-certificatemanager';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as route53 from 'aws-cdk-lib/aws-route53';
import * as targets from 'aws-cdk-lib/aws-route53-targets';
import * as apigwv2 from 'aws-cdk-lib/aws-apigatewayv2';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { AGENT_PORT } from './agent';
import { acknowledgeBucketDeployment } from './data-stack';
import { CognitoEdgeAuth } from './web-edge-auth';

/** Custom-domain wiring. Omit for a *.cloudfront.net-only deployment. */
export interface WebDomainConfig {
  /** Fully-qualified site hostname, e.g. atelier.example.com. */
  readonly fqdn: string;
  /** Route 53 hosted zone id that owns the FQDN. */
  readonly hostedZoneId: string;
  /** Hosted zone name, e.g. example.com. */
  readonly zoneName: string;
}

// Path to the built SPA. Ship a placeholder when it does not exist yet so the
// walking skeleton (cdk synth + first deploy) succeeds before web app code.
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const WEB_DIST = path.join(REPO_ROOT, 'modules', 'web', 'dist');

export interface WebStackProps extends StackProps {
  /** Deployment environment (dev | prod), validated upstream as ^[a-z0-9-]+$, <=16 chars. */
  readonly envName: string;
  /** Resource-name prefix (un-prefixed for prod, <name>-<env> otherwise). */
  readonly prefix: string;
  /** Optional custom HTTPS domain. When omitted, CloudFront serves *.cloudfront.net. */
  readonly domain?: WebDomainConfig;
  /**
   * ACM certificate for the custom domain. Created in a companion us-east-1
   * WebCertStack (CloudFront requires us-east-1) and passed in cross-region via
   * crossRegionReferences. Required when `domain` is set; ignored otherwise.
   */
  readonly certificate?: acm.ICertificate;
  /** The single API endpoint, served on the same hostname under /api/*. */
  readonly api: apigwv2.HttpApi;
  /** Header value CloudFront adds so the API accepts only CloudFront traffic. */
  readonly originSecret: secretsmanager.ISecret;
  /** The agent's internal ALB, served under /agent/* through a VPC origin. Absent without the semantic tier. */
  readonly agentLoadBalancer?: elbv2.IApplicationLoadBalancer;
  /** Destination of the web bucket's server access logs (s3/web/) and the distribution's standard logs (cloudfront/). */
  readonly logBucket: s3.Bucket;
}

interface WebCertStackProps extends StackProps {
  readonly fqdn: string;
  readonly hostedZoneId: string;
  readonly zoneName: string;
}

/**
 * WebCertStack — the ACM certificate for CloudFront, pinned to us-east-1.
 *
 * CloudFront only accepts certificates from us-east-1, regardless of where the
 * workload runs. Rather than the deprecated DnsValidatedCertificate, we use a
 * dedicated us-east-1 stack holding a real acm.Certificate (DNS-validated
 * against the child hosted zone) and surface it across regions via
 * crossRegionReferences:true on both this stack and WebStack.
 *
 * IMPORTANT: this must be a SIBLING of WebStack scoped to the App (see
 * bin/app.ts) — a Stack cannot be nested under another Stack that resolves to a
 * different region. bin/app.ts creates it with `app` as scope and passes
 * `webCert.certificate` into WebStack via the `certificate` prop.
 */
export class WebCertStack extends Stack {
  public readonly certificate: acm.ICertificate;

  constructor(scope: Construct, id: string, props: WebCertStackProps) {
    super(scope, id, props);

    const zone = route53.HostedZone.fromHostedZoneAttributes(this, 'CertZone', {
      hostedZoneId: props.hostedZoneId,
      zoneName: props.zoneName,
    });

    this.certificate = new acm.Certificate(this, 'WebCertificate', {
      domainName: props.fqdn,
      // DNS validation writes its records into the hosted zone, so it completes
      // without a manual step.
      validation: acm.CertificateValidation.fromDns(zone),
    });
  }
}

/**
 * WebStack — private S3 origin + CloudFront (OAC) for Atelier.
 *
 * PLM semantic layer sample: four site PLMs, ontology rules that catch misaligned interface features between parts
 *
 * Guardrails #2 (no public S3) and #4 (HTTPS-only, no public ALB / :80) are
 * enforced here. The SPA is served from a fully private bucket through a
 * CloudFront distribution using Origin Access Control, with an optional
 * publicly-trusted custom domain. Every behaviour runs the Cognito sign-in
 * (web-edge-auth.ts) on viewer-request, so nothing is reachable without a session.
 */
export class WebStack extends Stack {
  public readonly bucket: s3.Bucket;
  public readonly distribution: cloudfront.Distribution;

  constructor(scope: Construct, id: string, props: WebStackProps) {
    super(scope, id, props);

    // ---- Custom domain (optional) ------------------------------------------
    // The us-east-1 certificate is created in a companion WebCertStack (a
    // sibling under the App — see bin/app.ts) and passed in via props; here we
    // only import the zone for the alias records.
    let zone: route53.IHostedZone | undefined;
    const certificate = props.certificate;
    if (props.domain) {
      zone = route53.HostedZone.fromHostedZoneAttributes(this, 'WebZone', {
        hostedZoneId: props.domain.hostedZoneId,
        zoneName: props.domain.zoneName,
      });
    }

    // ---- Private bucket (GUARDRAIL #2) -------------------------------------
    this.bucket = new s3.Bucket(this, 'WebBucket', {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      publicReadAccess: false,
      encryption: s3.BucketEncryption.S3_MANAGED,
      enforceSSL: true,
      serverAccessLogsBucket: props.logBucket,
      serverAccessLogsPrefix: 's3/web/',
      // Demo bucket: tear down cleanly with the stack.
      removalPolicy: RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
      versioned: false,
    });

    // ---- CloudFront with Origin Access Control (GUARDRAIL #2 + #4) ---------
    // S3BucketOrigin.withOriginAccessControl provisions an OAC and grants
    // cloudfront.amazonaws.com read access scoped to this distribution — the
    // bucket stays private. viewerProtocolPolicy REDIRECT_TO_HTTPS makes the
    // site HTTPS-only (no :80, no public ALB anywhere).
    // LIST lets a missing key return 404 instead of 403.
    const s3Origin = origins.S3BucketOrigin.withOriginAccessControl(this.bucket, {
      originAccessLevels: [cloudfront.AccessLevel.READ, cloudfront.AccessLevel.LIST],
    });
    const apiOrigin = new origins.HttpOrigin(
      `${props.api.apiId}.execute-api.${this.region}.amazonaws.com`,
      {
        protocolPolicy: cloudfront.OriginProtocolPolicy.HTTPS_ONLY,
        customHeaders: { 'x-origin-verify': props.originSecret.secretValue.unsafeUnwrap() },
      },
    );
    // Plain HTTP to the internal ALB on 8080: CloudFront terminates TLS and the
    // VPC origin stays inside the VPC. The agent streams AG-UI events over SSE;
    // 60 s between events is the longest wait CloudFront allows without a
    // quota increase.
    const agentOrigin = props.agentLoadBalancer
      ? origins.VpcOrigin.withApplicationLoadBalancer(props.agentLoadBalancer, {
          protocolPolicy: cloudfront.OriginProtocolPolicy.HTTP_ONLY,
          httpPort: AGENT_PORT,
          readTimeout: Duration.seconds(60),
          // The agent accepts only requests carrying this distribution's origin secret; the
          // CloudFront VPC-origin security group is shared by every distribution in the VPC.
          customHeaders: { 'x-origin-verify': props.originSecret.secretValue.unsafeUnwrap() },
        })
      : undefined;

    // Cognito sign-in is a viewer-request function on every behaviour. The pool and the
    // function exist before the distribution, which associates the function; the app
    // client, which needs the distribution's domain, is created after it (bind).
    const cognitoAuth = new CognitoEdgeAuth(this, 'EdgeAuth', { prefix: props.prefix });
    const edgeLambdas = cognitoAuth.edgeLambdas;
    Validations.of(cognitoAuth.userPool).acknowledge(
      {
        id: 'AwsSolutions-COG2',
        reason: 'Demo pool without self sign-up: an administrator creates each user (scripts/bootstrap-user.sh); '
          + 'TOTP is offered as an optional second factor so a demo user can sign in without enrolling, '
          + 'and production should require it (Mfa.REQUIRED).',
      },
      {
        id: 'AwsSolutions-COG8',
        reason: 'The Plus feature plan (threat protection) is billed per monthly active user; the demo pool holds '
          + 'the few users an administrator creates.',
      },
    );

    this.distribution = new cloudfront.Distribution(this, 'WebDistribution', {
      comment: `${props.prefix} web distribution`,
      defaultRootObject: 'index.html',
      domainNames: props.domain ? [props.domain.fqdn] : undefined,
      certificate,
      // Applies with a custom domain; the default *.cloudfront.net certificate fixes the minimum at TLSv1.
      minimumProtocolVersion: cloudfront.SecurityPolicyProtocol.TLS_V1_2_2021,
      defaultBehavior: {
        origin: s3Origin,
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        allowedMethods: cloudfront.AllowedMethods.ALLOW_GET_HEAD_OPTIONS,
        cachedMethods: cloudfront.CachedMethods.CACHE_GET_HEAD_OPTIONS,
        compress: true,
        cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
        edgeLambdas,
      },
      // No /cad/* behaviour: CAD files reach the browser only as presigned S3
      // URLs issued by the query service for parts the viewer's profile may see
      // (scripts/check-web-behaviours.sh asserts this on the synthesized template).
      additionalBehaviors: {
        '/api/*': {
          origin: apiOrigin,
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.HTTPS_ONLY,
          // The officer's demo controls and the MCP endpoint POST through this behaviour.
          allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
          cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
          // Forwards every viewer header (all but Host) to API Gateway, so the
          // x-atelier-profile header the browser sends reaches the services.
          originRequestPolicy: cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER,
          edgeLambdas,
        },
        // The agent serves /agent/invocations (POST, AG-UI over SSE) and
        // /agent/health; the path is forwarded unchanged, with x-atelier-profile.
        ...(agentOrigin
          ? {
              '/agent/*': {
                origin: agentOrigin,
                viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.HTTPS_ONLY,
                allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
                cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
                originRequestPolicy: cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER,
                edgeLambdas,
              },
            }
          : {}),
      },
      // No error-response rewrite: the SPA has no client routes, and API 404s
      // must reach the browser as 404s.
      priceClass: cloudfront.PriceClass.PRICE_CLASS_100,
      httpVersion: cloudfront.HttpVersion.HTTP2_AND_3,
      enabled: true,
      enableLogging: true,
      logBucket: props.logBucket,
      logFilePrefix: 'cloudfront/',
      logIncludesCookies: false,
    });
    Validations.of(this.distribution).acknowledge(
      {
        id: 'AwsSolutions-CFR1',
        reason: 'The viewers are the users an administrator creates in the pool, admitted by the sign-in on every '
          + 'behaviour, not a geography.',
      },
      {
        id: 'AwsSolutions-CFR2',
        reason: 'Every behaviour requires the Cognito session the viewer-request function checks, and the API and '
          + 'agent origins accept only requests carrying the origin secret; a web ACL is not part of the sample.',
      },
      ...(props.domain
        ? []
        : [{
            id: 'AwsSolutions-CFR4',
            reason: 'Without a custom domain CloudFront serves its default certificate, whose minimum protocol '
              + 'is fixed at TLSv1; with a domain the distribution requires TLSv1.2_2021.',
          }]),
    );
    cognitoAuth.bind([
      `https://${this.distribution.distributionDomainName}`,
      ...(props.domain ? [`https://${props.domain.fqdn}`] : []),
    ]);

    // ---- Route 53 alias records (optional) ---------------------------------
    if (props.domain && zone) {
      const aliasTarget = route53.RecordTarget.fromAlias(
        new targets.CloudFrontTarget(this.distribution),
      );
      new route53.ARecord(this, 'WebAliasA', {
        zone,
        recordName: props.domain.fqdn,
        target: aliasTarget,
      });
      new route53.AaaaRecord(this, 'WebAliasAAAA', {
        zone,
        recordName: props.domain.fqdn,
        target: aliasTarget,
      });
    }

    // ---- Bucket deployment --------------------------------------------------
    // Deploy modules/web/dist if it exists; otherwise ship a tiny placeholder
    // so the walking skeleton deploys before the SPA bundle is built. Replace
    // the placeholder by running `npm run build -w modules/web` and redeploying.
    const sources: s3deploy.ISource[] = fs.existsSync(WEB_DIST)
      ? [s3deploy.Source.asset(WEB_DIST)]
      : [
          s3deploy.Source.data(
            'index.html',
            `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <title>Atelier (placeholder)</title>
</head>
<body>
  <h1>Atelier</h1>
  <p>PLM semantic layer sample: four site PLMs, ontology rules that catch misaligned interface features between parts</p>
  <p>Web bundle not yet deployed. Run <code>npm run build -w modules/web</code> and redeploy.</p>
</body>
</html>
`,
          ),
        ];

    new s3deploy.BucketDeployment(this, 'DeployWeb', {
      sources,
      destinationBucket: this.bucket,
      distribution: this.distribution,
      distributionPaths: ['/*'],
      // No prune: the config.json written by deploy.sh after this step must survive deploys.
      prune: false,
      retainOnDelete: false,
    });
    acknowledgeBucketDeployment(this, this.bucket, true);

    // ---- Outputs ------------------------------------------------------------
    new CfnOutput(this, 'WebBucketName', {
      value: this.bucket.bucketName,
      exportName: `${this.stackName}-WebBucketName`,
    });
    new CfnOutput(this, 'DistributionId', {
      value: this.distribution.distributionId,
      exportName: `${this.stackName}-DistributionId`,
    });
    new CfnOutput(this, 'SiteUrl', {
      value: props.domain
        ? `https://${props.domain.fqdn}`
        : `https://${this.distribution.distributionDomainName}`,
      exportName: `${this.stackName}-SiteUrl`,
    });
  }
}
