// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.de.domain;

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
 * One installation position of a part under its parent, native {@code einbaulage} table: the parts list line
 * itself is on the part row ({@code parent_id}, {@code menge}), and each of its uses has one row here.
 */
@Entity
@Table(name = "einbaulage")
@IdClass(Einbaulage.Key.class)
@OntologyClass("atelier:Occurrence")
@Describe("Einbaulage: one use of a part under its parent, placed relative to the part as its CAD file draws it")
public class Einbaulage {

    @Id
    @Column(name = "parent_id")
    @Describe("Übergeordnetes Teil: the parent of the parts list line")
    @Maps("atelier:parent")
    private String parentId;

    @Id
    @Column(name = "teil_nr")
    @Describe("Teilenummer: the part placed")
    @Maps("atelier:child")
    private String teilNr;

    @Id
    @Column(name = "lfd_nr")
    @Describe("Laufende Nummer: the use, 1 for the position the CAD file draws")
    @Maps("atelier:index")
    private Integer lfdNr;

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

    @Column(name = "rx_grad")
    @Describe("rx: the rotation of the occurrence about the frame's x axis in degrees, applied first")
    @Maps("atelier:rotationX")
    private BigDecimal rxGrad;

    @Column(name = "ry_grad")
    @Describe("ry: the rotation of the occurrence about the frame's y axis in degrees, applied second")
    @Maps("atelier:rotationY")
    private BigDecimal ryGrad;

    @Column(name = "rz_grad")
    @Describe("rz: the rotation of the occurrence about the frame's z axis in degrees, applied third")
    @Maps("atelier:rotationZ")
    private BigDecimal rzGrad;

    @Column(name = "spanne_von_mm")
    @Describe("Spanne von: the lowest coordinate of the placed occurrence along the product's station axis, in millimetres, computed from the child's CAD geometry")
    @Unit("MilliM")
    @Maps("atelier:spanFrom")
    private BigDecimal spanneVonMm;

    @Column(name = "spanne_bis_mm")
    @Describe("Spanne bis: the highest coordinate of the placed occurrence along the product's station axis, in millimetres")
    @Unit("MilliM")
    @Maps("atelier:spanTo")
    private BigDecimal spanneBisMm;

    protected Einbaulage() {
    }

    /** Key of an occurrence: its line's parent and child, and its number on the line. */
    public static class Key implements Serializable {
        private String parentId;
        private String teilNr;
        private Integer lfdNr;

        public Key() {
        }

        public Key(String parentId, String teilNr, Integer lfdNr) {
            this.parentId = parentId;
            this.teilNr = teilNr;
            this.lfdNr = lfdNr;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(parentId, k.parentId) && Objects.equals(teilNr, k.teilNr)
                    && Objects.equals(lfdNr, k.lfdNr);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parentId, teilNr, lfdNr);
        }
    }
}
