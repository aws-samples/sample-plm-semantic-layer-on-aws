-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Engrenages : nombre de dents et module en millimètres sur la ligne de la pièce, vides pour une pièce sans denture.
-- Un pignon étagé à plusieurs dentures ne porte que le module commun.
ALTER TABLE piece ADD COLUMN nombre_dents  INTEGER;
ALTER TABLE piece ADD COLUMN module_mm     NUMERIC(8, 3);
