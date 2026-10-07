-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- The British tables a value correction reaches beyond the harness connectors of
-- tables/uk-fixture.sql, in the shape of plm-uk's migrations: the component's revision, lifecycle
-- and mass, a fastener set, a hydraulic coupling, an external reference and two supplier offers
-- keyed by an integer.
ALTER TABLE component ADD COLUMN revision VARCHAR(8);
ALTER TABLE component ADD COLUMN lifecycle VARCHAR(32);
ALTER TABLE component ADD COLUMN mass_lb NUMERIC(10, 3);

UPDATE component SET revision = 'P1', lifecycle = 'Draft', mass_lb = 1.250 WHERE comp_id = 'ACTR-6190-L';
UPDATE component SET revision = 'C1', lifecycle = 'Released', mass_lb = 0.400 WHERE comp_id = 'HARN-6200-L';

CREATE TABLE fastener (
    fast_ref  VARCHAR(64) PRIMARY KEY,
    comp_id   VARCHAR(64) NOT NULL REFERENCES component (comp_id),
    standard  VARCHAR(64),
    dia       NUMERIC(8, 4),
    dia_uom   VARCHAR(16),
    qty       INTEGER
);

CREATE TABLE hyd_coupling (
    cplg_ref    VARCHAR(64) PRIMARY KEY,
    comp_id     VARCHAR(64) NOT NULL REFERENCES component (comp_id),
    standard    VARCHAR(64),
    rating      NUMERIC(12, 4),
    rating_uom  VARCHAR(16),
    fluid       VARCHAR(64)
);

CREATE TABLE external_ref (
    id                 VARCHAR(64) PRIMARY KEY,
    part_no            VARCHAR(64) NOT NULL,
    remote_urn         VARCHAR(255),
    expected_revision  VARCHAR(8)
);

CREATE TABLE supplier_part (
    id              INTEGER PRIMARY KEY,
    comp_id         VARCHAR(64) NOT NULL,
    supplier_id     VARCHAR(64) NOT NULL,
    lead_time_days  INTEGER,
    preferred       BOOLEAN
);

INSERT INTO fastener (fast_ref, comp_id, standard, dia, dia_uom, qty) VALUES
    ('FS 6190-01', 'ACTR-6190-L', 'NAS1149', 0.1900, 'IN', 4);

INSERT INTO hyd_coupling (cplg_ref, comp_id, standard, rating, rating_uom, fluid) VALUES
    ('HC 6190-01', 'ACTR-6190-L', 'AS5202', 3000.0000, 'PSI', 'MIL-PRF-5606');

INSERT INTO external_ref (id, part_no, remote_urn, expected_revision) VALUES
    ('XR-1', 'HARN-6200-L', 'urn:plm:es:part:ES-31O1', '1');

INSERT INTO supplier_part (id, comp_id, supplier_id, lead_time_days, preferred) VALUES
    (1, 'HARN-6200-L', 'SUP-1', 30, FALSE),
    (2, 'HARN-6200-L', 'SUP-2', 45, TRUE);
