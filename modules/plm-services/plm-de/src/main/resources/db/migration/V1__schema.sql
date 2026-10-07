-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Native German PLM schema: German table and column names, positions and lengths in millimetres,
-- pressures in bar. The part table carries no export classification: a part's jurisdiction and
-- releasability live in atelier_core.part_tag, keyed by the part's teil_nr.

-- Bauteile: eine Zeile je veröffentlichtem Bauteil, mit dem Pfad seiner CAD-Datei.
CREATE TABLE bauteil (
    teil_nr    VARCHAR(64)  PRIMARY KEY,
    benennung  VARCHAR(255) NOT NULL,
    cad_datei  VARCHAR(512)
);

-- Elektrische Stecker eines Bauteils, Position im Produktkoordinatensystem.
CREATE TABLE stecker (
    stecker_id  VARCHAR(64) PRIMARY KEY,
    teil_nr     VARCHAR(64) NOT NULL REFERENCES bauteil (teil_nr),
    pos_x_mm    NUMERIC(12, 3) NOT NULL,
    pos_y_mm    NUMERIC(12, 3) NOT NULL,
    pos_z_mm    NUMERIC(12, 3) NOT NULL,
    typ         VARCHAR(64),
    polzahl     INTEGER
);

CREATE INDEX stecker_teil_nr_idx ON stecker (teil_nr);

-- Befestiger (Niet- oder Schraubenreihen) eines Bauteils: Norm, Durchmesser, Anzahl und Klemmlänge.
CREATE TABLE befestiger (
    befestiger_id   VARCHAR(64) PRIMARY KEY,
    teil_nr         VARCHAR(64) NOT NULL REFERENCES bauteil (teil_nr),
    pos_x_mm        NUMERIC(12, 3) NOT NULL,
    pos_y_mm        NUMERIC(12, 3) NOT NULL,
    pos_z_mm        NUMERIC(12, 3) NOT NULL,
    norm            VARCHAR(64),
    durchmesser_mm  NUMERIC(8, 3),
    anzahl          INTEGER,
    klemmlaenge_mm  NUMERIC(8, 3)
);

CREATE INDEX befestiger_teil_nr_idx ON befestiger (teil_nr);

-- Hydraulikkupplungen eines Bauteils: Norm, Dash-Größe, Nenndruck und Fluid.
CREATE TABLE hydraulikkupplung (
    kupplung_id    VARCHAR(64) PRIMARY KEY,
    teil_nr        VARCHAR(64) NOT NULL REFERENCES bauteil (teil_nr),
    pos_x_mm       NUMERIC(12, 3) NOT NULL,
    pos_y_mm       NUMERIC(12, 3) NOT NULL,
    pos_z_mm       NUMERIC(12, 3) NOT NULL,
    norm           VARCHAR(64),
    dash_groesse   INTEGER,
    nenndruck_bar  NUMERIC(10, 3),
    fluid          VARCHAR(64)
);

CREATE INDEX hydraulikkupplung_teil_nr_idx ON hydraulikkupplung (teil_nr);
