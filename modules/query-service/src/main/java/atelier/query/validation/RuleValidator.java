// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.validation;

import atelier.query.Atelier;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.validation.ReportEntry;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.sparql.path.P_Link;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.stereotype.Component;

/**
 * Validates a federated model against ontology/shapes.ttl. The data graph is the model merged with
 * ontology/units.ttl (the constraints read conversion multipliers), with the rdfs:subClassOf
 * axioms of ontology/atelier.ttl (so a shape targeting atelier:InterfaceFeature reaches plugs, fasteners
 * and couplings) and with its atelier:Lifecycle concept scheme (each PLM's lifecycle word as the
 * altLabel of a canonical state). Results of every severity are returned; {@link RuleViolation#failsInterface}
 * tells the interface rules (sh:Violation, on features) from the data-quality findings
 * (sh:Warning, on parts).
 */
@Component
public class RuleValidator {
    private static final Node RULE = NodeFactory.createURI(Atelier.SHAPES + "rule");

    private final Graph shapesGraph;
    private final Shapes shapes;
    private final Model units;
    private final Model classHierarchy;
    private final Model lifecycle;
    private final Model ontology;

    public RuleValidator() {
        this(read("ontology/units.ttl"));
    }

    /** Validator over a given units graph (tests use this to substitute multipliers). */
    public RuleValidator(Model units) {
        this.shapesGraph = read("ontology/shapes.ttl").getGraph();
        this.shapes = Shapes.parse(shapesGraph);
        this.units = units;
        this.classHierarchy = ModelFactory.createDefaultModel();
        this.lifecycle = ModelFactory.createDefaultModel();
        this.ontology = read("ontology/atelier.ttl");
        ontology.listStatements(null, RDFS.subClassOf, (String) null).forEachRemaining(classHierarchy::add);
        Resource scheme = ontology.createResource(Atelier.LIFECYCLE);
        ontology.listSubjectsWithProperty(ontology.createProperty(Atelier.SKOS + "inScheme"), scheme)
                .forEachRemaining(concept -> lifecycle.add(concept.listProperties()));
    }

    public Model units() {
        return units;
    }

    /** The concepts of the atelier:Lifecycle scheme with their labels, as merged into the data graph. */
    public Model lifecycle() {
        return lifecycle;
    }

    /** The loaded ontology/atelier.ttl; the equivalents answer reads its item classes and vocabularies from it. */
    public Model ontology() {
        return ontology;
    }

    /** The loaded ontology/shapes.ttl. */
    public Graph shapesGraph() {
        return shapesGraph;
    }

    public List<RuleViolation> validate(Model federated) {
        return violations(report(federated));
    }

    /** SHACL report of the federated model merged with the units graph and the class hierarchy. */
    public ValidationReport report(Model federated) {
        Graph data = GraphFactory.createDefaultGraph();
        federated.getGraph().find().forEachRemaining(data::add);
        units.getGraph().find().forEachRemaining(data::add);
        classHierarchy.getGraph().find().forEachRemaining(data::add);
        lifecycle.getGraph().find().forEachRemaining(data::add);
        return ShaclValidator.get().validate(shapes, data);
    }

    public List<RuleViolation> violations(ValidationReport report) {
        return report.getEntries().stream().map(this::toViolation).toList();
    }

    private RuleViolation toViolation(ReportEntry e) {
        String shape = e.source().getURI();
        String rule = shapesGraph.find(e.source(), RULE, Node.ANY)
                .nextOptional().map(t -> t.getObject().getLiteralLexicalForm()).orElse(shape);
        String path = e.resultPath() instanceof P_Link link ? link.getNode().getURI() : null;
        String severity = Atelier.localName(e.severity().level().getURI());
        return new RuleViolation(rule, shape, severity, e.message(), e.focusNode().getURI(), e.value(), path);
    }

    static Model read(String resource) {
        try (InputStream in = RuleValidator.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing classpath resource " + resource);
            Model model = ModelFactory.createDefaultModel();
            RDFParser.source(in).lang(Lang.TURTLE).parse(model);
            return model;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
