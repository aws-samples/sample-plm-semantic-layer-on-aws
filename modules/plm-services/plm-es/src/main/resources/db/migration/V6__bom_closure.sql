-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Cierre de la lista de materiales: cada par (ascendiente, descendiente) de las líneas de los documentos, leídas
-- por la vista linea_lista_materiales, también (pieza, pieza). La capa semántica pide a un conjunto todo lo que
-- contiene y la base resuelve el árbol aquí: la capa nunca lista las piezas de un producto. UNION termina la
-- recursión incluso ante un ciclo.
CREATE VIEW cierre_lista_materiales AS
WITH RECURSIVE cierre (ascendiente, descendiente) AS (
    SELECT cod_pieza, cod_pieza FROM pieza
    UNION
    SELECT c.ascendiente, l.referencia
    FROM cierre c
    JOIN linea_lista_materiales l ON l.padre = c.descendiente
)
SELECT CAST(ascendiente AS VARCHAR(64)) AS ascendiente, CAST(descendiente AS VARCHAR(64)) AS descendiente FROM cierre;
