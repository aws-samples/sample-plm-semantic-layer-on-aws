// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.federation.Endpoints;
import atelier.query.federation.Federator;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.validation.RuleValidator;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

/**
 * Evidence assembled from the same pipeline as the interface answer, over fixtures/neighbourhood.ttl
 * served by {@link FixtureFederator}, for a viewer who sees every part.
 */
class EvidenceTest {
    static final Policy POLICY = new Policy(Path.of("../../ontology/policy.json"));
    static final Policy.Profile OFFICER = POLICY.profile("export-officer");

    private final FixtureFederator federator = new FixtureFederator();
    private final QueryService service = new QueryService(federator, new RuleValidator(), new NoOntopSql(),
            new CadUrls(null, null));

    /** Requests an endpoint receives in a run for a viewer who sees every part: the link store twice (facts, file index), the core four times (memberships, who tagged, the releases, products), each PLM six times. */
    static int requestsOf(String endpoint) {
        return Endpoints.sourceOf(endpoint) == null ? 2 : endpoint.equals("ontop-core") ? 4 : 6;
    }

    @Test
    void armsCoverEveryEndpointOfTheRunWithTheTextsSentToIt() {
        Json.Evidence evidence = service.evidence("IF-02", Caller.user(OFFICER)).orElseThrow();
        Federator.Result run = federator.last();
        Json.Provenance provenance = service.interfaceById("IF-02", Caller.user(OFFICER)).orElseThrow().provenance();

        assertThat(evidence.interfaceId()).isEqualTo("IF-02");
        assertThat(evidence.arms()).extracting(Json.Arm::endpoint)
                .containsExactlyElementsOf(provenance.calls().stream().map(Federator.Call::endpoint).toList())
                .containsExactlyInAnyOrder("ontop-fr", "ontop-de", "ontop-uk", "ontop-es", "ontop-core", "neptune");
        for (Json.Arm arm : evidence.arms()) {
            assertThat(arm.sparql()).as(arm.endpoint()).isEqualTo(String.join("\n\n", run.queries().get(arm.endpoint())));
            assertThat(arm.requests()).as("every endpoint of the run answered, involved in IF-02 or not").isEqualTo(requestsOf(arm.endpoint()));
            assertThat(arm.sql()).isNull();
            assertThat(arm.kind()).isEqualTo(arm.endpoint().equals("neptune") ? "materialized" : "virtual");
        }
        assertThat(evidence.sparql()).isEqualTo(run.sparql()).contains("CONSTRUCT").contains(OFFICER.filter())
                .as("the run is the profile's federation over every interface").doesNotContain("IF-02");
        assertThat(evidence.policy()).as("IF-02 names two tagged parts").isEqualTo(Caller.user(OFFICER).json(List.of()));
    }

    @Test
    void sqlIsOneReformulationPerRequestJoinedLikeTheRequests() {
        QueryService withSql = new QueryService(federator, new RuleValidator(),
                (plm, sparql) -> "SQL for " + plm + ": " + sparql, new CadUrls(null, null));
        Json.Evidence evidence = withSql.evidence("IF-02", Caller.user(OFFICER)).orElseThrow();
        Federator.Result run = federator.last();

        for (Json.Arm arm : evidence.arms()) {
            String source = Endpoints.sourceOf(arm.endpoint());
            if (source == null) {
                assertThat(arm.sql()).as(arm.endpoint()).isNull();
            } else {
                assertThat(arm.sql()).as(arm.endpoint()).isEqualTo(run.queries().get(arm.endpoint()).stream()
                        .map(q -> "SQL for " + source + ": " + q).collect(Collectors.joining("\n\n")));
            }
        }
        assertThat(evidence.arms()).filteredOn(a -> a.endpoint().equals("ontop-core")).extracting(Json.Arm::sql)
                .singleElement().asString().startsWith("SQL for core: PREFIX atelier:").contains("?part atelier:taggedBy ?taggedBy .")
                .contains(OFFICER.filter());
    }

