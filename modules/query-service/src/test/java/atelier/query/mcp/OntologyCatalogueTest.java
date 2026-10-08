// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import atelier.query.Atelier;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Query;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

/** The ontology tool's answer against the shipped ontology/atelier.ttl and ontology/shapes.ttl it is derived from. */
class OntologyCatalogueTest {
    private static final KnownTerms TERMS = KnownTerms.load();
    private static final OntologyCatalogue.Description ONTOLOGY = OntologyCatalogue.of(TERMS).describe();

    @Test
    void listsEveryClassAndPropertyTheOntologyDeclares() {
        Model declared = RDFDataMgr.loadModel("ontology/atelier.ttl");
        Set<String> classes = declared.listSubjectsWithProperty(RDF.type, OWL.Class).mapWith(Resource::getURI).toSet();
        Set<String> properties = Stream.of(OWL.ObjectProperty, OWL.DatatypeProperty)
                .flatMap(type -> declared.listSubjectsWithProperty(RDF.type, type).mapWith(Resource::getURI).toList().stream())
                .collect(Collectors.toSet());
        assertThat(classes).isNotEmpty();
        assertThat(properties).isNotEmpty();
        assertThat(ONTOLOGY.version()).isNotBlank()
                .isEqualTo(declared.listSubjectsWithProperty(RDF.type, OWL.Ontology).next().getProperty(OWL.versionInfo).getString());
        assertThat(ONTOLOGY.classes()).extracting(c -> expand(c.iri())).containsExactlyInAnyOrderElementsOf(classes);
        assertThat(ONTOLOGY.properties()).extracting(p -> expand(p.iri())).containsExactlyInAnyOrderElementsOf(properties);
        assertThat(ONTOLOGY.classes()).extracting(OntologyCatalogue.ClassEntry::iri).isSorted();
        assertThat(ONTOLOGY.properties()).extracting(OntologyCatalogue.PropertyEntry::iri).isSorted();
        assertThat(ONTOLOGY.classes()).allSatisfy(c -> assertThat(TERMS.allowsClass(expand(c.iri()))).as(c.iri()).isTrue());
        assertThat(ONTOLOGY.properties()).allSatisfy(p -> assertThat(TERMS.allowsProperty(expand(p.iri()))).as(p.iri()).isTrue());
        assertThat(ONTOLOGY.prefixes()).containsExactly(entry("atelier", Atelier.ONT), entry("qudt", Atelier.QUDT), entry("unit", Atelier.UNIT),
                entry("rdf", RDF.getURI()), entry("rdfs", "http://www.w3.org/2000/01/rdf-schema#"),
                entry("xsd", "http://www.w3.org/2001/XMLSchema#"), entry("sh", KnownTerms.SHACL), entry("ateliersh", Atelier.SHAPES));

        OntologyCatalogue.ClassEntry plug = clazz("atelier:Plug");
        assertThat(plug.label()).isEqualTo("Plug");
        assertThat(plug.subClassOf()).isEqualTo("atelier:InterfaceFeature");
        assertThat(plug.comment()).as("the first sentence of the rdfs:comment")
                .isEqualTo("An electrical connector located on a part: an interface feature with a connector type and a pin count.");
        assertThat(clazz("atelier:Part").subClassOf()).isNull();
        assertThat(clazz("qudt:QuantityValue").label()).isEqualTo("Quantity value");

        OntologyCatalogue.PropertyEntry onPart = property("atelier:onPart");
        assertThat(onPart.kind()).isEqualTo("object");
        assertThat(onPart.domain()).isEqualTo("atelier:InterfaceFeature");
        assertThat(onPart.range()).isEqualTo("atelier:Part");
        OntologyCatalogue.PropertyEntry pinCount = property("atelier:pinCount");
        assertThat(pinCount.kind()).isEqualTo("datatype");
        assertThat(pinCount.domain()).isEqualTo("atelier:Plug");
        assertThat(pinCount.range()).isEqualTo("xsd:integer");
        assertThat(pinCount.comment()).isEqualTo("Number of contacts of the plug.");
        assertThat(property("atelier:identifier").domain()).as("no domain declared").isNull();
        assertThat(property("atelier:taggedAt").range()).as("no range declared").isNull();
        assertThat(property("atelier:matesWith").kind()).as("also an owl:SymmetricProperty, listed once").isEqualTo("object");
        assertThat(property("qudt:unit").comment()).startsWith("QUDT unit of a quantity value");
    }

