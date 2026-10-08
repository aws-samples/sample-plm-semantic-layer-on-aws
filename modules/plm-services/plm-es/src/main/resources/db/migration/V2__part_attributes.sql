-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Atributos de una pieza: revisión (1, 2), estado en el idioma del taller, masa en kilogramos, material
-- y tipo (PART, SOFTWARE o DOCUMENT; una pieza sin tipo es una PART).
ALTER TABLE pieza ADD COLUMN revision  INTEGER;
ALTER TABLE pieza ADD COLUMN estado    VARCHAR(32);
ALTER TABLE pieza ADD COLUMN masa_kg   NUMERIC(12, 3);
ALTER TABLE pieza ADD COLUMN material  VARCHAR(120);
ALTER TABLE pieza ADD COLUMN tipo      VARCHAR(16) NOT NULL DEFAULT 'PART';
