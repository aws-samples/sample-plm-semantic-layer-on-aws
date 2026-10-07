// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.dto;

/**
 * PLM-neutral view of a part with its export classification ({@code jurisdiction} and the one
 * audience it is {@code releasableTo}). Same shape in every site PLM service.
 */
public record PartDto(String id, String name, String cadFile, String jurisdiction, String releasableTo, String plm) {
}
