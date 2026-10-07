// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.uk.domain;

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

/** Fastener set on a joint face as stored in the British PLM's native {@code fastener} table. */
@Entity
@Table(name = "fastener")
@OntologyClass("atelier:Fastener")
@Describe("Fastener: a set of identical fasteners (bolts or rivets) on one joint face of a part")
public class Fastener {

    @Id
    @Column(name = "fast_ref")
    @Describe("Fastener reference: the fastener set's key in the British PLM")
    @Maps("atelier:identifier")
    private String fastRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comp_id")
    @Describe("Part carrying the fasteners")
    @Maps("atelier:onPart")
    private Component component;

    @Column(name = "pos_x", precision = 12, scale = 4)
    @Describe("Fastener set position along the product X axis")
    @Unit(column = "pos_uom", stores = "IN")
    @Maps("atelier:positionX")
    private BigDecimal posX;

    @Column(name = "pos_y", precision = 12, scale = 4)
    @Describe("Fastener set position along the product Y axis")
    @Unit(column = "pos_uom", stores = "IN")
    @Maps("atelier:positionY")
    private BigDecimal posY;

    @Column(name = "pos_z", precision = 12, scale = 4)
    @Describe("Fastener set position along the product Z axis")
    @Unit(column = "pos_uom", stores = "IN")
    @Maps("atelier:positionZ")
    private BigDecimal posZ;

    @Column(name = "pos_uom")
    @Describe("Unit of measure of the position columns, as a QUDT unit local name (IN = inch)")
    private String posUom;

    @Column(name = "standard")
    @Describe("Standard: fastener specification (e.g. EN6115 Hi-Lok, NAS1097 rivet)")
    @Maps("atelier:fastenerStandard")
    private String standard;

    @Column(name = "dia", precision = 8, scale = 4)
    @Describe("Diameter: nominal shank diameter of one fastener")
    @Unit(column = "dia_uom", stores = "IN")
    @Maps("atelier:diameter")
    private BigDecimal dia;

    @Column(name = "dia_uom")
    @Describe("Unit of measure of the diameter, as a QUDT unit local name (IN = inch)")
    private String diaUom;

    @Column(name = "qty")
    @Describe("Quantity: number of fasteners in the set")
    @Maps("atelier:fastenerCount")
    private Integer qty;

    @Column(name = "grip", precision = 8, scale = 4)
    @Describe("Grip: grip length of one fastener")
    @Unit(column = "grip_uom", stores = "IN")
    @Maps("atelier:gripLength")
    private BigDecimal grip;

    @Column(name = "grip_uom")
    @Describe("Unit of measure of the grip length, as a QUDT unit local name (IN = inch)")
    private String gripUom;

    @Column(name = "port_name", length = 64)
    @Describe("Port name: the connection point of the host part this feature serves, the same under every option")
    @Maps("atelier:port")
    private String portName;

    @Column(name = "option_code", length = 64)
    @Describe("Option code: the code of the option under which alone this feature exists; empty in the base configuration")
    @Maps("atelier:appliesUnderOption")
    private String optionCode;

    protected Fastener() {
    }

    public String getFastRef() {
        return fastRef;
    }

    public Component getComponent() {
        return component;
    }

    public BigDecimal getPosX() {
        return posX;
    }

    public BigDecimal getPosY() {
        return posY;
    }

    public BigDecimal getPosZ() {
        return posZ;
    }

    public String getPosUom() {
        return posUom;
    }

    public String getStandard() {
        return standard;
    }

    public BigDecimal getDia() {
        return dia;
    }

    public String getDiaUom() {
        return diaUom;
    }

    public Integer getQty() {
        return qty;
    }

    public BigDecimal getGrip() {
        return grip;
    }

    public String getGripUom() {
        return gripUom;
    }

    public String getPortName() {
        return portName;
    }

    public String getOptionCode() {
        return optionCode;
    }
}
