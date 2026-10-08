-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Attributs d'une pièce : indice (A, B), état dans la langue de l'atelier, masse en kilogrammes, matière
-- et type de pièce (PART, SOFTWARE ou DOCUMENT ; une pièce sans type est une PART).
ALTER TABLE piece ADD COLUMN indice      VARCHAR(8);
ALTER TABLE piece ADD COLUMN etat        VARCHAR(32);
ALTER TABLE piece ADD COLUMN masse_kg    NUMERIC(12, 3);
ALTER TABLE piece ADD COLUMN matiere     VARCHAR(120);
ALTER TABLE piece ADD COLUMN type_piece  VARCHAR(16) NOT NULL DEFAULT 'PART';
