// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.uk.domain;

import atelier.plm.common.annotation.Accepts;
import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import atelier.plm.common.annotation.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Part as stored in the British PLM's native {@code component} table. */
@Entity
@Table(name = "component")
@OntologyClass("atelier:Part")
@Describe("Component: a part (assembly or section) managed in the British PLM")
public class Component {

    @Id
    @Column(name = "comp_id")
    @Describe("Component ID: the part's key in the British PLM")
    @Maps("atelier:identifier")
    private String compId;

    @Column(name = "name")
    @Describe("Name: human-readable part name")
    @Maps("atelier:label")
    private String name;

    @Column(name = "cad_file")
    @Describe("CAD file: the British PLM's own reference to the part's CAD file")
    @Maps("atelier:sourceFileRef")
    private String cadFile;

    @Column(name = "revision")
    @Describe("Revision: the component's revision in the British PLM's form (P1, P2 for prototypes, C1 once released)")
    @Accepts(pattern = "[PC]\\d+")
    @Maps("atelier:revision")
    private String revision;

    @Column(name = "lifecycle")
    @Describe("Lifecycle: the component's lifecycle state in English (Draft, Released, Frozen, Superseded)")
    @Accepts({"Draft", "Released", "Frozen", "Superseded"})
    @Maps("atelier:lifecycleLabel")
    private String lifecycle;

    @Column(name = "mass_lb")
    @Describe("Mass: the component's mass in pounds")
    @Unit("LB")
    @Maps("atelier:mass")
    private BigDecimal massLb;

    @Column(name = "material")
    @Describe("Material: the component's material")
    @Maps("atelier:material")
    private String material;

    @Column(name = "part_type")
    @Describe("Part type: what the item is, PART, ASSEMBLY, SOFTWARE or DOCUMENT; only a PART has a CAD file")
    @Maps("atelier:partType")
    private String partType;

    @Column(name = "standard")
    @Describe("Standard: the standard the purchased item is bought to (BS 2470); empty for a component made in-house")
    @Maps("atelier:standard")
    private String standard;

    @Column(name = "nominal_dia")
    @Describe("Nominal diameter: the purchased item's nominal (thread) diameter, in the unit of size_uom")
    @Unit(column = "size_uom", stores = "IN")
    @Maps("atelier:nominalDiameter")
    private BigDecimal nominalDia;

    @Column(name = "nominal_length")
    @Describe("Nominal length: the purchased item's nominal length, in the unit of size_uom; empty for a nut")
    @Unit(column = "size_uom", stores = "IN")
    @Maps("atelier:nominalLength")
    private BigDecimal nominalLength;

    @Column(name = "size_uom")
    @Describe("Unit of measure of the nominal size, that of the catalogue the item is bought from, as a QUDT unit local name (IN = inch, MilliM)")
    private String sizeUom;

    @Column(name = "teeth")
    @Describe("Teeth: the number of teeth of a gear; empty for a component with no teeth or a cluster of several gears")
    @Maps("atelier:toothCount")
    private Integer teeth;

    @Column(name = "module_in")
    @Describe("Module: the gear's module in inches (pitch diameter over tooth count); empty for a component with no teeth")
    @Unit("IN")
    @Maps("atelier:gearModule")
    private BigDecimal moduleIn;

    @Column(name = "span_from")
    @Describe("Span from: the lowest coordinate of the part along the product's station axis, in the unit of span_uom, computed from its CAD geometry; empty without stations")
    @Unit(column = "span_uom", stores = "IN")
    @Maps("atelier:spanFrom")
    private BigDecimal spanFrom;

    @Column(name = "span_to")
    @Describe("Span to: the highest coordinate of the part along the product's station axis, in the unit of span_uom")
    @Unit(column = "span_uom", stores = "IN")
    @Maps("atelier:spanTo")
    private BigDecimal spanTo;

    @Column(name = "span_uom")
    @Describe("Unit of measure of the span, as a QUDT unit local name (IN = inch)")
    private String spanUom;

    @Column(name = "item_class")
    @Describe("Item class: the class of the purchased item (fastener, o-ring, placard, container, tyre, wheel, brake); empty for a component made in-house")
    @Maps("atelier:itemClass")
    private String itemClass;

    @Column(name = "inside_dia")
    @Describe("Inside diameter: an O-ring's inside diameter, in the unit of size_uom; empty when the standard gives the size")
    @Unit(column = "size_uom", stores = "IN")
    @Maps("atelier:innerDiameter")
    private BigDecimal insideDia;

    @Column(name = "cross_section")
    @Describe("Cross-section: an O-ring's cross-section, in the unit of size_uom; empty when the standard gives the size")
    @Unit(column = "size_uom", stores = "IN")
    @Maps("atelier:crossSection")
    private BigDecimal crossSection;

    @Column(name = "compound")
    @Describe("Compound: an O-ring's elastomer compound with its hardness")
    @Maps("atelier:compound")
    private String compound;

    @Column(name = "legend")
    @Describe("Legend: the text printed on a placard")
    @Maps("atelier:legend")
    private String legend;

    @Column(name = "width")
    @Describe("Width: a placard's or a wheel's width, in the unit of size_uom")
    @Unit(column = "size_uom", stores = "IN")
    @Maps("atelier:itemWidth")
    private BigDecimal width;

