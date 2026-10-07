// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The deploy role's direct permissions against the calls the deploy path makes:
//   node --test infra/policies/deploy-role-permissions.test.mts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

interface Statement { Sid: string; Action: string | string[]; Resource: string | string[] }
const policy = JSON.parse(readFileSync(new URL('./deploy-role-permissions.json', import.meta.url), 'utf8')) as { Statement: Statement[] };
const statement = (sid: string) => policy.Statement.find((s) => s.Sid === sid);
const actions = (sid: string) => [statement(sid)?.Action ?? []].flat();
const resources = (sid: string) => [statement(sid)?.Resource ?? []].flat();

test('CloudFormationDeploy writes the Atelier* stacks only; the bootstrap stack is read with DescribeStacks, the one call the CDK CLI makes on it', () => {
  assert.deepEqual(resources('CloudFormationDeploy'), [
    'arn:aws:cloudformation:eu-west-1:111111111111:stack/Atelier*/*',
    'arn:aws:cloudformation:us-east-1:111111111111:stack/Atelier*/*',
  ]);
  assert.deepEqual(actions('CdkToolkitRead'), ['cloudformation:DescribeStacks']);
  assert.deepEqual(resources('CdkToolkitRead'), [
    'arn:aws:cloudformation:eu-west-1:111111111111:stack/CDKToolkit/*',
    'arn:aws:cloudformation:us-east-1:111111111111:stack/CDKToolkit/*',
  ]);
});

test('SsmForCdkBootstrap reads the bootstrap version parameter of the two deploy regions only', () => {
  assert.deepEqual(actions('SsmForCdkBootstrap'), ['ssm:GetParameter']);
  assert.deepEqual(resources('SsmForCdkBootstrap'), [
    'arn:aws:ssm:eu-west-1:111111111111:parameter/cdk-bootstrap/*',
    'arn:aws:ssm:us-east-1:111111111111:parameter/cdk-bootstrap/*',
  ]);
});

test('CdkAssetPublish reads and writes asset objects only: cdk bootstrap owns the assets bucket and its configuration', () => {
  assert.deepEqual(actions('CdkAssetPublish')?.slice().sort(), [
    's3:DeleteObject', 's3:GetBucketLocation', 's3:GetObject', 's3:ListBucket', 's3:PutObject',
  ]);
});

test('DescribeForDeployAndSmoke holds the security-group read deploy.sh makes, nothing else', () => {
  assert.deepEqual(actions('DescribeForDeployAndSmoke'), ['ec2:DescribeSecurityGroups']);
});
