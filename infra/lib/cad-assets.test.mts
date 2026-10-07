// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The CAD asset staging: node --test infra/lib/cad-assets.test.mts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import * as zlib from 'node:zlib';

const { gzipTree } = await import('./cad-assets.ts');

test('every file is staged gzip-encoded under its own relative path, in a fresh directory of its own on every run', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'cad-assets-'));
  const src = path.join(root, 'stp');
  const step = 'ISO-10303-21;\nHEADER;\n' + 'DATA;\n#1=CARTESIAN_POINT((0.,0.,0.));\n'.repeat(500) + 'END-ISO-10303-21;\n';
  fs.mkdirSync(path.join(src, 'rover'), { recursive: true });
  fs.writeFileSync(path.join(src, 'rover', 'chassis.stp'), step);
  fs.writeFileSync(path.join(src, 'top.stp'), 'ISO-10303-21;\n');

  const dest = gzipTree(src);
  const staged = fs.readdirSync(dest, { recursive: true }).map(String).filter((f) => f.endsWith('.stp')).sort();
  assert.deepEqual(staged, [path.join('rover', 'chassis.stp'), 'top.stp']);
  const chassis = fs.readFileSync(path.join(dest, 'rover', 'chassis.stp'));
  assert.equal(zlib.gunzipSync(chassis).toString(), step);
  assert.ok(chassis.length * 5 < step.length, `${chassis.length} bytes for ${step.length}`);

  // A directory that exists before the run is never the stage: nothing placed there beforehand is uploaded as the CAD objects.
  const again = gzipTree(src);
  assert.equal(path.dirname(again), os.tmpdir());
  assert.notEqual(again, dest, 'every run stages into a directory created for it');
  assert.deepEqual(fs.readdirSync(again, { recursive: true }).map(String).filter((f) => f.endsWith('.stp')).sort(), staged);
  for (const dir of [root, dest, again]) fs.rmSync(dir, { recursive: true, force: true });
});
