// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import atelier.query.Atelier;
import atelier.query.FixtureFederator;
import atelier.query.QueryService;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.federation.Federator;
import atelier.query.mcp.AtelierTools;
import atelier.query.mcp.KnownTerms;
import atelier.query.mcp.PlmApi;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.validation.RuleValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.assertj.core.groups.Tuple;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

/**
 * The preview over the neighbourhood, drivetrain, purchasing and lifecycle fixtures with the sites' R2RML of
 * modules/ontop/mappings and the catalogue entries of fixtures/catalogues.json: each idiom's cell rewrites the triple its
 * mapping produces, each fixture defect's correction previews as fixed with nothing newly failing, a wrong value previews
 * as still failing, a cell outside the site's vocabulary or on a hidden record is refused, and a preview reads only.
 */
class PreviewTest {
    static final Policy POLICY = new Policy(Path.of("../../ontology/policy.json"));
    static final Caller OFFICER = Caller.user(POLICY.profile("export-officer"));
    static final Caller DE = Caller.user(POLICY.profile("de-engineer"));
    static final ObjectMapper JSON = new ObjectMapper();
    static final JsonNode CATALOGUES = read();
    static final String NUMERIC = Atelier.QUDT + "numericValue";

    /** The fixture federator, keeping every request text it answers. */
    static final class Recording extends FixtureFederator {
        final List<String> requests = Collections.synchronizedList(new ArrayList<>());

        Recording() {
            super(fixture());
        }

        @Override
        protected Model answer(String url, String query) {
            requests.add(query);
            return super.answer(url, query);
        }
    }

