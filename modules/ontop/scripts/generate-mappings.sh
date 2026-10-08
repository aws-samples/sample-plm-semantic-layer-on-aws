#!/bin/sh
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# Regenerates modules/ontop/mappings/<code>.r2rml.ttl from the JPA entity annotations of each
# service (package atelier.plm.<code>.domain): the site PLMs (module plm-<code>) and the Atelier core
# (code core, module atelier-core), running Maven and the generator in a container.
# Usage: modules/ontop/scripts/generate-mappings.sh [code ...]    (default: fr de uk es core)
set -eu

ONTOP_DIR=$(cd "$(dirname "$0")/.." && pwd)
SERVICES_DIR=$(cd "$ONTOP_DIR/../plm-services" && pwd)
PLMS=${*:-fr de uk es core}
RUNTIME=$(command -v finch || command -v docker)
MAVEN_IMAGE=public.ecr.aws/docker/library/maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320

module_of() {
  case "$1" in
    core) echo atelier-core ;;
    *) echo "plm-$1" ;;
  esac
}

MODULES=$(for plm in $PLMS; do printf '%s,' "$(module_of "$plm")"; done)
MODULES=${MODULES%,}

"$RUNTIME" run --rm --name atelierontop-generate \
  -v atelierontop-m2:/root/.m2 \
  -v "$SERVICES_DIR":/repo:ro \
  -v "$ONTOP_DIR/mappings":/out \
  -e PLMS="$PLMS" -e MODULES="$MODULES" \
  "$MAVEN_IMAGE" sh -euc '
    cp -r /repo /src && cd /src
    mvn -B -q -pl "$MODULES" -am compile dependency:build-classpath \
      -Dmdep.includeScope=runtime -Dmdep.outputFile=target/classpath.txt
    for plm in $PLMS; do
      case "$plm" in core) module=atelier-core ;; *) module="plm-$plm" ;; esac
      java -cp "$module/target/classes:$(cat "$module/target/classpath.txt")" \
        atelier.plm.common.r2rml.R2rmlGenerator "$plm" "/out/$plm.r2rml.ttl"
    done'
