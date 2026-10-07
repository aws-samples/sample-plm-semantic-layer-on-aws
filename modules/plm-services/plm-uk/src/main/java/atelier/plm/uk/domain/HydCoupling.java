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

/** Hydraulic line coupling as stored in the British PLM's native {@code hyd_coupling} table. */
@Entity
@Table(name = "hyd_coupling")
@OntologyClass("atelier:HydraulicCoupling")
@Describe("Hydraulic coupling: a hydraulic line coupling half carried by a part at a joint face")
public class HydCoupling {

    @Id
    @Column(name = "cplg_ref")
    @Describe("Coupling reference: the coupling's key in the British PLM")
    @Maps("atelier:identifier")
    private String cplgRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comp_id")
    @Describe("Part carrying the coupling")
    @Maps("atelier:onPart")
    private Component component;

    @Column(name = "pos_x", precision = 12, scale = 4)
    @Describe("Coupling position along the product X axis")
    @Unit(column = "pos_uom", stores = "IN")
    @Maps("atelier:positionX")
    private BigDecimal posX;

    @Column(name = "pos_y", precision = 12, scale = 4)
    @Describe("Coupling position along the product Y axis")
    @Unit(column = "pos_uom", stores = "IN")
    @Maps("atelier:positionY")
    private BigDecimal posY;

    @Column(name = "pos_z", precision = 12, scale = 4)
    @Describe("Coupling position along the product Z axis")
    @Unit(column = "pos_uom", stores = "IN")
    @Maps("atelier:positionZ")
    private BigDecimal posZ;

    @Column(name = "pos_uom")
    @Describe("Unit of measure of the position columns, as a QUDT unit local name (IN = inch)")
    private String posUom;

    @Column(name = "standard")
    @Describe("Standard: coupling specification (e.g. AS4395 flareless fitting)")
    @Maps("atelier:couplingStandard")
    private String standard;

    @Column(name = "dash")
    @Describe("Dash: tube dash size in sixteenths of an inch (6 = 3/8 in, 8 = 1/2 in)")
    @Maps("atelier:dashSize")
    private Integer dash;

    @Column(name = "rating", precision = 12, scale = 4)
    @Describe("Rating: nominal working pressure")
    @Unit(column = "rating_uom", stores = "PSI")
    @Maps("atelier:pressureRating")
    private BigDecimal rating;

    @Column(name = "rating_uom")
    @Describe("Unit of measure of the pressure rating, as a QUDT unit local name (PSI = pounds per square inch)")
    private String ratingUom;

    @Column(name = "fluid")
    @Describe("Fluid: hydraulic fluid the line carries")
    @Maps("atelier:fluid")
    private String fluid;

    @Column(name = "port_name", length = 64)
    @Describe("Port name: the connection point of the host part this feature serves, the same under every option")
    @Maps("atelier:port")
    private String portName;

    @Column(name = "option_code", length = 64)
    @Describe("Option code: the code of the option under which alone this feature exists; empty in the base configuration")
    @Maps("atelier:appliesUnderOption")
    private String optionCode;

    protected HydCoupling() {
    }

    public String getCplgRef() {
        return cplgRef;
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

    public Integer getDash() {
        return dash;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public String getRatingUom() {
        return ratingUom;
    }

    public String getFluid() {
        return fluid;
    }

    public String getPortName() {
        return portName;
    }

    public String getOptionCode() {
        return optionCode;
    }
}