    private final Recording federator = new Recording();
    private final QueryService service = new QueryService(federator, new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));
    private final List<String> catalogueReads = Collections.synchronizedList(new ArrayList<>());
    private final PreviewService preview = new PreviewService(service, new CellMappings(Path.of("../ontop/mappings")), (plm, caller) -> {
        catalogueReads.add(plm);
        return CATALOGUES.path(plm);
    });

    static Model fixture() {
        Model model = RDFDataMgr.loadModel("fixtures/neighbourhood.ttl");
        for (String f : List.of("bom", "references", "lifecycle", "purchasing")) RDFDataMgr.read(model, "fixtures/" + f + ".ttl");
        return model;
    }

    private static JsonNode read() {
        try {
            return JSON.readTree(new File("src/test/resources/fixtures/catalogues.json"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static PreviewJson.Cell cell(String plm, String table, String key, String column, Object value) {
        return new PreviewJson.Cell(plm, table, key, column, JSON.valueToTree(value));
    }

    private PreviewJson.Answer run(Caller caller, String product, PreviewJson.Cell... cells) {
        return preview.preview(caller, new PreviewJson.Request(product, null, List.of(cells)));
    }

    private static PreviewJson.InterfaceDiff iface(PreviewJson.Answer answer, String id) {
        return answer.interfaces().stream().filter(i -> i.id().equals(id)).findFirst().orElseThrow(() -> new AssertionError("no " + id + " in " + answer.interfaces()));
    }

    private static PreviewJson.PartDiff part(PreviewJson.Answer answer, String id) {
        return answer.parts().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow(() -> new AssertionError("no " + id + " in " + answer.parts()));
    }

    private static List<String> rules(List<PreviewJson.Result> results) {
        return results.stream().map(PreviewJson.Result::rule).toList();
    }

    @Test
    void aDeNumericPositionRewritesTheQuantityValueOfItsAxis() {
        PreviewJson.Answer answer = run(OFFICER, "ornithopter", cell("de", "stecker", "J21", "pos_x_mm", 13004.3));
        assertThat(answer.cells().get(0).triples()).containsExactly(
                new PreviewJson.Triple(Atelier.DATA + "de/plug/J21/position/x", NUMERIC, "13000.000", "13004.3"));
    }

    @Test
    void aUkUnitColumnRewritesTheUnitOfEveryQuantityOfTheRowThatReadsIt() {
        PreviewJson.Answer answer = run(OFFICER, "wings", cell("uk", "harness_connector", "HC 61", "pos_uom", "IN"));
        assertThat(answer.cells().get(0).triples()).extracting(PreviewJson.Triple::subject, PreviewJson.Triple::predicate,
                PreviewJson.Triple::before, PreviewJson.Triple::after).containsExactlyInAnyOrder(
                Stream.of("x", "y", "z").map(axis -> tuple(Atelier.DATA + "uk/plug/HC%2061/position/" + axis,
                        Atelier.QUDT + "unit", null, Atelier.UNIT + "IN")).toArray(Tuple[]::new));
    }

    @Test
    void aFrLifecycleWordRewritesTheLifecycleLabel() {
        PreviewJson.Answer answer = run(OFFICER, "drivetrain", cell("fr", "piece", "fr-panel", "etat", "En cours"));
        assertThat(answer.cells().get(0).triples()).containsExactly(
                new PreviewJson.Triple(Atelier.DATA + "fr/part/fr-panel", Atelier.ONT + "lifecycleLabel", "Publié", "En cours"));
        assertThat(rules(part(answer, "fr-panel").fixed())).as("a part no longer released depends on nothing in conflict").contains("lifecycleConflict");
    }

    @Test
    void anEsIntegerRevisionRewritesTheRevisionAsAnInteger() {
        PreviewJson.Answer answer = run(OFFICER, "drivetrain", cell("es", "pieza", "es-frame", "revision", 3));
        assertThat(answer.cells().get(0).triples()).containsExactly(
                new PreviewJson.Triple(Atelier.DATA + "es/part/es-frame", Atelier.ONT + "revision", "2", "3"));
    }

    @Test
    void anExternalReferenceTakesItsUrnAndExpectedRevision() {
        PreviewJson.Answer answer = run(OFFICER, "drivetrain",
                cell("fr", "reference_externe", "XR-0003", "urn", "urn:plm:de:part:de-housing"),
                cell("fr", "reference_externe", "XR-0002", "indice_attendu", "02"));
        assertThat(answer.cells()).extracting(c -> c.triples().get(0)).containsExactly(
                new PreviewJson.Triple(Atelier.DATA + "fr/ref/XR-0003", Atelier.REMOTE_URN, "urn:plm:fr:part:de-housing", "urn:plm:de:part:de-housing"),
                new PreviewJson.Triple(Atelier.DATA + "fr/ref/XR-0002", Atelier.EXPECTED_REVISION, "01", "02"));
        PreviewJson.PartDiff panel = part(answer, "fr-panel");
        assertThat(panel.fixed()).extracting(PreviewJson.Result::rule, r -> r.value().id())
                .contains(tuple("danglingReference", "XR-0003"), tuple("staleRevision", "XR-0002"));
        assertThat(answer.tally().newlyFailing()).isZero();
    }

    @Test
    void aSupplierOffersLeadTimeIsItsLeadTimeDays() {
        PreviewJson.Answer answer = run(OFFICER, "fastening", cell("de", "lieferantenteil", "3", "lieferzeit_tage", 10));
        assertThat(answer.cells().get(0).triples()).containsExactly(
                new PreviewJson.Triple(Atelier.DATA + "de/offer/3", Atelier.ONT + "leadTimeDays", "35", "10"));
        assertThat(rules(part(answer, "de-bearing").fixed())).containsExactly("conflictingLeadTime");
    }

    @Test
    void eachFixtureDefectsCorrectionPreviewsAsFixedWithNothingNewlyFailing() {
        record Case(String product, String subject, String rule, PreviewJson.Cell cell) {}
        List<Case> cases = List.of(
                new Case("ornithopter", "IF-02", "position", cell("de", "stecker", "J21", "pos_x_mm", 13004.3)),
                new Case("ornithopter", "IF-03", "connector", cell("de", "stecker", "P31", "polzahl", 55)),
                new Case("ornithopter", "IF-04", "fastener", cell("de", "befestiger", "RF-4", "durchmesser_mm", 7.94)),
                new Case("wings", "IF-06", "unit", cell("uk", "harness_connector", "HC 61", "pos_uom", "IN")),
                new Case("drivetrain", "fr-panel", "staleRevision", cell("fr", "reference_externe", "XR-0002", "indice_attendu", "02")),
                new Case("drivetrain", "fr-hinge", "danglingReference", cell("fr", "reference_externe", "XR-0004", "urn", "urn:plm:fr:part:fr-panel")),
                new Case("drivetrain", "fr-panel", "lifecycleConflict", cell("uk", "component", "UK-4102", "lifecycle", "Released")),
                new Case("fastening", "de-bearing", "conflictingLeadTime", cell("de", "lieferantenteil", "3", "bevorzugt", false)));
        for (Case c : cases) {
            PreviewJson.Answer answer = run(OFFICER, c.product(), c.cell());
            List<PreviewJson.Result> fixed = c.subject().startsWith("IF-") ? iface(answer, c.subject()).fixed() : part(answer, c.subject()).fixed();
            assertThat(rules(fixed)).as(c.rule() + " on " + c.subject()).contains(c.rule());
            assertThat(answer.tally().newlyFailing()).as(c.rule() + ": nothing newly failing in " + answer).isZero();
        }
        assertThat(iface(run(OFFICER, "ornithopter", cases.get(1).cell()), "IF-03")).extracting(PreviewJson.InterfaceDiff::before,
                PreviewJson.InterfaceDiff::after).containsExactly("fail", "pass");
    }

    @Test
    void aWrongValuePreviewsAsStillFailing() {
        PreviewJson.InterfaceDiff connector = iface(run(OFFICER, "ornithopter", cell("de", "stecker", "P31", "polzahl", 40)), "IF-03");
        assertThat(connector.fixed()).isEmpty();
        assertThat(rules(connector.stillFailing())).containsExactly("connector");
        assertThat(connector.after()).isEqualTo("fail");

        PreviewJson.PartDiff panel = part(run(OFFICER, "drivetrain", cell("uk", "component", "UK-4102", "lifecycle", "Frozen")), "fr-panel");
        assertThat(rules(panel.stillFailing())).as("Frozen is BLOCKED: the released panel still depends on it").contains("lifecycleConflict");
    }

    @Test
    void aValueTheSitesVocabularyRefusesIsRefusedWithItsReason() {
        assertThatThrownBy(() -> run(OFFICER, "drivetrain", cell("de", "bauteil", "de-housing", "status", "Released")))
                .hasMessage("cells[0]: status of bauteil takes one of [In Arbeit, Freigegeben, Gesperrt, Ersetzt], not \"Released\"");
        assertThatThrownBy(() -> run(OFFICER, "drivetrain", cell("de", "bauteil", "de-housing", "revision", "2")))
                .hasMessage("cells[0]: revision of bauteil takes a value of the form \\d{2}, not 2");
        assertThatThrownBy(() -> run(OFFICER, "ornithopter", cell("de", "stecker", "P31", "polzahl", 3.5)))
                .hasMessage("cells[0]: polzahl of stecker takes an integer, not 3.5");
        assertThatThrownBy(() -> run(OFFICER, "ornithopter", cell("de", "stecker", "P31", "typ", "")))
                .hasMessage("cells[0]: typ of stecker takes a non-blank text, not \"\"");
        assertThatThrownBy(() -> run(OFFICER, "ornithopter", cell("de", "stecker", "P31", "stecker_id", "P32")))
                .hasMessage("cells[0]: stecker_id is the key column of stecker");
        assertThatThrownBy(() -> run(OFFICER, "ornithopter", cell("de", "stecker", "P31", "teil_nr", "de-housing")))
                .hasMessageStartingWith("cells[0]: teil_nr of stecker reaches no triple the rules read");
        assertThatThrownBy(() -> run(OFFICER, "ornithopter", cell("de", "kabel", "P31", "typ", "x")))
                .hasMessage("cells[0]: the DE catalogue has no table kabel");
        assertThatThrownBy(() -> run(OFFICER, "ornithopter", cell("it", "stecker", "P31", "typ", "x")))
                .hasMessageStartingWith("cells[0]: unknown plm it");
        PreviewJson.Cell[] many = Collections.nCopies(PreviewService.MAX_CELLS + 1, cell("de", "stecker", "P31", "polzahl", 55))
                .toArray(PreviewJson.Cell[]::new);
        assertThatThrownBy(() -> run(OFFICER, "ornithopter", many)).hasMessage("cells takes at most 500 cells, not 501");
        assertThat(federator.requests).as("a refused vocabulary is refused before any federation").isEmpty();
    }

    @Test
    void aCellOnARecordTheProfileMayNotSeeIsRefusedAsIfItDidNotExist() {
        assertThat(run(OFFICER, "tail", cell("de", "stecker", "P71", "polzahl", 19)).cells()).hasSize(1);
        assertThatThrownBy(() -> run(DE, "tail", cell("de", "stecker", "P71", "polzahl", 19)))
                .hasMessage("cells[0]: no row P71 of stecker the de-engineer profile may see");
        assertThatThrownBy(() -> run(DE, "tail", cell("de", "stecker", "P99", "polzahl", 19)))
                .hasMessage("cells[0]: no row P99 of stecker the de-engineer profile may see");
    }

    @Test
    void thePreviewCorrectionToolAnswersThePreviewAndRefusesWithTheReason() throws IOException {
        AtelierTools tools = new AtelierTools(service, POLICY, JSON, new PlmApi("", () -> null), KnownTerms.load(), preview);
        Map<String, Object> cell = new LinkedHashMap<>(Map.of("plm", "DE", "table", "stecker", "key", "P31", "column", "polzahl"));
        cell.put("value", 55);
        McpSchema.CallToolResult result = tools.call("preview_correction", Map.of("product", "ornithopter", "cells", List.of(cell)), OFFICER);
        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        JsonNode answer = JSON.readTree(((McpSchema.TextContent) result.content().get(0)).text());
        assertThat(answer.path("interfaces")).anySatisfy(i -> assertThat(i.path("id").asText() + " " + i.path("after").asText()).isEqualTo("IF-03 pass"));
        assertThat(answer.path("tally").path("newlyFailing").asInt()).isZero();

        cell.put("value", "many");
        McpSchema.CallToolResult refused = tools.call("preview_correction", Map.of("product", "ornithopter", "cells", List.of(cell)), OFFICER);
        assertThat(refused.isError()).isTrue();
        assertThat(((McpSchema.TextContent) refused.content().get(0)).text()).isEqualTo("cells[0]: polzahl of stecker takes a number, not \"many\"");
    }

    @Test
    void aPreviewOnlyReadsTheFederationAndTheCatalogue() throws IOException {
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        HttpServer stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/api/", exchange -> {
            seen.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            String plm = exchange.getRequestURI().getPath().replaceAll("^/api/([a-z]+)/catalogue$", "$1");
            byte[] body = CATALOGUES.path(plm).toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(CATALOGUES.has(plm) ? 200 : 404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        stub.start();
        try {
            PlmApi plm = new PlmApi("http://127.0.0.1:" + stub.getAddress().getPort() + "/api", () -> null);
            PreviewService live = new PreviewService(service, new CellMappings(Path.of("../ontop/mappings")), plm);
            PreviewJson.Answer answer = live.preview(OFFICER, new PreviewJson.Request("ornithopter", null, List.of(
                    cell("de", "stecker", "P31", "polzahl", 55), cell("fr", "connecteur", "J31", "nb_broches", 37))));
            assertThat(seen).as("one catalogue read per site the cells name, and no other request").containsExactly(
                    "GET /api/de/catalogue", "GET /api/fr/catalogue");
            assertThat(answer.provenance().calls()).extracting(Federator.Call::kind).containsOnly("materialized", "virtual");
            assertThat(federator.requests).as("every federation request parses as a SPARQL query, a CONSTRUCT").isNotEmpty()
                    .allSatisfy(q -> assertThat(QueryFactory.create(q).isConstructType()).isTrue());
        } finally {
            stub.stop(0);
        }
        assertThat(service.interfaceById("IF-03", OFFICER).orElseThrow()._interface().status())
                .as("the sources are as they were").isEqualTo("fail");
    }
}
