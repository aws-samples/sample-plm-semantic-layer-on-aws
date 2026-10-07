// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.fr.domain;

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

/** A use of another site's part by a part of the French PLM, native {@code reference_externe} table. */
@Entity
@Table(name = "reference_externe")
@OntologyClass("atelier:ExternalReference")
@Describe("Référence externe : a part of another site that this pièce uses, named by its URN with the indice the French PLM expects")
public class ReferenceExterne {

    @Id
    @Column(name = "id")
    @Describe("Id: the reference's surrogate key")
    private String id;

    @Column(name = "piece")
    @Describe("Pièce: the pièce that uses the remote part")
    @Maps("atelier:fromPart")
    private String piece;

    @Column(name = "urn")
    @Describe("URN: the remote part's key, urn:plm:<site>:part:<part number>, as entered")
    @Accepts(pattern = Forms.URN)
    @Maps("atelier:remoteUrn")
    private String urn;

    @Column(name = "quantite")
    @Describe("Quantité: how many of the remote part the pièce uses")
    @Maps("atelier:quantity")
    private BigDecimal quantite;

    @Column(name = "indice_attendu")
    @Describe("Indice attendu: the remote part's revision the pièce was designed against, in the remote site's form")
    @Accepts(pattern = Forms.ANY_REVISION)
    @Maps("atelier:expectedRevision")
    private String indiceAttendu;

    @Column(name = "note")
    @Describe("Note: free-text note on the use")
    @Maps("rdfs:comment")
    private String note;

    protected ReferenceExterne() {
    }

    public String getId() {
        return id;
    }

    public String getPiece() {
        return piece;
    }

    public String getUrn() {
        return urn;
    }

    public BigDecimal getQuantite() {
        return quantite;
    }

    public String getIndiceAttendu() {
        return indiceAttendu;
    }

    public String getNote() {
        return note;
    }
}
