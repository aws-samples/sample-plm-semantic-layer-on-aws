// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.rules;

import atelier.query.Atelier;
import atelier.query.evidence.ShapeSource;
import atelier.query.mcp.KnownTerms;
import atelier.query.validation.RuleValidator;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFWriterRegistry;
import org.apache.jena.riot.RIOT;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.system.PrefixMapFactory;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.stereotype.Component;

/**
 * The answer of {@code GET /query/rules}: every shape of the loaded ontology/shapes.ttl that carries ateliersh:rule, with
 * its severity, its focus (ateliersh:focus), what it requires (rdfs:comment), its plain-language account
 * (sh:description), its Turtle serialised from the loaded shapes graph, the SPARQL of its constraints and SPARQL target,
 * and the atelier: terms it reads with their definitions in ontology/atelier.ttl. Built once: the shapes are fixed for
 * the life of the service.
 */
@Component
public class RuleCatalogue {
    private static final String SH = KnownTerms.SHACL;
    private static final Node RULE = NodeFactory.createURI(Atelier.SHAPES + "rule");
    private static final Node FOCUS = NodeFactory.createURI(Atelier.SHAPES + "focus");
    private static final Node SEVERITY = NodeFactory.createURI(SH + "severity");
    private static final Node DESCRIPTION = NodeFactory.createURI(SH + "description");
    private static final Node SPARQL = NodeFactory.createURI(SH + "sparql");
    private static final Node TARGET = NodeFactory.createURI(SH + "target");
    private static final Node SELECT = NodeFactory.createURI(SH + "select");
    private static final Node MESSAGE = NodeFactory.createURI(SH + "message");
    private static final Pattern TERM = Pattern.compile("atelier:([A-Za-z][A-Za-z0-9_]*)");
    private static final PrefixMap PREFIXES = PrefixMapFactory.create();

    static {
        KnownTerms.PREFIXES.forEach(PREFIXES::add);
        PREFIXES.add("skos", Atelier.SKOS);
    }

    private final List<Rule> rules;

    public RuleCatalogue(RuleValidator validator) {
        Graph shapes = validator.shapesGraph();
        Model ontology = KnownTerms.load().ontology();
        this.rules = shapes.find(Node.ANY, RULE, Node.ANY).toList().stream()
                .map(t -> rule(shapes, ontology, t.getSubject(), t.getObject().getLiteralLexicalForm()))
                .sorted(Comparator.comparing(Rule::name)).toList();
    }

    public Rules describe() {
        return new Rules(rules);
    }

    /** The rule names the shapes declare. */
    public List<String> names() {
        return rules.stream().map(Rule::name).toList();
    }

    private static Rule rule(Graph shapes, Model ontology, Node shape, String name) {
        Graph source = canonical(ShapeSource.of(shapes, shape.getURI()));
        String severity = object(shapes, shape, SEVERITY).map(n -> Atelier.localName(n.getURI())).orElse("Violation");
        List<Sparql> sparql = Stream.concat(
                        queries(shapes, shape, SPARQL).map(q -> new Sparql("constraint", literal(shapes, q, MESSAGE), literal(shapes, q, SELECT))),
                        queries(shapes, shape, TARGET).map(q -> new Sparql("target", null, literal(shapes, q, SELECT))))
                .sorted(Comparator.comparing(Sparql::role).thenComparing(s -> String.valueOf(s.message()))).toList();
        return new Rule(name, shape.getURI(), severity, literal(shapes, shape, FOCUS), literal(shapes, shape, RDFS.comment.asNode()),
                literal(shapes, shape, DESCRIPTION), turtle(source), sparql, terms(source, ontology));
    }

    /** The blank nodes hanging off the shape by {@code property} that carry a SELECT query. */
    private static Stream<Node> queries(Graph shapes, Node shape, Node property) {
        return shapes.find(shape, property, Node.ANY).mapWith(Triple::getObject).toList().stream()
                .filter(n -> shapes.contains(n, SELECT, Node.ANY));
    }

    /** Every atelier: term the shape's triples name as a node or its SPARQL names as a prefixed name, in name order. */
    private static List<Term> terms(Graph source, Model ontology) {
        TreeSet<String> iris = new TreeSet<>();
        source.find().forEachRemaining(t -> Stream.of(t.getSubject(), t.getPredicate(), t.getObject()).forEach(n -> {
            if (n.isURI() && n.getURI().startsWith(Atelier.ONT)) iris.add(n.getURI());
            if (n.isLiteral()) {
                Matcher m = TERM.matcher(n.getLiteralLexicalForm());
                while (m.find()) iris.add(Atelier.ONT + m.group(1));
            }
        }));
        return iris.stream().map(iri -> term(ontology.createResource(iri))).toList();
    }

