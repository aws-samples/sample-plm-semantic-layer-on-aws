-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Classes d'articles achetés : la classe de l'article (fastener, o-ring, placard, container, tyre, wheel, brake), les
-- dimensions d'un joint torique (diamètre intérieur et section) et son mélange, la légende, le format, le support et
-- l'adhésif d'une plaquette, les dimensions et le matériau de coque d'un conteneur, les dimensions et l'indice de plis
-- d'un pneu, la jante et la largeur d'une roue, l'empilage et les rotors d'un frein, en millimètres, et la durée de
-- stockage en mois ; vides pour une pièce fabriquée ou un attribut que la classe n'a pas.
ALTER TABLE piece ADD COLUMN classe_article         VARCHAR(16);
ALTER TABLE piece ADD COLUMN diametre_interieur_mm  NUMERIC(8, 3);
ALTER TABLE piece ADD COLUMN section_mm             NUMERIC(8, 3);
ALTER TABLE piece ADD COLUMN melange                VARCHAR(120);
ALTER TABLE piece ADD COLUMN legende                VARCHAR(120);
ALTER TABLE piece ADD COLUMN largeur_mm             NUMERIC(8, 3);
ALTER TABLE piece ADD COLUMN hauteur_mm             NUMERIC(8, 3);
ALTER TABLE piece ADD COLUMN support                VARCHAR(120);
ALTER TABLE piece ADD COLUMN adhesif                VARCHAR(120);
ALTER TABLE piece ADD COLUMN largeur_base_mm        NUMERIC(8, 3);
ALTER TABLE piece ADD COLUMN profondeur_mm          NUMERIC(8, 3);
ALTER TABLE piece ADD COLUMN largeur_contour_mm     NUMERIC(8, 3);
ALTER TABLE piece ADD COLUMN materiau_coque         VARCHAR(120);
ALTER TABLE piece ADD COLUMN diametre_exterieur_mm  NUMERIC(8, 3);
ALTER TABLE piece ADD COLUMN largeur_section_mm     NUMERIC(8, 3);
ALTER TABLE piece ADD COLUMN diametre_jante_mm      NUMERIC(8, 3);
ALTER TABLE piece ADD COLUMN indice_plis            INTEGER;
ALTER TABLE piece ADD COLUMN diametre_empilage_mm   NUMERIC(8, 3);
ALTER TABLE piece ADD COLUMN nombre_rotors          INTEGER;
ALTER TABLE piece ADD COLUMN duree_stockage_mois    INTEGER;
