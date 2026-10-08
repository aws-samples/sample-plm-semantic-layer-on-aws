-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Positions de nomenclature : une ligne par utilisation de la pièce enfant d'un lien, numérotée par son rang. La
-- position est relative à la pièce telle que son fichier CAO la dessine : translation en millimètres, puis rotation en
-- degrés autour des axes fixes x, y et z du repère produit, dans cet ordre. Le rang 1 est la position du fichier CAO.
-- Un lien sans position s'utilise une fois, à la position du fichier CAO. Supprimer le lien supprime ses positions.
CREATE TABLE nomenclature_position (
    parent  VARCHAR(64)    NOT NULL,
    enfant  VARCHAR(64)    NOT NULL,
    rang    INTEGER        NOT NULL CHECK (rang >= 1),
    x_mm    NUMERIC(12, 4) NOT NULL,
    y_mm    NUMERIC(12, 4) NOT NULL,
    z_mm    NUMERIC(12, 4) NOT NULL,
    rx_deg  NUMERIC(9, 4)  NOT NULL,
    ry_deg  NUMERIC(9, 4)  NOT NULL,
    rz_deg  NUMERIC(9, 4)  NOT NULL,
    PRIMARY KEY (parent, enfant, rang),
    FOREIGN KEY (parent, enfant) REFERENCES nomenclature (parent, enfant) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE INDEX nomenclature_position_enfant_idx ON nomenclature_position (enfant);
