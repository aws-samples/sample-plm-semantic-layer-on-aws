// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { Stack, StackProps } from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as neptune from 'aws-cdk-lib/aws-neptune';

export interface GraphStackProps extends StackProps {
  readonly prefix: string;
  readonly vpc: ec2.IVpc;
}

/** The neptune-db actions of a client that only queries the graphs: the query service. */
export const NEPTUNE_READ_ACTIONS = [
  'neptune-db:ReadDataViaQuery',
  'neptune-db:GetQueryStatus',
  'neptune-db:CancelQuery',
  'neptune-db:GetEngineStatus',
];
/** The neptune-db actions of a client that also writes and replaces named graphs: the core service and the loader. */
export const NEPTUNE_WRITE_ACTIONS = [...NEPTUNE_READ_ACTIONS, 'neptune-db:WriteDataViaQuery', 'neptune-db:DeleteDataViaQuery'];

/**
 * The materialized graph: Amazon Neptune holding the links between PLMs
 * (interfaces and mated plugs) and the file index (where each part's CAD file
 * is). Everything else is read in place through the virtual graphs. The graphs
 * are written by the links loader and the core service, both in the Services
 * stack (see links-loader.ts).
 *
 * GUARDRAIL #5: the cluster is in private subnets and admits only the security
 * groups of the loader, the query service (reads) and the core service (writes
 * the links graph, restores the released graphs); those ingress rules are
 * created in the Services stack on this group. IAM database authentication is
 * on as well: each of the three clients signs every request with SigV4 (service
 * `neptune-db`) under its task or function role, and the Services stack grants
 * each role its own neptune-db actions (NEPTUNE_READ_ACTIONS for the query
 * service, NEPTUNE_WRITE_ACTIONS for the core service and the loader) on the
 * cluster's data ARN, built from clusterResourceId.
 */
export class GraphStack extends Stack {
  public readonly securityGroup: ec2.SecurityGroup;
  public readonly sparqlUrl: string;
  /**
   * The cluster resource id, exported for the Services stack: the resource of the data-access ARN
   * `arn:<partition>:neptune-db:<region>:<account>:<cluster resource id>/*` that the neptune-db actions are granted on.
   */
  public readonly clusterResourceId: string;

  constructor(scope: Construct, id: string, props: GraphStackProps) {
    super(scope, id, props);

    this.securityGroup = new ec2.SecurityGroup(this, 'NeptuneSg', {
      vpc: props.vpc,
      description: 'Neptune links graph - ingress from the loader and query service',
      allowAllOutbound: false,
    });

    const subnets = new neptune.CfnDBSubnetGroup(this, 'Subnets', {
      dbSubnetGroupDescription: `${props.prefix} Neptune private subnets`,
      subnetIds: props.vpc.selectSubnets({ subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS }).subnetIds,
    });
    // Audit log: every query against the links graph is exported to CloudWatch Logs.
    const parameters = new neptune.CfnDBClusterParameterGroup(this, 'ClusterParameters', {
      family: 'neptune1.4',
      description: `${props.prefix} Neptune cluster parameters`,
      parameters: { neptune_enable_audit_log: 1 },
    });
    const cluster = new neptune.CfnDBCluster(this, 'Cluster', {
      engineVersion: '1.4.8.0',
      dbSubnetGroupName: subnets.ref,
      dbClusterParameterGroupName: parameters.ref,
      vpcSecurityGroupIds: [this.securityGroup.securityGroupId],
      // Every request must carry a SigV4 signature by a role that holds the neptune-db action it performs;
      // the clients sign when NEPTUNE_IAM_AUTH=true, which the Services stack sets on all three.
      // https://docs.aws.amazon.com/neptune/latest/userguide/iam-auth.html
      iamAuthEnabled: true,
      storageEncrypted: true,
      deletionProtection: false,
      backupRetentionPeriod: 7,
      enableCloudwatchLogsExports: ['audit'],
    });
    const instance = new neptune.CfnDBInstance(this, 'Instance', {
      dbClusterIdentifier: cluster.ref,
      dbInstanceClass: 'db.t4g.medium',
      autoMinorVersionUpgrade: true,
    });
    instance.addDependency(cluster);

    this.sparqlUrl = `https://${cluster.attrEndpoint}:${cluster.attrPort}/sparql`;
    this.clusterResourceId = this.exportValue(cluster.attrClusterResourceId);
  }
}
