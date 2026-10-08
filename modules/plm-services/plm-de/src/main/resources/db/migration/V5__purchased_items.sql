-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Kaufteile: die Norm des Teils und seine Nenngröße (Gewindedurchmesser und Länge in Millimetern) auf der
-- Bauteilzeile, leer für ein Eigenfertigungsteil.
ALTER TABLE bauteil ADD COLUMN norm                VARCHAR(64);
ALTER TABLE bauteil ADD COLUMN nenndurchmesser_mm  NUMERIC(8, 3);
ALTER TABLE bauteil ADD COLUMN nennlaenge_mm       NUMERIC(8, 3);

-- Lieferanten des Werks.
CREATE TABLE lieferant (
    id    VARCHAR(64)  PRIMARY KEY,
    name  VARCHAR(255) NOT NULL,
    ort   VARCHAR(120)
);

-- Lieferantenteile: ein Angebot eines Lieferanten für ein Bauteil, mit seiner Teilenummer, der Lieferzeit in
-- Tagen und ob es bevorzugt ist. Ein Bauteil kann mehrere Lieferanten haben.
CREATE TABLE lieferantenteil (
    id                       INTEGER      PRIMARY KEY,
    teil_nr                  VARCHAR(64)  NOT NULL REFERENCES bauteil (teil_nr),
    lieferant_id             VARCHAR(64)  NOT NULL REFERENCES lieferant (id),
    lieferanten_teilenummer  VARCHAR(64)  NOT NULL,
    lieferzeit_tage          INTEGER      NOT NULL,
    bevorzugt                BOOLEAN      NOT NULL
);

CREATE INDEX lieferantenteil_teil_nr_idx ON lieferantenteil (teil_nr);
