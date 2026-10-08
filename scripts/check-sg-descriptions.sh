#!/usr/bin/env bash
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# Fails when a synthesized security group or rule description uses characters
# EC2 rejects at deploy time (allowed: a-zA-Z0-9. _-:/()#,@[]+=&;{}!$*).
set -euo pipefail
dir="${1:-infra/cdk.out}"
bad=$(jq -r '
  .Resources | to_entries[]
  | select(.value.Type | test("^AWS::EC2::SecurityGroup(Ingress|Egress)?$"))
  | [ .key, (.value.Properties.GroupDescription // empty), (.value.Properties.Description // empty),
      ((.value.Properties.SecurityGroupIngress // [])[] | .Description // empty),
      ((.value.Properties.SecurityGroupEgress // [])[] | .Description // empty) ]
  | .[0] as $id | .[1:][] | select(type == "string") | select(test("[^a-zA-Z0-9. _:/()#,@\\[\\]+=&;{}!$*-]")) | "\($id): \(.)"
' "$dir"/*.template.json)
if [ -n "$bad" ]; then echo "Invalid EC2 security group descriptions:"; echo "$bad"; exit 1; fi
echo "security group descriptions OK"
