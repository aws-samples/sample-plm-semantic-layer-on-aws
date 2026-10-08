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

/** A supplier as stored in the native {@code lieferant} table. */
@Entity
@Table(name = "lieferant")
@OntologyClass("atelier:Supplier")
@Describe("Lieferant: a supplier of the German site")
public class Lieferant {

    @Id
    @Column(name = "id")
    @Describe("Lieferantennummer: the supplier's key in the German PLM")
    @Maps("atelier:identifier")
    private String id;

    @Column(name = "name")
    @Describe("Name: the supplier's company name")
    @Maps("atelier:label")
    private String name;

    @Column(name = "ort")
    @Describe("Ort: the town the supplier delivers from")
    @Maps("atelier:location")
    private String ort;

    protected Lieferant() {
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOrt() {
        return ort;
    }
}
