#!/usr/bin/env bash
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

#
# infra/scripts/deploy.sh — deploy Atelier to AWS and publish runtime config.
#
#   0. (app group) check that every service image is in ECR under its content tag
#   1. synth the cloud assembly for the selected env
#   2. HARD GATE: scripts/check-guardrails.sh on that assembly
#   3. cdk deploy the selected stack group FROM THE GATED ASSEMBLY (--app cdk.out)
#   4. (app group, semantic) let the CloudFront VPC-origin security group reach the agent ALB
#   5. (app group) publish config.json + invalidate
#
# Stack groups:
#   base  ECR, network, Aurora, Neptune. Deployed before the image build so the images can be pushed.
#   app   PLM + core services, Ontop, query, agent, API, and the web stack with its Cognito edge
#         sign-in (a user pool and a Lambda@Edge function gate every web behaviour).
#
# The gate lives here because it is the one path both local and CI deploys take.
# There is deliberately NO override flag. Deploying `--app cdk.out` ships the
# exact templates the gate read.
#
# Context comes from the environment:
#   PLMS               comma list of PLM services to run (default: all four)
#   DEFAULT_PRODUCT    product key the web opens on (default: ornithopter)
#   SEMANTIC           "true" runs the semantic tier: Ontop, query service, agent
#   BEDROCK_MODEL_ID   Bedrock model or inference profile the agent calls (-c modelId)
#   PRICE_PER_1K_INPUT, PRICE_PER_1K_OUTPUT   USD per 1k tokens for the agent's cost estimate
#
# AUTH: under CI ($CI set) the runner's own credentials are used (the deploy role of
# infra/policies/, assumed by the runner), so no --profile; locally --profile $DEPLOY_PROFILE.
#
# USAGE:
#   ./infra/scripts/deploy.sh --env prod --stacks base
#   ./infra/scripts/deploy.sh --env prod --stacks app
#   ./infra/scripts/deploy.sh --env prod --stacks app --gate-only
#   DEPLOY_PROFILE=my-admin-profile ./infra/scripts/deploy.sh --env prod --stacks base
#
set -euo pipefail

# Target account: DEPLOY_PROFILE / DEPLOY_REGION locally (defaults below), the
# runner's credentials under CI. The account id is read from the credentials in use.
REGION="${DEPLOY_REGION:-eu-west-1}"
PROFILE="${DEPLOY_PROFILE:-default}"

ENV=""
GROUP=""
GATE_ONLY=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --env) ENV="${2:-}"; shift 2 ;;
    --stacks) GROUP="${2:-}"; shift 2 ;;
    --gate-only) GATE_ONLY=1; shift ;;
    *) echo "error: unknown argument '$1' (expected: --env <name> --stacks base|app [--gate-only])" >&2; exit 1 ;;
  esac
done
[ -n "$ENV" ] || { echo "error: --env <name> is required" >&2; exit 1; }
printf '%s' "$ENV" | grep -Eq '^[a-z0-9-]{1,16}$' || { echo "error: env '$ENV' must match ^[a-z0-9-]{1,16}$" >&2; exit 1; }
case "$GROUP" in base|app) ;; *) echo "error: --stacks must be base or app" >&2; exit 1 ;; esac

PROFILE_ARGS=()
DEPLOY_ARGS=()
[ -n "${CI:-}" ] || PROFILE_ARGS=(--profile "$PROFILE")
ACCOUNT="${DEPLOY_ACCOUNT:-}"
[ -n "$ACCOUNT" ] || ACCOUNT="$(aws sts get-caller-identity --query Account --output text --region "$REGION" ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"})"
printf '%s' "$ACCOUNT" | grep -Eq '^[0-9]{12}$' || { echo "error: could not read the account id from the credentials in use" >&2; exit 1; }
if [ -n "${CI:-}" ]; then
  # CloudFormation runs as the bootstrap execution role, so the CI role itself
  # needs only asset publishing, CloudFormation calls and PassRole on it.
  DEPLOY_ARGS=(--role-arn "arn:aws:iam::$ACCOUNT:role/cdk-hnb659fds-cfn-exec-role-$ACCOUNT-$REGION")
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$INFRA_DIR/.." && pwd)"
cd "$INFRA_DIR"

