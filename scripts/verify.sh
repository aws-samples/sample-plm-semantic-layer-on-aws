#!/usr/bin/env bash
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# =============================================================================
# verify.sh: the local gate, one command, cheapest check first.
#
#   bash scripts/verify.sh          # lint, typecheck, gate self-test, build, synth,
#                                   # template gates, ontology terms, identifiers
#   bash scripts/verify.sh --fast   # skip the synth and the template gates
#   bash scripts/verify.sh --live   # also run tests/smoke.mjs against the deployed
#                                   # system (SITE, API and ORIGIN_SECRET in the environment)
#
# The synth uses the placeholder account 111111111111 in eu-west-1, whose availability
# zones are in infra/cdk.json, so no AWS credentials are needed. Every check is judged
# on its exit code; a check that cannot run is printed as SKIPPED, never as a pass.
# The Maven suites, the agent tests and the mappings drift check run in CI
# (.github/workflows/ci.yml), not here.
# =============================================================================
set -uo pipefail

cd "$(dirname "$0")/.."

FAST=0
LIVE=0
for arg in "$@"; do
  case "$arg" in
    --fast) FAST=1 ;;
    --live) LIVE=1 ;;
    *) echo "usage: verify.sh [--fast] [--live]" >&2; exit 2 ;;
  esac
done

LOGS="$(mktemp -d)"
SYNTH_OUT="$LOGS/cdk.out"
failures=0
skipped=0

step() { printf '\n== %s\n' "$1"; }
ok()   { printf '   ok      %s\n' "$1"; }
bad()  { printf '   FAIL    %s\n' "$1" >&2; failures=$((failures + 1)); }
skip() { printf '   SKIPPED %s\n' "$1" >&2; skipped=$((skipped + 1)); }

step "lint"
if npm run --silent lint >"$LOGS/lint.log" 2>&1; then
  ok "clean"
else
  tail -20 "$LOGS/lint.log" >&2
  bad "eslint reported problems ($LOGS/lint.log)"
fi

step "typecheck"
if npm run --silent typecheck >"$LOGS/tsc.log" 2>&1; then
  ok "clean"
else
  grep -E 'error TS' "$LOGS/tsc.log" | head -20 >&2
  bad "tsc reported errors ($LOGS/tsc.log)"
fi

step "guardrail gate self-test"
if bash scripts/check-guardrails.test.sh >"$LOGS/gatetest.log" 2>&1; then
  ok "$(grep -oE '[0-9]+ passed' "$LOGS/gatetest.log" | tail -1)"
else
  grep -E 'failed|expected exit' "$LOGS/gatetest.log" | head -20 >&2
  bad "the guardrail gate does not catch what it claims ($LOGS/gatetest.log)"
fi

step "build"
if npm run --silent build >"$LOGS/build.log" 2>&1; then
  if [ -f modules/web/dist/index.html ]; then
    ok "modules/web/dist built"
  else
    bad "build succeeded but modules/web/dist/index.html is missing: the Web stack would deploy its placeholder page"
  fi
else
  tail -20 "$LOGS/build.log" >&2
  bad "build failed ($LOGS/build.log)"
fi

step "synth and template gates"
if [ "$FAST" = "1" ]; then
  skip "--fast: the guardrails, web behaviours and security-group descriptions were not asserted"
