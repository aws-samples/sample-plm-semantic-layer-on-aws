-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Stückliste als Baum auf der Bauteilzeile: jedes Bauteil nennt sein übergeordnetes Teil (eine Baugruppe
-- oder den Bausatz des Werks) und die Menge, die es davon verwendet. Ein Bausatz hat kein übergeordnetes
-- Teil; ein Teil hat höchstens eines. Baugruppen und Bausätze sind Bauteile der Teileart ASSEMBLY ohne CAD-Datei.
ALTER TABLE bauteil ADD COLUMN parent_id  VARCHAR(64) REFERENCES bauteil (teil_nr);
ALTER TABLE bauteil ADD COLUMN menge      NUMERIC(12, 3);

CREATE INDEX bauteil_parent_id_idx ON bauteil (parent_id);