    @Test
    void armTripleCountsAreTheRunsAndTheTurtleIsTheInterfacesShareOfIt() {
        Json.Evidence evidence = service.evidence("IF-02", Caller.user(OFFICER)).orElseThrow();
        Map<String, Federator.Call> calls = new LinkedHashMap<>();
        service.interfaceById("IF-02", Caller.user(OFFICER)).orElseThrow().provenance().calls().forEach(c -> calls.put(c.endpoint(), c));

        long neighbourhood = 0;
        long run = 0;
        for (Json.Arm arm : evidence.arms()) {
            assertThat((long) arm.tripleCount()).as(arm.endpoint()).isEqualTo(calls.get(arm.endpoint()).triples()).isPositive();
            int share = (int) parse(arm.triples()).size();
            assertThat(share).as(arm.endpoint()).isLessThanOrEqualTo(arm.tripleCount());
            if (share > 0) assertThat(arm.triples()).containsIgnoringCase("prefix atelier:").containsIgnoringCase("prefix sh:");
            neighbourhood += share;
            run += arm.tripleCount();
        }
        assertThat(neighbourhood).isEqualTo(evidence.merged().triples()).isLessThan(run);
        assertThat(parse(evidence.merged().turtle()).size()).isEqualTo(evidence.merged().triples());
    }

