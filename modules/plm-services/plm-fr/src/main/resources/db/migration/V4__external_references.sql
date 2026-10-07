-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Références externes : une pièce utilise une pièce d'un autre atelier. L'atelier ne copie jamais la pièce
-- distante ; il la désigne par son URN (urn:plm:<atelier>:part:<référence>) avec l'indice qu'il attend. Seule la
-- couche sémantique, qui voit les deux côtés, sait si la cible existe et si son indice est toujours le bon.
CREATE TABLE reference_externe (
    id              VARCHAR(16)    PRIMARY KEY,
    piece           VARCHAR(64)    NOT NULL REFERENCES piece (ref_piece),
    urn             VARCHAR(160)   NOT NULL,
    quantite        NUMERIC(12, 3) NOT NULL,
    indice_attendu  VARCHAR(8),
    note            VARCHAR(400)
);

CREATE INDEX reference_externe_piece_idx ON reference_externe (piece);
