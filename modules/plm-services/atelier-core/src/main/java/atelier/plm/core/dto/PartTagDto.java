// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.dto;

import atelier.plm.core.domain.PartTag;

import java.time.OffsetDateTime;

/**
 * A part tag as GET /core/tags returns it: {@code plm} lower case as in the part IRI, {@code taggedBy}
 * the upper-case PLM code, {@code taggedAt} serialised as an ISO-8601 instant.
 */
public record PartTagDto(String plm, String nativeKey, String jurisdiction, String releasableTo,
                         String taggedBy, OffsetDateTime taggedAt) {

    public static PartTagDto of(PartTag tag) {
        return new PartTagDto(tag.getPlm(), tag.getNativeKey(), tag.getJurisdiction(), tag.getReleasableTo(),
                tag.getTaggedBy(), tag.getTaggedAt());
    }
}
