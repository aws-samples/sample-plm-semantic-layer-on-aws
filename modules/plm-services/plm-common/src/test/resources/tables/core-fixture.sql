-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Atelier core part_tag as a PLM service reads it: a test copy of the atelier-core schema, seeded with the
-- tags of the British parts of uk-fixture.sql (one per releasability token; UNT-UK has none) and a
-- French tag sharing a British key, since a tag is looked up by (plm, native_key).
DROP TABLE IF EXISTS part_tag;

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
    ('uk', 'ACTR-6190-L', 'EU-DUAL-USE',    'EU',       'UK', '2026-09-01 08:00:00+00'),
    ('uk', 'HARN-6200-L', 'EU-DUAL-USE',    'EU',       'UK', '2026-09-01 08:00:00+00'),
    ('uk', 'PNL-ALL',     'NONE',           'ALL',      'UK', '2026-09-01 08:00:00+00'),
    ('uk', 'BRK-DE',      'NATIONAL-DE',    'DE',       'UK', '2026-09-01 08:00:00+00'),
    ('uk', 'RIB-FR',      'NATIONAL-FR',    'FR',       'UK', '2026-09-01 08:00:00+00'),
    ('uk', 'ACT-LIC',     'EXPORT-LICENCE', 'LICENSED', 'UK', '2026-09-01 08:00:00+00'),
    ('fr', 'UNT-UK',      'NONE',           'ALL',      'FR', '2026-09-01 08:00:00+00');
