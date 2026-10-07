// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The content-addressed image tags: node --test infra/lib/image-tags.test.mts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';

const { IMAGE_BUILDS, copySources, dockerignore, imageTag, imageTags } = await import('./image-tags.ts');

const repo = path.resolve(import.meta.dirname, '..', '..');

/** A copy of the build context every image reads, so a test can change one file. */
function contextCopy(): string {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'image-tags-'));
  for (const rel of ['.dockerignore', 'modules/plm-services', 'modules/ontop', 'modules/query-service', 'modules/agent', 'ontology', 'data']) {
    fs.cpSync(path.join(repo, rel), path.join(root, rel), {
      recursive: true,
      filter: (src) => !/\/(target|node_modules|\.venv|__pycache__)(\/|$)/.test(src),
    });
  }
  return root;
}

const changed = (before: Record<string, string>, after: Record<string, string>) =>
  Object.keys(before).filter((k) => before[k] !== after[k]).sort();

test('COPY sources resolve the build arguments and leave out other stages', () => {
  const ontop = fs.readFileSync(path.join(repo, 'modules/ontop/Dockerfile'), 'utf8');
  const sources = copySources(ontop, { PLM: 'de' });
  assert.ok(sources.includes('modules/ontop/mappings/de.r2rml.ttl'));
  assert.ok(sources.includes('ontology/atelier.ttl'), 'the ONTOLOGY default applies');
  assert.ok(!sources.some((s) => s.startsWith('/')), 'COPY --from paths are not context paths');
});

test('every image has a distinct key and its tag is stable across runs', () => {
  assert.equal(new Set(IMAGE_BUILDS.map((b) => b.key)).size, IMAGE_BUILDS.length);
  assert.deepEqual(imageTags(repo), imageTags(repo));
  for (const [key, tag] of Object.entries(imageTags(repo))) assert.match(tag, /^([a-z]+-)?[0-9a-f]{12}$/, key);
});

test('a change to one site service changes that site image only; the shared module changes the five', () => {
  const root = contextCopy();
  const before = imageTags(root);
  fs.appendFileSync(path.join(root, 'modules/plm-services/plm-de/src/main/resources/application.yml'), '\n');
  assert.deepEqual(changed(before, imageTags(root)), ['plm-de']);
  const mid = imageTags(root);
  fs.appendFileSync(path.join(root, 'modules/plm-services/pom.xml'), '\n');
  assert.deepEqual(changed(mid, imageTags(root)), ['plm-core', 'plm-de', 'plm-es', 'plm-fr', 'plm-uk']);
});

test('a site mapping changes its Ontop endpoint and the query service, which reformulates over every mapping', () => {
  const root = contextCopy();
  const before = imageTags(root);
  fs.appendFileSync(path.join(root, 'modules/ontop/mappings/uk.r2rml.ttl'), '\n');
  assert.deepEqual(changed(before, imageTags(root)), ['ontop-uk', 'query']);
});

test('regenerated seeds and graphs change the images that bake them', () => {
  const root = contextCopy();
  const before = imageTags(root);
  fs.appendFileSync(path.join(root, 'data/links.ttl'), '\n');
  assert.deepEqual(changed(before, imageTags(root)), ['plm-core', 'plm-de', 'plm-es', 'plm-fr', 'plm-uk', 'query']);
});

test('ignored files and files no image copies leave every tag unchanged', () => {
  const root = contextCopy();
  const before = imageTags(root);
  fs.mkdirSync(path.join(root, 'modules/plm-services/plm-fr/target'), { recursive: true });
  fs.writeFileSync(path.join(root, 'modules/plm-services/plm-fr/target/plm-fr.jar'), 'built');
  fs.mkdirSync(path.join(root, 'modules/agent/atelier_agent/__pycache__'), { recursive: true });
  fs.writeFileSync(path.join(root, 'modules/agent/atelier_agent/__pycache__/server.cpython-312.pyc'), 'x');
  fs.mkdirSync(path.join(root, 'data/products'), { recursive: true });
  fs.writeFileSync(path.join(root, 'data/products/extra.json'), '{}');
  assert.deepEqual(changed(before, imageTags(root)), []);
});

test('a file added to a copied directory, or an executable bit, changes the images that copy it', () => {
  const root = contextCopy();
  const before = imageTags(root);
  fs.writeFileSync(path.join(root, 'modules/agent/atelier_agent/notes.txt'), 'x');
  assert.deepEqual(changed(before, imageTags(root)), ['agent']);
  const mid = imageTags(root);
  fs.chmodSync(path.join(root, 'modules/ontop/src/entrypoint.sh'), 0o644);
  assert.deepEqual(changed(mid, imageTags(root)), ['ontop-core', 'ontop-de', 'ontop-es', 'ontop-fr', 'ontop-uk']);
});

test('.dockerignore patterns match the path or a parent directory', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'dockerignore-'));
  fs.writeFileSync(path.join(root, '.dockerignore'), '**/target\nnode_modules\ninfra/cdk.out\n');
  const ignored = dockerignore(root);
  assert.ok(ignored('modules/plm-services/plm-fr/target/x.jar'));
  assert.ok(ignored('node_modules/a/b.js'));
  assert.ok(ignored('infra/cdk.out/manifest.json'));
  assert.ok(!ignored('modules/node_modules_list.txt'));
  assert.ok(!ignored('infra/cdk.output'));
});

test('a COPY source outside the build context is refused', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'context-'));
  fs.writeFileSync(path.join(root, 'Dockerfile'), 'FROM scratch\nCOPY ../outside /outside\n');
  const build = { key: 'x', repo: 'x', dockerfile: 'Dockerfile', args: {} };
  assert.throws(() => imageTag(root, build), /outside the build context/);
});
