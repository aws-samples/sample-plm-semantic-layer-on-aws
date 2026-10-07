-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Atelier core demo_change as a PLM service reads it over the core read-only connection: a test copy of
-- the atelier-core schema, empty; each test logs the changes it resets.
CREATE TABLE IF NOT EXISTS demo_change (
    id           BIGSERIAL PRIMARY KEY,
    plm          TEXT NOT NULL,
    table_name   TEXT NOT NULL,
    row_key      TEXT NOT NULL,
    column_name  TEXT NOT NULL,
    before       TEXT,
    after        TEXT,
    actor        TEXT,
    purpose      TEXT,
    at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
