-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Bill of materials closure: every (ancestor, descendant) pair of the indented bom_line rows, (component,
-- component) included. The semantic layer asks an assembly for everything it contains and the database resolves
-- the tree here, so the layer never lists a product's parts. UNION terminates the recursion even on a cycle.
CREATE VIEW bom_closure AS
WITH RECURSIVE closure (ancestor, descendant) AS (
    SELECT comp_id, comp_id FROM component
    UNION
    SELECT c.ancestor, b.child_part_no
    FROM closure c
    JOIN bom_line b ON b.parent_part_no = c.descendant
)
SELECT CAST(ancestor AS VARCHAR(64)) AS ancestor, CAST(descendant AS VARCHAR(64)) AS descendant FROM closure;
