-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Each site's closure table against a full recomputation, on the fixture stack's PostgreSQL (run.sh: psql -v
-- ON_ERROR_STOP=1 < closure-maintenance.sql). The full recomputation walks every bill-of-materials line downwards
-- from every item, the shortest depth kept, independently of the triggers' upward search. After the seed the table
-- equals it; then, in a transaction rolled back, one line of the site's idiom moves an assembly with its subtree under
-- another assembly, and the table, kept by the triggers alone, equals it again. A mismatch raises an error. The pairs
-- of the full recomputation are those of the recursive view each table replaces.
\o /dev/null

\connect de_plm
CREATE TEMP VIEW volle_struktur AS
WITH RECURSIVE s (vorfahr, nachfahr, tiefe) AS (
    SELECT teil_nr, teil_nr, 0 FROM bauteil
    UNION ALL
    SELECT s.vorfahr, b.teil_nr, s.tiefe + 1 FROM s JOIN bauteil b ON b.parent_id = s.nachfahr
) CYCLE nachfahr SET zyklus USING pfad
SELECT vorfahr, nachfahr, min(tiefe) AS tiefe FROM s WHERE NOT zyklus GROUP BY vorfahr, nachfahr;
CREATE FUNCTION pg_temp.pruefen(fall TEXT) RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT * FROM volle_struktur EXCEPT SELECT * FROM teilestruktur)
       OR EXISTS (SELECT * FROM teilestruktur EXCEPT SELECT * FROM volle_struktur) THEN
        RAISE EXCEPTION 'de: teilestruktur differs from the full recomputation %', fall;
    END IF;
    RAISE NOTICE 'de: teilestruktur equals the full recomputation %: % pairs', fall, (SELECT count(*) FROM teilestruktur);
END;
$$;
SELECT pg_temp.pruefen('after the seed');
BEGIN;
UPDATE bauteil SET parent_id = 'D-37070' WHERE teil_nr = 'D-37074';
SELECT pg_temp.pruefen('after D-37074 moves from the gearbox D-37073 to D-37070');
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM teilestruktur WHERE vorfahr = 'D-37070' AND nachfahr = 'D-37074' AND tiefe = 1)
       OR EXISTS (SELECT 1 FROM teilestruktur WHERE vorfahr = 'D-37073' AND nachfahr = 'D-37074') THEN
        RAISE EXCEPTION 'de: the move is not in teilestruktur';
    END IF;
END $$;
ROLLBACK;

\connect fr_plm
CREATE TEMP VIEW fermeture_complete AS
WITH RECURSIVE f (ascendant, descendant, profondeur) AS (
    SELECT ref_piece, ref_piece, 0 FROM piece
    UNION ALL
    SELECT f.ascendant, n.enfant, f.profondeur + 1 FROM f JOIN nomenclature n ON n.parent = f.descendant
) CYCLE descendant SET en_cycle USING chemin
SELECT ascendant, descendant, min(profondeur) AS profondeur FROM f WHERE NOT en_cycle GROUP BY ascendant, descendant;
CREATE FUNCTION pg_temp.verifier(cas TEXT) RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT * FROM fermeture_complete EXCEPT SELECT * FROM nomenclature_fermeture)
       OR EXISTS (SELECT * FROM nomenclature_fermeture EXCEPT SELECT * FROM fermeture_complete) THEN
        RAISE EXCEPTION 'fr: nomenclature_fermeture differs from the full recomputation %', cas;
    END IF;
    RAISE NOTICE 'fr: nomenclature_fermeture equals the full recomputation %: % pairs', cas, (SELECT count(*) FROM nomenclature_fermeture);
END;
$$;
SELECT pg_temp.verifier('after the seed');
BEGIN;
UPDATE nomenclature SET parent = 'FR3775' WHERE parent = 'FR3771' AND enfant = 'FR3772';
SELECT pg_temp.verifier('after FR3772 moves from FR3771 to FR3775');
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM nomenclature_fermeture WHERE ascendant = 'FR3775' AND descendant = 'FR3772' AND profondeur = 1)
       OR EXISTS (SELECT 1 FROM nomenclature_fermeture WHERE ascendant = 'FR3771' AND descendant = 'FR3772') THEN
        RAISE EXCEPTION 'fr: the move is not in nomenclature_fermeture';
    END IF;
END $$;
ROLLBACK;

