// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.fr.domain;

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
 * One position of a child piece under its parent in the French PLM's bill of materials, native
 * {@code nomenclature_position} table: one row per use of a nomenclature link.
 */
@Entity
@Table(name = "nomenclature_position")
@IdClass(NomenclaturePosition.Key.class)
@OntologyClass("atelier:Occurrence")
@Describe("Position de nomenclature : one use of a link's child piece, placed relative to the piece as its CAD file draws it")
public class NomenclaturePosition {

    @Id
    @Column(name = "parent")
    @Describe("Parent: the parent of the nomenclature link")
    @Maps("atelier:parent")
    private String parent;

    @Id
    @Column(name = "enfant")
    @Describe("Enfant: the piece placed")
    @Maps("atelier:child")
    private String enfant;

    @Id
    @Column(name = "rang")
    @Describe("Rang: the use, 1 for the position the CAD file draws")
    @Maps("atelier:index")
    private Integer rang;

    @Column(name = "x_mm")
    @Describe("x: the translation of the occurrence along x from the child's position as drawn, in millimetres")
    @Unit("MilliM")
    @Maps("atelier:translationX")
    private BigDecimal xMm;

    @Column(name = "y_mm")
    @Describe("y: the translation of the occurrence along y from the child's position as drawn, in millimetres")
    @Unit("MilliM")
    @Maps("atelier:translationY")
    private BigDecimal yMm;

    @Column(name = "z_mm")
    @Describe("z: the translation of the occurrence along z from the child's position as drawn, in millimetres")
    @Unit("MilliM")
    @Maps("atelier:translationZ")
    private BigDecimal zMm;

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

    @Column(name = "travee_debut_mm")
    @Describe("Travée début: the lowest coordinate of the placed occurrence along the product's station axis, in millimetres, computed from the child's CAD geometry")
    @Unit("MilliM")
    @Maps("atelier:spanFrom")
    private BigDecimal traveeDebutMm;

    @Column(name = "travee_fin_mm")
    @Describe("Travée fin: the highest coordinate of the placed occurrence along the product's station axis, in millimetres")
    @Unit("MilliM")
    @Maps("atelier:spanTo")
    private BigDecimal traveeFinMm;

    protected NomenclaturePosition() {
    }

    /** Key of an occurrence: its line's parent and child, and its number on the line. */
    public static class Key implements Serializable {
        private String parent;
        private String enfant;
        private Integer rang;

        public Key() {
        }

        public Key(String parent, String enfant, Integer rang) {
            this.parent = parent;
            this.enfant = enfant;
            this.rang = rang;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(parent, k.parent) && Objects.equals(enfant, k.enfant)
                    && Objects.equals(rang, k.rang);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parent, enfant, rang);
        }
    }
}
