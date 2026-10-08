-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Articles achetés : la norme de l'article et sa dimension nominale (diamètre et longueur en millimètres) sur la
-- ligne de la pièce, vides pour une pièce fabriquée.
ALTER TABLE piece ADD COLUMN norme                 VARCHAR(64);
ALTER TABLE piece ADD COLUMN diametre_nominal_mm   NUMERIC(8, 3);
ALTER TABLE piece ADD COLUMN longueur_nominale_mm  NUMERIC(8, 3);

-- Fournisseurs de l'atelier.
CREATE TABLE fournisseur (
    code_fournisseur  VARCHAR(64)  PRIMARY KEY,
    raison_sociale    VARCHAR(255) NOT NULL,
    ville             VARCHAR(120)
);

-- Articles fournisseur : l'offre d'un fournisseur pour une pièce, avec sa référence, le délai en jours et si
-- elle est préférée. Une pièce peut avoir plusieurs fournisseurs.
CREATE TABLE article_fournisseur (
    id                     INTEGER      PRIMARY KEY,
    ref_piece              VARCHAR(64)  NOT NULL REFERENCES piece (ref_piece),
    code_fournisseur       VARCHAR(64)  NOT NULL REFERENCES fournisseur (code_fournisseur),
    reference_fournisseur  VARCHAR(64)  NOT NULL,
    delai_jours            INTEGER      NOT NULL,
    prefere                BOOLEAN      NOT NULL
);

CREATE INDEX article_fournisseur_ref_piece_idx ON article_fournisseur (ref_piece);
