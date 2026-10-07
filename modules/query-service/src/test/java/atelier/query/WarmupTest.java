// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.api.Json;
import atelier.query.cad.CadUrls;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import atelier.query.evidence.NoOntopSql;
import atelier.query.federation.Endpoints;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.validation.RuleValidator;
import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * The warm-up over fixtures/neighbourhood.ttl: one full answer per profile of the policy, in
 * policy order; a core endpoint that fails the DE engineer's request every time, and one that
 * refuses its first connections, as an Ontop task still starting does.
 */
class WarmupTest {
    private static final Policy POLICY = EvidenceTest.POLICY;
    private static final List<String> PROFILES = List.of("fr-engineer", "de-engineer", "uk-engineer", "es-engineer",
            "programme-cleared", "export-officer", "unknown");

    /** Records every request to the core endpoint; fails the one carrying the DE engineer's FILTER, as Jena does when an endpoint is down. */
    static class FailingForDe extends FixtureFederator {
        final List<String> coreRequests = new ArrayList<>();

        @Override
        protected Model answer(String url, String query) {
            if (url.equals(ENDPOINTS.ontop(Endpoints.CORE))) {
                coreRequests.add(query);
                if (query.contains(POLICY.profile("de-engineer").filter())) {
                    throw new IllegalStateException("ontop-core is down");
                }
            }
            return super.answer(url, query);
        }
    }

    /** Refuses the first {@code refusals} requests to the core endpoint, as Jena reports a refused connection. */
    static class RefusingFirst extends FixtureFederator {
        final AtomicInteger refusals;

        RefusingFirst(int refusals) {
            this.refusals = new AtomicInteger(refusals);
        }

        @Override
        protected Model answer(String url, String query) {
            if (url.equals(ENDPOINTS.ontop(Endpoints.CORE)) && refusals.getAndDecrement() > 0) {
                throw new HttpException(new ConnectException("Connection refused: ontop-core"));
            }
            return super.answer(url, query);
        }
    }

    /** The fixture's products, in the list's order. */
    private static final List<String> PRODUCTS = List.of("ornithopter", "tail", "wings");
    /** A profile's first screen after its full answer: the product list, then each product's parts, placements and interfaces. */
    private static final List<String> FIRST_SCREEN = java.util.stream.Stream.concat(java.util.stream.Stream.of("productList"),
            PRODUCTS.stream().flatMap(p -> java.util.stream.Stream.of("parts " + p, "placements " + p, "interfaces " + p))).toList();
    /** The core tag requests of a profile's warm-up: the full answer's and one for each answer of the first screen. */
    private static final int TAG_REQUESTS = 1 + FIRST_SCREEN.size();
    /** The federations of a profile's warm-up: the full answer and each product's parts, placements and interfaces. */
    private static final int RESULTS = 1 + 3 * PRODUCTS.size();

    private final FailingForDe federator = new FailingForDe();

    /** A warm-up with no time to warm a failed profile again: a failed first round is the profile's outcome. */
    /** Keeps every request text each endpoint received, as Ontop's translation cache keys them. */
    static class SentTexts extends FixtureFederator {
        final java.util.Set<String> sent = java.util.concurrent.ConcurrentHashMap.newKeySet();

        @Override
        protected Model answer(String url, String query) {
            sent.add(url + "\n" + query);
            return super.answer(url, query);
        }
    }

