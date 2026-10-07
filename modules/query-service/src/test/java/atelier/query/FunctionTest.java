// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import atelier.query.api.FunctionJson;
import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.federation.UnknownItem;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.validation.RuleValidator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * The flow and path answers over the product files as the sources hold them ({@link FunctionFixture}): the walk along
 * the functional edges both ways, the gear ratio along it, the rated-speed check and the meshModule finding, and the
 * connectivity paths with the redaction of hidden parts.
 */
class FunctionTest {
    static final Policy POLICY = EvidenceTest.POLICY;
    static final Caller OFFICER = Caller.user(POLICY.profile("export-officer"));
    static final Caller FR = Caller.user(POLICY.profile("fr-engineer"));

    private static final Map<String, QueryService> SERVICES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final RuleValidator VALIDATOR = new RuleValidator();

    private static QueryService service(String product) {
        return SERVICES.computeIfAbsent(product, key -> new QueryService(new FixtureFederator(FunctionFixture.of(key)), VALIDATOR,
                new NoOntopSql(), new CadUrls(null, null)));
    }

    private static List<String> reached(FunctionJson.Flow flow) {
        return flow.parts().stream().map(Json.Part::id).toList();
    }

    private static Json.Part part(FunctionJson.Flow flow, String id) {
        return flow.parts().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void theCartsLeftDriveSpringDrivesBothWheelsThroughTheSeededInterfaces() {
        FunctionJson.Flow flow = service("cart").flow(OFFICER, "cart", "FR3101", null, false);
        assertThat(reached(flow)).contains("D-31001", "D-31003", "D-31011", "ES-3102", "ES-3103", "FR3105")
                .doesNotContain("FR3102", "D-31002", "D-31004");
        assertThat(flow.steps()).extracting(s -> s.joint() == null ? null : s.joint().id()).contains("IF-100", "IF-116", "IF-102", "IF-103");
        assertThat(flow.ratio()).as("one mesh: the crown wheel's 30 teeth drive the lantern pinion's 6").isNotNull();
        assertThat(flow.ratio().ratio()).isCloseTo(0.2, within(1e-9));
        assertThat(flow.ratio().speedUp()).isCloseTo(5.0, within(1e-9));
        assertThat(flow.ratio().stages()).singleElement().satisfies(s -> {
            assertThat(s.driver().id()).isEqualTo("D-31001");
            assertThat(s.driver().teeth()).isEqualTo(30);
            assertThat(s.driven().teeth()).isEqualTo(6);
            assertThat(s.driven().moduleMm()).isCloseTo(20.0, within(1e-9));
        });
        assertThat(flow.checks()).as("the cart records no rated speed").isEmpty();
    }

    @Test
    void theWindTurbinesGearRatioFromTheHubIsTheDesignRatioAndTheGeneratorSpeedChecks() {
        FunctionJson.Flow flow = service("wind-turbine").flow(OFFICER, "wind-turbine", "ES-3701", "mechanical", false);
        assertThat(flow.ratio().to()).as("the last gear of the train").isEqualTo("D-37031");
        assertThat(flow.ratio().stages()).extracting(s -> s.driven().id()).containsExactly("D-37023", "D-37027", "D-37031");
        assertThat(flow.ratio().stages().get(0).reaction().id()).as("the planetary stage reacts on the ring").isEqualTo("D-37018");
        assertThat(flow.ratio().stages().get(0).ratio()).isCloseTo(22.0 / 121.0, within(1e-12));
        assertThat(flow.ratio().speedUp()).as("the product file's design ratio from teeth").isCloseTo(97.02, within(1e-9));
        assertThat(flow.ratio().text()).contains("97.02").contains("DE gears");
        assertThat(flow.checks()).singleElement().satisfies(c -> {
            assertThat(c.from()).isEqualTo("ES-3701");
            assertThat(c.to()).isEqualTo("UK-3702");
            assertThat(c.predictedRpm()).isCloseTo(12.1 * 97.02, within(1e-6));
            assertThat(c.holds()).isTrue();
        });
        assertThat(reached(flow)).as("mechanical only: the walk stops before the stator").contains("UK-3703").doesNotContain("UK-3704");
    }

    @Test
    void theHighSpeedPinionWithAnotherModuleIsAMeshModuleFindingOnTheDrivenGear() {
        FunctionJson.Flow flow = service("wind-turbine").flow(OFFICER, "wind-turbine", "D-37028", null, false);
        assertThat(part(flow, "D-37031").findings()).extracting(Json.Finding::rule).containsOnlyOnce("meshModule");
        Json.Finding finding = part(flow, "D-37031").findings().stream().filter(f -> f.rule().equals("meshModule")).findFirst().orElseThrow();
        assertThat(finding.message()).contains("DE D-37031").contains("module 18.0 mm").contains("D-37028").contains("module 20.0 mm");
        assertThat(finding.value()).isEqualTo(new Json.RowRef("de", "part", "D-37028"));
        assertThat(part(flow, "D-37030").findings()).as("a shaft is no gear").extracting(Json.Finding::rule).doesNotContain("meshModule");
        assertThat(service("wind-turbine").flow(OFFICER, "wind-turbine", "D-37024", null, false).parts().stream()
                .filter(p -> "D-37027".equals(p.id())).findFirst().orElseThrow().findings()).as("20 mm meshes 20 mm")
                .extracting(Json.Finding::rule).doesNotContain("meshModule");
    }

    @Test
    void upstreamTheRearSteeringWheelTracesBackToBothDriveSprings() {
        FunctionJson.Flow flow = service("cart").flow(OFFICER, "cart", "FR3105", null, true);
        assertThat(flow.direction()).isEqualTo("up");
        assertThat(reached(flow)).contains("FR3104", "D-31007", "D-31008", "FR3101", "FR3102");
        assertThat(flow.ratio()).as("ratios are stated downstream").isNull();
    }

    @Test
    void aFlowKindFiltersTheEdgesAndALoopEndsTheWalk() {
        FunctionJson.Flow steam = service("steam-engine").flow(OFFICER, "steam-engine", "ES-3301", "steam", false);
        assertThat(reached(steam)).contains("ES-3302", "D-33001", "D-33002", "ES-3305", "UK-3304").doesNotContain("D-33003", "UK-3301");
        assertThat(steam.steps()).allSatisfy(s -> assertThat(s.flow()).isEqualTo("steam"));
        FunctionJson.Flow all = service("steam-engine").flow(OFFICER, "steam-engine", "ES-3301", null, false);
        assertThat(reached(all)).contains("UK-3301", "UK-3303", "UK-3305");
        assertThat(all.steps()).as("the governor's edge back to the throttle closes the loop once")
                .filteredOn(s -> s.to() instanceof Json.PartRef r && r.id().equals("ES-3302")).hasSize(2);
        assertThatThrownBy(() -> service("steam-engine").flow(OFFICER, "steam-engine", "ES-3301", "pneumatic", false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown flow pneumatic");
    }

    @Test
    void aHiddenGearStopsTheWalkAndTheRatioForThatProfile() {
        FunctionJson.Flow flow = service("wind-turbine").flow(FR, "wind-turbine", "ES-3701", "mechanical", false);
        assertThat(reached(flow)).contains("D-37020").doesNotContain("D-37023", "D-37024", "UK-3702");
        assertThat(flow.steps()).extracting(FunctionJson.Step::to).contains(new Json.Redacted(true, "de"));
        assertThat(flow.steps()).extracting(FunctionJson.Step::reaction).filteredOn(Objects::nonNull)
                .containsOnly(new Json.Redacted(true, "de"));
        assertThat(flow.ratio()).isNull();
        assertThat(flow.notes()).anySatisfy(n -> assertThat(n).contains("not visible to your profile"));
        assertThat(List.of(flow.steps(), flow.parts(), flow.notes()).toString()).as("the hidden sun and ring are never named")
                .doesNotContain("D-37023", "D-37018");
    }

    @Test
    void aConnectivityPathNamesEveryInterfaceWithItsStatus() {
        FunctionJson.Paths paths = service("cart").paths(OFFICER, "cart", "D-31003", "UK-3102");
        assertThat(paths.paths()).hasSize(2).allSatisfy(p -> assertThat(p.length()).isEqualTo(5));
        assertThat(paths.paths()).extracting(p -> p.steps().stream().map(s -> s.joint().id()).toList()).containsExactly(
                List.of("IF-116", "IF-102", "IF-106", "IF-105", "IF-108"), List.of("IF-116", "IF-103", "IF-107", "IF-105", "IF-108"));
        FunctionJson.Step first = paths.paths().get(0).steps().get(0);
        assertThat(first.from()).isEqualTo(new Json.PartRef("D-31003", "de", null));
        assertThat(first.to()).isEqualTo(new Json.PartRef("D-31011", "de", null));
        assertThat(first.joint().status()).isIn("pass", "fail");
        assertThat(paths.parts()).extracting(Json.Part::id).contains("D-31003", "ES-3101", "UK-3102");
        assertThat(paths.notes()).isEmpty();
    }

    @Test
    void theDriveSpringAndTheWheelShareNoInterfacePathThoughTheSpringDrivesTheWheel() {
        FunctionJson.Paths paths = service("cart").paths(OFFICER, "cart", "FR3101", "ES-3104");
        assertThat(paths.paths()).isEmpty();
        assertThat(paths.notes()).singleElement().satisfies(n -> assertThat(n).startsWith("No path of at most 12 steps"));
        assertThat(service("cart").flow(OFFICER, "cart", "FR3101", null, false).parts()).extracting(Json.Part::id).contains("ES-3104");
    }

    @Test
    void aHiddenEndAndAnUnknownPartAreReported() {
        FunctionJson.Paths hidden = service("wind-turbine").paths(FR, "wind-turbine", "D-37023", "D-37001");
        assertThat(hidden.from()).isEqualTo(new Json.Redacted(true, "de"));
        assertThat(hidden.paths()).isEmpty();
        assertThatThrownBy(() -> service("cart").paths(OFFICER, "cart", "FR3101", "XX-0000")).isInstanceOf(UnknownItem.class);
    }
}
