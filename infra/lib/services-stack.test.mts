// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The API routes of the synthesised Services stack: node --test infra/lib/services-stack.test.mts
import { after, test } from 'node:test';
import assert from 'node:assert/strict';
import { SynthFixture } from './synth-fixture.mts';

const synth = new SynthFixture(['Atelier-Services']);
after(() => synth.remove());

test('the API routes every request of the core service: its read proxy and each of its writes, the equivalence confirmation among them', () => {
  const routes = Object.values(synth.template('Atelier-Services').Resources)
    .filter((r) => r.Type === 'AWS::ApiGatewayV2::Route')
    .map((r) => String(r.Properties.RouteKey));
  assert.deepEqual(routes.filter((key) => key.includes('/api/core')).sort(), [
    'DELETE /api/core/changes',
    'GET /api/core/changes',
    'GET /api/core/{proxy+}',
    'POST /api/core/changes',
    'POST /api/core/demo/reset',
    'POST /api/core/equivalences',
    'POST /api/core/graphs/reset',
    'POST /api/core/links',
    'POST /api/core/sql',
  ]);
});
