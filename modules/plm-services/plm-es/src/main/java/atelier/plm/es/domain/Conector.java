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

/** Electrical connector as stored in the Spanish PLM's native {@code conector} table. */
@Entity
@Table(name = "conector")
@OntologyClass("atelier:Plug")
@Describe("Conector: an electrical connector carried by a part")
public class Conector {

    @Id
    @Column(name = "cod_conector")
    @Describe("Código de conector: the connector's code in the Spanish PLM")
    @Maps("atelier:identifier")
    private String codConector;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cod_pieza")
    @Describe("Part carrying the connector")
    @Maps("atelier:onPart")
    private Pieza pieza;

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

    @Column(name = "tipo")
    @Describe("Tipo: connector standard / part number")
    @Maps("atelier:connectorType")
    private String tipo;

    @Column(name = "num_contactos")
    @Describe("Número de contactos: pin count")
    @Maps("atelier:pinCount")
    private Integer numContactos;

    @Column(name = "obsoleto")
    private Boolean obsoleto;

    @Column(name = "puerto", length = 64)
    @Describe("Puerto: the connection point of the host part this feature serves, the same under every option")
    @Maps("atelier:port")
    private String puerto;

    @Column(name = "opcion", length = 64)
    @Describe("Opción: the code of the option under which alone this feature exists; empty in the base configuration")
    @Maps("atelier:appliesUnderOption")
    private String opcion;

    protected Conector() {
    }

    public String getCodConector() {
        return codConector;
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

    public String getTipo() {
        return tipo;
    }

    public Integer getNumContactos() {
        return numContactos;
    }

    public Boolean getObsoleto() {
        return obsoleto;
    }

    public String getPuerto() {
        return puerto;
    }

    public String getOpcion() {
        return opcion;
    }
}
