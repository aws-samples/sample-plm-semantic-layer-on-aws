// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.mcp.AtelierTools;
import atelier.query.mcp.PlmApi;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.policy.Redaction;
import atelier.query.validation.RuleValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

/**
 * Hidden parts are never named. Over the released IF-44 (control post / instrumentation pod, both
 * French, releasable to FR) and IF-90 (hydraulic motor, EXPORT-LICENCE, releasable to licensed
 * recipients / hydraulic manifold, releasable to EU): for a profile that may not see a part, the
 * interface label names the visible parts by their own PLM names and the hidden ones as
 * {@value Redaction#HIDDEN_PART}, in the list, the detail, where-used, impact, the MCP compact list
 * and the evidence graph.
 */
class HiddenLabelTest {
    static final Policy POLICY = EvidenceTest.POLICY;
    static final ObjectMapper JSON = new ObjectMapper();
    static final String HIDDEN = Redaction.HIDDEN_PART;
    static final String FIXTURE = """
            @prefix atelier:  <https://example.com/atelier/ontology#> .
            @prefix qudt: <http://qudt.org/schema/qudt/> .
            @prefix unit: <http://qudt.org/vocab/unit/> .
            @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .
            @prefix ifo:  <https://example.com/atelier/interface/ornithopter/> .
            @prefix ifa:  <https://example.com/atelier/interface/aerial-screw/> .
            @prefix frp:  <https://example.com/atelier/fr/plug/> .
            @prefix dep:  <https://example.com/atelier/de/plug/> .
            @prefix frpt: <https://example.com/atelier/fr/part/> .
            @prefix dept: <https://example.com/atelier/de/part/> .

            frpt:FR-ORN-PCMD-001 a atelier:Part ; atelier:label "Poteau de commande" ; atelier:sourceFileRef "CAO/FR-ORN-PCMD-001.CATPart" ;
              atelier:cadFile "cad/ornithopter/fr-control-post.stp" ;
              atelier:jurisdiction "NATIONAL-FR" ; atelier:releasableTo "FR" ; atelier:taggedBy "FR" ; atelier:taggedAt "2026-09-01T08:00:00Z"^^xsd:dateTime .
            frpt:FR-ORN-NACI-001 a atelier:Part ; atelier:label "Nacelle d'instrumentation (acquisition de données)" ; atelier:sourceFileRef "CAO/FR-ORN-NACI-001.CATPart" ;
              atelier:cadFile "cad/ornithopter/fr-instrumentation-pod.stp" ;
              atelier:jurisdiction "NATIONAL-FR" ; atelier:releasableTo "FR" ; atelier:taggedBy "FR" ; atelier:taggedAt "2026-09-01T08:00:00Z"^^xsd:dateTime .
            dept:VENT-70110 a atelier:Part ; atelier:label "Hydraulikblock mit Magnetventilen" ; atelier:sourceFileRef "CAD/VENT-70110.prt" ;
              atelier:cadFile "cad/aerial-screw/de-manifold.stp" ;
              atelier:jurisdiction "EU-DUAL-USE" ; atelier:releasableTo "EU" ; atelier:taggedBy "DE" ; atelier:taggedAt "2026-09-01T08:00:00Z"^^xsd:dateTime .
            dept:HMOT-70090 a atelier:Part ; atelier:label "Hydraulischer Drehantrieb" ; atelier:sourceFileRef "CAD/HMOT-70090.prt" ;
              atelier:cadFile "cad/aerial-screw/de-hydraulic-motor.stp" ;
              atelier:jurisdiction "EXPORT-LICENCE" ; atelier:releasableTo "LICENSED" ; atelier:taggedBy "DE" ; atelier:taggedAt "2026-09-01T08:00:00Z"^^xsd:dateTime .

            ifo:IF-44 a atelier:Interface ; atelier:ofProduct <https://example.com/atelier/product/ornithopter> ; atelier:label "Control post / instrumentation pod (cradle, power and bus plug)" ; atelier:toleranceMm "2.0"^^xsd:decimal ;
              atelier:betweenPart frpt:FR-ORN-PCMD-001 , frpt:FR-ORN-NACI-001 ; atelier:declaresFeature frp:FR-ORN-PCMD-001-J02 , frp:FR-ORN-NACI-001-J05 .
            ifa:IF-90 a atelier:Interface ; atelier:ofProduct <https://example.com/atelier/product/aerial-screw> ; atelier:label "Hydraulic motor / manifold (A and B pressure lines)" ; atelier:toleranceMm "2.0"^^xsd:decimal ;
              atelier:betweenPart dept:HMOT-70090 , dept:VENT-70110 ; atelier:declaresFeature dep:HMOT-70090-X01 , dep:VENT-70110-X09 .

            frp:FR-ORN-PCMD-001-J02 atelier:matesWith frp:FR-ORN-NACI-001-J05 . frp:FR-ORN-NACI-001-J05 atelier:matesWith frp:FR-ORN-PCMD-001-J02 .
            dep:HMOT-70090-X01 atelier:matesWith dep:VENT-70110-X09 . dep:VENT-70110-X09 atelier:matesWith dep:HMOT-70090-X01 .

            frp:FR-ORN-PCMD-001-J02 a atelier:Plug ; atelier:onPart frpt:FR-ORN-PCMD-001 ; atelier:connectorType "M12-A" ; atelier:pinCount 8 ;
              atelier:positionX <https://example.com/atelier/fr/plug/FR-ORN-PCMD-001-J02/position/x> ;
              atelier:positionY <https://example.com/atelier/fr/plug/FR-ORN-PCMD-001-J02/position/y> ;
              atelier:positionZ <https://example.com/atelier/fr/plug/FR-ORN-PCMD-001-J02/position/z> .
            <https://example.com/atelier/fr/plug/FR-ORN-PCMD-001-J02/position/x> a qudt:QuantityValue ; qudt:numericValue 500.000 ; qudt:unit unit:MilliM .
            <https://example.com/atelier/fr/plug/FR-ORN-PCMD-001-J02/position/y> a qudt:QuantityValue ; qudt:numericValue -120.000 ; qudt:unit unit:MilliM .
            <https://example.com/atelier/fr/plug/FR-ORN-PCMD-001-J02/position/z> a qudt:QuantityValue ; qudt:numericValue 920.000 ; qudt:unit unit:MilliM .
            frp:FR-ORN-NACI-001-J05 a atelier:Plug ; atelier:onPart frpt:FR-ORN-NACI-001 ; atelier:connectorType "M12-A" ; atelier:pinCount 8 ;
              atelier:positionX <https://example.com/atelier/fr/plug/FR-ORN-NACI-001-J05/position/x> ;
              atelier:positionY <https://example.com/atelier/fr/plug/FR-ORN-NACI-001-J05/position/y> ;
              atelier:positionZ <https://example.com/atelier/fr/plug/FR-ORN-NACI-001-J05/position/z> .
            <https://example.com/atelier/fr/plug/FR-ORN-NACI-001-J05/position/x> a qudt:QuantityValue ; qudt:numericValue 500.000 ; qudt:unit unit:MilliM .
            <https://example.com/atelier/fr/plug/FR-ORN-NACI-001-J05/position/y> a qudt:QuantityValue ; qudt:numericValue -120.000 ; qudt:unit unit:MilliM .
            <https://example.com/atelier/fr/plug/FR-ORN-NACI-001-J05/position/z> a qudt:QuantityValue ; qudt:numericValue 920.000 ; qudt:unit unit:MilliM .
            dep:HMOT-70090-X01 a atelier:Plug ; atelier:onPart dept:HMOT-70090 ; atelier:connectorType "M12-A" ; atelier:pinCount 4 ;
              atelier:positionX <https://example.com/atelier/de/plug/HMOT-70090-X01/position/x> ;
              atelier:positionY <https://example.com/atelier/de/plug/HMOT-70090-X01/position/y> ;
              atelier:positionZ <https://example.com/atelier/de/plug/HMOT-70090-X01/position/z> .
            <https://example.com/atelier/de/plug/HMOT-70090-X01/position/x> a qudt:QuantityValue ; qudt:numericValue 1800.000 ; qudt:unit unit:MilliM .
            <https://example.com/atelier/de/plug/HMOT-70090-X01/position/y> a qudt:QuantityValue ; qudt:numericValue 0.000 ; qudt:unit unit:MilliM .
            <https://example.com/atelier/de/plug/HMOT-70090-X01/position/z> a qudt:QuantityValue ; qudt:numericValue 450.000 ; qudt:unit unit:MilliM .
            dep:VENT-70110-X09 a atelier:Plug ; atelier:onPart dept:VENT-70110 ; atelier:connectorType "M12-A" ; atelier:pinCount 4 ;
              atelier:positionX <https://example.com/atelier/de/plug/VENT-70110-X09/position/x> ;
              atelier:positionY <https://example.com/atelier/de/plug/VENT-70110-X09/position/y> ;
              atelier:positionZ <https://example.com/atelier/de/plug/VENT-70110-X09/position/z> .
            <https://example.com/atelier/de/plug/VENT-70110-X09/position/x> a qudt:QuantityValue ; qudt:numericValue 1800.000 ; qudt:unit unit:MilliM .
            <https://example.com/atelier/de/plug/VENT-70110-X09/position/y> a qudt:QuantityValue ; qudt:numericValue 0.000 ; qudt:unit unit:MilliM .
            <https://example.com/atelier/de/plug/VENT-70110-X09/position/z> a qudt:QuantityValue ; qudt:numericValue 450.000 ; qudt:unit unit:MilliM .
            """;

