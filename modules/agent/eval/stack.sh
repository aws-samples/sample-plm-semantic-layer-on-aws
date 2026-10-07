#!/usr/bin/env bash
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# The query stack of the fixtures (modules/query-service/fixtures/compose.yaml: PostgreSQL with the product data,
# one Ontop endpoint per source, Oxigraph for the link store, the query service) kept running for the show eval.
#   stack.sh up     generates the fixture, builds the query image, starts the stack, loads the graphs, waits for the warm-up
#   stack.sh down   removes the stack and its volumes
#   STACK       compose project name, default atelieragenteval
#   LINKS_PORT  host port of the link store (default 19478), QUERY_PORT of the query service (19480)
set -euo pipefail
FIXTURES=$(cd "$(dirname "$0")/../../query-service/fixtures" && pwd)
ROOT=$(cd "$FIXTURES/../../.." && pwd)
PGJDBC=42.7.13
STACK=${STACK:-atelieragenteval}
LINKS_PORT=${LINKS_PORT:-19478} QUERY_PORT=${QUERY_PORT:-19480} QUERY_IMAGE=$STACK-svc:local
LINKS=http://127.0.0.1:$LINKS_PORT
QUERY=http://127.0.0.1:$QUERY_PORT/query
# compose interpolates ${VAR} from the project's .env file, not from this shell's environment; run.sh and fixes.sh
# write the same file, so this script runs while neither does.
trap 'rm -f "$FIXTURES/.env"' EXIT
printf 'LINKS_PORT=%s\nQUERY_PORT=%s\nQUERY_IMAGE=%s\n' "$LINKS_PORT" "$QUERY_PORT" "$QUERY_IMAGE" > "$FIXTURES/.env"
compose() { finch compose -p "$STACK" -f "$FIXTURES/compose.yaml" "$@"; }

case "${1:-}" in
  up)
    (cd "$FIXTURES" && python3 generate.py)
    mkdir -p "$FIXTURES/vendor/jdbc"
    [ -f "$FIXTURES/vendor/jdbc/postgresql-$PGJDBC.jar" ] || curl -sSfL -o "$FIXTURES/vendor/jdbc/postgresql-$PGJDBC.jar" \
      "https://repo1.maven.org/maven2/org/postgresql/postgresql/$PGJDBC/postgresql-$PGJDBC.jar"
    finch build -q -f "$ROOT/modules/query-service/Dockerfile" -t "$QUERY_IMAGE" "$ROOT"
    compose down -v >/dev/null 2>&1 || true
    compose up -d >/dev/null 2>&1
    t0=$(date +%s)
    until curl -sf -o /dev/null --data-urlencode 'query=ASK {}' "$LINKS/query"; do
      [ $(( $(date +%s) - t0 )) -lt 120 ] || { compose ps; exit 1; }
      sleep 1
    done
    for graph in links fileindex labels options; do
      curl -sSf -X POST -H 'Content-Type: text/turtle' --data-binary "@$FIXTURES/generated/$graph.ttl" \
        "$LINKS/store?graph=https://example.com/atelier/graph/$graph"
    done
    t0=$(date +%s)
    warmup_status() { curl -sf "$QUERY/health/warmup" | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])' 2>/dev/null; }
    until [ "$(warmup_status)" = done ]; do
      [ $(( $(date +%s) - t0 )) -lt 480 ] || { compose logs query | tail -40; exit 1; }
      sleep 2
    done
    echo "query service at $QUERY, MCP at $QUERY/mcp"
    ;;
  down)
    compose down -v
    ;;
  *)
    echo "usage: $0 up|down" >&2
    exit 2
    ;;
esac
