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

/** Electrical connector as stored in the German PLM's native {@code stecker} table. */
@Entity
@Table(name = "stecker")
@OntologyClass("atelier:Plug")
@Describe("Stecker: an electrical connector carried by a part")
public class Stecker {

    @Id
    @Column(name = "stecker_id")
    @Describe("Stecker-ID: the connector's key in the German PLM")
    @Maps("atelier:identifier")
    private String steckerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teil_nr")
    @Describe("Part carrying the connector")
    @Maps("atelier:onPart")
    private Bauteil bauteil;

    @Column(name = "pos_x_mm", precision = 12, scale = 3)
    @Describe("Connector position along the product X axis")
    @Unit("MilliM")
    @Maps("atelier:positionX")
    private BigDecimal posXMm;

    @Column(name = "pos_y_mm", precision = 12, scale = 3)
    @Describe("Connector position along the product Y axis")
    @Unit("MilliM")
    @Maps("atelier:positionY")
    private BigDecimal posYMm;

    @Column(name = "pos_z_mm", precision = 12, scale = 3)
    @Describe("Connector position along the product Z axis")
    @Unit("MilliM")
    @Maps("atelier:positionZ")
    private BigDecimal posZMm;

    @Column(name = "typ")
    @Describe("Typ: connector standard / part number")
    @Maps("atelier:connectorType")
    private String typ;

    @Column(name = "polzahl")
    @Describe("Polzahl: pin count")
    @Maps("atelier:pinCount")
    private Integer polzahl;

    @Column(name = "anschluss", length = 64)
    @Describe("Anschluss: the connection point of the host part this feature serves, the same under every option")
    @Maps("atelier:port")
    private String anschluss;

    @Column(name = "variante", length = 64)
    @Describe("Variante: the code of the option under which alone this feature exists; empty in the base configuration")
    @Maps("atelier:appliesUnderOption")
    private String variante;

    protected Stecker() {
    }

    public String getSteckerId() {
        return steckerId;
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

    public String getTyp() {
        return typ;
    }

    public Integer getPolzahl() {
        return polzahl;
    }

    public String getAnschluss() {
        return anschluss;
    }

    public String getVariante() {
        return variante;
    }
}
