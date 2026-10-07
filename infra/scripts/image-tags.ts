// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Prints one line per service image, tab-separated: build key, ECR repository, content-addressed tag,
// Dockerfile, build arguments (KEY=VALUE, space-separated). build-images.sh and deploy.sh read it:
//   node infra/scripts/image-tags.ts
import * as path from 'path';
import { IMAGE_BUILDS, dockerignore, imageTag } from '../lib/image-tags.ts';

const root = path.resolve(import.meta.dirname, '..', '..');
const ignored = dockerignore(root);
const digests = new Map<string, string>();
for (const build of IMAGE_BUILDS) {
  const args = Object.entries(build.args).map(([k, v]) => `${k}=${v}`).join(' ');
  console.log([build.key, build.repo, imageTag(root, build, ignored, digests), build.dockerfile, args].join('\t'));
}
