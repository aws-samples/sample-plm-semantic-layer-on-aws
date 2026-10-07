-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Posiciones: cada línea del documento lista_materiales puede llevar "posiciones", una por uso de la pieza,
-- [{"x_mm": ..., "y_mm": ..., "z_mm": ..., "rx_grados": ..., "ry_grados": ..., "rz_grados": ...}, ...]. La posición es
-- relativa a la pieza tal como la dibuja su fichero CAD: traslación en milímetros, luego rotación en grados alrededor de
-- los ejes fijos x, y y z del marco del producto, en ese orden. La primera es la posición del fichero CAD; una línea sin
-- posiciones se usa una vez, en esa posición.

-- Las posiciones de los documentos como tabla, una fila por posición, numeradas desde 1 en el orden del documento. La
-- capa semántica lee cada posición con sus seis valores: con la clave de la tabla, la lectura es una fila por posición,
-- donde una vista sin clave se uniría consigo misma una vez por valor. Un disparador sobre pieza mantiene la tabla al
-- día: un cambio de documento solo recalcula las posiciones de esa pieza.
CREATE TABLE posicion_lista_materiales (
    padre       VARCHAR(64)    NOT NULL,
    referencia  VARCHAR(64)    NOT NULL,
    orden       INTEGER        NOT NULL,
    x_mm        NUMERIC(12, 4) NOT NULL,
    y_mm        NUMERIC(12, 4) NOT NULL,
    z_mm        NUMERIC(12, 4) NOT NULL,
    rx_grados   NUMERIC(9, 4)  NOT NULL,
    ry_grados   NUMERIC(9, 4)  NOT NULL,
    rz_grados   NUMERIC(9, 4)  NOT NULL,
    PRIMARY KEY (padre, referencia, orden)
);

CREATE INDEX posicion_lista_materiales_referencia_idx ON posicion_lista_materiales (referencia);

-- Recalcula las posiciones de los documentos de las piezas dadas.
CREATE FUNCTION posicion_lista_materiales_calcular(piezas VARCHAR(64)[]) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF piezas IS NULL OR cardinality(piezas) = 0 THEN
        RETURN;
    END IF;
    DELETE FROM posicion_lista_materiales WHERE padre = ANY (piezas);
    INSERT INTO posicion_lista_materiales (padre, referencia, orden, x_mm, y_mm, z_mm, rx_grados, ry_grados, rz_grados)
    SELECT p.cod_pieza,
           CAST(linea ->> 'referencia' AS VARCHAR(64)),
           CAST(posicion.orden AS INTEGER),
           CAST(posicion.valor ->> 'x_mm' AS NUMERIC(12, 4)),
           CAST(posicion.valor ->> 'y_mm' AS NUMERIC(12, 4)),
           CAST(posicion.valor ->> 'z_mm' AS NUMERIC(12, 4)),
           CAST(posicion.valor ->> 'rx_grados' AS NUMERIC(9, 4)),
           CAST(posicion.valor ->> 'ry_grados' AS NUMERIC(9, 4)),
           CAST(posicion.valor ->> 'rz_grados' AS NUMERIC(9, 4))
    FROM pieza p
    CROSS JOIN LATERAL jsonb_array_elements(p.lista_materiales -> 'lineas') AS linea
    CROSS JOIN LATERAL jsonb_array_elements(linea -> 'posiciones') WITH ORDINALITY AS posicion (valor, orden)
    WHERE p.cod_pieza = ANY (piezas) AND p.lista_materiales IS NOT NULL AND jsonb_typeof(linea -> 'posiciones') = 'array';
END;
$$;

-- Piezas de una instrucción sobre pieza: las creadas o borradas y, en un cambio, aquellas cuyo documento o código cambia.
CREATE FUNCTION posicion_lista_materiales_pieza() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM posicion_lista_materiales_calcular(ARRAY(SELECT cod_pieza FROM nuevas));
    ELSIF TG_OP = 'DELETE' THEN
        PERFORM posicion_lista_materiales_calcular(ARRAY(SELECT cod_pieza FROM anteriores));
    ELSE
        PERFORM posicion_lista_materiales_calcular(ARRAY(
            SELECT COALESCE(n.cod_pieza, a.cod_pieza) FROM nuevas n FULL JOIN anteriores a ON a.cod_pieza = n.cod_pieza
            WHERE a.cod_pieza IS NULL OR n.cod_pieza IS NULL OR a.lista_materiales IS DISTINCT FROM n.lista_materiales));
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER posicion_lista_materiales_creada AFTER INSERT ON pieza
    REFERENCING NEW TABLE AS nuevas FOR EACH STATEMENT EXECUTE FUNCTION posicion_lista_materiales_pieza();
CREATE TRIGGER posicion_lista_materiales_cambiada AFTER UPDATE ON pieza
    REFERENCING OLD TABLE AS anteriores NEW TABLE AS nuevas FOR EACH STATEMENT EXECUTE FUNCTION posicion_lista_materiales_pieza();
CREATE TRIGGER posicion_lista_materiales_borrada AFTER DELETE ON pieza
    REFERENCING OLD TABLE AS anteriores FOR EACH STATEMENT EXECUTE FUNCTION posicion_lista_materiales_pieza();

SELECT posicion_lista_materiales_calcular(ARRAY(SELECT cod_pieza FROM pieza));