    @Test
    void everyRequestOfAnyProfilesFirstScreenOfAnyProductWasSentByTheWarmup() {
        SentTexts texts = new SentTexts();
        QueryService warmed = new QueryService(texts, new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));
        oneRound(warmed, url -> true).run();
        java.util.Set<String> warm = java.util.Set.copyOf(texts.sent);
        texts.sent.clear();
        for (Policy.Profile profile : POLICY.profiles()) {
            Caller caller = new Caller(profile, "viewer");
            warmed.productList(caller);
            for (String product : PRODUCTS) {
                warmed.parts(caller, product, null, null);
                warmed.placements(caller, product, null, null);
                warmed.interfaces(caller, product, null);
            }
        }
        assertThat(texts.sent).as("a PLM's texts for a product name the parts the profile may see: every one is warm").isSubsetOf(warm);
    }

    private static Warmup oneRound(QueryService service, java.util.function.Predicate<String> answers) {
        return new Warmup(service, POLICY, FixtureFederator.ENDPOINTS, true, answers, Duration.ZERO, Warmup.ENDPOINT_WAIT, Duration.ZERO);
    }
    private final FixtureFederator healthyFederator = new FixtureFederator();
    private final QueryService healthy = new QueryService(healthyFederator, new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));
    private final QueryService service = new QueryService(federator, new RuleValidator(), new NoOntopSql(),
            new CadUrls(null, null));

    @Test
    void answersAllInterfacesOncePerProfileAndReportsTheFailingOne() {
        Warmup warmup = oneRound(service, url -> true);
        assertThat(warmup.summary()).isEqualTo(new Warmup.Summary("pending", Map.of()));

        warmup.run();

        assertThat(POLICY.profiles()).extracting(Policy.Profile::name).containsExactlyElementsOf(PROFILES);
        String de = POLICY.profile("de-engineer").filter();
        List<String> tagRequests = federator.coreRequests.stream().filter(q -> q.contains("atelier:releasableTo ?rel")).toList();
        assertThat(tagRequests).as("a full answer and a first screen per profile, and every attempt of the failing one")
                .hasSize(TAG_REQUESTS * (PROFILES.size() - 1) + Warmup.ATTEMPTS);
        assertThat(tagRequests.stream().filter(q -> q.contains(de))).hasSize(Warmup.ATTEMPTS);
        assertThat(tagRequests.stream().filter(q -> !q.contains(de)).toList()).as("the other profiles in policy order")
                .zipSatisfy(POLICY.profiles().stream().filter(p -> !p.name().equals("de-engineer"))
                                .flatMap(p -> java.util.Collections.nCopies(TAG_REQUESTS, p).stream()).toList(),
                        (q, p) -> assertThat(q).contains(p.filter()));
        assertThat(federator.results).as("every profile but the failing one produced its results").hasSize(RESULTS * (PROFILES.size() - 1));
        Warmup.Summary summary = warmup.summary();
        assertThat(summary.status()).isEqualTo("done");
        assertThat(summary.profiles().keySet()).containsExactlyElementsOf(PROFILES);
        Warmup.Outcome failed = summary.profiles().get("de-engineer");
        assertThat(failed.status()).isEqualTo("failed");
        assertThat(failed.attempts()).isEqualTo(Warmup.ATTEMPTS);
        assertThat(failed.failures()).hasSize(Warmup.ATTEMPTS).allMatch(f -> f.reason().contains("ontop-core is down") && f.silent() == null);
        assertThat(failed.failures()).extracting(Warmup.Failure::attempt).containsExactly(1, 2, 3, 4, 5);
        assertThat(summary.profiles().values()).filteredOn(o -> o.status().equals("done")).hasSize(6)
                .allMatch(o -> o.ms() >= 0 && o.attempts() == 1 && o.failures().isEmpty());
    }

    @Test
    void retriesAProfileWhileTheEndpointRefusesConnections() {
        RefusingFirst refusing = new RefusingFirst(2);
        QueryService refused = new QueryService(refusing, new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));
        Warmup warmup = new Warmup(refused, POLICY, FixtureFederator.ENDPOINTS, true, url -> true, Duration.ZERO);

        warmup.run();

        Warmup.Summary summary = warmup.summary();
        assertThat(summary.profiles().values()).as("every profile warm").allMatch(o -> o.status().equals("done"));
        assertThat(summary.profiles().get(PROFILES.get(0)).attempts()).as("the first profile, on its third attempt").isEqualTo(3);
        assertThat(summary.profiles().values().stream().skip(1)).allMatch(o -> o.attempts() == 1);
        assertThat(refusing.results).hasSize(RESULTS * PROFILES.size());
    }

    @Test
    void logsEachFailedAttemptWithItsReasonThenTheOutcome() {
        Logger logger = (Logger) LoggerFactory.getLogger(Warmup.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            QueryService refused = new QueryService(new RefusingFirst(2), new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));
            new Warmup(refused, POLICY, FixtureFederator.ENDPOINTS, true, url -> true, Duration.ZERO).run();
        } finally {
            logger.detachAppender(appender);
        }
        List<String> first = appender.list.stream().map(e -> e.getLevel() + " " + e.getFormattedMessage())
                .filter(m -> m.contains("Warm-up " + PROFILES.get(0) + ":")).toList();
        assertThat(first).hasSize(3);
        assertThat(first.get(0)).startsWith("WARN Warm-up fr-engineer: attempt 1 failed: ").contains("ConnectException: Connection refused: ontop-core");
        assertThat(first.get(1)).startsWith("WARN Warm-up fr-engineer: attempt 2 failed: ").contains("ConnectException: Connection refused: ontop-core");
        assertThat(first.get(2)).startsWith("INFO Warm-up fr-engineer: done on attempt 3, ");
        assertThat(appender.list).as("one line per other profile").filteredOn(e -> e.getFormattedMessage().contains(": done on attempt 1,"))
                .hasSize(PROFILES.size() - 1);
    }

    @Test
    void anEndpointThatNeverAcceptsFailsEachProfileAfterTheLastAttempt() {
        RefusingFirst refusing = new RefusingFirst(Integer.MAX_VALUE);
        QueryService refused = new QueryService(refusing, new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));
        Warmup warmup = oneRound(refused, url -> true);

        warmup.run();

        assertThat(warmup.summary().status()).isEqualTo("done");
        assertThat(warmup.summary().profiles().values()).allSatisfy(o -> {
            assertThat(o.status()).isEqualTo("failed");
            assertThat(o.attempts()).isEqualTo(Warmup.ATTEMPTS);
            assertThat(o.failures()).hasSize(Warmup.ATTEMPTS).allMatch(f -> f.reason().contains("Connection refused"));
        });
    }

    @Test
    void anAttemptWaitsForTheEndpointsToAnswerAgain() {
        // Every endpoint answers the first round; the core endpoint then stays silent for two probes.
        String core = FixtureFederator.ENDPOINTS.ontop(Endpoints.CORE);
        int endpoints = FixtureFederator.ENDPOINTS.namesByUrl().size();
        AtomicInteger coreProbes = new AtomicInteger();
        Warmup warmup = new Warmup(healthy, POLICY, FixtureFederator.ENDPOINTS, true,
                url -> !url.equals(core) || coreProbes.incrementAndGet() == 1 || coreProbes.get() > 3, Duration.ZERO);

        warmup.run();

        assertThat(endpoints).isGreaterThan(1);
        Warmup.Outcome first = warmup.summary().profiles().get(PROFILES.get(0));
        assertThat(first.attempts()).as("two attempts skipped while silent").isEqualTo(3);
        assertThat(first.failures()).as("each skipped attempt names the silent endpoint")
                .containsExactly(new Warmup.Failure(1, "endpoints not answering ASK {}", List.of("ontop-core")),
                        new Warmup.Failure(2, "endpoints not answering ASK {}", List.of("ontop-core")));
        assertThat(warmup.summary().profiles().values()).allMatch(o -> o.status().equals("done"));
        assertThat(healthyFederator.results).as("one warm-up per profile, none while the core endpoint was silent").hasSize(RESULTS * PROFILES.size());
    }

    @Test
    void waitsUntilEveryEndpointAnswersBeforeTheFirstQuery() {
        AtomicInteger probes = new AtomicInteger();
        int endpoints = FixtureFederator.ENDPOINTS.namesByUrl().size();
        Warmup warmup = oneRound(service, url -> probes.incrementAndGet() > endpoints);

        warmup.run();

        assertThat(probes.get()).as("a second round after the first found endpoints silent").isGreaterThan(endpoints);
        assertThat(federator.coreRequests.stream().filter(q -> q.contains("atelier:releasableTo ?rel")))
                .as("a full answer and a first screen per profile, every attempt of the failing one")
                .hasSize(TAG_REQUESTS * (PROFILES.size() - 1) + Warmup.ATTEMPTS);
        assertThat(warmup.summary().status()).isEqualTo("done");
    }

    @Test
    void eachProfileRunsItsFirstScreenAfterItsFullAnswer() {
        List<String> asked = new ArrayList<>();
        QueryService recording = new QueryService(healthyFederator, new RuleValidator(), new NoOntopSql(), new CadUrls(null, null)) {
            @Override public Answer answer(Caller caller) { asked.add(caller.profile().name() + " answer"); return super.answer(caller); }
            @Override public Json.ProductListResponse productList(Caller caller) { asked.add(caller.profile().name() + " productList"); return super.productList(caller); }
            @Override public Json.PartsResponse parts(Caller caller, String product, String root, String option) {
                asked.add(caller.profile().name() + " parts " + product); return super.parts(caller, product, root, option); }
            @Override public Json.Placements placements(Caller caller, String product, String root, String option) {
                asked.add(caller.profile().name() + " placements " + product); return super.placements(caller, product, root, option); }
            @Override public Json.InterfacesResponse interfaces(Caller caller, String product, String root) {
                asked.add(caller.profile().name() + " interfaces " + product); return super.interfaces(caller, product, root); }
        };

        new Warmup(recording, POLICY, FixtureFederator.ENDPOINTS, true, url -> true, Duration.ZERO).run();

        assertThat(asked).containsExactlyElementsOf(PROFILES.stream()
                .flatMap(p -> java.util.stream.Stream.concat(java.util.stream.Stream.of(p + " answer"), FIRST_SCREEN.stream().map(s -> p + " " + s)))
                .toList());
    }

    @Test
    void endpointsSilentLongerThanTheFirstPassEndWithEveryProfileWarmWithoutARestart() {
        // Silent through the first wait and every attempt of the first pass, as Ontop tasks a deploy is replacing; then answering.
        int endpoints = FixtureFederator.ENDPOINTS.namesByUrl().size();
        int firstPass = endpoints * (1 + PROFILES.size() * Warmup.ATTEMPTS);
        AtomicInteger probes = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<Warmup> self = new java.util.concurrent.atomic.AtomicReference<>();
        List<String> statusesOnceAnswering = new ArrayList<>();
        Warmup warmup = new Warmup(healthy, POLICY, FixtureFederator.ENDPOINTS, true, url -> {
            boolean answering = probes.incrementAndGet() > firstPass;
            if (answering) statusesOnceAnswering.add(self.get().summary().status());
            return answering;
        }, Duration.ZERO, Duration.ZERO, Duration.ofMinutes(1));
        self.set(warmup);

        warmup.run();

        assertThat(statusesOnceAnswering).as("the status while failed profiles are warmed again").first().isEqualTo("warming");
        Warmup.Summary summary = warmup.summary();
        assertThat(summary.status()).isEqualTo("done");
        assertThat(summary.profiles().keySet()).containsExactlyElementsOf(PROFILES);
        assertThat(summary.profiles().values()).allSatisfy(o -> {
            assertThat(o.status()).isEqualTo("done");
            assertThat(o.attempts()).as("the first pass's round, then one attempt of the next").isEqualTo(Warmup.ATTEMPTS + 1);
            assertThat(o.failures()).extracting(Warmup.Failure::attempt).containsExactly(1, 2, 3, 4, 5);
            assertThat(o.failures()).allMatch(f -> f.silent() != null && !f.silent().isEmpty());
        });
        assertThat(healthyFederator.results).as("one warm-up per profile, once the endpoints answer").hasSize(RESULTS * PROFILES.size());
    }

    @Test
    void aProfileStillFailingWhenTheBudgetIsSpentIsReportedFailed() {
        Warmup warmup = new Warmup(service, POLICY, FixtureFederator.ENDPOINTS, true, url -> true, Duration.ZERO, Duration.ZERO, Duration.ofMillis(200));

        warmup.run();

        Warmup.Outcome de = warmup.summary().profiles().get("de-engineer");
        assertThat(warmup.summary().status()).isEqualTo("done");
        assertThat(de.status()).isEqualTo("failed");
        assertThat(de.attempts()).as("more than one round before the budget ran out").isGreaterThan(Warmup.ATTEMPTS)
                .isEqualTo(de.failures().size());
        assertThat(warmup.summary().profiles().values()).filteredOn(o -> o.status().equals("done")).hasSize(PROFILES.size() - 1);
    }

    @Test
    void disabledNeverQueries() {
        Warmup warmup = new Warmup(service, POLICY, FixtureFederator.ENDPOINTS, false, url -> true, Duration.ZERO);

        warmup.start();

        assertThat(federator.coreRequests).isEmpty();
        assertThat(warmup.summary()).isEqualTo(new Warmup.Summary("disabled", Map.of()));
    }
}
