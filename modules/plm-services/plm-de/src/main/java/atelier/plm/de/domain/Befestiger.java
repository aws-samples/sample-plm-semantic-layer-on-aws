// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.de.domain;

import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import atelier.plm.common.annotation.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Fastener set on a joint face as stored in the German PLM's native {@code befestiger} table. */
@Entity
@Table(name = "befestiger")
@OntologyClass("atelier:Fastener")
@Describe("Befestiger: a set of identical fasteners (bolts or rivets) on one joint face of a part")
public class Befestiger {

    @Id
    @Column(name = "befestiger_id")
    @Describe("Befestiger-ID: the fastener set's key in the German PLM")
    @Maps("atelier:identifier")
    private String befestigerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teil_nr")
    @Describe("Part carrying the fasteners")
    @Maps("atelier:onPart")
    private Bauteil bauteil;

    @Column(name = "pos_x_mm", precision = 12, scale = 3)
    @Describe("Fastener set position along the product X axis")
    @Unit("MilliM")
    @Maps("atelier:positionX")
    private BigDecimal posXMm;

    @Column(name = "pos_y_mm", precision = 12, scale = 3)
    @Describe("Fastener set position along the product Y axis")
    @Unit("MilliM")
    @Maps("atelier:positionY")
    private BigDecimal posYMm;

    @Column(name = "pos_z_mm", precision = 12, scale = 3)
    @Describe("Fastener set position along the product Z axis")
    @Unit("MilliM")
    @Maps("atelier:positionZ")
    private BigDecimal posZMm;

    @Column(name = "norm")
    @Describe("Norm: fastener specification (e.g. EN6115 Hi-Lok, NAS1097 rivet)")
    @Maps("atelier:fastenerStandard")
    private String norm;

    @Column(name = "durchmesser_mm", precision = 8, scale = 3)
    @Describe("Durchmesser: nominal shank diameter of one fastener")
    @Unit("MilliM")
    @Maps("atelier:diameter")
    private BigDecimal durchmesserMm;

    @Column(name = "anzahl")
    @Describe("Anzahl: number of fasteners in the set")
    @Maps("atelier:fastenerCount")
    private Integer anzahl;

    @Column(name = "klemmlaenge_mm", precision = 8, scale = 3)
    @Describe("Klemmlänge: grip length of one fastener")
    @Unit("MilliM")
    @Maps("atelier:gripLength")
    private BigDecimal klemmlaengeMm;

    @Column(name = "anschluss", length = 64)
    @Describe("Anschluss: the connection point of the host part this feature serves, the same under every option")
    @Maps("atelier:port")
    private String anschluss;

    @Column(name = "variante", length = 64)
    @Describe("Variante: the code of the option under which alone this feature exists; empty in the base configuration")
    @Maps("atelier:appliesUnderOption")
    private String variante;

    protected Befestiger() {
    }

    public String getBefestigerId() {
        return befestigerId;
    }

    public Bauteil getBauteil() {
        return bauteil;
    }

    public BigDecimal getPosXMm() {
        return posXMm;
    }

    public BigDecimal getPosYMm() {
        return posYMm;
    }

    public BigDecimal getPosZMm() {
        return posZMm;
    }

    public String getNorm() {
        return norm;
    }

    public BigDecimal getDurchmesserMm() {
        return durchmesserMm;
    }

    public Integer getAnzahl() {
        return anzahl;
    }

    public BigDecimal getKlemmlaengeMm() {
        return klemmlaengeMm;
    }

    public String getAnschluss() {
        return anschluss;
    }

    public String getVariante() {
        return variante;
    }
}
