// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import atelier.query.api.ApiErrors;
import atelier.query.api.Json;
import atelier.query.api.QueryController;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.rules.RuleCatalogue;
import atelier.query.rules.RuleFailures;
import atelier.query.rules.RulesController;
import atelier.query.validation.RuleValidator;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * GET /query/rules/failures?product= over fixtures/neighbourhood.ttl with the CubeSat and the wind turbine added: the
 * records of the product asked for, and per rule a count of the other products where it fails, both from the caller's
 * own every-product answer, so a profile counts only what it may see.
 */
class RuleFailuresScopeTest {
    private static final Policy POLICY = new Policy(Path.of("../../ontology/policy.json"));
    private static final RuleValidator VALIDATOR = new RuleValidator();
    private static final RuleCatalogue CATALOGUE = new RuleCatalogue(VALIDATOR);
    private static final Caller OFFICER = Caller.user(POLICY.profile("export-officer"));
    private static final Caller DE = Caller.user(POLICY.profile("de-engineer"));

    private final QueryService service = new QueryService(new FixtureFederator(fixture()), VALIDATOR, new NoOntopSql(), new CadUrls(null, null));
    private final RuleFailures failures = new RuleFailures(service, CATALOGUE);

    private static Model fixture() {
        return FixtureFederator.fixture().add(ProductFixture.of("cubesat")).add(Closures.with(FunctionFixture.of("wind-turbine")));
    }

    /** Per rule, the number of products other than {@code product} the every-product answer names for it. */
    private static Map<String, Integer> elsewhere(List<RuleFailures.Failure> all, String product) {
        return all.stream().filter(f -> !f.product().equals(product)).collect(Collectors.groupingBy(RuleFailures.Failure::rule,
                Collectors.collectingAndThen(Collectors.mapping(RuleFailures.Failure::product, Collectors.toSet()), Set::size)));
    }

    @Test
    void aProductsAnswerHoldsItsOwnRecordsAndCountsTheOtherProductsPerProfile() {
        for (Caller caller : List.of(OFFICER, DE)) {
            List<RuleFailures.Failure> all = failures.failures(caller).failures();
            Set<String> products = all.stream().map(RuleFailures.Failure::product).collect(Collectors.toSet());
            assertThat(products).as(caller.profile().name()).hasSizeGreaterThan(1);
            for (String product : products) {
                RuleFailures.Response scoped = failures.failures(caller, product, null, null);
                assertThat(scoped.failures()).as(product).isNotEmpty().allMatch(f -> f.product().equals(product))
                        .containsExactlyElementsOf(all.stream().filter(f -> f.product().equals(product)).toList());
                assertThat(scoped.otherProducts()).as(product).isEqualTo(elsewhere(all, product));
            }
        }
        assertThat(failures.failures(OFFICER).otherProducts()).as("the every-product answer counts nothing").isNull();
    }

    @Test
    void theCountOfOtherProductsIsRedactedAsTheirRecordsAre() {
        Map<String, Integer> officer = failures.failures(OFFICER, "wind-turbine", null, null).otherProducts();
        Map<String, Integer> de = failures.failures(DE, "wind-turbine", null, null).otherProducts();
        assertThat(officer.get("massLimit")).as("the CubeSat's mass limit fails for the officer").isEqualTo(1);
        assertThat(de).as("the DE engineer cannot evaluate the CubeSat's mass: no count").doesNotContainKey("massLimit");
    }

    @Test
    void aSubtreeHoldsTheRecordsOfItsInterfacesAndParts() {
        RuleFailures.Response gearbox = failures.failures(OFFICER, "wind-turbine", "D-37073", null);
        Set<String> parts = service.parts(OFFICER, "wind-turbine", "D-37073").parts().stream().filter(Json.Part.class::isInstance)
                .map(p -> ((Json.Part) p).id()).collect(Collectors.toSet());
        assertThat(gearbox.failures()).contains(new RuleFailures.Failure("meshModule", "wind-turbine", "part", "D-37031", "de"))
                .noneMatch(f -> f.kind().equals("product"))
                .filteredOn(f -> f.kind().equals("part")).allMatch(f -> parts.contains(f.id()));
        assertThat(gearbox.failures()).hasSizeLessThan(failures.failures(OFFICER, "wind-turbine", null, null).failures().size());
        assertThat(gearbox.otherProducts()).isEqualTo(failures.failures(OFFICER, "wind-turbine", null, null).otherProducts());
    }

    @Test
    void theRestApiScopesByProductAndRefusesWhatNamesNone() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new RulesController(CATALOGUE, failures, POLICY)).setControllerAdvice(new ApiErrors()).build();
        mvc.perform(get("/query/rules/failures?product=cubesat").header(QueryController.PROFILE_HEADER, "export-officer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failures[?(@.product != 'cubesat')]").isEmpty())
                .andExpect(jsonPath("$.otherProducts").isMap());
        mvc.perform(get("/query/rules/failures").header(QueryController.PROFILE_HEADER, "export-officer"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.otherProducts").doesNotExist());
        mvc.perform(get("/query/rules/failures?product=no-such-product")).andExpect(status().isNotFound());
        mvc.perform(get("/query/rules/failures?root=D-37073")).andExpect(status().isBadRequest());
    }
}
