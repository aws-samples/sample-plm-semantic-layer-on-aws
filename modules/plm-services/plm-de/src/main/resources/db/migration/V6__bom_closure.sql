-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Teilestruktur: jedes Paar (Vorfahr, Nachfahr) der Stückliste auf der Bauteilzeile, auch (Teil, Teil). Die
-- semantische Schicht fragt eine Baugruppe nach allem, was sie enthält, und die Datenbank löst den Baum hier auf:
-- die Schicht listet nie die Teile eines Produkts. UNION statt UNION ALL beendet die Rekursion auch bei einem Zyklus.
CREATE VIEW teilestruktur AS
WITH RECURSIVE struktur (vorfahr, nachfahr) AS (
    SELECT teil_nr, teil_nr FROM bauteil
    UNION
    SELECT s.vorfahr, b.teil_nr
    FROM struktur s
    JOIN bauteil b ON b.parent_id = s.nachfahr
)
SELECT CAST(vorfahr AS VARCHAR(64)) AS vorfahr, CAST(nachfahr AS VARCHAR(64)) AS nachfahr FROM struktur;
