-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Bill of materials line placements: one row per occurrence of a line's child, numbered from 1, in a child table of
-- bom_line. A bom_line row stays one line with its quantity, so the indented listing and its closure are unchanged; the
-- placements key on the line's parent and child part numbers because a line number is a position in the listing and
-- changes whenever a line is inserted above it. A placement is relative to the component as its CAD file draws it:
-- translation in inches, then rotation in degrees about the fixed x, y and z axes of the product frame, in that order.
-- Occurrence 1 is the position of the CAD file; a line without placements is used once, at that position.
ALTER TABLE bom_line ADD CONSTRAINT bom_line_parent_child_key UNIQUE (parent_part_no, child_part_no);

CREATE TABLE bom_line_placement (
    parent_part_no  VARCHAR(64)    NOT NULL,
    child_part_no   VARCHAR(64)    NOT NULL,
    occurrence_no   INTEGER        NOT NULL CHECK (occurrence_no >= 1),
    x_in            NUMERIC(12, 4) NOT NULL,
    y_in            NUMERIC(12, 4) NOT NULL,
    z_in            NUMERIC(12, 4) NOT NULL,
    rx_deg          NUMERIC(9, 4)  NOT NULL,
    ry_deg          NUMERIC(9, 4)  NOT NULL,
    rz_deg          NUMERIC(9, 4)  NOT NULL,
    PRIMARY KEY (parent_part_no, child_part_no, occurrence_no),
    FOREIGN KEY (parent_part_no, child_part_no) REFERENCES bom_line (parent_part_no, child_part_no)
        ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE INDEX bom_line_placement_child_part_no_idx ON bom_line_placement (child_part_no);
