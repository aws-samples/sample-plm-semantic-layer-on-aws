-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Atelier core part_tag as the Spanish service reads it over its read-only connection: a test copy of the
-- atelier-core schema with the tags R__products_seed.sql gives the parts of es-bom-fixture.sql. The boiler
-- ES-3301 is releasable to ES alone; the kit that lists it and the other parts to ALL.
CREATE TABLE part_tag (
    plm            VARCHAR(2)  NOT NULL,
    native_key     VARCHAR(64) NOT NULL,
    jurisdiction   VARCHAR(16) NOT NULL,
    releasable_to  VARCHAR(8)  NOT NULL,
    tagged_by      VARCHAR(2)  NOT NULL,
    tagged_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (plm, native_key)
);

INSERT INTO part_tag (plm, native_key, jurisdiction, releasable_to, tagged_by, tagged_at) VALUES
    ('es', 'ES-3300', 'NONE',        'ALL', 'ES', '2026-09-01 08:00:00+00'),
    ('es', 'ES-3301', 'NATIONAL-ES', 'ES',  'ES', '2026-09-01 08:00:00+00'),
    ('es', 'ES-3302', 'NONE',        'ALL', 'ES', '2026-09-01 08:00:00+00'),
    ('es', 'ES-3303', 'NONE',        'ALL', 'ES', '2026-09-01 08:00:00+00'),
    ('es', 'ES-3304', 'NONE',        'ALL', 'ES', '2026-09-01 08:00:00+00'),
    ('es', 'ES-3305', 'NONE',        'ALL', 'ES', '2026-09-01 08:00:00+00'),
    ('es', 'ES-3306', 'NONE',        'ALL', 'ES', '2026-09-01 08:00:00+00');
