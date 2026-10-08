// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.de.domain;

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

/** A use of another site's part by a part of the German PLM, native {@code externer_verweis} table. */
@Entity
@Table(name = "externer_verweis")
@OntologyClass("atelier:ExternalReference")
@Describe("Externer Verweis: a part of another site that this Bauteil uses, named by its URN with the revision the German PLM expects")
public class ExternerVerweis {

    @Id
    @Column(name = "id")
    @Describe("Id: the reference's surrogate key")
    private String id;

    @Column(name = "teil_nr")
    @Describe("Teilenummer: the Bauteil that uses the remote part")
    @Maps("atelier:fromPart")
    private String teilNr;

    @Column(name = "urn")
    @Describe("URN: the remote part's key, urn:plm:<site>:part:<part number>, as entered")
    @Accepts(pattern = Forms.URN)
    @Maps("atelier:remoteUrn")
    private String urn;

    @Column(name = "menge")
    @Describe("Menge: how many of the remote part the Bauteil uses")
    @Maps("atelier:quantity")
    private BigDecimal menge;

    @Column(name = "erwartete_revision")
    @Describe("Erwartete Revision: the remote part's revision the Bauteil was designed against, in the remote site's form")
    @Accepts(pattern = Forms.ANY_REVISION)
    @Maps("atelier:expectedRevision")
    private String erwarteteRevision;

    @Column(name = "bemerkung")
    @Describe("Bemerkung: free-text note on the use")
    @Maps("rdfs:comment")
    private String bemerkung;

    protected ExternerVerweis() {
    }

    public String getId() {
        return id;
    }

    public String getTeilNr() {
        return teilNr;
    }

    public String getUrn() {
        return urn;
    }

    public BigDecimal getMenge() {
        return menge;
    }

    public String getErwarteteRevision() {
        return erwarteteRevision;
    }

    public String getBemerkung() {
        return bemerkung;
    }
}
