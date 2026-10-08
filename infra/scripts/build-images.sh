#!/usr/bin/env bash
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

#
# infra/scripts/build-images.sh — build the service images and push them to the ECR
# repositories of the target account. Requires Finch (or Docker: FINCH=docker).
#
#   DEPLOY_PROFILE=<aws profile> ./infra/scripts/build-images.sh [group]
#
# group: all (default) | java (plm-fr/de/uk/es/core + query) | other (ontop x5 + agent)
# Each image's tag is the hash of its own inputs (infra/lib/image-tags.ts). An image whose tag ECR
# already holds is neither built nor pushed, so a rebuild with no change does nothing, and the
# repositories' immutable tags never meet a second push of a tag. Builds run three at a time; the
# five PLM images share one compile stage, built first.
set -euo pipefail
PROFILE="${DEPLOY_PROFILE:-default}"
REGION="${DEPLOY_REGION:-eu-west-1}"
ENV_NAME="${ENV_NAME:-prod}"
GROUP="${1:-all}"
BIN="${FINCH:-finch}"
JOBS=3
PREFIX="atelier"; [ "$ENV_NAME" = "prod" ] || PREFIX="atelier-$ENV_NAME"
case "$GROUP" in all|java|other) ;; *) echo "error: group must be all, java or other" >&2; exit 1 ;; esac

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
ACCOUNT="$(aws sts get-caller-identity --query Account --output text --profile "$PROFILE" --region "$REGION")"
REGISTRY="$ACCOUNT.dkr.ecr.$REGION.amazonaws.com"

in_group() { # <key>
  case "$GROUP:$1" in all:*|java:plm-*|java:query|other:ontop-*|other:agent) return 0 ;; *) return 1 ;; esac
}
published() { # <repo> <tag>
  aws ecr describe-images --repository-name "$PREFIX/$1" --image-ids imageTag="$2" \
    --profile "$PROFILE" --region "$REGION" >/dev/null 2>&1
}

TAGS="$(node --disable-warning=MODULE_TYPELESS_PACKAGE_JSON infra/scripts/image-tags.ts)"
# The ECR lookups run side by side: each is a CLI start-up of about two seconds.
FOUND="$(mktemp -d)"; trap 'rm -rf "$FOUND"' EXIT
while IFS=$'\t' read -r key repo tag _; do
  in_group "$key" || continue
  { ! published "$repo" "$tag" || touch "$FOUND/$key"; } &
done <<<"$TAGS"
wait
KEYS=(); IMAGES=(); FILES=(); ARGS=()
while IFS=$'\t' read -r key repo tag dockerfile args; do
  in_group "$key" || continue
  image="$REGISTRY/$PREFIX/$repo:$tag"
  if [ -e "$FOUND/$key" ]; then echo ">> skip  $image (in ECR)"; continue; fi
  echo ">> build $image"
  KEYS+=("$key"); IMAGES+=("$image"); FILES+=("$dockerfile"); ARGS+=("$args")
done <<<"$TAGS"
[ "${#KEYS[@]}" -gt 0 ] || { echo ">> done ($GROUP): every image is in ECR"; exit 0; }

aws ecr get-login-password --profile "$PROFILE" --region "$REGION" | "$BIN" login --username AWS --password-stdin "$REGISTRY" >/dev/null

build() { # <index>
  local key="${KEYS[$1]}" image="${IMAGES[$1]}" log="$ROOT/build-${KEYS[$1]}.log" flags=() a
  for a in ${ARGS[$1]}; do flags+=(--build-arg "$a"); done
  "$BIN" build --platform linux/arm64 -f "${FILES[$1]}" -t "$image" ${flags[@]+"${flags[@]}"} . >"$log" 2>&1 \
    || { tail -40 "$log"; echo "FAILED: $image"; return 1; }
  "$BIN" push "$image" >>"$log" 2>&1 || { tail -20 "$log"; echo "PUSH FAILED: $image"; return 1; }
  echo ">> pushed $image ($key)"
}

# The first PLM image fills the compile stage the other four reuse.
FIRST=-1
for i in "${!KEYS[@]}"; do case "${KEYS[$i]}" in plm-*) FIRST=$i; break ;; esac; done
FAILED=0
[ "$FIRST" -lt 0 ] || build "$FIRST" || FAILED=1

PIDS=()
for i in "${!KEYS[@]}"; do
  [ "$i" -ne "$FIRST" ] || continue
  while [ "$(jobs -rp | wc -l)" -ge "$JOBS" ]; do sleep 1; done
  build "$i" &
  PIDS+=($!)
done
for pid in ${PIDS[@]+"${PIDS[@]}"}; do wait "$pid" || FAILED=1; done
[ "$FAILED" -eq 0 ] || { echo "FAILED: see build-<image>.log at the repository root" >&2; exit 1; }
echo ">> done ($GROUP): ${#KEYS[@]} image(s) built and pushed to $REGISTRY/$PREFIX"
