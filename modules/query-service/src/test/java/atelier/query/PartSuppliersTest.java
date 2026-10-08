// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.policy.Caller;
import atelier.query.validation.RuleValidator;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;

/**
 * A part's suppliers in the parts answer, over fixtures/purchasing.ttl: every supplier that built it and every supplier
 * with an offer for it, each marked by its role and named "name, town" as the file index names a builder.
 */
class PartSuppliersTest {
    static final Caller CLEARED = Caller.user(EvidenceTest.POLICY.profile("programme-cleared"));
    static final String DE = "https://example.com/atelier/de/part/";

    private static Json.Part part(Model fixture, String id) {
        QueryService service = new QueryService(new FixtureFederator(fixture), new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));
        return service.parts(CLEARED, PurchasingTest.PRODUCT).parts().stream().filter(p -> p instanceof Json.Part q && q.id().equals(id))
                .map(Json.Part.class::cast).findFirst().orElseThrow();
    }

    @Test
    void aPartWithTwoBuildersListsBoth() {
        Model fixture = PurchasingTest.fixture();
        fixture.createResource(DE + "de-nut").addProperty(fixture.createProperty(Atelier.ONT + "builtBy"), "Zinnkraut Antriebstechnik GmbH, Regensburg")
                .addProperty(fixture.createProperty(Atelier.ONT + "builtBy"), "Ampferhain Lager GmbH, Kassel");
        Json.Part nut = part(fixture, "de-nut");
        assertThat(nut.suppliers()).containsExactly(
                new Json.PartSupplier("Ampferhain Lager GmbH, Kassel", "built"),
                new Json.PartSupplier("Zinnkraut Antriebstechnik GmbH, Regensburg", "built"),
                new Json.PartSupplier("Quellmoos Normteile KG, Schwäbisch Hall", "offered"));
        assertThat(nut.supplier()).as("the supplier-built mark: the first builder by name").isEqualTo("Ampferhain Lager GmbH, Kassel");
    }

    @Test
    void aPartWithSeveralOffersListsEveryOfferingSupplier() {
        Json.Part bearing = part(PurchasingTest.fixture(), "de-bearing");
        assertThat(bearing.suppliers()).containsExactly(
                new Json.PartSupplier("Ampferhain Lager GmbH, Kassel", "offered"),
                new Json.PartSupplier("Quellmoos Normteile KG, Schwäbisch Hall", "offered"),
                new Json.PartSupplier("Zinnkraut Antriebstechnik GmbH, Regensburg", "offered"));
        assertThat(bearing.supplier()).as("offered, never built").isNull();
    }
}