PREFIX="Atelier"; [ "$ENV" = "prod" ] || PREFIX="Atelier-$ENV"
OUTPUTS_PATH="$REPO_ROOT/cdk-outputs-$ENV.json"
ASSEMBLY="cdk.out"

CONTEXT=(-c env="$ENV" -c account="$ACCOUNT" -c region="$REGION")
[ -z "${PLMS:-}" ] || CONTEXT+=(-c plms="$PLMS")
[ "${SEMANTIC:-false}" != "true" ] || CONTEXT+=(-c semantic=true)
[ -z "${BEDROCK_MODEL_ID:-}" ] || CONTEXT+=(-c modelId="$BEDROCK_MODEL_ID")
[ -z "${PRICE_PER_1K_INPUT:-}" ] || CONTEXT+=(-c pricePer1kInput="$PRICE_PER_1K_INPUT")
[ -z "${PRICE_PER_1K_OUTPUT:-}" ] || CONTEXT+=(-c pricePer1kOutput="$PRICE_PER_1K_OUTPUT")

if [ "$GROUP" = "base" ]; then
  STACKS=("$PREFIX-Ecr" "$PREFIX-Network" "$PREFIX-Data" "$PREFIX-Graph")
else
  STACKS=("$PREFIX-Services")
  [ -f "$REPO_ROOT/modules/web/dist/index.html" ] || { echo "error: modules/web/dist/index.html missing — run 'npm run build' first" >&2; exit 1; }
  # The Lambda@Edge sign-in function lives in its own us-east-1 stack; --exclusively needs it listed.
  STACKS+=("$PREFIX-Web" "$PREFIX-WebEdge")
  # A custom domain exists only when cdk.json names a hosted zone (same rule as bin/app.ts).
  case "$(jq -r --arg e "$ENV" '.context.domains[$e].hostedZoneId // "REPLACE"' cdk.json)" in REPLACE*) ;; *) STACKS+=("$PREFIX-WebCert") ;; esac
fi

