-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Fermeture de la nomenclature : chaque couple (ascendant, descendant) des liens de la nomenclature, y compris
-- (pièce, pièce). La couche sémantique demande à un ensemble tout ce qu'il contient et la base résout l'arbre ici :
-- la couche ne liste jamais les pièces d'un produit. Une pièce peut avoir plusieurs parents ; UNION ne garde
-- chaque couple qu'une fois et termine la récursion même sur un cycle.
CREATE VIEW nomenclature_fermeture AS
WITH RECURSIVE fermeture (ascendant, descendant) AS (
    SELECT ref_piece, ref_piece FROM piece
    UNION
    SELECT f.ascendant, n.enfant
    FROM fermeture f
    JOIN nomenclature n ON n.parent = f.descendant
)
SELECT CAST(ascendant AS VARCHAR(64)) AS ascendant, CAST(descendant AS VARCHAR(64)) AS descendant FROM fermeture;
