-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Native French PLM schema: French table and column names, positions and lengths in millimetres,
-- pressures in bar. The part table carries no export classification: a part's jurisdiction and
-- releasability live in atelier_core.part_tag, keyed by the part's ref_piece.

-- Pièces : une ligne par pièce publiée, avec le chemin de son fichier CAO.
CREATE TABLE piece (
    ref_piece    VARCHAR(64)  PRIMARY KEY,
    designation  VARCHAR(255) NOT NULL,
    fichier_cao  VARCHAR(512)
);

-- Connecteurs électriques d'une pièce, position dans le repère produit.
CREATE TABLE connecteur (
    id_connecteur    VARCHAR(64) PRIMARY KEY,
    ref_piece        VARCHAR(64) NOT NULL REFERENCES piece (ref_piece),
    pos_x_mm         NUMERIC(12, 3) NOT NULL,
    pos_y_mm         NUMERIC(12, 3) NOT NULL,
    pos_z_mm         NUMERIC(12, 3) NOT NULL,
    type_connecteur  VARCHAR(64),
    nb_broches       INTEGER
);

CREATE INDEX connecteur_ref_piece_idx ON connecteur (ref_piece);

-- Fixations (rangées de rivets ou de boulons) d'une pièce : norme, diamètre, nombre et longueur de serrage.
CREATE TABLE fixation (
    ref_fixation         VARCHAR(64) PRIMARY KEY,
    ref_piece            VARCHAR(64) NOT NULL REFERENCES piece (ref_piece),
    pos_x_mm             NUMERIC(12, 3) NOT NULL,
    pos_y_mm             NUMERIC(12, 3) NOT NULL,
    pos_z_mm             NUMERIC(12, 3) NOT NULL,
    norme                VARCHAR(64),
    diametre_mm          NUMERIC(8, 3),
    nombre               INTEGER,
    longueur_serrage_mm  NUMERIC(8, 3)
);

CREATE INDEX fixation_ref_piece_idx ON fixation (ref_piece);

-- Raccords hydrauliques d'une pièce : norme, taille dash, pression nominale et fluide.
CREATE TABLE raccord_hydraulique (
    ref_raccord   VARCHAR(64) PRIMARY KEY,
    ref_piece     VARCHAR(64) NOT NULL REFERENCES piece (ref_piece),
    pos_x_mm      NUMERIC(12, 3) NOT NULL,
    pos_y_mm      NUMERIC(12, 3) NOT NULL,
    pos_z_mm      NUMERIC(12, 3) NOT NULL,
    norme         VARCHAR(64),
    taille_dash   INTEGER,
    pression_bar  NUMERIC(10, 3),
    fluide        VARCHAR(64)
);

CREATE INDEX raccord_hydraulique_ref_piece_idx ON raccord_hydraulique (ref_piece);
