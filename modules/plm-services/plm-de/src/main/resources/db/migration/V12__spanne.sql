-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Spanne: die Ausdehnung des Teils entlang der Stationsachse des Produkts in Millimetern, aus seiner CAD-Geometrie
-- berechnet, nie eingegeben. Auf der Bauteilzeile die Ausdehnung des Teils, wie seine CAD-Datei es zeichnet; auf einer
-- Einbaulage die der platzierten Verwendung. Leer für ein Produkt ohne Stationen, eine Baugruppe oder ein Teil ohne
-- Geometrie.
ALTER TABLE bauteil ADD COLUMN spanne_von_mm  NUMERIC(12, 3);
ALTER TABLE bauteil ADD COLUMN spanne_bis_mm  NUMERIC(12, 3);
ALTER TABLE einbaulage ADD COLUMN spanne_von_mm  NUMERIC(12, 3);
ALTER TABLE einbaulage ADD COLUMN spanne_bis_mm  NUMERIC(12, 3);
