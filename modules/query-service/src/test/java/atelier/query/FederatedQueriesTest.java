// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.federation.Endpoints;
import atelier.query.federation.FederatedQueries;
import atelier.query.federation.Federator;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.validation.RuleValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.Op1;
import org.apache.jena.sparql.algebra.op.Op2;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpFilter;
import org.apache.jena.sparql.algebra.op.OpJoin;
import org.apache.jena.sparql.algebra.op.OpLeftJoin;
import org.apache.jena.sparql.algebra.op.OpN;
import org.apache.jena.sparql.algebra.op.OpTable;
import org.junit.jupiter.api.Test;

/**
 * The requests a run sends, over fixtures/neighbourhood.ttl: one plain CONSTRUCT per source and
 * concern, about the parts the viewer may see, with texts that depend on the profile alone.
 */
class FederatedQueriesTest {
    private static final Policy POLICY = EvidenceTest.POLICY;
    private static final Policy.Profile DE = POLICY.profile("de-engineer");
    private static final Policy.Profile OFFICER = POLICY.profile("export-officer");
    private static final String CONTROL_POST = Atelier.DATA + "fr/part/fr-control-post";
    private static final String TAIL_DRIVE = Atelier.DATA + "de/part/de-tail-drive";
    private static final String TAIL_PLANE = Atelier.DATA + "es/part/es-tail-plane";
    private static final Pattern PART = Pattern.compile("<(https://example\\.com/atelier/[a-z]+/part/[^>]+)>");

    private final FixtureFederator federator = new FixtureFederator();
    private final QueryService service = new QueryService(federator, new RuleValidator(), new NoOntopSql(),
            new CadUrls(null, null));

    @Test
    void noRequestOfAnyProfileContainsUnionMinusOrBind() {
        for (Policy.Profile profile : POLICY.profiles()) {
            service.interfaces(Caller.user(profile));
            service.parts(Caller.user(profile));
        }
        assertThat(federator.results).hasSize(2 * POLICY.profiles().size());
        int requests = 0;
        for (Federator.Result result : federator.results) {
            for (Map.Entry<String, List<String>> sent : result.queries().entrySet()) {
                for (String text : sent.getValue()) {
                    requests++;
                    assertThat(text).as(sent.getKey())
                            .doesNotContainPattern("(?i)\\bUNION\\b")
                            .doesNotContainPattern("(?i)\\bMINUS\\b")
                            .doesNotContainPattern("(?i)\\bBIND\\s*\\(");
                    assertThat(text).as("one request is one block of the run's text").doesNotContain("\n\n").endsWith("}\n");
                    Query query = QueryFactory.create(text);
                    assertThat(query.isConstructType()).as(sent.getKey()).isTrue();
                    if (Endpoints.sourceOf(sent.getKey()) != null) {
                        assertThat(algebra(Algebra.compile(query), new ArrayList<>()))
                                .as("an Ontop request is one basic graph pattern with VALUES, OPTIONALs and a FILTER: %s", text)
                                .isSubsetOf(OpBGP.class, OpTable.class, OpJoin.class, OpLeftJoin.class, OpFilter.class);
                    }
                }
            }
        }
        assertThat(requests).as("7 profiles: link store and core twice each, plus the PLM requests").isGreaterThan(28);
    }

    @Test
    void requestTextsAreIdenticalAcrossRunsOfOneProfile() {
        for (Policy.Profile profile : POLICY.profiles()) {
            Federator.Result first = service.answer(Caller.user(profile)).federation();
            Federator.Result second = service.answer(new InterfaceRef("IF-05", null), Caller.user(profile)).orElseThrow().federation();
            assertThat(second.queries()).as(profile.name()).isEqualTo(first.queries());
            assertThat(second.sparql()).as(profile.name()).isEqualTo(first.sparql());
            assertThat(federator.parts(profile).queries()).as("parts " + profile.name()).isEqualTo(federator.parts(profile).queries());
        }
    }

