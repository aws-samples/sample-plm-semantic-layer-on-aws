-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Lista de materiales: un documento JSONB versionado en la fila de la pieza padre (un conjunto o el kit del
-- taller), {"version": n, "lineas": [{"referencia": ..., "cantidad": ...}]}. Los conjuntos y los kits son
-- piezas de tipo ASSEMBLY sin fichero CAD.
ALTER TABLE pieza ADD COLUMN lista_materiales JSONB;

-- Las líneas del documento como relación, una fila por línea: la capa semántica mapea relaciones, no JSON.
CREATE VIEW linea_lista_materiales AS
SELECT p.cod_pieza                                 AS padre,
       CAST(linea ->> 'referencia' AS VARCHAR(64)) AS referencia,
       CAST(linea ->> 'cantidad' AS NUMERIC(12, 3)) AS cantidad
FROM pieza p
CROSS JOIN LATERAL jsonb_array_elements(p.lista_materiales -> 'lineas') AS linea
WHERE p.lista_materiales IS NOT NULL;
