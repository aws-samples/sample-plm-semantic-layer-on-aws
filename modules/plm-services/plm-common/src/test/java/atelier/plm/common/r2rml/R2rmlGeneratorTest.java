// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.r2rml;

import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import atelier.plm.common.annotation.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class R2rmlGeneratorTest {

    @Entity
    @Table(name = "component")
    @OntologyClass("atelier:Part")
    static class Component {
        @Id
        @Column(name = "comp_id")
        @Maps("atelier:identifier")
        String id;

        @Column(name = "name")
        @Maps("atelier:label")
        String name;

        @Column(name = "cad_file")
        @Maps("atelier:cadFile")
        String cadFile;
    }

    @Entity
    @Table(name = "harness_connector")
    @OntologyClass("atelier:Plug")
    static class HarnessConnector {
        @Id
        @Column(name = "conn_ref")
        @Maps("atelier:identifier")
        String ref;

        @ManyToOne
        @JoinColumn(name = "comp_id")
        @Maps("atelier:onPart")
        Component component;

        @Column(name = "pos_x")
        @Unit(column = "pos_uom")
        @Maps("atelier:positionX")
        BigDecimal x;

        @Column(name = "pos_y")
        @Unit(column = "pos_uom")
        @Maps("atelier:positionY")
        BigDecimal y;

        @Column(name = "pos_z")
        @Unit(column = "pos_uom")
        @Maps("atelier:positionZ")
        BigDecimal z;

        @Column(name = "pos_uom")
        @Describe("QUDT unit local name of the position")
        String uom;

        @Column(name = "shell_type")
        @Maps("atelier:connectorType")
        String shellType;

        @Column(name = "pin_qty")
        @Maps("atelier:pinCount")
        Integer pinQty;

        @Column(name = "obsolete")
        Boolean obsolete;
    }

    /** British fastener set: every quantity carries its unit in a per-row column. */
    @Entity
    @Table(name = "fastener")
    @OntologyClass("atelier:Fastener")
    static class Fastener {
        @Id
        @Column(name = "fast_ref")
        @Maps("atelier:identifier")
        String ref;

        @ManyToOne
        @JoinColumn(name = "comp_id")
        @Maps("atelier:onPart")
        Component component;

        @Column(name = "pos_x")
        @Unit(column = "pos_uom")
        @Maps("atelier:positionX")
        BigDecimal x;

        @Column(name = "pos_y")
        @Unit(column = "pos_uom")
        @Maps("atelier:positionY")
        BigDecimal y;

        @Column(name = "pos_z")
        @Unit(column = "pos_uom")
        @Maps("atelier:positionZ")
        BigDecimal z;

        @Column(name = "pos_uom")
        String posUom;

        @Column(name = "standard")
        @Maps("atelier:fastenerStandard")
        String standard;

        @Column(name = "dia")
        @Unit(column = "dia_uom")
        @Maps("atelier:diameter")
        BigDecimal dia;

        @Column(name = "dia_uom")
        String diaUom;

        @Column(name = "qty")
        @Maps("atelier:fastenerCount")
        Integer qty;

        @Column(name = "grip")
        @Unit(column = "grip_uom")
        @Maps("atelier:gripLength")
        BigDecimal grip;

        @Column(name = "grip_uom")
        String gripUom;
    }

    /** French part with its export-control columns. */
    @Entity
    @Table(name = "piece")
    @OntologyClass("atelier:Part")
    static class Piece {
        @Id
        @Column(name = "ref_piece")
        @Maps("atelier:identifier")
        String ref;

        @Column(name = "designation")
        @Maps("atelier:label")
        String designation;

        @Column(name = "fichier_cao")
        @Maps("atelier:cadFile")
        String fichierCao;

        @Column(name = "classification_export")
        @Maps("atelier:jurisdiction")
        String classificationExport;

        @Column(name = "diffusable_a")
        @Maps("atelier:releasableTo")
        String diffusableA;
    }

    /** French hydraulic coupling: fixed units (millimetres, bar). */
    @Entity
    @Table(name = "raccord_hydraulique")
    @OntologyClass("atelier:HydraulicCoupling")
    static class RaccordHydraulique {
        @Id
        @Column(name = "ref_raccord")
        @Maps("atelier:identifier")
        String ref;

        @ManyToOne
        @JoinColumn(name = "ref_piece")
        @Maps("atelier:onPart")
        Piece piece;

        @Column(name = "pos_x_mm")
        @Unit("MilliM")
        @Maps("atelier:positionX")
        BigDecimal x;

        @Column(name = "pos_y_mm")
        @Unit("MilliM")
        @Maps("atelier:positionY")
        BigDecimal y;

        @Column(name = "pos_z_mm")
        @Unit("MilliM")
        @Maps("atelier:positionZ")
        BigDecimal z;

        @Column(name = "norme")
        @Maps("atelier:couplingStandard")
        String norme;

        @Column(name = "taille_dash")
        @Maps("atelier:dashSize")
        Integer tailleDash;

        @Column(name = "pression_bar")
        @Unit("BAR")
        @Maps("atelier:pressureRating")
        BigDecimal pressionBar;

        @Column(name = "fluide")
        @Maps("atelier:fluid")
        String fluide;
    }

    @Entity
    @Table(name = "prise")
    @OntologyClass("atelier:Plug")
    static class FixedUnitPlug {
        @Id
        @Column(name = "id_prise")
        String id;

        @Column(name = "x_mm")
        @Unit("MilliM")
        @Maps("atelier:positionX")
        BigDecimal x;

        @Column(name = "y_raw")
        @Maps("atelier:positionY")
        BigDecimal y;
    }

    @Entity
    @Table(name = "both")
    @OntologyClass("atelier:Plug")
    static class BothUnitsPlug {
        @Id
        String id;

        @Unit(value = "MilliM", column = "uom")
        @Maps("atelier:positionX")
        BigDecimal x;
    }

    @Entity
    @OntologyClass("atelier:Interface")
    static class UnsupportedClass {
        @Id
        String id;
    }

    /** Atelier core's tag of a part: the subject is the PLM's part IRI, declared over two columns. */
    @Entity
    @Table(name = "part_tag")
    @OntologyClass(value = "atelier:Part", subject = "https://example.com/atelier/{plm}/part/{native_key}")
    static class PartTag {
        @Id
        @Column(name = "plm")
        String plm;

        @Id
        @Column(name = "native_key")
        String nativeKey;

        @Column(name = "jurisdiction")
        @Maps("atelier:jurisdiction")
        String jurisdiction;

        @Column(name = "releasable_to")
        @Maps("atelier:releasableTo")
        String releasableTo;

        @Column(name = "tagged_by")
        @Maps("atelier:taggedBy")
        String taggedBy;

        @Column(name = "tagged_at")
        @Maps("atelier:taggedAt")
        OffsetDateTime taggedAt;
    }

    /** Atelier core's product: its own subject, so no atelier:ownedBy. */
    @Entity
    @Table(name = "product")
    @OntologyClass(value = "atelier:Product", subject = "https://example.com/atelier/product/{product_key}")
    static class Product {
        @Id
        @Column(name = "product_key")
        String productKey;

        @Column(name = "name")
        @Maps("atelier:label")
        String name;

        @Column(name = "frame")
        @Maps("atelier:frame")
        String frame;
    }

    /** Atelier core's membership of a PLM part in a product: the part IRI as subject, the product IRI as object. */
    @Entity
    @Table(name = "product_part")
    @OntologyClass(value = "atelier:Part", subject = "https://example.com/atelier/{plm}/part/{native_key}")
    static class ProductPart {
        @Id
        @Column(name = "product_key")
        @Maps("atelier:partOf")
        String productKey;

        @Id
        @Column(name = "plm")
        String plm;

        @Id
        @Column(name = "native_key")
        String nativeKey;
    }

    @Entity
    @Table(name = "stamp")
    @OntologyClass(value = "atelier:Plug", subject = "https://example.com/atelier/{plm}/plug/{id_stamp}")
    static class InstantStamp {
        @Id
        @Column(name = "id_stamp")
        String id;

        @Column(name = "plm")
        String plm;

        @Column(name = "at")
        @Maps("atelier:taggedAt")
        Instant at;
    }

    @Entity
    @Table(name = "typo")
    @OntologyClass(value = "atelier:Part", subject = "https://example.com/atelier/{plm}/part/{native_kay}")
    static class MistypedTemplate {
        @Id
        @Column(name = "plm")
        String plm;

        @Column(name = "native_key")
        String nativeKey;
    }

    /** British component with its attributes: the mass is a quantity in pounds, the lifecycle word a plain literal. */
    @Entity
    @Table(name = "component")
    @OntologyClass("atelier:Part")
    static class WeighedComponent {
        @Id
        @Column(name = "comp_id")
        @Maps("atelier:identifier")
        String id;

        @Column(name = "lifecycle")
        @Maps("atelier:lifecycleLabel")
        String lifecycle;

        @Column(name = "mass_lb")
        @Unit("LB")
        @Maps("atelier:mass")
        BigDecimal massLb;
    }

    /** British gear: its tooth count a plain integer, its module a length quantity in inches. */
    @Entity
    @Table(name = "component")
    @OntologyClass("atelier:Part")
    static class GearComponent {
        @Id
        @Column(name = "comp_id")
        @Maps("atelier:identifier")
        String id;

        @Column(name = "teeth")
        @Maps("atelier:toothCount")
        Integer teeth;

        @Column(name = "module_in")
        @Unit("IN")
        @Maps("atelier:gearModule")
        BigDecimal moduleIn;
    }

    /** German fastener set with the port it serves and the option under which alone it exists. */
    @Entity
    @Table(name = "befestiger")
    @OntologyClass("atelier:Fastener")
    static class OptionFastener {
        @Id
        @Column(name = "befestiger_id")
        @Maps("atelier:identifier")
        String id;

        @Column(name = "anschluss")
        @Maps("atelier:port")
        String anschluss;

        @Column(name = "variante")
        @Maps("atelier:appliesUnderOption")
        String variante;
    }

    /** German part whose bill of materials is a tree on the part row: a parent column and a quantity. */
    @Entity
    @Table(name = "bauteil")
    @OntologyClass("atelier:Part")
    static class TreePart {
        @Id
        @Column(name = "teil_nr")
        @Maps("atelier:identifier")
        String teilNr;

        @Column(name = "benennung")
        @Maps("atelier:label")
        String benennung;

        @Column(name = "parent_id")
        @Maps("atelier:parent")
        String parentId;

        @Column(name = "menge")
        @Maps("atelier:quantity")
        BigDecimal menge;
    }

    /** French bill-of-materials link table: one row per parent and child. */
    @Entity
    @Table(name = "nomenclature")
    @OntologyClass("atelier:BomLine")
    static class LinkLine {
        @Id
        @Column(name = "parent")
        @Maps("atelier:parent")
        String parent;

        @Id
        @Column(name = "enfant")
        @Maps("atelier:child")
        String enfant;

        @Column(name = "quantite")
        @Maps("atelier:quantity")
        BigDecimal quantite;

        @Column(name = "repere")
        String repere;
    }

    /** British line placements: one row per occurrence of a line, keyed by the line's parent and child, in inches. */
    @Entity
    @Table(name = "bom_line_placement")
    @OntologyClass("atelier:Occurrence")
    static class Placement {
        @Id
        @Column(name = "parent_part_no")
        @Maps("atelier:parent")
        String parentPartNo;

        @Id
        @Column(name = "child_part_no")
        @Maps("atelier:child")
        String childPartNo;

        @Id
        @Column(name = "occurrence_no")
        @Maps("atelier:index")
        Integer occurrenceNo;

        @Column(name = "x_in")
        @Unit("IN")
        @Maps("atelier:translationX")
        BigDecimal xIn;

        @Column(name = "rz_deg")
        @Maps("atelier:rotationZ")
        BigDecimal rzDeg;
    }

    /** British bill-of-materials closure table: every (ancestor, descendant) pair, the pair of an item with itself included. */
    @Entity
    @Table(name = "bom_closure")
    @OntologyClass(value = "atelier:Part", subject = "https://example.com/atelier/uk/part/{ancestor}")
    static class Closure {
        @Id
        @Column(name = "ancestor")
        String ancestor;

        @Id
        @Column(name = "descendant")
        @Maps("atelier:contains")
        String descendant;
    }

    @Entity
    @Table(name = "half")
    @OntologyClass("atelier:BomLine")
    static class LineWithoutChild {
        @Id
        @Column(name = "parent")
        @Maps("atelier:parent")
        String parent;
    }

    /** British external-reference table: a component's use of another site's part, by URN. */
    @Entity
    @Table(name = "external_ref")
    @OntologyClass("atelier:ExternalReference")
    static class Reference {
        @Id
        @Column(name = "id")
        String id;

        @Column(name = "part_no")
        @Maps("atelier:fromPart")
        String partNo;

        @Column(name = "remote_urn")
        @Maps("atelier:remoteUrn")
        String remoteUrn;

        @Column(name = "qty")
        @Maps("atelier:quantity")
        BigDecimal qty;

        @Column(name = "expected_revision")
        @Maps("atelier:expectedRevision")
        String expectedRevision;

        @Column(name = "note")
        @Maps("rdfs:comment")
        String note;
    }

    /** British purchased item: a standard and a nominal size whose unit is stored per row. */
    @Entity
    @Table(name = "component")
    @OntologyClass("atelier:Part")
    static class PurchasedComponent {
        @Id
        @Column(name = "comp_id")
        String compId;

        @Column(name = "standard")
        @Maps("atelier:standard")
        String standard;

        @Column(name = "nominal_dia")
        @Unit(column = "size_uom")
        @Maps("atelier:nominalDiameter")
        BigDecimal nominalDia;

        @Column(name = "nominal_length")
        @Unit(column = "size_uom")
        @Maps("atelier:nominalLength")
        BigDecimal nominalLength;

        @Column(name = "size_uom")
        String sizeUom;
    }

    /** German supplier offer: the part offered, the supplier, a lead time and a preferred flag. */
    @Entity
    @Table(name = "lieferantenteil")
    @OntologyClass("atelier:SupplierOffer")
    static class Offer {
        @Id
        @Column(name = "id")
        Integer id;

        @Column(name = "teil_nr")
        @Maps("atelier:offersPart")
        String teilNr;

        @Column(name = "lieferant_id")
        @Maps("atelier:fromSupplier")
        String lieferantId;

        @Column(name = "lieferzeit_tage")
        @Maps("atelier:leadTimeDays")
        Integer lieferzeitTage;

        @Column(name = "bevorzugt")
        @Maps("atelier:preferred")
        Boolean bevorzugt;
    }

    @Entity
    static class NotInOntology {
        @Id
        String id;
    }

    @Test
    void perRowUnitColumnMappingIsExactlyTheExpectedDocument() throws IOException {
        String generated = R2rmlGenerator.generate("UK",
                List.of(HarnessConnector.class, Component.class, NotInOntology.class));
        assertThat(generated).isEqualTo(resource("/r2rml/uk-fixture.r2rml.ttl"));
    }

    @Test
    void fastenerWithPerRowUnitsIsExactlyTheExpectedDocument() throws IOException {
        String generated = R2rmlGenerator.generate("uk", List.of(Fastener.class, Component.class));
        assertThat(generated).isEqualTo(resource("/r2rml/uk-fastener.r2rml.ttl"));
    }

    @Test
    void hydraulicCouplingWithFixedUnitsAndClassifiedPartIsExactlyTheExpectedDocument() throws IOException {
        String generated = R2rmlGenerator.generate("fr", List.of(RaccordHydraulique.class, Piece.class));
        assertThat(generated).isEqualTo(resource("/r2rml/fr-coupling.r2rml.ttl"));
    }

    @Test
    void fixedUnitIsAConstantAndUndeclaredUnitEmitsNoUnitTriple() {
        String generated = R2rmlGenerator.generate("fr", List.of(FixedUnitPlug.class));
        assertThat(generated).contains("""
                map:FixedUnitPlug_positionX a rr:TriplesMap ;
                  rr:logicalTable [ rr:tableName "prise" ] ;
                  rr:subjectMap [ rr:template "https://example.com/atelier/fr/plug/{id_prise}/position/x" ; rr:class qudt:QuantityValue ] ;
                  rr:predicateObjectMap [ rr:predicate qudt:numericValue ; rr:objectMap [ rr:column "x_mm" ; rr:datatype xsd:decimal ] ] ;
                  rr:predicateObjectMap [ rr:predicate qudt:unit ; rr:object unit:MilliM ] .
                """);
        assertThat(generated).contains("""
                  rr:predicateObjectMap [ rr:predicate qudt:numericValue ; rr:objectMap [ rr:column "y_raw" ; rr:datatype xsd:decimal ] ] .
                """);
        assertThat(generated).containsOnlyOnce("qudt:unit");
    }

    @Test
    void massColumnIsAQuantityValueUnderThePartWithItsFixedUnit() {
        String generated = R2rmlGenerator.generate("uk", List.of(WeighedComponent.class));
        assertThat(generated).contains("""
                  rr:predicateObjectMap [ rr:predicate atelier:lifecycleLabel ; rr:objectMap [ rr:column "lifecycle" ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:mass ; rr:objectMap [ rr:template "https://example.com/atelier/uk/part/{comp_id}/mass" ] ] .
                """);
        assertThat(generated).contains("""
                map:WeighedComponent_mass a rr:TriplesMap ;
                  rr:logicalTable [ rr:tableName "component" ] ;
                  rr:subjectMap [ rr:template "https://example.com/atelier/uk/part/{comp_id}/mass" ; rr:class qudt:QuantityValue ] ;
                  rr:predicateObjectMap [ rr:predicate qudt:numericValue ; rr:objectMap [ rr:column "mass_lb" ; rr:datatype xsd:decimal ] ] ;
                  rr:predicateObjectMap [ rr:predicate qudt:unit ; rr:object unit:LB ] .
                """);
    }

    @Test
    void optionCodeIsTheOptionIriAndThePortALiteral() {
        String generated = R2rmlGenerator.generate("de", List.of(OptionFastener.class));
        assertThat(generated).contains("""
                  rr:predicateObjectMap [ rr:predicate atelier:port ; rr:objectMap [ rr:column "anschluss" ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:appliesUnderOption ; rr:objectMap [ rr:template "https://example.com/atelier/option/{variante}" ] ] .
                """);
    }

    @Test
    void gearModuleIsALengthQuantityUnderThePartAndTheToothCountAnInteger() {
        String generated = R2rmlGenerator.generate("uk", List.of(GearComponent.class));
        assertThat(generated).contains("""
                  rr:predicateObjectMap [ rr:predicate atelier:toothCount ; rr:objectMap [ rr:column "teeth" ; rr:datatype xsd:integer ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:gearModule ; rr:objectMap [ rr:template "https://example.com/atelier/uk/part/{comp_id}/module" ] ] .
                """);
        assertThat(generated).contains("""
                map:GearComponent_gearModule a rr:TriplesMap ;
                  rr:logicalTable [ rr:tableName "component" ] ;
                  rr:subjectMap [ rr:template "https://example.com/atelier/uk/part/{comp_id}/module" ; rr:class qudt:QuantityValue ] ;
                  rr:predicateObjectMap [ rr:predicate qudt:numericValue ; rr:objectMap [ rr:column "module_in" ; rr:datatype xsd:decimal ] ] ;
                  rr:predicateObjectMap [ rr:predicate qudt:unit ; rr:object unit:IN ] .
                """);
    }

    @Test
    void treeOnThePartRowIsASecondTriplesMapFromTheParentToTheRowsPart() {
        String generated = R2rmlGenerator.generate("de", List.of(TreePart.class));
        assertThat(generated).contains("""
                map:TreePart a rr:TriplesMap ;
                  rr:logicalTable [ rr:tableName "bauteil" ] ;
                  rr:subjectMap [ rr:template "https://example.com/atelier/de/part/{teil_nr}" ; rr:class atelier:Part ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:ownedBy ; rr:object "DE" ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:identifier ; rr:objectMap [ rr:column "teil_nr" ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:label ; rr:objectMap [ rr:column "benennung" ] ] .
                """);
        assertThat(generated).contains("""
                map:TreePart_BomLine a rr:TriplesMap ;
                  rr:logicalTable [ rr:tableName "bauteil" ] ;
                  rr:subjectMap [ rr:template "https://example.com/atelier/de/bomline/{parent_id}/{teil_nr}" ; rr:class atelier:BomLine ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:ownedBy ; rr:object "DE" ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:parent ; rr:objectMap [ rr:template "https://example.com/atelier/de/part/{parent_id}" ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:child ; rr:objectMap [ rr:template "https://example.com/atelier/de/part/{teil_nr}" ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:quantity ; rr:objectMap [ rr:column "menge" ; rr:datatype xsd:decimal ] ] .
                """);
    }

    /** The closure states atelier:contains between part IRIs and no class, so a request for atelier:Part never reads the view. */
    @Test
    void closureViewStatesContainsBetweenPartIrisWithoutAClass() {
        String generated = R2rmlGenerator.generate("uk", List.of(Closure.class));
        assertThat(generated).contains("""
                map:Closure a rr:TriplesMap ;
                  rr:logicalTable [ rr:tableName "bom_closure" ] ;
                  rr:subjectMap [ rr:template "https://example.com/atelier/uk/part/{ancestor}" ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:contains ; rr:objectMap [ rr:template "https://example.com/atelier/uk/part/{descendant}" ] ] .
                """);
        assertThat(generated).doesNotContain("rr:class").doesNotContain("ownedBy");
    }

    @Test
    void linkTableLineIsMintedFromItsParentAndChildColumns() {
        String generated = R2rmlGenerator.generate("fr", List.of(LinkLine.class));
        assertThat(generated).contains("""
                map:LinkLine a rr:TriplesMap ;
                  rr:logicalTable [ rr:tableName "nomenclature" ] ;
                  rr:subjectMap [ rr:template "https://example.com/atelier/fr/bomline/{parent}/{enfant}" ; rr:class atelier:BomLine ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:ownedBy ; rr:object "FR" ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:parent ; rr:objectMap [ rr:template "https://example.com/atelier/fr/part/{parent}" ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:child ; rr:objectMap [ rr:template "https://example.com/atelier/fr/part/{enfant}" ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:quantity ; rr:objectMap [ rr:column "quantite" ; rr:datatype xsd:decimal ] ] .
                """);
        assertThat(generated).doesNotContain("repere");
    }

    @Test
    void occurrenceIsMintedFromItsLineAndIndexNamesItsLineAndCarriesItsTranslationAsAQuantity() {
        String generated = R2rmlGenerator.generate("uk", List.of(Placement.class));
        assertThat(generated).contains("""
                map:Placement a rr:TriplesMap ;
                  rr:logicalTable [ rr:tableName "bom_line_placement" ] ;
                  rr:subjectMap [ rr:template "https://example.com/atelier/uk/occurrence/{parent_part_no}/{child_part_no}/{occurrence_no}" ; rr:class atelier:Occurrence ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:ownedBy ; rr:object "UK" ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:ofLine ; rr:objectMap [ rr:template "https://example.com/atelier/uk/bomline/{parent_part_no}/{child_part_no}" ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:index ; rr:objectMap [ rr:column "occurrence_no" ; rr:datatype xsd:integer ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:translationX ; rr:objectMap [ rr:template "https://example.com/atelier/uk/occurrence/{parent_part_no}/{child_part_no}/{occurrence_no}/translation/x" ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:rotationZ ; rr:objectMap [ rr:column "rz_deg" ; rr:datatype xsd:decimal ] ] .

                map:Placement_translationX a rr:TriplesMap ;
                  rr:logicalTable [ rr:tableName "bom_line_placement" ] ;
                  rr:subjectMap [ rr:template "https://example.com/atelier/uk/occurrence/{parent_part_no}/{child_part_no}/{occurrence_no}/translation/x" ; rr:class qudt:QuantityValue ] ;
                  rr:predicateObjectMap [ rr:predicate qudt:numericValue ; rr:objectMap [ rr:column "x_in" ; rr:datatype xsd:decimal ] ] ;
                  rr:predicateObjectMap [ rr:predicate qudt:unit ; rr:object unit:IN ] .
                """);
        assertThat(generated).doesNotContain("atelier:parent ;").doesNotContain("atelier:child ;");
    }

    @Test
    void externalReferenceIsMintedFromItsIdAndNamesTheLocalPart() {
        String generated = R2rmlGenerator.generate("uk", List.of(Reference.class));
        assertThat(generated).contains("""
                map:Reference a rr:TriplesMap ;
                  rr:logicalTable [ rr:tableName "external_ref" ] ;
                  rr:subjectMap [ rr:template "https://example.com/atelier/uk/ref/{id}" ; rr:class atelier:ExternalReference ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:ownedBy ; rr:object "UK" ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:fromPart ; rr:objectMap [ rr:template "https://example.com/atelier/uk/part/{part_no}" ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:remoteUrn ; rr:objectMap [ rr:column "remote_urn" ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:quantity ; rr:objectMap [ rr:column "qty" ; rr:datatype xsd:decimal ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:expectedRevision ; rr:objectMap [ rr:column "expected_revision" ] ] ;
                  rr:predicateObjectMap [ rr:predicate rdfs:comment ; rr:objectMap [ rr:column "note" ] ] .
                """);
    }

    @Test
    void nominalSizeColumnsAreQuantityValuesSharingThePerRowUnitColumn() {
        String generated = R2rmlGenerator.generate("uk", List.of(PurchasedComponent.class));
        assertThat(generated).contains("""
                  rr:predicateObjectMap [ rr:predicate atelier:standard ; rr:objectMap [ rr:column "standard" ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:nominalDiameter ; rr:objectMap [ rr:template "https://example.com/atelier/uk/part/{comp_id}/nominal-diameter" ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:nominalLength ; rr:objectMap [ rr:template "https://example.com/atelier/uk/part/{comp_id}/nominal-length" ] ] .
                """);
        assertThat(generated).contains("""
                map:PurchasedComponent_nominalLength a rr:TriplesMap ;
                  rr:logicalTable [ rr:tableName "component" ] ;
                  rr:subjectMap [ rr:template "https://example.com/atelier/uk/part/{comp_id}/nominal-length" ; rr:class qudt:QuantityValue ] ;
                  rr:predicateObjectMap [ rr:predicate qudt:numericValue ; rr:objectMap [ rr:column "nominal_length" ; rr:datatype xsd:decimal ] ] ;
                  rr:predicateObjectMap [ rr:predicate qudt:unit ; rr:objectMap [ rr:template "http://qudt.org/vocab/unit/{size_uom}" ] ] .
                """);
    }

    @Test
    void supplierOfferNamesItsPartAndItsSupplierByIri() {
        String generated = R2rmlGenerator.generate("de", List.of(Offer.class));
        assertThat(generated).contains("""
                map:Offer a rr:TriplesMap ;
                  rr:logicalTable [ rr:tableName "lieferantenteil" ] ;
                  rr:subjectMap [ rr:template "https://example.com/atelier/de/offer/{id}" ; rr:class atelier:SupplierOffer ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:ownedBy ; rr:object "DE" ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:offersPart ; rr:objectMap [ rr:template "https://example.com/atelier/de/part/{teil_nr}" ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:fromSupplier ; rr:objectMap [ rr:template "https://example.com/atelier/de/supplier/{lieferant_id}" ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:leadTimeDays ; rr:objectMap [ rr:column "lieferzeit_tage" ; rr:datatype xsd:integer ] ] ;
                  rr:predicateObjectMap [ rr:predicate atelier:preferred ; rr:objectMap [ rr:column "bevorzugt" ; rr:datatype xsd:boolean ] ] .
                """);
    }

    @Test
    void rejectsLineEntityWithoutAChildColumn() {
        assertThatThrownBy(() -> R2rmlGenerator.generate("fr", List.of(LineWithoutChild.class)))
                .hasMessageContaining("LineWithoutChild: an atelier:BomLine entity needs a column mapped to atelier:child");
    }

    @Test
    void rejectsAmbiguousUnit() {
        assertThatThrownBy(() -> R2rmlGenerator.generate("fr", List.of(BothUnitsPlug.class)))
                .hasMessageContaining("both a fixed unit and a unit column");
    }

    @Test
    void rejectsUnknownOntologyClassNamingTheEntityAndTheClass() {
        assertThatThrownBy(() -> R2rmlGenerator.generate("fr", List.of(UnsupportedClass.class)))
                .hasMessageContaining("UnsupportedClass: unsupported @OntologyClass atelier:Interface")
                .hasMessageContaining("(expected one of [atelier:BomLine, atelier:ExternalReference, atelier:Fastener, atelier:HydraulicCoupling, "
                        + "atelier:Occurrence, atelier:Part, atelier:Plug, atelier:Supplier, atelier:SupplierOffer])");
    }

    @Test
    void declaredSubjectTemplateIsUsedVerbatimWithoutOwnedByAndIsExactlyTheExpectedDocument() throws IOException {
        String generated = R2rmlGenerator.generate("core", List.of(PartTag.class));
        assertThat(generated).isEqualTo(resource("/r2rml/core-tags.r2rml.ttl"));
        assertThat(generated).doesNotContain("ownedBy");
    }

    @Test
    void productMembershipMintsTheProductIriAndIsExactlyTheExpectedDocument() throws IOException {
        String generated = R2rmlGenerator.generate("core", List.of(ProductPart.class, Product.class));
        assertThat(generated).isEqualTo(resource("/r2rml/core-products.r2rml.ttl"));
        assertThat(generated).doesNotContain("ownedBy");
    }

    @Test
    void instantAndOffsetDateTimeColumnsAreDateTimeLiterals() {
        String generated = R2rmlGenerator.generate("fr", List.of(InstantStamp.class));
        assertThat(generated).contains(
                "rr:subjectMap [ rr:template \"https://example.com/atelier/{plm}/plug/{id_stamp}\" ; rr:class atelier:Plug ] ;\n"
                + "  rr:predicateObjectMap [ rr:predicate atelier:taggedAt ; rr:objectMap [ rr:column \"at\" ] ] .");
    }

    @Test
    void rejectsSubjectTemplateNamingAColumnTheEntityDoesNotMap() {
        assertThatThrownBy(() -> R2rmlGenerator.generate("core", List.of(MistypedTemplate.class)))
                .hasMessageContaining("MistypedTemplate: subject template names column native_kay")
                .hasMessageContaining("[plm, native_key]");
    }

    @Test
    void namesTheModuleOfEachServiceCode() {
        assertThat(R2rmlGenerator.moduleName("fr")).isEqualTo("plm-fr");
        assertThat(R2rmlGenerator.moduleName("core")).isEqualTo("atelier-core");
    }

    @Test
    void scansEntitiesOfAPackage() {
        assertThat(R2rmlGenerator.scanEntities("atelier.plm.common.r2rml"))
                .contains(Component.class, HarnessConnector.class, NotInOntology.class);
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = R2rmlGeneratorTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
