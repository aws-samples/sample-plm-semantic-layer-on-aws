-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Span: the extent of the component along the product's station axis, in the unit of span_uom (IN), computed from its
-- CAD geometry and never entered. On the component row, the extent of the component as its CAD file draws it; on a
-- line placement, that of the placed occurrence. Empty for a product without stations, an assembly or a component
-- without geometry.
ALTER TABLE component ADD COLUMN span_from  NUMERIC(12, 4);
ALTER TABLE component ADD COLUMN span_to    NUMERIC(12, 4);
ALTER TABLE component ADD COLUMN span_uom   VARCHAR(16);
ALTER TABLE bom_line_placement ADD COLUMN span_from  NUMERIC(12, 4);
ALTER TABLE bom_line_placement ADD COLUMN span_to    NUMERIC(12, 4);
ALTER TABLE bom_line_placement ADD COLUMN span_uom   VARCHAR(16);
