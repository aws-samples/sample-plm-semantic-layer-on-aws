#!/usr/bin/env bash
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# Asserts the CloudFront distribution shape the export-control policy relies on,
# on the synthesized templates (run after `cdk synth`, before `cdk deploy`):
#
#   1. no `/cad/*` cache behaviour: CAD files reach the browser only as
#      presigned S3 URLs the query service issues for visible parts;
#   2. the `/api/*` and `/agent/*` behaviours each exist exactly once and
#      forward the x-atelier-profile viewer header to the origin, through the
#      managed AllViewerExceptHostHeader policy or a custom OriginRequestPolicy
#      in the same template that lets the header through;
#   3. the sign-in gate: every behaviour, the default one included, runs a
#      viewer-request Lambda@Edge function, so the Cognito sign-in gates the site,
#      /api/* and /agent/*; the `/api/*` behaviour allows POST (the officer's demo
#      controls and the MCP endpoint);
#   4. no load balancer listener on port 80 in any template: the agent's ALB is
#      reached only through the CloudFront VPC origin, on 8080;
#   5. the CAD deployments store the STEP files gzip-encoded (Content-Encoding
#      gzip, Content-Type model/step), so a presigned download moves a fifth of
#      the bytes and the browser decodes it as it reads, and each product's
#      bounds.json gzip-encoded as application/json; each deployment excludes the
#      other's files and prunes, so neither deletes the other's objects;
#   6. the Ontop task sizes: the core endpoint, which answers every profile's
#      tag and membership requests over every product, at 1 vCPU and 2 GiB with
#      a 1536 MiB heap, the four PLM endpoints at 0.5 vCPU and 1 GiB on the
#      image's default heap;
#   7. the agent's task prices its turns: PRICE_PER_1K_INPUT 0.0022 and
#      PRICE_PER_1K_OUTPUT 0.011 USD, Claude Sonnet 5 on Bedrock in eu-west-1.
#
#   check-web-behaviours.sh [cdk.out-dir]   # default: infra/cdk.out
#
# Exit codes: 0 pass . 1 assertion failed . 2 no distribution found / usage.
set -euo pipefail

dir="infra/cdk.out"
while [ "$#" -gt 0 ]; do
  case "$1" in
    -*) echo "usage: check-web-behaviours.sh [cdk.out-dir]" >&2; exit 2 ;;
    *) dir="$1"; shift ;;
  esac
done
[ -d "$dir" ] || { echo "FATAL: '$dir' not found. Run 'cdk synth' first." >&2; exit 2; }

# Managed policy id of Managed-AllViewerExceptHostHeader.
ALL_VIEWER_EXCEPT_HOST="b689b0a8-53d0-40ab-baf2-68738e2966ac"
HEADER="x-atelier-profile"

templates=()
while IFS= read -r -d '' f; do templates+=("$f"); done \
  < <(find "$dir" -type f -name '*.template.json' -print0)

