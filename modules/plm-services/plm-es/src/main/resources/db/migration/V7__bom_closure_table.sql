-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Cierre de la lista de materiales como tabla: cada par (ascendiente, descendiente) de las líneas de los documentos,
-- también (pieza, pieza), con la profundidad más corta. La capa semántica pide a un conjunto todo lo que contiene; la
-- búsqueda por raíz es así un acceso por índice, no una recursión sobre toda la lista. Un disparador sobre pieza
-- mantiene la tabla al día: un cambio de documento solo recalcula los pares del subárbol afectado.
DROP VIEW cierre_lista_materiales;

CREATE TABLE cierre_lista_materiales (
    ascendiente   VARCHAR(64) NOT NULL,
    descendiente  VARCHAR(64) NOT NULL,
    profundidad   INTEGER     NOT NULL,
    PRIMARY KEY (ascendiente, descendiente)
);

CREATE INDEX cierre_lista_materiales_descendiente_idx ON cierre_lista_materiales (descendiente);
-- Los padres de una pieza son los documentos que la nombran en una línea: el índice responde a la contención.
CREATE INDEX pieza_lista_materiales_idx ON pieza USING gin (lista_materiales jsonb_path_ops);

-- Recalcula los pares cuyo descendiente está bajo una de las semillas (la semilla incluida): solo una línea que entra
-- en ese subárbol puede cambiar un par. Una pieza puede estar en varios documentos: cada par guarda la profundidad más
-- corta. Los pares se buscan hacia arriba desde cada pieza afectada; CYCLE termina la búsqueda incluso ante un ciclo.
-- Una línea puede nombrar una referencia sin fila en pieza: tiene sus ascendientes, no el par consigo misma.
CREATE FUNCTION cierre_lista_materiales_calcular(semillas VARCHAR(64)[]) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    afectadas VARCHAR(64)[];
BEGIN
    IF semillas IS NULL OR cardinality(semillas) = 0 THEN
        RETURN;
    END IF;
    WITH RECURSIVE debajo (pieza) AS (
        SELECT unnest(semillas)
        UNION
        SELECT CAST(linea ->> 'referencia' AS VARCHAR(64))
        FROM debajo d
        JOIN pieza p ON p.cod_pieza = d.pieza
        CROSS JOIN LATERAL jsonb_array_elements(p.lista_materiales -> 'lineas') AS linea
        WHERE p.lista_materiales IS NOT NULL
    )
    SELECT array_agg(pieza) INTO afectadas FROM debajo;

    DELETE FROM cierre_lista_materiales WHERE descendiente = ANY (afectadas);
    INSERT INTO cierre_lista_materiales (ascendiente, descendiente, profundidad)
    WITH RECURSIVE encima (ascendiente, descendiente, profundidad) AS (
        SELECT a, a, 0 FROM unnest(afectadas) AS a
        UNION ALL
        SELECT p.cod_pieza, e.descendiente, e.profundidad + 1
        FROM encima e
        JOIN pieza p ON p.lista_materiales @> jsonb_build_object('lineas', jsonb_build_array(jsonb_build_object('referencia', e.ascendiente)))
    ) CYCLE ascendiente SET en_ciclo USING camino
    SELECT ascendiente, descendiente, min(profundidad) FROM encima
    WHERE NOT en_ciclo AND (profundidad > 0 OR EXISTS (SELECT 1 FROM pieza p WHERE p.cod_pieza = encima.descendiente))
    GROUP BY ascendiente, descendiente;
END;
$$;

-- Semillas de una instrucción sobre pieza: las piezas creadas o borradas y las referencias de sus documentos; en un
-- cambio, las piezas cuyo documento o código cambia y las referencias del documento anterior y del nuevo.
CREATE FUNCTION cierre_lista_materiales_pieza() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM cierre_lista_materiales_calcular(ARRAY(
            SELECT cod_pieza FROM nuevas
            UNION SELECT linea ->> 'referencia' FROM nuevas CROSS JOIN LATERAL jsonb_array_elements(lista_materiales -> 'lineas') AS linea));
    ELSIF TG_OP = 'DELETE' THEN
        PERFORM cierre_lista_materiales_calcular(ARRAY(
            SELECT cod_pieza FROM anteriores
            UNION SELECT linea ->> 'referencia' FROM anteriores CROSS JOIN LATERAL jsonb_array_elements(lista_materiales -> 'lineas') AS linea));
    ELSE
        PERFORM cierre_lista_materiales_calcular(ARRAY(
            WITH cambiadas AS (
                SELECT n.cod_pieza, n.lista_materiales AS nueva, a.lista_materiales AS anterior
                FROM nuevas n FULL JOIN anteriores a ON a.cod_pieza = n.cod_pieza
                WHERE a.cod_pieza IS NULL OR n.cod_pieza IS NULL OR a.lista_materiales IS DISTINCT FROM n.lista_materiales
            )
            SELECT cod_pieza FROM cambiadas WHERE cod_pieza IS NOT NULL
            UNION SELECT linea ->> 'referencia' FROM cambiadas CROSS JOIN LATERAL jsonb_array_elements(nueva -> 'lineas') AS linea
            UNION SELECT linea ->> 'referencia' FROM cambiadas CROSS JOIN LATERAL jsonb_array_elements(anterior -> 'lineas') AS linea
            UNION SELECT a.cod_pieza FROM anteriores a WHERE NOT EXISTS (SELECT 1 FROM nuevas n WHERE n.cod_pieza = a.cod_pieza)));
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER cierre_lista_materiales_creada AFTER INSERT ON pieza
    REFERENCING NEW TABLE AS nuevas FOR EACH STATEMENT EXECUTE FUNCTION cierre_lista_materiales_pieza();
CREATE TRIGGER cierre_lista_materiales_cambiada AFTER UPDATE ON pieza
    REFERENCING OLD TABLE AS anteriores NEW TABLE AS nuevas FOR EACH STATEMENT EXECUTE FUNCTION cierre_lista_materiales_pieza();
CREATE TRIGGER cierre_lista_materiales_borrada AFTER DELETE ON pieza
    REFERENCING OLD TABLE AS anteriores FOR EACH STATEMENT EXECUTE FUNCTION cierre_lista_materiales_pieza();

SELECT cierre_lista_materiales_calcular(ARRAY(SELECT cod_pieza FROM pieza));
