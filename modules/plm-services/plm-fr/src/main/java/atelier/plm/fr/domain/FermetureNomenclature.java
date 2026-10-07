// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.fr.domain;

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
 * One pair of the bill-of-materials closure, read from the table {@code nomenclature_fermeture}: the item
 * {@code ascendant} and an item it contains at any depth, itself included. Read-only: triggers on piece and nomenclature keep
 * the table current from the bill of materials, where a line is written; a change recomputes the pairs of the affected
 * subtree only. The rows state {@code atelier:contains} about this service's own part IRIs, so the mapping carries
 * neither a class nor {@code atelier:ownedBy}: the part rows state both.
 */
@Entity
@Immutable
@Table(name = "nomenclature_fermeture")
@IdClass(FermetureNomenclature.Key.class)
@OntologyClass(value = "atelier:Part", subject = "https://example.com/atelier/fr/part/{ascendant}")
@Describe("Fermeture de la nomenclature: every (ancestor, descendant) pair of the nomenclature links, as a row of a table kept current by triggers")
public class FermetureNomenclature {

    @Id
    @Column(name = "ascendant")
    @Describe("Ascendant: the assembly or site kit, or any piece for the pair with itself")
    private String ascendant;

    @Id
    @Column(name = "descendant")
    @Describe("Descendant: a piece the ascendant contains at any depth of the nomenclature, the ascendant itself included")
    @Maps("atelier:contains")
    private String descendant;

    @Column(name = "profondeur")
    @Describe("Profondeur: the number of lines on the shortest path from the ascendant to the descendant, 0 for the pair with itself")
    private int profondeur;

    protected FermetureNomenclature() {
    }

    public String getAscendant() {
        return ascendant;
    }

    public String getDescendant() {
        return descendant;
    }

    /** Key of a pair: the containing item and the contained one. */
    public static class Key implements Serializable {
        private String ascendant;
        private String descendant;

        public Key() {
        }

        public Key(String ascendant, String descendant) {
            this.ascendant = ascendant;
            this.descendant = descendant;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(ascendant, k.ascendant) && Objects.equals(descendant, k.descendant);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ascendant, descendant);
        }
    }
}
