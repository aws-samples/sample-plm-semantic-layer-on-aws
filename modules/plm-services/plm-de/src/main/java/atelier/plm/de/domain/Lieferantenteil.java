// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.de.domain;

import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A supplier's offer for a part, native {@code lieferantenteil} table. */
@Entity
@Table(name = "lieferantenteil")
@OntologyClass("atelier:SupplierOffer")
@Describe("Lieferantenteil: one supplier's offer for a part of the German PLM; a part may have several")
public class Lieferantenteil {

    @Id
    @Column(name = "id")
    @Describe("Nummer: the offer's key")
    private Integer id;

    @Column(name = "teil_nr")
    @Describe("Teilenummer: the part offered")
    @Maps("atelier:offersPart")
    private String teilNr;

    @Column(name = "lieferant_id")
    @Describe("Lieferant: the supplier making the offer")
    @Maps("atelier:fromSupplier")
    private String lieferantId;

    @Column(name = "lieferanten_teilenummer")
    @Describe("Lieferantenteilenummer: the supplier's own number for the item")
    @Maps("atelier:supplierPartNumber")
    private String lieferantenTeilenummer;

    @Column(name = "lieferzeit_tage")
    @Describe("Lieferzeit: the lead time in days")
    @Maps("atelier:leadTimeDays")
    private Integer lieferzeitTage;

    @Column(name = "bevorzugt")
    @Describe("Bevorzugt: whether the site orders from this offer first")
    @Maps("atelier:preferred")
    private Boolean bevorzugt;

    protected Lieferantenteil() {
    }

    public Integer getId() {
        return id;
    }

    public String getTeilNr() {
        return teilNr;
    }

    public String getLieferantId() {
        return lieferantId;
    }

    public String getLieferantenTeilenummer() {
        return lieferantenTeilenummer;
    }

    public Integer getLieferzeitTage() {
        return lieferzeitTage;
    }

    public Boolean getBevorzugt() {
        return bevorzugt;
    }
}
