// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.domain;

import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Atelier's export-control tag of one part of one PLM, as stored in the core {@code part_tag} table.
 * Its subject is the PLM's own part IRI, so the tag's properties join the part in the virtual graph.
 */
@Entity
@Table(name = "part_tag")
@IdClass(PartTagId.class)
@OntologyClass(value = "atelier:Part", subject = "https://example.com/atelier/{plm}/part/{native_key}")
@Describe("Part tag: Atelier's own export-control record of a part published by a PLM, one row per part; "
        + "served unfiltered by /core/tables, as the tags are the classification itself and not PLM data")
public class PartTag {

    @Id
    @Column(name = "plm", length = 2)
    @Describe("PLM: lower-case code of the PLM owning the part (fr, de, uk, es), as in the part IRI")
    private String plm;

    @Id
    @Column(name = "native_key", length = 64)
    @Describe("Native key: the part's key in the owning PLM")
    private String nativeKey;

    @Column(name = "jurisdiction", length = 16)
    @Describe("Jurisdiction: export-control regime of the part (EU-DUAL-USE, EXPORT-LICENCE, US-EAR, NATIONAL-FR/DE/UK/ES or NONE)")
    @Maps("atelier:jurisdiction")
    private String jurisdiction;

    @Column(name = "releasable_to", length = 8)
    @Describe("Releasable to: the one audience the part and its features may be released to (ALL, EU, FR, DE, UK, ES or LICENSED)")
    @Maps("atelier:releasableTo")
    private String releasableTo;

    @Column(name = "tagged_by", length = 2)
    @Describe("Tagged by: upper-case code of the PLM that published the part and wrote the tag (FR, DE, UK, ES)")
    @Maps("atelier:taggedBy")
    private String taggedBy;

    @Column(name = "tagged_at")
    @Describe("Tagged at: when the PLM published the part")
    @Maps("atelier:taggedAt")
    private OffsetDateTime taggedAt;

    protected PartTag() {
    }

    public String getPlm() {
        return plm;
    }

    public String getNativeKey() {
        return nativeKey;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public String getReleasableTo() {
        return releasableTo;
    }

    public String getTaggedBy() {
        return taggedBy;
    }

    public OffsetDateTime getTaggedAt() {
        return taggedAt;
    }
}
