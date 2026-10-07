// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.mapping.TermsMapper;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.validation.RuleValidator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

/**
 * Terms over the released labels graph (data/labels.ttl, every item of the ten products and the products' glossary):
 * Zahnrad, roue and gear name the same concept, and its items are found at the German, French and British sites, each
 * in its own language. Then the route over the fixture federation: the names of an item the viewer may not see are
 * redacted before the terms are resolved.
 */
class TermsTest {
    static final String GEAR = "https://example.com/atelier/term/gear";
    static final Policy POLICY = EvidenceTest.POLICY;

    /** The released labels graph with every item placed in a product, as the redacted model of an officer holds it. */
    static Model released() {
        Model model = RDFDataMgr.loadModel("../../data/labels.ttl");
        Property partOf = model.createProperty(Atelier.PART_OF);
        Resource product = model.createResource(Atelier.PRODUCT + "catalogue");
        for (Resource item : model.listSubjectsWithProperty(model.createProperty(Atelier.PREF_LABEL)).toList()) {
            if (Atelier.plmOf(item.getURI()) != null) item.addProperty(partOf, product);
        }
        return model;
    }

    private static Json.Term gear(List<Json.Term> terms) {
        return terms.stream().filter(t -> t.iri().equals(GEAR)).findFirst().orElseThrow();
    }

    @Test
    void zahnradRoueAndGearNameTheSameConceptWithItemsAtTheGermanFrenchAndBritishSites() {
        TermsMapper mapper = new TermsMapper(released());
        List<Json.Term> zahnrad = mapper.resolve("Zahnrad");
        List<Json.Term> roue = mapper.resolve("roue");
        List<Json.Term> gear = mapper.resolve("gear");

        assertThat(zahnrad.get(0).iri()).as("the German label is the term").isEqualTo(GEAR);
        assertThat(zahnrad.get(0).match()).isEqualTo("exact");
        assertThat(gear.get(0).iri()).isEqualTo(GEAR);
        assertThat(roue).extracting(Json.Term::iri).as("roue dentée is a French label of the gear").contains(GEAR);
        assertThat(gear(roue).match()).isEqualTo("word");
        assertThat(gear(roue).parts()).isEqualTo(zahnrad.get(0).parts()).isEqualTo(gear.get(0).parts());

        Json.Term term = gear.get(0);
        assertThat(term.labels()).containsEntry("en", "gear").containsEntry("de", "Zahnrad").containsEntry("fr", "engrenage")
                .containsEntry("es", "engranaje");
        Set<String> sites = term.parts().stream().map(Json.TermPart::plm).collect(Collectors.toSet());
        assertThat(sites).contains("de", "fr", "uk");
        assertThat(term.parts()).filteredOn(p -> p.id().equals("D-31001")).singleElement()
                .satisfies(p -> {
                    assertThat(p.name()).isEqualTo("Antriebszahnrad links (Kronrad)");
                    assertThat(p.nameEn()).isEqualTo("Left first drive gear (crown wheel)");
                    assertThat(p.matched()).as("a German label inside a compound").isEqualTo(new Json.TermLabel("de", "Zahnrad"));
                });
        assertThat(term.parts()).filteredOn(p -> p.plm().equals("fr")).extracting(Json.TermPart::matched)
                .as("a pinion is a gear").contains(new Json.TermLabel("fr", "pignon"));
        assertThat(term.parts()).filteredOn(p -> p.plm().equals("uk")).extracting(Json.TermPart::matched)
                .contains(new Json.TermLabel("en", "gear"));
        assertThat(term.parts()).extracting(Json.TermPart::id).as("a gearbox is another term").doesNotContain("UK-3730");
    }

    @Test
    void aWordOfAnotherLanguageIsAWholeWordAndAccentsAndCaseDoNotMatter() {
        TermsMapper mapper = new TermsMapper(released());
        assertThat(mapper.resolve("MANIVELLE")).extracting(Json.Term::iri)
                .contains("https://example.com/atelier/term/crank", "https://example.com/atelier/term/hand-crank");
        assertThat(mapper.resolve("mat")).extracting(Json.Term::iri).as("mât, without its accent")
                .contains("https://example.com/atelier/term/mast", "https://example.com/atelier/term/tower");
        assertThat(mapper.resolve("piston").get(0).parts()).allMatch(p -> !p.name().toLowerCase().contains("pistonné"));
        assertThat(mapper.resolve("nothing of the kind")).isEmpty();
    }

