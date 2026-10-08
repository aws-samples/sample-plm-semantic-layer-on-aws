-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Gears: tooth count and module in inches on the component row, empty for a component with no teeth. A stepped
-- gear cluster with several gears carries the common module only.
ALTER TABLE component ADD COLUMN teeth      INTEGER;
ALTER TABLE component ADD COLUMN module_in  NUMERIC(8, 5);
