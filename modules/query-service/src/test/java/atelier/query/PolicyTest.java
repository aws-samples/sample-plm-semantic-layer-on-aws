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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Export control over fixtures/neighbourhood.ttl: the FR control post is tagged releasable to FR, the DE
 * tail drive to licensed recipients, the ES tail plane carries no tag, every other part is tagged EU. The
 * {@link FixtureFederator} runs every request against the fixture, so visibility is decided as
 * live: by the core request's FILTER, the parts each PLM is asked about, and the in-process
 * redaction; these tests check the answer shapes that produces.
 */
class PolicyTest {
    static final String TAIL_PLANE = "https://example.com/atelier/es/part/es-tail-plane";
    static final Json.Part DE_RIGHT_ROOT_FITTING = new Json.Part("de-right-root-fitting", "de", "Right wing root fitting",
            "cad/ornithopter/de-right-root-fitting.stp", null, "CAD/de-right-root-fitting.prt", "EU-DUAL-USE", "EU", "DE", "2026-09-01T08:00:00Z", null, List.of(), null, null, null, null, null, null);

    private final Policy policy = EvidenceTest.POLICY;
    private final FixtureFederator federator = new FixtureFederator();
    private final QueryService service = new QueryService(federator, new RuleValidator(), new NoOntopSql(),
            new CadUrls(null, null));

