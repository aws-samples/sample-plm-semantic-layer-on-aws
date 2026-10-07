#!/bin/sh
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# Regenerates modules/ontop/metadata/<code>-db-metadata.json: the PostgreSQL schema of each service's
# database (every Flyway migration: versioned in version order, then the repeatable seed) serialised by `ontop extract-db-metadata`,
# so the query service can reformulate SPARQL into SQL without a database connection. The site
# PLMs (code fr, de, uk, es: module plm-<code>, database <code>_plm) and the Atelier core (code core:
# module atelier-core, database atelier_core). PostgreSQL, the JDBC driver fetch and Ontop all run in
# containers; only the container runtime is needed on the host.
# Usage: modules/ontop/scripts/extract-db-metadata.sh [code ...]    (default: fr de uk es core)
set -eu

ONTOP_DIR=$(cd "$(dirname "$0")/.." && pwd)
SERVICES_DIR=$(cd "$ONTOP_DIR/../plm-services" && pwd)
PLMS=${*:-fr de uk es core}
RUNTIME=$(command -v finch || command -v docker)
POSTGRES_IMAGE=public.ecr.aws/docker/library/postgres:16.15@sha256:1a6ab3f5345eb6dbe04a1349529caabdb0ab09293a09590fad07b2246bfa4b54
ONTOP_IMAGE=ontop/ontop:5.5.0@sha256:d19a2055b02812c8ecc0a00cca1733c1669c4143dfeb728acbfdff92b45e94d7
JDK_IMAGE=public.ecr.aws/docker/library/eclipse-temurin:11-jdk@sha256:8db2bcf62ae171d1247c331db0410d8bc6347e7493bdafe104bb9a33d59c1291
PGJDBC_VERSION=42.7.13
PGJDBC_SHA256=6e0e4cc2d8cae902084f8a2b18728b073a6fd9d1f87c9d8bff8f298c18185b93
# A fresh password per run for the throwaway local container; it never leaves this machine.
DB_PASSWORD="$(openssl rand -hex 16)"

cleanup() {
  "$RUNTIME" rm -f ateliermeta-pg >/dev/null 2>&1 || true
  "$RUNTIME" volume rm ateliermeta-jdbc >/dev/null 2>&1 || true
  "$RUNTIME" network rm ateliermeta-net >/dev/null 2>&1 || true
}
trap cleanup EXIT
cleanup
mkdir -p "$ONTOP_DIR/metadata"

# Same driver jar and checksum as modules/ontop/Dockerfile, fetched in an image that has CA
# certificates (the Ontop image has none), then mounted on Ontop's /opt/ontop/jdbc classpath.
# The volume is created 0700 root; Ontop runs as user ontop (uid 999), hence the chmod.
"$RUNTIME" volume create ateliermeta-jdbc >/dev/null
"$RUNTIME" run --rm --name ateliermeta-jdbc-fetch -v ateliermeta-jdbc:/jdbc "$JDK_IMAGE" sh -euc "
  curl -sSfL -o /jdbc/postgresql-$PGJDBC_VERSION.jar \
    https://repo1.maven.org/maven2/org/postgresql/postgresql/$PGJDBC_VERSION/postgresql-$PGJDBC_VERSION.jar
  echo '$PGJDBC_SHA256  /jdbc/postgresql-$PGJDBC_VERSION.jar' | sha256sum -c -
  chmod 755 /jdbc"

"$RUNTIME" network create ateliermeta-net >/dev/null
"$RUNTIME" run -d --name ateliermeta-pg --network ateliermeta-net \
  -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  -v "$SERVICES_DIR":/repo:ro \
  -v "$ONTOP_DIR/metadata":/out \
  "$POSTGRES_IMAGE" >/dev/null
tries=0
until "$RUNTIME" exec ateliermeta-pg pg_isready -q -h localhost -U postgres >/dev/null 2>&1; do
  tries=$((tries + 1))
  [ "$tries" -lt 60 ] || { echo "PostgreSQL did not become ready" >&2; exit 1; }
  sleep 1
done

for plm in $PLMS; do
  case "$plm" in
    core) module=atelier-core; database=atelier_core ;;
    *) module="plm-$plm"; database="${plm}_plm" ;;
  esac
  "$RUNTIME" exec ateliermeta-pg psql -q -v ON_ERROR_STOP=1 -U postgres -c "CREATE DATABASE $database"
  # GNU sort -V orders V1, V2, ... V10 by version number, as Flyway does.
  "$RUNTIME" exec -e MODULE="$module" -e DATABASE="$database" ateliermeta-pg sh -euc '
    for migration in $(ls /repo/$MODULE/src/main/resources/db/migration/V*__*.sql | sort -V) $(ls /repo/$MODULE/src/main/resources/db/migration/R__*.sql); do
      psql -q -v ON_ERROR_STOP=1 -U postgres -d "$DATABASE" -f "$migration"
    done'

  "$RUNTIME" run --rm --name "ateliermeta-ontop-$plm" --network ateliermeta-net \
    -v ateliermeta-jdbc:/opt/ontop/jdbc:ro \
    -v "$ONTOP_DIR/metadata":/out \
    -e PLM="$plm" -e DATABASE="$database" -e DB_PASSWORD="$DB_PASSWORD" \
    "$ONTOP_IMAGE" sh -euc '
      printf "jdbc.url=jdbc:postgresql://ateliermeta-pg:5432/%s\njdbc.user=postgres\njdbc.password=%s\njdbc.driver=org.postgresql.Driver\n" \
        "$DATABASE" "$DB_PASSWORD" > /tmp/ontop-db.properties
      exec /opt/ontop/ontop extract-db-metadata \
        --properties=/tmp/ontop-db.properties --output="/out/$PLM-db-metadata.json"'

  # Deterministic file: sorted keys and no extraction timestamp. Ontop writes neither the JDBC
  # URL nor credentials into the JSON.
  "$RUNTIME" exec ateliermeta-pg perl -MJSON::PP -0777 -i -pe '
    my $json = JSON::PP->new->canonical->pretty;
    my $doc = $json->decode($_);
    delete $doc->{metadata}{extractionTime};
    $_ = $json->encode($doc)' "/out/$plm-db-metadata.json"
done
