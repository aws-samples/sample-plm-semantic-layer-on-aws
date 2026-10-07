// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import atelier.query.api.Json;
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
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

/**
 * The virtual bill of materials over fixtures/bom.ttl (a drivetrain whose four sites each hold their own kit):
 * the product root minted by the layer, the quantities multiplied down the trees, masses in kg whatever unit a
 * PLM stores, a hidden assembly left unexpanded and counted apart, and the where-used over the lines.
 */
class BomTest {
    static final Policy POLICY = EvidenceTest.POLICY;
    static final String PRODUCT = "drivetrain";
    static final String GEAR = "https://example.com/atelier/de/part/de-gear";

    private final BomFederator federator = new BomFederator();
    private final QueryService service = new QueryService(federator, new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));

    /** The fixture federation, keeping each bill-of-materials run so a test can read its merged graph. */
    static final class BomFederator extends FixtureFederator {
        Result bomRun;

        BomFederator() {
            super(fixture());
        }

        static Model fixture() {
            Model model = FixtureFederator.fixture();
            RDFDataMgr.read(model, "fixtures/bom.ttl");
            return model;
        }

        @Override
        public Result bom(Policy.Profile profile, String product) {
            bomRun = super.bom(profile, product);
            return bomRun;
        }
    }

    private Json.Bom bom(String profile) {
        return service.bom(Caller.user(POLICY.profile(profile)), PRODUCT);
    }

    private static Json.BomNode site(Json.Bom bom, String plm) {
        return bom.root().children().stream().filter(Json.BomNode.class::isInstance).map(Json.BomNode.class::cast)
                .filter(n -> plm.equals(n.plm())).findFirst().orElseThrow();
    }

    private static Json.BomNode child(Json.BomNode node, String id) {
        return node.children().stream().filter(Json.BomNode.class::isInstance).map(Json.BomNode.class::cast)
                .filter(n -> id.equals(n.id())).findFirst().orElseThrow();
    }

    private static Json.BomRollup rollup(Json.Bom bom, String plm) {
        return bom.sites().stream().filter(r -> plm.equals(r.plm())).findFirst().orElseThrow();
    }

    @Test
    void theRootIsTheLayersAndHoldsTheSiteKitOfEveryPlm() {
        Json.Bom bom = bom("export-officer");

        assertThat(bom.product()).isEqualTo(PRODUCT);
        assertThat(bom.root().partType()).isEqualTo("PRODUCT");
        assertThat(bom.root().name()).isEqualTo("Drivetrain");
        assertThat(bom.root().plm()).as("no PLM holds the product").isNull();
        assertThat(bom.root().children()).map(k -> ((Json.BomNode) k).id()).containsExactly("fr-kit", "de-kit", "uk-kit", "es-kit");
        assertThat(site(bom, "de").partType()).isEqualTo("ASSEMBLY");
        assertThat(site(bom, "de").lifecycleState()).isEqualTo("RELEASED");
        assertThat(bom.sites()).map(Json.BomRollup::plm).containsExactly("fr", "de", "uk", "es");
    }

    @Test
    void occurrencesMultiplyTheQuantitiesDownAThreeLevelTree() {
        Json.Bom bom = bom("export-officer");
        Json.BomNode stage = child(child(site(bom, "de"), "de-gearbox"), "de-stage");
        Json.BomNode gear = child(stage, "de-gear");

        assertThat(stage.quantity()).isEqualTo(2);
        assertThat(gear.quantity()).isEqualTo(3);
        assertThat(gear.occurrences()).as("1 gearbox x 2 stages x 3 gears").isEqualTo(6);
        assertThat(child(stage, "de-bolt-kit").occurrences()).isEqualTo(8);
        assertThat(gear.unitMassKg()).isEqualTo(1.5);
        assertThat(gear.extendedMassKg()).isEqualTo(9.0);
        assertThat(stage.unitMassKg()).as("an assembly weighs what it holds").isEqualTo(4.5);

        Json.BomRollup de = rollup(bom, "de");
        assertThat(de.occurrences()).as("6 gears, 8 bolt kits, 1 housing; assemblies are not parts").isEqualTo(15);
        assertThat(de.massKg()).isCloseTo(19.0, within(1e-9));
        assertThat(de.withoutMass()).as("the bolt kit states no mass").isEqualTo(1);
        assertThat(site(bom, "de").extendedMassKg()).isCloseTo(19.0, within(1e-9));
    }

    @Test
    void aMassStoredInPoundsRollsUpInKilograms() {
        Json.Bom bom = bom("export-officer");
        Json.BomNode motor = child(site(bom, "uk"), "uk-motor");

        assertThat(motor.unitMassKg()).isCloseTo(4.5359237, within(1e-9));
        assertThat(motor.extendedMassKg()).isCloseTo(9.0718474, within(1e-9));
        assertThat(rollup(bom, "uk").massKg()).isCloseTo(9.0718474, within(1e-9));
        Json.BomNode frame = child(site(bom, "es"), "es-frame");
        assertThat(frame.unitMassKg()).as("a part with items weighs itself and them").isCloseTo(31.0, within(1e-9));
        assertThat(rollup(bom, "es").massKg()).isCloseTo(31.0, within(1e-9));
        assertThat(rollup(bom, "es").occurrences()).isEqualTo(3);
        assertThat(bom.total().occurrences()).isEqualTo(8 + 15 + 2 + 3);
        assertThat(bom.total().massKg()).isCloseTo(12 + 19 + 9.0718474 + 31, within(1e-9));
    }

    @Test
    void aHiddenAssemblyIsAMarkerItsItemsAreNotExpandedAndItCountsAsHidden() throws Exception {
        Json.Bom de = bom("de-engineer");
        Json.BomNode frKit = site(de, "fr");

        assertThat(frKit.children()).hasSize(2);
        assertThat(frKit.children()).filteredOn(Json.BomHidden.class::isInstance)
                .containsExactly(new Json.BomHidden(true, "fr", 1, 1));
        assertThat(child(frKit, "fr-panel").occurrences()).as("the panel's direct line stays").isEqualTo(2);
        assertThat(rollup(de, "fr").occurrences()).isEqualTo(2);
        assertThat(rollup(de, "fr").hiddenOccurrences()).isEqualTo(1);
        assertThat(de.total().hiddenOccurrences()).isEqualTo(1);
        String tree = new ObjectMapper().writeValueAsString(List.of(de.root(), de.sites(), de.total()));
        assertThat(tree).doesNotContain("fr-cover-set").doesNotContain("Ensemble capots");

        Json.Bom officer = bom("export-officer");
        assertThat(rollup(officer, "fr").occurrences()).as("4 panels and 2 hinges in the cover set, 2 panels on the kit").isEqualTo(8);
        assertThat(rollup(officer, "fr").withoutMass()).as("the hinge states no mass").isEqualTo(1);
        assertThat(rollup(officer, "fr").hiddenOccurrences()).isZero();
        assertThat(new ObjectMapper().writeValueAsString(officer.root())).doesNotContain("\"redacted\"");
    }

    /** The file index answers for every part; what it says about an item the viewer may not see leaves the graph. */
    @Test
    void theCadFileOfAHiddenItemNoInterfaceNamesIsRedacted() {
        Property cadFile = ModelFactory.createDefaultModel().createProperty(Atelier.CAD_FILE);
        Resource hinge = ModelFactory.createDefaultModel().createResource("https://example.com/atelier/fr/part/fr-hinge");
        bom("de-engineer");
        assertThat(federator.bomRun.model().contains(hinge, cadFile)).isFalse();
        bom("export-officer");
        assertThat(federator.bomRun.model().contains(hinge, cadFile)).isTrue();
    }

    @Test
    void eachPlmIsAskedForItsPartsTheirReferencesAndTheLinesFromAndToThem() {
        bom("export-officer");
        Federator.Result run = federator.bomRun;

        for (String plm : Atelier.PLMS) {
            assertThat(run.queries().get("ontop-" + plm)).as(plm).hasSize(4)
                    .anySatisfy(q -> assertThat(q).contains("atelier:ExternalReference ; atelier:fromPart ?part"))
                    .anySatisfy(q -> assertThat(q).contains("atelier:BomLine ; atelier:parent ?part"))
                    .anySatisfy(q -> assertThat(q).contains("atelier:BomLine ; atelier:child ?part"));
        }
        Model model = run.model();
        assertThat(model.contains(model.createResource("https://example.com/atelier/de/part/de-stage"),
                model.createProperty(Atelier.ONT + "assembles"), model.createResource(GEAR))).as("parent assembles child").isTrue();
    }

    @Test
    void whereUsedOverTheLinesNamesEachParentWithQuantityAndOccurrences() {
        Json.BomWhereUsed gear = service.bomWhereUsed("de-gear", Caller.user(POLICY.profile("export-officer")), PRODUCT).orElseThrow();
        assertThat(gear.usedIn()).containsExactly(new Json.BomUse("de-stage", "de", "Planetenstufe", "ASSEMBLY", null, 3, 6.0));
        assertThat(gear.occurrences()).isEqualTo(6);

        Json.BomWhereUsed panel = service.bomWhereUsed("fr-panel", Caller.user(POLICY.profile("export-officer")), PRODUCT).orElseThrow();
        assertThat(panel.usedIn()).containsExactlyInAnyOrder(
                new Json.BomUse("fr-cover-set", "fr", "Ensemble capots", "ASSEMBLY", null, 4, 4.0),
                new Json.BomUse("fr-kit", "fr", "Kit capotage", "ASSEMBLY", null, 2, 2.0));
        assertThat(panel.occurrences()).isEqualTo(6);

        Json.BomWhereUsed seenByDe = service.bomWhereUsed("fr-panel", Caller.user(POLICY.profile("de-engineer")), PRODUCT).orElseThrow();
        assertThat(seenByDe.usedIn()).containsExactlyInAnyOrder(
                new Json.BomUse("fr-kit", "fr", "Kit capotage", "ASSEMBLY", null, 2, 2.0),
                new Json.BomUse(null, "fr", null, null, true, 4, null));
        assertThat(seenByDe.occurrences()).isEqualTo(2);

        Json.BomWhereUsed hidden = service.bomWhereUsed("fr-cover-set", Caller.user(POLICY.profile("de-engineer")), PRODUCT).orElseThrow();
        assertThat(hidden.part()).isEqualTo(new Json.Redacted(true, "fr"));
        assertThat(hidden.usedIn()).isEmpty();

        assertThat(service.bomWhereUsed("no-such-part", Caller.user(POLICY.profile("export-officer")), PRODUCT)).isEmpty();
    }

    @Test
    void aBillOfMaterialsIsPerProduct() {
        assertThatThrownBy(() -> service.bom(Caller.user(POLICY.profile("export-officer")), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theBomToolsAnswerOverMcpAndTheTreeIsCutAtTheDepthAsked() throws Exception {
        ObjectMapper json = new ObjectMapper();
        AtelierTools tools = new AtelierTools(service, POLICY, json, new PlmApi("", () -> null), KnownTerms.load());
        Caller officer = Caller.user(POLICY.profile("export-officer"));

        McpSchema.CallToolResult cut = tools.call("bom", Map.of("product", PRODUCT, "depth", 2), officer);
        assertThat(cut.isError()).isNotEqualTo(Boolean.TRUE);
        JsonNode bom = json.readTree(((McpSchema.TextContent) cut.content().get(0)).text());
        JsonNode deKit = bom.path("root").path("children").get(1);
        assertThat(deKit.path("id").asText()).isEqualTo("de-kit");
        assertThat(deKit.path("children").get(0).path("id").asText()).isEqualTo("de-gearbox");
        assertThat(deKit.path("children").get(0).has("children")).as("depth 2: root, kits, their items").isFalse();
        assertThat(bom.path("sites").get(1).path("occurrences").asDouble()).as("the roll-up covers the whole tree").isEqualTo(15);
        assertThat(bom.has("sparql")).isFalse();

        McpSchema.CallToolResult used = tools.call("bom_where_used", Map.of("part", "de-gear", "product", PRODUCT), officer);
        JsonNode usedIn = json.readTree(((McpSchema.TextContent) used.content().get(0)).text());
        assertThat(usedIn.path("usedIn").get(0).path("id").asText()).isEqualTo("de-stage");
        assertThat(tools.call("bom", Map.of(), officer).isError()).isTrue();
        assertThat(List.of(tools.call("bom", Map.of("product", PRODUCT, "depth", 0), officer).isError())).containsExactly(true);
    }
}
