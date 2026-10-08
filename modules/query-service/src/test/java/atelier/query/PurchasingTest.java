// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.mapping.ItemClasses;
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
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

/**
 * Purchased items over fixtures/purchasing.ttl: one socket head cap screw bought under four standards in two units,
 * two screws whose sites state no length, a bearing whose preferred offers disagree on the lead time, and a supplier
 * listed by two sites. The ontology's standards scheme decides which standards are one item; the converted sizes decide
 * the rest.
 */
class PurchasingTest {
    static final Policy POLICY = EvidenceTest.POLICY;
    static final String PRODUCT = "fastening";
    static final Caller UNKNOWN = Caller.user(POLICY.profile("unknown"));
    static final Caller UK = Caller.user(POLICY.profile("uk-engineer"));

    private final RuleValidator validator = new RuleValidator();
    private final QueryService service = new QueryService(new FixtureFederator(fixture()), validator, new NoOntopSql(),
            new CadUrls(null, null));

    static Model fixture() {
        Model model = FixtureFederator.fixture();
        RDFDataMgr.read(model, "fixtures/purchasing.ttl");
        return model;
    }


    /** The fixture with owl:sameAs in the links graph between every two of {@code parts}, as POST /core/equivalences writes it. */
    static Model confirmed(String... parts) {
        Model model = fixture();
        for (String a : parts) {
            for (String b : parts) {
                if (!a.equals(b)) model.add(model.createResource(SCREW + a), model.createProperty(Atelier.SAME_AS), model.createResource(SCREW + b));
            }
        }
        return model;
    }

    static final String SCREW = "https://example.com/atelier/";

    @Test
    void aGroupIsAProposalUntilAUserConfirmsEveryMemberAsTheSameItem() {
        assertThat(service.equivalents(UNKNOWN, PRODUCT).groups()).extracting(Json.EquivalentGroup::confirmed).containsOnly(false);
        assertThat(service.equivalents(UNKNOWN, PRODUCT).groups().get(0).members()).extracting(Json.EquivalentMember::iri)
                .containsExactly(SCREW + "fr/part/fr-screw", SCREW + "de/part/de-screw", SCREW + "uk/part/uk-screw", SCREW + "es/part/es-screw");

        // The officer confirmed the spare screw too, which the unknown profile may not see: its four visible members are linked.
        FixtureFederator all = new FixtureFederator(confirmed("fr/part/fr-screw", "de/part/de-screw", "uk/part/uk-screw", "es/part/es-screw",
                "uk/part/uk-spare-screw"));
        QueryService confirmedService = new QueryService(all, validator, new NoOntopSql(), new CadUrls(null, null));
        assertThat(confirmedService.equivalents(UNKNOWN, PRODUCT).groups()).extracting(Json.EquivalentGroup::confirmed).containsExactly(true, false);
        assertThat(all.last().model().listStatements(null, all.last().model().createProperty(Atelier.SAME_AS),
                all.last().model().createResource(SCREW + "uk/part/uk-spare-screw")).toList())
                .as("a visible part's equivalence to a hidden one is redacted").isEmpty();

        QueryService partly = new QueryService(new FixtureFederator(confirmed("fr/part/fr-screw", "de/part/de-screw", "uk/part/uk-screw")),
                validator, new NoOntopSql(), new CadUrls(null, null));
        assertThat(partly.equivalents(UNKNOWN, PRODUCT).groups().get(0).confirmed()).as("the Spanish screw is not linked").isFalse();
    }

    @Test
    void theOntologyDecidesWhichStandardsDesignateOneItem() {
        ItemClasses classes = new ItemClasses(validator.ontology());
        String screw = classes.resolve(Atelier.ONT + "FastenerStandard", "ISO 4762");
        assertThat(screw).isEqualTo(Atelier.ONT + "SocketHeadCapScrew");
        assertThat(List.of("DIN 912", "NF EN ISO 4762", "UNE-EN ISO 4762", "BS EN ISO 4762"))
                .as("the national adoptions and the DIN standard it replaced are altLabels")
                .allMatch(s -> screw.equals(classes.resolve(Atelier.ONT + "FastenerStandard", s)));
        assertThat(List.of("NF EN ISO 10642", "DIN 934", "BS 2470")).as("no concept carries them")
                .allMatch(s -> classes.resolve(Atelier.ONT + "FastenerStandard", s) == null);
    }

    static List<Object> value(Json.EquivalentMember m, String attribute) {
        Json.MemberValue v = m.values().stream().filter(x -> x.attribute().equals(attribute)).findFirst().orElse(null);
        return v == null ? null : java.util.Arrays.asList(v.stored(), v.unit(), v.mm());
    }

