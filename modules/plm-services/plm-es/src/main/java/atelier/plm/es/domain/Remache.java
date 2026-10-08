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

/** Fastener set on a joint face as stored in the Spanish PLM's native {@code remache} table. */
@Entity
@Table(name = "remache")
@OntologyClass("atelier:Fastener")
@Describe("Remache: a set of identical fasteners (bolts or rivets) on one joint face of a part")
public class Remache {

    @Id
    @Column(name = "cod_remache")
    @Describe("Código de remache: the fastener set's code in the Spanish PLM")
    @Maps("atelier:identifier")
    private String codRemache;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cod_pieza")
    @Describe("Part carrying the fasteners")
    @Maps("atelier:onPart")
    private Pieza pieza;

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

    @Column(name = "norma")
    @Describe("Norma: fastener specification (e.g. EN6115 Hi-Lok, NAS1097 rivet)")
    @Maps("atelier:fastenerStandard")
    private String norma;

    @Column(name = "diametro_mm", precision = 8, scale = 3)
    @Describe("Diámetro: nominal shank diameter of one fastener")
    @Unit("MilliM")
    @Maps("atelier:diameter")
    private BigDecimal diametroMm;

    @Column(name = "cantidad")
    @Describe("Cantidad: number of fasteners in the set")
    @Maps("atelier:fastenerCount")
    private Integer cantidad;

    @Column(name = "longitud_apriete_mm", precision = 8, scale = 3)
    @Describe("Longitud de apriete: grip length of one fastener")
    @Unit("MilliM")
    @Maps("atelier:gripLength")
    private BigDecimal longitudAprieteMm;

    @Column(name = "puerto", length = 64)
    @Describe("Puerto: the connection point of the host part this feature serves, the same under every option")
    @Maps("atelier:port")
    private String puerto;

    @Column(name = "opcion", length = 64)
    @Describe("Opción: the code of the option under which alone this feature exists; empty in the base configuration")
    @Maps("atelier:appliesUnderOption")
    private String opcion;

    protected Remache() {
    }

    public String getCodRemache() {
        return codRemache;
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

    public BigDecimal getDiametroMm() {
        return diametroMm;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public BigDecimal getLongitudAprieteMm() {
        return longitudAprieteMm;
    }

    public String getPuerto() {
        return puerto;
    }

    public String getOpcion() {
        return opcion;
    }
}
