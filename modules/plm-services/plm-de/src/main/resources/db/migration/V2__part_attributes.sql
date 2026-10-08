-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Stammdaten eines Bauteils: Revision (01, 02), Status in der Sprache des Werks, Masse in Kilogramm,
-- Werkstoff und Teileart (PART, SOFTWARE oder DOCUMENT; ein Bauteil ohne Angabe ist ein PART).
ALTER TABLE bauteil ADD COLUMN revision   VARCHAR(8);
ALTER TABLE bauteil ADD COLUMN status     VARCHAR(32);
ALTER TABLE bauteil ADD COLUMN masse_kg   NUMERIC(12, 3);
ALTER TABLE bauteil ADD COLUMN werkstoff  VARCHAR(120);
ALTER TABLE bauteil ADD COLUMN teileart   VARCHAR(16) NOT NULL DEFAULT 'PART';
