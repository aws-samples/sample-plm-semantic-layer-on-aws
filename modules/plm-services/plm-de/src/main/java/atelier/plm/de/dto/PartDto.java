// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.de.dto;

/**
 * PLM-neutral view of a part: {@code cadFile} is the PLM's own file reference, {@code plm} the
 * owning PLM code. Export classification is not a PLM fact; it lives in atelier_core.part_tag.
 * Same shape in every site PLM service.
 */
public record PartDto(String id, String name, String cadFile, String plm) {
}
