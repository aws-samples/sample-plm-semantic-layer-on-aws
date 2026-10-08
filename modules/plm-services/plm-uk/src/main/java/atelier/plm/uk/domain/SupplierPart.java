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

/** A supplier's offer for a part, native {@code supplier_part} table. */
@Entity
@Table(name = "supplier_part")
@OntologyClass("atelier:SupplierOffer")
@Describe("Supplier part: one supplier's offer for a component of the British PLM; a component may have several")
public class SupplierPart {

    @Id
    @Column(name = "id")
    @Describe("Number: the offer's key")
    private Integer id;

    @Column(name = "comp_id")
    @Describe("Component ID: the component offered")
    @Maps("atelier:offersPart")
    private String compId;

    @Column(name = "supplier_id")
    @Describe("Supplier ID: the supplier making the offer")
    @Maps("atelier:fromSupplier")
    private String supplierId;

    @Column(name = "supplier_part_no")
    @Describe("Supplier part number: the supplier's own number for the item")
    @Maps("atelier:supplierPartNumber")
    private String supplierPartNo;

    @Column(name = "lead_time_days")
    @Describe("Lead time: the lead time in days")
    @Maps("atelier:leadTimeDays")
    private Integer leadTimeDays;

    @Column(name = "preferred")
    @Describe("Preferred: whether the site orders from this offer first")
    @Maps("atelier:preferred")
    private Boolean preferred;

    protected SupplierPart() {
    }

    public Integer getId() {
        return id;
    }

    public String getCompId() {
        return compId;
    }

    public String getSupplierId() {
        return supplierId;
    }

    public String getSupplierPartNo() {
        return supplierPartNo;
    }

    public Integer getLeadTimeDays() {
        return leadTimeDays;
    }

    public Boolean getPreferred() {
        return preferred;
    }
}