    @Test
    void theRouteResolvesOverTheRedactedLabelsGraph() {
        Model fixture = FixtureFederator.fixture();
        RDFDataMgr.read(fixture, "fixtures/purchasing.ttl");
        RDFDataMgr.read(fixture, "fixtures/labels.ttl");
        Resource screw = fixture.createResource("https://example.com/atelier/term/screw");
        screw.addProperty(fixture.createProperty(Atelier.SKOS + "inScheme"), fixture.createResource(Atelier.TERMS));
        for (String[] label : new String[][] {{"en", "screw"}, {"de", "Schraube"}, {"fr", "vis"}, {"es", "tornillo"}}) {
            screw.addProperty(fixture.createProperty(Atelier.PREF_LABEL), fixture.createLiteral(label[1], label[0]));
        }
        QueryService service = new QueryService(new FixtureFederator(fixture), new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));

        Json.Terms officer = service.terms(Caller.user(POLICY.profile("export-officer")), "Schraube");
        assertThat(officer.q()).isEqualTo("Schraube");
        assertThat(officer.terms()).singleElement().extracting(Json.Term::iri).isEqualTo(screw.getURI());
        assertThat(officer.terms().get(0).parts()).extracting(Json.TermPart::id, Json.TermPart::product)
                .containsExactly(tuple("fr-screw", "fastening"), tuple("de-screw", "fastening"),
                        tuple("uk-screw", "fastening"), tuple("uk-spare-screw", "fastening"));
        assertThat(officer.provenance().calls()).filteredOn(c -> c.requests() > 0).extracting(c -> c.endpoint(), c -> c.requests())
                .as("the labels graph, then the memberships and the tags; no PLM")
                .containsExactlyInAnyOrder(tuple("neptune", 1), tuple("ontop-core", 3));

        Json.Terms de = service.terms(Caller.user(POLICY.profile("de-engineer")), "vis");
        assertThat(de.terms().get(0).parts()).extracting(Json.TermPart::id).as("the spare screw is releasable to UK only")
                .containsExactly("fr-screw", "de-screw", "uk-screw");
    }

    @Test
    void aTextThatIsNotATermIsRefused() {
        QueryService service = new QueryService(new FixtureFederator(), new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));
        for (String q : new String[] {"", "  ", "gear}", "<x>", "a".repeat(65)}) {
            assertThatThrownBy(() -> service.terms(Caller.user(POLICY.profile("export-officer")), q)).as(q)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void aTermFindsTheItemsOfItsNarrowerTermsAndSaysWhichOne() {
        TermsMapper mapper = new TermsMapper(released());
        Json.Term seal = mapper.resolve("seal").get(0);
        assertThat(seal.iri()).isEqualTo("https://example.com/atelier/term/seal");
        assertThat(seal.parts()).filteredOn(p -> List.of("D-33009", "UK-3306", "ES-3308").contains(p.id()))
                .as("the steam engine's three O-rings, each by its own site's word").extracting(Json.TermPart::id, Json.TermPart::matched,
                        Json.TermPart::narrower)
                .containsExactlyInAnyOrder(tuple("D-33009", new Json.TermLabel("de", "O-Ring"), "O-ring"),
                        tuple("UK-3306", new Json.TermLabel("en", "O-ring"), "O-ring"),
                        tuple("ES-3308", new Json.TermLabel("es", "junta"), null));
        assertThat(seal.parts()).filteredOn(p -> p.id().equals("FR3816")).singleElement().as("a lid gasket is a seal")
                .satisfies(p -> assertThat(p.narrower()).isEqualTo("gasket"));

        Json.Term gear = mapper.resolve("landing gear").get(0);
        assertThat(gear.parts()).extracting(Json.TermPart::id).contains("FR-ORN-PAT-L-001", "FR-ORN-PAT-R-001");
        assertThat(gear.parts()).filteredOn(p -> p.id().equals("FR-ORN-PAT-L-001")).singleElement()
                .satisfies(p -> assertThat(p.narrower()).isEqualTo("landing skid"));

        List<String> fasteners = mapper.resolve("fastener").get(0).parts().stream().map(Json.TermPart::id).toList();
        assertThat(fasteners).as("screws, bolts, nuts, washers and rivets of every site")
                .contains("D-38014", "FR3811", "UK-3823", "ES-3809", "ES-3828", "FR3817", "ES-3830", "D-34009");
        assertThat(fasteners).as("the screw of an aerial screw is the aerial screw").doesNotContain("BAUS-70000", "KIT-7000",
                "FR-VIS-KIT-001", "CONJ-7000");
    }
}
