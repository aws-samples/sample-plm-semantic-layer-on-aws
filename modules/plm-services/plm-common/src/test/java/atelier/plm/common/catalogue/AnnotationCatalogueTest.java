// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.catalogue;

import atelier.plm.common.annotation.Accepts;
import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import atelier.plm.common.annotation.Unit;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationCatalogueTest {

    @Entity
    @Table(name = "prise")
    @OntologyClass("atelier:Plug")
    @Describe("Test connector")
    static class TestPlug {
        @Id
        @Column(name = "id_prise")
        @Describe("Connector key")
        @Maps("atelier:identifier")
        String id;

        @Column(name = "pos_x_mm")
        @Describe("X position")
        @Unit("MilliM")
        @Maps("atelier:positionX")
        BigDecimal x;

        @Column(name = "pos_y")
        @Describe("Y position, unit not declared")
        @Maps("atelier:positionY")
        double y;

        @Column(name = "pos_z")
        @Describe("Z position, unit read per row")
        @Unit(column = "pos_uom", stores = {"MilliM", "IN"})
        @Maps("atelier:positionZ")
        BigDecimal z;

        @Column(name = "longueur")
        @Describe("Length, unit read per row")
        @Unit(column = "pos_uom", stores = {"IN", "CentiM"})
        @Maps("atelier:nominalLength")
        BigDecimal length;

        @Column(name = "pos_uom")
        @Describe("Unit of the positions and the length")
        String unit;

        @Column(name = "etat")
        @Describe("Lifecycle state")
        @Accepts({"En cours", "Publié"})
        @Maps("atelier:lifecycleLabel")
        String state;

        @Column(name = "indice")
        @Describe("Revision")
        @Accepts(pattern = "[A-Z]")
        @Maps("atelier:revision")
        String revision;

        @Column(name = "remarque")
        String note;

        @Column(name = "nb_broches")
        @Describe("Pin count")
        @Maps("atelier:pinCount")
        Integer pinCount;

        @Transient
        String computed;
    }

    /** Rows about another service's subjects: the IRI template names two columns, the last is the lookup key. */
    @Entity
    @Table(name = "part_tag")
    @OntologyClass(value = "atelier:Part", subject = "https://example.com/atelier/{plm}/part/{native_key}")
    static class TestTag {
        @Id
        @Column(name = "plm")
        String plm;

        @Id
        @Column(name = "native_key")
        String nativeKey;
    }

    /** A bill-of-materials link table: its subject ends with the child, the column a line is looked up by. */
    @Entity
    @Table(name = "nomenclature")
    @OntologyClass("atelier:BomLine")
    static class TestLine {
        @Id
        @Column(name = "parent")
        @Maps("atelier:parent")
        String parent;

        @Id
        @Column(name = "enfant")
        @Maps("atelier:child")
        String enfant;
    }

    private final EntityEntry entity =
            AnnotationCatalogue.fromClasses("TEST", List.of(TestPlug.class)).entities().get(0);

    @Test
    void keyColumnIsTheIdColumnOrTheColumnEndingTheDeclaredSubject() {
        assertThat(AnnotationCatalogue.subjectTemplate(TestPlug.class)).isEmpty();
        assertThat(AnnotationCatalogue.keyColumn(TestPlug.class)).isEqualTo("id_prise");

        assertThat(AnnotationCatalogue.subjectTemplate(TestTag.class)).contains("https://example.com/atelier/{plm}/part/{native_key}");
        assertThat(AnnotationCatalogue.templateColumns("https://example.com/atelier/{plm}/part/{native_key}"))
                .containsExactly("plm", "native_key");
        assertThat(AnnotationCatalogue.keyColumn(TestTag.class)).isEqualTo("native_key");

        assertThat(AnnotationCatalogue.keyColumn(TestLine.class)).isEqualTo("enfant");
    }

    private ColumnEntry column(String name) {
        return entity.columns().stream().filter(c -> c.column().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void readsTableAndOntologyClass() {
        assertThat(entity.table()).isEqualTo("prise");
        assertThat(entity.ontologyClass()).isEqualTo("atelier:Plug");
        assertThat(entity.description()).isEqualTo("Test connector");
    }

    @Test
    void excludesTransientFields() {
        assertThat(entity.columns()).extracting(ColumnEntry::field)
                .containsExactly("id", "x", "y", "z", "length", "unit", "state", "revision", "note", "pinCount");
    }

    @Test
    void flagsFieldWithoutDescribeAsUndescribed() {
        assertThat(column("remarque").undescribed()).isTrue();
        assertThat(column("pos_x_mm").undescribed()).isFalse();
    }

    @Test
    void flagsPositionFieldWithoutUnit() {
        ColumnEntry y = column("pos_y");
        assertThat(y.unitMissing()).isTrue();
        assertThat(y.unit()).isNull();
        assertThat(y.unitColumn()).isNull();
        assertThat(y.javaType()).isEqualTo("double");
    }

    @Test
    void doesNotFlagPositionWithUnitOrNonPositionNumber() {
        ColumnEntry x = column("pos_x_mm");
        assertThat(x.unitMissing()).isFalse();
        assertThat(x.unit()).isEqualTo("MilliM");
        assertThat(x.unitColumn()).isNull();
        assertThat(x.ontologyTerm()).isEqualTo("atelier:positionX");
        assertThat(column("nb_broches").unitMissing()).isFalse();
    }

    @Test
    void reportsPerRowUnitColumnWithoutFlaggingItMissing() {
        ColumnEntry z = column("pos_z");
        assertThat(z.unitColumn()).isEqualTo("pos_uom");
        assertThat(z.unit()).isNull();
        assertThat(z.unitMissing()).isFalse();
    }

    @Test
    void reportsTheWordsOrTheFormATextColumnAccepts() {
        assertThat(column("etat").accepts()).containsExactly("En cours", "Publié");
        assertThat(column("etat").pattern()).isNull();
        assertThat(column("indice").pattern()).isEqualTo("[A-Z]");
        assertThat(column("indice").accepts()).isNull();
        assertThat(column("remarque").accepts()).isNull();
        assertThat(column("remarque").pattern()).isNull();
    }

    @Test
    void reportsTheDistinctUnitsAUnitColumnStoresInDeclarationOrder() {
        assertThat(column("pos_uom").accepts()).containsExactly("MilliM", "IN", "CentiM");
        assertThat(column("pos_uom").pattern()).isNull();
        assertThat(column("pos_z").accepts()).as("the measure itself accepts any number").isNull();
    }

    @Test
    void omitsAcceptsAndPatternFromTheJsonWhenAbsent() throws Exception {
        ObjectMapper json = new ObjectMapper();
        assertThat(json.readTree(json.writeValueAsString(column("remarque"))).has("accepts")).isFalse();
        assertThat(json.readTree(json.writeValueAsString(column("remarque"))).has("pattern")).isFalse();
        assertThat(json.readTree(json.writeValueAsString(column("remarque"))).has("description")).as("other nulls stay").isTrue();
        assertThat(json.readTree(json.writeValueAsString(column("indice"))).get("pattern").asText()).isEqualTo("[A-Z]");
        assertThat(json.readTree(json.writeValueAsString(column("pos_uom"))).get("accepts")).hasSize(3);
    }
}
