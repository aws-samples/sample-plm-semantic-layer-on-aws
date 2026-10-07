-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Test stand-in for the seed migration (V2__products_seed.sql), run after the shipped V1 schema migration:
-- one component per releasability tier of core-tags.sql plus one no tag mentions, each carrying one
-- connector, one fastener and one coupling, so every plain route has a row to show or withhold per part.
INSERT INTO component (comp_id, name, cad_file) VALUES
    ('PNL-ALL',  'Linen panel',                       'cad/ornithopter/uk-linen-panel.stp'),
    ('HARN-EU',  'Left wing sensor harness',          'cad/ornithopter/uk-left-sensor-harness.stp'),
    ('BRK-DE',   'Bracket under German licence',      NULL),
    ('RIB-FR',   'Wing rib under French licence',     NULL),
    ('ACT-LIC',  'Actuator under export licence',     NULL),
    ('UNT-UK',   'Bracket without a tag',             NULL);

INSERT INTO harness_connector (conn_ref, comp_id, pos_x, pos_y, pos_z, pos_uom, shell_type, pin_qty) VALUES
    ('PL ALL-01', 'PNL-ALL', 100.0000, 0.0000, 0.0000, 'IN', 'D38999', 37),
    ('PL EU-01',  'HARN-EU',  18.7008, -4.9213, 37.4016, 'IN', 'EN3645', 22),
    ('PL DE-01',  'BRK-DE',  200.0000, 0.0000, 0.0000, 'IN', 'D38999', 37),
    ('PL FR-01',  'RIB-FR',  300.0000, 0.0000, 0.0000, 'IN', 'D38999', 37),
    ('PL LIC-01', 'ACT-LIC', 400.0000, 0.0000, 0.0000, 'IN', 'D38999', 37),
    ('PL UNT-01', 'UNT-UK',  500.0000, 0.0000, 0.0000, 'IN', 'D38999', 37);

INSERT INTO fastener (fast_ref, comp_id, pos_x, pos_y, pos_z, pos_uom, standard, dia, dia_uom, qty, grip, grip_uom) VALUES
    ('FS ALL-01', 'PNL-ALL', 100.0000, 1.0000, 0.0000, 'IN', 'NAS1097', 0.1875, 'IN', 12, 0.2500, 'IN'),
    ('FS EU-01',  'HARN-EU',  18.7008, 1.0000, 37.4016, 'IN', 'NAS1097', 0.1875, 'IN', 12, 0.2500, 'IN'),
    ('FS DE-01',  'BRK-DE',  200.0000, 1.0000, 0.0000, 'IN', 'NAS1097', 0.1875, 'IN', 12, 0.2500, 'IN'),
    ('FS FR-01',  'RIB-FR',  300.0000, 1.0000, 0.0000, 'IN', 'NAS1097', 0.1875, 'IN', 12, 0.2500, 'IN'),
    ('FS LIC-01', 'ACT-LIC', 400.0000, 1.0000, 0.0000, 'IN', 'NAS1097', 0.1875, 'IN', 12, 0.2500, 'IN'),
    ('FS UNT-01', 'UNT-UK',  500.0000, 1.0000, 0.0000, 'IN', 'NAS1097', 0.1875, 'IN', 12, 0.2500, 'IN');

INSERT INTO hyd_coupling (cplg_ref, comp_id, pos_x, pos_y, pos_z, pos_uom, standard, dash, rating, rating_uom, fluid) VALUES
    ('HC ALL-01', 'PNL-ALL', 100.0000, 2.0000, 0.0000, 'IN', 'AS4395', 6, 3000.0000, 'PSI', 'MIL-PRF-83282'),
    ('HC EU-01',  'HARN-EU',  18.7008, 2.0000, 37.4016, 'IN', 'AS4395', 6, 3000.0000, 'PSI', 'MIL-PRF-83282'),
    ('HC DE-01',  'BRK-DE',  200.0000, 2.0000, 0.0000, 'IN', 'AS4395', 6, 3000.0000, 'PSI', 'MIL-PRF-83282'),
    ('HC FR-01',  'RIB-FR',  300.0000, 2.0000, 0.0000, 'IN', 'AS4395', 6, 3000.0000, 'PSI', 'MIL-PRF-83282'),
    ('HC LIC-01', 'ACT-LIC', 400.0000, 2.0000, 0.0000, 'IN', 'AS4395', 6, 3000.0000, 'PSI', 'MIL-PRF-83282'),
    ('HC UNT-01', 'UNT-UK',  500.0000, 2.0000, 0.0000, 'IN', 'AS4395', 6, 3000.0000, 'PSI', 'MIL-PRF-83282');
