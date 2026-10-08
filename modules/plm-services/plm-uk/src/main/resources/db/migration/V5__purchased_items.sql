-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Purchased items: the item's standard and its nominal size (diameter and length) on the component row, in the unit
-- of the catalogue it is bought from (size_uom, a QUDT unit local name: IN or MilliM); empty for a made component.
ALTER TABLE component ADD COLUMN standard        VARCHAR(64);
ALTER TABLE component ADD COLUMN nominal_dia     NUMERIC(8, 4);
ALTER TABLE component ADD COLUMN nominal_length  NUMERIC(8, 4);
ALTER TABLE component ADD COLUMN size_uom        VARCHAR(16);

-- The site's suppliers.
CREATE TABLE supplier (
    supplier_id  VARCHAR(64)  PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    town         VARCHAR(120)
);

-- Supplier parts: one supplier's offer for a component, with its part number, the lead time in days and whether it
-- is preferred. A component may have several suppliers.
CREATE TABLE supplier_part (
    id                INTEGER      PRIMARY KEY,
    comp_id           VARCHAR(64)  NOT NULL REFERENCES component (comp_id),
    supplier_id       VARCHAR(64)  NOT NULL REFERENCES supplier (supplier_id),
    supplier_part_no  VARCHAR(64)  NOT NULL,
    lead_time_days    INTEGER      NOT NULL,
    preferred         BOOLEAN      NOT NULL
);

CREATE INDEX supplier_part_comp_id_idx ON supplier_part (comp_id);
