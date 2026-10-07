// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import atelier.plm.common.annotation.Accepts;
import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Forms;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import atelier.plm.common.annotation.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * The British entities of the demo-control tests, annotated as plm-uk's are, over the tables of
 * {@code tables/uk-fixture.sql} and {@code demo/uk-update-fixture.sql}.
 */
final class UkDemoEntities {

    private UkDemoEntities() {
    }

    @Entity
    @Table(name = "component")
    @OntologyClass("atelier:Part")
    static class Component {
        @Id
        @Column(name = "comp_id")
        @Maps("atelier:identifier")
        String compId;

        @Column(name = "name")
        @Maps("atelier:label")
        String name;

        @Column(name = "cad_file")
        String cadFile;

        @Column(name = "revision")
        @Accepts(pattern = "[PC]\\d+")
        @Maps("atelier:revision")
        String revision;

        @Column(name = "lifecycle")
        @Accepts({"Draft", "Released", "Frozen", "Superseded"})
        @Maps("atelier:lifecycleLabel")
        String lifecycle;

        @Column(name = "mass_lb")
        @Unit("LB")
        @Maps("atelier:mass")
        BigDecimal massLb;
    }

    @Entity
    @Table(name = "harness_connector")
    @OntologyClass("atelier:Plug")
    static class HarnessConnector {
        @Id
        @Column(name = "conn_ref")
        @Maps("atelier:identifier")
        String connRef;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "comp_id")
        @Maps("atelier:onPart")
        Component component;

        @Column(name = "pos_x", precision = 12, scale = 4)
        @Describe("Connector position along X")
        @Unit(column = "pos_uom", stores = "IN")
        @Maps("atelier:positionX")
        BigDecimal posX;

        @Column(name = "pos_y", precision = 12, scale = 4)
        @Unit(column = "pos_uom", stores = "IN")
        @Maps("atelier:positionY")
        BigDecimal posY;

        /** Numeric but mapped to no ontology term: a correction here would be invisible through Ontop. */
        @Column(name = "pos_z", precision = 12, scale = 4)
        BigDecimal posZ;

        @Column(name = "pos_uom")
        String posUom;

        @Column(name = "shell_type")
        @Maps("atelier:connectorType")
        String shellType;

        @Column(name = "pin_qty")
        @Maps("atelier:pinCount")
        Integer pinQty;
    }

    @Entity
    @Table(name = "fastener")
    @OntologyClass("atelier:Fastener")
    static class Fastener {
        @Id
        @Column(name = "fast_ref")
        @Maps("atelier:identifier")
        String fastRef;

        @Column(name = "comp_id")
        @Maps("atelier:onPart")
        String compId;

        @Column(name = "standard")
        @Maps("atelier:fastenerStandard")
        String standard;

        @Column(name = "dia", precision = 8, scale = 4)
        @Unit(column = "dia_uom", stores = "IN")
        @Maps("atelier:diameter")
        BigDecimal dia;

        @Column(name = "dia_uom")
        String diaUom;

        @Column(name = "qty")
        @Maps("atelier:fastenerCount")
        Integer qty;
    }

    @Entity
    @Table(name = "hyd_coupling")
    @OntologyClass("atelier:HydraulicCoupling")
    static class HydCoupling {
        @Id
        @Column(name = "cplg_ref")
        @Maps("atelier:identifier")
        String cplgRef;

        @Column(name = "comp_id")
        @Maps("atelier:onPart")
        String compId;

        @Column(name = "standard")
        @Maps("atelier:couplingStandard")
        String standard;

        @Column(name = "rating", precision = 12, scale = 4)
        @Unit(column = "rating_uom", stores = "PSI")
        @Maps("atelier:pressureRating")
        BigDecimal rating;

        @Column(name = "rating_uom")
        String ratingUom;

        @Column(name = "fluid")
        @Maps("atelier:fluid")
        String fluid;
    }

    @Entity
    @Table(name = "external_ref")
    @OntologyClass("atelier:ExternalReference")
    static class ExternalRef {
        @Id
        @Column(name = "id")
        String id;

        @Column(name = "part_no")
        @Maps("atelier:fromPart")
        String partNo;

        @Column(name = "remote_urn")
        @Accepts(pattern = Forms.URN)
        @Maps("atelier:remoteUrn")
        String remoteUrn;

        @Column(name = "expected_revision")
        @Accepts(pattern = Forms.ANY_REVISION)
        @Maps("atelier:expectedRevision")
        String expectedRevision;
    }

    @Entity
    @Table(name = "supplier_part")
    @OntologyClass("atelier:SupplierOffer")
    static class SupplierPart {
        @Id
        @Column(name = "id")
        Integer id;

        @Column(name = "comp_id")
        @Maps("atelier:offersPart")
        String compId;

        @Column(name = "supplier_id")
        @Maps("atelier:fromSupplier")
        String supplierId;

        @Column(name = "lead_time_days")
        @Maps("atelier:leadTimeDays")
        Integer leadTimeDays;

        @Column(name = "preferred")
        @Maps("atelier:preferred")
        Boolean preferred;
    }
}
