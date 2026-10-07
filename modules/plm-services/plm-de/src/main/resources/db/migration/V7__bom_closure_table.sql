-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Teilestruktur als Tabelle: jedes Paar (Vorfahr, Nachfahr) der Stückliste auf der Bauteilzeile, auch (Teil, Teil),
-- mit der kürzesten Tiefe. Die semantische Schicht fragt eine Baugruppe nach allem, was sie enthält; die Abfrage nach
-- einer Wurzel ist damit ein Indexzugriff, keine Rekursion über die ganze Stückliste. Trigger auf bauteil halten die
-- Tabelle aktuell: eine Änderung berechnet nur die Paare des betroffenen Teilbaums neu.
DROP VIEW teilestruktur;

CREATE TABLE teilestruktur (
    vorfahr   VARCHAR(64) NOT NULL,
    nachfahr  VARCHAR(64) NOT NULL,
    tiefe     INTEGER     NOT NULL,
    PRIMARY KEY (vorfahr, nachfahr)
);

CREATE INDEX teilestruktur_nachfahr_idx ON teilestruktur (nachfahr);

-- Berechnet die Paare neu, deren Nachfahr unter einem der Keime liegt (der Keim eingeschlossen): nur durch eine Kante
-- in diesen Teilbaum kann sich ein Paar ändern. Die Paare werden von jedem betroffenen Teil aus nach oben gesucht;
-- CYCLE beendet die Suche auch bei einem Zyklus.
CREATE FUNCTION teilestruktur_berechnen(keime VARCHAR(64)[]) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    betroffen VARCHAR(64)[];
BEGIN
    IF keime IS NULL OR cardinality(keime) = 0 THEN
        RETURN;
    END IF;
    WITH RECURSIVE unten (teil) AS (
        SELECT unnest(keime)
        UNION
        SELECT b.teil_nr FROM unten u JOIN bauteil b ON b.parent_id = u.teil
    )
    SELECT array_agg(teil) INTO betroffen FROM unten;

    DELETE FROM teilestruktur WHERE nachfahr = ANY (betroffen);
    INSERT INTO teilestruktur (vorfahr, nachfahr, tiefe)
    WITH RECURSIVE oben (vorfahr, nachfahr, tiefe) AS (
        SELECT teil_nr, teil_nr, 0 FROM bauteil WHERE teil_nr = ANY (betroffen)
        UNION ALL
        SELECT b.parent_id, o.nachfahr, o.tiefe + 1
        FROM oben o
        JOIN bauteil b ON b.teil_nr = o.vorfahr
        WHERE b.parent_id IS NOT NULL
    ) CYCLE vorfahr SET zyklus USING pfad
    SELECT vorfahr, nachfahr, min(tiefe) FROM oben WHERE NOT zyklus GROUP BY vorfahr, nachfahr;
END;
$$;

-- Keime einer Anweisung auf bauteil: die eingefügten und gelöschten Teile, bei einer Änderung die Teile, deren
-- übergeordnetes Teil oder Nummer sich ändert.
CREATE FUNCTION teilestruktur_nachfuehren() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM teilestruktur_berechnen(ARRAY(SELECT teil_nr FROM neu));
    ELSIF TG_OP = 'DELETE' THEN
        PERFORM teilestruktur_berechnen(ARRAY(SELECT teil_nr FROM alt));
    ELSE
        PERFORM teilestruktur_berechnen(ARRAY(
            SELECT n.teil_nr FROM neu n LEFT JOIN alt a ON a.teil_nr = n.teil_nr
            WHERE a.teil_nr IS NULL OR a.parent_id IS DISTINCT FROM n.parent_id
            UNION
            SELECT a.teil_nr FROM alt a WHERE NOT EXISTS (SELECT 1 FROM neu n WHERE n.teil_nr = a.teil_nr)));
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER teilestruktur_einfuegen AFTER INSERT ON bauteil
    REFERENCING NEW TABLE AS neu FOR EACH STATEMENT EXECUTE FUNCTION teilestruktur_nachfuehren();
CREATE TRIGGER teilestruktur_aendern AFTER UPDATE ON bauteil
    REFERENCING OLD TABLE AS alt NEW TABLE AS neu FOR EACH STATEMENT EXECUTE FUNCTION teilestruktur_nachfuehren();
CREATE TRIGGER teilestruktur_loeschen AFTER DELETE ON bauteil
    REFERENCING OLD TABLE AS alt FOR EACH STATEMENT EXECUTE FUNCTION teilestruktur_nachfuehren();

SELECT teilestruktur_berechnen(ARRAY(SELECT teil_nr FROM bauteil));
