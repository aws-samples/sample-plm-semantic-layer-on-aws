#!/usr/bin/env bash
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

#
# infra/scripts/ci-image-tags.sh — the image plan of a CI pipeline, as dotenv lines for the image jobs:
#
#   TAG_<key>=<tag>      the tag the image job pushes (key: plm_fr .. plm_core, query, ontop_fr .. ontop_core, agent)
#   HELD_<key>=true      ECR already holds that tag: the image job skips its build (ECR tags are immutable)
#
# The tags are the content-addressed tags of infra/lib/image-tags.ts, the ones the synth names each service's
# image by: tags() prints the first three columns of `node infra/scripts/image-tags.ts`, "<key> <repo> <tag>" per
# image. Run from the repository root; needs node and the AWS CLI.
#
#   ./infra/scripts/ci-image-tags.sh > images.env
set -euo pipefail
REGION="${DEPLOY_REGION:-eu-west-1}"
PREFIX="atelier"

tags() {
  node --disable-warning=MODULE_TYPELESS_PACKAGE_JSON infra/scripts/image-tags.ts | cut -f1-3
}

tags | while IFS=$'\t' read -r key repo tag; do
  var="${key//-/_}"
  echo "TAG_$var=$tag"
  if aws ecr describe-images --repository-name "$PREFIX/$repo" --image-ids imageTag="$tag" --region "$REGION" >/dev/null 2>&1; then
    echo "HELD_$var=true"
  fi
done
