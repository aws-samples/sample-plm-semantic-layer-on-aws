-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Bill of materials closure as a table: every (ancestor, descendant) pair of the indented bom_line rows, (component,
-- component) included, with the shortest depth. The semantic layer asks an assembly for everything it contains; the
-- lookup by root is an index access, not a recursion over the whole bill of materials. Triggers on component and
-- bom_line keep the table current: a change recomputes the pairs of the affected subtree only.
DROP VIEW bom_closure;

CREATE TABLE bom_closure (
    ancestor    VARCHAR(64) NOT NULL,
    descendant  VARCHAR(64) NOT NULL,
    depth       INTEGER     NOT NULL,
    PRIMARY KEY (ancestor, descendant)
);

CREATE INDEX bom_closure_descendant_idx ON bom_closure (descendant);
CREATE INDEX bom_line_parent_part_no_idx ON bom_line (parent_part_no);

-- Recomputes the pairs whose descendant lies under one of the seeds, the seed included: only a line into that subtree
-- can change a pair. A component may sit on several lines: each pair keeps its shortest depth. The pairs are found
-- upwards from each affected component; CYCLE ends the search even on a cycle.
CREATE FUNCTION bom_closure_compute(seeds VARCHAR(64)[]) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    affected VARCHAR(64)[];
BEGIN
    IF seeds IS NULL OR cardinality(seeds) = 0 THEN
        RETURN;
    END IF;
    WITH RECURSIVE below (component) AS (
        SELECT unnest(seeds)
        UNION
        SELECT b.child_part_no FROM below w JOIN bom_line b ON b.parent_part_no = w.component
    )
    SELECT array_agg(component) INTO affected FROM below;

    DELETE FROM bom_closure WHERE descendant = ANY (affected);
    INSERT INTO bom_closure (ancestor, descendant, depth)
    WITH RECURSIVE above (ancestor, descendant, depth) AS (
        SELECT comp_id, comp_id, 0 FROM component WHERE comp_id = ANY (affected)
        UNION ALL
        SELECT b.parent_part_no, a.descendant, a.depth + 1
        FROM above a
        JOIN bom_line b ON b.child_part_no = a.ancestor
        WHERE b.parent_part_no IS NOT NULL
    ) CYCLE ancestor SET on_cycle USING path
    SELECT ancestor, descendant, min(depth) FROM above WHERE NOT on_cycle GROUP BY ancestor, descendant;
END;
$$;

-- Seeds of a statement: the components inserted or deleted, the children of the lines inserted, deleted or changed.
CREATE FUNCTION bom_closure_component() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM bom_closure_compute(ARRAY(SELECT comp_id FROM inserted));
    ELSE
        PERFORM bom_closure_compute(ARRAY(SELECT comp_id FROM deleted));
    END IF;
    RETURN NULL;
END;
$$;

CREATE FUNCTION bom_closure_line() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM bom_closure_compute(ARRAY(SELECT child_part_no FROM inserted));
    ELSIF TG_OP = 'DELETE' THEN
        PERFORM bom_closure_compute(ARRAY(SELECT child_part_no FROM deleted));
    ELSE
        PERFORM bom_closure_compute(ARRAY(
            SELECT i.child_part_no FROM inserted i LEFT JOIN deleted d ON d.line_no = i.line_no
            WHERE d.line_no IS NULL OR d.parent_part_no IS DISTINCT FROM i.parent_part_no OR d.child_part_no <> i.child_part_no
            UNION
            SELECT d.child_part_no FROM deleted d LEFT JOIN inserted i ON i.line_no = d.line_no
            WHERE i.line_no IS NULL OR i.parent_part_no IS DISTINCT FROM d.parent_part_no OR i.child_part_no <> d.child_part_no));
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER bom_closure_component_inserted AFTER INSERT ON component
    REFERENCING NEW TABLE AS inserted FOR EACH STATEMENT EXECUTE FUNCTION bom_closure_component();
CREATE TRIGGER bom_closure_component_deleted AFTER DELETE ON component
    REFERENCING OLD TABLE AS deleted FOR EACH STATEMENT EXECUTE FUNCTION bom_closure_component();
CREATE TRIGGER bom_closure_line_inserted AFTER INSERT ON bom_line
    REFERENCING NEW TABLE AS inserted FOR EACH STATEMENT EXECUTE FUNCTION bom_closure_line();
CREATE TRIGGER bom_closure_line_updated AFTER UPDATE ON bom_line
    REFERENCING OLD TABLE AS deleted NEW TABLE AS inserted FOR EACH STATEMENT EXECUTE FUNCTION bom_closure_line();
CREATE TRIGGER bom_closure_line_deleted AFTER DELETE ON bom_line
    REFERENCING OLD TABLE AS deleted FOR EACH STATEMENT EXECUTE FUNCTION bom_closure_line();

SELECT bom_closure_compute(ARRAY(SELECT comp_id FROM component));
