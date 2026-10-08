-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Component attributes: revision (P1, P2, then C1), lifecycle state, mass in pounds, material and part
-- type (PART, SOFTWARE or DOCUMENT; a component without one is a PART).
ALTER TABLE component ADD COLUMN revision   VARCHAR(8);
ALTER TABLE component ADD COLUMN lifecycle  VARCHAR(32);
ALTER TABLE component ADD COLUMN mass_lb    NUMERIC(12, 3);
ALTER TABLE component ADD COLUMN material   VARCHAR(120);
ALTER TABLE component ADD COLUMN part_type  VARCHAR(16) NOT NULL DEFAULT 'PART';
