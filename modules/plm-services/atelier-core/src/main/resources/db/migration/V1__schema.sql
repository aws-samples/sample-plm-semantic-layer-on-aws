-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Atelier core schema: what the integrator holds about the PLMs' parts (their export-control tag and the
-- product each belongs to) and its own operational log. Parts are keyed like their IRIs
-- https://example.com/atelier/{plm}/part/{native_key}: plm is the lower-case PLM code, native_key the
-- part's key in that PLM's own schema. The seed migration replaces the rows of product, product_part and
-- part_tag together; the column names are fixed.

-- The export-control tag of every part a PLM has published, one row per part: tagged_by is the publishing
-- PLM's code and tagged_at when it published. The PLM part tables carry no classification; this table is
-- where a part's jurisdiction and releasability live.
CREATE TABLE part_tag (
    plm            VARCHAR(2)  NOT NULL,
    native_key     VARCHAR(64) NOT NULL,
    jurisdiction   VARCHAR(16) NOT NULL,
    releasable_to  VARCHAR(8)  NOT NULL,
    tagged_by      VARCHAR(2)  NOT NULL,
    tagged_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (plm, native_key)
);

-- The products the PLMs' parts are assembled into, one row per product: its key (the last segment of its
-- IRI https://example.com/atelier/product/{product_key}), its display name and the coordinate frame its
-- CAD and feature positions are expressed in. The ontop-core virtual graph exposes a row as
-- atelier:Product (atelier:label, atelier:frame).
CREATE TABLE product (
    product_key VARCHAR(32)  PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    frame       TEXT
);

-- Places a part in a product, keyed like part_tag; a part may sit in several products. Exposed as
-- atelier:partOf from the PLM's part IRI to the product IRI, which is how the query service scopes an
-- answer to one product.
CREATE TABLE product_part (
    product_key VARCHAR(32) NOT NULL REFERENCES product(product_key),
    plm         VARCHAR(2)  NOT NULL,
    native_key  VARCHAR(64) NOT NULL,
    PRIMARY KEY (product_key, plm, native_key)
);

-- The log of the export-control officer's demo value corrections, one row per POST /core/changes,
-- appended by the query service after a PLM has applied the correction. Atelier operational data, not
-- part data: no JPA entity maps it, so it is in neither the catalogue nor the R2RML mapping. The reset
-- replays the rows in reverse (after becomes before) and deletes them.
CREATE TABLE demo_change (
    id           BIGSERIAL PRIMARY KEY,
    plm          TEXT NOT NULL,
    table_name   TEXT NOT NULL,
    row_key      TEXT NOT NULL,
    column_name  TEXT NOT NULL,
    before       TEXT,
    after        TEXT,
    actor        TEXT,
    purpose      TEXT,
    at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
