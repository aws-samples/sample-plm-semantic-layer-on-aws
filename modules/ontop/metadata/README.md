# Ontop DB metadata

`<plm>-db-metadata.json` (fr, de, uk, es) is the PostgreSQL schema of one PLM database as
Ontop 5.5.0 serialises it (`ontop extract-db-metadata`): tables, columns with their PostgreSQL
types and nullability, primary keys and foreign keys. The query service passes the file as
`dbMetadataFile` when it builds a `QueryReformulator`, so it translates SPARQL arms into SQL
offline, with no database credentials or connectivity.

Regenerate from the repository root:

```sh
modules/ontop/scripts/extract-db-metadata.sh          # all four PLMs
modules/ontop/scripts/extract-db-metadata.sh uk       # one PLM
```

The script runs `postgres:16` and `ontop/ontop:5.5.0` in containers (Finch or Docker), applies
each PLM's Flyway migrations in Flyway's order (the versioned `V*__*.sql`, then the repeatable
`R__products_seed.sql` under `modules/plm-services/plm-<plm>/src/main/resources/db/migration/`), extracts the
metadata and normalises the JSON (sorted keys, no extraction timestamp), so re-running on an
unchanged schema produces no diff.

Regenerate whenever a migration changes the schema: a stale file makes the query service
reformulate against tables or columns that no longer match the database.
