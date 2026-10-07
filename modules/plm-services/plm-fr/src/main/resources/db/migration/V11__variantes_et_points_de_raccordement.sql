-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Variantes : le code de l'option sous laquelle seule une pièce ou une caractéristique existe, vide pour un article
-- de la configuration de base. Point de raccordement : le nom du point qu'une pièce hôte offre à un emplacement
-- d'installation, le même d'une option à l'autre.
ALTER TABLE piece               ADD COLUMN variante            VARCHAR(64);
ALTER TABLE connecteur          ADD COLUMN point_raccordement  VARCHAR(64);
ALTER TABLE connecteur          ADD COLUMN variante            VARCHAR(64);
ALTER TABLE fixation            ADD COLUMN point_raccordement  VARCHAR(64);
ALTER TABLE fixation            ADD COLUMN variante            VARCHAR(64);
ALTER TABLE raccord_hydraulique ADD COLUMN point_raccordement  VARCHAR(64);
ALTER TABLE raccord_hydraulique ADD COLUMN variante            VARCHAR(64);
