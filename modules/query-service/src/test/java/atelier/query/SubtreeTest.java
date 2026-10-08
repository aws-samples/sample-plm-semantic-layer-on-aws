// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.federation.Endpoints;
import atelier.query.federation.FederatedQueries;
import atelier.query.federation.UnknownItem;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.validation.RuleValidator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

/**
 * Answers scoped to one item's subtree over the drivetrain of fixtures/bom.ttl, references.ttl and subtree.ttl, each
 * site's closure stated as its view states it ({@link Closures}). The DE gearbox holds a three-level tree; its gear
 * references the ES frame and the UK encoder, so the subtree resolves in two rounds across three sites; the FR cover
 * set is releasable to FR only. Every PLM request names roots, never parts.
 */
class SubtreeTest {
    static final Policy POLICY = EvidenceTest.POLICY;
    static final Caller OFFICER = Caller.user(POLICY.profile("export-officer"));
    static final Caller DE_ENGINEER = Caller.user(POLICY.profile("de-engineer"));
    static final String PRODUCT = "drivetrain";
    static final String DE = "https://example.com/atelier/de/part/";

    private final FixtureFederator federator = new FixtureFederator(fixture());
    private final QueryService service = new QueryService(federator, new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));

    static Model fixture() {
        Model model = FixtureFederator.fixture();
        for (String file : List.of("fixtures/bom.ttl", "fixtures/references.ttl", "fixtures/subtree.ttl")) RDFDataMgr.read(model, file);
        return Closures.with(model);
    }

    private static List<String> ids(Json.PartsResponse response) {
        return response.parts().stream().filter(Json.Part.class::isInstance).map(p -> ((Json.Part) p).plm() + "/" + ((Json.Part) p).id())
                .sorted().toList();
    }

    /** The texts the last run sent to the PLM endpoints. */
    private List<String> plmRequests() {
        return Atelier.PLMS.stream().flatMap(plm -> federator.last().queries().get(Endpoints.ontopName(plm)).stream()).toList();
    }

    @Test
    void theGearboxResolvesInTwoRoundsAcrossThreeSites() {
        Json.PartsResponse parts = service.parts(OFFICER, PRODUCT, "de-gearbox");

        assertThat(ids(parts)).containsExactly("de/de-bolt-kit", "de/de-gear", "de/de-gearbox", "de/de-housing", "de/de-stage",
                "es/es-bracket", "es/es-frame", "uk/UK-4101");
        Json.Subtree subtree = parts.subtree();
        assertThat(subtree.root()).isEqualTo("de-gearbox");
        assertThat(subtree.plm()).isEqualTo("de");
        assertThat(subtree.name()).isEqualTo("Getriebe");
        assertThat(subtree.depth()).as("round 3 asks for the dangling de-missing and adds nothing").isEqualTo(2);
        assertThat(subtree.rounds()).containsExactly(
                new Json.SubtreeRound(1, List.of(new Json.SubtreeRoot("de-gearbox", "de")), 5),
                new Json.SubtreeRound(2, List.of(new Json.SubtreeRoot("es-frame", "es"), new Json.SubtreeRoot("UK-4101", "uk")), 3));
        assertThat(subtree.items()).isEqualTo(8);
        assertThat(subtree.productRules()).isEqualTo("not-evaluable");
        assertThat(subtree.unresolved()).isEmpty();
    }

    @Test
    void everyPlmRequestNamesRootsAndNoneListsParts() {
        service.parts(OFFICER, PRODUCT, "de-gearbox");

        List<String> requests = plmRequests();
        assertThat(requests).isNotEmpty().allSatisfy(r -> assertThat(r).contains("VALUES ?root").contains("atelier:contains")
                .doesNotContain("VALUES ?part"));
        assertThat(federator.last().queries().get("ontop-fr")).as("no FR item is in the gearbox's subtree").isEmpty();
        assertThat(String.join("\n", federator.last().queries().get("ontop-de")))
                .as("the DE requests name the gearbox and the dangling target, never a part under the gearbox")
                .doesNotContain("de-stage").doesNotContain("de-gear>").doesNotContain("de-housing");
    }

    @Test
    void aHiddenAssemblyIsARedactedNodeAndNothingUnderItLeavesItsDatabase() {
        Json.PartsResponse parts = service.parts(DE_ENGINEER, PRODUCT, "fr-kit");

        assertThat(ids(parts)).as("the panel is under the kit directly too; the hinge only under the hidden cover set")
                .containsExactly("de/de-housing", "fr/fr-kit", "fr/fr-panel");
        assertThat(parts.parts()).filteredOn(Json.Redacted.class::isInstance).containsExactly(new Json.Redacted(true, "fr"));
        assertThat(parts.subtree().items()).isEqualTo(3);
        String fr = String.join("\n", federator.last().queries().get("ontop-fr"));
        assertThat(fr).as("the hidden cover set and the hinge under it are never named to the FR PLM")
                .doesNotContain("fr-cover-set").doesNotContain("fr-hinge");
        assertThat(fr).as("the kit, above a hidden item, is read by itself").contains("FILTER (?part = ?root)");

        Json.Bom bom = service.bom(DE_ENGINEER, PRODUCT, "fr-kit");
        assertThat(bom.root().id()).isEqualTo("fr-kit");
        assertThat(bom.root().children()).hasSize(2).anySatisfy(c -> assertThat(c).isEqualTo(new Json.BomHidden(true, "fr", 1, 1)));
        Json.BomNode panel = bom.root().children().stream().filter(Json.BomNode.class::isInstance).map(Json.BomNode.class::cast)
                .findFirst().orElseThrow();
        assertThat(panel.id()).isEqualTo("fr-panel");
        assertThat(panel.children()).as("two references of the panel name the DE housing: the tree crosses to DE")
                .singleElement().satisfies(c -> {
                    Json.BomNode housing = (Json.BomNode) c;
                    assertThat(housing.plm()).isEqualTo("de");
                    assertThat(housing.quantity()).isEqualTo(2);
                    assertThat(housing.occurrences()).isEqualTo(4);
                });
        assertThat(bom.sites()).extracting(Json.BomRollup::plm).containsExactly("fr", "de");
        assertThat(bom.total().hiddenOccurrences()).isEqualTo(1);
    }

    @Test
    void aHiddenRootIsRedactedAndNotExpanded() {
        Json.PartsResponse parts = service.parts(DE_ENGINEER, PRODUCT, "fr-cover-set");

        assertThat(parts.parts()).containsExactly(new Json.Redacted(true, "fr"));
        assertThat(parts.subtree().redacted()).isTrue();
        assertThat(parts.subtree().name()).isNull();
        assertThat(parts.subtree().items()).isZero();
        assertThat(federator.last().queries().get("ontop-fr")).as("its site is never asked about it").isEmpty();
    }

    /** The UK motor references the FR hinge, which de-engineer may not see: a redacted node, and the FR PLM is never asked. */
    @Test
    void aHiddenReferencedItemIsARedactedNodeItsSiteIsNotAskedAbout() {
        Json.PartsResponse parts = service.parts(DE_ENGINEER, PRODUCT, "uk-motor");

        assertThat(ids(parts)).containsExactly("uk/uk-motor");
        assertThat(parts.parts()).filteredOn(Json.Redacted.class::isInstance).containsExactly(new Json.Redacted(true, "fr"));
        assertThat(parts.subtree().rounds()).extracting(Json.SubtreeRound::roots).containsExactly(
                List.of(new Json.SubtreeRoot("uk-motor", "uk")), List.of(new Json.SubtreeRoot("fr-hinge", "fr")));
        assertThat(federator.last().queries().get("ontop-fr")).isEmpty();
    }

    @Test
    void validationRunsOnTheSubtreeOnly() {
        Json.References references = service.references(OFFICER, PRODUCT, "de-gearbox");

        Map<String, String> status = references.references().stream()
                .collect(Collectors.toMap(r -> r.plm() + "/" + r.id(), Json.Reference::status));
        assertThat(status).as("the gear's and the bracket's references, none of the panel's, the hinge's or the motor's")
                .containsOnlyKeys("de/XR-0001", "de/XR-0002", "es/XR-0001", "es/XR-0002")
                .containsEntry("de/XR-0001", "staleRevision").containsEntry("de/XR-0002", "ok")
                .containsEntry("es/XR-0001", "danglingReference").containsEntry("es/XR-0002", "danglingReference");
        assertThat(references.timings().validationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void theInterfacesTouchingTheSubtreeComeWithTheirFarSideAsContext() {
        Json.InterfacesResponse answer = service.interfaces(OFFICER, PRODUCT, "de-gearbox");

        assertThat(answer.interfaces()).extracting(Json.Interface::id).containsExactly("IF-D1");
        Json.Interface iface = answer.interfaces().get(0);
        assertThat(iface.status()).isEqualTo("pass");
        Map<String, Boolean> context = iface.parts().stream().map(Json.Part.class::cast)
                .collect(Collectors.toMap(Json.Part::id, p -> Boolean.TRUE.equals(p.context())));
        assertThat(context).containsExactlyInAnyOrderEntriesOf(Map.of("de-housing", false, "fr-panel", true));
        Json.Part panel = iface.parts().stream().map(Json.Part.class::cast).filter(p -> p.id().equals("fr-panel")).findFirst().orElseThrow();
        assertThat(panel.findings()).as("a context part is outside the subtree: its own findings are not stated").isEmpty();
        assertThat(answer.subtree().context()).isEqualTo(1);
        assertThat(String.join("\n", federator.last().queries().get("ontop-fr")))
                .as("the panel is read by itself, attributes and features").contains("FILTER (?part = ?root)")
                .doesNotContain("ExternalReference");
    }

    @Test
    void theLinkStoreIsAskedLastOncePerGraphWithTheSubtreeItems() {
        service.interfaces(OFFICER, PRODUCT, "de-gearbox");

        List<String> sent = List.of(federator.last().sparql().split("\n\n(?=PREFIX)"));
        List<String> links = federator.last().queries().get("neptune");
        assertThat(links).hasSize(2);
        int lastRound = 0;
        for (int i = 0; i < sent.size(); i++) if (sent.get(i).contains("atelier:BomLine")) lastRound = i;
        assertThat(sent.indexOf(links.get(0))).as("the interfaces after the last round's structure request").isGreaterThan(lastRound);
        assertThat(sent.getLast()).as("the file index last, once the context parts are known").isEqualTo(links.get(1));
        assertThat(links.get(0)).as("the interfaces with a side in the subtree").contains("VALUES ?side")
                .contains("?if atelier:betweenPart ?side .").contains("<" + DE + "de-gear>").doesNotContain("uk-motor")
                .doesNotContain("fr-panel");
        assertThat(links.get(1)).as("the file index of the subtree and of its context part").contains("VALUES ?part")
                .contains("<" + DE + "de-housing>").contains("fr-panel>").doesNotContain("uk-motor").doesNotContain("fr-hinge");

        Model answered = links(links.get(0));
        assertThat(answered.listSubjectsWithProperty(answered.createProperty(Atelier.ONT + "betweenPart")).mapWith(s -> s.getLocalName()).toSet())
                .as("the store returns IF-D1 and not IF-D2, both sides of which are outside the subtree")
                .containsExactly("IF-D1");
    }

    @Test
    void thePartsAnswerOfASubtreeReadsTheFileIndexTheNamesOfItsItemsAndTheDrives() {
        service.parts(OFFICER, PRODUCT, "de-gearbox");

        List<String> links = federator.last().queries().get("neptune");
        assertThat(links).hasSize(3);
        assertThat(links.get(0)).contains("VALUES ?part").contains("<" + DE + "de-gearbox>").doesNotContain("atelier:betweenPart");
        assertThat(links.get(1)).as("the labels and equivalences of the same items").contains(Atelier.LABELS_GRAPH)
                .contains("<" + DE + "de-gearbox>").doesNotContain("atelier:betweenPart");
        assertThat(links.get(2)).as("the product's functional edges, which the meshModule rule reads")
                .isEqualTo(FederatedQueries.drives(Atelier.productIri(PRODUCT)));
    }

    /** The answer of the fixture's links graph to a link-store request, as the link store evaluates it. */
    private static Model links(String query) {
        org.apache.jena.query.Dataset dataset = org.apache.jena.query.DatasetFactory.create();
        dataset.addNamedModel(Atelier.LINKS_GRAPH, RDFDataMgr.loadModel("fixtures/subtree.ttl"));
        try (org.apache.jena.query.QueryExecution exec = org.apache.jena.query.QueryExecutionFactory.create(query, dataset)) {
            return exec.execConstruct();
        }
    }

    @Test
    void theProductRootIsTheWholeProductAsWithoutARoot() {
        Json.PartsResponse whole = service.parts(OFFICER, PRODUCT);
        String sparql = whole.sparql();
        Json.PartsResponse rooted = service.parts(OFFICER, PRODUCT, PRODUCT);

        assertThat(rooted.subtree()).isNull();
        assertThat(rooted.parts()).isEqualTo(whole.parts());
        assertThat(rooted.sparql()).isEqualTo(sparql);
        assertThat(service.interfaces(OFFICER, PRODUCT, PRODUCT).interfaces()).isEqualTo(service.interfaces(OFFICER, PRODUCT).interfaces());
        assertThat(service.bom(OFFICER, PRODUCT, PRODUCT).root()).isEqualTo(service.bom(OFFICER, PRODUCT).root());
        assertThat(service.references(OFFICER, PRODUCT, null).references()).isEqualTo(service.references(OFFICER, PRODUCT).references());
    }

    @Test
    void theProductRulesAreNotEvaluatedOnASubtree() {
        QueryService engine = new QueryService(new FixtureFederator(Closures.with(ProductFixture.of("difference-engine"))),
                new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));

        assertThat(rules(engine.parts(OFFICER, "difference-engine", null))).as("the product root").contains("massScale");
        assertThat(rules(engine.parts(OFFICER, "difference-engine", "D-36100"))).doesNotContain("massScale", "massLimit");
    }

    private static Set<String> rules(Json.PartsResponse response) {
        return response.parts().stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast)
                .flatMap(p -> p.findings() == null ? java.util.stream.Stream.empty() : p.findings().stream())
                .map(Json.Finding::rule).collect(Collectors.toSet());
    }

    @Test
    void aRootNamesAnItemOfTheProduct() {
        assertThatThrownBy(() -> service.parts(OFFICER, PRODUCT, "no-such-item")).isInstanceOf(UnknownItem.class)
                .hasMessage("no item no-such-item in drivetrain");
        assertThatThrownBy(() -> service.parts(OFFICER, null, "de-gearbox")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.parts(OFFICER, "wings", "de-gearbox")).isInstanceOf(UnknownItem.class);
    }

    @Test
    void theWindTurbineGearboxIsTheClosureOfItsLinesInTheProductFile() {
        Model turbine = Closures.with(ProductFixture.of("wind-turbine"));
        QueryService engine = new QueryService(new FixtureFederator(turbine), new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));

        Json.PartsResponse parts = engine.parts(OFFICER, "wind-turbine", "D-37073");

        Set<String> expected = turbine.listObjectsOfProperty(turbine.createResource(DE + "D-37073"),
                turbine.createProperty(Atelier.ONT + "contains")).mapWith(o -> "de/" + Atelier.nativeId(o.asResource().getURI())).toSet();
        assertThat(ids(parts)).containsExactlyInAnyOrderElementsOf(expected).hasSize(40);
        assertThat(parts.subtree().depth()).as("the gearbox's tree references no other site").isEqualTo(1);
    }
}
