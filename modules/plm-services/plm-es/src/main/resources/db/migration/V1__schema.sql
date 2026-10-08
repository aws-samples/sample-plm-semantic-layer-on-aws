-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Native Spanish PLM schema: Spanish table and column names, positions and lengths in millimetres,
-- pressures in bar. The part table carries no export classification: a part's jurisdiction and
-- releasability live in atelier_core.part_tag, keyed by the part's cod_pieza.

-- Piezas: una fila por pieza publicada, con la ruta de su fichero CAD.
CREATE TABLE pieza (
    cod_pieza     VARCHAR(64)  PRIMARY KEY,
    denominacion  VARCHAR(255) NOT NULL,
    fichero_cad   VARCHAR(512)
);

-- Conectores eléctricos de una pieza, posición en el sistema de referencia del producto. obsoleto is a
-- native flag of the Spanish PLM that the integration layer does not map: it is in neither the catalogue
-- nor the R2RML mapping.
CREATE TABLE conector (
    cod_conector   VARCHAR(64) PRIMARY KEY,
    cod_pieza      VARCHAR(64) NOT NULL REFERENCES pieza (cod_pieza),
    pos_x_mm       NUMERIC(12, 3) NOT NULL,
    pos_y_mm       NUMERIC(12, 3) NOT NULL,
    pos_z_mm       NUMERIC(12, 3) NOT NULL,
    tipo           VARCHAR(64),
    num_contactos  INTEGER,
    obsoleto       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX conector_cod_pieza_idx ON conector (cod_pieza);

-- Remaches (filas de remaches o tornillos) de una pieza: norma, diámetro, cantidad y longitud de apriete.
CREATE TABLE remache (
    cod_remache          VARCHAR(64) PRIMARY KEY,
    cod_pieza            VARCHAR(64) NOT NULL REFERENCES pieza (cod_pieza),
    pos_x_mm             NUMERIC(12, 3) NOT NULL,
    pos_y_mm             NUMERIC(12, 3) NOT NULL,
    pos_z_mm             NUMERIC(12, 3) NOT NULL,
    norma                VARCHAR(64),
    diametro_mm          NUMERIC(8, 3),
    cantidad             INTEGER,
    longitud_apriete_mm  NUMERIC(8, 3)
);

CREATE INDEX remache_cod_pieza_idx ON remache (cod_pieza);

-- Acoplamientos hidráulicos de una pieza: norma, tamaño dash, presión nominal y fluido.
CREATE TABLE acoplamiento (
    cod_acoplamiento  VARCHAR(64) PRIMARY KEY,
    cod_pieza         VARCHAR(64) NOT NULL REFERENCES pieza (cod_pieza),
    pos_x_mm          NUMERIC(12, 3) NOT NULL,
    pos_y_mm          NUMERIC(12, 3) NOT NULL,
    pos_z_mm          NUMERIC(12, 3) NOT NULL,
    norma             VARCHAR(64),
    tamano_dash       INTEGER,
    presion_bar       NUMERIC(10, 3),
    fluido            VARCHAR(64)
);

CREATE INDEX acoplamiento_cod_pieza_idx ON acoplamiento (cod_pieza);
