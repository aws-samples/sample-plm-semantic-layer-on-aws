// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.uk.domain;

import atelier.plm.common.annotation.Accepts;
import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Forms;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** A use of another site's part by a part of the British PLM, native {@code external_ref} table. */
@Entity
@Table(name = "external_ref")
@OntologyClass("atelier:ExternalReference")
@Describe("External reference: a part of another site that this component uses, named by its URN with the revision the British PLM expects")
public class ExternalRef {

    @Id
    @Column(name = "id")
    @Describe("Id: the reference's surrogate key")
    private String id;

    @Column(name = "part_no")
    @Describe("Part number: the component that uses the remote part")
    @Maps("atelier:fromPart")
    private String partNo;

    @Column(name = "remote_urn")
    @Describe("Remote URN: the remote part's key, urn:plm:<site>:part:<part number>, as entered")
    @Accepts(pattern = Forms.URN)
    @Maps("atelier:remoteUrn")
    private String remoteUrn;

    @Column(name = "qty")
    @Describe("Quantity: how many of the remote part the component uses")
    @Maps("atelier:quantity")
    private BigDecimal qty;

    @Column(name = "expected_revision")
    @Describe("Expected revision: the remote part's revision the component was designed against, in the remote site's form")
    @Accepts(pattern = Forms.ANY_REVISION)
    @Maps("atelier:expectedRevision")
    private String expectedRevision;

    @Column(name = "note")
    @Describe("Note: free-text note on the use")
    @Maps("rdfs:comment")
    private String note;

    protected ExternalRef() {
    }

    public String getId() {
        return id;
    }

    public String getPartNo() {
        return partNo;
    }

    public String getRemoteUrn() {
        return remoteUrn;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public String getExpectedRevision() {
        return expectedRevision;
    }

    public String getNote() {
        return note;
    }
}
