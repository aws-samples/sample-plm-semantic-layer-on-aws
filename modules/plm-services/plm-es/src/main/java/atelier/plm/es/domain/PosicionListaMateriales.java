// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.es.domain;

import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import atelier.plm.common.annotation.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One position of a line of a parent piece's {@code lista_materiales} document, read through the table
 * {@code posicion_lista_materiales} a trigger keeps from the documents: a line of the document carries its
 * {@code posiciones}, one per use of the piece, and the table's key lets the semantic layer read a position as one
 * row. Read-only: the document on the parent row is where a position is written.
 */
@Entity
@Immutable
@Table(name = "posicion_lista_materiales")
@IdClass(PosicionListaMateriales.Key.class)
@OntologyClass("atelier:Occurrence")
@Describe("Posición de lista de materiales: one use of a document line's piece, placed relative to the piece as its CAD file draws it")
public class PosicionListaMateriales {

    @Id
    @Column(name = "padre")
    @Describe("Padre: the piece whose document holds the line")
    @Maps("atelier:parent")
    private String padre;

    @Id
    @Column(name = "referencia")
    @Describe("Referencia: the piece placed")
    @Maps("atelier:child")
    private String referencia;

    @Id
    @Column(name = "orden")
    @Describe("Orden: the use, 1 for the position the CAD file draws")
    @Maps("atelier:index")
    private Integer orden;

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

    @Column(name = "rx_grados")
    @Describe("rx: the rotation of the occurrence about the frame's x axis in degrees, applied first")
    @Maps("atelier:rotationX")
    private BigDecimal rxGrados;

    @Column(name = "ry_grados")
    @Describe("ry: the rotation of the occurrence about the frame's y axis in degrees, applied second")
    @Maps("atelier:rotationY")
    private BigDecimal ryGrados;

    @Column(name = "rz_grados")
    @Describe("rz: the rotation of the occurrence about the frame's z axis in degrees, applied third")
    @Maps("atelier:rotationZ")
    private BigDecimal rzGrados;

    @Column(name = "tramo_desde_mm")
    @Describe("Tramo desde: the lowest coordinate of the placed occurrence along the product's station axis, in millimetres, computed from the child's CAD geometry")
    @Unit("MilliM")
    @Maps("atelier:spanFrom")
    private BigDecimal tramoDesdeMm;

    @Column(name = "tramo_hasta_mm")
    @Describe("Tramo hasta: the highest coordinate of the placed occurrence along the product's station axis, in millimetres")
    @Unit("MilliM")
    @Maps("atelier:spanTo")
    private BigDecimal tramoHastaMm;

    protected PosicionListaMateriales() {
    }

    /** Key of an occurrence: its line's parent and child, and its number on the line. */
    public static class Key implements Serializable {
        private String padre;
        private String referencia;
        private Integer orden;

        public Key() {
        }

        public Key(String padre, String referencia, Integer orden) {
            this.padre = padre;
            this.referencia = referencia;
            this.orden = orden;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(padre, k.padre) && Objects.equals(referencia, k.referencia)
                    && Objects.equals(orden, k.orden);
        }

        @Override
        public int hashCode() {
            return Objects.hash(padre, referencia, orden);
        }
    }
}
