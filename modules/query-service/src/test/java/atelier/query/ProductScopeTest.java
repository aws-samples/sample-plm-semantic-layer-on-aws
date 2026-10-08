// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.federation.FederatedQueries;
import atelier.query.federation.Federator;
import atelier.query.federation.UnknownProduct;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.validation.RuleValidator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The product dimension over fixtures/neighbourhood.ttl: three Atelier core products (ornithopter holds
 * every part and the interfaces IF-01 to IF-04; wings the mid cross beam, the left root fitting and the
 * left actuator with IF-05 and IF-06; tail the tail boom, the tail drive and the untagged tail plane
 * with IF-07 and IF-08). A run scoped to a product keeps the interfaces of the product
 * ({@code atelier:ofProduct}) and asks the PLMs about the product's parts ({@code atelier:partOf}) only;
 * the export-control filter then applies as always.
 */
class ProductScopeTest {
    private static final Policy POLICY = EvidenceTest.POLICY;
    private static final Caller OFFICER = Caller.user(POLICY.profile("export-officer"));
    private static final Caller DE = Caller.user(POLICY.profile("de-engineer"));
    private static final Caller NOBODY = Caller.user(POLICY.profile(null));

    private final FixtureFederator federator = new FixtureFederator();
    private final QueryService service = new QueryService(federator, new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));

    @Test
    void productsAreListedWithTheNumberOfPartsTheProfileMaySee() {
        Json.ProductsResponse officer = service.products(OFFICER);
        assertThat(officer.products()).containsExactly(
                new Json.Product("ornithopter", "Ornithopter ground demonstrator (Paris Manuscript B, f. 74v)", "x aft from the frame nose, y to the pilot's right, z up with z = 0 on the keel beam axis; mm", 9, List.of(), null, 0),
                new Json.Product("tail", "Tail unit", "x aft from the frame nose, y to the pilot's right, z up with z = 0 on the keel beam axis; mm", 3, List.of(), null, 0),
                new Json.Product("wings", "Wings", null, 3, List.of(), null, 0));
        assertThat(officer.provenance().calls()).extracting(Federator.Call::endpoint, Federator.Call::requests)
                .contains(org.assertj.core.groups.Tuple.tuple("ontop-core", 4));
        assertThat(officer.sparql()).contains("atelier:partOf").contains("atelier:frame");
        assertThat(officer.policy().profile()).isEqualTo("export-officer");

        assertThat(service.products(DE).products()).extracting(Json.Product::partCount)
                .as("the control post (FR only), the tail drive (licensed) and the untagged tail plane are not counted").containsExactly(6, 1, 3);
        assertThat(service.products(NOBODY).products()).extracting(Json.Product::key, Json.Product::partCount)
                .as("the products are listed even when the profile sees none of their parts")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("ornithopter", 0), org.assertj.core.groups.Tuple.tuple("tail", 0),
                        org.assertj.core.groups.Tuple.tuple("wings", 0));
    }

    @Test
    void aProductThatHoldsNoPartIsNotListed() {
        org.apache.jena.rdf.model.Model fixture = FixtureFederator.fixture();
        org.apache.jena.rdf.model.Resource empty = fixture.createResource(Atelier.productIri("empty"));
        fixture.add(empty, org.apache.jena.vocabulary.RDF.type, fixture.createResource(Atelier.ONT + "Product"));
        fixture.add(empty, fixture.createProperty(Atelier.ONT + "label"), "A product with no part");
        QueryService over = new QueryService(new FixtureFederator(fixture), new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));
        assertThat(over.products(OFFICER).products()).extracting(Json.Product::key).containsExactly("ornithopter", "tail", "wings");
        assertThat(over.productList(OFFICER).products()).extracting(Json.ProductListing::key).containsExactly("ornithopter", "tail", "wings");
    }

    @Test
    void theProductListIsTheProductsAnswerWithoutTheRulesFromOneRequestPerPlm() {
        for (Policy.Profile profile : POLICY.profiles()) {
            Caller caller = Caller.user(profile);
            Json.ProductListResponse list = service.productList(caller);
            assertThat(list.products()).as(profile.name()).containsExactlyElementsOf(service.products(caller).products().stream()
                    .map(p -> new Json.ProductListing(p.key(), p.name(), p.frame(), p.partCount())).toList());
            assertThat(list.timings().validationMs()).isZero();
            assertThat(list.policy().profile()).isEqualTo(profile.name());
            assertThat(list.provenance().calls()).as("each PLM with a visible part is asked for their description only, one request")
                    .filteredOn(c -> c.endpoint().startsWith("ontop-") && !c.endpoint().equals("ontop-core"))
                    .allSatisfy(c -> assertThat(c.requests()).isLessThanOrEqualTo(1));
            if (profile.name().equals("export-officer")) assertThat(list.provenance().calls()).extracting(Federator.Call::requests).doesNotContain(0);
            assertThat(list.sparql()).doesNotContain("atelier:BomLine").doesNotContain("atelier:ExternalReference");
        }
    }

    @Test
    void aProductKeepsItsOwnInterfacesAndAsksThePlmsAboutItsPartsOnly() {
        Json.InterfacesResponse wings = service.interfaces(OFFICER, "wings");
        assertThat(wings.interfaces()).extracting(Json.Interface::id, Json.Interface::status)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("IF-05", "pass"), org.assertj.core.groups.Tuple.tuple("IF-06", "fail"));
        Map<String, List<String>> sent = federator.last().queries();
        assertThat(FederatedQueriesTest.parts(sent.get("ontop-fr").get(0))).containsExactly(Atelier.DATA + "fr/part/fr-cross-beam-mid");
        assertThat(FederatedQueriesTest.parts(sent.get("ontop-uk").get(0))).containsExactly(Atelier.DATA + "uk/part/uk-left-root-fitting", Atelier.DATA + "uk/part/uk-left-wing-actuator");
        assertThat(sent.get("ontop-de")).as("no DE part in the wings").isEmpty();
        assertThat(sent.get("ontop-es")).isEmpty();
        List<String> core = sent.get("ontop-core");
        assertThat(core.get(0)).as("the wings' memberships, not every product's").isEqualTo(FederatedQueries.members(Atelier.productIri("wings")));
        for (String tags : core.subList(1, 3)) {
            assertThat(tags).as("the core is asked about the product's parts by the product key, not part by part")
                    .contains("?part atelier:partOf <" + Atelier.productIri("wings") + "> .").doesNotContain("VALUES");
        }
        assertThat(core.get(3)).as("the wings' facts alone, read once").isEqualTo(FederatedQueries.productFacts(Atelier.productIri("wings")));
        assertThat(core).allSatisfy(text -> assertThat(FederatedQueriesTest.parts(text)).isEmpty());
        assertThat(wings.policy().untagged()).as("the untagged tail plane is outside the product").isEmpty();

        Json.PartsResponse parts = service.parts(OFFICER, "wings");
        assertThat(parts.parts()).extracting(p -> ((Json.Part) p).id()).containsExactly("fr-cross-beam-mid", "uk-left-root-fitting", "uk-left-wing-actuator");
    }

    @Test
    void theProductHoldingEveryPartListsItsOwnInterfacesAndThePartsTheyName() {
        Json.InterfacesResponse all = service.interfaces(DE);
        Json.InterfacesResponse ornithopter = service.interfaces(DE, "ornithopter");
        assertThat(ornithopter.interfaces()).as("the wings' and the tail's interfaces belong to their own products")
                .isEqualTo(all.interfaces().stream().filter(i -> i.product().equals("ornithopter")).toList())
                .extracting(Json.Interface::id).containsExactly("IF-01", "IF-02", "IF-03", "IF-04");
        assertThat(all.interfaces()).extracting(Json.Interface::product)
                .containsExactly("ornithopter", "ornithopter", "ornithopter", "ornithopter", "wings", "wings", "tail", "tail");
        assertThat(ornithopter.findings()).isEqualTo(all.findings());
        assertThat(ornithopter.policy().untagged()).as("the untagged tail plane is a part of the ornithopter, which holds every part").containsExactly(PolicyTest.TAIL_PLANE);
        assertThat(all.policy().untagged()).containsExactly(PolicyTest.TAIL_PLANE);
        assertThat(service.parts(DE, "ornithopter").parts()).as("every part of the ornithopter, including those on the other products' interfaces only, as the DE engineer sees them")
                .extracting(p -> p instanceof Json.Part part ? part.id() : "redacted " + ((Json.Redacted) p).plm())
                .containsExactly("fr-cross-beam-mid", "de-right-inner-spar", "de-right-root-fitting", "uk-left-root-fitting", "uk-left-wing-actuator",
                        "es-tail-boom", "redacted fr");
        Map<String, List<String>> sent = federator.last().queries();
        assertThat(FederatedQueriesTest.parts(sent.get("ontop-uk").get(0))).as("the UK parts of the ornithopter sit on the wings' interfaces only")
                .containsExactly(Atelier.DATA + "uk/part/uk-left-root-fitting", Atelier.DATA + "uk/part/uk-left-wing-actuator");
        assertThat(FederatedQueriesTest.parts(sent.get("ontop-es").get(0))).containsExactly(Atelier.DATA + "es/part/es-tail-boom");
    }

    @Test
    void exportControlAppliesInsideTheProduct() {
        Json.InterfacesResponse de = service.interfaces(DE, "tail");
        assertThat(de.interfaces()).extracting(Json.Interface::id, Json.Interface::status)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("IF-07", "not-evaluable"), org.assertj.core.groups.Tuple.tuple("IF-08", "not-evaluable"));
        assertThat(de.policy().untagged()).containsExactly(PolicyTest.TAIL_PLANE);
        assertThat(FederatedQueriesTest.parts(federator.last().queries().get("ontop-es").get(0)))
                .as("the DE engineer's ES arm names the tail boom only: the tail plane is untagged").containsExactly(Atelier.DATA + "es/part/es-tail-boom");
        assertThat(federator.last().queries().get("ontop-de")).as("the tail drive is EXPORT-LICENCE: nothing of the tail is visible in DE").isEmpty();

        Json.InterfacesResponse officer = service.interfaces(OFFICER, "tail");
        assertThat(officer.interfaces()).extracting(Json.Interface::id, Json.Interface::status)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("IF-07", "fail"), org.assertj.core.groups.Tuple.tuple("IF-08", "pass"));
        assertThat(officer.interfaces().get(0).violations()).extracting(Json.Violation::rule).containsExactly("orphan");
    }

    @Test
    void aProductNoNamedPartBelongsToIsRefusedAndAKeyOutsideTheAlphabetToo() {
        assertThatThrownBy(() -> service.interfaces(OFFICER, "nope")).isInstanceOf(UnknownProduct.class).hasMessage("no product nope");
        assertThatThrownBy(() -> service.parts(OFFICER, "nope")).isInstanceOf(UnknownProduct.class);
        assertThatThrownBy(() -> service.interfaces(OFFICER, "tail unit")).isInstanceOf(IllegalArgumentException.class).hasMessage("invalid product key");
        assertThatThrownBy(() -> service.interfaces(OFFICER, "../x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theScopedGraphDescribesTheProductOnly() {
        Answer tail = service.answer(OFFICER, "tail");
        String turtle = atelier.query.evidence.Turtle.of(tail.model().getGraph());
        assertThat(turtle).contains("product/tail").doesNotContain("product/ornithopter").doesNotContain("product/wings")
                .doesNotContain("fr-control-post").doesNotContain("uk-left-wing-actuator").doesNotContain("IF-01").doesNotContain("IF-05");
        assertThat(tail.interfaces()).hasSize(2);
    }
}
