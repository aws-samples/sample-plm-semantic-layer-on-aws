// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.es.domain;

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

/** A use of another site's part by a part of the Spanish PLM, native {@code referencia_externa} table. */
@Entity
@Table(name = "referencia_externa")
@OntologyClass("atelier:ExternalReference")
@Describe("Referencia externa: a part of another site that this pieza uses, named by its URN with the revision the Spanish PLM expects")
public class ReferenciaExterna {

    @Id
    @Column(name = "id")
    @Describe("Id: the reference's surrogate key")
    private String id;

    @Column(name = "pieza")
    @Describe("Pieza: the pieza that uses the remote part")
    @Maps("atelier:fromPart")
    private String pieza;

    @Column(name = "urn")
    @Describe("URN: the remote part's key, urn:plm:<site>:part:<part number>, as entered")
    @Accepts(pattern = Forms.URN)
    @Maps("atelier:remoteUrn")
    private String urn;

    @Column(name = "cantidad")
    @Describe("Cantidad: how many of the remote part the pieza uses")
    @Maps("atelier:quantity")
    private BigDecimal cantidad;

    @Column(name = "revision_esperada")
    @Describe("Revisión esperada: the remote part's revision the pieza was designed against, in the remote site's form")
    @Accepts(pattern = Forms.ANY_REVISION)
    @Maps("atelier:expectedRevision")
    private String revisionEsperada;

    @Column(name = "nota")
    @Describe("Nota: free-text note on the use")
    @Maps("rdfs:comment")
    private String nota;

    protected ReferenciaExterna() {
    }

    public String getId() {
        return id;
    }

    public String getPieza() {
        return pieza;
    }

    public String getUrn() {
        return urn;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public String getRevisionEsperada() {
        return revisionEsperada;
    }

    public String getNota() {
        return nota;
    }
}
