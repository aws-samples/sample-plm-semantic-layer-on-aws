-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Fermeture de la nomenclature en table : chaque couple (ascendant, descendant) des liens de la nomenclature, y
-- compris (pièce, pièce), avec la plus courte profondeur. La couche sémantique demande à un ensemble tout ce qu'il
-- contient ; la recherche par racine est ainsi un accès par index, pas une récursion sur toute la nomenclature. Des
-- déclencheurs sur piece et nomenclature tiennent la table à jour : un changement ne recalcule que les couples du
-- sous-arbre touché.
DROP VIEW nomenclature_fermeture;

CREATE TABLE nomenclature_fermeture (
    ascendant   VARCHAR(64) NOT NULL,
    descendant  VARCHAR(64) NOT NULL,
    profondeur  INTEGER     NOT NULL,
    PRIMARY KEY (ascendant, descendant)
);

CREATE INDEX nomenclature_fermeture_descendant_idx ON nomenclature_fermeture (descendant);

-- Recalcule les couples dont le descendant est sous l'un des germes (le germe compris) : seul un lien entrant dans ce
-- sous-arbre peut changer un couple. Une pièce peut avoir plusieurs parents : chaque couple garde la plus courte
-- profondeur. Les couples sont cherchés vers le haut depuis chaque pièce touchée ; CYCLE arrête la recherche même
-- sur un cycle.
CREATE FUNCTION nomenclature_fermeture_calculer(germes VARCHAR(64)[]) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    touchees VARCHAR(64)[];
BEGIN
    IF germes IS NULL OR cardinality(germes) = 0 THEN
        RETURN;
    END IF;
    WITH RECURSIVE dessous (piece) AS (
        SELECT unnest(germes)
        UNION
        SELECT n.enfant FROM dessous d JOIN nomenclature n ON n.parent = d.piece
    )
    SELECT array_agg(piece) INTO touchees FROM dessous;

    DELETE FROM nomenclature_fermeture WHERE descendant = ANY (touchees);
    INSERT INTO nomenclature_fermeture (ascendant, descendant, profondeur)
    WITH RECURSIVE dessus (ascendant, descendant, profondeur) AS (
        SELECT ref_piece, ref_piece, 0 FROM piece WHERE ref_piece = ANY (touchees)
        UNION ALL
        SELECT n.parent, d.descendant, d.profondeur + 1
        FROM dessus d
        JOIN nomenclature n ON n.enfant = d.ascendant
    ) CYCLE ascendant SET en_cycle USING chemin
    SELECT ascendant, descendant, min(profondeur) FROM dessus WHERE NOT en_cycle GROUP BY ascendant, descendant;
END;
$$;

-- Germes d'une instruction : les pièces créées ou supprimées, les enfants des liens créés, supprimés ou modifiés.
CREATE FUNCTION nomenclature_fermeture_piece() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM nomenclature_fermeture_calculer(ARRAY(SELECT ref_piece FROM nouvelles));
    ELSE
        PERFORM nomenclature_fermeture_calculer(ARRAY(SELECT ref_piece FROM anciennes));
    END IF;
    RETURN NULL;
END;
$$;

CREATE FUNCTION nomenclature_fermeture_lien() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM nomenclature_fermeture_calculer(ARRAY(SELECT enfant FROM nouvelles));
    ELSIF TG_OP = 'DELETE' THEN
        PERFORM nomenclature_fermeture_calculer(ARRAY(SELECT enfant FROM anciennes));
    ELSE
        PERFORM nomenclature_fermeture_calculer(ARRAY(
            SELECT enfant FROM nouvelles UNION SELECT enfant FROM anciennes
            EXCEPT
            SELECT n.enfant FROM nouvelles n JOIN anciennes a ON a.parent = n.parent AND a.enfant = n.enfant));
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER nomenclature_fermeture_piece_creee AFTER INSERT ON piece
    REFERENCING NEW TABLE AS nouvelles FOR EACH STATEMENT EXECUTE FUNCTION nomenclature_fermeture_piece();
CREATE TRIGGER nomenclature_fermeture_piece_supprimee AFTER DELETE ON piece
    REFERENCING OLD TABLE AS anciennes FOR EACH STATEMENT EXECUTE FUNCTION nomenclature_fermeture_piece();
CREATE TRIGGER nomenclature_fermeture_lien_cree AFTER INSERT ON nomenclature
    REFERENCING NEW TABLE AS nouvelles FOR EACH STATEMENT EXECUTE FUNCTION nomenclature_fermeture_lien();
CREATE TRIGGER nomenclature_fermeture_lien_modifie AFTER UPDATE ON nomenclature
    REFERENCING OLD TABLE AS anciennes NEW TABLE AS nouvelles FOR EACH STATEMENT EXECUTE FUNCTION nomenclature_fermeture_lien();
CREATE TRIGGER nomenclature_fermeture_lien_supprime AFTER DELETE ON nomenclature
    REFERENCING OLD TABLE AS anciennes FOR EACH STATEMENT EXECUTE FUNCTION nomenclature_fermeture_lien();

SELECT nomenclature_fermeture_calculer(ARRAY(SELECT ref_piece FROM piece));
