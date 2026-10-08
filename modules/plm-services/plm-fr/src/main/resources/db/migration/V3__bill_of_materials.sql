-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Nomenclature : une ligne par lien entre une pièce parente (un ensemble ou le kit de l'atelier) et une
-- pièce enfant, avec la quantité et le repère. Une pièce peut avoir plusieurs parents. Les ensembles et
-- les kits sont des pièces de type ASSEMBLY sans fichier CAO.
CREATE TABLE nomenclature (
    parent    VARCHAR(64)    NOT NULL REFERENCES piece (ref_piece),
    enfant    VARCHAR(64)    NOT NULL REFERENCES piece (ref_piece),
    quantite  NUMERIC(12, 3) NOT NULL,
    repere    VARCHAR(16),
    PRIMARY KEY (parent, enfant)
);

CREATE INDEX nomenclature_enfant_idx ON nomenclature (enfant);
