#!/bin/sh
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# Writes the JDBC properties from the environment, then starts the Ontop SPARQL endpoint
# (http://<host>:8080/sparql) over this image's mapping and ontology.
set -eu
PROPERTIES=/tmp/ontop-db.properties
java -cp "/opt/ontop/launcher:/opt/ontop/lib/*:/opt/ontop/jdbc/*" OntopDbProperties "$PROPERTIES"
# One JSON line per executed query with reformulation and execution durations,
# so the answer path can be profiled from the container logs.
cat >> "$PROPERTIES" <<'EOP'
ontop.queryLogging=true
ontop.queryLogging.includeSparqlQuery=false
ontop.queryLogging.includeReformulatedQuery=false
ontop.queryLogging.includeTables=true
EOP
exec /opt/ontop/entrypoint.sh \
  --ontology=/opt/ontop/input/ontology.ttl \
  --mapping=/opt/ontop/input/mapping.r2rml.ttl \
  --properties="$PROPERTIES" \
  --port=8080
