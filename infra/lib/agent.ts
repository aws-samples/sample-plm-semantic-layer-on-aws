// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { CfnOutput, Duration, Stack, Tags, Validations } from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { repoName } from './ecr-stack';

/** The agent container, its ALB listener and the CloudFront VPC origin all use this port. */
export const AGENT_PORT = 8080;

export interface AgentServiceProps {
  readonly prefix: string;
  readonly vpc: ec2.IVpc;
  readonly cluster: ecs.ICluster;
  /** Content-addressed tag of the agent image (infra/lib/image-tags.ts). */
  readonly imageTag: string;
  /** API Gateway endpoint; the agent calls <endpoint>/api/query/mcp with the origin secret. */
  readonly apiEndpoint: string;
  readonly originSecret: secretsmanager.ISecret;
  /** Bedrock model or inference profile id, or its ARN (env MODEL_ID). */
  readonly modelId: string;
  /** USD per 1k input / output tokens for the agent's cost estimate. */
  readonly pricePer1kInput: string;
  readonly pricePer1kOutput: string;
  /** Destination of the load balancer's access logs (alb/agent/). */
  readonly logBucket: s3.IBucket;
}

/**
 * The in-app agent (Strands, FastAPI on 8080, AG-UI over SSE) behind an
 * internal ALB that CloudFront reaches through a VPC origin under /agent/*.
 * The agent serves /agent/invocations and /agent/health; CloudFront forwards
 * the path unchanged.
 *
 * GUARDRAIL #4: the load balancer is internal and its only listener is plain
 * HTTP on 8080, never 80; CloudFront terminates TLS and is the only viewer
 * path. The ALB security group has no ingress rule at synth: CloudFront
 * creates its VPC-origin security group (CloudFront-VPCOrigins-Service-SG)
 * during the first VPC-origin deploy, so infra/scripts/deploy.sh authorizes
 * it after every app deploy.
 */
export class AgentService extends Construct {
  public readonly loadBalancer: elbv2.ApplicationLoadBalancer;

  constructor(scope: Construct, id: string, props: AgentServiceProps) {
    super(scope, id);
    const stack = Stack.of(this);

    const albSg = new ec2.SecurityGroup(this, 'AlbSg', {
      vpc: props.vpc,
      description: 'Agent ALB - ingress from the CloudFront VPC origin only, authorized by deploy.sh',
      allowAllOutbound: false,
    });
    // The deploy role may authorize ingress only on security groups carrying this tag.
    Tags.of(albSg).add('atelier:role', 'agent-alb');
    const agentSg = new ec2.SecurityGroup(this, 'Sg', {
      vpc: props.vpc,
      description: 'Agent - ingress from its ALB only',
    });
    agentSg.addIngressRule(albSg, ec2.Port.tcp(AGENT_PORT), 'Agent ALB to agent');

    this.loadBalancer = new elbv2.ApplicationLoadBalancer(this, 'Alb', {
      vpc: props.vpc,
      internetFacing: false,
      securityGroup: albSg,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      // An AG-UI turn streams over SSE for as long as the model works.
      idleTimeout: Duration.seconds(300),
      dropInvalidHeaderFields: true,
    });
    this.loadBalancer.logAccessLogs(props.logBucket, 'alb/agent');
    const listener = this.loadBalancer.addListener('Http', {
      port: AGENT_PORT,
      protocol: elbv2.ApplicationProtocol.HTTP,
      // No 0.0.0.0/0 ingress on the ALB security group.
      open: false,
    });

    const task = new ecs.FargateTaskDefinition(this, 'Task', {
      cpu: 1024,
      memoryLimitMiB: 2048,
      runtimePlatform: {
        cpuArchitecture: ecs.CpuArchitecture.ARM64,
        operatingSystemFamily: ecs.OperatingSystemFamily.LINUX,
      },
    });
    task.addContainer('app', {
      image: ecs.ContainerImage.fromEcrRepository(
        ecr.Repository.fromRepositoryName(this, 'Repo', repoName(props.prefix, 'agent')),
        props.imageTag,
      ),
      portMappings: [{ containerPort: AGENT_PORT }],
      environment: {
        MODEL_ID: props.modelId,
        MCP_URL: `${props.apiEndpoint}/api/query/mcp`,
        ORIGIN_SECRET_ARN: props.originSecret.secretArn,
        PRICE_PER_1K_INPUT: props.pricePer1kInput,
        PRICE_PER_1K_OUTPUT: props.pricePer1kOutput,
      },
      // python:3.12-slim ships neither wget nor curl; urlopen raises on non-2xx.
      healthCheck: {
        command: ['CMD-SHELL', `python3 -c "import urllib.request; urllib.request.urlopen('http://localhost:${AGENT_PORT}/agent/health')" || exit 1`],
        interval: Duration.seconds(10),
        startPeriod: Duration.seconds(60),
      },
      logging: ecs.LogDrivers.awsLogs({ streamPrefix: 'agent', logRetention: logs.RetentionDays.ONE_WEEK }),
    });
    props.originSecret.grantRead(task.taskRole);
    task.addToTaskRolePolicy(new iam.PolicyStatement({
      actions: ['bedrock:InvokeModel', 'bedrock:InvokeModelWithResponseStream'],
      resources: [
        // A cross-region inference profile routes to foundation models in other regions.
        'arn:aws:bedrock:*::foundation-model/*',
        props.modelId.startsWith('arn:')
          ? props.modelId
          : stack.formatArn({ service: 'bedrock', resource: 'inference-profile', resourceName: props.modelId }),
      ],
    }));
    Validations.of(task).acknowledge(
      {
        id: 'AwsSolutions-ECS2',
        reason: 'The environment carries the model id, the MCP URL, the secret ARN and the prices; '
          + 'the origin secret itself is read from Secrets Manager at run time.',
      },
      { id: 'AwsSolutions-IAM5[Resource::*]', reason: 'ecr:GetAuthorizationToken of the execution role takes no resource.' },
      {
        id: 'AwsSolutions-IAM5[Resource::arn:aws:bedrock:*::foundation-model/*]',
        reason: 'The model is a deployment parameter and a cross-region inference profile invokes the foundation '
          + 'model in whichever of its regions answers, so the region and the model id are not known at synth.',
      },
    );

    const service = new ecs.FargateService(this, 'Service', {
      cluster: props.cluster,
      taskDefinition: task,
      desiredCount: 1,
      assignPublicIp: false,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      securityGroups: [agentSg],
      circuitBreaker: { enable: true, rollback: true },
      healthCheckGracePeriod: Duration.seconds(60),
    });
    const targetGroup = listener.addTargets('Agent', {
      port: AGENT_PORT,
      protocol: elbv2.ApplicationProtocol.HTTP,
      targets: [service],
      healthCheck: { path: '/agent/health', interval: Duration.seconds(15) },
    });

    // deploy.sh adds the CloudFront VPC-origin ingress rule to this group; the
    // smoke job asserts a healthy target through this target group.
    new CfnOutput(stack, 'AgentAlbSecurityGroupId', { value: albSg.securityGroupId });
    new CfnOutput(stack, 'AgentTargetGroupArn', { value: targetGroup.targetGroupArn });
  }
}