    @Test
    void eachPlmReceivesOneRequestPerConcernAboutItsVisiblePartsOnly() {
        service.interfaces(Caller.user(DE));
        Map<String, List<String>> sent = federator.last().queries();

        assertThat(sent.get("neptune")).hasSize(2);
        assertThat(sent.get("neptune").get(0))
                .contains("GRAPH <" + Atelier.LINKS_GRAPH + ">").doesNotContain(Atelier.FILE_INDEX_GRAPH)
                .contains("atelier:declaresFeature|atelier:declaresPlug ?f").contains("rdfs:label ?ifLabel").doesNotContain("VALUES");
        assertThat(sent.get("neptune").get(1)).as("the file index of every part, whether or not an interface names it")
                .contains("GRAPH <" + Atelier.FILE_INDEX_GRAPH + ">").contains("?part atelier:cadFile|atelier:builtBy ?entry .")
                .contains("OPTIONAL { ?part atelier:cadFile ?cad }").contains("OPTIONAL { ?part atelier:builtBy ?supplier }")
                .doesNotContain(Atelier.LINKS_GRAPH).doesNotContain("VALUES");
        assertThat(sent.get("ontop-core")).hasSize(4);
        assertThat(sent.get("ontop-core").get(0)).as("every product membership, so a part no interface names is still a part of its product")
                .contains("?part atelier:partOf ?product .").doesNotContain("VALUES").doesNotContain("FILTER");
        assertThat(sent.get("ontop-core").get(1)).as("who tagged every part, a text that names no part and carries no FILTER")
                .contains("?part atelier:taggedBy ?taggedBy .").doesNotContain("releasableTo").doesNotContain("VALUES").doesNotContain("FILTER");
        assertThat(sent.get("ontop-core").get(2)).as("the tags the profile FILTER releases, on the whole pattern, naming no part")
                .contains(DE.filter()).contains("atelier:releasableTo ?rel").doesNotContain("OPTIONAL").doesNotContain("VALUES");
        assertThat(parts(sent.get("ontop-core").get(1))).isEmpty();
        assertThat(sent.get("ontop-core").get(3)).as("each product's facts once, the memberships having named every member")
                .contains("?product atelier:label ?productName .").contains("OPTIONAL { ?product atelier:frame ?frame }")
                .doesNotContain("?part").doesNotContain("VALUES").doesNotContain("FILTER");
        for (String plm : Atelier.PLMS) {
            List<String> requests = sent.get("ontop-" + plm);
            assertThat(requests).as(plm).hasSize(6);
            assertThat(requests.get(0)).contains("?part a atelier:Part ; atelier:label ?partName .").contains("OPTIONAL { ?part atelier:sourceFileRef ?sourceFileRef }");
            assertThat(requests.get(1)).contains("?f a atelier:Plug ;").contains("atelier:positionX ?qx").contains("OPTIONAL { ?f atelier:pinCount ?pins }");
            assertThat(requests.get(2)).contains("?f a atelier:Fastener ;").contains("atelier:diameter ?qd").contains("atelier:gripLength ?qg");
            assertThat(requests.get(3)).contains("?f a atelier:HydraulicCoupling ;").contains("atelier:pressureRating ?qr").contains("OPTIONAL { ?f atelier:fluid ?fluid }");
            assertThat(requests.get(4)).as("the references the visible parts make, the URN as stored")
                    .contains("?ref a atelier:ExternalReference ; atelier:fromPart ?part ; atelier:remoteUrn ?urn .")
                    .contains("OPTIONAL { ?ref atelier:expectedRevision ?expected }");
            assertThat(requests.get(5)).as("the lines from the visible parts, the dependencies the lifecycle rule reads")
                    .isEqualTo(FederatedQueries.bomLinesFrom(parts(requests.get(0))));
            assertThat(requests.stream().map(FederatedQueriesTest::parts).distinct()).as("the six requests name the same parts").hasSize(1);
            assertThat(requests.get(0)).doesNotContain("IN (");
        }
        assertThat(parts(sent.get("ontop-fr").get(0))).as("the FR control post is releasable to FR only").containsExactly(Atelier.DATA + "fr/part/fr-cross-beam-mid");
        assertThat(parts(sent.get("ontop-de").get(0))).as("the tail drive is EXPORT-LICENCE").doesNotContain(TAIL_DRIVE).hasSize(2);
        assertThat(parts(sent.get("ontop-es").get(0))).as("the untagged tail plane is hidden").containsExactly(Atelier.DATA + "es/part/es-tail-boom");
        assertThat(federator.last().sparql()).isEqualTo(String.join("\n\n", List.of(String.join("\n\n", sent.get("neptune")), String.join("\n\n", sent.get("ontop-core")),
                String.join("\n\n", sent.get("ontop-fr")), String.join("\n\n", sent.get("ontop-de")),
                String.join("\n\n", sent.get("ontop-uk")), String.join("\n\n", sent.get("ontop-es")))));
    }

