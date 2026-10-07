// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.validation.RuleValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

/**
 * Data-quality findings: the sh:Warning shape of ontology/shapes.ttl over the parts of the merged
 * graph, on fixtures/neighbourhood.ttl plus fixtures/head-hoop.ttl (an FR head hoop described by its
 * PLM and tagged in the core but with no atelier:cadFile in the file index, the state the released file
 * index ships FR-ORN-CERC-001 in; IF-45 mates it with the control post and passes).
 * A finding is reported on the part and counted per rule on the interfaces answer; it never
 * changes an interface's status.
 */
class CadFindingsTest {
    static final Json.Finding CAD_MISSING = new Json.Finding("cadMissing", "no CAD file published in the file index for this part", null);
    static final String HEAD_HOOP = Atelier.DATA + "fr/part/fr-head-hoop";
    static final String RIGHT_INNER_SPAR = Atelier.DATA + "de/part/de-right-inner-spar";
    static final String RIGHT_ROOT_FITTING = Atelier.DATA + "de/part/de-right-root-fitting";
    private static final Policy POLICY = EvidenceTest.POLICY;
    private static final Caller OFFICER = Caller.user(POLICY.profile("export-officer"));
    private static final Caller FR = Caller.user(POLICY.profile("fr-engineer"));
    private static final Caller DE = Caller.user(POLICY.profile("de-engineer"));

    /** neighbourhood.ttl with the head hoop and IF-45 added: the file index has no entry for the head hoop. */
    static Model released() {
        return FixtureFederator.fixture().add(RDFDataMgr.loadModel("fixtures/head-hoop.ttl"));
    }

