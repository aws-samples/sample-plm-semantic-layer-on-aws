// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import * as crypto from 'crypto';
import * as fs from 'fs';
import * as path from 'path';

/**
 * The service images and their content-addressed tags. An image's tag is a hash of what it is built from: its
 * Dockerfile, its build arguments and every file its COPY instructions take from the build context (with the
 * executable bit), after .dockerignore. The tag changes exactly when the image would differ, so
 * build-images.sh builds and pushes only images whose tag ECR does not hold yet, and a synth gives an unchanged
 * service the same task definition: it does not roll. A private product copied into a deploy worktree changes the
 * seeds and graphs it is generated into, and with them the tags of the images that bake them.
 */
export interface ImageBuild {
  /** Unique name of the build: the repository, or `ontop-<site>` for the five Ontop endpoints. */
  readonly key: string;
  /** ECR repository under the deployment prefix. */
  readonly repo: string;
  readonly dockerfile: string;
  readonly args: Readonly<Record<string, string>>;
  /** Context directories COPY takes that the image's own content does not depend on. */
  readonly exclude?: readonly string[];
  /** Prefix of the tag, before the hash. */
  readonly tagPrefix?: string;
}

const PLM_SITES = ['fr', 'de', 'uk', 'es', 'core'] as const;
const plmModule = (site: string) => (site === 'core' ? 'atelier-core' : `plm-${site}`);

/**
 * The PLM Dockerfile compiles every module once, in a stage shared by the five images, and each image keeps its
 * own jar. That jar is built from the parent pom, plm-common and the image's module, so the other sites' modules
 * stay out of its tag: a change to one site's service changes that site's image only.
 */
const plm = (site: string): ImageBuild => ({
  key: `plm-${site}`,
  repo: `plm-${site}`,
  dockerfile: 'modules/plm-services/Dockerfile',
  args: { PLM: site },
  exclude: PLM_SITES.filter((s) => s !== site).map((s) => `modules/plm-services/${plmModule(s)}/`),
});

const ontop = (site: string): ImageBuild => ({
  key: `ontop-${site}`,
  repo: 'ontop',
  dockerfile: 'modules/ontop/Dockerfile',
  args: { PLM: site },
  tagPrefix: `${site}-`,
});

export const IMAGE_BUILDS: readonly ImageBuild[] = [
  ...PLM_SITES.map(plm),
  { key: 'query', repo: 'query', dockerfile: 'modules/query-service/Dockerfile', args: {} },
  ...PLM_SITES.map(ontop),
  { key: 'agent', repo: 'agent', dockerfile: 'modules/agent/Dockerfile', args: {} },
];

/** Instructions of a Dockerfile, line continuations joined, comments dropped. */
function instructions(text: string): string[] {
  return text
    .replace(/\\\r?\n/g, ' ')
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith('#'));
}

/** The context paths a Dockerfile's COPY and ADD instructions read, ARG references resolved. */
export function copySources(dockerfile: string, args: Readonly<Record<string, string>>): string[] {
  const values: Record<string, string> = {};
  const sources: string[] = [];
  const expand = (s: string) => s.replace(/\$\{(\w+)\}|\$(\w+)/g, (_, a, b) => {
    const name = a ?? b;
    if (!(name in values)) throw new Error(`${dockerfile}: \${${name}} has no value`);
    return values[name];
  });
  for (const line of instructions(dockerfile)) {
    const [op, ...rest] = line.split(/\s+/);
    const word = op.toUpperCase();
    if (word === 'ARG') {
      const eq = rest[0].indexOf('=');
      const name = eq < 0 ? rest[0] : rest[0].slice(0, eq);
      if (name in args) values[name] = args[name];
      else if (eq >= 0) values[name] = rest[0].slice(eq + 1);
    } else if (word === 'COPY' || word === 'ADD') {
      const operands = rest.filter((t) => !t.startsWith('--'));
      if (rest.some((t) => t.startsWith('--from='))) continue;
      sources.push(...operands.slice(0, -1).map(expand));
    }
  }
  return sources;
}

// The source of a .dockerignore pattern as a regular expression: metacharacters escaped, a double star and slash any
// directories, `*` and `?` within a segment.
const globSource = (pattern: string) => pattern.replace(/\/+$/, '').split('**/').map((part) =>
  part.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '[^/]*').replace(/\?/g, '[^/]')).join('(?:.*/)?');

/**
 * The absolute path of a context path, which stays under the root: a COPY source that climbs out of the build
 * context is refused, as Docker refuses it.
 */