    @Test
    void theOfficerIsAskedAboutUntaggedPartsAndAProfileWithoutVisiblePartsAsksNoPlm() {
        service.interfaces(Caller.user(OFFICER));
        Map<String, List<String>> officer = federator.last().queries();
        assertThat(parts(officer.get("ontop-es").get(0))).contains(TAIL_PLANE);
        assertThat(parts(officer.get("ontop-de").get(0))).contains(TAIL_DRIVE);
        assertThat(parts(officer.get("ontop-fr").get(0))).contains(CONTROL_POST);

        service.interfaces(Caller.user(POLICY.profile(null)));
        Federator.Result unknown = federator.last();
        for (String plm : Atelier.PLMS) {
            assertThat(unknown.queries().get("ontop-" + plm)).as(plm).isEmpty();
        }
        assertThat(unknown.calls()).extracting(Federator.Call::endpoint, Federator.Call::requests).containsExactly(
                org.assertj.core.groups.Tuple.tuple("ontop-fr", 0), org.assertj.core.groups.Tuple.tuple("ontop-de", 0),
                org.assertj.core.groups.Tuple.tuple("ontop-uk", 0), org.assertj.core.groups.Tuple.tuple("ontop-es", 0),
                org.assertj.core.groups.Tuple.tuple("ontop-core", 4), org.assertj.core.groups.Tuple.tuple("neptune", 2));
        assertThat(unknown.sparql().split("\n\n")).as("the link store, the file index, the memberships, who tagged, the releases and the products").hasSize(6);
    }

    @Test
    void partsAnswerAsksEachPlmForItsPartsOnly() {
        service.parts(Caller.user(OFFICER));
        Map<String, List<String>> sent = federator.last().queries();
        assertThat(sent.get("neptune")).hasSize(4);
        assertThat(sent.get("neptune").get(0))
                .contains("GRAPH <" + Atelier.LINKS_GRAPH + "> { ?if atelier:betweenPart ?part . OPTIONAL { ?if atelier:ofProduct ?ifProduct } }")
                .doesNotContain("declaresFeature");
        assertThat(sent.get("neptune").get(1)).isEqualTo(FederatedQueries.fileIndex());
        List<String> described = Atelier.PLMS.stream().flatMap(plm -> parts(sent.get("ontop-" + plm).get(0)).stream()).sorted().toList();
        assertThat(sent.get("neptune").get(2)).as("the names and confirmed equivalences of the parts the PLMs describe")
                .isEqualTo(FederatedQueries.layer(described));
        assertThat(sent.get("neptune").get(3)).as("the functional edges of every product, which the meshModule rule reads")
                .isEqualTo(FederatedQueries.drives(null)).doesNotContain("VALUES ?product");
        assertThat(sent.get("ontop-core")).hasSize(4);
        for (String plm : Atelier.PLMS) {
            assertThat(sent.get("ontop-" + plm)).as(plm).hasSize(4);
            assertThat(sent.get("ontop-" + plm).get(0)).as(plm).contains("?part a atelier:Part").doesNotContain("?f ");
            assertThat(sent.get("ontop-" + plm).get(1)).as(plm).isEqualTo(FederatedQueries.references(parts(sent.get("ontop-" + plm).get(0))));
            assertThat(sent.get("ontop-" + plm).get(2)).as(plm + ": the supplier offers for the same parts")
                    .isEqualTo(FederatedQueries.offers(parts(sent.get("ontop-" + plm).get(0))));
            assertThat(sent.get("ontop-" + plm).get(3)).as(plm + ": the lines from the same parts")
                    .isEqualTo(FederatedQueries.bomLinesFrom(parts(sent.get("ontop-" + plm).get(0))));
        }
        service.interfaces(Caller.user(OFFICER));
        assertThat(federator.last().queries().get("ontop-uk").get(0)).as("the parts request is the same text in both answers")
                .isEqualTo(sent.get("ontop-uk").get(0));
        assertThat(federator.last().queries().get("ontop-core")).isEqualTo(sent.get("ontop-core"));
    }

    /** Part IRIs a request names, in text order. */
    static List<String> parts(String request) {
        return PART.matcher(request).results().map(m -> m.group(1)).toList();
    }

    private static List<Class<?>> algebra(Op op, List<Class<?>> out) {
        out.add(op.getClass());
        if (op instanceof Op1 one) algebra(one.getSubOp(), out);
        if (op instanceof Op2 two) {
            algebra(two.getLeft(), out);
            algebra(two.getRight(), out);
        }
        if (op instanceof OpN n) n.getElements().forEach(e -> algebra(e, out));
        return out;
    }
}
