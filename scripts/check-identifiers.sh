#!/usr/bin/env bash
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

#
# scripts/check-identifiers.sh: a public sample names no account, person, private network or
# organisation-specific architecture.
# Scans the contents and the paths of every tracked file, case-insensitively, and fails on:
#
#   account     a 12-digit number other than the placeholder 111111111111 (digits inside a
#               longer token such as a hash or a decimal fraction are not a number)
#   email       an e-mail address other than *@example.com and the code-of-conduct address
#   hostname    a hostname under .aws.dev
#   arn         an arn:aws: whose account field is not the placeholder
#   term        a word or phrase that ties the sample to one organisation's programme or target
#               architecture (product and platform choices, internal jargon); the sample describes
#               a generic PLM reference architecture
#
# The term check has two lists. The generic terms are below. The organisation-specific terms
# (names that must not be written into the repository, not even here) are read from the file
# FORBIDDEN_TERMS_FILE, default ~/.config/atelier/forbidden-terms, one term per line or several on a
# line separated by colons, each matched as a whole word, case-insensitively; blank lines and lines
# starting with # are ignored. The gate fails when that file is missing or holds no term, unless
# --without-organisation-terms is given: then the generic checks alone decide, and one line says
# that the organisation-specific terms were not checked. The publication pipeline and the private
# GitLab CI have the file (a masked file variable of the same name, FORBIDDEN_TERMS_FILE; GitLab
# masks a single line only, so the variable holds the terms on one line, separated by colons); the
# public repository's GitHub workflow has none by design and passes the flag.
#
# Runs under bash 3.2 (macOS) and the CI runners; needs git and grep only.
#
#   scripts/check-identifiers.sh                              # gate: exit 1 on any hit
#   scripts/check-identifiers.sh --report                     # count hits per check, exit 0
#   scripts/check-identifiers.sh --without-organisation-terms # gate on the generic checks only
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

SELF="scripts/check-identifiers.sh"
PLACEHOLDER="111111111111"
REPORT=0; ORG_TERMS=1
for arg in "$@"; do
  case "$arg" in
    --report) REPORT=1 ;;
    --without-organisation-terms) ORG_TERMS=0 ;;
    *) echo "usage: $SELF [--report] [--without-organisation-terms]" >&2; exit 2 ;;
  esac
done

LIST="$(mktemp)"; HITS="$(mktemp)"; trap 'rm -f "$LIST" "$HITS"' EXIT
git ls-files | grep -v -x -F "$SELF" > "$LIST"
TOTAL="$(wc -l < "$LIST" | tr -d ' ')"

# Each check: an extended regex over contents and paths, and an optional regex that
# removes allowed matches (applied to the matched text alone).
RE_ACCOUNT='(^|[^0-9a-f.])[0-9]{12}([^0-9a-f]|$)'
RE_EMAIL='[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}'
RE_HOST='[a-z0-9.-]+\.aws\.dev'
RE_ARN='arn:aws[a-z-]*:[a-z0-9-]*:[a-z0-9-]*:[0-9]{12}'
# Terms: one alternative per line of the list, short tokens bounded so they match whole words only.
B='(^|[^a-z0-9])'; E='([^a-z0-9]|$)'
RE_TERM="$(tr '\n' '|' <<TERMS | sed 's/|$//'
appstream
${B}daas${E}
openshift
mainframe
nightly batch
neo4j
fastify
phone[ _-]?book
national (pdm|plm)
${B}on stage${E}
on[ -]premises?
peak jobs?
event engine
ingestion pipelines?
people, organi[sz]ation
${B}cdc${E}
TERMS
)"
if [ "$ORG_TERMS" = 1 ]; then
  TERMS_FILE="${FORBIDDEN_TERMS_FILE:-$HOME/.config/atelier/forbidden-terms}"
  # A missing or empty file leaves PRIVATE empty (the last grep selects nothing; under pipefail that must not end the script).
  PRIVATE="$( { grep -v -E '^[[:space:]]*(#|$)' "$TERMS_FILE" 2>/dev/null || true; } | tr ':' '\n' \
    | sed 's/^[[:space:]]*//; s/[[:space:]]*$//' | grep -v '^$' | tr '\n' '|' | sed 's/|$//' || true)"
  if [ -z "$PRIVATE" ]; then
    echo "IDENTIFIER CHECK FAILED: no organisation-specific term read from FORBIDDEN_TERMS_FILE ($TERMS_FILE);" >&2
    echo "  set FORBIDDEN_TERMS_FILE to a file of terms, one per line or separated by colons," >&2
    echo "  or pass --without-organisation-terms to run the generic checks alone." >&2
    exit 1
  fi
  RE_TERM="$RE_TERM|${B}($PRIVATE)${E}"
else
  echo "NOTE: organisation-specific terms not checked (--without-organisation-terms, no FORBIDDEN_TERMS_FILE given); the generic checks decide."
fi
ALLOW_ACCOUNT="(^|[^0-9])$PLACEHOLDER([^0-9]|$)"
ALLOW_EMAIL='@example\.com$|^opensource-codeofconduct@amazon\.com$'
ALLOW_ARN=":$PLACEHOLDER\$"

# $1 name, $2 regex, $3 allow regex (may be empty). Writes "file:line:match" or
# "path:<path>" lines to $HITS.
scan() {
  name="$1"; re="$2"; allow="${3:-}"
  : > "$HITS"
  { tr '\n' '\0' < "$LIST" | xargs -0 grep -EnoiI -- "$re" 2>/dev/null || true; } \
    | awk -F: -v allow="$allow" '{
        match($0, /^[^:]*:[0-9]+:/); text = substr($0, RLENGTH + 1)
        if (allow == "" || tolower(text) !~ allow) print
      }' >> "$HITS"
  { grep -Eoi -- "$re" "$LIST" || true; } \
    | awk -v allow="$allow" '{ if (allow == "" || tolower($0) !~ allow) print "path:" $0 }' >> "$HITS"
  count="$(wc -l < "$HITS" | tr -d ' ')"
  if [ "$count" != 0 ]; then
    rc=1
    printf '%-10s %5s hit(s)\n' "$name" "$count"
    [ "$REPORT" = 1 ] || head -10 "$HITS" | sed 's/^/    /'
  fi
}

rc=0
scan account  "$RE_ACCOUNT" "$ALLOW_ACCOUNT"
scan email    "$RE_EMAIL"   "$ALLOW_EMAIL"
scan hostname "$RE_HOST"
scan arn      "$RE_ARN"     "$ALLOW_ARN"
scan term     "$RE_TERM"

if [ "$rc" = 0 ]; then echo "IDENTIFIER CHECK PASSED over $TOTAL tracked files."; exit 0; fi
[ "$REPORT" = 1 ] && exit 0
echo "IDENTIFIER CHECK FAILED: replace the identifiers above with placeholders and the terms with generic ones." >&2
exit 1
