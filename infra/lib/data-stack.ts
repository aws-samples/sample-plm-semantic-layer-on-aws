// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import * as path from 'path';
import { AssetHashType, CfnElement, CustomResource, DefaultStackSynthesizer, Duration, FileSystem, RemovalPolicy, Stack, StackProps, Validations } from 'aws-cdk-lib';
import { Construct, IConstruct } from 'constructs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as s3deploy from 'aws-cdk-lib/aws-s3-deployment';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as nodejs from 'aws-cdk-lib/aws-lambda-nodejs';
import * as cr from 'aws-cdk-lib/custom-resources';
import { gzipTree } from './cad-assets';

/** The four site PLMs. Each owns one database and one login role. */
export const PLMS = ['fr', 'de', 'uk', 'es'] as const;
export type Plm = (typeof PLMS)[number];

/**
 * The Atelier core: the Atelier-owned relational store (part tags) on the same cluster.
 * It is described like a PLM (Spring module, virtual graph) but is not one.
 */
export const CORE = 'core';
export const CORE_DB = 'atelier_core';
/** Read-only login on atelier_core, used by the PLM services to read part tags. */
export const CORE_READER = 'core_reader';

// The STEP files the file index names by key (cad/<product key>/<file>.stp), one folder per product.
const CAD_DIR = path.resolve(__dirname, '..', '..', 'modules', 'cad', 'stp');

/** Reasons of the cdk-nag acknowledgements shared by the stacks (see bin/app.ts). */
export const LAMBDA_BASIC_EXECUTION_ROLE =
  'AwsSolutions-IAM4[Policy::arn:<AWS::Partition>:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole]';
export const LAMBDA_BASIC_EXECUTION_REASON =
  'AWSLambdaBasicExecutionRole grants the function its CloudWatch Logs writes and nothing else.';
export const LAMBDA_RUNTIME_REASON =
  'Node.js 22 is a supported LTS runtime of Lambda and Lambda@Edge; every function of the sample runs it, '
  + 'and the bucket-deployment function runs the runtime of its CDK construct.';
export const SECRET_ROTATION_REASON =
  'Demo secret created and destroyed with the stack; the services read it once at task start, so a rotation '
  + 'would need the rolling restart the sample does not include.';

/** cdk-nag names a wildcard finding by its resolved resource; these compose the names of the CDK-generated ones. */
const logicalId = (resource: IConstruct) => Stack.of(resource).getLogicalId(resource.node.defaultChild as CfnElement);
const assetsBucketObjects = (stack: Stack) =>
  `arn:aws:s3:::cdk-${DefaultStackSynthesizer.DEFAULT_QUALIFIER}-assets-${stack.account}-${stack.region}/*`;

/**
 * The BucketDeployment construct's singleton function and role, created under the stack: the role copies
 * the asset from the CDK assets bucket, lists, writes and deletes in the destination bucket and, for the
 * web bucket, invalidates the distribution.
 */
export function acknowledgeBucketDeployment(stack: Stack, destination: s3.Bucket, invalidates: boolean): void {
  const reason = 'The CDK BucketDeployment role: it reads the asset from the CDK assets bucket and lists, writes '
    + 'and deletes the objects of the destination bucket.';
  for (const child of stack.node.children) {
    if (!child.node.id.startsWith('Custom::CDKBucketDeployment')) continue;
    Validations.of(child).acknowledge(
      { id: LAMBDA_BASIC_EXECUTION_ROLE, reason: LAMBDA_BASIC_EXECUTION_REASON },
      { id: 'AwsSolutions-L1', reason: LAMBDA_RUNTIME_REASON },
      ...['s3:GetBucket*', 's3:GetObject*', 's3:List*', 's3:DeleteObject*', 's3:Abort*']
        .map((action) => ({ id: `AwsSolutions-IAM5[Action::${action}]`, reason })),
      { id: `AwsSolutions-IAM5[Resource::${assetsBucketObjects(stack)}]`, reason },
      { id: `AwsSolutions-IAM5[Resource::<${logicalId(destination)}.Arn>/*]`, reason },
      ...(invalidates
        ? [{ id: 'AwsSolutions-IAM5[Resource::*]', reason: 'cloudfront:CreateInvalidation takes no resource.' }]
        : []),
    );
  }
}

/** The custom-resource provider framework invokes the handler function and its versions. */
export function acknowledgeProviderFramework(provider: IConstruct, handler: IConstruct): void {
  Validations.of(provider).acknowledge(
    { id: LAMBDA_BASIC_EXECUTION_ROLE, reason: LAMBDA_BASIC_EXECUTION_REASON },
    {
      id: `AwsSolutions-IAM5[Resource::<${logicalId(handler)}.Arn>:*]`,
      reason: 'The provider framework invokes the handler function and its versions.',
    },
  );
}