    static Double mm(Json.EquivalentGroup g, String attribute) {
        return g.attributes().stream().filter(a -> a.attribute().equals(attribute)).findFirst().map(Json.GroupAttribute::mm).orElse(null);
    }

    @Test
    void fourPartNumbersInTwoUnitsAreOneScrew() {
        Json.Equivalents answer = service.equivalents(UNKNOWN, PRODUCT);
        assertThat(answer.groups()).extracting(Json.EquivalentGroup::itemClass, g -> g.attributes().get(0).text(),
                        g -> mm(g, "nominalDiameter"), g -> mm(g, "nominalLength"))
                .containsExactly(tuple("fastener", "ISO 4762", 3.0, 10.0), tuple("fastener", "ISO 4762", 4.0, null));

        Json.EquivalentGroup screw = answer.groups().get(0);
        assertThat(screw.classLabel()).isEqualTo("Fastener");
        assertThat(screw.attributes()).extracting(Json.GroupAttribute::attribute, Json.GroupAttribute::label)
                .containsExactly(tuple("standard", "standard"), tuple("nominalDiameter", "nominal diameter"), tuple("nominalLength", "nominal length"));
        assertThat(screw.attributes().get(0).concept()).isEqualTo(Atelier.ONT + "SocketHeadCapScrew");
        assertThat(screw.members()).extracting(Json.EquivalentMember::plm, Json.EquivalentMember::id, Json.EquivalentMember::standard,
                        m -> value(m, "nominalDiameter"), m -> value(m, "nominalLength").get(2))
                .containsExactly(
                        tuple("fr", "fr-screw", "NF EN ISO 4762", List.of("3.000", "MilliM", 3.0), 10.0),
                        tuple("de", "de-screw", "DIN 912", List.of("3.000", "MilliM", 3.0), 10.0),
                        tuple("uk", "uk-screw", "BS EN ISO 4762", List.of("0.1180", "IN", 2.9972), 10.0076),
                        tuple("es", "es-screw", "UNE-EN ISO 4762", List.of("0.118", "IN", 2.9972), 10.0076));
        assertThat(screw.members()).extracting(Json.EquivalentMember::name).contains("Tornillo cilíndrico 0,118 x 0,394 in");
        assertThat(screw.stocking()).as("four part numbers in four sites; one stock line once confirmed")
                .isEqualTo(new Json.Stocking(4, 4, 4, 1));
    }

    @Test
    void aSizeOutsideTheToleranceOrAStandardNoConceptCarriesStaysOut() {
        List<String> grouped = service.equivalents(UNKNOWN, PRODUCT).groups().stream()
                .flatMap(g -> g.members().stream()).map(Json.EquivalentMember::id).toList();
        assertThat(grouped).doesNotContain("es-long-screw", "fr-countersunk", "de-nut", "es-nut", "de-bearing");
    }

    @Test
    void aPartWithoutALengthGroupsWithPartsWithoutOne() {
        Json.EquivalentGroup stud = service.equivalents(UNKNOWN, PRODUCT).groups().get(1);
        assertThat(mm(stud, "nominalLength")).isNull();
        assertThat(stud.members()).extracting(Json.EquivalentMember::id).containsExactly("de-stud", "es-stud");
    }

    @Test
    void aHiddenPartJoinsTheGroupOnlyForAProfileThatSeesIt() {
        assertThat(service.equivalents(UK, PRODUCT).groups().get(0).members()).extracting(Json.EquivalentMember::id)
                .containsExactly("fr-screw", "de-screw", "uk-screw", "uk-spare-screw", "es-screw");
        assertThat(new ObjectMapper().valueToTree(service.equivalents(UNKNOWN, PRODUCT).groups()).toString()).doesNotContain("uk-spare-screw");
    }

    @Test
    void suppliersAreGroupedByNameAcrossTheSitesThatListThem() {
        Json.Suppliers answer = service.suppliers(UNKNOWN, PRODUCT);
        assertThat(answer.suppliers()).extracting(Json.Supplier::name).containsExactly("Ampferhain Lager GmbH",
                "Kestrelmoor Fastener Supply Ltd", "Quellmoos Normteile KG", "Tornillería Vega Lenta S.L.", "Visserie Brumevale SARL",
                "Zinnkraut Antriebstechnik GmbH");
        Json.Supplier kestrelmoor = answer.suppliers().get(1);
        assertThat(kestrelmoor.sites()).extracting(Json.SupplierSite::plm, Json.SupplierSite::id, Json.SupplierSite::location)
                .containsExactly(tuple("uk", "S02", "Birmingham"), tuple("es", "P-01", "Birmingham"));
        assertThat(kestrelmoor.sites().get(0).offers()).as("the spare screw is hidden from this profile")
                .extracting(Json.Offer::id, Json.Offer::partId, Json.Offer::supplierPartNumber, Json.Offer::leadTimeDays, Json.Offer::preferred)
                .containsExactly(tuple("1", "uk-screw", "KF-SHC-0118-0394", 21, true));
        Json.Supplier quellmoos = answer.suppliers().get(2);
        assertThat(quellmoos.sites().get(0).offers()).as("each offer is named by its key in the site's offer table")
                .extracting(Json.Offer::id, Json.Offer::partId)
                .containsExactly(tuple("2", "de-bearing"), tuple("5", "de-nut"), tuple("1", "de-screw"));
    }