    @Test
    void shapesForIf02AreExactlyThePositionShape() {
        Json.Shacl shacl = service.evidence("IF-02", Caller.user(OFFICER)).orElseThrow().shacl();

        assertThat(shacl.shapes()).extracting(Json.Shape::rule, Json.Shape::shape)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("position", Atelier.SHAPES + "PositionShape"));
        Model shape = parse(shacl.shapes().get(0).turtle());
        Resource position = shape.createResource(Atelier.SHAPES + "PositionShape");
        assertThat(position.hasProperty(RDF.type, shape.createResource(SHACL.NodeShape.getURI()))).isTrue();
        assertThat(position.getProperty(shape.createProperty(SHACL.sparql.getURI())).getResource()
                .getProperty(shape.createProperty(SHACL.select.getURI())).getString()).contains("atelier:toleranceMm");
        assertThat(shape.listStatements(null, shape.createProperty(Atelier.SHAPES + "rule"), (String) null).toList())
                .hasSize(1);
    }

    @Test
    void shapesForIf06AreUnitAndHydraulic() {
        Json.Shacl shacl = service.evidence("IF-06", Caller.user(OFFICER)).orElseThrow().shacl();
        assertThat(shacl.shapes()).extracting(Json.Shape::rule)
                .containsExactlyInAnyOrder("unit", "hydraulic");
    }

    @Test
    void reportHasOneValidationResultPerViolationOfTheInterface() {
        for (String id : List.of("IF-02", "IF-04", "IF-06", "IF-05")) {
            Json.Evidence evidence = service.evidence(id, Caller.user(OFFICER)).orElseThrow();
            int violations = service.interfaceById(id, Caller.user(OFFICER)).orElseThrow()._interface().violations().size();
            Model report = parse(evidence.shacl().report());
            assertThat(report.listSubjectsWithProperty(RDF.type,
                    report.createResource(SHACL.ValidationResult.getURI())).toList()).as(id).hasSize(violations);
            assertThat(evidence.shacl().report()).contains("sh:ValidationReport");
        }
    }

    @Test
    void tablesNameTheNativeRowsOfTheInterfaceWithDecodedKeys() {
        Json.Evidence evidence = service.evidence("IF-05", Caller.user(OFFICER)).orElseThrow();
        Map<String, Json.Arm> arms = new LinkedHashMap<>();
        evidence.arms().forEach(a -> arms.put(a.endpoint(), a));

        assertThat(arms.get("ontop-uk").tables()).extracting(Json.Table::table, Json.Table::keys).containsExactly(
                org.assertj.core.groups.Tuple.tuple("component", List.of("uk-left-root-fitting")),
                org.assertj.core.groups.Tuple.tuple("harness_connector", List.of("HC 51")),
                org.assertj.core.groups.Tuple.tuple("fastener", List.of("FS 5")),
                org.assertj.core.groups.Tuple.tuple("hyd_coupling", List.of("HYD 5")));
        assertThat(arms.get("ontop-fr").tables()).extracting(Json.Table::table, Json.Table::keys).containsExactly(
                org.assertj.core.groups.Tuple.tuple("piece", List.of("fr-cross-beam-mid")),
                org.assertj.core.groups.Tuple.tuple("connecteur", List.of("J51")),
                org.assertj.core.groups.Tuple.tuple("fixation", List.of("RF-5")),
                org.assertj.core.groups.Tuple.tuple("raccord_hydraulique", List.of("HY-5")));
        assertThat(arms.get("ontop-core").tables()).as("one part_tag row per tagged part of IF-05, one product_part row per part placed in a product")
                .containsExactly(new Json.Table("core", "part_tag", List.of("fr-cross-beam-mid", "uk-left-root-fitting")),
                        new Json.Table("core", "product_part", List.of("fr-cross-beam-mid", "uk-left-root-fitting")));
        for (String uninvolved : List.of("ontop-de", "ontop-es")) {
            assertThat(arms.get(uninvolved).tables()).as(uninvolved).isEmpty();
            assertThat(parse(arms.get(uninvolved).triples()).size()).as(uninvolved).isZero();
            assertThat(arms.get(uninvolved).requests()).as(uninvolved + " answered the run").isEqualTo(6);
        }
        assertThat(arms.get("neptune").tables()).isEmpty();
        assertThat(arms.get("neptune").kind()).isEqualTo("materialized");
    }

    @Test
    void triplesAreAttributedByPredicateToTheCoreGraphTheFileIndexAndThePlms() {
        Json.Evidence evidence = service.evidence("IF-05", Caller.user(OFFICER)).orElseThrow();
        Map<String, Json.Arm> arms = new LinkedHashMap<>();
        evidence.arms().forEach(a -> arms.put(a.endpoint(), a));

        assertThat(arms.get("ontop-core").triples()).contains("atelier:taggedBy").contains("atelier:releasableTo").contains("atelier:jurisdiction")
                .contains("atelier:taggedAt").as("product membership is a core fact").contains("atelier:partOf")
                .doesNotContain("atelier:label").doesNotContain("atelier:cadFile");
        assertThat(arms.get("ontop-fr").triples()).doesNotContain("atelier:partOf");
        assertThat(arms.get("neptune").triples()).contains("atelier:cadFile").contains("atelier:betweenPart").doesNotContain("atelier:taggedBy");
        assertThat(arms.get("ontop-fr").triples()).contains("atelier:label").contains("atelier:sourceFileRef")
                .doesNotContain("atelier:cadFile").doesNotContain("atelier:releasableTo");

        Map<String, Json.Arm> controlPost = new LinkedHashMap<>();
        service.evidence("IF-01", Caller.user(OFFICER)).orElseThrow().arms().forEach(a -> controlPost.put(a.endpoint(), a));
        assertThat(controlPost.get("neptune").triples()).as("the supplier is a file-index fact").contains("atelier:builtBy");
        assertThat(controlPost.get("ontop-fr").triples()).doesNotContain("atelier:builtBy");
        assertThat(controlPost.get("ontop-core").triples()).doesNotContain("atelier:builtBy");
    }

    @Test
    void unknownInterfaceHasNoEvidence() {
        assertThat(service.evidence("IF-99", Caller.user(OFFICER))).isEmpty();
    }

    static Model parse(String turtle) {
        Model model = ModelFactory.createDefaultModel();
        RDFParser.fromString(turtle, Lang.TURTLE).parse(model);
        return model;
    }
}
