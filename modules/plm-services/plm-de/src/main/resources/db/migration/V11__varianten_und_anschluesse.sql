-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Varianten: der Code der Option, unter der allein ein Bauteil oder ein Merkmal besteht, leer für einen Artikel der
-- Grundkonfiguration. Anschluss: der Name des Anschlusspunkts, den ein Trägerbauteil einem Einbauplatz bietet, von
-- Option zu Option derselbe.
ALTER TABLE bauteil           ADD COLUMN variante  VARCHAR(64);
ALTER TABLE stecker           ADD COLUMN anschluss VARCHAR(64);
ALTER TABLE stecker           ADD COLUMN variante  VARCHAR(64);
ALTER TABLE befestiger        ADD COLUMN anschluss VARCHAR(64);
ALTER TABLE befestiger        ADD COLUMN variante  VARCHAR(64);
ALTER TABLE hydraulikkupplung ADD COLUMN anschluss VARCHAR(64);
ALTER TABLE hydraulikkupplung ADD COLUMN variante  VARCHAR(64);