    @Test
    void aPartWithOneOfferIsSingleSource() {
        assertThat(service.suppliers(UNKNOWN, PRODUCT).singleSource())
                .extracting(Json.SingleSource::plm, Json.SingleSource::id, Json.SingleSource::supplier, Json.SingleSource::leadTimeDays)
                .containsExactly(tuple("fr", "fr-screw", "Visserie Brumevale SARL", 10),
                        tuple("de", "de-nut", "Quellmoos Normteile KG", 7),
                        tuple("de", "de-screw", "Quellmoos Normteile KG", 7),
                        tuple("uk", "uk-screw", "Kestrelmoor Fastener Supply Ltd", 21),
                        tuple("es", "es-long-screw", "Tornillería Vega Lenta S.L.", 14),
                        tuple("es", "es-nut", "Tornillería Vega Lenta S.L.", 14),
                        tuple("es", "es-screw", "Kestrelmoor Fastener Supply Ltd", 21));
    }

    @Test
    void twoPreferredOffersWithDifferentLeadTimesAreAFindingOnThePartOnly() {
        String message = "preferred offers disagree on the lead time: 10 days from Quellmoos Normteile KG, 35 days from Zinnkraut Antriebstechnik GmbH";
        assertThat(service.suppliers(UNKNOWN, PRODUCT).conflicts()).extracting(Json.LeadTimeConflict::plm, Json.LeadTimeConflict::id,
                Json.LeadTimeConflict::message).containsExactly(tuple("de", "de-bearing", message));
        List<Json.Part> parts = service.parts(UNKNOWN, PRODUCT).parts().stream().filter(Json.Part.class::isInstance)
                .map(Json.Part.class::cast).toList();
        assertThat(parts).filteredOn(p -> p.findings().stream().anyMatch(f -> f.rule().equals("conflictingLeadTime")))
                .extracting(Json.Part::id, p -> p.findings().get(0).message(), p -> p.findings().get(0).value())
                .as("the finding names the longer preferred offer").containsExactly(tuple("de-bearing", message, new Json.RowRef("de", "offer", "3")));
        assertThat(validator.validate(fixture())).filteredOn(v -> v.rule().equals("conflictingLeadTime"))
                .allMatch(v -> v.severity().equals("Warning") && !v.failsInterface())
                .extracting(v -> v.value().getURI()).containsExactly("https://example.com/atelier/de/offer/3");
    }

    @Test
    void purchasedItemsAndSuppliersArePerProduct() {
        assertThatThrownBy(() -> service.equivalents(UNKNOWN, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.suppliers(UNKNOWN, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theToolsAnswerOverMcp() throws Exception {
        ObjectMapper json = new ObjectMapper();
        AtelierTools tools = new AtelierTools(service, POLICY, json, new PlmApi("", () -> null), KnownTerms.load());
        McpSchema.CallToolResult equivalents = tools.call("equivalent_parts", Map.of("product", PRODUCT), UNKNOWN);
        JsonNode groups = json.readTree(((McpSchema.TextContent) equivalents.content().get(0)).text()).path("groups");
        assertThat(groups.get(0).path("members").size()).isEqualTo(4);
        McpSchema.CallToolResult suppliers = tools.call("suppliers", Map.of("product", PRODUCT), UNKNOWN);
        JsonNode conflicts = json.readTree(((McpSchema.TextContent) suppliers.content().get(0)).text()).path("conflicts");
        assertThat(conflicts.get(0).path("id").asText()).isEqualTo("de-bearing");
        JsonNode offer = json.readTree(((McpSchema.TextContent) suppliers.content().get(0)).text()).path("suppliers").get(0)
                .path("sites").get(0).path("offers").get(0);
        assertThat(offer.path("id").asText()).isEqualTo("4");
        assertThat(tools.call("suppliers", Map.of(), UNKNOWN).isError()).isTrue();
    }
}
