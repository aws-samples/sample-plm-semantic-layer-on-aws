// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import * as path from 'path';
import { ArnFormat, CfnOutput, Duration, RemovalPolicy, Stack, StackProps, Validations } from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as nodejs from 'aws-cdk-lib/aws-lambda-nodejs';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as servicediscovery from 'aws-cdk-lib/aws-servicediscovery';
import * as apigwv2 from 'aws-cdk-lib/aws-apigatewayv2';
import * as integrations from 'aws-cdk-lib/aws-apigatewayv2-integrations';
import * as authorizers from 'aws-cdk-lib/aws-apigatewayv2-authorizers';
import {
  acknowledgeProviderFramework,
  CORE,
  CORE_DB,
  LAMBDA_BASIC_EXECUTION_REASON,
  LAMBDA_BASIC_EXECUTION_ROLE,
  LAMBDA_RUNTIME_REASON,
  Plm,
  SECRET_ROTATION_REASON,
} from './data-stack';
import { ImageName, repoName } from './ecr-stack';
import { AgentService } from './agent';
import { NEPTUNE_READ_ACTIONS, NEPTUNE_WRITE_ACTIONS } from './graph-stack';
import { LinksLoader } from './links-loader';

export interface ServicesStackProps extends StackProps {
  readonly prefix: string;
  readonly vpc: ec2.IVpc;
  readonly cluster: rds.DatabaseCluster;
  readonly dbSecurityGroup: ec2.SecurityGroup;
  readonly plmSecrets: Record<Plm, rds.DatabaseSecret>;
  /** core_app: owner of atelier_core, for the core service and its Ontop endpoint. */
  readonly coreSecret: rds.DatabaseSecret;
  /** core_reader: read-only on atelier_core, given to every PLM service. */
  readonly coreReaderSecret: rds.DatabaseSecret;
  /** Private CAD bucket; the query service presigns cad/* objects of visible parts. */
  readonly cadBucket: s3.IBucket;
  /** Destination of the agent ALB access logs (alb/agent/). */
  readonly logBucket: s3.IBucket;
  /** PLM services to run; each needs its image pushed first. */
  readonly plms: readonly Plm[];
  /** Content-addressed tag of each image, by build key (infra/lib/image-tags.ts): an unchanged image keeps its task definition. */
  readonly imageTags: Readonly<Record<string, string>>;
  /**
   * The Neptune graphs: SPARQL endpoint, the cluster's security group (its ingress rules are created here) and the
   * cluster resource id (the neptune-db grants of the three clients are created here, on its data ARN).
   */
  readonly graph: {
    readonly sparqlUrl: string;
    readonly securityGroup: ec2.ISecurityGroup;
    readonly clusterResourceId: string;
  };
  /**
   * The semantic tier: one Ontop virtual graph per database (PLMs and core),
   * the query service federating them with the Neptune graphs, and the agent.
   * Off until its images exist.
   */
  readonly semantic: boolean;
  /** Bedrock model or inference profile the agent calls (env MODEL_ID); used with `semantic`. */
  readonly modelId: string;
  /** USD per 1k input / output tokens for the agent's cost estimate. */
  readonly pricePer1kInput: string;
  readonly pricePer1kOutput: string;
}

/** One Spring service and one Ontop endpoint run over each of these. */
interface Database {
  /** Route prefix, image suffix and Cloud Map name (fr, de, uk, es, core). */
  readonly code: Plm | typeof CORE;
  readonly db: string;
  readonly secret: rds.DatabaseSecret;
}

/**
 * The Atelier single endpoint: an API Gateway HTTP API reaching the PLM and core
 * services in private subnets through a VPC link and Cloud Map.
 *
 * GUARDRAIL #5: every route has an authorizer. Viewers sign in with Cognito
 * at CloudFront; CloudFront then adds a secret origin header that the Lambda
 * authorizer checks, so the execute-api hostname rejects direct calls.
 *
 * It also holds the links loader (see links-loader.ts): the Lambda that loads
 * the released Neptune graphs and consumes the PLMs' business events, calling
 * this API with the origin secret, so it lives downstream of the API and the
 * Graph stack stays Neptune only.
 *
 * With the semantic tier it also runs the in-app agent behind an internal ALB
 * that the web stack reaches through a CloudFront VPC origin (see agent.ts).
 */
