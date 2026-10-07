-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Clases de artículos comprados: la clase del artículo (fastener, o-ring, placard, container, tyre, wheel, brake), las
-- medidas de una junta tórica (diámetro interior y sección) y su compuesto, la leyenda, el formato, el soporte y el
-- adhesivo de una placa, las medidas y el material del casco de un contenedor, las medidas y el índice de telas de un
-- neumático, la llanta y el ancho de una rueda, el disipador y los rotores de un freno, en la unidad de unidad_medida,
-- y la vida útil en meses; vacíos para una pieza fabricada o un atributo que la clase no tiene.
ALTER TABLE pieza ADD COLUMN clase_articulo      VARCHAR(16);
ALTER TABLE pieza ADD COLUMN diametro_interior   NUMERIC(8, 3);
ALTER TABLE pieza ADD COLUMN seccion             NUMERIC(8, 3);
ALTER TABLE pieza ADD COLUMN compuesto           VARCHAR(120);
ALTER TABLE pieza ADD COLUMN leyenda             VARCHAR(120);
ALTER TABLE pieza ADD COLUMN ancho               NUMERIC(8, 3);
ALTER TABLE pieza ADD COLUMN alto                NUMERIC(8, 3);
ALTER TABLE pieza ADD COLUMN soporte             VARCHAR(120);
ALTER TABLE pieza ADD COLUMN adhesivo            VARCHAR(120);
ALTER TABLE pieza ADD COLUMN ancho_base          NUMERIC(8, 3);
ALTER TABLE pieza ADD COLUMN fondo               NUMERIC(8, 3);
ALTER TABLE pieza ADD COLUMN ancho_contorno      NUMERIC(8, 3);
ALTER TABLE pieza ADD COLUMN material_casco      VARCHAR(120);
ALTER TABLE pieza ADD COLUMN diametro_exterior   NUMERIC(8, 3);
ALTER TABLE pieza ADD COLUMN ancho_seccion       NUMERIC(8, 3);
ALTER TABLE pieza ADD COLUMN diametro_llanta     NUMERIC(8, 3);
ALTER TABLE pieza ADD COLUMN indice_telas        INTEGER;
ALTER TABLE pieza ADD COLUMN diametro_disipador  NUMERIC(8, 3);
ALTER TABLE pieza ADD COLUMN numero_rotores      INTEGER;
ALTER TABLE pieza ADD COLUMN vida_util_meses     INTEGER;
