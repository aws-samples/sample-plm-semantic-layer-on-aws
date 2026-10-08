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

/** Electrical connector as stored in the British PLM's native {@code harness_connector} table. */
@Entity
@Table(name = "harness_connector")
@OntologyClass("atelier:Plug")
@Describe("Harness connector: an electrical connector carried by a part")
public class HarnessConnector {

    @Id
    @Column(name = "conn_ref")
    @Describe("Connector reference: the connector's key in the British PLM")
    @Maps("atelier:identifier")
    private String connRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comp_id")
    @Describe("Part carrying the connector")
    @Maps("atelier:onPart")
    private Component component;

    @Column(name = "pos_x", precision = 12, scale = 4)
    @Describe("Connector position along the product X axis")
    @Unit(column = "pos_uom", stores = "IN")
    @Maps("atelier:positionX")
    private BigDecimal posX;

    @Column(name = "pos_y", precision = 12, scale = 4)
    @Describe("Connector position along the product Y axis")
    @Unit(column = "pos_uom", stores = "IN")
    @Maps("atelier:positionY")
    private BigDecimal posY;

    @Column(name = "pos_z", precision = 12, scale = 4)
    @Describe("Connector position along the product Z axis")
    @Unit(column = "pos_uom", stores = "IN")
    @Maps("atelier:positionZ")
    private BigDecimal posZ;

    @Column(name = "pos_uom")
    @Describe("Unit of measure of the position columns, as a QUDT unit local name (IN = inch)")
    private String posUom;

    @Column(name = "shell_type")
    @Describe("Shell type: connector standard / part number")
    @Maps("atelier:connectorType")
    private String shellType;

    @Column(name = "pin_qty")
    @Describe("Pin quantity: pin count")
    @Maps("atelier:pinCount")
    private Integer pinQty;

    @Column(name = "port_name", length = 64)
    @Describe("Port name: the connection point of the host part this feature serves, the same under every option")
    @Maps("atelier:port")
    private String portName;

    @Column(name = "option_code", length = 64)
    @Describe("Option code: the code of the option under which alone this feature exists; empty in the base configuration")
    @Maps("atelier:appliesUnderOption")
    private String optionCode;

    protected HarnessConnector() {
    }

    public String getConnRef() {
        return connRef;
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

    public String getShellType() {
        return shellType;
    }

    public Integer getPinQty() {
        return pinQty;
    }

    public String getPortName() {
        return portName;
    }

    public String getOptionCode() {
        return optionCode;
    }
}
