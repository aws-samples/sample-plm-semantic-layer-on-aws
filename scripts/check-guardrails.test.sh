#!/usr/bin/env bash
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# =============================================================================
# check-guardrails.test.sh — prove the gate GATES.
#
# A gate you have only ever seen pass is not a gate. This feeds
# check-guardrails.sh synthetic CloudFormation containing one breach at a time
# and asserts it exits non-zero, plus negative controls (secure patterns that
# must NOT trip). Run it after editing the gate, and in CI.
#
#   bash scripts/check-guardrails.test.sh
#
# Exit 0 = the gate correctly caught every breach and passed every control.
# =============================================================================
set -uo pipefail

GATE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/check-guardrails.sh"
[ -f "$GATE" ] || { echo "FATAL: gate not found at $GATE" >&2; exit 2; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
pass=0 fail=0

# expect <expected-exit> <case-name> <<< template-json
#
# Each case gets its OWN directory. That matters twice: the gate recurses, so a
# shared dir would let one case's breach template fail an unrelated case; and
# splitting these `local` declarations is required because bash expands every RHS
# in a single `local` statement before the names exist (`local a=$1 b=$a` sees an
# unbound $a under `set -u`).
expect() {
  local want="$1"
  local name="$2"
  local dir
  dir="$WORK/$(printf '%s' "$name" | tr -c 'a-zA-Z0-9' '_')"
  mkdir -p "$dir"
  cat > "$dir/Test.template.json"
  bash "$GATE" "$dir" >/dev/null 2>&1
  local got=$?
  if [ "$got" -eq "$want" ]; then
    pass=$((pass + 1)); printf '  ✓ %s (exit %s)\n' "$name" "$got"
  else
    fail=$((fail + 1)); printf '  ✗ %s — expected exit %s, got %s\n' "$name" "$want" "$got"
  fi
}

echo "Proving the gate catches breaches (expect exit 1):"

expect 1 "#1 cognito self-signup" <<'JSON'
{"Resources":{"Pool":{"Type":"AWS::Cognito::UserPool","Properties":{
  "AdminCreateUserConfig":{"AllowAdminCreateUserOnly":false}}}}}
JSON

expect 1 "#1 cognito no AdminCreateUserConfig" <<'JSON'
{"Resources":{"Pool":{"Type":"AWS::Cognito::UserPool","Properties":{}}}}
JSON

expect 1 "#2a bucket without public-access block" <<'JSON'
{"Resources":{"B":{"Type":"AWS::S3::Bucket","Properties":{}}}}
JSON

expect 1 "#2a bucket with partial block" <<'JSON'
{"Resources":{"B":{"Type":"AWS::S3::Bucket","Properties":{"PublicAccessBlockConfiguration":{
  "BlockPublicAcls":true,"IgnorePublicAcls":true,"BlockPublicPolicy":false,"RestrictPublicBuckets":true}}}}}
JSON

expect 1 "#2b wildcard Action:* to Principal:*" <<'JSON'
{"Resources":{"P":{"Type":"AWS::S3::BucketPolicy","Properties":{"PolicyDocument":{"Statement":[
  {"Effect":"Allow","Principal":"*","Action":"*","Resource":"arn:aws:s3:::b/*"}]}}}}}
JSON

expect 1 "#2b s3:* to Principal:*" <<'JSON'
{"Resources":{"P":{"Type":"AWS::S3::BucketPolicy","Properties":{"PolicyDocument":{"Statement":[
  {"Effect":"Allow","Principal":{"AWS":"*"},"Action":["s3:*"],"Resource":"arn:aws:s3:::b/*"}]}}}}}
JSON

expect 1 "#2b Referer condition is not scoping" <<'JSON'
{"Resources":{"P":{"Type":"AWS::S3::BucketPolicy","Properties":{"PolicyDocument":{"Statement":[
  {"Effect":"Allow","Principal":"*","Action":"s3:GetObject","Resource":"arn:aws:s3:::b/*",
   "Condition":{"StringEquals":{"aws:Referer":"secret"}}}]}}}}}
JSON

expect 1 "#3 lambda function url" <<'JSON'
{"Resources":{"U":{"Type":"AWS::Lambda::Url","Properties":{"AuthType":"NONE"}}}}
JSON

expect 1 "#4a internet-facing ALB" <<'JSON'
{"Resources":{"LB":{"Type":"AWS::ElasticLoadBalancingV2::LoadBalancer","Properties":{"Scheme":"internet-facing"}}}}
JSON

expect 1 "#4a internet-facing CLASSIC ELB" <<'JSON'
{"Resources":{"LB":{"Type":"AWS::ElasticLoadBalancing::LoadBalancer","Properties":{"Scheme":"internet-facing"}}}}
JSON

expect 1 "#4b listener on :80" <<'JSON'
{"Resources":{"L":{"Type":"AWS::ElasticLoadBalancingV2::Listener","Properties":{"Port":80,"Protocol":"HTTP"}}}}
JSON

expect 1 "#4b CLASSIC :80 listener" <<'JSON'
{"Resources":{"LB":{"Type":"AWS::ElasticLoadBalancing::LoadBalancer","Properties":{"Scheme":"internal",
  "Listeners":[{"LoadBalancerPort":80,"InstancePort":8080,"Protocol":"HTTP"}]}}}}
JSON

expect 1 "#5a anonymous REST method" <<'JSON'
{"Resources":{"M":{"Type":"AWS::ApiGateway::Method","Properties":{"HttpMethod":"POST","AuthorizationType":"NONE"}}}}
JSON

expect 1 "#5b anonymous HTTP-API route" <<'JSON'
{"Resources":{"R":{"Type":"AWS::ApiGatewayV2::Route","Properties":{"RouteKey":"POST /ingest","AuthorizationType":"NONE"}}}}
JSON

expect 1 "#5c publicly accessible RDS instance" <<'JSON'
{"Resources":{"D":{"Type":"AWS::RDS::DBInstance","Properties":{"PubliclyAccessible":true}}}}
JSON

expect 1 "#5c publicly accessible RDS CLUSTER" <<'JSON'
{"Resources":{"D":{"Type":"AWS::RDS::DBCluster","Properties":{"PubliclyAccessible":true}}}}
JSON

expect 1 "#5d AppSync default API_KEY" <<'JSON'
{"Resources":{"A":{"Type":"AWS::AppSync::GraphQLApi","Properties":{"Name":"a","AuthenticationType":"API_KEY"}}}}
JSON

expect 1 "#5d AppSync additional API_KEY provider" <<'JSON'
{"Resources":{"A":{"Type":"AWS::AppSync::GraphQLApi","Properties":{"Name":"a",
  "AuthenticationType":"AMAZON_COGNITO_USER_POOLS",
  "AdditionalAuthenticationProviders":[{"AuthenticationType":"API_KEY"}]}}}}
JSON

expect 1 "#5d AppSync ApiKey resource" <<'JSON'
{"Resources":{"K":{"Type":"AWS::AppSync::ApiKey","Properties":{"ApiId":"x"}}}}
JSON

expect 1 "#5e anonymous OpenSearch access policy" <<'JSON'
{"Resources":{"O":{"Type":"AWS::OpenSearchService::Domain","Properties":{"AccessPolicies":{"Statement":[
  {"Effect":"Allow","Principal":{"AWS":"*"},"Action":"es:*","Resource":"arn:aws:es:::domain/d/*"}]}}}}}
JSON

expect 1 "IAM Action:* in a workload role" <<'JSON'
{"Resources":{"P":{"Type":"AWS::IAM::Policy","Properties":{"PolicyDocument":{"Statement":[
  {"Effect":"Allow","Action":"*","Resource":"arn:aws:s3:::b/*"}]}}}}}
JSON

expect 1 "IAM unconditioned iam:PassRole on *" <<'JSON'
{"Resources":{"P":{"Type":"AWS::IAM::Policy","Properties":{"PolicyDocument":{"Statement":[
  {"Effect":"Allow","Action":["iam:PassRole"],"Resource":"*"}]}}}}}
JSON

echo
echo "Negative controls — secure patterns that must PASS (expect exit 0):"

expect 0 "OAC bucket policy with SourceArn" <<'JSON'
{"Resources":{
 "B":{"Type":"AWS::S3::Bucket","Properties":{"PublicAccessBlockConfiguration":{
   "BlockPublicAcls":true,"IgnorePublicAcls":true,"BlockPublicPolicy":true,"RestrictPublicBuckets":true}}},
 "P":{"Type":"AWS::S3::BucketPolicy","Properties":{"PolicyDocument":{"Statement":[
   {"Effect":"Allow","Principal":{"Service":"cloudfront.amazonaws.com"},"Action":"s3:GetObject",
    "Resource":"arn:aws:s3:::b/*",
    "Condition":{"StringEquals":{"AWS:SourceArn":"arn:aws:cloudfront::1:distribution/E1"}}}]}}}}}
JSON

expect 0 "enforceSSL Deny with Principal:*" <<'JSON'
{"Resources":{
 "B":{"Type":"AWS::S3::Bucket","Properties":{"PublicAccessBlockConfiguration":{
   "BlockPublicAcls":true,"IgnorePublicAcls":true,"BlockPublicPolicy":true,"RestrictPublicBuckets":true}}},
 "P":{"Type":"AWS::S3::BucketPolicy","Properties":{"PolicyDocument":{"Statement":[
   {"Effect":"Deny","Principal":"*","Action":"s3:*","Resource":"arn:aws:s3:::b/*",
    "Condition":{"Bool":{"aws:SecureTransport":"false"}}}]}}}}}
JSON

expect 0 "internal HTTPS ALB" <<'JSON'
{"Resources":{
 "LB":{"Type":"AWS::ElasticLoadBalancingV2::LoadBalancer","Properties":{"Scheme":"internal"}},
 "L":{"Type":"AWS::ElasticLoadBalancingV2::Listener","Properties":{"Port":443,"Protocol":"HTTPS"}}}}
JSON

expect 0 "CORS OPTIONS method may be NONE" <<'JSON'
{"Resources":{"M":{"Type":"AWS::ApiGateway::Method","Properties":{"HttpMethod":"OPTIONS","AuthorizationType":"NONE"}}}}
JSON

expect 0 "authorized HTTP-API route" <<'JSON'
{"Resources":{"R":{"Type":"AWS::ApiGatewayV2::Route","Properties":{"RouteKey":"POST /x",
  "AuthorizationType":"JWT","AuthorizerId":"abc"}}}}
JSON

expect 0 "AppSync with USER_POOL + IAM only" <<'JSON'
{"Resources":{"A":{"Type":"AWS::AppSync::GraphQLApi","Properties":{"Name":"a",
  "AuthenticationType":"AMAZON_COGNITO_USER_POOLS",
  "AdditionalAuthenticationProviders":[{"AuthenticationType":"AWS_IAM"}]}}}}
JSON

expect 0 "IAM prefix wildcard (s3:GetObject*) is normal CDK output" <<'JSON'
{"Resources":{"P":{"Type":"AWS::IAM::Policy","Properties":{"PolicyDocument":{"Statement":[
  {"Effect":"Allow","Action":["s3:GetObject*","s3:GetBucket*","s3:List*"],"Resource":"arn:aws:s3:::b/*"}]}}}}}
JSON

expect 0 "IAM conditioned PassRole is fine" <<'JSON'
{"Resources":{"P":{"Type":"AWS::IAM::Policy","Properties":{"PolicyDocument":{"Statement":[
  {"Effect":"Allow","Action":"iam:PassRole","Resource":"*",
   "Condition":{"StringEquals":{"iam:PassedToService":"lambda.amazonaws.com"}}}]}}}}}
JSON

expect 0 "AppSync USER_POOL+IAM passes by DEFAULT (strict flag off)" <<'JSON'
{"Resources":{"A":{"Type":"AWS::AppSync::GraphQLApi","Properties":{"Name":"a",
  "AuthenticationType":"AMAZON_COGNITO_USER_POOLS",
  "AdditionalAuthenticationProviders":[{"AuthenticationType":"AWS_IAM"}]}}}}
JSON

expect 0 "IAM-authorized OpenSearch (conditioned wildcard)" <<'JSON'
{"Resources":{"O":{"Type":"AWS::OpenSearchService::Domain","Properties":{
  "VPCOptions":{"SubnetIds":["subnet-1"]},
  "AccessPolicies":{"Statement":[{"Effect":"Allow","Principal":{"AWS":"*"},"Action":"es:ESHttpGet",
    "Resource":"arn:aws:es:::domain/d/*","Condition":{"IpAddress":{"aws:SourceIp":"10.0.0.0/8"}}}]}}}}}
JSON

# The opt-in strictness flag must actually tighten: the SAME template that passes
# by default must fail with GUARDRAILS_STRICT_APPSYNC=1. A flag nobody proves is a
# flag that silently does nothing.
STRICT="$WORK/strict"
mkdir -p "$STRICT"
cat > "$STRICT/Test.template.json" <<'JSON'
{"Resources":{"A":{"Type":"AWS::AppSync::GraphQLApi","Properties":{"Name":"a",
  "AuthenticationType":"AMAZON_COGNITO_USER_POOLS",
  "AdditionalAuthenticationProviders":[{"AuthenticationType":"AWS_IAM"}]}}}}
JSON
GUARDRAILS_STRICT_APPSYNC=1 bash "$GATE" "$STRICT" >/dev/null 2>&1
if [ $? -eq 1 ]; then
  pass=$((pass + 1)); echo "  ✓ GUARDRAILS_STRICT_APPSYNC=1 rejects a non-Cognito-only API (exit 1)"
else
  fail=$((fail + 1)); echo "  ✗ GUARDRAILS_STRICT_APPSYNC=1 did not tighten anything"
fi

# Recursion: a Stages app writes templates to cdk.out/assembly-*/ . A breach there
# must still be caught (a -maxdepth 1 search would find nothing and mis-report).
NESTED="$WORK/nested/assembly-Stage/deep"
mkdir -p "$NESTED"
cat > "$NESTED/Nested.template.json" <<'JSON'
{"Resources":{"U":{"Type":"AWS::Lambda::Url","Properties":{"AuthType":"NONE"}}}}
JSON
bash "$GATE" "$WORK/nested" >/dev/null 2>&1
if [ $? -eq 1 ]; then
  pass=$((pass + 1)); echo "  ✓ finds a breach in a nested assembly-*/ dir (exit 1)"
else
  fail=$((fail + 1)); echo "  ✗ MISSED a breach in a nested assembly-*/ dir — is the find recursive?"
fi

# Usage errors must be exit 2, distinct from a violation.
bash "$GATE" "$WORK/does-not-exist" >/dev/null 2>&1
[ $? -eq 2 ] && { pass=$((pass+1)); echo "  ✓ missing cdk.out dir exits 2"; } \
             || { fail=$((fail+1)); echo "  ✗ missing cdk.out dir should exit 2"; }

mkdir -p "$WORK/empty"
bash "$GATE" "$WORK/empty" >/dev/null 2>&1
[ $? -eq 2 ] && { pass=$((pass+1)); echo "  ✓ empty cdk.out exits 2 (never a silent pass)"; } \
             || { fail=$((fail+1)); echo "  ✗ empty cdk.out should exit 2, NOT pass"; }

echo
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ] || exit 1
echo "The gate gates."
