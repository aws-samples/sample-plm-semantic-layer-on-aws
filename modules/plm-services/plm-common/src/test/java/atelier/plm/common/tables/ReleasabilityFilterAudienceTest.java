// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.tables;

import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import atelier.plm.common.policy.Clearance;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tag table is filtered on its audience column, with the clearance's own tokens as the values; a
 * bill-of-materials line table through its child, the part the line places, and a table of line placements through the
 * child it places; a bill-of-materials closure table through the contained item, so a pair names a hidden item only as
 * the ancestor of a visible one; an external-reference table through the part that makes the reference; a supplier
 * offer table through the part it offers; a supplier list is unfiltered.
 */
class ReleasabilityFilterAudienceTest {

    @OntologyClass(value = "atelier:Part", subject = "https://example.com/atelier/{plm}/part/{native_key}")
    static class Tag {
        @Column(name = "releasable_to")
        @Maps("atelier:releasableTo")
        String releasableTo;
    }

    @OntologyClass("atelier:BomLine")
    static class Line {
        @jakarta.persistence.Id
        @Column(name = "line_no")
        Integer lineNo;

        @Column(name = "parent_part_no")
        @Maps("atelier:parent")
        String parent;

        @Column(name = "child_part_no")
        @Maps("atelier:child")
        String child;
    }

    @OntologyClass("atelier:ExternalReference")
    static class Reference {
        @jakarta.persistence.Id
        @Column(name = "id")
        String id;

        @Column(name = "part_no")
        @Maps("atelier:fromPart")
        String partNo;

        @Column(name = "remote_urn")
        @Maps("atelier:remoteUrn")
        String remoteUrn;
    }

    @Test
    void referenceTableIsFilteredThroughThePartThatMakesTheReference() {
        ReleasabilityFilter filter = ReleasabilityFilter.of(Reference.class);
        assertThat(filter).isEqualTo(new ReleasabilityFilter.ThroughPart("Reference", "id", "part_no"));
    }

    @OntologyClass("atelier:SupplierOffer")
    static class Offer {
        @jakarta.persistence.Id
        @Column(name = "id")
        Integer id;

        @Column(name = "comp_id")
        @Maps("atelier:offersPart")
        String compId;

        @Column(name = "supplier_id")
        @Maps("atelier:fromSupplier")
        String supplierId;
    }

    @OntologyClass("atelier:Supplier")
    static class Supplier {
        @jakarta.persistence.Id
        @Column(name = "supplier_id")
        String supplierId;
    }

    @Test
    void offerTableIsFilteredThroughThePartItOffers() {
        assertThat(ReleasabilityFilter.of(Offer.class)).isEqualTo(new ReleasabilityFilter.ThroughPart("Offer", "id", "comp_id"));
    }

    @Test
    void supplierTableIsUnfiltered() {
        assertThat(ReleasabilityFilter.of(Supplier.class)).isInstanceOf(ReleasabilityFilter.Unfiltered.class);
    }

    @OntologyClass(value = "atelier:Part", subject = "https://example.com/atelier/uk/part/{ancestor}")
    static class Closure {
        @jakarta.persistence.Id
        @Column(name = "ancestor")
        String ancestor;

        @jakarta.persistence.Id
        @Column(name = "descendant")
        @Maps("atelier:contains")
        String descendant;
    }

    @Test
    void closureViewIsFilteredThroughTheContainedItem() {
        assertThat(ReleasabilityFilter.of(Closure.class)).isEqualTo(new ReleasabilityFilter.ThroughPart("Closure", "ancestor", "descendant"));
    }

    @OntologyClass("atelier:Occurrence")
    static class Placement {
        @jakarta.persistence.Id
        @Column(name = "parent_part_no")
        @Maps("atelier:parent")
        String parent;

        @jakarta.persistence.Id
        @Column(name = "child_part_no")
        @Maps("atelier:child")
        String child;

        @jakarta.persistence.Id
        @Column(name = "occurrence_no")
        @Maps("atelier:index")
        Integer occurrenceNo;
    }

    @Test
    void placementTableIsFilteredThroughTheChildItPlaces() {
        assertThat(ReleasabilityFilter.of(Placement.class)).isInstanceOfSatisfying(ReleasabilityFilter.ThroughPart.class,
                filter -> assertThat(filter.fkColumn()).isEqualTo("child_part_no"));
    }

    @Test
    void lineTableIsFilteredThroughItsChild() {
        ReleasabilityFilter filter = ReleasabilityFilter.of(Line.class);
        assertThat(filter).isEqualTo(new ReleasabilityFilter.ThroughPart("Line", "child_part_no", "child_part_no"));
    }

    @Test
    void tagTableKeepsTheRowsWhoseAudienceTheClearanceHolds() {
        ReleasabilityFilter filter = ReleasabilityFilter.of(Tag.class);
        assertThat(filter).isInstanceOf(ReleasabilityFilter.OnAudience.class);

        Clearance clearance = new Clearance("de-engineer", List.of("ALL", "EU", "DE"));
        ReleasabilityFilter.Clause all = filter.forAllRows(clearance, null);
        assertThat(all.condition()).startsWith("releasable_to IN (");
        assertThat(all.args()).containsExactly("ALL", "EU", "DE");
        assertThat(all.error()).isNull();

        ReleasabilityFilter.Clause keyed = filter.clause(null, List.of("fr:x"), clearance, null);
        assertThat(keyed.condition()).isEqualTo(all.condition());
    }
}