function sanitizeContextPath(root: string, rel: string): string {
  const abs = path.resolve(root, rel); // nosemgrep: path-join-resolve-traversal -- root is the repository checkout the synth entry point and the tests name; rel is checked against it on the next line
  const inside = path.relative(root, abs);
  if (inside === '..' || inside.startsWith(`..${path.sep}`) || path.isAbsolute(inside)) throw new Error(`${rel} is outside the build context ${root}`);
  return abs;
}

/** A matcher for the root .dockerignore: a path is ignored when it or one of its parent directories matches. */
export function dockerignore(root: string): (rel: string) => boolean {
  const file = sanitizeContextPath(root, '.dockerignore');
  const text = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : ''; // nosemgrep: detect-non-literal-fs-filename -- the .dockerignore of the repository checkout, read at synth time
  const patterns = text.split(/\r?\n/).map((l) => l.trim()).filter((l) => l && !l.startsWith('#'));
  if (patterns.some((p) => p.startsWith('!'))) throw new Error('.dockerignore exceptions (!) are not supported by the image tags');
  const regexes = patterns.map((p) => new RegExp(`^${globSource(p)}$`)); // nosemgrep: detect-non-literal-regexp -- p is a .dockerignore pattern whose metacharacters globSource escapes; only * and ? remain, as [^/]* and [^/]
  return (rel) => {
    const segments = rel.split('/');
    return segments.some((_, i) => regexes.some((r) => r.test(segments.slice(0, i + 1).join('/'))));
  };
}

/** Every file under the given context paths, relative to the root, sorted, without ignored and excluded paths. */
function contextFiles(root: string, sources: string[], ignored: (rel: string) => boolean, exclude: readonly string[]): string[] {
  const files = new Set<string>();
  const add = (rel: string) => {
    if (ignored(rel) || exclude.some((e) => `${rel}/`.startsWith(e))) return;
    const abs = sanitizeContextPath(root, rel);
    const stat = fs.statSync(abs); // nosemgrep: detect-non-literal-fs-filename -- a COPY source under the repository checkout, kept inside it by sanitizeContextPath, read at synth time
    if (stat.isDirectory()) for (const name of fs.readdirSync(abs)) add(path.posix.join(rel, name)); // nosemgrep: detect-non-literal-fs-filename -- a COPY source directory under the repository checkout, kept inside it by sanitizeContextPath, listed at synth time
    else files.add(rel);
  };
  for (const source of sources) add(path.posix.normalize(source).replace(/\/+$/, ''));
  return [...files].sort();
}

const sha256 = (data: string | Buffer) => crypto.createHash('sha256').update(data).digest('hex');

/** The tag of one image: its prefix and the first 12 hex digits of the hash of its inputs. */
export function imageTag(root: string, build: ImageBuild, ignored = dockerignore(root), digests = new Map<string, string>()): string {
  const dockerfile = fs.readFileSync(sanitizeContextPath(root, build.dockerfile), 'utf8'); // nosemgrep: detect-non-literal-fs-filename -- the Dockerfile of an IMAGE_BUILDS entry, a tracked path under the repository checkout, read at synth time
  const lines = [`dockerfile ${build.dockerfile} ${sha256(dockerfile)}`];
  for (const [name, value] of Object.entries(build.args).sort()) lines.push(`arg ${name}=${value}`);
  for (const rel of contextFiles(root, copySources(dockerfile, build.args), ignored, build.exclude ?? [])) {
    let digest = digests.get(rel);
    if (digest === undefined) {
      const abs = sanitizeContextPath(root, rel);
      digest = `${(fs.statSync(abs).mode & 0o111) !== 0 ? 'x' : '-'} ${sha256(fs.readFileSync(abs))}`; // nosemgrep: detect-non-literal-fs-filename -- a file a COPY instruction takes from the repository checkout, kept inside it by sanitizeContextPath, hashed at synth time
      digests.set(rel, digest);
    }
    lines.push(`file ${rel} ${digest}`);
  }
  return `${build.tagPrefix ?? ''}${sha256(lines.join('\n')).slice(0, 12)}`;
}

/** The tag of every image, by build key. */
export function imageTags(root: string): Record<string, string> {
  const ignored = dockerignore(root);
  const digests = new Map<string, string>();
  return Object.fromEntries(IMAGE_BUILDS.map((b) => [b.key, imageTag(root, b, ignored, digests)]));
}
