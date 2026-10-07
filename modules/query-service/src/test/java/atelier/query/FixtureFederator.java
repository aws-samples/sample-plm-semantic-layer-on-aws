// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import atelier.query.federation.Endpoints;
import atelier.query.federation.Federator;
import atelier.query.policy.Policy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

/**
 * Federator stand-in that answers every request by running its text against fixtures/neighbourhood.ttl
 * arranged as the sources hold it: the links graph (with the interfaces' rdfs:label), the file-index
 * and labels graphs, and the PLM and core facts (features carry atelier:identifier and atelier:ownedBy, as a PLM mapping
 * mints them). Visibility is therefore decided as live: by the core request's FILTER and the
 * in-process redaction. Keeps every result, so the requests of one run can be compared with the next.
 */
public class FixtureFederator extends Federator {
    static final Endpoints ENDPOINTS = new Endpoints("http://fr/sparql", "http://de/sparql", "http://uk/sparql",
            "http://es/sparql", "http://core/sparql", "http://links/query", Duration.ofSeconds(5), Duration.ofSeconds(30));

    final List<Result> results = new ArrayList<>();
    private final Dataset dataset;

    public FixtureFederator() {
        this(fixture());
    }

    /** Over another model arranged like fixtures/neighbourhood.ttl (tests add parts and interfaces to it). */
    public FixtureFederator(Model fixture) {
        this(fixture, ModelFactory.createDefaultModel());
    }

    /** As {@link #FixtureFederator(Model)}, with {@code options} the link store's options graph. */
    public FixtureFederator(Model fixture, Model options) {
        super(ENDPOINTS);
        this.dataset = dataset(fixture);
        dataset.addNamedModel(Atelier.OPTIONS_GRAPH, options);
    }

    static Model fixture() {
        return RDFDataMgr.loadModel("fixtures/neighbourhood.ttl");
    }

    static Model units() {
        return RDFDataMgr.loadModel("ontology/units.ttl");
    }

    static Dataset dataset(Model fixture) {
        Property label = fixture.createProperty(Atelier.ONT + "label");
        Model links = ModelFactory.createDefaultModel().add(fixture);
        fixture.listSubjectsWithProperty(RDF.type, fixture.createResource(Atelier.ONT + "Interface"))
                .forEachRemaining(i -> links.add(i, RDFS.label, i.getRequiredProperty(label).getObject()));
        Model sources = ModelFactory.createDefaultModel().add(fixture);
        fixture.listSubjectsWithProperty(fixture.createProperty(Atelier.ONT + "onPart")).forEachRemaining(f -> {
            sources.add(f, sources.createProperty(Atelier.ONT + "identifier"), Atelier.nativeId(f.getURI()));
            sources.add(f, sources.createProperty(Atelier.ONT + "ownedBy"), Atelier.plmOf(f.getURI()).toUpperCase());
        });
        Dataset dataset = DatasetFactory.create(sources);
        dataset.addNamedModel(Atelier.LINKS_GRAPH, links);
        dataset.addNamedModel(Atelier.FILE_INDEX_GRAPH, fixture);
        dataset.addNamedModel(Atelier.LABELS_GRAPH, fixture);
        return dataset;
    }

    /** URL of the endpoint reported under {@code name} ("ontop-fr", "ontop-core", "neptune"). */
    static String urlOf(String name) {
        String source = Endpoints.sourceOf(name);
        return source == null ? ENDPOINTS.linkStore() : ENDPOINTS.ontop(source);
    }

    Result last() {
        return results.getLast();
    }

    @Override
    public Result interfaces(Policy.Profile profile, String product) {
        return recorded(super.interfaces(profile, product));
    }

    @Override
    public Result parts(Policy.Profile profile, String product) {
        return recorded(super.parts(profile, product));
    }

    @Override
    public Result purchased(Policy.Profile profile, String product) {
        return recorded(super.purchased(profile, product));
    }

    @Override
    public Result variant(Policy.Profile profile, String product, String variantIri) {
        return recorded(super.variant(profile, product, variantIri));
    }

    @Override
    public Result parts(Policy.Profile profile, String product, String variantIri) {
        return recorded(super.parts(profile, product, variantIri));
    }

    @Override
    public Result placements(Policy.Profile profile, String product, String variantIri) {
        return recorded(super.placements(profile, product, variantIri));
    }

    @Override
    public Result placements(Policy.Profile profile, String product) {
        return recorded(super.placements(profile, product));
    }

    @Override
    public Result stations(Policy.Profile profile, String product) {
        return recorded(super.stations(profile, product));
    }

    @Override
    public SubtreeRun placementsUnder(Policy.Profile profile, String product, String root) {
        return recorded(super.placementsUnder(profile, product, root));
    }

    @Override
    public SubtreeRun interfacesUnder(Policy.Profile profile, String product, String root) {
        return recorded(super.interfacesUnder(profile, product, root));
    }

    @Override
    public SubtreeRun partsUnder(Policy.Profile profile, String product, String root) {
        return recorded(super.partsUnder(profile, product, root));
    }

    @Override
    public SubtreeRun bomUnder(Policy.Profile profile, String product, String root) {
        return recorded(super.bomUnder(profile, product, root));
    }

    private SubtreeRun recorded(SubtreeRun run) {
        recorded(run.result());
        return run;
    }

    private Result recorded(Result result) {
        results.add(result);
        return result;
    }

    @Override
    protected Model answer(String url, String query) {
        try (QueryExecution exec = QueryExecutionFactory.create(query, dataset)) {
            return exec.execConstruct();
        }
    }
}
