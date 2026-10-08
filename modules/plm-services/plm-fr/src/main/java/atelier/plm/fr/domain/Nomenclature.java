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

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/** One link of the French PLM's bill of materials, native {@code nomenclature} table. */
@Entity
@Table(name = "nomenclature")
@IdClass(Nomenclature.Key.class)
@OntologyClass("atelier:BomLine")
@Describe("Nomenclature : a link between a parent piece (an assembly or the site kit) and a child piece; a piece may have several parents")
public class Nomenclature {

    @Id
    @Column(name = "parent")
    @Describe("Parent: the piece that uses the child")
    @Maps("atelier:parent")
    private String parent;

    @Id
    @Column(name = "enfant")
    @Describe("Enfant: the piece used")
    @Maps("atelier:child")
    private String enfant;

    @Column(name = "quantite")
    @Describe("Quantité: how many of the child the parent uses")
    @Maps("atelier:quantity")
    private BigDecimal quantite;

    @Column(name = "repere")
    @Describe("Repère: the item's find number on the parent's drawing")
    private String repere;

    protected Nomenclature() {
    }

    public String getParent() {
        return parent;
    }

    public String getEnfant() {
        return enfant;
    }

    public BigDecimal getQuantite() {
        return quantite;
    }

    public String getRepere() {
        return repere;
    }

    /** Primary key of a link: the parent and the child. */
    public static class Key implements Serializable {
        private String parent;
        private String enfant;

        public Key() {
        }

        public Key(String parent, String enfant) {
            this.parent = parent;
            this.enfant = enfant;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(parent, k.parent) && Objects.equals(enfant, k.enfant);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parent, enfant);
        }
    }
}
