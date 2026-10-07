// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.dto;

import java.math.BigDecimal;

/**
 * PLM-neutral view of a plug (connector). Coordinates are passed through in the PLM's native unit,
 * named by {@code unit} (QUDT local name); no conversion happens at this layer.
 */
public record PlugDto(String id, String partId, BigDecimal x, BigDecimal y, BigDecimal z,
                      String unit, String connectorType, Integer pinCount, String plm) {
}
