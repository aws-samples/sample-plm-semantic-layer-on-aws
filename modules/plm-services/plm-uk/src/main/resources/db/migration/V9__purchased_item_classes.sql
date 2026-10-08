-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Purchased item classes: the item's class (fastener, o-ring, placard, container, tyre, wheel, brake), an O-ring's
-- inside diameter, cross-section and compound, a placard's legend, size, face material and adhesive, a container's
-- dimensions and shell material, a tyre's dimensions and ply rating, a wheel's rim and width, a brake's heat stack and
-- rotors, the sizes in the unit of size_uom, and the shelf life in months; empty for a component made in-house or an
-- attribute its class does not have.
ALTER TABLE component ADD COLUMN item_class         VARCHAR(16);
ALTER TABLE component ADD COLUMN inside_dia         NUMERIC(8, 4);
ALTER TABLE component ADD COLUMN cross_section      NUMERIC(8, 4);
ALTER TABLE component ADD COLUMN compound           VARCHAR(120);
ALTER TABLE component ADD COLUMN legend             VARCHAR(120);
ALTER TABLE component ADD COLUMN width              NUMERIC(8, 4);
ALTER TABLE component ADD COLUMN height             NUMERIC(8, 4);
ALTER TABLE component ADD COLUMN face_material      VARCHAR(120);
ALTER TABLE component ADD COLUMN adhesive           VARCHAR(120);
ALTER TABLE component ADD COLUMN base_width         NUMERIC(8, 4);
ALTER TABLE component ADD COLUMN depth              NUMERIC(8, 4);
ALTER TABLE component ADD COLUMN contour_width      NUMERIC(8, 4);
ALTER TABLE component ADD COLUMN shell_material     VARCHAR(120);
ALTER TABLE component ADD COLUMN outside_dia        NUMERIC(8, 4);
ALTER TABLE component ADD COLUMN section_width      NUMERIC(8, 4);
ALTER TABLE component ADD COLUMN rim_dia            NUMERIC(8, 4);
ALTER TABLE component ADD COLUMN ply_rating         INTEGER;
ALTER TABLE component ADD COLUMN heat_stack_dia     NUMERIC(8, 4);
ALTER TABLE component ADD COLUMN rotors             INTEGER;
ALTER TABLE component ADD COLUMN shelf_life_months  INTEGER;
