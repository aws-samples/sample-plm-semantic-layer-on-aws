#!/usr/bin/env bash
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# =============================================================================
# teardown.sh — remove Atelier in the ORDER THAT MATTERS.
#
#   bash scripts/teardown.sh --env prod --dry-run    # print the plan, touch nothing
#   bash scripts/teardown.sh --env prod              # do it (asks once)
#   bash scripts/teardown.sh --env prod --yes        # do it without asking
#
# The order matters. The parent NS delegation is deleted BEFORE the child hosted
# zone: deleting the zone first leaves the parent pointing at a zone that no longer
# exists, a dangling delegation, which is a subdomain-takeover risk.
#
# Order:
#   1. parent NS delegation record   (DNS account, DNS_PROFILE)
#   2. cdk destroy --all             (target account: CloudFront, S3, Cognito, ...)
#   3. child hosted zone(s)          (target account)
#   4. orphaned CloudWatch log groups (they OUTLIVE cdk destroy)
#   5. the deploy role of infra/policies/
# Steps 1 and 3 run only when a custom domain was created for the site
# (DEMO_BASE_DOMAIN and DEMO_PARENT_ZONE_ID set).
#
# Everything is idempotent: a re-run after a partial teardown is expected.
# =============================================================================
set -euo pipefail

DEMO_NAME="Atelier"
DEMO_SLUG="plm-semantic-layer-sample"
BASE_DOMAIN="${DEMO_BASE_DOMAIN:-}"          # e.g. example.com; the site is <slug>.<base domain>
PARENT_ZONE_ID="${DEMO_PARENT_ZONE_ID:-}"    # hosted zone of the base domain, which delegates the child zone
PROFILE="${DEPLOY_PROFILE:-default}"         # target/workload account
DNS_PROFILE="${DNS_PROFILE:-default}"        # account owning the parent zone
REGION="${DEPLOY_REGION:-eu-west-1}"
ROLE_NAME="${DEMO_NAME}Deploy"

ENV="prod"
DRY=0
ASSUME_YES=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --env) ENV="${2:?--env needs a value}"; shift 2 ;;
    --dry-run) DRY=1; shift ;;
    --yes|-y) ASSUME_YES=1; shift ;;
    *) echo "error: unknown argument '$1'" >&2; exit 1 ;;
  esac
done

FQDN=""
if [ -n "$BASE_DOMAIN" ]; then
  [ -n "$PARENT_ZONE_ID" ] || { echo "error: DEMO_PARENT_ZONE_ID is required with DEMO_BASE_DOMAIN" >&2; exit 1; }
  if [ "$ENV" = "prod" ]; then
    FQDN="$DEMO_SLUG.$BASE_DOMAIN"
  else
    FQDN="$DEMO_SLUG-$ENV.$BASE_DOMAIN"
  fi
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

say()  { echo ">> $*"; }
step() { echo; echo "── $* ─────────────────────────────────────────"; }
run()  {
  if [ "$DRY" = "1" ]; then
    echo "   [dry-run] $*"
  else
    "$@"
  fi
}

step "PLAN — env=$ENV, fqdn=${FQDN:-(no custom domain)}"
cat <<PLAN
   1. DELETE the NS record '$FQDN' from parent zone $PARENT_ZONE_ID
      (profile $DNS_PROFILE)  <-- MUST happen before step 3; skipped without a custom domain
   2. cdk destroy --all -c env=$ENV        (profile $PROFILE)
   3. DELETE the child hosted zone for $FQDN (profile $PROFILE); skipped without a custom domain
   4. DELETE orphaned /aws/lambda/${DEMO_NAME}* log groups (profile $PROFILE)
   5. DELETE IAM role $ROLE_NAME           (profile $PROFILE)
PLAN

if [ "$DRY" = "1" ]; then
  echo
  say "dry-run: nothing was changed."
  exit 0
fi

