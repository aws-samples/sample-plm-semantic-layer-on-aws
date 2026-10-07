-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Native British PLM schema. Every quantity carries its unit per row, as a QUDT unit local name
-- ('IN' inches, 'PSI' pounds per square inch), in the *_uom column next to it; a NULL unit column
-- leaves the quantity without a unit, which the integration rules report rather than guess.
-- The component table carries no export classification: a part's jurisdiction and releasability
-- live in atelier_core.part_tag, keyed by the part's comp_id.

-- Components: one row per published component, with the path of its CAD file.
CREATE TABLE component (
    comp_id   VARCHAR(64)  PRIMARY KEY,
    name      VARCHAR(255) NOT NULL,
    cad_file  VARCHAR(512)
);

-- Harness connectors of a component, positioned in the product frame.
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

CREATE INDEX harness_connector_comp_id_idx ON harness_connector (comp_id);

-- Fasteners (rivet or bolt rows) of a component: standard, diameter, quantity and grip length.
CREATE TABLE fastener (
    fast_ref   VARCHAR(64) PRIMARY KEY,
    comp_id    VARCHAR(64) NOT NULL REFERENCES component (comp_id),
    pos_x      NUMERIC(12, 4) NOT NULL,
    pos_y      NUMERIC(12, 4) NOT NULL,
    pos_z      NUMERIC(12, 4) NOT NULL,
    pos_uom    VARCHAR(16),
    standard   VARCHAR(64),
    dia        NUMERIC(8, 4),
    dia_uom    VARCHAR(16),
    qty        INTEGER,
    grip       NUMERIC(8, 4),
    grip_uom   VARCHAR(16)
);

CREATE INDEX fastener_comp_id_idx ON fastener (comp_id);

-- Hydraulic couplings of a component: standard, dash size, pressure rating and fluid.
CREATE TABLE hyd_coupling (
    cplg_ref    VARCHAR(64) PRIMARY KEY,
    comp_id     VARCHAR(64) NOT NULL REFERENCES component (comp_id),
    pos_x       NUMERIC(12, 4) NOT NULL,
    pos_y       NUMERIC(12, 4) NOT NULL,
    pos_z       NUMERIC(12, 4) NOT NULL,
    pos_uom     VARCHAR(16),
    standard    VARCHAR(64),
    dash        INTEGER,
    rating      NUMERIC(12, 4),
    rating_uom  VARCHAR(16),
    fluid       VARCHAR(64)
);

CREATE INDEX hyd_coupling_comp_id_idx ON hyd_coupling (comp_id);