export class ServicesStack extends Stack {
  public readonly api: apigwv2.HttpApi;
  public readonly originSecret: secretsmanager.Secret;
  /** The agent's internal ALB, the origin of the web stack's /agent/* behaviour. */
  public readonly agentLoadBalancer?: elbv2.IApplicationLoadBalancer;

  constructor(scope: Construct, id: string, props: ServicesStackProps) {
    super(scope, id, props);
    const { vpc } = props;

    const databases: Database[] = [
      ...props.plms.map((plm) => ({ code: plm, db: `${plm}_plm`, secret: props.plmSecrets[plm] })),
      { code: CORE, db: CORE_DB, secret: props.coreSecret },
    ];

    const cluster = new ecs.Cluster(this, 'Cluster', {
      vpc,
      clusterName: `${props.prefix}-cluster`,
      containerInsightsV2: ecs.ContainerInsights.ENABLED,
    });
    const namespace = new servicediscovery.PrivateDnsNamespace(this, 'Namespace', {
      name: `${props.prefix.toLowerCase()}.local`,
      vpc,
    });

    const linkSg = new ec2.SecurityGroup(this, 'VpcLinkSg', {
      vpc,
      description: 'API Gateway VPC link',
    });
    const serviceSg = new ec2.SecurityGroup(this, 'ServiceSg', {
      vpc,
      description: 'PLM services - ingress from the VPC link only',
    });
    serviceSg.addIngressRule(linkSg, ec2.Port.tcp(8080), 'VPC link to services');
    // The core service has its own group: it is the one service that writes the
    // links graph, so the Neptune ingress names it and no PLM.
    const coreSg = new ec2.SecurityGroup(this, 'CoreSg', {
      vpc,
      description: 'Atelier core service - ingress from the VPC link only',
    });
    coreSg.addIngressRule(linkSg, ec2.Port.tcp(8080), 'VPC link to core service');
    // Imported so the rule is created in this stack (no Data -> Services cycle).
    const dbSg = ec2.SecurityGroup.fromSecurityGroupId(this, 'DbSgRef', props.dbSecurityGroup.securityGroupId);
    dbSg.addIngressRule(serviceSg, ec2.Port.tcp(5432), 'Services to Aurora');
    dbSg.addIngressRule(coreSg, ec2.Port.tcp(5432), 'Core service to Aurora');
    // Imported for the same reason: the Neptune ingress rules (loader, core, query) are created here.
    const neptuneSg = ec2.SecurityGroup.fromSecurityGroupId(this, 'NeptuneSgRef', props.graph.securityGroup.securityGroupId);
    // The cluster's data-access ARN, the cluster resource id followed by /* (the one form the neptune-db actions
    // accept). Every client of the cluster signs with SigV4 under its own role (NEPTUNE_IAM_AUTH=true) and holds
    // its own actions on this ARN: reads for the query service, reads and writes for the core service and the loader.
    const neptuneDataArn = Stack.of(this).formatArn({
      service: 'neptune-db',
      resource: props.graph.clusterResourceId,
      resourceName: '*',
      arnFormat: ArnFormat.SLASH_RESOURCE_NAME,
    });
    // cdk-nag names the wildcard finding by the flattened ARN: the stack's partition, region and account (concrete,
    // bin/app.ts requires both) and the import of the graph stack's export of the resource id.
    const neptuneDataArnFinding = `AwsSolutions-IAM5[Resource::arn:${Stack.of(this).partition}:neptune-db:${Stack.of(this).region}:${
      Stack.of(this).account}:${Stack.of(this).resolve(props.graph.clusterResourceId)['Fn::ImportValue']}/*]`;
    const neptuneDataArnReason = 'The data-access ARN of a Neptune cluster is its resource id followed by /*; the actions are the '
      + 'fine-grained neptune-db ones this principal performs.';

    const vpcLink = new apigwv2.VpcLink(this, 'VpcLink', {
      vpc,
      subnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      securityGroups: [linkSg],
    });

    this.originSecret = new secretsmanager.Secret(this, 'OriginVerifySecret', {
      description: 'Header value CloudFront sends to the API origin',
      generateSecretString: { passwordLength: 48, excludePunctuation: true },
    });
    Validations.of(this.originSecret).acknowledge({ id: 'AwsSolutions-SMG4', reason: SECRET_ROTATION_REASON });
    const authorizerFn = new nodejs.NodejsFunction(this, 'OriginAuthorizerFn', {
      entry: path.join(__dirname, '..', 'lambda', 'origin-authorizer', 'index.ts'),
      runtime: lambda.Runtime.NODEJS_22_X,
      architecture: lambda.Architecture.ARM_64,
      timeout: Duration.seconds(5),
      bundling: { externalModules: ['@aws-sdk/*'] },
      environment: { SECRET_ARN: this.originSecret.secretArn },
    });
    this.originSecret.grantRead(authorizerFn);
    Validations.of(authorizerFn).acknowledge(
      { id: LAMBDA_BASIC_EXECUTION_ROLE, reason: LAMBDA_BASIC_EXECUTION_REASON },
      { id: 'AwsSolutions-L1', reason: LAMBDA_RUNTIME_REASON },
    );
    const authorizer = new authorizers.HttpLambdaAuthorizer('OriginAuthorizer', authorizerFn, {
      responseTypes: [authorizers.HttpLambdaResponseType.SIMPLE],
      identitySource: ['$request.header.x-origin-verify'],
      resultsCacheTtl: Duration.minutes(5),
    });

    this.api = new apigwv2.HttpApi(this, 'Api', {
      apiName: `${props.prefix}-api`,
      createDefaultStage: true,
    });
    // Access log of the default stage: one JSON line per request, kept one week like the task logs.
    const apiLogs = new logs.LogGroup(this, 'ApiAccessLogs', {
      retention: logs.RetentionDays.ONE_WEEK,
      removalPolicy: RemovalPolicy.DESTROY,
    });
    (this.api.defaultStage!.node.defaultChild as apigwv2.CfnStage).accessLogSettings = {
      destinationArn: apiLogs.logGroupArn,
      format: JSON.stringify({
        requestId: '$context.requestId',
        ip: '$context.identity.sourceIp',
        requestTime: '$context.requestTime',
        method: '$context.httpMethod',
        route: '$context.routeKey',
        status: '$context.status',
        responseLength: '$context.responseLength',
        integrationError: '$context.integrationErrorMessage',
      }),
    };

    const image = (name: ImageName) =>
      ecs.ContainerImage.fromEcrRepository(
        ecr.Repository.fromRepositoryName(this, `Repo-${name}`, repoName(props.prefix, name)),
        props.imageTags[name],
      );
    const defaultBusArn = Stack.of(this).formatArn({ service: 'events', resource: 'event-bus', resourceName: 'default' });
    // Every route below carries the origin authorizer and maps /api/<code>/x to /<code>/x on the service.
    // API Gateway's greedy path variable; the mapping forwards it as $request.path.proxy.
    const PROXY = '/{proxy+}';
    const route = (id: string, path: string, methods: apigwv2.HttpMethod[], service: ecs.FargateService, target: string) =>
      this.api.addRoutes({
        path,
        methods,
        authorizer,
        integration: new integrations.HttpServiceDiscoveryIntegration(id, service.cloudMapService!, {
          vpcLink,
          parameterMapping: new apigwv2.ParameterMapping().overwritePath(apigwv2.MappingValue.custom(target)),
        }),
      });
    // Every task definition: the credentials arrive through `secrets` (Secrets Manager); the environment
    // holds names, URLs and ARNs. The execution role's one wildcard is ecr:GetAuthorizationToken, which
    // takes no resource.
    const acknowledgeTask = (task: ecs.FargateTaskDefinition) =>
      Validations.of(task).acknowledge(
        {
          id: 'AwsSolutions-ECS2',
          reason: 'The environment carries database names, service URLs, the bus name and secret ARNs; '
            + 'the credentials themselves are injected from Secrets Manager through the container secrets.',
        },
        { id: 'AwsSolutions-IAM5[Resource::*]', reason: 'ecr:GetAuthorizationToken of the execution role takes no resource.' },
      );

    for (const { code, db, secret } of databases) {
      const task = new ecs.FargateTaskDefinition(this, `Task-${code}`, {
        cpu: 512,
        memoryLimitMiB: 1024,
        runtimePlatform: {
          cpuArchitecture: ecs.CpuArchitecture.ARM64,
          operatingSystemFamily: ecs.OperatingSystemFamily.LINUX,
        },
      });
      // A PLM enforces /tables from the part tags in atelier_core, read-only; the
      // core service owns that database and needs no second login.
      const isPlm = code !== CORE;
      // A PLM publishes part.cad.published (see graph-stack.ts), the core
      // service interface.link.added and equivalence.confirmed; all on the default bus only.
      task.addToTaskRolePolicy(new iam.PolicyStatement({ actions: ['events:PutEvents'], resources: [defaultBusArn] }));
      task.addContainer('app', {
        image: image(`plm-${code}`),
        portMappings: [{ containerPort: 8080 }],
        environment: {
          DB_NAME: db,
          ATELIER_EVENT_BUS: 'default',
          AWS_REGION: Stack.of(this).region,
          ...(isPlm
            ? { CORE_DB_NAME: CORE_DB }
            : {
              // The core service writes the links graph and restores the released
              // links.ttl, fileindex.ttl and labels.ttl its image bundles (Graph Store URL
              // derived from the SPARQL one), signing each request with SigV4.
              RELEASED_GRAPHS_DIR: '/app/data',
              ...(props.semantic ? { NEPTUNE_SPARQL_URL: props.graph.sparqlUrl, NEPTUNE_IAM_AUTH: 'true' } : {}),
              // The reset replays the logged corrections through the PLM services,
              // reached through this API with the origin secret.
              PLM_API_BASE: `${this.api.apiEndpoint}/api`,
              ORIGIN_SECRET_ARN: this.originSecret.secretArn,
            }),
        },
        secrets: {
          DB_SECRET_JSON: ecs.Secret.fromSecretsManager(secret),
          ...(isPlm ? { CORE_DB_SECRET_JSON: ecs.Secret.fromSecretsManager(props.coreReaderSecret) } : {}),
        },
        // Cloud Map only advertises the task once this passes, so a rolling
        // replacement never routes to a JVM that is still starting.
        healthCheck: {
          command: ['CMD-SHELL', `wget -q -O /dev/null http://localhost:8080/${code}/health || exit 1`],
          interval: Duration.seconds(10),
          startPeriod: Duration.seconds(60),
        },
        logging: ecs.LogDrivers.awsLogs({
          streamPrefix: `plm-${code}`,
          logRetention: logs.RetentionDays.ONE_WEEK,
        }),
      });
      if (!isPlm) this.originSecret.grantRead(task.taskRole);
      if (!isPlm && props.semantic) {
        task.addToTaskRolePolicy(new iam.PolicyStatement({ actions: NEPTUNE_WRITE_ACTIONS, resources: [neptuneDataArn] }));
        Validations.of(task.taskRole).acknowledge({ id: neptuneDataArnFinding, reason: neptuneDataArnReason });
      }
      acknowledgeTask(task);
      const service = new ecs.FargateService(this, `Service-${code}`, {
        cluster,
        taskDefinition: task,
        desiredCount: 1,
        assignPublicIp: false,
        vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
        securityGroups: [isPlm ? serviceSg : coreSg],
        circuitBreaker: { enable: true, rollback: true },
        cloudMapOptions: {
          name: `plm-${code}`,
          cloudMapNamespace: namespace,
          dnsRecordType: servicediscovery.DnsRecordType.SRV,
          containerPort: 8080,
        },
      });
      const { GET, POST, DELETE } = apigwv2.HttpMethod;
      route(`Integration-${code}`, `/api/${code}${PROXY}`, [GET], service, `/${code}/$request.path.proxy`);
      // The write-shaped requests a service accepts: catalogue-grounded SQL on
      // every service; the demo controls of a PLM (/demo/update, /demo/events/cad,
      // its own engineer or the officer); on core the demo change log (appended by
      // the links loader), the link write, the equivalence confirmation, the officer's
      // demo reset and the released-graph restore (GET /graphs/health falls under the
      // proxy route above).
      route(`SqlIntegration-${code}`, `/api/${code}/sql`, [POST], service, `/${code}/sql`);
      if (isPlm) {
        route(`DemoIntegration-${code}`, `/api/${code}/demo${PROXY}`, [POST], service, `/${code}/demo/$request.path.proxy`);
      } else {
        route(`ChangesIntegration-${code}`, `/api/${code}/changes`, [GET, POST, DELETE], service, `/${code}/changes`);
        route(`LinksIntegration-${code}`, `/api/${code}/links`, [POST], service, `/${code}/links`);
        route(`EquivalencesIntegration-${code}`, `/api/${code}/equivalences`, [POST], service, `/${code}/equivalences`);
        route(`DemoResetIntegration-${code}`, `/api/${code}/demo/reset`, [POST], service, `/${code}/demo/reset`);
        route(`GraphsResetIntegration-${code}`, `/api/${code}/graphs/reset`, [POST], service, `/${code}/graphs/reset`);
      }
    }

    const linksLoader = new LinksLoader(this, 'LinksLoader', {
      prefix: props.prefix,
      vpc,
      neptuneSparqlUrl: props.graph.sparqlUrl,
      neptuneSecurityGroup: neptuneSg,
      neptuneDataArn,
      apiEndpoint: this.api.apiEndpoint,
      originSecret: this.originSecret,
    });
    // The loader runs in the VPC (network interfaces from AWSLambdaVPCAccessExecutionRole) behind a
    // custom-resource provider.
    Validations.of(linksLoader).acknowledge(
      { id: LAMBDA_BASIC_EXECUTION_ROLE, reason: LAMBDA_BASIC_EXECUTION_REASON },
      {
        id: 'AwsSolutions-IAM4[Policy::arn:<AWS::Partition>:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole]',
        reason: 'AWSLambdaVPCAccessExecutionRole grants the network interfaces a function in the VPC needs, and its logs.',
      },
      { id: 'AwsSolutions-L1', reason: LAMBDA_RUNTIME_REASON },
      { id: neptuneDataArnFinding, reason: neptuneDataArnReason },
    );
    acknowledgeProviderFramework(linksLoader.node.findChild('Provider'), linksLoader.node.findChild('Fn'));

    if (props.semantic) {
      const ontopSg = new ec2.SecurityGroup(this, 'OntopSg', {
        vpc,
        description: 'Ontop virtual graphs - ingress from the query service only',
      });
      const querySg = new ec2.SecurityGroup(this, 'QuerySg', {
        vpc,
        description: 'Query service - ingress from the VPC link only',
      });
      ontopSg.addIngressRule(querySg, ec2.Port.tcp(8080), 'Query service to Ontop');
      querySg.addIngressRule(linkSg, ec2.Port.tcp(8080), 'VPC link to query service');
      ec2.SecurityGroup.fromSecurityGroupId(this, 'DbSgOntopRef', props.dbSecurityGroup.securityGroupId)
        .addIngressRule(ontopSg, ec2.Port.tcp(5432), 'Ontop to Aurora');
      neptuneSg.addIngressRule(querySg, ec2.Port.tcp(8182), 'Query service to Neptune');
      neptuneSg.addIngressRule(coreSg, ec2.Port.tcp(8182), 'Atelier core service to Neptune');

      const ontopUrls: Record<string, string> = {};
      for (const { code, db, secret } of databases) {
        // The core endpoint answers every profile's tag and membership requests over every product, a PLM endpoint its
        // site's rows: the core runs at 1 vCPU and 2 GiB with a 1536 MiB heap (the Ontop image's default is -Xmx512m).
        const core = code === CORE;
        const task = new ecs.FargateTaskDefinition(this, `OntopTask-${code}`, {
          cpu: core ? 1024 : 512,
          memoryLimitMiB: core ? 2048 : 1024,
          runtimePlatform: {
            cpuArchitecture: ecs.CpuArchitecture.ARM64,
            operatingSystemFamily: ecs.OperatingSystemFamily.LINUX,
          },
        });
        task.addContainer('ontop', {
          image: ecs.ContainerImage.fromEcrRepository(
            ecr.Repository.fromRepositoryName(this, `RepoOntop-${code}`, repoName(props.prefix, 'ontop')),
            props.imageTags[`ontop-${code}`],
          ),
          portMappings: [{ containerPort: 8080 }],
          environment: { DB_NAME: db, ...(core ? { ONTOP_JAVA_ARGS: '-Xmx1536m' } : {}) },
          secrets: { DB_SECRET_JSON: ecs.Secret.fromSecretsManager(secret) },
          // Ontop answers on / once the mapping is loaded; Cloud Map advertises the
        // task only after this passes (the image ships wget, not curl).
        healthCheck: {
          command: ['CMD-SHELL', 'wget -q -O /dev/null http://localhost:8080/ || exit 1'],
          interval: Duration.seconds(10),
          startPeriod: Duration.seconds(120),
        },
        logging: ecs.LogDrivers.awsLogs({ streamPrefix: `ontop-${code}`, logRetention: logs.RetentionDays.ONE_WEEK }),
        });
        acknowledgeTask(task);
        new ecs.FargateService(this, `OntopService-${code}`, {
          cluster,
          taskDefinition: task,
          desiredCount: 1,
          assignPublicIp: false,
          vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
          securityGroups: [ontopSg],
          circuitBreaker: { enable: true, rollback: true },
          // A records: the query service resolves ontop-<code> by plain DNS.
          cloudMapOptions: { name: `ontop-${code}`, cloudMapNamespace: namespace, dnsRecordType: servicediscovery.DnsRecordType.A },
        });
        ontopUrls[`ONTOP_${code.toUpperCase()}_URL`] = `http://ontop-${code}.${namespace.namespaceName}:8080/sparql`;
      }

      const queryTask = new ecs.FargateTaskDefinition(this, 'QueryTask', {
        cpu: 1024,
        memoryLimitMiB: 2048,
        runtimePlatform: {
          cpuArchitecture: ecs.CpuArchitecture.ARM64,
          operatingSystemFamily: ecs.OperatingSystemFamily.LINUX,
        },
      });
      queryTask.addContainer('app', {
        image: image('query'),
        portMappings: [{ containerPort: 8080 }],
        environment: {
          ...ontopUrls,
          NEPTUNE_SPARQL_URL: props.graph.sparqlUrl,
          NEPTUNE_IAM_AUTH: 'true',
          CAD_BUCKET: props.cadBucket.bucketName,
          // The catalogue and sql MCP tools call the PLM services through the
          // same API base as the browser, with the origin secret.
          PLM_API_BASE: `${this.api.apiEndpoint}/api`,
          ORIGIN_SECRET_ARN: this.originSecret.secretArn,
          // The released links.ttl and fileindex.ttl the image bundles; the demo
          // change list is the live graphs diffed against them. Writes to the
          // graph and the events that announce them belong to the core service.
          RELEASED_GRAPHS_DIR: '/app/data',
        },
        healthCheck: {
          command: ['CMD-SHELL', 'wget -q -O /dev/null http://localhost:8080/query/health || exit 1'],
          interval: Duration.seconds(10),
          startPeriod: Duration.seconds(90),
        },
        logging: ecs.LogDrivers.awsLogs({ streamPrefix: 'query', logRetention: logs.RetentionDays.ONE_WEEK }),
      });
      // Presigning needs GetObject on the CAD keys only: no listing, no other
      // prefix, so a URL can never be minted for anything but a CAD file.
      queryTask.addToTaskRolePolicy(new iam.PolicyStatement({
        actions: ['s3:GetObject'],
        resources: [props.cadBucket.arnForObjects('cad/*')],
      }));
      // The query service only reads the graphs.
      queryTask.addToTaskRolePolicy(new iam.PolicyStatement({ actions: NEPTUNE_READ_ACTIONS, resources: [neptuneDataArn] }));
      this.originSecret.grantRead(queryTask.taskRole);
      acknowledgeTask(queryTask);
      // cdk-nag names the finding by the imported bucket ARN; exportValue returns the export the reference above
      // already creates in the Data stack, so the template is unchanged.
      const cadBucketArn = Stack.of(props.cadBucket).resolve(Stack.of(props.cadBucket).exportValue(props.cadBucket.bucketArn));
      Validations.of(queryTask.taskRole).acknowledge(
        {
          id: `AwsSolutions-IAM5[Resource::${cadBucketArn['Fn::ImportValue']}/cad/*]`,
          reason: 'GetObject on the cad/ prefix of the CAD bucket: the service presigns the file of a part its policy '
            + 'releases, and holds no listing and no other prefix.',
        },
        { id: neptuneDataArnFinding, reason: neptuneDataArnReason },
      );
      const queryService = new ecs.FargateService(this, 'QueryService', {
        cluster,
        taskDefinition: queryTask,
        desiredCount: 1,
        assignPublicIp: false,
        vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
        securityGroups: [querySg],
        circuitBreaker: { enable: true, rollback: true },
        cloudMapOptions: {
          name: 'query',
          cloudMapNamespace: namespace,
          dnsRecordType: servicediscovery.DnsRecordType.SRV,
          containerPort: 8080,
        },
      });
      const { GET, POST, DELETE } = apigwv2.HttpMethod;
      // The query service only reads: GET for everything it serves, including
      // the derived change list (/demo/changes) and /demo/health; the demo
      // controls are routed to the owning PLM and core services above.
      route('Integration-query', '/api/query/{proxy+}', [GET], queryService, '/query/$request.path.proxy');
      // MCP over streamable HTTP is POST (DELETE ends a session); GET on the
      // same path falls under the /api/query/{proxy+} route above.
      route('Integration-query-mcp', '/api/query/mcp', [POST, DELETE], queryService, '/query/mcp');
      // The preview takes its cells in a POST body and writes nothing: the
      // copy of the merged graph it validates lives for the request.
      route('Integration-query-preview', '/api/query/preview', [POST], queryService, '/query/preview');

      const agent = new AgentService(this, 'Agent', {
        prefix: props.prefix,
        vpc,
        cluster,
        imageTag: props.imageTags.agent,
        apiEndpoint: this.api.apiEndpoint,
        originSecret: this.originSecret,
        modelId: props.modelId,
        pricePer1kInput: props.pricePer1kInput,
        pricePer1kOutput: props.pricePer1kOutput,
        logBucket: props.logBucket,
      });
      this.agentLoadBalancer = agent.loadBalancer;
    }

    new CfnOutput(this, 'ApiEndpoint', { value: this.api.apiEndpoint });
    new CfnOutput(this, 'OriginSecretArn', { value: this.originSecret.secretArn });
  }
}
