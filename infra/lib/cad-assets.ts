// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import * as zlib from 'zlib';

/**
 * Writes every file under `src` gzip-compressed to the same relative path under a directory created for this run
 * in the system's temporary directory, and returns that directory. STEP is text and compresses about five to one;
 * the CAD bucket serves the files with Content-Encoding gzip and the browser decodes them as it reads them. The
 * stage is fresh on every run, so only the files of `src` are ever in it: the caller hashes `src` itself to name
 * the asset, and a directory that exists beforehand is never taken for the stage. `src` is the repository's CAD
 * tree, named by the data stack at synth time; no request input reaches a synth.
 */
export function gzipTree(src: string): string {
  const dest = fs.mkdtempSync(path.join(os.tmpdir(), 'atelier-cad-'));
  for (const entry of fs.readdirSync(src, { recursive: true, withFileTypes: true })) { // nosemgrep: detect-non-literal-fs-filename -- src is the CAD tree of the repository, a path the data stack computes; no request input
    if (!entry.isFile()) continue;
    const from = path.join(entry.parentPath, entry.name); // nosemgrep: path-join-resolve-traversal -- entry is a file readdirSync listed under src
    const to = path.join(dest, sanitizeRelativePath(src, from));
    fs.mkdirSync(path.dirname(to), { recursive: true }); // nosemgrep: detect-non-literal-fs-filename -- to is under dest, the directory mkdtempSync created, checked by sanitizeRelativePath
    fs.writeFileSync(to, zlib.gzipSync(fs.readFileSync(from), { level: 9 })); // nosemgrep: detect-non-literal-fs-filename -- from is a file readdirSync listed under src; to is under dest, checked by sanitizeRelativePath
  }
  return dest;
}

/** The path of `file` relative to `root`, which `file` must be under: the stage mirrors the tree and nothing outside it. */
function sanitizeRelativePath(root: string, file: string): string {
  const rel = path.relative(root, file);
  if (rel === '' || rel.startsWith('..') || path.isAbsolute(rel)) throw new Error(`${file} is not a file under ${root}`);
  return rel;
}
