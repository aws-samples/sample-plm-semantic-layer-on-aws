-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Bill of materials as flat indented rows, in line order: the site kit on level 0 with no parent, then
-- every line depth first, one level below its parent. A line names its parent and child components and the
-- quantity the parent uses. Assemblies and kits are components of part type ASSEMBLY with no CAD file.
CREATE TABLE bom_line (
    line_no         INTEGER        PRIMARY KEY,
    level           INTEGER        NOT NULL,
    parent_part_no  VARCHAR(64)    REFERENCES component (comp_id),
    child_part_no   VARCHAR(64)    NOT NULL REFERENCES component (comp_id),
    qty             NUMERIC(12, 3) NOT NULL
);

CREATE INDEX bom_line_child_part_no_idx ON bom_line (child_part_no);
