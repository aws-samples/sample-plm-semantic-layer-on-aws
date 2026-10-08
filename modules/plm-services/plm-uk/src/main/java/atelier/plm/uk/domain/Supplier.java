// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.uk.domain;

import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A supplier as stored in the native {@code supplier} table. */
@Entity
@Table(name = "supplier")
@OntologyClass("atelier:Supplier")
@Describe("Supplier: a supplier of the British site")
public class Supplier {

    @Id
    @Column(name = "supplier_id")
    @Describe("Supplier ID: the supplier's key in the British PLM")
    @Maps("atelier:identifier")
    private String supplierId;

    @Column(name = "name")
    @Describe("Name: the supplier's company name")
    @Maps("atelier:label")
    private String name;

    @Column(name = "town")
    @Describe("Town: the town the supplier delivers from")
    @Maps("atelier:location")
    private String town;

    protected Supplier() {
    }

    public String getSupplierId() {
        return supplierId;
    }

    public String getName() {
        return name;
    }

    public String getTown() {
        return town;
    }
}
