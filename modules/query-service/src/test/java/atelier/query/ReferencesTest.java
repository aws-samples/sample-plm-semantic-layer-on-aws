// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.federation.Federator;
import atelier.query.mapping.ExternalReferences;
import atelier.query.mcp.AtelierTools;
import atelier.query.mcp.KnownTerms;
import atelier.query.mcp.PlmApi;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.validation.RuleValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

/**
 * External references over fixtures/references.ttl (added to the neighbourhood and drivetrain fixtures): the shapes
 * resolve each URN to a part IRI, report a malformed or unresolvable URN as danglingReference and a moved-on or
 * superseded target as staleRevision, on the part that makes the reference; a target hidden from the viewer exists
 * (the core memberships say so) but cannot be compared, so it is neither dangling nor stale.
 */
class ReferencesTest {
    static final Policy POLICY = EvidenceTest.POLICY;
    static final Caller OFFICER = Caller.user(POLICY.profile("export-officer"));
    static final Caller DE = Caller.user(POLICY.profile("de-engineer"));
    static final String PRODUCT = "drivetrain";

    private final ReferenceFederator federator = new ReferenceFederator();
    private final QueryService service = new QueryService(federator, new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));

    static final class ReferenceFederator extends FixtureFederator {
        ReferenceFederator() {
            super(fixture());
        }

        static Model fixture() {
            Model model = FixtureFederator.fixture();
            RDFDataMgr.read(model, "fixtures/bom.ttl");
            RDFDataMgr.read(model, "fixtures/references.ttl");
            return model;
        }
    }

    private static Json.Reference reference(Json.References answer, String plm, String id) {
        return answer.references().stream().filter(r -> r.plm().equals(plm) && r.id().equals(id)).findFirst().orElseThrow();
    }

    private static List<Json.Finding> findings(Json.Interface iface, String part) {
        return iface.parts().stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast)
                .filter(p -> p.id().equals(part)).findFirst().orElseThrow().findings();
    }

    @Test
    void aReferenceToAPartOfAnotherProductResolvesThroughThatPartsMembership() {
        Json.References wings = service.references(OFFICER, "wings");
        assertThat(wings.references()).extracting(Json.Reference::part, Json.Reference::remoteUrn, Json.Reference::status)
                .as("the tail drive and the right root fitting belong to other products than the wings: they exist, so the "
                        + "references are not dangling, and the wings' run does not describe them, so they are not evaluable")
                .containsExactlyInAnyOrder(tuple("fr-cross-beam-mid", "urn:plm:de:part:de-tail-drive", "not-evaluable"),
                        tuple("uk-left-root-fitting", "urn:plm:de:part:de-right-root-fitting", "not-evaluable"));
        List<String> core = federator.last().queries().get("ontop-core");
        assertThat(core.getFirst()).as("the run reads the wings' memberships only")
                .contains("?part atelier:partOf <" + Atelier.productIri("wings") + "> .").doesNotContain("?product");
        assertThat(core.getLast()).as("then the memberships of the parts its references name outside the wings")
                .contains("VALUES ?part").contains(Atelier.DATA + "de/part/de-tail-drive").contains("?part atelier:partOf ?product .");
        assertThat(federator.last().sparql()).contains(core.getLast());
    }

    @Test
    void everyReferenceOfTheProductCarriesTheShapesVerdict() {
        Json.References answer = service.references(OFFICER, PRODUCT);
        assertThat(answer.product()).isEqualTo(PRODUCT);
        assertThat(answer.references()).extracting(Json.Reference::part, Json.Reference::remoteUrn, Json.Reference::status).containsExactly(
                tuple("de-gear", "urn:plm:es:part:es-frame", "staleRevision"),
                tuple("de-gear", "urn:plm:uk:part:UK/4101", "ok"),
                tuple("es-bracket", "urn:plm:de:part:de-missing", "danglingReference"),
                tuple("es-bracket", "urn:plm:es:pieza:es-frame", "danglingReference"),
                tuple("fr-hinge", "urn:plm:de:part:de-missing", "danglingReference"),
                tuple("fr-panel", "urn:plm:de:part:de-housing", "ok"),
                tuple("fr-panel", "urn:plm:de:part:de-housing", "staleRevision"),
                tuple("fr-panel", "urn:plm:fr:part:de-housing", "danglingReference"),
                tuple("uk-motor", "urn:plm:fr:part:fr-hinge", "ok"));
        assertThat(answer.counts()).isEqualTo(Map.of("danglingReference", 4, "ok", 3, "staleRevision", 2));

        Json.Reference ok = reference(answer, "fr", "XR-0001");
        assertThat(ok).isEqualTo(new Json.Reference("XR-0001", "fr", "fr-panel", "urn:plm:de:part:de-housing",
                new Json.PartRef("de-housing", "de", null), 1.0, "02", "02", "RELEASED", "ok", null, "panel screwed to the housing"));
        assertThat(reference(answer, "fr", "XR-0002").message())
                .isEqualTo("fr-panel expects revision 01 of urn:plm:de:part:de-housing, which is at revision 02 (RELEASED)");
        assertThat(reference(answer, "de", "XR-0001").message()).as("the revision matches as text, the lifecycle is superseded")
                .isEqualTo("de-gear expects revision 2 of urn:plm:es:part:es-frame, which is at revision 2 (SUPERSEDED)");
        assertThat(reference(answer, "de", "XR-0002").target()).as("the British native form UK/4101 is the part key UK-4101")
                .isEqualTo(new Json.PartRef("UK-4101", "uk", null));
        assertThat(reference(answer, "fr", "XR-0003").message()).as("the site segment names the PLM that must hold the part")
                .isEqualTo("fr-panel references urn:plm:fr:part:de-housing, which resolves to no part of any PLM");
        assertThat(reference(answer, "es", "XR-0002").message())
                .isEqualTo("es-bracket references urn:plm:es:pieza:es-frame, which is not a urn:plm:<site>:part:<local id> key and resolves to no part");
        assertThat(reference(answer, "es", "XR-0002").target()).isNull();
    }

    @Test
    void aHiddenTargetIsNeitherDanglingNorStaleAndAHiddenPartsReferencesAreNotShown() {
        Json.References answer = service.references(DE, PRODUCT);
        assertThat(answer.references()).extracting(Json.Reference::part).doesNotContain("fr-hinge");
        Json.Reference hidden = reference(answer, "uk", "XR-0001");
        assertThat(hidden.status()).isEqualTo("not-evaluable");
        assertThat(hidden.target()).as("the target is not named").isNull();
        assertThat(hidden.currentRevision()).isNull();
        assertThat(hidden.lifecycleState()).isNull();
        assertThat(hidden.message()).isNull();
        assertThat(answer.counts()).isEqualTo(Map.of("danglingReference", 3, "not-evaluable", 1, "ok", 2, "staleRevision", 2));
        assertThat(federator.last().model().listSubjects().toList()).extracting(r -> r.getURI())
                .as("the hidden part's reference never leaves redaction").doesNotContain(Atelier.DATA + "fr/ref/XR-0004");
    }

    @Test
    void theReferenceRulesAreFindingsOnThePartThatMakesTheReference() {
        List<Json.PartView> parts = service.parts(OFFICER, PRODUCT).parts();
        Json.Part bracket = parts.stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast)
                .filter(p -> p.id().equals("es-bracket")).findFirst().orElseThrow();
        assertThat(bracket.findings()).extracting(Json.Finding::rule).filteredOn(ExternalReferences.RULES::contains)
                .containsExactly("danglingReference", "danglingReference");
        assertThat(bracket.findings()).filteredOn(f -> ExternalReferences.RULES.contains(f.rule())).extracting(Json.Finding::value)
                .as("each finding names the reference it is about").containsExactlyInAnyOrder(
                        new Json.RowRef("es", "ref", "XR-0001"), new Json.RowRef("es", "ref", "XR-0002"));
        Json.Part gear = parts.stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast)
                .filter(p -> p.id().equals("de-gear")).findFirst().orElseThrow();
        assertThat(gear.findings()).extracting(Json.Finding::rule).filteredOn(ExternalReferences.RULES::contains)
                .containsExactly("staleRevision");
    }

    @Test
    void anInterfacesNeighbourhoodCarriesItsPartsReferencesAndTheirTargets() {
        Json.Interface officer = service.interfaceById(new InterfaceRef("IF-05", null), OFFICER).orElseThrow()._interface();
        assertThat(findings(officer, "uk-left-root-fitting")).containsExactly(new Json.Finding("staleRevision",
                "uk-left-root-fitting expects revision 01 of urn:plm:de:part:de-right-root-fitting, which is at revision 02 (RELEASED)",
                new Json.RowRef("uk", "ref", "XR-0002")));
        assertThat(findings(officer, "fr-cross-beam-mid")).containsExactly(new Json.Finding("staleRevision",
                "fr-cross-beam-mid expects revision 01 of urn:plm:de:part:de-tail-drive, which is at revision 01 (SUPERSEDED)",
                new Json.RowRef("fr", "ref", "XR-0005")));

        Json.Interface de = service.interfaceById(new InterfaceRef("IF-05", null), DE).orElseThrow()._interface();
        assertThat(findings(de, "fr-cross-beam-mid")).as("the tail drive is LICENSED: it exists, so not dangling, and cannot be compared").isEmpty();
        assertThat(findings(de, "uk-left-root-fitting")).extracting(Json.Finding::rule).containsExactly("staleRevision");

        FixtureFederator plain = new FixtureFederator();
        Json.Interface without = new QueryService(plain, new RuleValidator(), new NoOntopSql(), new CadUrls(null, null))
                .interfaceById(new InterfaceRef("IF-05", null), OFFICER).orElseThrow()._interface();
        assertThat(officer.status()).as("a finding never changes an interface's status").isEqualTo(without.status());
        assertThat(officer.violations()).isEqualTo(without.violations());
    }

    @Test
    void evidenceAttributesReferencesToTheNativeReferenceTables() {
        Json.Evidence evidence = service.evidence("IF-05", OFFICER).orElseThrow();
        Map<String, Json.Arm> arms = new java.util.LinkedHashMap<>();
        evidence.arms().forEach(a -> arms.put(a.endpoint(), a));
        assertThat(arms.get("ontop-fr").tables()).contains(new Json.Table("fr", "reference_externe", List.of("XR-0005")));
        assertThat(arms.get("ontop-uk").tables()).contains(new Json.Table("uk", "external_ref", List.of("XR-0002")));
        assertThat(Federator.sourceOf(Triple.create(NodeFactory.createURI(Atelier.DATA + "es/ref/XR-0001"),
                NodeFactory.createURI(Atelier.REMOTE_URN), NodeFactory.createLiteralString("urn:plm:de:part:x")))).isEqualTo("ontop-es");
    }

    @Test
    void theToolAnswersAsTheRestEndpoint() throws Exception {
        ObjectMapper json = new ObjectMapper();
        AtelierTools tools = new AtelierTools(service, POLICY, json, new PlmApi("", () -> null), KnownTerms.load());
        McpSchema.CallToolResult result = tools.call("external_references", Map.of("product", PRODUCT), DE);
        JsonNode node = json.readTree(((McpSchema.TextContent) result.content().get(0)).text());
        assertThat(node.has("sparql")).isFalse();
        assertThat(node.path("counts")).isEqualTo(json.valueToTree(service.references(DE, PRODUCT).counts()));
        assertThat(node.path("references").get(0).path("status").asText()).isEqualTo("staleRevision");
    }

    @Test
    void theResolutionIsTheShapesOwnText() throws IOException {
        String shapes = Files.readString(Path.of("../../ontology/shapes.ttl")).replaceAll("\\s+", " ");
        String resolution = ExternalReferences.RESOLUTION.replaceAll("\\s+", " ").strip();
        assertThat(shapes.split(java.util.regex.Pattern.quote(resolution), -1)).as("one copy per reference constraint and one in the lifecycle rule").hasSize(5);
    }
}
