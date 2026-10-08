// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.mapping.BomMapper;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.validation.RuleValidator;
import atelier.query.validation.RuleViolation;
import java.math.BigDecimal;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.Test;

/**
 * The product rules over the production product files ({@link ProductFixture}): massLimit adds the mass of every
 * site's items, occurrences times unit mass in kg, and compares it with the product's limit, which only the semantic
 * layer can do; massScale finds the site of a product whose masses are in another unit than its column states.
 */
class ProductRulesTest {
    static final Policy POLICY = EvidenceTest.POLICY;
    static final Caller OFFICER = Caller.user(POLICY.profile("export-officer"));
    static final Caller CLEARED = Caller.user(POLICY.profile("programme-cleared"));

    private final RuleValidator validator = new RuleValidator();

    private QueryService service(Model fixture) {
        return new QueryService(new FixtureFederator(fixture), validator, new NoOntopSql(), new CadUrls(null, null));
    }

    private static Json.Product product(Json.ProductsResponse response, String key) {
        return response.products().stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void theCubesatExceedsItsMassLimitOverEverySite() {
        Json.ProductsResponse products = service(ProductFixture.of("cubesat")).products(OFFICER);
        Json.Product cubesat = product(products, "cubesat");
        assertThat(cubesat.findings()).containsExactly(new Json.Finding("massLimit",
                "cubesat weighs 2.01 kg over every site's parts, more than its limit of 2.0 kg", null));
        assertThat(cubesat.partCount()).as("the flight software is no part with geometry").isEqualTo(26);
        assertThat(products.sparql()).contains("atelier:massLimit", "atelier:parent");
    }

    @Test
    void theCubesatFailsForTheOfficerAndIsNotEvaluableWhereABoardIsHidden() {
        Model fixture = ProductFixture.of("cubesat");
        Json.Product officer = product(service(fixture).products(OFFICER), "cubesat");
        assertThat(officer.massLimit()).isEqualTo(new Json.MassLimit("fail", 2.0, null));

        Json.Product cleared = product(service(fixture).products(CLEARED), "cubesat");
        assertThat(cleared.massLimit()).as("the LICENSED board is hidden from programme-cleared")
                .isEqualTo(new Json.MassLimit("not-evaluable", 2.0, 1));
        assertThat(cleared.findings()).as("a partial view raises no violation").isEmpty();
    }

    @Test
    void massScaleIsNotEvaluatedWhereAPartOfTheProductIsHidden() {
        Model fixture = ProductFixture.of("difference-engine");
        Resource hidden = fixture.createResource(ProductFixture.iri("DE", "D-36001"));
        org.apache.jena.rdf.model.Property releasable = fixture.createProperty(Atelier.ONT + "releasableTo");
        hidden.removeAll(releasable).addProperty(releasable, "LICENSED");
        QueryService service = service(fixture);
        assertThat(product(service.products(OFFICER), "difference-engine").findings()).as("once per product, naming no part")
                .extracting(Json.Finding::rule, Json.Finding::value).containsExactly(tuple("massScale", null));
        assertThat(product(service.products(CLEARED), "difference-engine").findings()).isEmpty();
        assertThat(service.parts(CLEARED, "difference-engine").parts()).filteredOn(Json.Part.class::isInstance).map(Json.Part.class::cast)
                .allSatisfy(p -> assertThat(p.findings() == null ? List.<Json.Finding>of() : p.findings())
                        .extracting(Json.Finding::rule).doesNotContain("massScale"));
    }

    @Test
    void theCubesatPassesWhenItsLimitIsRaised() {
        Model fixture = ProductFixture.of("cubesat");
        Resource limit = fixture.createResource(Atelier.productIri("cubesat") + "/mass-limit");
        limit.removeAll(fixture.createProperty("http://qudt.org/schema/qudt/numericValue"));
        limit.addLiteral(fixture.createProperty("http://qudt.org/schema/qudt/numericValue"), fixture.createTypedLiteral(new BigDecimal("2.100")));
        Json.Product cubesat = product(service(fixture).products(OFFICER), "cubesat");
        assertThat(cubesat.findings()).isEmpty();
        assertThat(cubesat.massLimit()).isEqualTo(new Json.MassLimit("pass", 2.1, null));
    }

    @Test
    void occurrencesMultiplyDownTheLinesFromTheSiteKit() {
        Model model = ProductFixture.of("difference-engine");
        BomMapper.deriveOccurrences(model);
        // FR3600 (kit) > FR3601 x1 ... the totals of the product file's roll-up per site
        List<RuleViolation> none = validator.validate(model).stream().filter(r -> r.rule().equals("massLimit")).toList();
        assertThat(none).as("no limit, no finding").isEmpty();
        BigDecimal fr = BigDecimal.ZERO;
        for (Resource item : model.listSubjectsWithProperty(model.createProperty(Atelier.ONT + "occurrences")).toList()) {
            if (item.getURI().contains("/fr/part/") && "PART".equals(item.getProperty(model.createProperty(Atelier.ONT + "partType")).getString())) {
                fr = fr.add(new BigDecimal(item.getProperty(model.createProperty(Atelier.ONT + "occurrences")).getString()));
            }
        }
        assertThat(fr.intValue()).as("FR part occurrences, as data/generate.py counts them").isEqualTo(2285);
    }

    @Test
    void massScaleFlagsTheFrenchPartsOfTheDifferenceEngineOnly() {
        Model fixture = ModelFactory.createDefaultModel();
        for (String key : List.of("aerial-screw", "antikythera", "cart", "cubesat", "difference-engine", "glider", "ornithopter",
                "rover", "steam-engine", "wind-turbine")) fixture.add(ProductFixture.of(key));
        List<RuleViolation> results = validator.validate(fixture).stream().filter(r -> r.rule().equals("massScale")).toList();
        List<String> frParts = fixture.listSubjectsWithProperty(fixture.createProperty(Atelier.ONT + "mass")).toList().stream()
                .filter(p -> p.getURI().startsWith(Atelier.DATA + "fr/part/FR36")
                        && "PART".equals(p.getProperty(fixture.createProperty(Atelier.ONT + "partType")).getString()))
                .map(Resource::getURI).sorted().toList();
        assertThat(results.stream().map(RuleViolation::subject).sorted().toList()).as("every FR part of the difference engine with a mass, nothing else")
                .containsExactlyElementsOf(frParts);
        assertThat(results).allSatisfy(r -> {
            assertThat(r.focusNode()).isEqualTo(Atelier.productIri("difference-engine"));
            assertThat(r.severity()).isEqualTo("Warning");
            assertThat(r.message()).isEqualTo("FR part masses in difference-engine are 119.0 times the other sites' (median 157.0 kg"
                    + " against 1.322 kg): the site stores its masses in another unit than its column states");
        });
    }

    @Test
    void massScaleIsSilentWhenTheFrenchMassesAreInKilograms() {
        Model fixture = ProductFixture.of("difference-engine");
        org.apache.jena.rdf.model.Property value = fixture.createProperty("http://qudt.org/schema/qudt/numericValue");
        for (Resource q : fixture.listSubjectsWithProperty(value).toList()) {
            if (!q.getURI().startsWith(Atelier.DATA + "fr/part/")) continue;
            BigDecimal grams = new BigDecimal(q.getProperty(value).getString());
            q.removeAll(value).addLiteral(value, fixture.createTypedLiteral(grams.movePointLeft(3)));
        }
        assertThat(validator.validate(fixture)).extracting(RuleViolation::rule).doesNotContain("massScale");
    }

    @Test
    void theFrenchPartsOfTheDifferenceEngineCarryTheFindingAndSoftwareHasNoCadFinding() {
        Json.PartsResponse parts = service(ProductFixture.of("difference-engine")).parts(OFFICER, "difference-engine");
        List<Json.Part> all = parts.parts().stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast).toList();
        assertThat(all).filteredOn(p -> p.plm().equals("fr") && "PART".equals(p.partType()) && p.mass() != null)
                .isNotEmpty().allSatisfy(p -> assertThat(p.findings()).extracting(Json.Finding::rule).contains("massScale"));
        assertThat(all).filteredOn(p -> !p.plm().equals("fr"))
                .allSatisfy(p -> assertThat(p.findings() == null ? List.<Json.Finding>of() : p.findings())
                        .extracting(Json.Finding::rule).doesNotContain("massScale"));
        assertThat(all).filteredOn(p -> List.of("UK-3690", "D-36060").contains(p.id()))
                .extracting(Json.Part::partType, p -> p.findings() == null || p.findings().isEmpty(), Json.Part::cadFile)
                .containsOnly(org.assertj.core.groups.Tuple.tuple("DOCUMENT", true, null));
    }
}
