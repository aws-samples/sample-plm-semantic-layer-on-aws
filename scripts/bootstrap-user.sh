#!/usr/bin/env bash
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

#
# scripts/bootstrap-user.sh <email> — creates a user in the web stack's Cognito user
# pool with a permanent password. The pool has no self-service sign-up, so this is the
# only way in through the Cognito edge sign-in.
#
#   ATELIER_USER_PASSWORD='...' scripts/bootstrap-user.sh someone@example.com
#
# The pool id comes from cdk-outputs-<env>.json written by deploy.sh (DEPLOY_ENV,
# default prod). Credentials: DEPLOY_PROFILE / DEPLOY_REGION locally, the runner's
# credentials under CI, like deploy.sh. The password is read from ATELIER_USER_PASSWORD, handed
# to the AWS CLI through a private temporary file and never printed or put on a command line.
set -euo pipefail

EMAIL="${1:-}"
[ -n "$EMAIL" ] || { echo "usage: ATELIER_USER_PASSWORD='...' $0 <email>" >&2; exit 2; }
[ -n "${ATELIER_USER_PASSWORD:-}" ] || { echo "error: ATELIER_USER_PASSWORD is not set" >&2; exit 2; }

ENV="${DEPLOY_ENV:-prod}"
REGION="${DEPLOY_REGION:-eu-west-1}"
PROFILE="${DEPLOY_PROFILE:-default}"
PROFILE_ARGS=()
[ -n "${CI:-}" ] || PROFILE_ARGS=(--profile "$PROFILE")

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUTS="$REPO_ROOT/cdk-outputs-$ENV.json"
[ -f "$OUTPUTS" ] || { echo "error: $OUTPUTS not found; deploy the app group first" >&2; exit 2; }
POOL_ID="$(jq -r 'first(.[] | to_entries[] | select(.key | endswith("UserPoolId")) | .value) // empty' "$OUTPUTS")"
[ -n "$POOL_ID" ] || { echo "error: no UserPoolId output in $OUTPUTS; the web stack was not deployed" >&2; exit 2; }

if out="$(aws cognito-idp admin-create-user --user-pool-id "$POOL_ID" --username "$EMAIL" \
    --user-attributes Name=email,Value="$EMAIL" Name=email_verified,Value=true \
    --message-action SUPPRESS --region "$REGION" "${PROFILE_ARGS[@]}" 2>&1)"; then
  echo ">> created $EMAIL in pool $POOL_ID"
elif grep -q UsernameExistsException <<<"$out"; then
  echo ">> $EMAIL already exists in pool $POOL_ID; setting the password"
else
  echo "$out" >&2; exit 1
fi

# jq reads the password from the environment, so it appears on no command line.
INPUT="$(mktemp)"
chmod 600 "$INPUT"
trap 'rm -f "$INPUT"' EXIT
jq -n --arg pool "$POOL_ID" --arg user "$EMAIL" \
  '{UserPoolId: $pool, Username: $user, Password: env.ATELIER_USER_PASSWORD, Permanent: true}' > "$INPUT"
aws cognito-idp admin-set-user-password --cli-input-json "file://$INPUT" --region "$REGION" "${PROFILE_ARGS[@]}"
echo ">> $EMAIL can sign in to the $ENV web stack with the password from ATELIER_USER_PASSWORD"
