// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.federation.UnknownItem;
import atelier.query.validation.RuleValidator;
import java.util.List;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

/**
 * The parts and placements answers of a product under one option of a variant group ({@code ?option=}), over
 * fixtures/variants.ttl and its options graph: the configuration the variant diff validates, redacted for the profile,
 * the default option's being the base product.
 */
class ConfigurationAnswersTest {
    private final FixtureFederator federator = new FixtureFederator(VariantDiffTest.fixture(), RDFDataMgr.loadModel("fixtures/variants-options.ttl"));
    private final QueryService service = new QueryService(federator, new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));

    private static List<String> visible(Json.PartsResponse answer) {
        return answer.parts().stream().filter(Json.Part.class::isInstance).map(p -> ((Json.Part) p).id()).sorted().toList();
    }

    private static List<String> placed(Json.Placements answer) {
        return answer.parts().stream().map(Json.PartPlacements::id).sorted().toList();
    }

    @Test
    void theOptionsPartsReplaceTheDefaultOptionsAndTheDefaultIsTheBase() {
        List<String> base = visible(service.parts(VariantDiffTest.PROGRAMME, "slot", null, null));
        assertThat(base).contains("ES-ARM", "ES-PEDAL", "FR-FRAME").doesNotContain("UK-SPRING", "DE-ANCHOR");
        assertThat(visible(service.parts(VariantDiffTest.PROGRAMME, "slot", null, "cord"))).isEqualTo(base);
        assertThat(visible(service.parts(VariantDiffTest.PROGRAMME, "slot", null, "spring")))
                .contains("UK-SPRING", "DE-ANCHOR", "ES-PEDAL", "FR-FRAME").doesNotContain("ES-ARM");
    }

    @Test
    void theOptionsPlacementsComposeDownItsOwnTrees() {
        assertThat(placed(service.placements(VariantDiffTest.PROGRAMME, "slot", null, null))).containsExactly("ES-ARM", "ES-PEDAL", "FR-FRAME");
        assertThat(placed(service.placements(VariantDiffTest.PROGRAMME, "slot", null, "cord"))).containsExactly("ES-ARM", "ES-PEDAL", "FR-FRAME");
        Json.Placements spring = service.placements(VariantDiffTest.PROGRAMME, "slot", null, "spring");
        assertThat(placed(spring)).containsExactly("DE-ANCHOR", "ES-PEDAL", "FR-FRAME", "UK-SPRING");
        assertThat(spring.occurrences()).as("the option's line places its two springs").isEqualTo(5);
        Json.PartPlacements springs = spring.parts().stream().filter(p -> p.id().equals("UK-SPRING")).findFirst().orElseThrow();
        assertThat(springs.occurrences()).containsExactly(List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0), List.of(254.0, 0.0, 0.0, 0.0, 0.0, 180.0));
    }

    @Test
    void anOptionPartTheProfileMayNotSeeIsRedactedAsTheBaseRedactsIt() {
        Json.PartsResponse parts = service.parts(VariantDiffTest.FR, "slot", null, "spring");
        assertThat(visible(parts)).contains("UK-SPRING").doesNotContain("DE-ANCHOR", "ES-ARM");
        assertThat(placed(service.placements(VariantDiffTest.FR, "slot", null, "spring"))).doesNotContain("DE-ANCHOR").contains("UK-SPRING");
    }

    @Test
    void anUnknownOptionIsNotFoundAndAnOptionWithASubtreeOrWithoutAProductIsRefused() {
        assertThatThrownBy(() -> service.parts(VariantDiffTest.PROGRAMME, "slot", null, "rocket"))
                .isInstanceOf(UnknownItem.class).hasMessage("no item option rocket in slot; its variant groups: return (cord, spring)");
        assertThatThrownBy(() -> service.placements(VariantDiffTest.PROGRAMME, "slot", "ES-KIT", "spring"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.parts(VariantDiffTest.PROGRAMME, null, null, "spring"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
