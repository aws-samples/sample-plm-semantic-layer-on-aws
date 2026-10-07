// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

/** The diff of a live graph against its released content: how each term is shortened, and the IRIs and PLM kept beside. */
class GraphDiffTest {
    static final String PREFIXES = """
            @prefix atelier:  <https://example.com/atelier/ontology#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .
            @prefix unit: <http://qudt.org/vocab/unit/> .
            """;
    static final String DATA = "https://example.com/atelier/";
    static final String IF30 = DATA + "interface/ornithopter/IF-30";
    static final String SERVO = DATA + "es/plug/SERV-6120-C01";
    static final String POST = DATA + "fr/plug/FR-ORN-PCMD-001-J01";
    static final String ROOT = DATA + "uk/plug/PL%206180-01";

    static Model turtle(String body) {
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, new StringReader(PREFIXES + body), null, Lang.TURTLE);
        return model;
    }

    @Test
    void addedAndRemovedTriplesAreListedSortedAndShortenedWithTheirIrisAndPlmBeside() {
        Model released = turtle("""
                <https://example.com/atelier/interface/ornithopter/IF-30> a atelier:Interface ; rdfs:label "Control post / tail servo" ; atelier:toleranceMm "2.0"^^xsd:decimal .
                <https://example.com/atelier/uk/plug/PL%206180-01> atelier:matesWith <https://example.com/atelier/es/plug/SERV-6120-C01> .
                <https://example.com/atelier/es/part/SERV-6120> atelier:cadFile "cad/ornithopter/es-tail-servo.stp" .
                """);
        Model live = turtle("""
                <https://example.com/atelier/interface/ornithopter/IF-30> a atelier:Interface ; rdfs:label "Control post / tail servo" ; atelier:toleranceMm "2.0"^^xsd:decimal .
                <https://example.com/atelier/es/part/SERV-6120> atelier:cadFile "cad/ornithopter/es-tail-servo.stp" .
                <https://example.com/atelier/fr/part/FR-ORN-CERC-001> atelier:cadFile "cad/ornithopter/fr-head-hoop.stp" .
                <https://example.com/atelier/fr/plug/FR-ORN-PCMD-001-J01> atelier:matesWith <https://example.com/atelier/es/plug/SERV-6120-C01> .
                <https://example.com/atelier/es/plug/SERV-6120-C01> atelier:matesWith <https://example.com/atelier/fr/plug/FR-ORN-PCMD-001-J01> .
                <https://example.com/atelier/es/plug/SERV-6120-C01> atelier:positionX <https://example.com/atelier/es/plug/SERV-6120-C01/position/x> .
                <https://example.com/atelier/es/plug/SERV-6120-C01/position/x> <http://qudt.org/schema/qudt/unit> unit:MilliM .
                """);

        GraphDiff.Diff diff = GraphDiff.of(released, live);

        assertThat(diff.removed()).containsExactly(
                new GraphDiff.Triple("PL 6180-01", "atelier:matesWith", "SERV-6120-C01", ROOT, SERVO, "uk"));
        assertThat(diff.added()).as("a literal object has no IRI; a part's PLM is read from its IRI; a position node is no PLM's").containsExactly(
                new GraphDiff.Triple("<" + SERVO + "/position/x>", "<http://qudt.org/schema/qudt/unit>", "<http://qudt.org/vocab/unit/MilliM>",
                        SERVO + "/position/x", "http://qudt.org/vocab/unit/MilliM", null),
                new GraphDiff.Triple("FR-ORN-CERC-001", "atelier:cadFile", "cad/ornithopter/fr-head-hoop.stp", DATA + "fr/part/FR-ORN-CERC-001", null, "fr"),
                new GraphDiff.Triple("FR-ORN-PCMD-001-J01", "atelier:matesWith", "SERV-6120-C01", POST, SERVO, "fr"),
                new GraphDiff.Triple("SERV-6120-C01", "atelier:matesWith", "FR-ORN-PCMD-001-J01", SERVO, POST, "es"),
                new GraphDiff.Triple("SERV-6120-C01", "atelier:positionX", "<" + SERVO + "/position/x>", SERVO, SERVO + "/position/x", "es"));
    }

    @Test
    void identicalGraphsDiffToNothingAndEveryTermKindShortens() {
        Model released = turtle("""
                <https://example.com/atelier/interface/ornithopter/IF-30> a atelier:Interface ; rdfs:label "Control post / tail servo" ; atelier:toleranceMm "2.0"^^xsd:decimal .
                """);
        GraphDiff.Diff same = GraphDiff.of(released, turtle("""
                <https://example.com/atelier/interface/ornithopter/IF-30> atelier:toleranceMm "2.0"^^xsd:decimal ; rdfs:label "Control post / tail servo" ; a atelier:Interface .
                """));
        assertThat(same.added()).isEmpty();
        assertThat(same.removed()).isEmpty();

        GraphDiff.Diff all = GraphDiff.of(ModelFactory.createDefaultModel(), released);
        assertThat(all.added()).as("an interface is Atelier's, not a PLM's").containsExactly(
                new GraphDiff.Triple("IF-30", "atelier:toleranceMm", "2.0", IF30, null, null),
                new GraphDiff.Triple("IF-30", "rdf:type", "atelier:Interface", IF30, "https://example.com/atelier/ontology#Interface", null),
                new GraphDiff.Triple("IF-30", "rdfs:label", "Control post / tail servo", IF30, null, null));
    }

    @Test
    void aStoreThatCanonicalisesNumeralsReportsNoChangeWhileStringsAndIrisCompareExactly() {
        Model released = turtle("""
                <https://example.com/atelier/interface/ornithopter/IF-30> atelier:toleranceMm "2.0"^^xsd:decimal ; rdfs:label "Control post / tail servo" .
                <https://example.com/atelier/interface/ornithopter/IF-31> atelier:toleranceMm "1.50"^^xsd:decimal .
                <https://example.com/atelier/uk/plug/PL%206180-01> atelier:pinCount "037"^^xsd:integer ; atelier:sealed "1"^^xsd:boolean ;
                    atelier:positionX "1.9E3"^^xsd:double .
                """);
        Model live = turtle("""
                <https://example.com/atelier/interface/ornithopter/IF-30> atelier:toleranceMm "2"^^xsd:decimal ; rdfs:label "Control post / tail servo" .
                <https://example.com/atelier/interface/ornithopter/IF-31> atelier:toleranceMm "1.5"^^xsd:decimal .
                <https://example.com/atelier/uk/plug/PL%206180-01> atelier:pinCount "37"^^xsd:integer ; atelier:sealed "true"^^xsd:boolean ;
                    atelier:positionX "1900.0"^^xsd:double .
                """);
        GraphDiff.Diff same = GraphDiff.of(released, live);
        assertThat(same.added()).isEmpty();
        assertThat(same.removed()).isEmpty();

        GraphDiff.Diff different = GraphDiff.of(released, turtle("""
                <https://example.com/atelier/interface/ornithopter/IF-30> atelier:toleranceMm "2.1"^^xsd:decimal ; rdfs:label "control post / tail servo" .
                <https://example.com/atelier/interface/ornithopter/IF-31> atelier:toleranceMm "1.50"^^xsd:decimal .
                <https://example.com/atelier/uk/plug/PL%206180-01> atelier:pinCount "037"^^xsd:integer ; atelier:sealed "1"^^xsd:boolean ;
                    atelier:positionX <https://example.com/atelier/uk/plug/PL%206180-01/position/x> .
                """));
        assertThat(different.removed()).containsExactly(
                new GraphDiff.Triple("IF-30", "atelier:toleranceMm", "2.0", IF30, null, null),
                new GraphDiff.Triple("IF-30", "rdfs:label", "Control post / tail servo", IF30, null, null),
                new GraphDiff.Triple("PL 6180-01", "atelier:positionX", "1.9E3", ROOT, null, "uk"));
        assertThat(different.added()).containsExactly(
                new GraphDiff.Triple("IF-30", "atelier:toleranceMm", "2.1", IF30, null, null),
                new GraphDiff.Triple("IF-30", "rdfs:label", "control post / tail servo", IF30, null, null),
                new GraphDiff.Triple("PL 6180-01", "atelier:positionX", "<" + ROOT + "/position/x>", ROOT, ROOT + "/position/x", "uk"));
    }
}