# --- 0. images --------------------------------------------------------------
# The synth names each service's image by the content-addressed tag of infra/lib/image-tags.ts, so a
# service whose image is unchanged keeps its task definition. Every image the app group runs must be in
# ECR under that tag: build-images.sh pushes the missing ones.
if [ "$GROUP" = "app" ] && [ "$GATE_ONLY" = "0" ]; then
  REPO_PREFIX="$(printf '%s' "$PREFIX" | tr '[:upper:]' '[:lower:]')"
  TAGS="$(node --disable-warning=MODULE_TYPELESS_PACKAGE_JSON "$REPO_ROOT/infra/scripts/image-tags.ts")"
  ABSENT="$(mktemp -d)"
  while IFS=$'\t' read -r key repo tag _; do
    case "$key" in
      plm-core) ;;
      plm-*) case ",${PLMS:-fr,de,uk,es}," in *",${key#plm-},"*) ;; *) continue ;; esac ;;
      *) [ "${SEMANTIC:-false}" = "true" ] || continue ;;
    esac
    { aws ecr describe-images --repository-name "$REPO_PREFIX/$repo" --image-ids imageTag="$tag" \
        --region "$REGION" ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"} >/dev/null 2>&1 || touch "$ABSENT/$repo:$tag"; } &
  done <<<"$TAGS"
  wait
  MISSING="$(ls "$ABSENT")"; rm -rf "$ABSENT"
  [ -z "$MISSING" ] || { echo "error: not in ECR: $(echo "$MISSING" | tr '\n' ' '); run infra/scripts/build-images.sh" >&2; exit 1; }
  echo ">> every service image is in ECR under its content tag"
fi

# --- 1. synth --------------------------------------------------------------
echo ">> synthesizing $PREFIX (${CONTEXT[*]})"
rm -rf "$ASSEMBLY"
npx cdk synth "${CONTEXT[@]}" -q ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"}

# --- 2. HARD GATE ----------------------------------------------------------
GATE="$REPO_ROOT/scripts/check-guardrails.sh"
[ -f "$GATE" ] || { echo "FATAL: guardrail gate not found at $GATE — refusing to deploy ungated." >&2; exit 1; }
echo ">> guardrail gate ($(bash "$GATE" --version))"
bash "$GATE" "$INFRA_DIR/$ASSEMBLY" || { echo "FATAL: guardrail violation — deploy aborted." >&2; exit 1; }
if [ "$GATE_ONLY" = "1" ]; then echo ">> --gate-only: synth + guardrails passed."; exit 0; fi

# --- 3. deploy the gated assembly ------------------------------------------
echo ">> deploying ${STACKS[*]} to $ACCOUNT / $REGION"
npx cdk deploy "${STACKS[@]}" --exclusively \
  --app "$ASSEMBLY" --require-approval never \
  --outputs-file "$OUTPUTS_PATH" ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"} ${DEPLOY_ARGS[@]+"${DEPLOY_ARGS[@]}"}

pick() { jq -r --arg s "$1" 'first(.[] | to_entries[] | select(.key | endswith($s)) | .value) // empty' "$OUTPUTS_PATH"; }

# --- 4. CloudFront VPC origin -> agent ALB ----------------------------------
# CloudFront creates the VPC-origin security group (CloudFront-VPCOrigins-Service-SG)
# in the VPC during the first VPC-origin deploy, so no template can reference it:
# the agent ALB admits it here, on its listener port, after every app deploy.
ALB_SG="$(pick AgentAlbSecurityGroupId)"
if [ -n "$ALB_SG" ]; then
  VPC_ID="$(aws ec2 describe-security-groups --group-ids "$ALB_SG" \
    --query 'SecurityGroups[0].VpcId' --output text --region "$REGION" ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"})"
  CF_SG="$(aws ec2 describe-security-groups \
    --filters "Name=vpc-id,Values=$VPC_ID" "Name=group-name,Values=CloudFront-VPCOrigins-Service-SG" \
    --query 'SecurityGroups[0].GroupId' --output text --region "$REGION" ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"})"
  [ -n "$CF_SG" ] && [ "$CF_SG" != None ] || { echo "error: CloudFront-VPCOrigins-Service-SG not found in $VPC_ID; the /agent/* VPC origin did not deploy" >&2; exit 1; }
  if out="$(aws ec2 authorize-security-group-ingress --group-id "$ALB_SG" --protocol tcp --port 8080 \
      --source-group "$CF_SG" --region "$REGION" ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"} 2>&1)"; then
    echo ">> agent ALB $ALB_SG now admits CloudFront VPC origin $CF_SG on 8080"
  elif grep -q InvalidPermission.Duplicate <<<"$out"; then
    echo ">> agent ALB $ALB_SG already admits CloudFront VPC origin $CF_SG on 8080"
  else
    echo "$out" >&2; exit 1
  fi
fi

# --- 5. runtime config (web stack only) ------------------------------------
WEB_BUCKET="$(pick WebBucketName)"
DISTRIBUTION_ID="$(pick DistributionId)"
if [ -z "$WEB_BUCKET" ]; then
  echo ">> no web stack in this deploy; skipping config.json"
  exit 0
fi
[ -n "$DISTRIBUTION_ID" ] || { echo "error: DistributionId output missing" >&2; exit 1; }

jq -n --arg envName "$ENV" --arg apiBase "/api" --arg plms "${PLMS:-fr,de,uk,es}" --arg product "${DEFAULT_PRODUCT:-ornithopter}" \
  '{envName:$envName, apiBase:$apiBase, plms:($plms|split(",")), defaultProduct:$product}' > config.json
cat config.json
aws s3 cp config.json "s3://$WEB_BUCKET/config.json" --content-type application/json \
  --cache-control no-store --region "$REGION" ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"}
aws cloudfront create-invalidation --distribution-id "$DISTRIBUTION_ID" --paths '/*' \
  --region us-east-1 ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"} >/dev/null
echo ">> deployed $PREFIX web -> $(pick SiteUrl)"
