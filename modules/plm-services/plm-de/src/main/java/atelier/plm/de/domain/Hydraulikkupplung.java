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

/** Hydraulic line coupling as stored in the German PLM's native {@code hydraulikkupplung} table. */
@Entity
@Table(name = "hydraulikkupplung")
@OntologyClass("atelier:HydraulicCoupling")
@Describe("Hydraulikkupplung: a hydraulic line coupling half carried by a part at a joint face")
public class Hydraulikkupplung {

    @Id
    @Column(name = "kupplung_id")
    @Describe("Kupplungs-ID: the coupling's key in the German PLM")
    @Maps("atelier:identifier")
    private String kupplungId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teil_nr")
    @Describe("Part carrying the coupling")
    @Maps("atelier:onPart")
    private Bauteil bauteil;

    @Column(name = "pos_x_mm", precision = 12, scale = 3)
    @Describe("Coupling position along the product X axis")
    @Unit("MilliM")
    @Maps("atelier:positionX")
    private BigDecimal posXMm;

    @Column(name = "pos_y_mm", precision = 12, scale = 3)
    @Describe("Coupling position along the product Y axis")
    @Unit("MilliM")
    @Maps("atelier:positionY")
    private BigDecimal posYMm;

    @Column(name = "pos_z_mm", precision = 12, scale = 3)
    @Describe("Coupling position along the product Z axis")
    @Unit("MilliM")
    @Maps("atelier:positionZ")
    private BigDecimal posZMm;

    @Column(name = "norm")
    @Describe("Norm: coupling specification (e.g. AS4395 flareless fitting)")
    @Maps("atelier:couplingStandard")
    private String norm;

    @Column(name = "dash_groesse")
    @Describe("Dash-Größe: tube dash size in sixteenths of an inch (6 = 3/8 in, 8 = 1/2 in)")
    @Maps("atelier:dashSize")
    private Integer dashGroesse;

    @Column(name = "nenndruck_bar", precision = 10, scale = 3)
    @Describe("Nenndruck: nominal working pressure")
    @Unit("BAR")
    @Maps("atelier:pressureRating")
    private BigDecimal nenndruckBar;

    @Column(name = "fluid")
    @Describe("Fluid: hydraulic fluid the line carries")
    @Maps("atelier:fluid")
    private String fluid;

    @Column(name = "anschluss", length = 64)
    @Describe("Anschluss: the connection point of the host part this feature serves, the same under every option")
    @Maps("atelier:port")
    private String anschluss;

    @Column(name = "variante", length = 64)
    @Describe("Variante: the code of the option under which alone this feature exists; empty in the base configuration")
    @Maps("atelier:appliesUnderOption")
    private String variante;

    protected Hydraulikkupplung() {
    }

    public String getKupplungId() {
        return kupplungId;
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

    public Integer getDashGroesse() {
        return dashGroesse;
    }

    public BigDecimal getNenndruckBar() {
        return nenndruckBar;
    }

    public String getFluid() {
        return fluid;
    }

    public String getAnschluss() {
        return anschluss;
    }

    public String getVariante() {
        return variante;
    }
}
