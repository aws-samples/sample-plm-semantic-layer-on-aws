// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.es.domain;

import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;
import java.util.Objects;

/**
 * One pair of the bill-of-materials closure, read from the table {@code cierre_lista_materiales}: the item
 * {@code ascendiente} and an item it contains at any depth, itself included. Read-only: triggers on pieza keep the table
 * current from the bill of materials, where a line is written; a change recomputes the pairs of the affected subtree
 * only. The rows state {@code atelier:contains} about this service's own part IRIs, so the mapping carries neither a
 * class nor {@code atelier:ownedBy}: the part rows state both.
 */
@Entity
@Immutable
@Table(name = "cierre_lista_materiales")
@IdClass(CierreListaMateriales.Key.class)
@OntologyClass(value = "atelier:Part", subject = "https://example.com/atelier/es/part/{ascendiente}")
@Describe("Cierre de la lista de materiales: every (ancestor, descendant) pair of the lista_materiales documents, as a row of a table kept current by triggers")
public class CierreListaMateriales {

    @Id
    @Column(name = "ascendiente")
    @Describe("Ascendiente: the assembly or site kit, or any piece for the pair with itself")
    private String ascendiente;

    @Id
    @Column(name = "descendiente")
    @Describe("Descendiente: a piece the ascendiente contains at any depth of the bill of materials, the ascendiente itself included")
    @Maps("atelier:contains")
    private String descendiente;

    @Column(name = "profundidad")
    @Describe("Profundidad: the number of lines on the shortest path from the ascendiente to the descendiente, 0 for the pair with itself")
    private int profundidad;

    protected CierreListaMateriales() {
    }

    public String getAscendiente() {
        return ascendiente;
    }

    public String getDescendiente() {
        return descendiente;
    }

    /** Key of a pair: the containing item and the contained one. */
    public static class Key implements Serializable {
        private String ascendiente;
        private String descendiente;

        public Key() {
        }

        public Key(String ascendiente, String descendiente) {
            this.ascendiente = ascendiente;
            this.descendiente = descendiente;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(ascendiente, k.ascendiente) && Objects.equals(descendiente, k.descendiente);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ascendiente, descendiente);
        }
    }
}