# Resolves a behaviour's origin request policy: a literal managed id, or a Ref
# to a custom AWS::CloudFront::OriginRequestPolicy in the same template.
forwards_header() {
  local tpl="$1" id="$2" pattern="$3"
  jq -r --arg id "$id" --arg pattern "$pattern" --arg managed "$ALL_VIEWER_EXCEPT_HOST" --arg h "$HEADER" '
    . as $root
    | ((.Resources[$id].Properties.DistributionConfig.CacheBehaviors // []) | map(select(.PathPattern == $pattern))) as $b
    | $b[0].OriginRequestPolicyId as $p
    | (if ($p | type) == "object" then $p.Ref else null end) as $ref
    | (if $ref != null then $root.Resources[$ref].Properties.OriginRequestPolicyConfig.HeadersConfig else null end) as $hc
    | $hc.HeaderBehavior as $bh
    | ((($hc.Headers.Items // []) | map(ascii_downcase) | index($h)) != null) as $listed
    | if ($b | length) != 1 then "FAIL: expected exactly one \($pattern) behaviour, found \($b | length)"
      elif $p == null then "FAIL: \($pattern) has no OriginRequestPolicyId (viewer headers are not forwarded)"
      elif $p == $managed then "PASS: \($pattern) forwards all viewer headers (Managed-AllViewerExceptHostHeader)"
      elif $ref == null then "FAIL: \($pattern) uses origin request policy \($p | tojson), which is not known to forward \($h)"
      elif $hc == null then "FAIL: \($pattern) refers to \($ref), which is not an OriginRequestPolicy in this template"
      elif ($bh == "allViewer" or $bh == "allViewerAndWhitelistCloudFront")
           or ($bh == "whitelist" and $listed) or ($bh == "allExcept" and ($listed | not))
        then "PASS: \($pattern) forwards \($h) through custom policy \($ref)"
      else "FAIL: custom policy \($ref) does not forward \($h) (HeadersConfig: \($hc | tojson))"
      end' "$tpl"
}

# Every behaviour, the default one included, runs the sign-in function on viewer-request;
# one without it is reachable without signing in.
has_edge_auth() {
  local tpl="$1" id="$2"
  jq -r --arg id "$id" '
    .Resources[$id].Properties.DistributionConfig as $d
    | ([{PathPattern: "(default)", Associations: ($d.DefaultCacheBehavior.LambdaFunctionAssociations // [])}]
       + (($d.CacheBehaviors // []) | map({PathPattern, Associations: (.LambdaFunctionAssociations // [])}))) as $all
    | ($all | map(select((.Associations | map(select(.EventType == "viewer-request" and ((.LambdaFunctionARN // "") | tostring | length) > 0)) | length) == 0)) | map(.PathPattern)) as $missing
    | if ($missing | length) > 0
        then "FAIL: behaviour(s) without a viewer-request Lambda@Edge association (reachable without signing in): \($missing | join(" "))"
      else "PASS: all \($all | length) behaviours run the viewer-request sign-in function"
      end' "$tpl"
}

# The officer's demo controls and the MCP endpoint POST through /api/*: the behaviour must allow it.
allows_post() {
  local tpl="$1" id="$2" pattern="$3"
  jq -r --arg id "$id" --arg pattern "$pattern" '
    ((.Resources[$id].Properties.DistributionConfig.CacheBehaviors // []) | map(select(.PathPattern == $pattern))) as $b
    | if ($b | length) != 1 then "FAIL: expected exactly one \($pattern) behaviour, found \($b | length)"
      elif (($b[0].AllowedMethods // []) | index("POST")) != null then "PASS: \($pattern) allows POST (\($b[0].AllowedMethods | join(",")))"
      else "FAIL: \($pattern) does not allow POST (\(($b[0].AllowedMethods // []) | join(","))): every officer control and the MCP endpoint would answer 403 from CloudFront"
      end' "$tpl"
}

record() { echo "  $1"; case "$1" in FAIL*) failures=$((failures + 1)) ;; esac; }

distributions=0
failures=0
for tpl in "${templates[@]}"; do
  ids=$(jq -r '(.Resources // {}) | to_entries[] | select(.value.Type == "AWS::CloudFront::Distribution") | .key' "$tpl")
  [ -n "$ids" ] || continue
  while IFS= read -r id; do
    distributions=$((distributions + 1))
    echo "==> $(basename "$tpl") / $id"

    cad=$(jq -r --arg id "$id" '
      .Resources[$id].Properties.DistributionConfig.CacheBehaviors // []
      | map(select(.PathPattern | test("^/cad(/|$)"))) | .[].PathPattern' "$tpl")
    if [ -n "$cad" ]; then
      record "FAIL: cache behaviour(s) serve CAD paths: $cad"
    else
      record "PASS: no /cad/* cache behaviour"
    fi

    record "$(forwards_header "$tpl" "$id" '/api/*')"
    record "$(allows_post "$tpl" "$id" '/api/*')"
    record "$(forwards_header "$tpl" "$id" '/agent/*')"
    record "$(has_edge_auth "$tpl" "$id")"
  done <<< "$ids"
done

echo "==> load balancer listeners"
listeners=0
for tpl in "${templates[@]}"; do
  listeners=$((listeners + $(jq '[(.Resources // {}) | to_entries[] | select(.value.Type == "AWS::ElasticLoadBalancingV2::Listener")] | length' "$tpl")))
  on80=$(jq -r '(.Resources // {}) | to_entries[]
    | select(.value.Type == "AWS::ElasticLoadBalancingV2::Listener")
    | select((.value.Properties.Port | tostring) == "80") | .key' "$tpl")
  [ -z "$on80" ] || record "FAIL: $(basename "$tpl") has listener(s) on port 80: $(tr '\n' ' ' <<< "$on80")"
done
[ "$failures" -gt 0 ] || record "PASS: no :80 listener among $listeners listener(s)"

echo "==> CAD deployment"
cad_deployments=$(jq -s -r '[.[] | (.Resources // {}) | to_entries[]
  | select(.value.Type == "Custom::CDKBucketDeployment" and .value.Properties.DestinationBucketKeyPrefix == "cad/")
  | .value.Properties | {meta: (.SystemMetadata // {}), exclude: (.Exclude // []), include: (.Include // []), prune: (.Prune // true)}]
  | (map(select(.meta["content-type"] == "model/step")) | first) as $step
  | (map(select(.meta["content-type"] == "application/json")) | first) as $json
  | if length != 2 then "FAIL: expected two bucket deployments under cad/, found \(length)"
    elif $step == null or $json == null then "FAIL: the cad/ deployments are not one model/step and one application/json: \(map(.meta) | tojson)"
    elif ($step.meta["content-encoding"] != "gzip") or ($json.meta["content-encoding"] != "gzip") then "FAIL: the cad/ objects are not all stored gzip-encoded: \(map(.meta) | tojson)"
    elif ($step.exclude | index("*.json")) == null or $json.exclude != ["*"] or $json.include != ["*.json"] then
      "FAIL: the STEP deployment must exclude *.json and the bounds deployment include only *.json: \(map({exclude, include}) | tojson)"
    elif ($step.prune | tostring) != "true" or ($json.prune | tostring) != "true" then "FAIL: both cad/ deployments must prune"
    else "PASS: the STEP files are stored gzip-encoded as model/step and the bounds.json files as application/json" end' "${templates[@]}")
record "$cad_deployments"

echo "==> Ontop task sizes"
ontop_tasks=$(jq -s -r '[.[] | (.Resources // {}) | to_entries[] | select(.value.Type == "AWS::ECS::TaskDefinition") | .value.Properties
  | (.ContainerDefinitions[0]) as $c | ($c.LogConfiguration.Options["awslogs-stream-prefix"] // "") as $name
  | select($name | startswith("ontop-"))
  | {name: $name, cpu: (.Cpu | tostring), memory: (.Memory | tostring), heap: ([($c.Environment // [])[] | select(.Name == "ONTOP_JAVA_ARGS") | .Value] | first)}]
  | (map(select(.name == "ontop-core")) | first) as $core
  | map(select(.name != "ontop-core")) as $plms
  | if $core == null or ($plms | length) != 4 then "FAIL: expected ontop-core and four PLM Ontop tasks, found \(map(.name) | tojson)"
    elif $core.cpu != "1024" or $core.memory != "2048" or $core.heap != "-Xmx1536m" then "FAIL: ontop-core must run at 1024 CPU units and 2048 MiB with -Xmx1536m: \($core | tojson)"
    elif any($plms[]; .cpu != "512" or .memory != "1024" or .heap != null) then "FAIL: the PLM Ontop tasks must run at 512 CPU units and 1024 MiB on the default heap: \($plms | tojson)"
    else "PASS: ontop-core at 1 vCPU and 2 GiB with a 1536 MiB heap, the four PLM Ontop tasks at 0.5 vCPU and 1 GiB" end' "${templates[@]}")
record "$ontop_tasks"

echo "==> agent prices"
agent_prices=$(jq -s -r '[.[] | (.Resources // {}) | to_entries[] | select(.value.Type == "AWS::ECS::TaskDefinition") | .value.Properties.ContainerDefinitions[]
  | select((.LogConfiguration.Options["awslogs-stream-prefix"] // "") == "agent")
  | ((.Environment // []) | map({(.Name): .Value}) | add // {}) | {input: .PRICE_PER_1K_INPUT, output: .PRICE_PER_1K_OUTPUT}]
  | if length != 1 then "FAIL: expected one agent container, found \(length)"
    elif .[0].input != "0.0022" or .[0].output != "0.011" then "FAIL: the agent must price turns at 0.0022 USD input and 0.011 USD output per 1K tokens: \(.[0] | tojson)"
    else "PASS: the agent prices turns at 0.0022 USD input and 0.011 USD output per 1K tokens" end' "${templates[@]}")
record "$agent_prices"

if [ "$distributions" -eq 0 ]; then
  echo "FATAL: no AWS::CloudFront::Distribution under '$dir'; nothing was asserted." >&2
  exit 2
fi
if [ "$failures" -gt 0 ]; then
  echo "WEB BEHAVIOUR CHECK FAILED: $failures assertion(s) over $distributions distribution(s)." >&2
  exit 1
fi
echo "WEB BEHAVIOUR CHECK PASSED over $distributions distribution(s)."
