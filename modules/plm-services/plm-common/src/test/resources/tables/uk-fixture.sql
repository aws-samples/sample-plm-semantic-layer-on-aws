-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- British PLM schema and demo rows for the tables endpoint tests (same shape as plm-uk's migrations:
-- the part table carries no classification), with one part per releasability token of the tags in
-- core-fixture.sql, one part no tag mentions, and a table carrying no export-control mapping. The
-- two dual-use parts are the ornithopter's left wing actuator and sensor harness; the harness
-- carries two connectors, one of them stored without a unit.
CREATE TABLE component (
    comp_id   VARCHAR(64)  PRIMARY KEY,
    name      VARCHAR(255) NOT NULL,
    cad_file  VARCHAR(512)
);

CREATE TABLE harness_connector (
    conn_ref    VARCHAR(64) PRIMARY KEY,
    comp_id     VARCHAR(64) NOT NULL REFERENCES component (comp_id),
    pos_x       NUMERIC(12, 4) NOT NULL,
    pos_y       NUMERIC(12, 4) NOT NULL,
    pos_z       NUMERIC(12, 4) NOT NULL,
    pos_uom     VARCHAR(16),
    shell_type  VARCHAR(64),
    pin_qty     INTEGER
);

CREATE TABLE supplier (
    supplier_id  VARCHAR(64)  PRIMARY KEY,
    name         VARCHAR(255) NOT NULL
);

INSERT INTO component (comp_id, name, cad_file) VALUES
    ('ACTR-6190-L', 'Left wing hydraulic actuator', 'cad/ornithopter/uk-left-wing-actuator.stp'),
    ('HARN-6200-L', 'Left wing sensor harness', 'cad/ornithopter/uk-left-sensor-harness.stp'),
    ('PNL-ALL', 'Linen panel', 'cad/ornithopter/uk-linen-panel.stp'),
    ('BRK-DE', 'Bracket under German licence', NULL),
    ('RIB-FR', 'Wing rib under French licence', NULL),
    ('ACT-LIC', 'Actuator under export licence', NULL),
    ('UNT-UK', 'Bracket without a tag', NULL);

INSERT INTO harness_connector (conn_ref, comp_id, pos_x, pos_y, pos_z, pos_uom, shell_type, pin_qty) VALUES
    ('PL 6190-01', 'ACTR-6190-L', 90.5512, -17.3228, -4.7244, 'IN', 'M12-A', 5),
    ('PL 6200-01', 'HARN-6200-L', 18.7008, -4.9213, 37.4016, 'IN', 'EN3645', 22),
    ('PL 6200-03', 'HARN-6200-L', 22.6378, -4.9213, 37.4016, NULL, 'D38999', 55),
    ('PL ALL-01', 'PNL-ALL', 100.0000, 0.0000, 0.0000, 'IN', 'D38999', 37),
    ('PL DE-01', 'BRK-DE', 200.0000, 0.0000, 0.0000, 'IN', 'D38999', 37),
    ('PL FR-01', 'RIB-FR', 300.0000, 0.0000, 0.0000, 'IN', 'D38999', 37),
    ('PL LIC-01', 'ACT-LIC', 400.0000, 0.0000, 0.0000, 'IN', 'D38999', 37),
    ('PL UNT-01', 'UNT-UK', 500.0000, 0.0000, 0.0000, 'IN', 'D38999', 37);

INSERT INTO supplier (supplier_id, name) VALUES
    ('SUP-1', 'Anywhere Aerostructures Ltd'),
    ('SUP-2', 'Elsewhere Harness GmbH');
