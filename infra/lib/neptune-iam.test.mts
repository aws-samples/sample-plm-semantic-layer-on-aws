// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// IAM database authentication on the Neptune cluster and the grants and switch of its three clients, in the
// synthesised Graph and Services stacks: node --test infra/lib/neptune-iam.test.mts
import { after, test } from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { SynthFixture, type Resource } from './synth-fixture.mts';

// The fine-grained neptune-db actions each principal holds (the lists graph-stack.ts grants).
const NEPTUNE_READ_ACTIONS = ['neptune-db:ReadDataViaQuery', 'neptune-db:GetQueryStatus', 'neptune-db:CancelQuery', 'neptune-db:GetEngineStatus'];
const NEPTUNE_WRITE_ACTIONS = [...NEPTUNE_READ_ACTIONS, 'neptune-db:WriteDataViaQuery', 'neptune-db:DeleteDataViaQuery'];

const synth = new SynthFixture(['Atelier-Graph', 'Atelier-Services']);
after(() => synth.remove());

const graph = synth.template('Atelier-Graph');
const services = synth.template('Atelier-Services');
const resources = (template: { Resources: Record<string, Resource> }, type: string) =>
  Object.entries(template.Resources).filter(([, r]) => r.Type === type);

interface Statement { Action: string | string[]; Resource: unknown }
/** The neptune-db statements of the Services stack, by the logical id of the role they are attached to. */
function neptuneStatements(): Map<string, Statement> {
  const byRole = new Map<string, Statement>();
  for (const [, policy] of resources(services, 'AWS::IAM::Policy')) {
    const document = policy.Properties.PolicyDocument as { Statement: Statement[] };
    const roles = policy.Properties.Roles as { Ref: string }[];
    for (const statement of document.Statement) {
      const actions = ([] as string[]).concat(statement.Action);
      if (!actions.some((a) => a.startsWith('neptune-db:'))) continue;
      assert.equal(roles.length, 1);
      assert.equal(byRole.has(roles[0].Ref), false, `one neptune-db statement per role: ${roles[0].Ref}`);
      byRole.set(roles[0].Ref, statement);
    }
  }
  return byRole;
}
const roleOf = (taskLogicalId: string) =>
  (services.Resources[taskLogicalId].Properties.TaskRoleArn as { 'Fn::GetAtt': [string, string] })['Fn::GetAtt'][0];
const containerEnv = (taskLogicalId: string, container: string): Record<string, unknown> => {
  const definitions = services.Resources[taskLogicalId].Properties.ContainerDefinitions as { Name: string; Environment: { Name: string; Value: unknown }[] }[];
  return Object.fromEntries(definitions.find((c) => c.Name === container)!.Environment.map((e) => [e.Name, e.Value]));
};

test('the cluster has IAM database authentication on; the synth has no cdk-nag violation and the Graph stack acknowledges no N5 finding', () => {
  const [[, cluster]] = resources(graph, 'AWS::Neptune::DBCluster');
  assert.equal(cluster.Properties.IamAuthEnabled, true);
  assert.deepEqual(synth.validationReport().pluginReports.flatMap((r) => r.violations ?? []), []);
  assert.equal(fs.readFileSync(path.join(import.meta.dirname, 'graph-stack.ts'), 'utf8').includes('AwsSolutions-N5'), false);
});

test('the data-access ARN every grant names is the imported cluster resource id followed by /*', () => {
  const outputs = (graph as unknown as { Outputs: Record<string, { Export?: { Name: string }; Value: unknown }> }).Outputs;
  const [exportName] = Object.values(outputs).filter((o) => JSON.stringify(o.Value).includes('ClusterResourceId')).map((o) => o.Export!.Name);
  assert.ok(exportName, 'the Graph stack exports the cluster resource id');
  for (const [role, statement] of neptuneStatements()) {
    assert.deepEqual(statement.Resource, {
      'Fn::Join': ['', ['arn:aws:neptune-db:eu-west-1:111111111111:', { 'Fn::ImportValue': exportName }, '/*']],
    }, role);
  }
});

test('the query service task role reads only; the core service task role and the loader function role read and write', () => {
  const statements = neptuneStatements();
  const [[queryTask]] = resources(services, 'AWS::ECS::TaskDefinition').filter(([id]) => id.startsWith('QueryTask'));
  const [[coreTask]] = resources(services, 'AWS::ECS::TaskDefinition').filter(([id]) => id.startsWith('Taskcore'));
  const [[loaderFn]] = resources(services, 'AWS::Lambda::Function').filter(([, fn]) =>
    'GSP_URL' in ((fn.Properties.Environment as { Variables?: Record<string, unknown> } | undefined)?.Variables ?? {}));
  const loaderRole = (services.Resources[loaderFn].Properties.Role as { 'Fn::GetAtt': [string, string] })['Fn::GetAtt'][0];

  assert.deepEqual([...statements.keys()].sort(), [roleOf(queryTask), roleOf(coreTask), loaderRole].sort(), 'exactly three principals');
  // The template lists the actions of a statement sorted.
  assert.deepEqual(statements.get(roleOf(queryTask))!.Action, [...NEPTUNE_READ_ACTIONS].sort());
  assert.deepEqual(statements.get(roleOf(coreTask))!.Action, [...NEPTUNE_WRITE_ACTIONS].sort());
  assert.deepEqual(statements.get(loaderRole)!.Action, [...NEPTUNE_WRITE_ACTIONS].sort());

  // The PLM task roles hold no neptune-db action at all.
  for (const [id] of resources(services, 'AWS::ECS::TaskDefinition').filter(([id]) => /^Task(fr|de|uk|es)/.test(id))) {
    assert.equal(statements.has(roleOf(id)), false, id);
  }
});

test('the three clients get the switch NEPTUNE_IAM_AUTH=true next to their Neptune URL; no PLM task does', () => {
  const [[queryTask]] = resources(services, 'AWS::ECS::TaskDefinition').filter(([id]) => id.startsWith('QueryTask'));
  const [[coreTask]] = resources(services, 'AWS::ECS::TaskDefinition').filter(([id]) => id.startsWith('Taskcore'));
  const [[, loaderFn]] = resources(services, 'AWS::Lambda::Function').filter(([, fn]) =>
    'GSP_URL' in ((fn.Properties.Environment as { Variables?: Record<string, unknown> } | undefined)?.Variables ?? {}));

  assert.equal(containerEnv(queryTask, 'app').NEPTUNE_IAM_AUTH, 'true');
  assert.ok(containerEnv(queryTask, 'app').NEPTUNE_SPARQL_URL);
  assert.equal(containerEnv(coreTask, 'app').NEPTUNE_IAM_AUTH, 'true');
  assert.ok(containerEnv(coreTask, 'app').NEPTUNE_SPARQL_URL);
  const loaderEnv = (loaderFn.Properties.Environment as { Variables: Record<string, unknown> }).Variables;
  assert.equal(loaderEnv.NEPTUNE_IAM_AUTH, 'true');
  for (const [id] of resources(services, 'AWS::ECS::TaskDefinition').filter(([id]) => /^Task(fr|de|uk|es)/.test(id))) {
    assert.equal('NEPTUNE_IAM_AUTH' in containerEnv(id, 'app'), false, id);
  }
});
