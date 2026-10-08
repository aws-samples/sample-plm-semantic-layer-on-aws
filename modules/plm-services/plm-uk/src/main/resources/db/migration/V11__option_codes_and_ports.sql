-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Option codes: the option under which alone a component or a feature exists, empty for an item of the base
-- configuration. Port name: the connection point a host component offers an installation slot, the same from one
-- option to the next.
ALTER TABLE component         ADD COLUMN option_code VARCHAR(64);
ALTER TABLE harness_connector ADD COLUMN port_name   VARCHAR(64);
ALTER TABLE harness_connector ADD COLUMN option_code VARCHAR(64);
ALTER TABLE fastener          ADD COLUMN port_name   VARCHAR(64);
ALTER TABLE fastener          ADD COLUMN option_code VARCHAR(64);
ALTER TABLE hyd_coupling      ADD COLUMN port_name   VARCHAR(64);
ALTER TABLE hyd_coupling      ADD COLUMN option_code VARCHAR(64);