    private final QueryService service = new QueryService(new FixtureFederator(fixture()), new RuleValidator(), new NoOntopSql(),
            new CadUrls(null, null));
    private final AtelierTools tools = new AtelierTools(service, POLICY, JSON, new PlmApi("", () -> null));
    private final Caller de = Caller.user(POLICY.profile("de-engineer"));

    static Model fixture() {
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, new StringReader(FIXTURE), null, Lang.TURTLE);
        return model;
    }

    @Test
    void deEngineerSeesNeitherTheControlPostNorThePodInTheLabelOfIf44() {
        Json.Interface if44 = interfaces(de).get("IF-44");
        assertThat(if44.status()).isEqualTo("not-evaluable");
        assertThat(if44.label()).isEqualTo(HIDDEN + " / " + HIDDEN);
        assertThat(if44.label().toLowerCase()).doesNotContain("control post").doesNotContain("pcmd").doesNotContain("instrumentation")
                .doesNotContain("nacelle").doesNotContain("poteau");
    }

    @Test
    void deEngineerSeesTheManifoldButNotTheMotorInTheLabelOfIf90Everywhere() {
        String expected = HIDDEN + " / Hydraulikblock mit Magnetventilen";
        Json.Interface listed = interfaces(de).get("IF-90");
        assertThat(listed.status()).isEqualTo("not-evaluable");
        assertThat(listed.label()).isEqualTo(expected);
        assertThat(service.interfaceById("IF-90", de).orElseThrow()._interface().label()).isEqualTo(expected);
        assertThat(service.whereUsed("VENT-70110", de).orElseThrow().interfaces()).extracting(Json.Use::label).containsExactly(expected);
        assertThat(service.impact("VENT-70110", null, de).orElseThrow().interfaces()).extracting(Json.Impacted::label).containsExactly(expected);
        assertThat(mcp("list_interfaces", Map.of(), de).path("interfaces"))
                .extracting(i -> i.path("label").asText()).containsExactly(HIDDEN + " / " + HIDDEN, expected);
        assertThat(mcp("interface_check", Map.of("interface", "IF-90"), de).path("interface").path("label").asText()).isEqualTo(expected);

        assertThat(JSON.valueToTree(listed).toString().toLowerCase()).as("the answer spells neither the motor's names nor its id")
                .doesNotContain("hydraulic motor").doesNotContain("drehantrieb").doesNotContain("hmot-70090");
        assertThat(service.evidence("IF-90", de).orElseThrow().merged().turtle()).as("the validated graph carries the composed label")
                .contains(expected).doesNotContain("Hydraulic motor").doesNotContain("Drehantrieb");
    }

    @Test
    void aProfileThatSeesBothPartsKeepsTheCuratedLabel() {
        Map<String, Json.Interface> officer = interfaces(Caller.user(POLICY.profile("export-officer")));
        assertThat(officer.get("IF-44").label()).isEqualTo("Control post / instrumentation pod (cradle, power and bus plug)");
        assertThat(officer.get("IF-90").label()).isEqualTo("Hydraulic motor / manifold (A and B pressure lines)");

        Map<String, Json.Interface> fr = interfaces(Caller.user(POLICY.profile("fr-engineer")));
        assertThat(fr.get("IF-44").label()).as("both French parts are releasable to FR")
                .isEqualTo("Control post / instrumentation pod (cradle, power and bus plug)");
        assertThat(fr.get("IF-90").label()).as("the licensed motor is hidden from the FR engineer too")
                .isEqualTo(HIDDEN + " / Hydraulikblock mit Magnetventilen");
    }

    private Map<String, Json.Interface> interfaces(Caller caller) {
        return service.interfaces(caller).interfaces().stream().collect(Collectors.toMap(Json.Interface::id, i -> i));
    }

    private JsonNode mcp(String tool, Map<String, Object> args, Caller caller) {
        McpSchema.CallToolResult result = tools.call(tool, args, caller);
        assertThat(result.isError()).as(tool).isNotEqualTo(Boolean.TRUE);
        try {
            return JSON.readTree(((McpSchema.TextContent) result.content().get(0)).text());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
