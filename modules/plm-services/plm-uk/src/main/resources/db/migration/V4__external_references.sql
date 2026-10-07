-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- External references: a component uses a part held by another site. The site never copies the remote part; it
-- names it by URN (urn:plm:<site>:part:<part number>) with the revision it expects. Only the semantic layer, which
-- sees both ends, can tell whether the target exists and whether its revision still holds.
CREATE TABLE external_ref (
    id                 VARCHAR(16)    PRIMARY KEY,
    part_no            VARCHAR(64)    NOT NULL REFERENCES component (comp_id),
    remote_urn         VARCHAR(160)   NOT NULL,
    qty                NUMERIC(12, 3) NOT NULL,
    expected_revision  VARCHAR(8),
    note               VARCHAR(400)
);

CREATE INDEX external_ref_part_no_idx ON external_ref (part_no);
