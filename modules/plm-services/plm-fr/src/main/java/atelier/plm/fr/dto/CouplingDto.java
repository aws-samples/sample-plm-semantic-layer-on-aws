// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.fr.dto;

import java.math.BigDecimal;

/**
 * PLM-neutral view of a hydraulic coupling. Quantities are passed through in the PLM's native
 * units, each named by its {@code *Unit} field (QUDT local name); no conversion happens at this layer.
 */
public record CouplingDto(String id, String partId, BigDecimal x, BigDecimal y, BigDecimal z, String unit,
                          String standard, Integer dashSize, BigDecimal rating, String ratingUnit,
                          String fluid, String plm) {
}
