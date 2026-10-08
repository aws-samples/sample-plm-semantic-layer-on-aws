#!/usr/bin/env bash
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# =============================================================================
# check-guardrails.sh — HARD GATE on the five mandatory security guardrails.
#
# Scans every synthesized CloudFormation template under cdk.out/ and exits
# non-zero on ANY violation. Run AFTER `cdk synth` and BEFORE `cdk deploy`;
# infra/scripts/deploy.sh aborts on a non-zero exit.
#
#   check-guardrails.sh [cdk.out-dir]     # default: cdk.out
#   check-guardrails.sh --version         # print the gate version and exit
#
# Guardrails:
#   #1 Cognito  : every UserPool has AdminCreateUserConfig.AllowAdminCreateUserOnly == true
#   #2 S3       : every Bucket has a fully-locked PublicAccessBlockConfiguration (all 4 true);
#                 no BucketPolicy Allow statement grants a wildcard Principal any s3:
#                 action (or "*") without a SourceArn/SourceAccount/OrgID-shaped condition
#   #3 Lambda   : no AWS::Lambda::Url resource exists
#   #4 ELB      : no internet-facing LoadBalancer (v2 OR classic); no listener on port 80
#   #5 No public data-plane bypass of the auth layer:
#                 - no API Gateway REST method / HTTP-API route with AuthorizationType "NONE"
#                 - no AppSync API with API_KEY auth (default or additional), no ApiKey resource
#                 - no RDS DBInstance/DBCluster with PubliclyAccessible == true
#                 - no OpenSearch domain whose AccessPolicies allow a wildcard principal
#                   with no condition
#                 NOTE: JWT / SigV4 / IAM / COGNITO_USER_POOLS / CUSTOM authorizers and
#                 AgentCore Runtime endpoints are AUTHENTICATED ingress and are NOT bypasses.
#
# SCOPE: every assertion here is made against synthesized CloudFormation, so this
# gate covers INFRASTRUCTURE only. An auth bypass that lives in application code
# (a handler trusting a client-supplied identity, a proxy forwarding an unverified
# header) is invisible here. That tier is covered by the unit tests of each module
# and by the post-deploy probe in tests/smoke.mjs.
#
# Exit codes: 0 pass · 1 guardrail violation · 2 prerequisite/usage error.
# =============================================================================
set -euo pipefail

# Bump when an assertion is added or changed.
GUARDRAILS_VERSION="2.1.0"

if [[ "${1:-}" == "--version" ]]; then
  echo "check-guardrails.sh $GUARDRAILS_VERSION"
  exit 0
fi

CDK_OUT="${1:-cdk.out}"

if ! command -v jq >/dev/null 2>&1; then
  echo "FATAL: jq is required for the guardrail gate but was not found on PATH." >&2
  exit 2
fi

if [[ ! -d "$CDK_OUT" ]]; then
  echo "FATAL: cdk.out directory '$CDK_OUT' not found. Run 'cdk synth' first." >&2
  exit 2
fi

# Collect templates safely (NUL-delimited; portable to bash 3.2 on macOS, which
# has no `mapfile`). Handles spaces / newlines in paths.
#
# RECURSIVE on purpose: a CDK app that uses Stages writes its templates to
# cdk.out/assembly-*/ , and nested stacks land in subdirectories too. A
# -maxdepth 1 search finds ZERO templates there and the gate would abort with a
# usage error on a perfectly valid app.
TEMPLATES=()
while IFS= read -r -d '' f; do
  TEMPLATES+=("$f")
done < <(find "$CDK_OUT" -type f -name '*.template.json' -print0)