if [ "$ASSUME_YES" != "1" ]; then
  echo
  printf "Destroy %s (%s)? This deletes live AWS resources. Type the slug to confirm: " "$DEMO_NAME" "$ENV"
  read -r reply
  if [ "$reply" != "$DEMO_SLUG" ]; then
    echo "aborted (expected '$DEMO_SLUG')."
    exit 1
  fi
fi

# --- 1. parent NS delegation FIRST ------------------------------------------
step "1/5 parent NS delegation"
# Read the record back and delete it verbatim: a DELETE change batch must match
# the existing record set EXACTLY (name, type, TTL, every value), so building it
# from the live record is the only reliable way.
NS_JSON="null"
[ -z "$FQDN" ] || NS_JSON="$(aws route53 list-resource-record-sets \
  --profile "$DNS_PROFILE" --hosted-zone-id "$PARENT_ZONE_ID" \
  --query "ResourceRecordSets[?Name=='${FQDN}.' && Type=='NS'] | [0]" \
  --output json 2>/dev/null || echo "null")"

if [ -z "$FQDN" ]; then
  say "no custom domain configured — skipping"
elif [ "$NS_JSON" = "null" ] || [ -z "$NS_JSON" ]; then
  say "no NS delegation for $FQDN in the parent zone (already removed) — skipping"
else
  BATCH="$(mktemp)"
  jq -n --argjson rrs "$NS_JSON" --arg fqdn "$FQDN" \
    '{Comment: ("Remove " + $fqdn + " delegation before deleting the child zone"),
      Changes: [{Action: "DELETE", ResourceRecordSet: $rrs}]}' > "$BATCH"
  say "deleting NS delegation for $FQDN from $PARENT_ZONE_ID"
  aws route53 change-resource-record-sets \
    --profile "$DNS_PROFILE" --hosted-zone-id "$PARENT_ZONE_ID" \
    --change-batch "file://$BATCH" >/dev/null
  rm -f "$BATCH"
  say "delegation removed (no dangling NS)"
fi

# --- 2. cdk destroy ----------------------------------------------------------
step "2/5 cdk destroy"
if [ -d "$REPO_ROOT/infra" ]; then
  ACCOUNT="$(aws sts get-caller-identity --query Account --output text --profile "$PROFILE" --region "$REGION")"
  ( cd "$REPO_ROOT/infra" && npx cdk destroy --all --force -c env="$ENV" -c account="$ACCOUNT" -c region="$REGION" --profile "$PROFILE" ) \
    || say "WARNING: cdk destroy reported an error; check CloudFormation before continuing"
else
  say "no infra/ directory — skipping"
fi

# --- 3. child hosted zone ----------------------------------------------------
step "3/5 child hosted zone"
ZID="None"
[ -z "$FQDN" ] || ZID="$(aws route53 list-hosted-zones-by-name --profile "$PROFILE" \
  --dns-name "$FQDN" \
  --query "HostedZones[?Name=='${FQDN}.'].Id | [0]" --output text 2>/dev/null || echo "None")"
if [ -z "$FQDN" ]; then
  say "no custom domain configured — skipping"
elif [ "$ZID" = "None" ] || [ -z "$ZID" ]; then
  say "no child zone for $FQDN (already deleted) — skipping"