    @Test
    void deEngineerCannotEvaluateTheControlPostOrTheTailDriveInterfaces() {
        Map<String, Json.Interface> byId = interfaces("de-engineer");

        Json.Interface if01 = byId.get("IF-01");
        assertThat(if01.status()).isEqualTo("not-evaluable");
        assertThat(if01.violations()).isEmpty();
        assertThat(if01.parts()).containsExactly(DE_RIGHT_ROOT_FITTING, new Json.Redacted(true, "fr"));
        assertThat(if01.features()).hasSize(2);
        assertThat(if01.features().get(0)).isEqualTo(new Json.Redacted(true, "fr"));
        Json.Feature p11 = (Json.Feature) if01.features().get(1);
        assertThat(p11.id()).isEqualTo("P11");
        assertThat(p11.matesWith()).as("the hidden mate's id does not leak").containsExactly(new Json.Redacted(true, "fr"));

        Json.Interface if07 = byId.get("IF-07");
        assertThat(if07.status()).isEqualTo("not-evaluable");
        assertThat(if07.violations()).as("the orphan P71 is on the hidden tail drive").isEmpty();
        assertThat(if07.parts()).extracting(p -> p instanceof Json.Redacted r ? "redacted " + r.plm() : ((Json.Part) p).id())
                .containsExactly("redacted de", "es-tail-boom");
        assertThat(if07.features()).filteredOn(f -> f instanceof Json.Redacted).hasSize(2);
        Json.Feature j72 = (Json.Feature) if07.features().stream().filter(f -> f instanceof Json.Feature).findFirst().orElseThrow();
        assertThat(j72.id()).isEqualTo("J72");
        assertThat(j72.matesWith()).containsExactly(new Json.Redacted(true, "de"));

        List<Json.PartView> parts = service.parts(Caller.user(policy.profile("de-engineer"))).parts();
        assertThat(parts).filteredOn(p -> p instanceof Json.Part).hasSize(6)
                .allMatch(p -> ((Json.Part) p).jurisdiction() != null && ((Json.Part) p).releasableTo() != null
                        && ((Json.Part) p).taggedBy() != null && ((Json.Part) p).taggedAt() != null);
        assertThat(parts.subList(6, 9)).as("one marker per hidden part, in IRI order")
                .containsExactly(new Json.Redacted(true, "de"), new Json.Redacted(true, "es"), new Json.Redacted(true, "fr"));

        assertThat(byId.values()).filteredOn(i -> !List.of("IF-01", "IF-07", "IF-08").contains(i.id()))
                .extracting(Json.Interface::id, Json.Interface::status).containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("IF-02", "fail"), org.assertj.core.groups.Tuple.tuple("IF-03", "fail"),
                        org.assertj.core.groups.Tuple.tuple("IF-04", "fail"), org.assertj.core.groups.Tuple.tuple("IF-05", "pass"),
                        org.assertj.core.groups.Tuple.tuple("IF-06", "fail"));
    }

    @Test
    void untaggedPartIsHiddenFromEveryoneButTheExportOfficerAndReported() {
        Json.InterfacesResponse de = service.interfaces(Caller.user(policy.profile("de-engineer")));
        assertThat(de.policy().untagged()).containsExactly(TAIL_PLANE);
        Json.Interface if08 = de.interfaces().stream().filter(i -> i.id().equals("IF-08")).findFirst().orElseThrow();
        assertThat(if08.status()).isEqualTo("not-evaluable");
        assertThat(if08.parts()).extracting(p -> p instanceof Json.Redacted r ? "redacted " + r.plm() : ((Json.Part) p).id())
                .containsExactly("es-tail-boom", "redacted es");
        assertThat(if08.features()).hasSize(2);
        Json.Feature j82 = (Json.Feature) if08.features().stream().filter(f -> f instanceof Json.Feature).findFirst().orElseThrow();
        assertThat(j82.id()).isEqualTo("J82");
        assertThat(j82.matesWith()).containsExactly(new Json.Redacted(true, "es"));
        assertThat(service.parts(Caller.user(policy.profile("programme-cleared"))).policy().untagged()).containsExactly(TAIL_PLANE);
        assertThat(service.evidence("IF-08", Caller.user(policy.profile("de-engineer"))).orElseThrow().policy().untagged()).containsExactly(TAIL_PLANE);

        Json.InterfacesResponse officer = service.interfaces(Caller.user(policy.profile("export-officer")));
        assertThat(officer.policy().untagged()).as("the missing tag is reported to the officer too").containsExactly(TAIL_PLANE);
        Json.Interface if08Officer = officer.interfaces().stream().filter(i -> i.id().equals("IF-08")).findFirst().orElseThrow();
        assertThat(if08Officer.status()).isEqualTo("pass");
        assertThat(if08Officer.parts()).containsExactly(
                new Json.Part("es-tail-boom", "es", "Tail boom", "cad/ornithopter/es-tail-boom.stp", null, "CAD/es-tail-boom.sldprt",
                        "EU-DUAL-USE", "EU", "ES", "2026-09-01T08:00:00Z", null, List.of(), null, null, null, null, null, null),
                new Json.Part("es-tail-plane", "es", "Tail plane", "cad/ornithopter/es-tail-plane.stp", null,
                        "CAD/es-tail-plane.sldprt", null, null, null, null, null, List.of(), null, null, null, null, null, null));
    }

    @Test
    void frEngineerSeesTheControlPostButNotTheFin() {
        Map<String, Json.Interface> byId = interfaces("fr-engineer");
        assertThat(byId.get("IF-01").status()).isEqualTo("pass");
        assertThat(byId.get("IF-07").status()).isEqualTo("not-evaluable");
    }

    @Test
    void exportOfficerSeesEverything() {
        Map<String, Json.Interface> byId = interfaces("export-officer");
        assertThat(byId.values()).extracting(Json.Interface::status).doesNotContain("not-evaluable");
        assertThat(byId.get("IF-07").violations()).extracting(Json.Violation::rule).containsExactly("orphan");
        assertThat(byId.get("IF-07").features()).filteredOn(f -> ((Json.Feature) f).id().equals("J72"))
                .extracting(f -> ((Json.Feature) f).matesWith()).containsExactly(List.of("P72"));
        assertThat(service.parts(Caller.user(policy.profile("export-officer"))).parts()).hasSize(9)
                .allMatch(p -> p instanceof Json.Part);
    }

    @Test
    void programmeClearedSeesEveryTaggedNationalPartButNotTheLicensedTailDrive() {
        Map<String, Json.Interface> byId = interfaces("programme-cleared");
        assertThat(byId.get("IF-01").status()).isEqualTo("pass");
        assertThat(byId.get("IF-07").status()).isEqualTo("not-evaluable");
        assertThat(byId.get("IF-08").status()).isEqualTo("not-evaluable");
        assertThat(service.parts(Caller.user(policy.profile("programme-cleared"))).parts()).hasSize(9)
                .filteredOn(p -> p instanceof Json.Redacted).containsExactly(new Json.Redacted(true, "de"), new Json.Redacted(true, "es"));
    }

    @Test
    void unknownProfileSeesOnlyPartsReleasableToAll() {
        Policy.Profile unknown = policy.profile(null);
        assertThat(unknown.name()).isEqualTo("unknown");
        assertThat(policy.profile("nobody")).isSameAs(unknown);
        assertThat(unknown.releasable()).containsExactly("ALL");
        assertThat(unknown.seesUntagged()).isFalse();

        assertThat(service.parts(Caller.user(unknown)).parts()).hasSize(9).allMatch(p -> p instanceof Json.Redacted);
        assertThat(interfaces(null).values()).extracting(Json.Interface::status).containsOnly("not-evaluable");
    }

    @Test
    void everyAnswerCarriesThePolicyAndTheQueryCarriesItsFilter() {
        Policy.Profile de = policy.profile("de-engineer");
        Json.Policy expected = new Json.Policy("de-engineer", List.of("ALL", "EU", "DE"),
                "FILTER (?rel IN (\"ALL\", \"EU\", \"DE\"))", List.of(TAIL_PLANE), "user");
        Json.Policy expectedIf02 = new Json.Policy("de-engineer", List.of("ALL", "EU", "DE"), expected.filter(), List.of(), "user");

        assertThat(service.interfaces(Caller.user(de)).policy()).isEqualTo(expected);
        assertThat(federator.last().sparql().split(Pattern.quote(expected.filter()), -1)).as("the core request only").hasSize(2);
        assertThat(service.interfaceById("IF-02", Caller.user(de)).orElseThrow().policy()).as("IF-02 names two tagged parts").isEqualTo(expectedIf02);
        assertThat(service.evidence("IF-02", Caller.user(de)).orElseThrow().policy()).isEqualTo(expectedIf02);
        assertThat(service.parts(Caller.user(de)).policy()).isEqualTo(expected);
        assertThat(federator.last().sparql().split(Pattern.quote(expected.filter()), -1)).hasSize(2);
    }

    @Test
    void redactedSidesLeaveNoTraceInTheValidatedGraph() {
        Json.Evidence evidence = service.evidence("IF-01", Caller.user(policy.profile("de-engineer"))).orElseThrow();
        assertThat(evidence.merged().turtle()).as("the hidden part's facts, tag, CAD file and its plug's values are gone")
                .doesNotContain("Control post").doesNotContain("NATIONAL-FR").doesNotContain("fr-control-post.stp")
                .doesNotContain("Ateliers du Clos Lucé, Amboise").doesNotContain("J11/position")
                .as("the link store's mention of the hidden plug stays").contains("fr/plug/J11");
        assertThat(evidence.shacl().report()).doesNotContain("sh:ValidationResult");
    }

    @Test
    void redactedEntriesSerialiseAsTheContractStates() throws Exception {
        Json.Interface if01 = interfaces("de-engineer").get("IF-01");
        String json = new ObjectMapper().writeValueAsString(if01);
        assertThat(json).contains("\"parts\":[{\"id\":\"de-right-root-fitting\"")
                .contains("\"cadFile\":\"cad/ornithopter/de-right-root-fitting.stp\",\"cadUrl\":null,\"sourceFileRef\":\"CAD/de-right-root-fitting.prt\"")
                .contains("\"taggedBy\":\"DE\",\"taggedAt\":\"2026-09-01T08:00:00Z\"},{\"redacted\":true,\"plm\":\"fr\"}]")
                .contains("\"features\":[{\"redacted\":true,\"plm\":\"fr\"},{\"id\":\"P11\"")
                .contains("\"matesWith\":[{\"redacted\":true,\"plm\":\"fr\"}]")
                .contains("\"status\":\"not-evaluable\"")
                .as("the redacted control post never exposes its supplier; the right root fitting has none").doesNotContain("supplier");
    }

    @Test
    void supplierSerialisesOnlyOnASupplierBuiltPartTheViewerMaySee() {
        JsonNode parts = new ObjectMapper().valueToTree(interfaces("export-officer").get("IF-01")).path("parts");
        assertThat(parts.get(0).path("id").asText()).isEqualTo("de-right-root-fitting");
        assertThat(parts.get(0).has("supplier")).as("built by the PLM's own plant: no key").isFalse();
        assertThat(parts.get(1).path("id").asText()).isEqualTo("fr-control-post");
        assertThat(parts.get(1).path("supplier").asText()).isEqualTo("Ateliers du Clos Lucé, Amboise");
        assertThat(parts.get(1).path("suppliers").toString()).isEqualTo("[{\"name\":\"Ateliers du Clos Lucé, Amboise\",\"role\":\"built\"}]");
        assertThat(parts.get(1).fieldNames()).toIterable().endsWith("taggedAt", "supplier", "suppliers");
    }

    private Map<String, Json.Interface> interfaces(String profile) {
        return service.interfaces(Caller.user(policy.profile(profile))).interfaces().stream()
                .collect(Collectors.toMap(Json.Interface::id, i -> i));
    }
}