    @Test
    void namesTheNamedGraphsAndEveryRuleOfTheShapes() {
        assertThat(ONTOLOGY.graphs()).extracting(OntologyCatalogue.GraphEntry::iri).containsExactly(Atelier.LINKS_GRAPH, Atelier.FILE_INDEX_GRAPH);
        assertThat(ONTOLOGY.graphs().get(0).holds()).contains("atelier:betweenPart", "atelier:declaresFeature", "atelier:matesWith", "atelier:toleranceMm");
        assertThat(ONTOLOGY.graphs().get(1).holds()).contains("atelier:cadFile", "atelier:builtBy");

        Graph shapes = RDFDataMgr.loadGraph(OntologyCatalogue.SHAPES_FILE);
        Set<String> declared = shapes.find(Node.ANY, NodeFactory.createURI(Atelier.SHAPES + "rule"), Node.ANY)
                .mapWith(t -> t.getObject().getLiteralLexicalForm()).toSet();
        assertThat(declared).contains("unit", "position", "cadMissing");
        assertThat(ONTOLOGY.rules()).extracting(OntologyCatalogue.Rule::name).containsExactlyInAnyOrderElementsOf(declared).isSorted();

        OntologyCatalogue.Rule cadMissing = rule("cadMissing");
        assertThat(cadMissing.shape()).isEqualTo("ateliersh:PartCadShape");
        assertThat(cadMissing.severity()).isEqualTo("Warning");
        assertThat(cadMissing.message()).isEqualTo("Every part with geometry (part type PART) carries exactly one atelier:cadFile from the file index.");
        OntologyCatalogue.Rule position = rule("position");
        assertThat(position.shape()).isEqualTo("ateliersh:PositionShape");
        assertThat(position.severity()).as("sh:Violation when the shape states no severity").isEqualTo("Violation");
        assertThat(position.message()).contains("atelier:toleranceMm");
        OntologyCatalogue.Rule leadTime = rule("conflictingLeadTime");
        assertThat(leadTime.shape()).isEqualTo("ateliersh:ConflictingLeadTimeShape");
        assertThat(leadTime.severity()).isEqualTo("Warning");
        assertThat(rule("massScale").severity()).isEqualTo("Warning");
        assertThat(rule("massLimit").severity()).as("a product over its limit fails").isEqualTo("Violation");
        assertThat(ONTOLOGY.rules()).filteredOn(r -> r.severity().equals("Warning")).extracting(OntologyCatalogue.Rule::name)
                .as("the data-quality shapes on parts").containsExactlyInAnyOrder("cadMissing", "massScale", "danglingReference",
                        "staleRevision", "conflictingLeadTime", "lifecycleConflict", "meshModule");
        assertThat(ONTOLOGY.rules()).filteredOn(r -> !r.severity().equals("Warning"))
                .allSatisfy(r -> assertThat(r.severity()).as(r.name()).isEqualTo("Violation"));
    }

    @Test
    void examplesPassTheGuardAndTheAnswerStaysCompact() throws Exception {
        List<OntologyCatalogue.Example> examples = ONTOLOGY.examples();
        assertThat(examples).hasSize(3);
        for (OntologyCatalogue.Example example : examples) {
            assertThat(example.question()).endsWith("?");
            Query query = SparqlGuard.check(example.query(), TERMS);
            assertThat(query.isSelectType()).as(example.question()).isTrue();
            assertThat(query.getLimit()).isEqualTo(SparqlGuard.LIMIT);
        }
        assertThat(examples.get(0).query()).contains("atelier:Plug", "atelier:ownedBy");
        assertThat(examples.get(1).query()).contains("atelier:betweenPart", "atelier:declaresFeature", "qudt:unit");
        assertThat(examples.get(2).query()).contains("atelier:connectorType");

        String json = new ObjectMapper().writeValueAsString(ONTOLOGY);
        assertThat(json.getBytes(StandardCharsets.UTF_8).length).isLessThan(32 * 1024);
        assertThat(json).as("an absent domain, range or subClassOf is left out").doesNotContain("null");
        assertThat(json).startsWith("{\"version\":\"" + ONTOLOGY.version() + "\",\"prefixes\":{\"atelier\":");
    }

    @Test
    void aCommentIsItsFirstSentence() {
        assertThat(OntologyCatalogue.sentence("A physical part owned by one site PLM, such as a wing spar or a mast segment. Corresponds to an AP242 product."))
                .isEqualTo("A physical part owned by one site PLM, such as a wing spar or a mast segment.");
        assertThat(OntologyCatalogue.sentence("Mated groups must agree within 0.05 mm after conversion. Node IRI <feature IRI>/diameter."))
                .isEqualTo("Mated groups must agree within 0.05 mm after conversion.");
        assertThat(OntologyCatalogue.sentence("QUDT numeric value of a quantity value, as stored by the PLM (xsd:decimal)."))
                .isEqualTo("QUDT numeric value of a quantity value, as stored by the PLM (xsd:decimal).");
    }

    private static OntologyCatalogue.ClassEntry clazz(String iri) {
        return ONTOLOGY.classes().stream().filter(c -> c.iri().equals(iri)).findFirst().orElseThrow();
    }

    private static OntologyCatalogue.PropertyEntry property(String iri) {
        return ONTOLOGY.properties().stream().filter(p -> p.iri().equals(iri)).findFirst().orElseThrow();
    }

    private static OntologyCatalogue.Rule rule(String name) {
        return ONTOLOGY.rules().stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
    }

    private static String expand(String curie) {
        int colon = curie.indexOf(':');
        return ONTOLOGY.prefixes().get(curie.substring(0, colon)) + curie.substring(colon + 1);
    }
}
