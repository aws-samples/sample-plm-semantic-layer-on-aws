-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Test stand-in for the seed migration (V2__products_seed.sql), run after the shipped V1 schema
-- migration: a few tags across PLMs, one per releasability tier a profile may or may not hold, two of
-- them sharing a native key, since /core/tables/part_tag looks rows up by native_key across PLMs, every
-- tagged part placed in the one product.
INSERT INTO part_tag (plm, native_key, jurisdiction, releasable_to, tagged_by, tagged_at) VALUES
    ('fr', 'FR-ORN-PCMD-001', 'NATIONAL-FR',    'FR',       'FR', '2026-09-01 08:00:00+00'),
    ('de', 'HMOT-R-61110',    'EXPORT-LICENCE', 'LICENSED', 'DE', '2026-09-01 08:05:00+00'),
    ('uk', 'SHARED-1',        'EU-DUAL-USE',    'EU',       'UK', '2026-09-01 08:10:00+00'),
    ('es', 'SHARED-1',        'EU-DUAL-USE',    'EU',       'ES', '2026-09-01 08:15:00+00'),
    ('uk', 'PNL-ALL',         'NONE',           'ALL',      'UK', '2026-09-01 08:20:00+00');

INSERT INTO product (product_key, name, frame) VALUES
    ('ornithopter', 'Ornithopter ground demonstrator (Paris Manuscript B, f. 74v)', 'x aft from the frame nose, y to the pilot''s right, z up with z = 0 on the keel beam axis; mm, the frame of modules/cad/ornithopter.py. Span 11.0 m (wing tips at y +/-5500), frame x 0 to 3600 with the tail plane to 4900, skid underside at z -580, control post top at 1090.');

INSERT INTO product_part (product_key, plm, native_key)
    SELECT 'ornithopter', plm, native_key FROM part_tag;
