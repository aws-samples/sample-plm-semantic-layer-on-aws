-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Klassen der Kaufteile: die Teileklasse (fastener, o-ring, placard, container, tyre, wheel, brake), die Maße eines
-- O-Rings (Innendurchmesser und Schnurstärke) und seine Mischung, Beschriftung, Format, Trägerwerkstoff und Klebstoff
-- eines Schilds, Maße und Schalenwerkstoff eines Containers, Maße und Lagenzahl eines Reifens, Felge und Breite eines
-- Rads, Wärmesenke und Rotoren einer Bremse, in Millimetern, und die Lagerdauer in Monaten; leer für ein
-- Eigenfertigungsteil oder ein Merkmal, das die Klasse nicht hat.
ALTER TABLE bauteil ADD COLUMN teileklasse                 VARCHAR(16);
ALTER TABLE bauteil ADD COLUMN innendurchmesser_mm         NUMERIC(8, 3);
ALTER TABLE bauteil ADD COLUMN schnurstaerke_mm            NUMERIC(8, 3);
ALTER TABLE bauteil ADD COLUMN mischung                    VARCHAR(120);
ALTER TABLE bauteil ADD COLUMN beschriftung                VARCHAR(120);
ALTER TABLE bauteil ADD COLUMN breite_mm                   NUMERIC(8, 3);
ALTER TABLE bauteil ADD COLUMN hoehe_mm                    NUMERIC(8, 3);
ALTER TABLE bauteil ADD COLUMN traegerwerkstoff            VARCHAR(120);
ALTER TABLE bauteil ADD COLUMN klebstoff                   VARCHAR(120);
ALTER TABLE bauteil ADD COLUMN bodenbreite_mm              NUMERIC(8, 3);
ALTER TABLE bauteil ADD COLUMN tiefe_mm                    NUMERIC(8, 3);
ALTER TABLE bauteil ADD COLUMN konturbreite_mm             NUMERIC(8, 3);
ALTER TABLE bauteil ADD COLUMN schalenwerkstoff            VARCHAR(120);
ALTER TABLE bauteil ADD COLUMN aussendurchmesser_mm        NUMERIC(8, 3);
ALTER TABLE bauteil ADD COLUMN querschnittsbreite_mm       NUMERIC(8, 3);
ALTER TABLE bauteil ADD COLUMN felgendurchmesser_mm        NUMERIC(8, 3);
ALTER TABLE bauteil ADD COLUMN lagenzahl                   INTEGER;
ALTER TABLE bauteil ADD COLUMN waermesenkendurchmesser_mm  NUMERIC(8, 3);
ALTER TABLE bauteil ADD COLUMN rotorzahl                   INTEGER;
ALTER TABLE bauteil ADD COLUMN lagerdauer_monate           INTEGER;
