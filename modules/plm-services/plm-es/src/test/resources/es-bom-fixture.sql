-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- The Spanish part table with its bill-of-materials document, as V1, V2, V3, V5, V8 and V9 shape it, and the steam
-- engine's site kit ES-3300 with its six parts, rows copied from R__products_seed.sql. H2 has no JSONB, so the
-- document is text and linea_lista_materiales, a view over the document in PostgreSQL, is a table holding
-- the same lines.
CREATE TABLE pieza (
    cod_pieza         VARCHAR(64)  PRIMARY KEY,
    denominacion      VARCHAR(255) NOT NULL,
    fichero_cad       VARCHAR(512),
    revision          INTEGER,
    estado            VARCHAR(32),
    masa_kg           NUMERIC(12, 3),
    material          VARCHAR(120),
    tipo              VARCHAR(16)  NOT NULL DEFAULT 'PART',
    lista_materiales  VARCHAR(4000),
    norma             VARCHAR(64),
    diametro_nominal  NUMERIC(12, 3),
    longitud_nominal  NUMERIC(12, 3),
    unidad_medida     VARCHAR(16),
    numero_dientes    INTEGER,
    modulo_mm         NUMERIC(8, 3),
    clase_articulo    VARCHAR(16),
    diametro_interior NUMERIC(8, 3),
    seccion           NUMERIC(8, 3),
    compuesto         VARCHAR(120),
    leyenda           VARCHAR(120),
    ancho             NUMERIC(8, 3),
    alto              NUMERIC(8, 3),
    soporte           VARCHAR(120),
    adhesivo          VARCHAR(120),
    ancho_base        NUMERIC(8, 3),
    fondo             NUMERIC(8, 3),
    ancho_contorno    NUMERIC(8, 3),
    material_casco    VARCHAR(120),
    diametro_exterior NUMERIC(8, 3),
    ancho_seccion     NUMERIC(8, 3),
    diametro_llanta   NUMERIC(8, 3),
    indice_telas      INTEGER,
    diametro_disipador NUMERIC(8, 3),
    numero_rotores    INTEGER,
    vida_util_meses   INTEGER,
    tramo_desde_mm    NUMERIC(12, 3),
    tramo_hasta_mm    NUMERIC(12, 3),
    opcion            VARCHAR(64)
);

CREATE TABLE linea_lista_materiales (
    padre       VARCHAR(64),
    referencia  VARCHAR(64),
    cantidad    NUMERIC(12, 3)
);

INSERT INTO pieza (cod_pieza, denominacion, fichero_cad, revision, estado, masa_kg, material, tipo, lista_materiales, norma, diametro_nominal, longitud_nominal, unidad_medida) VALUES
    ('ES-3300', 'Kit lado vapor', NULL, 2, 'Liberado', NULL, NULL, 'ASSEMBLY', '{"version": 2, "lineas": [{"referencia": "ES-3301", "cantidad": 1}, {"referencia": "ES-3302", "cantidad": 1}, {"referencia": "ES-3303", "cantidad": 1}, {"referencia": "ES-3304", "cantidad": 1}, {"referencia": "ES-3305", "cantidad": 1}, {"referencia": "ES-3306", "cantidad": 1}]}', NULL, NULL, NULL, NULL),
    ('ES-3301', 'Caldera', 'cad/steam-engine/es-boiler.stp', 2, 'Liberado', 3850, 'chapa de acero P265GH', 'PART', NULL, NULL, NULL, NULL, NULL),
    ('ES-3302', 'Válvula de regulación', 'cad/steam-engine/es-throttle-valve.stp', 1, 'Liberado', 96, 'fundición gris EN-GJL-250', 'PART', NULL, NULL, NULL, NULL, NULL),
    ('ES-3303', 'Tubería de vapor', 'cad/steam-engine/es-steam-pipe.stp', 1, 'Liberado', 162, 'acero P235GH', 'PART', NULL, NULL, NULL, NULL, NULL),
    ('ES-3304', 'Tubería de escape', 'cad/steam-engine/es-eduction-pipe.stp', 1, 'Liberado', 44, 'acero P235GH', 'PART', NULL, NULL, NULL, NULL, NULL),
    ('ES-3305', 'Condensador', 'cad/steam-engine/es-condenser.stp', 1, 'Liberado', 525, 'fundición gris EN-GJL-250', 'PART', NULL, NULL, NULL, NULL, NULL),
    ('ES-3306', 'Bomba de aire', 'cad/steam-engine/es-air-pump.stp', 1, 'Liberado', 486, 'fundición gris EN-GJL-250 / bronce', 'PART', NULL, NULL, NULL, NULL, NULL);

INSERT INTO linea_lista_materiales (padre, referencia, cantidad) VALUES
    ('ES-3300', 'ES-3301', 1),
    ('ES-3300', 'ES-3302', 1),
    ('ES-3300', 'ES-3303', 1),
    ('ES-3300', 'ES-3304', 1),
    ('ES-3300', 'ES-3305', 1),
    ('ES-3300', 'ES-3306', 1);
