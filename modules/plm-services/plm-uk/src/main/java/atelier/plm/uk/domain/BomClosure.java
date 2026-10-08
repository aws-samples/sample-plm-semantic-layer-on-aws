// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.uk.domain;

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
 * One pair of the bill-of-materials closure, read from the table {@code bom_closure}: the item {@code ancestor} and an
 * item it contains at any depth, itself included. Read-only: triggers on component and bom_line keep the table current
 * from the bill of materials, where a line is written; a change recomputes the pairs of the affected subtree only. The
 * rows state {@code atelier:contains} about this service's own part IRIs, so the mapping carries neither a class nor
 * {@code atelier:ownedBy}: the part rows state both.
 */
@Entity
@Immutable
@Table(name = "bom_closure")
@IdClass(BomClosure.Key.class)
@OntologyClass(value = "atelier:Part", subject = "https://example.com/atelier/uk/part/{ancestor}")
@Describe("Bill of materials closure: every (ancestor, descendant) pair of the bom_line rows, as a row of a table kept current by triggers")
public class BomClosure {

    @Id
    @Column(name = "ancestor")
    @Describe("Ancestor: the assembly or site kit, or any component for the pair with itself")
    private String ancestor;

    @Id
    @Column(name = "descendant")
    @Describe("Descendant: a component the ancestor contains at any depth of the bill of materials, the ancestor itself included")
    @Maps("atelier:contains")
    private String descendant;

    @Column(name = "depth")
    @Describe("Depth: the number of lines on the shortest path from the ancestor to the descendant, 0 for the pair with itself")
    private int depth;

    protected BomClosure() {
    }

    public String getAncestor() {
        return ancestor;
    }

    public String getDescendant() {
        return descendant;
    }

    /** Key of a pair: the containing item and the contained one. */
    public static class Key implements Serializable {
        private String ancestor;
        private String descendant;

        public Key() {
        }

        public Key(String ancestor, String descendant) {
            this.ancestor = ancestor;
            this.descendant = descendant;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(ancestor, k.ancestor) && Objects.equals(descendant, k.descendant);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ancestor, descendant);
        }
    }
}