\connect uk_plm
CREATE TEMP VIEW full_closure AS
WITH RECURSIVE c (ancestor, descendant, depth) AS (
    SELECT comp_id, comp_id, 0 FROM component
    UNION ALL
    SELECT c.ancestor, b.child_part_no, c.depth + 1 FROM c JOIN bom_line b ON b.parent_part_no = c.descendant
) CYCLE descendant SET on_cycle USING path
SELECT ancestor, descendant, min(depth) AS depth FROM c WHERE NOT on_cycle GROUP BY ancestor, descendant;
CREATE FUNCTION pg_temp.check_closure(state TEXT) RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT * FROM full_closure EXCEPT SELECT * FROM bom_closure)
       OR EXISTS (SELECT * FROM bom_closure EXCEPT SELECT * FROM full_closure) THEN
        RAISE EXCEPTION 'uk: bom_closure differs from the full recomputation %', state;
    END IF;
    RAISE NOTICE 'uk: bom_closure equals the full recomputation %: % pairs', state, (SELECT count(*) FROM bom_closure);
END;
$$;
SELECT pg_temp.check_closure('after the seed');
BEGIN;
UPDATE bom_line SET parent_part_no = 'UK-3770' WHERE parent_part_no = 'UK-3775' AND child_part_no = 'UK-3776';
SELECT pg_temp.check_closure('after UK-3776 moves from UK-3775 to UK-3770');
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM bom_closure WHERE ancestor = 'UK-3770' AND descendant = 'UK-3776' AND depth = 1)
       OR EXISTS (SELECT 1 FROM bom_closure WHERE ancestor = 'UK-3775' AND descendant = 'UK-3776') THEN
        RAISE EXCEPTION 'uk: the move is not in bom_closure';
    END IF;
END $$;
ROLLBACK;

\connect es_plm
CREATE TEMP VIEW cierre_completo AS
WITH RECURSIVE c (ascendiente, descendiente, profundidad) AS (
    SELECT cod_pieza, cod_pieza, 0 FROM pieza
    UNION ALL
    SELECT c.ascendiente, l.referencia, c.profundidad + 1 FROM c JOIN linea_lista_materiales l ON l.padre = c.descendiente
) CYCLE descendiente SET en_ciclo USING camino
SELECT ascendiente, descendiente, min(profundidad) AS profundidad FROM c WHERE NOT en_ciclo GROUP BY ascendiente, descendiente;
CREATE FUNCTION pg_temp.comprobar(caso TEXT) RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT * FROM cierre_completo EXCEPT SELECT * FROM cierre_lista_materiales)
       OR EXISTS (SELECT * FROM cierre_lista_materiales EXCEPT SELECT * FROM cierre_completo) THEN
        RAISE EXCEPTION 'es: cierre_lista_materiales differs from the full recomputation %', caso;
    END IF;
    RAISE NOTICE 'es: cierre_lista_materiales equals the full recomputation %: % pairs', caso, (SELECT count(*) FROM cierre_lista_materiales);
END;
$$;
SELECT pg_temp.comprobar('after the seed');
BEGIN;
-- One statement moves the line: out of the document of ES-3774, onto the end of the document of ES-3771.
UPDATE pieza p SET lista_materiales = CASE p.cod_pieza
    WHEN 'ES-3774' THEN jsonb_set(p.lista_materiales, '{lineas}',
        (SELECT jsonb_agg(l) FROM jsonb_array_elements(p.lista_materiales -> 'lineas') AS l WHERE l ->> 'referencia' <> 'ES-3784'))
    ELSE jsonb_set(p.lista_materiales, '{lineas}', (p.lista_materiales -> 'lineas') || (
        SELECT jsonb_agg(l) FROM pieza o CROSS JOIN LATERAL jsonb_array_elements(o.lista_materiales -> 'lineas') AS l
        WHERE o.cod_pieza = 'ES-3774' AND l ->> 'referencia' = 'ES-3784'))
    END
WHERE p.cod_pieza IN ('ES-3774', 'ES-3771');
SELECT pg_temp.comprobar('after ES-3784 moves from ES-3774 to ES-3771');
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM cierre_lista_materiales WHERE ascendiente = 'ES-3771' AND descendiente = 'ES-3784' AND profundidad = 1)
       OR EXISTS (SELECT 1 FROM cierre_lista_materiales WHERE ascendiente = 'ES-3774' AND descendiente = 'ES-3784') THEN
        RAISE EXCEPTION 'es: the move is not in cierre_lista_materiales';
    END IF;
END $$;
ROLLBACK;
