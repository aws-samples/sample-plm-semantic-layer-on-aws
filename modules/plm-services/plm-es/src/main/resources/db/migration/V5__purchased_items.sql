-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Piezas compradas: la norma del artículo y su medida nominal (diámetro y longitud) en la fila de la pieza, con la
-- unidad del catálogo del que se compra (MilliM o IN, nombre local de la unidad QUDT); vacías para una pieza fabricada.
ALTER TABLE pieza ADD COLUMN norma             VARCHAR(64);
ALTER TABLE pieza ADD COLUMN diametro_nominal  NUMERIC(8, 3);
ALTER TABLE pieza ADD COLUMN longitud_nominal  NUMERIC(8, 3);
ALTER TABLE pieza ADD COLUMN unidad_medida     VARCHAR(16);

-- Proveedores del taller.
CREATE TABLE proveedor (
    cod_proveedor  VARCHAR(64)  PRIMARY KEY,
    nombre         VARCHAR(255) NOT NULL,
    ciudad         VARCHAR(120)
);

-- Piezas de proveedor: la oferta de un proveedor para una pieza, con su referencia, el plazo en días y si es la
-- preferida. Una pieza puede tener varios proveedores.
CREATE TABLE pieza_proveedor (
    id                    INTEGER      PRIMARY KEY,
    cod_pieza             VARCHAR(64)  NOT NULL REFERENCES pieza (cod_pieza),
    cod_proveedor         VARCHAR(64)  NOT NULL REFERENCES proveedor (cod_proveedor),
    referencia_proveedor  VARCHAR(64)  NOT NULL,
    plazo_dias            INTEGER      NOT NULL,
    preferido             BOOLEAN      NOT NULL
);

CREATE INDEX pieza_proveedor_cod_pieza_idx ON pieza_proveedor (cod_pieza);