elif ( cd infra && npx cdk synth -q -o "$SYNTH_OUT" -c env=prod -c account=111111111111 -c region=eu-west-1 -c semantic=true ) >"$LOGS/synth.log" 2>&1; then
  ok "synthesized $(find "$SYNTH_OUT" -maxdepth 1 -name '*.template.json' | wc -l | tr -d ' ') templates with the placeholder account"
  if bash scripts/check-guardrails.sh "$SYNTH_OUT" >"$LOGS/guardrails.log" 2>&1; then
    ok "$(grep -oE 'PASSED \(v[0-9.]+\)' "$LOGS/guardrails.log" | head -1) guardrails"
    grep -E 'REVIEW' "$LOGS/guardrails.log" | head -5 || true
  else
    grep -E 'VIOLATION|FAILED' "$LOGS/guardrails.log" | head -20 >&2
    bad "guardrail violation ($LOGS/guardrails.log)"
  fi
  if bash scripts/check-web-behaviours.sh "$SYNTH_OUT" >"$LOGS/web.log" 2>&1; then
    ok "CloudFront behaviours"
  else
    grep -E 'FAIL' "$LOGS/web.log" | head -20 >&2
    bad "web behaviour check failed ($LOGS/web.log)"
  fi
  if bash scripts/check-sg-descriptions.sh "$SYNTH_OUT" >"$LOGS/sg.log" 2>&1; then
    ok "security-group descriptions"
  else
    cat "$LOGS/sg.log" >&2
    bad "security-group description EC2 would reject ($LOGS/sg.log)"
  fi
else
  tail -20 "$LOGS/synth.log" >&2
  bad "cdk synth failed ($LOGS/synth.log)"
fi

step "ontology terms"
if command -v python3 >/dev/null 2>&1; then
  if python3 scripts/check-ontology-terms.py >"$LOGS/ontology.log" 2>&1; then
    ok "$(tail -1 "$LOGS/ontology.log")"
  else
    cat "$LOGS/ontology.log" >&2
    bad "a mapping or a shape uses an undeclared atelier: term"
  fi
else
  skip "python3 not found: the ontology terms were not checked"
fi

step "data checks"
if command -v python3 >/dev/null 2>&1; then
  if python3 -m unittest discover -s tests/data >"$LOGS/data-tests.log" 2>&1; then
    ok "data checks: $(grep -o 'Ran [0-9]* tests*' "$LOGS/data-tests.log")"
  else
    cat "$LOGS/data-tests.log" >&2
    bad "a check of data/*.py refuses or accepts the wrong product data"
  fi
else
  skip "python3 not found: the data checks were not run"
fi

step "smoke and fix-cycle expectations"
if node --test tests/seeded.test.mjs >"$LOGS/seeded-tests.log" 2>&1; then
  ok "expectations read from the product files: $(grep -Eo 'pass [0-9]+' "$LOGS/seeded-tests.log" | tail -1)"
else
  cat "$LOGS/seeded-tests.log" >&2
  bad "an expectation of tests/smoke.mjs or tests/fix-cycle.mjs is not what the product files seed"
fi

step "identifiers"
if bash scripts/check-identifiers.sh >"$LOGS/identifiers.log" 2>&1; then
  ok "$(tail -1 "$LOGS/identifiers.log")"
else
  cat "$LOGS/identifiers.log" >&2
  bad "a tracked file carries an account, address or hostname"
fi

if [ "$LIVE" = "1" ]; then
  step "live smoke (deployed system)"
  if [ -z "${SITE:-}" ] || [ -z "${API:-}" ] || [ -z "${ORIGIN_SECRET:-}" ]; then
    skip "--live needs SITE, API and ORIGIN_SECRET in the environment (see tests/smoke.mjs)"
  elif node tests/smoke.mjs >"$LOGS/smoke.log" 2>&1; then
    ok "the deployed system passes tests/smoke.mjs"
  else
    grep -E 'FAIL' "$LOGS/smoke.log" | head -20 >&2
    bad "the deployed system fails tests/smoke.mjs ($LOGS/smoke.log)"
  fi
fi

echo
if [ "$skipped" -gt 0 ]; then
  echo "$skipped check(s) SKIPPED; a skip is not a pass."
fi
if [ "$failures" -gt 0 ]; then
  echo "VERIFY FAILED: $failures check(s) failed. Logs in $LOGS" >&2
  exit 1
fi
rm -rf "$LOGS"
echo "VERIFY PASSED$([ "$FAST" = "1" ] && echo " (--fast: template gates not asserted)")"
