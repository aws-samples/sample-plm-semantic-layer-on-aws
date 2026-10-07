// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.validation.RuleValidator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

/**
 * The lifecycle rule over fixtures/lifecycle.ttl (lifecycle words, lines and references added to the drivetrain of
 * bom.ttl and references.ttl): a RELEASED item that depends on a WORKING or BLOCKED one, through a bill-of-materials
 * line or an external reference, carries a lifecycleConflict finding naming both items, both sites' words and the
 * canonical states; a dependency hidden from the viewer raises nothing.
 */
class LifecycleTest {
    static final Policy POLICY = EvidenceTest.POLICY;
    static final Caller OFFICER = Caller.user(POLICY.profile("export-officer"));
    static final Caller DE = Caller.user(POLICY.profile("de-engineer"));
    static final String RULE = "lifecycleConflict";

    private final QueryService service = new QueryService(new FixtureFederator(fixture()), new RuleValidator(), new NoOntopSql(),
            new CadUrls(null, null));

    static Model fixture() {
        Model model = FixtureFederator.fixture();
        RDFDataMgr.read(model, "fixtures/bom.ttl");
        RDFDataMgr.read(model, "fixtures/references.ttl");
        RDFDataMgr.read(model, "fixtures/lifecycle.ttl");
        return model;
    }

    /** The lifecycleConflict messages of each visible part of the product that carries one, by part id. */
    private Map<String, List<String>> conflicts(Caller caller, String product) {
        return service.parts(caller, product).parts().stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast)
                .filter(p -> p.findings() != null && p.findings().stream().anyMatch(f -> f.rule().equals(RULE)))
                .collect(Collectors.toMap(Json.Part::id, p -> p.findings().stream().filter(f -> f.rule().equals(RULE))
                        .map(Json.Finding::message).toList()));
    }

    @Test
    void aReleasedItemOverAWorkingOrBlockedChildIsAFindingInEachSitesWords() {
        Map<String, List<String>> officer = conflicts(OFFICER, "drivetrain");
        assertThat(officer.get("de-gearbox")).containsExactly(
                "DE de-gearbox Getriebe is Freigegeben (RELEASED) but its bill of materials holds DE de-stage Planetenstufe, which is In Arbeit (WORKING)");
        assertThat(officer.get("uk-kit")).as("the British Frozen is BLOCKED").containsExactly(
                "UK uk-kit Electrical kit is Released (RELEASED) but its bill of materials holds UK uk-motor Pitch motor, which is Frozen (BLOCKED)");
        assertThat(officer.get("fr-kit")).containsExactly(
                "FR fr-kit Kit capotage is Publié (RELEASED) but its bill of materials holds FR fr-cover-set Ensemble capots, which is En cours (WORKING)");
    }

    @Test
    void aReleasedItemReferencingAnUnreleasedPartOfAnotherSiteIsAFinding() {
        Map<String, List<String>> officer = conflicts(OFFICER, "drivetrain");
        assertThat(officer.get("fr-panel")).as("the British native form UK/4102 resolves as the reference rules resolve it").containsExactly(
                "FR fr-panel Panneau is Publié (RELEASED) but references urn:plm:uk:part:UK/4102, UK UK-4102 Pitch controller, which is Draft (WORKING)");
        assertThat(officer.get("de-housing")).containsExactly(
                "DE de-housing Gehäuse is Freigegeben (RELEASED) but references urn:plm:fr:part:fr-cover-set, FR fr-cover-set Ensemble capots, which is En cours (WORKING)");
        assertThat(officer).as("de-gear references a SUPERSEDED and a RELEASED part: neither is a lifecycle conflict")
                .containsOnlyKeys("de-gearbox", "uk-kit", "fr-kit", "fr-panel", "de-housing");
    }

    @Test
    void aConflictNamesTheDependencyPart() {
        Json.Part gearbox = service.parts(OFFICER, "drivetrain").parts().stream().filter(Json.Part.class::isInstance)
                .map(Json.Part.class::cast).filter(p -> p.id().equals("de-gearbox")).findFirst().orElseThrow();
        assertThat(gearbox.findings()).filteredOn(f -> f.rule().equals(RULE)).extracting(Json.Finding::value)
                .containsExactly(new Json.RowRef("de", "part", "de-stage"));
    }

    @Test
    void aDependencyHiddenFromTheViewerRaisesNothing() {
        Map<String, List<String>> de = conflicts(DE, "drivetrain");
        assertThat(de).as("the FR cover set is releasable to FR only: the kit over it and the housing referencing it are not evaluable")
                .containsOnlyKeys("de-gearbox", "uk-kit", "fr-panel");
    }

    @Test
    void theProductsAnswerCountsTheConflictsOfEachProduct() {
        Map<String, Integer> officer = service.products(OFFICER).products().stream()
                .collect(Collectors.toMap(Json.Product::key, Json.Product::lifecycleConflicts));
        assertThat(officer).isEqualTo(Map.of("drivetrain", 5, "ornithopter", 1, "wings", 1, "tail", 0));
        assertThat(service.products(DE).products()).filteredOn(p -> p.key().equals("drivetrain"))
                .extracting(Json.Product::lifecycleConflicts).containsExactly(3);
    }

    @Test
    void anInterfacesNeighbourhoodCarriesTheLinesFromItsParts() {
        Json.Interface iface = service.interfaceById(new InterfaceRef("IF-05", null), OFFICER).orElseThrow()._interface();
        Json.Part beam = iface.parts().stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast)
                .filter(p -> p.id().equals("fr-cross-beam-mid")).findFirst().orElseThrow();
        assertThat(beam.findings()).filteredOn(f -> f.rule().equals(RULE)).extracting(Json.Finding::message).containsExactly(
                "FR fr-cross-beam-mid Mid cross beam (wing root) is Publié (RELEASED) but its bill of materials holds FR fr-beam-insert Insert de longeron, which is En cours (WORKING)");
        Json.Interface plain = new QueryService(new FixtureFederator(), new RuleValidator(), new NoOntopSql(), new CadUrls(null, null))
                .interfaceById(new InterfaceRef("IF-05", null), OFFICER).orElseThrow()._interface();
        assertThat(iface.status()).as("a finding never changes an interface's status").isEqualTo(plain.status());
    }
}
