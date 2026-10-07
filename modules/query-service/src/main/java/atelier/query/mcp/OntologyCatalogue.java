// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mcp;

import atelier.query.Atelier;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

/**
 * The answer of the {@code ontology} tool and of {@code GET /query/ontology}: the ontology the
 * {@code sparql} tool is checked against, derived on each call from the loaded ontology/atelier.ttl
 * (version, classes, properties with domain and range) and ontology/shapes.ttl (the rules), with the
 * named graphs of the link store and three example patterns the guard accepts. Classes, properties
 * and shapes are prefixed names under {@code prefixes}; graphs are plain IRIs. A comment is the
 * first sentence of the rdfs:comment and a rule's message is what its shape requires (the shape's
 * rdfs:comment; the filled-in sh:message texts come back from list_interfaces and interface_check),
 * which keeps the whole answer under 32 KiB. The merged graph the sparql tool queries is one default
 * graph: the named graphs say where triples come from, not how to address them.
 */
public final class OntologyCatalogue {
    static final String SHAPES_FILE = "ontology/shapes.ttl";
    private static final Node RULE = NodeFactory.createURI(Atelier.SHAPES + "rule");
    private static final Node SEVERITY = NodeFactory.createURI(KnownTerms.SHACL + "severity");
    private static final String PREFIX = "PREFIX atelier: <" + Atelier.ONT + "> PREFIX qudt: <" + Atelier.QUDT + "> ";

    static final List<GraphEntry> GRAPHS = List.of(
            new GraphEntry(Atelier.LINKS_GRAPH, "the interfaces of the link store: " + curies(Atelier.LINK_PREDICATES)
                    + "; the functional edges (atelier:Drive) and rated speeds: " + curies(Atelier.FUNCTION_PREDICATES)),
            new GraphEntry(Atelier.FILE_INDEX_GRAPH, "the file index of the link store, per part: " + curies(Atelier.FILE_INDEX_PREDICATES)));

    static final List<Example> EXAMPLES = List.of(
            new Example("How many plugs does each PLM own?", PREFIX
                    + "SELECT ?owner (COUNT(?plug) AS ?plugs) WHERE { ?plug a atelier:Plug ; atelier:ownedBy ?owner } GROUP BY ?owner ORDER BY ?owner"),
            new Example("Which parts carry features whose quantity values have no unit (the unit rule), on how many interfaces?", PREFIX
                    + "SELECT ?part (COUNT(DISTINCT ?interface) AS ?interfaces) (COUNT(DISTINCT ?feature) AS ?featuresWithoutUnit) WHERE {"
                    + " ?interface a atelier:Interface ; atelier:betweenPart ?part ; atelier:declaresFeature ?feature ."
                    + " ?feature atelier:onPart ?part ; atelier:positionX|atelier:positionY|atelier:positionZ|atelier:diameter|atelier:gripLength|atelier:pressureRating ?quantity ."
                    + " FILTER NOT EXISTS { ?quantity qudt:unit ?unit } } GROUP BY ?part ORDER BY DESC(?featuresWithoutUnit)"),
            new Example("Which connector types are in use across the PLMs, in how many PLMs and on how many plugs?", PREFIX
                    + "SELECT ?connectorType (COUNT(DISTINCT ?owner) AS ?plms) (COUNT(?plug) AS ?plugs) WHERE {"
                    + " ?plug a atelier:Plug ; atelier:connectorType ?connectorType ; atelier:ownedBy ?owner } GROUP BY ?connectorType ORDER BY DESC(?plms) ?connectorType"));

    private final Model ontology;
    private final Graph shapes;

    public OntologyCatalogue(Model ontology, Graph shapes) {
        this.ontology = ontology;
        this.shapes = shapes;
    }

    /** Over the ontology the guard checks against and the shipped ontology/shapes.ttl. */
    public static OntologyCatalogue of(KnownTerms terms) {
        return new OntologyCatalogue(terms.ontology(), RDFDataMgr.loadGraph(SHAPES_FILE));
    }

    public Description describe() {
        Resource header = ontology.listSubjectsWithProperty(RDF.type, OWL.Ontology).next();
        List<ClassEntry> classes = ontology.listSubjectsWithProperty(RDF.type, OWL.Class).toList().stream()
                .map(c -> new ClassEntry(curie(c), text(c, RDFS.label), sentence(c, RDFS.comment), iri(c, RDFS.subClassOf)))
                .sorted(Comparator.comparing(ClassEntry::iri)).toList();
        List<PropertyEntry> properties = Stream.of(OWL.ObjectProperty, OWL.DatatypeProperty)
                .flatMap(type -> ontology.listSubjectsWithProperty(RDF.type, type).toList().stream().map(p -> new PropertyEntry(
                        curie(p), type.equals(OWL.ObjectProperty) ? "object" : "datatype", iri(p, RDFS.domain), iri(p, RDFS.range), sentence(p, RDFS.comment))))
                .sorted(Comparator.comparing(PropertyEntry::iri)).toList();
        return new Description(text(header, OWL.versionInfo), KnownTerms.PREFIXES, classes, properties, GRAPHS, rules(), EXAMPLES);
    }

    /** One entry per shape carrying ateliersh:rule, by rule name; severity sh:Violation unless the shape states another. */
    private List<Rule> rules() {
        List<Rule> rules = new ArrayList<>();
        shapes.find(Node.ANY, RULE, Node.ANY).forEachRemaining(t -> {
            Node shape = t.getSubject();
            Node severity = shapes.find(shape, SEVERITY, Node.ANY).mapWith(s -> s.getObject()).nextOptional()
                    .orElse(NodeFactory.createURI(KnownTerms.SHACL + "Violation"));
            String comment = shapes.find(shape, RDFS.comment.asNode(), Node.ANY).mapWith(s -> s.getObject().getLiteralLexicalForm())
                    .nextOptional().orElse("");
            rules.add(new Rule(t.getObject().getLiteralLexicalForm(), KnownTerms.curie(shape.getURI()),
                    severity.getLocalName(), sentence(comment)));
        });
        rules.sort(Comparator.comparing(Rule::name));
        return rules;
    }

    /** The first sentence of a comment: up to the first full stop followed by a space and a capital letter. */
    static String sentence(String comment) {
        return comment.split("(?<=\\.)\\s+(?=[A-Z])", 2)[0];
    }

    private static String sentence(Resource subject, Property property) {
        String text = text(subject, property);
        return text == null ? null : sentence(text);
    }

    private static String text(Resource subject, Property property) {
        Statement statement = subject.getProperty(property);
        return statement == null ? null : statement.getString();
    }

    private static String iri(Resource subject, Property property) {
        Statement statement = subject.getProperty(property);
        return statement == null ? null : curie(statement.getResource());
    }

    private static String curie(Resource resource) {
        return KnownTerms.curie(resource.getURI());
    }

    private static String curies(List<String> iris) {
        return iris.stream().map(KnownTerms::curie).collect(Collectors.joining(", "));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Description(String version, Map<String, String> prefixes, List<ClassEntry> classes,
                              List<PropertyEntry> properties, List<GraphEntry> graphs, List<Rule> rules, List<Example> examples) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClassEntry(String iri, String label, String comment, String subClassOf) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PropertyEntry(String iri, String kind, String domain, String range, String comment) {}

    public record GraphEntry(String iri, String holds) {}

    public record Rule(String name, String shape, String severity, String message) {}

    public record Example(String question, String query) {}
}
