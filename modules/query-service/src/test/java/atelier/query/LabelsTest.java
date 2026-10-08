// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.federation.Federator;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.validation.RuleValidator;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

/**
 * The English names of the labels graph (fixtures/labels.ttl) in the parts and bill-of-materials answers: each item
 * carries its native name and its English name; the link store is asked about the visible items only, so the names of an
 * item the viewer may not see never leave it, and the interfaces answer asks for none.
 */
class LabelsTest {
    static final Policy POLICY = EvidenceTest.POLICY;
    static final Caller OFFICER = Caller.user(POLICY.profile("export-officer"));
    static final Caller DE = Caller.user(POLICY.profile("de-engineer"));

    private final FixtureFederator federator = new FixtureFederator(fixture()) {
        @Override
        public Result bom(Policy.Profile profile, String product) {
            Result run = super.bom(profile, product);
            results.add(run);
            return run;
        }
    };
    private final QueryService service = new QueryService(federator, new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));

    static Model fixture() {
        Model model = FixtureFederator.fixture();
        RDFDataMgr.read(model, "fixtures/purchasing.ttl");
        RDFDataMgr.read(model, "fixtures/bom.ttl");
        RDFDataMgr.read(model, "fixtures/labels.ttl");
        return model;
    }

    private static Json.Part part(Json.PartsResponse answer, String id) {
        return answer.parts().stream().filter(Json.Part.class::isInstance).map(Json.Part.class::cast)
                .filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void thePartsAnswerCarriesTheEnglishNameBesideTheNativeOne() {
        Json.PartsResponse answer = service.parts(OFFICER, "fastening");

        assertThat(part(answer, "de-screw").name()).isEqualTo("Zylinderschraube DIN 912 M3 x 10");
        assertThat(part(answer, "de-screw").nameEn()).isEqualTo("Socket head cap screw M3 x 10");
        assertThat(part(answer, "fr-screw").nameEn()).isEqualTo("Socket head cap screw M3 x 10");
        assertThat(part(answer, "uk-screw").nameEn()).as("a UK item's native name is English").isEqualTo(part(answer, "uk-screw").name());
        assertThat(part(answer, "es-screw").nameEn()).as("no name in the labels graph").isNull();
    }

    @Test
    void theLinkStoreIsAskedForTheNamesOfTheVisibleItemsOnly() {
        service.parts(DE, "fastening");
        String layer = federator.last().queries().get("neptune").get(2);
        assertThat(layer).contains("graph/labels").contains("de-screw>").doesNotContain("uk-spare-screw");

        service.bom(DE, "drivetrain");
        Federator.Result run = federator.last();
        String bomLayer = run.queries().get("neptune").get(2);
        assertThat(bomLayer).contains("de-gearbox>").doesNotContain("fr-hinge").doesNotContain("fr-cover-set");
        assertThat(run.model().listStatements(run.model().createResource("https://example.com/atelier/fr/part/fr-hinge"), null,
                (org.apache.jena.rdf.model.RDFNode) null).toList()).extracting(s -> s.getPredicate().getURI())
                .as("nothing of the hidden hinge's names reached the merged graph").doesNotContain(Atelier.PREF_LABEL, Atelier.ALT_LABEL);
    }

    @Test
    void theBillOfMaterialsCarriesTheEnglishNameOfEachNode() {
        Json.Bom bom = service.bom(OFFICER, "drivetrain");
        Json.BomNode de = bom.root().children().stream().map(Json.BomNode.class::cast).filter(n -> "de".equals(n.plm())).findFirst().orElseThrow();
        Json.BomNode gearbox = de.children().stream().map(Json.BomNode.class::cast).filter(n -> n.id().equals("de-gearbox")).findFirst().orElseThrow();

        assertThat(de.name()).isEqualTo("Antriebsbausatz");
        assertThat(de.nameEn()).isEqualTo("Drive kit");
        assertThat(gearbox.nameEn()).isEqualTo("Gearbox");
        assertThat(bom.root().nameEn()).as("the product root is the layer's, named by the core").isNull();
    }

    @Test
    void theInterfacesAnswerAsksTheLinkStoreForNoNames() {
        service.interfaces(Caller.user(POLICY.profile("export-officer")));
        List<String> links = federator.last().queries().get("neptune");
        assertThat(links).hasSize(2).noneMatch(q -> q.contains("graph/labels"));
    }
}
