// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.es.domain;

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

/** Hydraulic line coupling as stored in the Spanish PLM's native {@code acoplamiento} table. */
@Entity
@Table(name = "acoplamiento")
@OntologyClass("atelier:HydraulicCoupling")
@Describe("Acoplamiento: a hydraulic line coupling half carried by a part at a joint face")
public class Acoplamiento {

    @Id
    @Column(name = "cod_acoplamiento")
    @Describe("Código de acoplamiento: the coupling's code in the Spanish PLM")
    @Maps("atelier:identifier")
    private String codAcoplamiento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cod_pieza")
    @Describe("Part carrying the coupling")
    @Maps("atelier:onPart")
    private Pieza pieza;

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

    @Column(name = "norma")
    @Describe("Norma: coupling specification (e.g. AS4395 flareless fitting)")
    @Maps("atelier:couplingStandard")
    private String norma;

    @Column(name = "tamano_dash")
    @Describe("Tamaño dash: tube dash size in sixteenths of an inch (6 = 3/8 in, 8 = 1/2 in)")
    @Maps("atelier:dashSize")
    private Integer tamanoDash;

    @Column(name = "presion_bar", precision = 10, scale = 3)
    @Describe("Presión: nominal working pressure")
    @Unit("BAR")
    @Maps("atelier:pressureRating")
    private BigDecimal presionBar;

    @Column(name = "fluido")
    @Describe("Fluido: hydraulic fluid the line carries")
    @Maps("atelier:fluid")
    private String fluido;

    @Column(name = "puerto", length = 64)
    @Describe("Puerto: the connection point of the host part this feature serves, the same under every option")
    @Maps("atelier:port")
    private String puerto;

    @Column(name = "opcion", length = 64)
    @Describe("Opción: the code of the option under which alone this feature exists; empty in the base configuration")
    @Maps("atelier:appliesUnderOption")
    private String opcion;

    protected Acoplamiento() {
    }

    public String getCodAcoplamiento() {
        return codAcoplamiento;
    }

    public Pieza getPieza() {
        return pieza;
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

    public String getNorma() {
        return norma;
    }

    public Integer getTamanoDash() {
        return tamanoDash;
    }

    public BigDecimal getPresionBar() {
        return presionBar;
    }

    public String getFluido() {
        return fluido;
    }

    public String getPuerto() {
        return puerto;
    }

    public String getOpcion() {
        return opcion;
    }
}