export interface DataStackProps extends StackProps {
  readonly prefix: string;
  readonly vpc: ec2.IVpc;
  /**
   * Browser origin allowed to fetch presigned CAD URLs (the site's own
   * https://<fqdn>). Omit when the site has no custom domain.
   */
  readonly siteOrigin?: string;
}

/**
 * One Aurora PostgreSQL Serverless v2 cluster holding five separate databases:
 * one per site PLM (fr_plm, de_plm, uk_plm, es_plm), each owned by its own
 * <plm>_app role so every PLM service can only see its own site schema,
 * and the Atelier core database (atelier_core) owned by core_app with a read-only
 * core_reader login for the PLM services. Plus the private CAD bucket whose
 * objects the query service presigns for visible parts.
 *
 * GUARDRAIL #5: the cluster sits in private subnets, is not publicly
 * accessible, and admits only the security group granted by the services.
 * Databases and roles are created through the RDS Data API (IAM-authenticated),
 * so no bootstrap code needs network access to the cluster.
 *
 * GUARDRAIL #2: the CAD bucket blocks all public access; nothing serves it but
 * 15-minute presigned URLs, so an export-controlled file has no path the
 * policy does not decide.
 */
export class DataStack extends Stack {
  public readonly cluster: rds.DatabaseCluster;
  public readonly dbSecurityGroup: ec2.SecurityGroup;
  public readonly plmSecrets: Record<Plm, rds.DatabaseSecret>;
  /** core_app: owner of atelier_core, used by the core service and its Ontop endpoint. */
  public readonly coreSecret: rds.DatabaseSecret;
  /** core_reader: read-only on atelier_core, used by the PLM services' /tables policy. */
  public readonly coreReaderSecret: rds.DatabaseSecret;
  public readonly cadBucket: s3.Bucket;
  /** Destination of the S3 server access logs, the agent ALB access logs and the CloudFront standard logs. */
  public readonly logBucket: s3.Bucket;

