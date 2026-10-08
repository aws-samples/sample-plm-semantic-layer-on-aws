// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.policy.Caller;
import atelier.query.validation.RuleValidator;
import java.util.List;
import java.util.stream.Stream;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;

/** Interface ids list in reading order: the number in the id, not its text, orders IF-99 before IF-100. */
class InterfaceOrderTest {
    static final Caller OFFICER = Caller.user(EvidenceTest.POLICY.profile("export-officer"));

    @Test
    void digitRunsCompareByValue() {
        List<String> ids = Stream.of("IF-100", "IF-11", "IF-02", "IF-110", "IF-10", "IF-09", "IF-1000", "IF-99")
                .sorted(Atelier.BY_ID).toList();
        assertThat(ids).containsExactly("IF-02", "IF-09", "IF-10", "IF-11", "IF-99", "IF-100", "IF-110", "IF-1000");
        assertThat(Stream.of("FR-B-10", "FR-A-2", "FR-A-10", "FR-A").sorted(Atelier.BY_ID).toList())
                .containsExactly("FR-A", "FR-A-2", "FR-A-10", "FR-B-10");
        assertThat(Atelier.BY_ID.compare("IF-07", "IF-7")).as("equal by value, ordered as text").isLessThan(0);
    }

    @Test
    void theCatalogueListsTwoAndThreeDigitIdsInReadingOrder() {
        Model catalogue = FunctionFixture.of("steam-engine");
        QueryService service = new QueryService(new FixtureFederator(catalogue), new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));
        List<String> ids = service.interfaces(OFFICER).interfaces().stream().map(Json.Interface::id).toList();
        List<Integer> numbers = ids.stream().map(id -> Integer.parseInt(id.replaceAll("\\D+", ""))).toList();
        assertThat(numbers).contains(1, 99, 100).isSorted();
        assertThat(ids.get(ids.lastIndexOf("IF-99") + 1)).isEqualTo("IF-100");
    }
}
