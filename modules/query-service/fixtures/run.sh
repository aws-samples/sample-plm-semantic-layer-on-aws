#!/usr/bin/env bash
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# End-to-end check of the query service image against the local fixture stack (Finch), which runs the
# production dataset (data/products/*.json through the PLM services' migrations and the production mappings).
# Generates the fixture, builds the image, starts the stack, loads the links and file-index graphs,
# calls the API under several viewer profiles (each profile's first screen right after the warm-up: the product
# list, then the first product's parts, placements and interfaces; each interface of every product once; every product's
# parts, placements and interfaces as every profile; then twenty
# full answers), calls the where-used, impact and export-status answers, the wind turbine's bill of
# materials and one part's used-in, the placements of three products and of one subtree, the parts and placements of
# every variant option's configuration, the ornithopter's stations and sections, the subtrees of three of its assemblies, each site's closure table (its pairs for one
# tree, its maintenance by the triggers against a full recomputation, closure-maintenance.sql, and the plan and time
# of each subtree round's root lookup), every product's external references, calls the MCP endpoint as an MCP client (mcp_client.py through uv), removes
# one part's tag and calls the API again, asserts the expected status per interface, the export-control
# redaction, the untagged-part report, the head hoop's missing-CAD finding, the bill-of-materials roll-up per
# site and its redaction, the first-call times, the Ontop reformulation cache hits, the absence of any Ontop
# reformulation failure and the MCP answers, writes the responses and the Ontop logs to out/ (and keeps them in
# out-failed/ when the run fails) and removes the stack. Needs finch, python3, curl, uv.
#   STACK       compose project name (containers <STACK>-<service>-1), default atelierquery
#   LINKS_PORT  host port of the link store (default 18478), QUERY_PORT host port of the service (18480)
set -euo pipefail
cd "$(dirname "$0")"
ROOT=$(cd ../../.. && pwd)
PGJDBC=42.7.13
STACK=${STACK:-atelierquery}
LINKS_PORT=${LINKS_PORT:-18478} QUERY_PORT=${QUERY_PORT:-18480} QUERY_IMAGE=$STACK-svc:local
LINKS=http://127.0.0.1:$LINKS_PORT
QUERY=http://127.0.0.1:$QUERY_PORT/query
# Every interface as <product key>/<id>: interface ids are unique within a product, so a single-interface call names both.
INTERFACES=$(python3 -c "import json, sys; print(' '.join(f'{d[\"product\"][\"key\"]}/{i[\"id\"]}' for f in sys.argv[1:] for d in [json.load(open(f))] for i in d['interfaces']))" "$ROOT"/data/products/*.json)
rm -rf out && mkdir -p out vendor/jdbc
exec > >(tee out/run.log) 2>&1

# compose interpolates ${VAR} from the project's .env file, not from this shell's environment.
printf 'LINKS_PORT=%s\nQUERY_PORT=%s\nQUERY_IMAGE=%s\n' "$LINKS_PORT" "$QUERY_PORT" "$QUERY_IMAGE" > .env
compose() { finch compose -p "$STACK" -f compose.yaml "$@"; }
# A failing run's responses and logs are kept in out-failed/, so the next run does not erase them.
# The query container's log when it does not come up: its first and last 200 lines in out/ (kept in out-failed/), the
# head carrying the cause of a crash and the tail its last frames.
query_log() {
  compose logs --no-log-prefix query > out/query.log 2>&1 || true
  head -200 out/query.log > out/query-head.log
  tail -200 out/query.log > out/query-tail.log
  rm -f out/query.log
  tail -40 out/query-tail.log
}
cleanup() {
  local status=$?
  compose down -v >/dev/null 2>&1 || true
  rm -f .env
  if [ "$status" -ne 0 ]; then rm -rf out-failed && cp -R out out-failed && echo "  responses kept in out-failed/"; fi
}
trap cleanup EXIT

python3 generate.py
[ -f "vendor/jdbc/postgresql-$PGJDBC.jar" ] || curl -sSfL -o "vendor/jdbc/postgresql-$PGJDBC.jar" \
  "https://repo1.maven.org/maven2/org/postgresql/postgresql/$PGJDBC/postgresql-$PGJDBC.jar"

echo "== build image"
finch build -q -f "$ROOT/modules/query-service/Dockerfile" -t "$QUERY_IMAGE" "$ROOT"
finch images "$QUERY_IMAGE" --format '  {{.Repository}}:{{.Tag}} {{.Size}}' | head -1

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
until curl -sf -o out/parts-unknown.json "$QUERY/parts"; do
  [ $(( $(date +%s) - t0 )) -lt 180 ] || { query_log; exit 1; }
  sleep 2
done
echo "  endpoints answering after $(( $(date +%s) - t0 )) s"  # nosemgrep: unquoted-command-substitution-in-command -- arithmetic, not split
t0=$(date +%s)
warmup_status() { curl -sf "$QUERY/health/warmup" | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])' 2>/dev/null; }
# A request made while the warm-up runs is answered then, not after it: the warm-up holds no request back.
until [ "$(warmup_status)" != pending ]; do
  [ $(( $(date +%s) - t0 )) -lt 900 ] || { query_log; exit 1; }
  sleep 1
done
warmup_status > out/during-warmup-status.txt
curl -sSf -o out/during-warmup-list.json -w '%{time_total}' -H 'x-atelier-profile: de-engineer' "$QUERY/products/list" > out/during-warmup-time.txt
warmup_status >> out/during-warmup-status.txt
until [ "$(warmup_status)" = done ]; do
  [ $(( $(date +%s) - t0 )) -lt 900 ] || { query_log; exit 1; }
  sleep 1
done
echo "  warm-up done after $(( $(date +%s) - t0 )) s"  # nosemgrep: unquoted-command-substitution-in-command -- arithmetic, not split

echo "== API calls"
curl -sSf "$QUERY/health"; echo
curl -sSf -o out/warmup.json "$QUERY/health/warmup"
api() { curl -sSf -H "x-atelier-profile: $1" -o "out/$2" -w "  $3 as $1: HTTP %{http_code}, %{time_total} s\n" "$QUERY/$3"; }
# Ontop's query log so far, one line per request: "<source> {json with reformulationCacheHit}". The random
# per-request queryId is dropped so the saved log is free of accidental substrings.
ontop_log() {
  for src in fr de uk es core; do
    compose logs --no-log-prefix "ontop-$src" 2>/dev/null | grep -F reformulationCacheHit | sed -E "s/^/$src /; s/\"queryId\":\"[^\"]*\",//"
  done
}
ontop_log > out/ontop-log-warm.txt
echo "== first screen of each profile after warm-up: the product list, then the first product's parts, placements and interfaces"
for profile in $(python3 -c 'import json; print(" ".join(json.load(open("../../../ontology/policy.json"))["profiles"]))'); do
  api "$profile" "screen-$profile-list.json" products/list
  first=$(python3 -c 'import json, sys; print(json.load(open(sys.argv[1]))["products"][0]["key"])' "out/screen-$profile-list.json")
  api "$profile" "screen-$profile-parts.json" "parts?product=$first"
  api "$profile" "screen-$profile-placements.json" "placements?product=$first"
  api "$profile" "screen-$profile-interfaces.json" "interfaces?product=$first"
done
ontop_log > out/ontop-log-screen.txt
echo "== first call to each interface after warm-up (the profile's request texts, translated at warm-up)"
for profile in programme-cleared de-engineer; do
  for ref in $INTERFACES; do
    api "$profile" "first-$profile-${ref%/*}-${ref#*/}.json" "interfaces/${ref#*/}?product=${ref%/*}"
  done
done
ontop_log > out/ontop-log-first.txt
uptime | sed -E 's/.*(load averages?:)/\1/' > out/load-first.txt
echo "== every product's first screen as every profile: its parts, placements and interfaces"
for profile in $(python3 -c 'import json; print(" ".join(json.load(open("../../../ontology/policy.json"))["profiles"]))'); do
  for product in $(python3 -c "import json, sys; print(' '.join(json.load(open(f))['product']['key'] for f in sys.argv[1:]))" "$ROOT"/data/products/*.json); do
    for answer in parts placements interfaces; do
      curl -sSf -o /dev/null -H "x-atelier-profile: $profile" "$QUERY/$answer?product=$product"
    done
  done
done
ontop_log > out/ontop-log-products.txt
for run in $(seq 1 20); do
  api programme-cleared "interfaces-run$run.json" interfaces
done
cp out/interfaces-run20.json out/interfaces.json
api programme-cleared parts.json parts
api programme-cleared interface-IF-13.json "interfaces/IF-13?product=ornithopter"
curl -s -o /dev/null -w "  interfaces/IF-999: HTTP %{http_code}\n" "$QUERY/interfaces/IF-999"
for id in IF-13 IF-25 IF-05 IF-02 IF-31; do
  api programme-cleared "evidence-$id.json" "interfaces/$id/evidence?product=ornithopter"
done
api de-engineer interfaces-de.json interfaces
api de-engineer interface-IF-44-de.json "interfaces/IF-44?product=ornithopter"
api de-engineer interface-IF-43-de.json "interfaces/IF-43?product=ornithopter"
api de-engineer evidence-IF-03-de.json "interfaces/IF-03/evidence?product=ornithopter"
api de-engineer parts-de.json parts
api export-officer interfaces-officer.json interfaces
api export-officer parts-officer.json parts
api export-officer products-officer.json products
api export-officer products-list-officer.json products/list
api de-engineer products-de.json products
api de-engineer products-list-de.json products/list
api unknown interfaces-unknown.json interfaces

echo "== where-used, impact and export status"
api programme-cleared where-used-FR-ORN-KEEL-001.json parts/FR-ORN-KEEL-001/where-used
api programme-cleared impact-HL-6180-02.json "impact?feature=HL%206180-02"
api programme-cleared impact-WURZ-R-61080.json "impact?part=WURZ-R-61080"
api de-engineer export-status-HMOT-70090-de.json parts/HMOT-70090/export-status
api export-officer export-status-HMOT-70090-officer.json parts/HMOT-70090/export-status
api programme-cleared export-status-KNKL-6130-L.json parts/KNKL-6130-L/export-status
api de-engineer export-status-KNKL-6130-L-de.json parts/KNKL-6130-L/export-status
curl -s -o /dev/null -w "  parts/NOPE/where-used: HTTP %{http_code}\n" "$QUERY/parts/NOPE/where-used"

echo "== bill of materials of the wind turbine, and the assembly its main bearing shaft seal is used in (4 per unit)"
api export-officer bom-wind-turbine-officer.json "bom?product=wind-turbine"
api de-engineer bom-wind-turbine-de.json "bom?product=wind-turbine"
api programme-cleared used-in-D-37007.json "parts/D-37007/used-in?product=wind-turbine"
echo "== subtrees of the wind turbine: the gearbox (DE), the hub control (UK, references into DE and FR), the blade set as"
echo "   de-engineer (NATIONAL-FR items hidden); each asked twice and the second answer kept, Ontop's translation cache warm"
twice() { api "$@" >/dev/null; api "$@"; }
for root in D-37073 UK-3776; do
  for answer in parts interfaces bom; do
    twice programme-cleared "subtree-$answer-$root.json" "$answer?product=wind-turbine&root=$root"
  done
done
twice de-engineer subtree-parts-FR3770-de.json "parts?product=wind-turbine&root=FR3770"
twice de-engineer subtree-parts-UK-3776-de.json "parts?product=wind-turbine&root=UK-3776"
twice programme-cleared subtree-parts-wind-turbine.json "parts?product=wind-turbine&root=wind-turbine"
twice programme-cleared parts-wind-turbine.json "parts?product=wind-turbine"
twice programme-cleared interfaces-wind-turbine.json "interfaces?product=wind-turbine"
echo "== placements: every occurrence of the rover, the wind turbine and the difference engine composed down the trees, the"
echo "   wind turbine as de-engineer (hidden items have no placements), and the wind turbine's blade set as a subtree"
for product in rover wind-turbine difference-engine; do
  api export-officer "placements-$product.json" "placements?product=$product"
done
api de-engineer placements-wind-turbine-de.json "placements?product=wind-turbine"
twice export-officer placements-subtree-FR3770.json "placements?product=wind-turbine&root=FR3770"
echo "== stations and sections of the ornithopter: WS 1500 to WS 3600 on both wings, as programme-cleared and as unknown,"
echo "   WS 3000 to WS 4100 on the right wing, and the sections with their joints"
api programme-cleared stations-ornithopter.json "stations?product=ornithopter&from=WS%201500&to=WS%203600"
api unknown stations-ornithopter-unknown.json "stations?product=ornithopter&from=WS%201500&to=WS%203600"
api programme-cleared stations-ornithopter-right.json "stations?product=ornithopter&from=WS%203000&to=WS%204100&side=right"
api programme-cleared sections-ornithopter.json "sections?product=ornithopter"
echo "== closure tables: each site's pairs for a tree of three levels or more, read on its own database"
for spec in de:teilestruktur:vorfahr:nachfahr:D-37073 fr:nomenclature_fermeture:ascendant:descendant:FR3770 \
            es:cierre_lista_materiales:ascendiente:descendiente:ES-3774 uk:bom_closure:ancestor:descendant:UK-3775; do
  IFS=: read -r site table ancestor descendant root <<< "$spec"
  finch exec "$STACK-pg-1" psql -At -U postgres -d "${site}_plm" \
    -c "SELECT $ancestor, $descendant FROM $table WHERE $ancestor = '$root'" > "out/closure-$site.txt"
  echo "  $table: $(wc -l < "out/closure-$site.txt" | tr -d ' ') pairs under $root"
done
echo "== closure maintenance: after the seed and after moving one line per site, each table equals a full recomputation"
finch exec -i "$STACK-pg-1" psql -q -v ON_ERROR_STOP=1 -U postgres -d postgres < closure-maintenance.sql 2>&1 | sed 's/^NOTICE:  /  /'
echo "== closure lookups: each subtree round's roots on their site's closure table, EXPLAIN ANALYZE five times each"
: > out/closure-explain.txt
python3 - out/subtree-interfaces-D-37073.json out/subtree-interfaces-UK-3776.json <<'PY' | while IFS='|' read -r root site sql; do
import json, sys
TABLES = {"de": ("teilestruktur", "vorfahr", "nachfahr"), "fr": ("nomenclature_fermeture", "ascendant", "descendant"),
          "es": ("cierre_lista_materiales", "ascendiente", "descendiente"), "uk": ("bom_closure", "ancestor", "descendant")}
for path in sys.argv[1:]:
    subtree = json.load(open(path))["subtree"]
    for rnd in subtree["rounds"]:
        for site in sorted({r["plm"] for r in rnd["roots"]}):
            table, ancestor, descendant = TABLES[site]
            ids = ", ".join(f"'{r['id']}'" for r in rnd["roots"] if r["plm"] == site)
            print(f"{subtree['root']}|{site}|SELECT {ancestor}, {descendant} FROM {table} WHERE {ancestor} IN ({ids})")
PY
  for _ in 1 2 3 4 5; do
    printf '%s|%s|%s|' "$root" "$site" "$sql" >> out/closure-explain.txt
    finch exec "$STACK-pg-1" psql -At -U postgres -d "${site}_plm" -c "EXPLAIN (ANALYZE, FORMAT JSON) $sql" < /dev/null | tr -d '\n' >> out/closure-explain.txt
    echo >> out/closure-explain.txt
  done
  echo "  $root on $site: $sql"
done
for product in $(python3 -c "import json, sys; print(' '.join(json.load(open(f))['product']['key'] for f in sys.argv[1:]))" "$ROOT"/data/products/*.json); do
  api programme-cleared "references-$product.json" "references?product=$product"
  api de-engineer "references-$product-de.json" "references?product=$product"
done

echo "== purchased items of the rover: the parts that are one item, and its suppliers; the O-rings of the wind turbine and the beam engine"
api programme-cleared equivalents-rover.json "equivalents?product=rover"
api programme-cleared equivalents-wind-turbine.json "equivalents?product=wind-turbine"
api programme-cleared equivalents-steam-engine.json "equivalents?product=steam-engine"
api programme-cleared equivalents-ornithopter.json "equivalents?product=ornithopter"
api programme-cleared suppliers-rover.json "suppliers?product=rover"
echo "== terms in three languages: the glossary concept and the items of every site whose names hold it"
for word in Zahnrad roue gear; do api programme-cleared "terms-$word.json" "terms?q=$word"; done

echo "== variant diffs: every option of every variant group against its group's default, as the officer and as de-engineer"
python3 - "$ROOT"/data/products/*.json <<'PY' | while read -r product group option; do
import json, sys
for path in sys.argv[1:]:
    data = json.load(open(path))
    for group, spec in ((data.get("extended") or {}).get("variants") or {}).items():
        for option, o in spec["options"].items():
            if not o.get("default"):
                print(data["product"]["key"], group, option)
PY
  api export-officer "variant-$product-$option.json" "variant-diff?product=$product&group=$group&option=$option"
  api de-engineer "variant-$product-$option-de.json" "variant-diff?product=$product&group=$group&option=$option"
  api export-officer "option-parts-$product-$option.json" "parts?product=$product&option=$option"
  api export-officer "option-placements-$product-$option.json" "placements?product=$product&option=$option"
  api de-engineer "option-parts-$product-$option-de.json" "parts?product=$product&option=$option"
done
api export-officer option-placements-wind-turbine-onshore.json "placements?product=wind-turbine&option=onshore"
curl -s -o out/option-unknown.json -w "  parts of an unknown option: HTTP %{http_code}\n" "$QUERY/parts?product=steam-engine&option=triple-expansion"
api programme-cleared variants-ornithopter.json "variants?product=ornithopter"
curl -s -o out/variant-unknown.json -w "  variant-diff of an unknown option: HTTP %{http_code}\n" \
  "$QUERY/variant-diff?product=steam-engine&group=acting&option=triple-expansion"

echo "== MCP endpoint, as an MCP client over streamable HTTP"
uv run --quiet --with mcp==2.1.1 python3 mcp_client.py "$QUERY/mcp" out

echo "== untagged part: the ES left pulley block (IF-27, with the FR middle cross beam) loses its tag row in atelier_core"
finch exec "$STACK-pg-1" psql -q -U postgres -d atelier_core -c "DELETE FROM part_tag WHERE native_key = 'POLE-L-6050'"
api programme-cleared interfaces-untagged.json interfaces
api programme-cleared interface-IF-27-untagged.json "interfaces/IF-27?product=ornithopter"
api programme-cleared evidence-IF-27-untagged.json "interfaces/IF-27/evidence?product=ornithopter"
api programme-cleared parts-untagged.json parts
api export-officer interfaces-untagged-officer.json interfaces

echo "== service startup (Spring Boot), Ontop reformulator builds and warm-up"
compose logs query 2>&1 | grep -E 'Started QueryServiceApplication|Ontop reformulator|Warm-up|policy|CAD_BUCKET' | sed 's/^.*atelier-query-service\] /  /'
ontop_log > out/ontop-log-final.txt
for src in fr de uk es core; do
  compose logs --no-log-prefix "ontop-$src" > "out/ontop-$src.log" 2>&1
done

echo "== assertions"
python3 check.py out
python3 check_subtree.py out
echo "== ALL ASSERTIONS PASSED"
