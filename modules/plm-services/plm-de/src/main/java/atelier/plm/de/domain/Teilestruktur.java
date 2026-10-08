// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.de.domain;

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
 * One pair of the bill-of-materials closure, read from the table {@code teilestruktur}: the item {@code vorfahr} and an
 * item it contains at any depth, itself included. Read-only: triggers on bauteil keep the table current from the bill
 * of materials, where a line is written; a change recomputes the pairs of the affected subtree only. The rows state
 * {@code atelier:contains} about this service's own part IRIs, so the mapping carries neither a class nor
 * {@code atelier:ownedBy}: the part rows state both.
 */
@Entity
@Immutable
@Table(name = "teilestruktur")
@IdClass(Teilestruktur.Key.class)
@OntologyClass(value = "atelier:Part", subject = "https://example.com/atelier/de/part/{vorfahr}")
@Describe("Teilestruktur: every (ancestor, descendant) pair of the bill of materials on the part row, as a row of a table kept current by triggers")
public class Teilestruktur {

    @Id
    @Column(name = "vorfahr")
    @Describe("Vorfahr: the assembly or site kit, or any item for the pair with itself")
    private String vorfahr;

    @Id
    @Column(name = "nachfahr")
    @Describe("Nachfahr: an item the Vorfahr contains at any depth of the bill of materials, the Vorfahr itself included")
    @Maps("atelier:contains")
    private String nachfahr;

    @Column(name = "tiefe")
    @Describe("Tiefe: the number of lines on the shortest path from the Vorfahr to the Nachfahr, 0 for the pair with itself")
    private int tiefe;

    protected Teilestruktur() {
    }

    public String getVorfahr() {
        return vorfahr;
    }

    public String getNachfahr() {
        return nachfahr;
    }

    /** Key of a pair: the containing item and the contained one. */
    public static class Key implements Serializable {
        private String vorfahr;
        private String nachfahr;

        public Key() {
        }

        public Key(String vorfahr, String nachfahr) {
            this.vorfahr = vorfahr;
            this.nachfahr = nachfahr;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(vorfahr, k.vorfahr) && Objects.equals(nachfahr, k.nachfahr);
        }

        @Override
        public int hashCode() {
            return Objects.hash(vorfahr, nachfahr);
        }
    }
}
