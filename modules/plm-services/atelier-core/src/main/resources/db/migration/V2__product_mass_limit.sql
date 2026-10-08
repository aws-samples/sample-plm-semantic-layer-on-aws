-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- The most a product may weigh, in kilograms, over every site's parts; NULL when the product has none. No PLM
-- holds a product, so no PLM can check its mass: the ontop-core virtual graph exposes the limit as the QUDT
-- quantity atelier:massLimit, and the query service compares it with the mass summed over the sites.
ALTER TABLE product ADD COLUMN mass_limit_kg NUMERIC(12, 3);
