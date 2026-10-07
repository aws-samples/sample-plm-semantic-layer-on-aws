-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Zahnräder: Zähnezahl und Modul in Millimetern auf der Bauteilzeile, leer für ein Bauteil ohne Verzahnung. Ein
-- Stufenrad mit mehreren Verzahnungen trägt nur den gemeinsamen Modul.
ALTER TABLE bauteil ADD COLUMN zaehnezahl  INTEGER;
ALTER TABLE bauteil ADD COLUMN modul_mm    NUMERIC(8, 3);
