// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.validation.RuleValidator;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

/**
 * A single-interface answer is the profile's answer over every interface, cut to one interface
 * after federation: the same request texts per endpoint, the same Interface JSON, and evidence
 * over that interface's neighbourhood only.
 */
class SingleInterfaceTest {
    private static final Policy POLICY = EvidenceTest.POLICY;
    private static final Policy.Profile OFFICER = POLICY.profile("export-officer");
    private static final String TAIL_PLANE = Atelier.DATA + "es/part/es-tail-plane";

    private final FixtureFederator federator = new FixtureFederator();
    private final QueryService service = new QueryService(federator, new RuleValidator(), new NoOntopSql(),
            new CadUrls(null, null));

    @Test
    void singleInterfaceRunSendsEveryEndpointTheRequestsOfTheFullAnswer() {
        for (Policy.Profile profile : POLICY.profiles()) {
            service.interfaces(Caller.user(profile));
            String fullText = federator.last().sparql();
            Map<String, List<String>> fullRequests = federator.last().queries();
            assertThat(fullRequests.keySet()).containsExactly("ontop-fr", "ontop-de", "ontop-uk", "ontop-es", "ontop-core", "neptune");

            for (String id : List.of("IF-01", "IF-05", "IF-08", "IF-99")) {
                service.interfaceById(id, Caller.user(profile));
                assertThat(federator.last().sparql()).as(profile.name() + " " + id).isEqualTo(fullText);
                assertThat(federator.last().queries()).as(profile.name() + " " + id).isEqualTo(fullRequests);
                service.evidence(id, Caller.user(profile));
                assertThat(federator.last().sparql()).as("evidence " + profile.name() + " " + id).isEqualTo(fullText);
            }
        }
    }

    @Test
    void singleInterfaceAnswerEqualsTheFullAnswersEntryForEveryInterfaceAndProfile() {
        for (Policy.Profile profile : POLICY.profiles()) {
            Json.InterfacesResponse full = service.interfaces(Caller.user(profile));
            assertThat(full.interfaces()).hasSize(8);
            for (Json.Interface expected : full.interfaces()) {
                Json.InterfaceResponse one = service.interfaceById(expected.id(), Caller.user(profile)).orElseThrow();
                assertThat(one._interface()).as(profile.name() + " " + expected.id()).isEqualTo(expected);
                assertThat(one.provenance().calls()).as("the run's provenance, times aside")
                        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("ms").isEqualTo(full.provenance().calls());
                assertThat(one.sparql()).isEqualTo(full.sparql());
            }
        }
        assertThat(service.interfaces(Caller.user(OFFICER)).interfaces()).extracting(Json.Interface::status)
                .containsExactly("pass", "fail", "fail", "fail", "pass", "fail", "fail", "pass");
    }

    @Test
    void evidenceHoldsTheInterfacesNeighbourhoodOnly() {
        Json.Evidence evidence = service.evidence("IF-06", Caller.user(OFFICER)).orElseThrow();

        Model merged = EvidenceTest.parse(evidence.merged().turtle());
        assertThat(merged.listSubjectsWithProperty(RDF.type, merged.createResource(Atelier.ONT + "Interface")).toList())
                .extracting(Resource::getURI).containsExactly(Atelier.interfaceIri("wings", "IF-06"));
        assertThat(merged.listSubjectsWithProperty(RDF.type, merged.createResource(Atelier.ONT + "Part")).toList())
                .extracting(Resource::getURI).containsExactlyInAnyOrder(Atelier.DATA + "fr/part/fr-cross-beam-mid",
                        Atelier.DATA + "uk/part/uk-left-wing-actuator");
        assertThat(merged.listSubjectsWithProperty(merged.createProperty(Atelier.ONT + "onPart")).toList())
                .extracting(Resource::getURI).containsExactlyInAnyOrder(Atelier.DATA + "fr/plug/J61",
                        Atelier.DATA + "uk/plug/HC%2061", Atelier.DATA + "fr/coupling/HY-6", Atelier.DATA + "uk/coupling/HYD%206");
        assertThat(merged.listSubjectsWithProperty(RDF.type, merged.createResource(Atelier.QUDT + "QuantityValue")).toList())
                .as("the value nodes of the four features: 3 positions each, 2 ratings").hasSize(14);

        Model report = EvidenceTest.parse(evidence.shacl().report());
        assertThat(report.listStatements(null, report.createProperty(SHACL.focusNode.getURI()), (RDFNode) null).toList())
                .as("unit x3 on HC 61, hydraulic on HY-6; nothing of IF-02's position defect")
                .extracting(s -> s.getResource().getURI())
                .containsExactlyInAnyOrder(Atelier.DATA + "uk/plug/HC%2061", Atelier.DATA + "uk/plug/HC%2061",
                        Atelier.DATA + "uk/plug/HC%2061", Atelier.DATA + "fr/coupling/HY-6");
    }

    @Test
    void untaggedPartsOfAnAnswerAreThoseItNames() {
        assertThat(service.interfaces(Caller.user(OFFICER)).policy().untagged()).containsExactly(TAIL_PLANE);
        assertThat(service.interfaceById("IF-08", Caller.user(OFFICER)).orElseThrow().policy().untagged()).containsExactly(TAIL_PLANE);
        assertThat(service.evidence("IF-08", Caller.user(OFFICER)).orElseThrow().policy().untagged()).containsExactly(TAIL_PLANE);
        assertThat(service.interfaceById("IF-02", Caller.user(OFFICER)).orElseThrow().policy().untagged()).isEmpty();
    }
}
