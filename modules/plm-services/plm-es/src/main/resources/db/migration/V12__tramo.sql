-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Tramo: la extensión de la pieza a lo largo del eje de estaciones del producto, en milímetros, calculada a partir de
-- su geometría CAD y nunca introducida. En la fila de la pieza, la extensión de la pieza tal como la dibuja su fichero
-- CAD; en una posición del documento lista_materiales ("tramo_desde_mm", "tramo_hasta_mm"), la del uso colocado.
-- Vacíos para un producto sin estaciones, un conjunto o una pieza sin geometría.
ALTER TABLE pieza ADD COLUMN tramo_desde_mm  NUMERIC(12, 3);
ALTER TABLE pieza ADD COLUMN tramo_hasta_mm  NUMERIC(12, 3);
ALTER TABLE posicion_lista_materiales ADD COLUMN tramo_desde_mm  NUMERIC(12, 3);
ALTER TABLE posicion_lista_materiales ADD COLUMN tramo_hasta_mm  NUMERIC(12, 3);

-- La tabla de posiciones lleva también el tramo de cada posición.
CREATE OR REPLACE FUNCTION posicion_lista_materiales_calcular(piezas VARCHAR(64)[]) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF piezas IS NULL OR cardinality(piezas) = 0 THEN
        RETURN;
    END IF;
    DELETE FROM posicion_lista_materiales WHERE padre = ANY (piezas);
    INSERT INTO posicion_lista_materiales (padre, referencia, orden, x_mm, y_mm, z_mm, rx_grados, ry_grados, rz_grados,
                                           tramo_desde_mm, tramo_hasta_mm)
    SELECT p.cod_pieza,
           CAST(linea ->> 'referencia' AS VARCHAR(64)),
           CAST(posicion.orden AS INTEGER),
           CAST(posicion.valor ->> 'x_mm' AS NUMERIC(12, 4)),
           CAST(posicion.valor ->> 'y_mm' AS NUMERIC(12, 4)),
           CAST(posicion.valor ->> 'z_mm' AS NUMERIC(12, 4)),
           CAST(posicion.valor ->> 'rx_grados' AS NUMERIC(9, 4)),
           CAST(posicion.valor ->> 'ry_grados' AS NUMERIC(9, 4)),
           CAST(posicion.valor ->> 'rz_grados' AS NUMERIC(9, 4)),
           CAST(posicion.valor ->> 'tramo_desde_mm' AS NUMERIC(12, 3)),
           CAST(posicion.valor ->> 'tramo_hasta_mm' AS NUMERIC(12, 3))
    FROM pieza p
    CROSS JOIN LATERAL jsonb_array_elements(p.lista_materiales -> 'lineas') AS linea
    CROSS JOIN LATERAL jsonb_array_elements(linea -> 'posiciones') WITH ORDINALITY AS posicion (valor, orden)
    WHERE p.cod_pieza = ANY (piezas) AND p.lista_materiales IS NOT NULL AND jsonb_typeof(linea -> 'posiciones') = 'array';
END;
$$;

SELECT posicion_lista_materiales_calcular(ARRAY(SELECT cod_pieza FROM pieza));
