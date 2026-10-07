-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Referencias externas: una pieza usa una pieza de otro taller. El taller nunca copia la pieza remota; la nombra
-- por su URN (urn:plm:<taller>:part:<código>) con la revisión que espera. Solo la capa semántica, que ve ambos
-- lados, sabe si el destino existe y si su revisión sigue vigente.
CREATE TABLE referencia_externa (
    id                 VARCHAR(16)    PRIMARY KEY,
    pieza              VARCHAR(64)    NOT NULL REFERENCES pieza (cod_pieza),
    urn                VARCHAR(160)   NOT NULL,
    cantidad           NUMERIC(12, 3) NOT NULL,
    revision_esperada  VARCHAR(8),
    nota               VARCHAR(400)
);

CREATE INDEX referencia_externa_pieza_idx ON referencia_externa (pieza);
