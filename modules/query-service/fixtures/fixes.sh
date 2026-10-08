#!/usr/bin/env bash
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# The release, pass, reset, fail cycle of every rule against the local fixture stack (Finch) with the services that
# own the facts: the query stack of compose.yaml plus the four PLM services, the Atelier core service and a gateway
# routing /fr/ /de/ /uk/ /es/ /core/ /query/ as the API Gateway does (compose.fixes.yaml). Generates the fixture,
# builds the query image and the PLM and core jars, starts the stack, loads the links and file-index graphs, waits for
# every service through the gateway, then runs tests/preview-cycle.mjs (each rule's proposal previewed, nothing written)
# and tests/fix-cycle.mjs with the links loader stood in for (each released
# correction's rows posted to /core/changes): for one seeded defect of each rule it builds the correction with the
# screen's proposals, releases it as the owning site's engineer, asserts the rule passes, resets as the officer and
# asserts it fails again, and ends with every product's failures equal to its seeded defects; then tests/smoke.mjs in
# its local mode (LOCAL_API, the gateway), every smoke check that needs no CloudFront, Cognito, S3 or event. Writes
# the cycle's log and the services' logs to out-fixes/ and removes the stack. Needs finch, python3, curl, node and Maven with JDK 21.
#   STACK         compose project name, default atelierfixes
#   LINKS_PORT    host port of the link store (default 18578), QUERY_PORT of the query service (18580),
#   GATEWAY_PORT  of the gateway (18590)
#   MVN           the Maven command and its arguments, split on spaces ("mvn.sh -o"), default mvn
set -euo pipefail
cd "$(dirname "$0")"
ROOT=$(cd ../../.. && pwd)
PGJDBC=42.7.13
STACK=${STACK:-atelierfixes}
LINKS_PORT=${LINKS_PORT:-18578} QUERY_PORT=${QUERY_PORT:-18580} GATEWAY_PORT=${GATEWAY_PORT:-18590}
QUERY_IMAGE=$STACK-svc:local
read -r -a MVN <<< "${MVN:-mvn}"
LINKS=http://127.0.0.1:$LINKS_PORT
GATEWAY=http://127.0.0.1:$GATEWAY_PORT
rm -rf out-fixes && mkdir -p out-fixes vendor/jdbc
exec > >(tee out-fixes/fixes.log) 2>&1

# compose interpolates ${VAR} from the project's .env file, not from this shell's environment; run.sh writes the same
# file, so the two scripts run one at a time.
printf 'LINKS_PORT=%s\nQUERY_PORT=%s\nGATEWAY_PORT=%s\nQUERY_IMAGE=%s\n' "$LINKS_PORT" "$QUERY_PORT" "$GATEWAY_PORT" "$QUERY_IMAGE" > .env
compose() { finch compose -p "$STACK" -f compose.yaml -f compose.fixes.yaml "$@"; }
cleanup() {
  # compose logs waits forever on a service whose container never started, as after a failure before or during
  # `compose up`; compose ps ignores a service argument, so the started services are read from its JSON listing.
  started=" $(compose ps -a --format json 2>/dev/null | python3 -c 'import json, sys
print(" ".join(c["Service"] for c in json.load(sys.stdin) if c["State"] in ("running", "exited")))' 2>/dev/null || true) "
  for service in plm-fr plm-de plm-uk plm-es core query gateway; do
    case "$started" in *" $service "*) ;; *) continue ;; esac
    compose logs --no-log-prefix "$service" > "out-fixes/$service.log" 2>&1 || true
  done
  compose down -v >/dev/null 2>&1 || true
  rm -f .env
}
trap cleanup EXIT

python3 generate.py
[ -f "vendor/jdbc/postgresql-$PGJDBC.jar" ] || curl -sSfL -o "vendor/jdbc/postgresql-$PGJDBC.jar" \
  "https://repo1.maven.org/maven2/org/postgresql/postgresql/$PGJDBC/postgresql-$PGJDBC.jar"

echo "== build the query image and the PLM and core jars"
finch build -q -f "$ROOT/modules/query-service/Dockerfile" -t "$QUERY_IMAGE" "$ROOT"
(cd "$ROOT/modules/plm-services" && "${MVN[@]}" -q -B -DskipTests package)

echo "== start stack"
compose down -v >/dev/null 2>&1 || true
compose up -d >/dev/null 2>&1
t0=$(date +%s)
until curl -sf -o /dev/null --data-urlencode 'query=ASK {}' "$LINKS/query"; do
  [ $(( $(date +%s) - t0 )) -lt 120 ] || { compose ps; exit 1; }
  sleep 1
done
for graph in links fileindex labels options; do
  curl -sSf -X POST -H 'Content-Type: text/turtle' --data-binary "@generated/$graph.ttl" \
    "$LINKS/store?graph=https://example.com/atelier/graph/$graph"
done
t0=$(date +%s)
for path in fr/health de/health uk/health es/health core/health query/health; do
  until curl -sf -o /dev/null "$GATEWAY/$path"; do
    [ $(( $(date +%s) - t0 )) -lt 240 ] || { compose ps; compose logs gateway | tail -20; exit 1; }
    sleep 2
  done
  echo "  /$path answers through the gateway after $(( $(date +%s) - t0 )) s"  # nosemgrep: unquoted-command-substitution-in-command -- arithmetic, not split
done
warmup_status() { curl -sf "$GATEWAY/query/health/warmup" | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])' 2>/dev/null; }
until [ "$(warmup_status)" = done ]; do
  [ $(( $(date +%s) - t0 )) -lt 900 ] || { compose logs query | tail -40; exit 1; }
  sleep 2
done
echo "  query warm-up done after $(( $(date +%s) - t0 )) s"  # nosemgrep: unquoted-command-substitution-in-command -- arithmetic, not split

echo "== preview cycle, then fix cycle (links loader stood in for)"
(cd "$ROOT" && BASE="$GATEWAY" node --input-type=module -e '
import { fixCycle } from "./tests/fix-cycle.mjs";
import { previewCycle } from "./tests/preview-cycle.mjs";
let failures = 0;
const check = (ok, label, detail = "") => {
  console.log(`${ok ? "PASS" : "FAIL"}  ${label}${detail ? `  (${detail})` : ""}`);
  if (!ok) failures++;
};
await previewCycle({ base: process.env.BASE, headers: {}, check });
await fixCycle({ base: process.env.BASE, headers: {}, check, loader: "stand-in" });
console.log(failures ? `\n${failures} check(s) failed` : "\nall checks passed");
process.exit(failures ? 1 : 0);
')

echo "== smoke test, local mode: every check that needs no CloudFront, Cognito, S3 or event"
(cd "$ROOT" && LOCAL_API="$GATEWAY" node tests/smoke.mjs)
