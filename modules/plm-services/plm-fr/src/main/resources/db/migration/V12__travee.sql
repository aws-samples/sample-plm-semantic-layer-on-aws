-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Travée : l'étendue de la pièce le long de l'axe des stations du produit, en millimètres, calculée depuis sa
-- géométrie CAO et jamais saisie. Sur la ligne de la pièce, l'étendue de la pièce telle que son fichier CAO la dessine ;
-- sur une position de nomenclature, celle de l'utilisation placée. Vides pour un produit sans stations, un ensemble ou
-- une pièce sans géométrie.
ALTER TABLE piece ADD COLUMN travee_debut_mm  NUMERIC(12, 3);
ALTER TABLE piece ADD COLUMN travee_fin_mm    NUMERIC(12, 3);
ALTER TABLE nomenclature_position ADD COLUMN travee_debut_mm  NUMERIC(12, 3);
ALTER TABLE nomenclature_position ADD COLUMN travee_fin_mm    NUMERIC(12, 3);