if [[ ${#TEMPLATES[@]} -eq 0 ]]; then
  echo "FATAL: no *.template.json files under '$CDK_OUT'. Did 'cdk synth' run?" >&2
  exit 2
fi

violations=0
note() { echo "  VIOLATION: $*" >&2; violations=$((violations + 1)); }
review() { echo "  REVIEW: $*"; }

# Resource-type tallies. A "PASSED" over zero buckets is not the same as a
# "PASSED" over three, and the difference must be visible: a scan that examined
# nothing is the failure mode that reads exactly like success.
n_pools=0 n_buckets=0 n_policies=0 n_urls=0 n_lbs=0 n_listeners=0  # nosemgrep: unquoted-variable-expansion-in-command -- literal assignments; semgrep's bash parser fails on this file and reports this line
n_rest=0 n_http=0 n_appsync=0 n_rds=0 n_os=0

count_type() { # $1 template, $2 resource type
  jq -r --arg t "$2" '[(.Resources // {}) | to_entries[] | select(.value.Type == $t)] | length' "$1"
}

for tpl in "${TEMPLATES[@]}"; do
  echo "==> Scanning $(basename "$tpl")"

  # --- Validate JSON up front so a malformed template can't silently pass. ---
  if ! jq -e . "$tpl" >/dev/null 2>&1; then
    echo "  FATAL: $tpl is not valid JSON." >&2
    exit 2
  fi

  # Each tally adds one integer printed by count_type inside $(( )), where bash does not word-split and
  # rejects quotes; semgrep's unquoted-expansion rule does not know arithmetic context.
  n_pools=$((    n_pools    + $(count_type "$tpl" "AWS::Cognito::UserPool") ))  # nosemgrep: unquoted-command-substitution-in-command -- arithmetic, not split
  n_buckets=$((  n_buckets  + $(count_type "$tpl" "AWS::S3::Bucket") ))  # nosemgrep: unquoted-command-substitution-in-command -- arithmetic, not split
  n_policies=$(( n_policies + $(count_type "$tpl" "AWS::S3::BucketPolicy") ))  # nosemgrep: unquoted-command-substitution-in-command -- arithmetic, not split
  n_urls=$((     n_urls     + $(count_type "$tpl" "AWS::Lambda::Url") ))  # nosemgrep: unquoted-command-substitution-in-command -- arithmetic, not split
  n_listeners=$((n_listeners + $(count_type "$tpl" "AWS::ElasticLoadBalancingV2::Listener") ))  # nosemgrep: unquoted-command-substitution-in-command -- arithmetic, not split
  n_rest=$((     n_rest     + $(count_type "$tpl" "AWS::ApiGateway::Method") ))  # nosemgrep: unquoted-command-substitution-in-command -- arithmetic, not split
  n_http=$((     n_http     + $(count_type "$tpl" "AWS::ApiGatewayV2::Route") ))  # nosemgrep: unquoted-command-substitution-in-command -- arithmetic, not split
  n_appsync=$((  n_appsync  + $(count_type "$tpl" "AWS::AppSync::GraphQLApi") ))  # nosemgrep: unquoted-command-substitution-in-command -- arithmetic, not split
  n_os=$((       n_os       + $(count_type "$tpl" "AWS::OpenSearchService::Domain") ))  # nosemgrep: unquoted-command-substitution-in-command -- arithmetic, not split
  n_lbs=$((      n_lbs      + $(count_type "$tpl" "AWS::ElasticLoadBalancingV2::LoadBalancer") ))  # nosemgrep: unquoted-command-substitution-in-command -- arithmetic, not split
  n_lbs=$((      n_lbs      + $(count_type "$tpl" "AWS::ElasticLoadBalancing::LoadBalancer") ))  # nosemgrep: unquoted-command-substitution-in-command -- arithmetic, not split
  n_rds=$((      n_rds      + $(count_type "$tpl" "AWS::RDS::DBInstance") ))  # nosemgrep: unquoted-command-substitution-in-command -- arithmetic, not split
  n_rds=$((      n_rds      + $(count_type "$tpl" "AWS::RDS::DBCluster") ))  # nosemgrep: unquoted-command-substitution-in-command -- arithmetic, not split

  # ---- GUARDRAIL #1: Cognito self-signup disabled --------------------------
  # Fail if any UserPool lacks AllowAdminCreateUserOnly == true (boolean true
  # OR the string "true", since CFN may render either).
  bad_pools="$(
    jq -r '
      (.Resources // {}) | to_entries[]
      | select(.value.Type == "AWS::Cognito::UserPool")
      | select(
          (.value.Properties.AdminCreateUserConfig.AllowAdminCreateUserOnly) as $a
          | ($a == true or $a == "true") | not
        )
      | .key
    ' "$tpl"
  )"
  if [[ -n "$bad_pools" ]]; then
    while IFS= read -r p; do
      note "[#1 Cognito] UserPool '$p' allows self-signup (AdminCreateUserConfig.AllowAdminCreateUserOnly != true)."
    done <<< "$bad_pools"
  fi

  # ---- GUARDRAIL #2a: every S3 bucket fully locks public access ------------
  # All four PublicAccessBlockConfiguration flags must be true.
  bad_buckets="$(
    jq -r '
      (.Resources // {}) | to_entries[]
      | select(.value.Type == "AWS::S3::Bucket")
      | .value.Properties.PublicAccessBlockConfiguration as $pab
      | select(
          ($pab == null)
          or ((($pab.BlockPublicAcls)       | (. == true or . == "true")) | not)
          or ((($pab.BlockPublicPolicy)      | (. == true or . == "true")) | not)
          or ((($pab.IgnorePublicAcls)       | (. == true or . == "true")) | not)
          or ((($pab.RestrictPublicBuckets)  | (. == true or . == "true")) | not)
        )
      | .key
    ' "$tpl"
  )"
  if [[ -n "$bad_buckets" ]]; then
    while IFS= read -r b; do
      note "[#2 S3] Bucket '$b' lacks a fully-locked PublicAccessBlockConfiguration (all four flags must be true)."
    done <<< "$bad_buckets"
  fi

  # ---- GUARDRAIL #2b: no wildcard-principal grant without an OAC condition --
  # Flag any BucketPolicy statement that is Effect Allow, has a wildcard
  # Principal ("*" or {AWS|Service:"*"}), grants ANY s3 action (or "*"), and
  # carries no SCOPING condition.
  #
  # Two points of the rule:
  #   (1) the action test covers every s3: action, `s3:*` and `Action: "*"`,
  #       not only s3:Get*;
  #   (2) "has a Condition" is not enough. A real OAC grant conditions on
  #       aws:SourceArn; a condition on aws:Referer restricts nothing an
  #       attacker cannot send. So the condition must mention SourceArn /
  #       SourceAccount / PrincipalOrgID / PrincipalArn to count as scoping.
  bad_policies="$(
    jq -r '
      def princ_wild($p):
        ($p == "*")
        or ($p == {"AWS": "*"})
        or (($p | type == "object") and (
              ((.AWS // empty) | if type=="array" then index("*") != null else . == "*" end)
              or ((.Service // empty) | if type=="array" then index("*") != null else . == "*" end)
            ));
      def s3ish: ascii_downcase | (. == "*" or startswith("s3:"));
      def is_s3_grant($a):
        ($a | type == "string" and s3ish)
        or ($a | type == "array" and (map(ascii_downcase | (. == "*" or startswith("s3:"))) | any));
      def scoped($c):
        ($c != null)
        and (($c | tostring | ascii_downcase)
             | (contains("sourcearn") or contains("sourceaccount")
                or contains("principalorgid") or contains("principalarn")));
      (.Resources // {}) | to_entries[]
      | select(.value.Type == "AWS::S3::BucketPolicy")
      | .key as $id
      | ( .value.Properties.PolicyDocument.Statement
          | if type == "array" then . else [.] end ) as $stmts
      | $stmts[]
      | select((.Effect // "Allow") == "Allow")
      | select(princ_wild(.Principal))
      | select(is_s3_grant(.Action))
      | select(scoped(.Condition) | not)
      | $id
    ' "$tpl"
  )"
  if [[ -n "$bad_policies" ]]; then
    while IFS= read -r bp; do
      note "[#2 S3] BucketPolicy '$bp' grants s3 access to a wildcard Principal with no aws:SourceArn/SourceAccount/OrgID condition (a Referer-style condition does not count)."
    done <<< "$bad_policies"
  fi

  # ---- GUARDRAIL #3: no Lambda Function URL --------------------------------
  bad_urls="$(
    jq -r '
      (.Resources // {}) | to_entries[]
      | select(.value.Type == "AWS::Lambda::Url")
      | .key
    ' "$tpl"
  )"
  if [[ -n "$bad_urls" ]]; then
    while IFS= read -r u; do
      note "[#3 Lambda] Function URL resource '$u' is forbidden — invoke Lambdas via AppSync/API/events behind auth."
    done <<< "$bad_urls"
  fi

  # ---- GUARDRAIL #4a: no internet-facing load balancer (v2 AND classic) ----
  bad_albs="$(
    jq -r '
      (.Resources // {}) | to_entries[]
      | select(.value.Type == "AWS::ElasticLoadBalancingV2::LoadBalancer"
               or .value.Type == "AWS::ElasticLoadBalancing::LoadBalancer")
      | select((.value.Properties.Scheme // "") == "internet-facing")
      | .key
    ' "$tpl"
  )"
  if [[ -n "$bad_albs" ]]; then
    while IFS= read -r lb; do
      note "[#4 ELB] LoadBalancer '$lb' is internet-facing — only internal, HTTPS-only load balancers are allowed."
    done <<< "$bad_albs"
  fi

  # ---- GUARDRAIL #4b: no listener on port 80 (v2 AND classic) -------------
  bad_listeners="$(
    jq -r '
      (.Resources // {}) | to_entries[]
      | select(.value.Type == "AWS::ElasticLoadBalancingV2::Listener")
      | (.value.Properties.Port) as $port
      | select($port == 80 or $port == "80")
      | .key
    ' "$tpl"
  )"
  if [[ -n "$bad_listeners" ]]; then
    while IFS= read -r ls; do
      note "[#4 ELB] Listener '$ls' is on port 80 — plaintext HTTP ingress is forbidden (HTTPS only)."
    done <<< "$bad_listeners"
  fi
  bad_classic_listeners="$(
    jq -r '
      (.Resources // {}) | to_entries[]
      | select(.value.Type == "AWS::ElasticLoadBalancing::LoadBalancer")
      | .key as $id
      | ( .value.Properties.Listeners // [] )[]
      | select((.LoadBalancerPort) as $p | $p == 80 or $p == "80")
      | $id
    ' "$tpl"
  )"
  if [[ -n "$bad_classic_listeners" ]]; then
    while IFS= read -r ls; do
      note "[#4 ELB] Classic LoadBalancer '$ls' has a :80 listener — plaintext HTTP ingress is forbidden."
    done <<< "$bad_classic_listeners"
  fi
  # Plaintext on a non-80 port behind an INTERNAL load balancer is inside the VPC,
  # so it is not a public bypass — surfaced for review, not failed.
  http_listeners="$(
    jq -r '
      (.Resources // {}) | to_entries[]
      | select(.value.Type == "AWS::ElasticLoadBalancingV2::Listener")
      | select((.value.Properties.Protocol // "") == "HTTP")
      | select(((.value.Properties.Port) as $p | $p == 80 or $p == "80") | not)
      | .key
    ' "$tpl"
  )"
  if [[ -n "$http_listeners" ]]; then
    while IFS= read -r ls; do
      review "[#4 ELB] Listener '$ls' speaks plaintext HTTP on a non-80 port. Fine only if its load balancer is internal and fronted by CloudFront; make it HTTPS otherwise."
    done <<< "$http_listeners"
  fi

  # ---- GUARDRAIL #5a: no anonymous API Gateway REST method -----------------
  # An AWS::ApiGateway::Method with AuthorizationType "NONE" is an unauthenticated
  # public entry point into the backend/data plane. (OPTIONS = CORS preflight is
  # exempt — it carries no data and AWS requires it to be NONE.)
  bad_rest="$(
    jq -r '
      (.Resources // {}) | to_entries[]
      | select(.value.Type == "AWS::ApiGateway::Method")
      | select((.value.Properties.HttpMethod // "") != "OPTIONS")
      | select((.value.Properties.AuthorizationType // "NONE") == "NONE")
      | .key
    ' "$tpl"
  )"
  if [[ -n "$bad_rest" ]]; then
    while IFS= read -r m; do
      note "[#5 Auth-bypass] API Gateway method '$m' has AuthorizationType NONE — a public, unauthenticated path to the backend. Put it behind COGNITO_USER_POOLS / AWS_IAM / a CUSTOM authorizer."
    done <<< "$bad_rest"
  fi

  # ---- GUARDRAIL #5b: no anonymous HTTP-API (v2) route ---------------------
  bad_http="$(
    jq -r '
      (.Resources // {}) | to_entries[]
      | select(.value.Type == "AWS::ApiGatewayV2::Route")
      | select((.value.Properties.AuthorizationType // "NONE") == "NONE")
      | select((.value.Properties.AuthorizerId // null) == null)
      | .key
    ' "$tpl"
  )"
  if [[ -n "$bad_http" ]]; then
    while IFS= read -r r; do
      note "[#5 Auth-bypass] HTTP-API route '$r' has AuthorizationType NONE and no authorizer — an open public route. Attach a JWT / IAM / Lambda authorizer."
    done <<< "$bad_http"
  fi

  # ---- GUARDRAIL #5c: no RDS instance OR cluster publicly accessible -------
  bad_rds="$(
    jq -r '
      (.Resources // {}) | to_entries[]
      | select(.value.Type == "AWS::RDS::DBInstance" or .value.Type == "AWS::RDS::DBCluster")
      | (.value.Properties.PubliclyAccessible) as $p
      | select($p == true or $p == "true")
      | .key
    ' "$tpl"
  )"
  if [[ -n "$bad_rds" ]]; then
    while IFS= read -r db; do
      note "[#5 Auth-bypass] RDS resource '$db' is PubliclyAccessible — the data store must be private (VPC-only), reached through the app's auth layer."
    done <<< "$bad_rds"
  fi

  # ---- GUARDRAIL #5d: no AppSync API_KEY authorization ---------------------
  # An API key is a shared secret shipped in the SPA bundle, not user auth: it
  # gives any holder standing access to the data plane. USER_POOL / AWS_IAM /
  # OPENID_CONNECT / AWS_LAMBDA are the acceptable modes.
  bad_appsync="$(
    jq -r '
      (.Resources // {}) | to_entries[]
      | select(.value.Type == "AWS::AppSync::GraphQLApi")
      | select(
          ((.value.Properties.AuthenticationType // "") == "API_KEY")
          or ((.value.Properties.AdditionalAuthenticationProviders // [])
              | map(.AuthenticationType // "") | index("API_KEY") != null)
        )
      | .key
    ' "$tpl"
  )"
  if [[ -n "$bad_appsync" ]]; then
    while IFS= read -r a; do
      note "[#5 Auth-bypass] AppSync API '$a' accepts API_KEY auth (default or additional provider) — an API key in a browser bundle is a standing public credential, not authentication. Use USER_POOL / AWS_IAM / OIDC / Lambda auth."
    done <<< "$bad_appsync"
  fi
  # OPT-IN extra strictness: GUARDRAILS_STRICT_APPSYNC=1 requires AppSync to be
  # Cognito-ONLY (no additional providers at all, not even AWS_IAM). A flag rather
  # than a fork, so one version of the script serves both rules.
  if [[ "${GUARDRAILS_STRICT_APPSYNC:-0}" = "1" ]]; then
    strict_appsync="$(
      jq -r '
        (.Resources // {}) | to_entries[]
        | select(.value.Type == "AWS::AppSync::GraphQLApi")
        | select(
            (.value.Properties.AuthenticationType != "AMAZON_COGNITO_USER_POOLS")
            or (((.value.Properties.AdditionalAuthenticationProviders // []) | length) != 0)
          )
        | .key
      ' "$tpl"
    )"
    if [[ -n "$strict_appsync" ]]; then
      while IFS= read -r a; do
        note "[#5 strict] AppSync API '$a' is not Cognito-only (GUARDRAILS_STRICT_APPSYNC=1)."
      done <<< "$strict_appsync"
    fi
  fi

  bad_apikeys="$(
    jq -r '
      (.Resources // {}) | to_entries[]
      | select(.value.Type == "AWS::AppSync::ApiKey")
      | .key
    ' "$tpl"
  )"
  if [[ -n "$bad_apikeys" ]]; then
    while IFS= read -r k; do
      note "[#5 Auth-bypass] AppSync ApiKey resource '$k' exists — remove it; the API is Cognito/IAM-authorized."
    done <<< "$bad_apikeys"
  fi

  # ---- GUARDRAIL #5e: no anonymous OpenSearch access policy ---------------
  # A domain OUTSIDE a VPC is allowed when it is IAM/FGAC-authorized (that is
  # authenticated ingress). What is forbidden is an access policy that allows a
  # wildcard principal with no condition — genuinely anonymous data-plane access.
  bad_os="$(
    jq -r '
      def princ_wild($p):
        ($p == "*")
        or ($p == {"AWS": "*"})
        or (($p | type == "object") and (
              ((.AWS // empty) | if type=="array" then index("*") != null else . == "*" end)
            ));
      (.Resources // {}) | to_entries[]
      | select(.value.Type == "AWS::OpenSearchService::Domain"
               or .value.Type == "AWS::Elasticsearch::Domain")
      | .key as $id
      | ( .value.Properties.AccessPolicies.Statement // []
          | if type == "array" then . else [.] end ) as $stmts
      | $stmts[]
      | select((.Effect // "Allow") == "Allow")
      | select(princ_wild(.Principal))
      | select((.Condition // null) == null)
      | $id
    ' "$tpl"
  )"
  if [[ -n "$bad_os" ]]; then
    while IFS= read -r d; do
      note "[#5 Auth-bypass] OpenSearch domain '$d' has an access policy allowing a wildcard Principal with no condition — anonymous access to the data plane. Require IAM/FGAC, or put the domain in a VPC."
    done <<< "$bad_os"
  fi
  # ---- IAM hygiene (tripwire) ---------------------------------------------
  # Not one of the five, but it lives in the same synthesized templates and is the
  # cheapest place to catch it. The sample's stacks generate none of these; the
  # check fires when a stack widens a workload role.
  #
  #   - Action "*"                     -> a role that can do anything
  #   - Action "*" AND Resource "*"     -> ... to everything
  #   - iam:PassRole on "*" uncondtioned -> privilege escalation to any role
  #
  # `s3:GetObject*`-style PREFIX wildcards are normal CDK output and are NOT
  # flagged; a service-wide `s3:*` is reported as REVIEW, not failed.
  bad_iam="$(
    jq -r '
      def stmts($p):
        (($p.PolicyDocument.Statement // empty) | if type=="array" then . else [.] end),
        (($p.Policies // [])[] | (.PolicyDocument.Statement // empty) | if type=="array" then . else [.] end);
      def arr($x): if ($x|type) == "array" then $x else [$x] end;
      (.Resources // {}) | to_entries[]
      | select(.value.Type == "AWS::IAM::Policy" or .value.Type == "AWS::IAM::Role"
               or .value.Type == "AWS::IAM::ManagedPolicy")
      | .key as $id
      | stmts(.value.Properties)
      | .[]
      | select((.Effect // "Allow") == "Allow")
      | . as $s
      | (arr($s.Action) | map(select(type == "string"))) as $acts
      | (arr($s.Resource) | map(select(type == "string"))) as $ress
      | if ($acts | index("*")) then
          "\($id)|star-action"
        elif (($acts | map(ascii_downcase) | index("iam:passrole"))
              and ($ress | index("*")) and (($s.Condition // null) == null)) then
          "\($id)|passrole-star"
        else empty end
    ' "$tpl"
  )"
  if [[ -n "$bad_iam" ]]; then
    while IFS= read -r entry; do
      rid="${entry%%|*}"; kind="${entry##*|}"
      case "$kind" in
        star-action)   note "[IAM] '$rid' grants Action \"*\" — a workload role must enumerate its actions." ;;
        passrole-star) note "[IAM] '$rid' grants iam:PassRole on \"*\" with no condition — that is privilege escalation to any role. Scope it to the sample's roles." ;;
      esac
    done <<< "$bad_iam"
  fi
  wide_iam="$(
    jq -r '
      def stmts($p):
        (($p.PolicyDocument.Statement // empty) | if type=="array" then . else [.] end),
        (($p.Policies // [])[] | (.PolicyDocument.Statement // empty) | if type=="array" then . else [.] end);
      def arr($x): if ($x|type) == "array" then $x else [$x] end;
      (.Resources // {}) | to_entries[]
      | select(.value.Type == "AWS::IAM::Policy" or .value.Type == "AWS::IAM::Role"
               or .value.Type == "AWS::IAM::ManagedPolicy")
      | .key as $id
      | stmts(.value.Properties)
      | .[]
      | select((.Effect // "Allow") == "Allow")
      | (arr(.Action) | map(select(type == "string"))) as $acts
      | select($acts | map(test("^[a-z0-9-]+:\\*$")) | any)
      | $id
    ' "$tpl"
  )"
  if [[ -n "$wide_iam" ]]; then
    while IFS= read -r rid; do
      review "[IAM] '$rid' grants a service-wide wildcard (e.g. \"s3:*\"). Acceptable scoped to one resource in a sandbox account; narrow it for anything shared."
    done <<< "$wide_iam"
  fi

  # Informational: a public-endpoint domain is legitimate only when IAM/FGAC gates
  # it. Say so, so "no VPC" is a decision rather than an oversight.
  public_os="$(
    jq -r '
      (.Resources // {}) | to_entries[]
      | select(.value.Type == "AWS::OpenSearchService::Domain"
               or .value.Type == "AWS::Elasticsearch::Domain")
      | select((.value.Properties.VPCOptions // null) == null)
      | .key
    ' "$tpl"
  )"
  if [[ -n "$public_os" ]]; then
    while IFS= read -r d; do
      review "[#5] OpenSearch domain '$d' has no VPCOptions (public endpoint). Allowed only because IAM/FGAC authorizes it — confirm that, or move it into the VPC."
    done <<< "$public_os"
  fi
done

echo
echo "Examined: ${#TEMPLATES[@]} template(s) · ${n_pools} UserPool(s) · ${n_buckets} Bucket(s)/${n_policies} BucketPolicy(ies) ·"
echo "          ${n_urls} Lambda Url(s) · ${n_lbs} LoadBalancer(s)/${n_listeners} Listener(s) ·"
echo "          ${n_rest} REST method(s)/${n_http} HTTP route(s) · ${n_appsync} AppSync API(s) ·"
echo "          ${n_rds} RDS resource(s) · ${n_os} OpenSearch domain(s)"
echo "          (a PASS over zero resources is not a PASS over your resources — check these counts)"

if [[ $violations -gt 0 ]]; then
  echo "GUARDRAIL CHECK FAILED (v$GUARDRAILS_VERSION): $violations violation(s) found. Deploy aborted." >&2
  exit 1
fi

echo "GUARDRAIL CHECK PASSED (v$GUARDRAILS_VERSION): all five guardrails satisfied."
echo "REMINDER: this gate reads CloudFormation only. App-code auth bypasses are invisible to it."
exit 0