  constructor(scope: Construct, id: string, props: DataStackProps) {
    super(scope, id, props);

    // CloudFront standard logging writes its objects with ACLs, so the bucket keeps ACLs enabled
    // (BUCKET_OWNER_PREFERRED); S3 and the ALB deliver their logs under the bucket policy.
    this.logBucket = new s3.Bucket(this, 'LogBucket', {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.S3_MANAGED,
      enforceSSL: true,
      objectOwnership: s3.ObjectOwnership.BUCKET_OWNER_PREFERRED,
      lifecycleRules: [{ expiration: Duration.days(30) }],
      removalPolicy: RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
    });
    Validations.of(this.logBucket).acknowledge({
      id: 'AwsSolutions-S1',
      reason: 'Destination of the other buckets\' server access logs; logging it to itself would loop.',
    });

    this.cadBucket = new s3.Bucket(this, 'CadBucket', {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      publicReadAccess: false,
      encryption: s3.BucketEncryption.S3_MANAGED,
      enforceSSL: true,
      serverAccessLogsBucket: this.logBucket,
      serverAccessLogsPrefix: 's3/cad/',
      removalPolicy: RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
      // The browser fetches presigned URLs from the site origin, a different
      // origin than the bucket's own hostname. The presigned URL carries the
      // authorization; CORS only lets a browser at the site read the response.
      // Without a custom domain the distribution's hostname is not known when
      // the bucket is created, so any CloudFront-hosted origin is allowed.
      cors: [{
        allowedMethods: [s3.HttpMethods.GET, s3.HttpMethods.HEAD],
        allowedOrigins: [props.siteOrigin ?? 'https://*.cloudfront.net'],
        allowedHeaders: ['*'],
        maxAge: 3600,
      }],
    });
    // The files are stored gzip-encoded: a product's download moves a fifth of the bytes. The asset is named after
    // the STEP files themselves, so the same files deploy as the same asset whatever compresses them; the stage
    // they are compressed into is created for this synth.
    const cadHash = FileSystem.fingerprint(CAD_DIR);
    const cadSource = s3deploy.Source.asset(gzipTree(CAD_DIR), { assetHash: cadHash, assetHashType: AssetHashType.CUSTOM });
    // A deployment sets one content type: the STEP files are model/step, each product's bounds.json application/json.
    // Each deployment excludes the other's files, and prune leaves excluded files in place.
    new s3deploy.BucketDeployment(this, 'DeployCad', {
      sources: [cadSource],
      destinationBucket: this.cadBucket,
      destinationKeyPrefix: 'cad/',
      exclude: ['*.json'],
      contentType: 'model/step',
      contentEncoding: 'gzip',
      prune: true,
    });
    new s3deploy.BucketDeployment(this, 'DeployCadBounds', {
      sources: [cadSource],
      destinationBucket: this.cadBucket,
      destinationKeyPrefix: 'cad/',
      exclude: ['*'],
      include: ['*.json'],
      contentType: 'application/json',
      contentEncoding: 'gzip',
      prune: true,
    });
    acknowledgeBucketDeployment(this, this.cadBucket, false);

    this.dbSecurityGroup = new ec2.SecurityGroup(this, 'DbSg', {
      vpc: props.vpc,
      description: 'Aurora PLM cluster - ingress only from PLM services and Ontop',
      allowAllOutbound: false,
    });

    this.cluster = new rds.DatabaseCluster(this, 'PlmCluster', {
      engine: rds.DatabaseClusterEngine.auroraPostgres({
        version: rds.AuroraPostgresEngineVersion.VER_16_13,
      }),
      writer: rds.ClusterInstance.serverlessV2('writer', { publiclyAccessible: false }),
      serverlessV2MinCapacity: 0.5,
      serverlessV2MaxCapacity: 4,
      vpc: props.vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      securityGroups: [this.dbSecurityGroup],
      credentials: rds.Credentials.fromGeneratedSecret('plmadmin'),
      defaultDatabaseName: 'postgres',
      enableDataApi: true,
      storageEncrypted: true,
      removalPolicy: RemovalPolicy.DESTROY,
    });
    Validations.of(this.cluster).acknowledge(
      {
        id: 'AwsSolutions-RDS6',
        reason: 'The six services and the five Ontop endpoints log in with per-database roles whose passwords '
          + 'come from Secrets Manager at task start (DB_SECRET_JSON); IAM database authentication is not used.',
      },
      {
        id: 'AwsSolutions-RDS10',
        reason: 'Demo cluster created and destroyed on demand (RemovalPolicy.DESTROY, scripts/teardown.sh); '
          + 'deletion protection would block its own teardown.',
      },
      { id: 'AwsSolutions-SMG4', reason: SECRET_ROTATION_REASON },
    );

    // A login secret attached to the cluster (host, port added at deploy time).
    const loginSecret = (id: string, username: string) => {
      const secret = new rds.DatabaseSecret(this, id, {
        username,
        excludeCharacters: ' %+~`#$&*()|[]{}:;<>?!\'/@"\\,=^',
      });
      Validations.of(secret).acknowledge({ id: 'AwsSolutions-SMG4', reason: SECRET_ROTATION_REASON });
      return secret.attach(this.cluster) as rds.DatabaseSecret;
    };

    this.plmSecrets = {} as Record<Plm, rds.DatabaseSecret>;
    for (const plm of PLMS) this.plmSecrets[plm] = loginSecret(`Secret-${plm}`, `${plm}_app`);
    this.coreSecret = loginSecret(`Secret-${CORE}`, `${CORE}_app`);
    this.coreReaderSecret = loginSecret(`Secret-${CORE}-reader`, CORE_READER);

    // Every database with its owner role's secret; atelier_core also names the
    // read-only role the bootstrap grants SELECT on the owner's tables.
    const databases = {
      ...Object.fromEntries(PLMS.map((p) => [`${p}_plm`, { role: `${p}_app`, secretArn: this.plmSecrets[p].secretArn }])),
      [CORE_DB]: {
        role: `${CORE}_app`,
        secretArn: this.coreSecret.secretArn,
        reader: { role: CORE_READER, secretArn: this.coreReaderSecret.secretArn },
      },
    };
    const bootstrapFn = new nodejs.NodejsFunction(this, 'DbBootstrapFn', {
      entry: path.join(__dirname, '..', 'lambda', 'db-bootstrap', 'index.ts'),
      runtime: lambda.Runtime.NODEJS_22_X,
      architecture: lambda.Architecture.ARM_64,
      timeout: Duration.minutes(5),
      bundling: { externalModules: ['@aws-sdk/*'] },
      environment: {
        CLUSTER_ARN: this.cluster.clusterArn,
        ADMIN_SECRET_ARN: this.cluster.secret!.secretArn,
        DATABASES: JSON.stringify(databases),
      },
    });
    this.cluster.grantDataApiAccess(bootstrapFn);
    for (const secret of [...Object.values(this.plmSecrets), this.coreSecret, this.coreReaderSecret]) {
      secret.grantRead(bootstrapFn);
    }
    Validations.of(bootstrapFn).acknowledge(
      { id: LAMBDA_BASIC_EXECUTION_ROLE, reason: LAMBDA_BASIC_EXECUTION_REASON },
      { id: 'AwsSolutions-L1', reason: LAMBDA_RUNTIME_REASON },
    );

    const provider = new cr.Provider(this, 'DbBootstrapProvider', {
      onEventHandler: bootstrapFn,
    });
    acknowledgeProviderFramework(provider, bootstrapFn);
    const bootstrap = new CustomResource(this, 'DbBootstrap', {
      serviceToken: provider.serviceToken,
      // Re-runs when the database list or the bootstrap logic (Version) changes.
      properties: { Databases: Object.keys(databases).join(','), Version: '2' },
    });
    bootstrap.node.addDependency(this.cluster);
  }
}