    private static Term term(Resource r) {
        String kind = r.hasProperty(RDF.type, OWL.Class) ? "class"
                : r.hasProperty(RDF.type, OWL.ObjectProperty) || r.hasProperty(RDF.type, OWL.DatatypeProperty) ? "property"
                : r.hasProperty(RDF.type, r.getModel().createResource(Atelier.SKOS + "Concept")) ? "concept" : "other";
        Statement comment = r.getProperty(RDFS.comment);
        return new Term(KnownTerms.curie(r.getURI()), kind, comment == null ? null : comment.getString());
    }

    /**
     * The shape's graph with each blank node renamed after a digest of what it holds: the writer orders a subject's objects
     * by node, so stable names give the same Turtle on every start.
     */
    private static Graph canonical(Graph source) {
        Map<Node, Node> names = new HashMap<>();
        source.find().forEachRemaining(t -> {
            if (t.getObject().isBlank()) names.computeIfAbsent(t.getObject(), b -> NodeFactory.createBlankNode(digest(source, b)));
        });
        Graph out = GraphFactory.createDefaultGraph();
        source.find().forEachRemaining(t -> out.add(Triple.create(names.getOrDefault(t.getSubject(), t.getSubject()), t.getPredicate(),
                names.getOrDefault(t.getObject(), t.getObject()))));
        return out;
    }

    private static String digest(Graph graph, Node blank) {
        StringBuilder text = new StringBuilder();
        graph.find(blank, Node.ANY, Node.ANY).toList().stream().map(t -> t.getPredicate() + " " + (t.getObject().isBlank() ? "_" : t.getObject()))
                .sorted().forEach(s -> text.append(s).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Pretty Turtle, each multi-line string (the SPARQL) as a long string, so a query reads as it is written in the shapes file. */
    static String turtle(Graph graph) {
        StringWriter out = new StringWriter();
        RDFWriterRegistry.getWriterGraphFactory(RDFFormat.TURTLE_PRETTY).create(RDFFormat.TURTLE_PRETTY)
                .write(out, graph, PREFIXES, null, RIOT.getContext());
        return longStrings(out.toString());
    }

    /**
     * The writer's output with each short string literal that holds an escaped line break written as a long string. A scan,
     * not a regular expression: a regex alternation repeated over a literal recurses once per character, so a long query
     * would overflow the thread's stack.
     */
    private static String longStrings(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) != '"') {
                out.append(text.charAt(i++));
                continue;
            }
            int end = i + 1;
            boolean lineBreak = false;
            while (end < text.length() && text.charAt(end) != '"' && text.charAt(end) != '\n') {
                if (text.charAt(end) == '\\' && end + 1 < text.length()) lineBreak |= text.charAt(++end) == 'n';
                end++;
            }
            if (end >= text.length() || text.charAt(end) != '"') {
                out.append(text, i, end);
                i = end;
                continue;
            }
            String body = text.substring(i + 1, end);
            out.append(lineBreak ? "\"\"\"" + unescape(body) + "\"\"\"" : text.substring(i, end + 1));
            i = end + 1;
        }
        return out.toString();
    }

    private static String unescape(String escaped) {
        return escaped.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static Optional<Node> object(Graph graph, Node subject, Node property) {
        return graph.find(subject, property, Node.ANY).mapWith(Triple::getObject).nextOptional();
    }

    private static String literal(Graph graph, Node subject, Node property) {
        return object(graph, subject, property).filter(Node::isLiteral).map(Node::getLiteralLexicalForm).orElse(null);
    }

    public record Rules(List<Rule> rules) {}

    /** One shape; {@code focus} is interface, part, reference or product; {@code severity} Violation or Warning. */
    public record Rule(String name, String shape, String severity, String focus, String message, String description, String turtle,
                       List<Sparql> sparql, List<Term> terms) {}

    /** A SELECT query of the shape: a constraint with its sh:message, or the SPARQL target that picks its focus nodes. */
    public record Sparql(String role, String message, String query) {}

    /** An atelier: term as a prefixed name; kind class, property, concept or other; its rdfs:comment, or null. */
    public record Term(String term, String kind, String definition) {}
}
