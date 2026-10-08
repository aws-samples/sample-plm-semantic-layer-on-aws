// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.uk.domain;

import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import atelier.plm.common.annotation.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One occurrence of a line of the British PLM's indented bill of materials, native {@code bom_line_placement}
 * table, keyed by the line's parent and child part numbers: a line number is a position in the listing, which
 * changes whenever a line is inserted above it.
 */
@Entity
@Table(name = "bom_line_placement")
@IdClass(BomLinePlacement.Key.class)
@OntologyClass("atelier:Occurrence")
@Describe("BOM line placement: one occurrence of a line's child component, placed relative to the component as its CAD file draws it")
public class BomLinePlacement {

    @Id
    @Column(name = "parent_part_no")
    @Describe("Parent part number: the parent of the line")
    @Maps("atelier:parent")
    private String parentPartNo;

    @Id
    @Column(name = "child_part_no")
    @Describe("Child part number: the component placed")
    @Maps("atelier:child")
    private String childPartNo;

    @Id
    @Column(name = "occurrence_no")
    @Describe("Occurrence number: the use, 1 for the position the CAD file draws")
    @Maps("atelier:index")
    private Integer occurrenceNo;

    @Column(name = "x_in")
    @Describe("x: the translation of the occurrence along x from the child's position as drawn, in inches")
    @Unit("IN")
    @Maps("atelier:translationX")
    private BigDecimal xIn;

    @Column(name = "y_in")
    @Describe("y: the translation of the occurrence along y from the child's position as drawn, in inches")
    @Unit("IN")
    @Maps("atelier:translationY")
    private BigDecimal yIn;

    @Column(name = "z_in")
    @Describe("z: the translation of the occurrence along z from the child's position as drawn, in inches")
    @Unit("IN")
    @Maps("atelier:translationZ")
    private BigDecimal zIn;

    @Column(name = "rx_deg")
    @Describe("rx: the rotation of the occurrence about the frame's x axis in degrees, applied first")
    @Maps("atelier:rotationX")
    private BigDecimal rxDeg;

    @Column(name = "ry_deg")
    @Describe("ry: the rotation of the occurrence about the frame's y axis in degrees, applied second")
    @Maps("atelier:rotationY")
    private BigDecimal ryDeg;

    @Column(name = "rz_deg")
    @Describe("rz: the rotation of the occurrence about the frame's z axis in degrees, applied third")
    @Maps("atelier:rotationZ")
    private BigDecimal rzDeg;

    @Column(name = "span_from")
    @Describe("Span from: the lowest coordinate of the placed occurrence along the product's station axis, in the unit of span_uom, computed from the child's CAD geometry")
    @Unit(column = "span_uom", stores = "IN")
    @Maps("atelier:spanFrom")
    private BigDecimal spanFrom;

    @Column(name = "span_to")
    @Describe("Span to: the highest coordinate of the placed occurrence along the product's station axis, in the unit of span_uom")
    @Unit(column = "span_uom", stores = "IN")
    @Maps("atelier:spanTo")
    private BigDecimal spanTo;

    @Column(name = "span_uom")
    @Describe("Unit of measure of the span, as a QUDT unit local name (IN = inch)")
    private String spanUom;

    protected BomLinePlacement() {
    }

    /** Key of an occurrence: its line's parent and child, and its number on the line. */
    public static class Key implements Serializable {
        private String parentPartNo;
        private String childPartNo;
        private Integer occurrenceNo;

        public Key() {
        }

        public Key(String parentPartNo, String childPartNo, Integer occurrenceNo) {
            this.parentPartNo = parentPartNo;
            this.childPartNo = childPartNo;
            this.occurrenceNo = occurrenceNo;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(parentPartNo, k.parentPartNo) && Objects.equals(childPartNo, k.childPartNo)
                    && Objects.equals(occurrenceNo, k.occurrenceNo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parentPartNo, childPartNo, occurrenceNo);
        }
    }
}