    @Column(name = "height")
    @Describe("Height: a placard's or a container's height, in the unit of size_uom")
    @Unit(column = "size_uom", stores = "IN")
    @Maps("atelier:itemHeight")
    private BigDecimal height;

    @Column(name = "face_material")
    @Describe("Face material: the material a placard is printed on, with its thickness")
    @Maps("atelier:facestock")
    private String faceMaterial;

    @Column(name = "adhesive")
    @Describe("Adhesive: a placard's adhesive")
    @Maps("atelier:adhesive")
    private String adhesive;

    @Column(name = "base_width")
    @Describe("Base width: a container's base width, in the unit of size_uom")
    @Unit(column = "size_uom", stores = "IN")
    @Maps("atelier:baseWidth")
    private BigDecimal baseWidth;

    @Column(name = "depth")
    @Describe("Depth: a container's depth, in the unit of size_uom")
    @Unit(column = "size_uom", stores = "IN")
    @Maps("atelier:itemDepth")
    private BigDecimal depth;

    @Column(name = "contour_width")
    @Describe("Contour width: a container's width over its contour, in the unit of size_uom")
    @Unit(column = "size_uom", stores = "IN")
    @Maps("atelier:contourWidth")
    private BigDecimal contourWidth;

    @Column(name = "shell_material")
    @Describe("Shell material: a container's shell material")
    @Maps("atelier:shellMaterial")
    private String shellMaterial;

    @Column(name = "outside_dia")
    @Describe("Outside diameter: a tyre's outer diameter, in the unit of size_uom")
    @Unit(column = "size_uom", stores = "IN")
    @Maps("atelier:outerDiameter")
    private BigDecimal outsideDia;

    @Column(name = "section_width")
    @Describe("Section width: a tyre's section width, in the unit of size_uom")
    @Unit(column = "size_uom", stores = "IN")
    @Maps("atelier:sectionWidth")
    private BigDecimal sectionWidth;

    @Column(name = "rim_dia")
    @Describe("Rim diameter: a tyre's or a wheel's rim diameter, in the unit of size_uom")
    @Unit(column = "size_uom", stores = "IN")
    @Maps("atelier:rimDiameter")
    private BigDecimal rimDia;

    @Column(name = "ply_rating")
    @Describe("Ply rating: a tyre's ply rating")
    @Maps("atelier:plyRating")
    private Integer plyRating;

    @Column(name = "heat_stack_dia")
    @Describe("Heat stack diameter: a brake's heat stack diameter, in the unit of size_uom")
    @Unit(column = "size_uom", stores = "IN")
    @Maps("atelier:heatStackDiameter")
    private BigDecimal heatStackDia;

    @Column(name = "rotors")
    @Describe("Rotors: the rotors of a brake's heat stack")
    @Maps("atelier:rotorCount")
    private Integer rotors;

    @Column(name = "shelf_life_months")
    @Describe("Shelf life: the item's shelf life in months; empty when the site states none")
    @Maps("atelier:shelfLifeMonths")
    private Integer shelfLifeMonths;

    @Column(name = "option_code", length = 64)
    @Describe("Option code: the code of the option under which alone this part exists; empty in the base configuration")
    @Maps("atelier:appliesUnderOption")
    private String optionCode;

    protected Component() {
    }

    public String getCompId() {
        return compId;
    }

    public String getName() {
        return name;
    }

    public String getCadFile() {
        return cadFile;
    }

    public String getRevision() {
        return revision;
    }

    public String getLifecycle() {
        return lifecycle;
    }

    public BigDecimal getMassLb() {
        return massLb;
    }

    public String getMaterial() {
        return material;
    }

    public String getPartType() {
        return partType;
    }

    public String getStandard() {
        return standard;
    }

    public BigDecimal getNominalDia() {
        return nominalDia;
    }

    public BigDecimal getNominalLength() {
        return nominalLength;
    }

    public String getSizeUom() {
        return sizeUom;
    }

    public Integer getTeeth() {
        return teeth;
    }

    public BigDecimal getModuleIn() {
        return moduleIn;
    }

    public String getItemClass() {
        return itemClass;
    }

    public BigDecimal getInsideDia() {
        return insideDia;
    }

    public BigDecimal getCrossSection() {
        return crossSection;
    }

    public String getCompound() {
        return compound;
    }

    public String getLegend() {
        return legend;
    }

    public BigDecimal getWidth() {
        return width;
    }

    public BigDecimal getHeight() {
        return height;
    }

    public String getFaceMaterial() {
        return faceMaterial;
    }

    public String getAdhesive() {
        return adhesive;
    }

    public Integer getShelfLifeMonths() {
        return shelfLifeMonths;
    }

    public BigDecimal getBaseWidth() {
        return baseWidth;
    }

    public BigDecimal getDepth() {
        return depth;
    }

    public BigDecimal getContourWidth() {
        return contourWidth;
    }

    public String getShellMaterial() {
        return shellMaterial;
    }

    public BigDecimal getOutsideDia() {
        return outsideDia;
    }

    public BigDecimal getSectionWidth() {
        return sectionWidth;
    }

    public BigDecimal getRimDia() {
        return rimDia;
    }

    public Integer getPlyRating() {
        return plyRating;
    }

    public BigDecimal getHeatStackDia() {
        return heatStackDia;
    }

    public Integer getRotors() {
        return rotors;
    }

    public String getOptionCode() {
        return optionCode;
    }
}