else
  ZID="${ZID#/hostedzone/}"
  # A zone deletes only when it holds nothing but its apex SOA + NS. cdk destroy
  # removes the ACM validation CNAME and the alias records; anything else lingering
  # has to go first, or the delete fails with HostedZoneNotEmpty.
  LEFTOVER="$(aws route53 list-resource-record-sets --profile "$PROFILE" \
    --hosted-zone-id "$ZID" \
    --query "ResourceRecordSets[?Type!='SOA' && Type!='NS']" --output json)"
  if [ "$(echo "$LEFTOVER" | jq 'length')" -gt 0 ]; then
    say "child zone still holds non-apex records; deleting them first"
    echo "$LEFTOVER" | jq -c '.[]' | while IFS= read -r rrs; do
      B="$(mktemp)"
      jq -n --argjson r "$rrs" '{Changes:[{Action:"DELETE",ResourceRecordSet:$r}]}' > "$B"
      aws route53 change-resource-record-sets --profile "$PROFILE" \
        --hosted-zone-id "$ZID" --change-batch "file://$B" >/dev/null \
        || say "  could not delete a record — continuing"
      rm -f "$B"
    done
  fi
  say "deleting child zone $ZID ($FQDN)"
  aws route53 delete-hosted-zone --profile "$PROFILE" --id "$ZID" >/dev/null \
    || say "WARNING: child zone delete failed — it may still hold records"
fi

# --- 4. orphaned log groups --------------------------------------------------
step "4/5 orphaned CloudWatch log groups"
# The log groups of the Lambda functions and of the CDK custom resources (bucket
# deployment, auto-delete objects) outlive `cdk destroy`; they cost money and a
# log group with the same name can block a later redeploy.
for prefix in "/aws/lambda/${DEMO_NAME}" "/aws/lambda/${DEMO_NAME}-${ENV}"; do
  GROUPS="$(aws logs describe-log-groups --profile "$PROFILE" --region "$REGION" \
    --log-group-name-prefix "$prefix" \
    --query 'logGroups[].logGroupName' --output text 2>/dev/null || true)"
  for lg in $GROUPS; do
    say "deleting log group $lg"
    aws logs delete-log-group --profile "$PROFILE" --region "$REGION" \
      --log-group-name "$lg" 2>/dev/null || say "  (already gone)"
  done
done

# --- 5. deploy role ----------------------------------------------------------
step "5/5 deploy role"
if aws iam get-role --profile "$PROFILE" --role-name "$ROLE_NAME" >/dev/null 2>&1; then
  # Inline policies and attached managed policies must go before the role.
  for p in $(aws iam list-role-policies --profile "$PROFILE" --role-name "$ROLE_NAME" \
               --query 'PolicyNames[]' --output text 2>/dev/null || true); do
    say "deleting inline policy $p"
    aws iam delete-role-policy --profile "$PROFILE" --role-name "$ROLE_NAME" --policy-name "$p"
  done
  for a in $(aws iam list-attached-role-policies --profile "$PROFILE" --role-name "$ROLE_NAME" \
               --query 'AttachedPolicies[].PolicyArn' --output text 2>/dev/null || true); do
    say "detaching managed policy $a"
    aws iam detach-role-policy --profile "$PROFILE" --role-name "$ROLE_NAME" --policy-arn "$a"
  done
  say "deleting role $ROLE_NAME"
  aws iam delete-role --profile "$PROFILE" --role-name "$ROLE_NAME"
else
  say "role $ROLE_NAME not found (already deleted) — skipping"
fi

# --- verify ------------------------------------------------------------------
step "verify"
if [ -n "$FQDN" ]; then
  say "NS delegation for $FQDN in the parent zone:"
  aws route53 list-resource-record-sets --profile "$DNS_PROFILE" \
    --hosted-zone-id "$PARENT_ZONE_ID" \
    --query "length(ResourceRecordSets[?Name=='${FQDN}.' && Type=='NS'])" --output text
  say "(0 = clean; anything else is a DANGLING DELEGATION — remove it now)"
fi
say "resources still tagged Demo=$DEMO_SLUG:"
aws resourcegroupstaggingapi get-resources --profile "$PROFILE" --region "$REGION" \
  --tag-filters "Key=Demo,Values=$DEMO_SLUG" \
  --query 'length(ResourceTagMappingList)' --output text 2>/dev/null || echo "?"
say "(the tagging API is eventually consistent — re-check in a few minutes before"
say " concluding something survived, and confirm with the service's own describe call)"
echo
say "teardown complete for $DEMO_NAME ($ENV)."
