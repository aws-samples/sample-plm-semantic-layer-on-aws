// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.mcp.AtelierTools;
import atelier.query.mcp.PlmApi;
import atelier.query.policy.Caller;
import atelier.query.validation.RuleValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

/**
 * An orphan's candidate mates, on fixtures/neighbourhood.ttl with unmated ES plugs added on the tail
 * cone, the part facing IF-07's orphan DE P71 (31 450, 0, 1 500 mm, tolerance 2 mm): C71 at the same
 * position in mm and C72 at 1 mm in inches are candidates; C73 at 5 mm, the mated J72 and an unmated
 * fastener at the position are not. No interface declares the three plugs, so a single-interface
 * answer finds them in the whole graph, not in the neighbourhood.
 */
class CandidateMatesTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Caller OFFICER = Caller.user(EvidenceTest.POLICY.profile("export-officer"));
    static final String ES_PLUG = Atelier.DATA + "es/plug/";
    static final String UNMATED = """
            @prefix atelier:  <https://example.com/atelier/ontology#> .
            @prefix qudt: <http://qudt.org/schema/qudt/> .
            @prefix unit: <http://qudt.org/vocab/unit/> .
            @prefix esp:  <https://example.com/atelier/es/plug/> .
            @prefix esf:  <https://example.com/atelier/es/fastener/> .
            @prefix espt: <https://example.com/atelier/es/part/> .

            esp:C71 a atelier:Plug ; atelier:onPart espt:es-tail-boom ; atelier:connectorType "EN3645" ; atelier:pinCount 19 ;
              atelier:positionX <https://example.com/atelier/es/plug/C71/position/x> ; atelier:positionY <https://example.com/atelier/es/plug/C71/position/y> ;
              atelier:positionZ <https://example.com/atelier/es/plug/C71/position/z> .
            <https://example.com/atelier/es/plug/C71/position/x> a qudt:QuantityValue ; qudt:numericValue 31450.000 ; qudt:unit unit:MilliM .
            <https://example.com/atelier/es/plug/C71/position/y> a qudt:QuantityValue ; qudt:numericValue 0.000 ; qudt:unit unit:MilliM .
            <https://example.com/atelier/es/plug/C71/position/z> a qudt:QuantityValue ; qudt:numericValue 1500.000 ; qudt:unit unit:MilliM .

            esp:C72 a atelier:Plug ; atelier:onPart espt:es-tail-boom ; atelier:connectorType "D38999" ; atelier:pinCount 37 ;
              atelier:positionX <https://example.com/atelier/es/plug/C72/position/x> ; atelier:positionY <https://example.com/atelier/es/plug/C72/position/y> ;
              atelier:positionZ <https://example.com/atelier/es/plug/C72/position/z> .
            <https://example.com/atelier/es/plug/C72/position/x> a qudt:QuantityValue ; qudt:numericValue 1238.228346 ; qudt:unit unit:IN .
            <https://example.com/atelier/es/plug/C72/position/y> a qudt:QuantityValue ; qudt:numericValue 0.0 ; qudt:unit unit:IN .
            <https://example.com/atelier/es/plug/C72/position/z> a qudt:QuantityValue ; qudt:numericValue 59.05511811 ; qudt:unit unit:IN .

            esp:C73 a atelier:Plug ; atelier:onPart espt:es-tail-boom ; atelier:connectorType "EN3645" ; atelier:pinCount 19 ;
              atelier:positionX <https://example.com/atelier/es/plug/C73/position/x> ; atelier:positionY <https://example.com/atelier/es/plug/C73/position/y> ;
              atelier:positionZ <https://example.com/atelier/es/plug/C73/position/z> .
            <https://example.com/atelier/es/plug/C73/position/x> a qudt:QuantityValue ; qudt:numericValue 31455.000 ; qudt:unit unit:MilliM .
            <https://example.com/atelier/es/plug/C73/position/y> a qudt:QuantityValue ; qudt:numericValue 0.000 ; qudt:unit unit:MilliM .
            <https://example.com/atelier/es/plug/C73/position/z> a qudt:QuantityValue ; qudt:numericValue 1500.000 ; qudt:unit unit:MilliM .

            esf:CF-7 a atelier:Fastener ; atelier:onPart espt:es-tail-boom ; atelier:fastenerStandard "EN6115" ; atelier:fastenerCount 24 ;
              atelier:positionX <https://example.com/atelier/es/fastener/CF-7/position/x> ; atelier:positionY <https://example.com/atelier/es/fastener/CF-7/position/y> ;
              atelier:positionZ <https://example.com/atelier/es/fastener/CF-7/position/z> .
            <https://example.com/atelier/es/fastener/CF-7/position/x> a qudt:QuantityValue ; qudt:numericValue 31450.000 ; qudt:unit unit:MilliM .
            <https://example.com/atelier/es/fastener/CF-7/position/y> a qudt:QuantityValue ; qudt:numericValue 0.000 ; qudt:unit unit:MilliM .
            <https://example.com/atelier/es/fastener/CF-7/position/z> a qudt:QuantityValue ; qudt:numericValue 1500.000 ; qudt:unit unit:MilliM .
            """;

    private final QueryService service = new QueryService(new FixtureFederator(fixture()), new RuleValidator(), new NoOntopSql(),
            new CadUrls(null, null));
    private final AtelierTools tools = new AtelierTools(service, EvidenceTest.POLICY, JSON, new PlmApi("", () -> null));

    static Model fixture() {
        Model model = RulesTest.fixture();
        RDFDataMgr.read(model, new StringReader(UNMATED), null, Lang.TURTLE);
        return model;
    }

    @Test
    @SuppressWarnings("unchecked")
    void theOrphanCarriesTheUnmatedPlugsOfTheFacingPartWithinToleranceUnitConverted() {
        Json.Interface if07 = service.interfaces(OFFICER).interfaces().stream().filter(i -> i.id().equals("IF-07")).findFirst().orElseThrow();
        assertThat(if07.violations()).extracting(Json.Violation::rule).containsExactly("orphan");
        Json.Violation orphan = if07.violations().get(0);
        assertThat(orphan.features()).containsExactly("P71");

        List<Json.CandidateMate> mates = (List<Json.CandidateMate>) orphan.detail().get("candidateMates");
        assertThat(mates).extracting(Json.CandidateMate::id).as("C73 is 5 mm off, J72 is mated, CF-7 is a fastener").containsExactly("C71", "C72");
        assertThat(mates.get(0)).isEqualTo(new Json.CandidateMate("C71", "es-tail-boom", "es", ES_PLUG + "C71",
                new Json.Mm(31450.0, 0.0, 1500.0), "EN3645", 19));
        Json.CandidateMate c72 = mates.get(1);
        assertThat(c72.iri()).isEqualTo(ES_PLUG + "C72");
        assertThat(c72.connectorType()).isEqualTo("D38999");
        assertThat(c72.pinCount()).isEqualTo(37);
        assertThat(c72.position().x()).as("inches converted to mm").isCloseTo(31451.0, within(0.001));
        assertThat(c72.position().z()).isCloseTo(1500.0, within(0.001));

        assertThat(service.interfaceById("IF-07", OFFICER).orElseThrow()._interface().violations())
                .as("the single-interface answer reads the candidates from the whole graph").isEqualTo(if07.violations());
        assertThat(service.interfaces(Caller.user(EvidenceTest.POLICY.profile("de-engineer"))).interfaces().stream()
                .filter(i -> i.id().equals("IF-07")).findFirst().orElseThrow().violations()).as("the tail drive is hidden from DE").isEmpty();
    }

    @Test
    void theMcpInterfaceCheckCarriesTheCandidates() {
        McpSchema.CallToolResult result = tools.call("interface_check", Map.of("interface", "IF-07"), OFFICER);
        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        JsonNode candidates = read(((McpSchema.TextContent) result.content().get(0)).text())
                .path("interface").path("violations").get(0).path("detail").path("candidateMates");
        assertThat(candidates).hasSize(2);
        List<String> fields = new ArrayList<>();
        candidates.get(0).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("id", "part", "plm", "iri", "position", "connectorType", "pinCount");
        assertThat(candidates.get(0).path("id").asText()).isEqualTo("C71");
        assertThat(candidates.get(0).path("position").path("x").asDouble()).isEqualTo(31450.0);
    }

    private static JsonNode read(String json) {
        try {
            return JSON.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