    static QueryService service(Model fixture) {
        return new QueryService(new FixtureFederator(fixture), new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));
    }

    static Json.Part part(List<Json.PartView> parts, String id) {
        return parts.stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast)
                .filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    static Json.Interface iface(Json.InterfacesResponse answer, String id) {
        return answer.interfaces().stream().filter(i -> i.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void releasedFileIndexHeadHoopIsAFindingAndItsInterfacePasses() {
        QueryService service = service(released());
        for (Caller caller : List.of(OFFICER, FR)) {
            Json.InterfacesResponse answer = service.interfaces(caller);
            assertThat(answer.findings()).as(caller.profile().name()).isEqualTo(Map.of("cadMissing", 1));
            Json.Interface if45 = iface(answer, "IF-45");
            assertThat(if45.status()).isEqualTo("pass");
            assertThat(if45.violations()).isEmpty();
            assertThat(part(if45.parts(), "fr-head-hoop").findings()).containsExactly(CAD_MISSING);
            assertThat(part(if45.parts(), "fr-control-post").findings()).isEmpty();

            Json.PartsResponse parts = service.parts(caller);
            Json.Part headHoop = part(parts.parts(), "fr-head-hoop");
            assertThat(headHoop.cadFile()).isNull();
            assertThat(headHoop.cadUrl()).isNull();
            assertThat(headHoop.findings()).containsExactly(CAD_MISSING);
            assertThat(parts.parts()).filteredOn(p -> p instanceof Json.Part q && !q.id().equals("fr-head-hoop"))
                    .allMatch(p -> ((Json.Part) p).findings().isEmpty());
        }
        Json.ExportStatus status = service.exportStatus("fr-head-hoop", FR).orElseThrow();
        assertThat(((Json.Part) status.part()).findings()).containsExactly(CAD_MISSING);
        assertThat(status.cadAvailable()).isFalse();
    }

    @Test
    void hiddenHeadHoopIsNotReportedToTheProfile() {
        QueryService service = service(released());
        Json.InterfacesResponse answer = service.interfaces(DE);
        assertThat(answer.findings()).isEmpty();
        assertThat(iface(answer, "IF-45").status()).isEqualTo("not-evaluable");
        assertThat(service.parts(DE).parts()).filteredOn(Json.Part.class::isInstance)
                .allMatch(p -> ((Json.Part) p).findings().isEmpty());
    }

    @Test
    void publishedFileIndexEntryClearsTheFinding() {
        Model published = released();
        published.createResource(HEAD_HOOP).addProperty(published.createProperty(Atelier.CAD_FILE), "cad/ornithopter/fr-head-hoop.stp");
        QueryService service = service(published);

        Json.InterfacesResponse answer = service.interfaces(OFFICER);
        assertThat(answer.findings()).isEmpty();
        assertThat(iface(answer, "IF-45").status()).isEqualTo("pass");
        Json.Part headHoop = part(service.parts(OFFICER).parts(), "fr-head-hoop");
        assertThat(headHoop.cadFile()).isEqualTo("cad/ornithopter/fr-head-hoop.stp");
        assertThat(headHoop.findings()).isEmpty();
    }

    @Test
    void twoCadFilesAreAFindingToo() {
        Model twice = FixtureFederator.fixture();
        twice.createResource(RIGHT_INNER_SPAR).addProperty(twice.createProperty(Atelier.CAD_FILE), "cad/ornithopter/de-right-inner-spar-v2.stp");
        QueryService service = service(twice);

        Json.InterfacesResponse answer = service.interfaces(OFFICER);
        assertThat(answer.findings()).isEqualTo(Map.of("cadMissing", 1));
        assertThat(part(service.parts(OFFICER).parts(), "de-right-inner-spar").findings()).containsExactly(CAD_MISSING);
        assertThat(iface(answer, "IF-03").violations()).as("the right inner spar's interfaces keep their rules")
                .extracting(Json.Violation::rule).containsExactly("connector");
        assertThat(iface(answer, "IF-04").violations()).extracting(Json.Violation::rule).containsExactly("fastener");
    }

    @Test
    void findingsNeverChangeAnInterfacesStatus() {
        Model missing = FixtureFederator.fixture();
        missing.removeAll(missing.createResource(RIGHT_ROOT_FITTING), missing.createProperty(Atelier.CAD_FILE), null);
        Json.InterfacesResponse with = service(missing).interfaces(OFFICER);
        Json.InterfacesResponse without = service(FixtureFederator.fixture()).interfaces(OFFICER);

        assertThat(with.findings()).isEqualTo(Map.of("cadMissing", 1));
        assertThat(without.findings()).isEmpty();
        assertThat(with.interfaces()).extracting(Json.Interface::id, Json.Interface::status)
                .containsExactlyElementsOf(without.interfaces().stream().map(i -> tuple(i.id(), i.status())).toList());
        assertThat(with.interfaces()).extracting(Json.Interface::violations)
                .containsExactlyElementsOf(without.interfaces().stream().map(Json.Interface::violations).toList());
        assertThat(iface(with, "IF-01").status()).as("the right root fitting sits on IF-01 (pass) and IF-02 (fail)").isEqualTo("pass");
        assertThat(iface(with, "IF-02").status()).isEqualTo("fail");
    }

    @Test
    void findingsSerialiseOnThePartAndAsCountsOnTheInterfacesAnswer() throws Exception {
        QueryService service = service(released());
        ObjectMapper json = new ObjectMapper();

        String parts = json.writeValueAsString(service.parts(FR));
        assertThat(parts).contains("\"id\":\"fr-head-hoop\"")
                .contains("\"findings\":[{\"rule\":\"cadMissing\",\"message\":\"no CAD file published in the file index for this part\"}]");
        assertThat(parts.split("\"findings\"", -1)).as("findings only on the head hoop").hasSize(2);
        assertThat(json.writeValueAsString(service.interfaces(FR))).endsWith("\"findings\":{\"cadMissing\":1}}");
        assertThat(json.writeValueAsString(service.interfaces(DE))).endsWith("\"findings\":{}}");
    }

    @Test
    void evidenceReportCarriesTheWarningAndItsShape() {
        Json.Evidence evidence = service(released()).evidence("IF-45", OFFICER).orElseThrow();

        Model report = EvidenceTest.parse(evidence.shacl().report());
        List<Resource> results = report.listSubjectsWithProperty(RDF.type, report.createResource(SHACL.ValidationResult.getURI())).toList();
        assertThat(results).hasSize(1);
        Resource result = results.get(0);
        assertThat(result.getPropertyResourceValue(report.createProperty(SHACL.resultSeverity.getURI())).getURI())
                .isEqualTo(SHACL.Warning.getURI());
        assertThat(result.getPropertyResourceValue(report.createProperty(SHACL.focusNode.getURI())).getURI()).isEqualTo(HEAD_HOOP);
        assertThat(evidence.shacl().shapes()).extracting(Json.Shape::rule, Json.Shape::shape)
                .containsExactly(tuple("cadMissing", Atelier.SHAPES + "PartCadShape"));
        assertThat(evidence.shacl().shapes().get(0).turtle()).contains("sh:Warning").contains("atelier:cadFile");
    }
}
