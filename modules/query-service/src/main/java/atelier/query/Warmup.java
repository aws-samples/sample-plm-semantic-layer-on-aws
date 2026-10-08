// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import atelier.query.api.Json;
import atelier.query.federation.Endpoints;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Warms the federation once the application is ready: when every SPARQL endpoint answers
 * {@code ASK {}}, the full answer ({@link QueryService#answer} over all interfaces) runs once per
 * profile of the policy, in policy order, on a daemon thread. Each Ontop endpoint thereby
 * translates the request texts of each profile before a client asks for it, which is where the
 * first answer for a profile spends most of its time; a single-interface answer sends the same
 * texts, so it is warm as well. Then the profile's first screen runs: the product list and the
 * parts, placements and interfaces of the list's first product, then of every other product. An endpoint can stop answering after the first round (an Ontop
 * task still starting refuses connections or times out), so each profile's attempt starts when
 * every endpoint answers {@code ASK {}} again, and a failed attempt is retried after a backoff
 * that doubles, up to {@link #ATTEMPTS} attempts in a round. A deploy can replace the Ontop tasks
 * for longer than a round, so after the first pass every profile whose round failed is warmed
 * again, round after round with a wait that doubles up to {@link #REWARM_CAP}, until it is done or
 * {@link #REWARM_BUDGET} has passed; the status is {@code warming} until then. Each failed attempt
 * is logged at WARN with its reason, and each round's outcome on one line; readiness and requests
 * never wait for or depend on the warm-up. Disabled with {@code atelier.warmup.enabled=false}. The summary, with every
 * failed attempt, is served at {@code GET /query/health/warmup}.
 */
@Component
public class Warmup {
    /**
     * Status of the warm-up and of each profile so far: {@code disabled}, {@code pending}, {@code running} (the first pass),
     * {@code warming} (a failed profile is being warmed again) or {@code done} (every profile done, or the budget spent).
     */
    public record Summary(String status, Map<String, Outcome> profiles) {}

    /**
     * One profile's warm-up: {@code done} with the answer's total time; {@code warming} when its attempts failed and it is
     * warmed again; {@code failed} once the budget is spent, with the time its last round took. {@code attempts} counts
     * every attempt made, and {@code failures} says why each one before the outcome did not answer.
     */
    public record Outcome(String status, long ms, int attempts, List<Failure> failures) {}

    /**
     * One failed attempt: the failure and its root cause, or, for an attempt skipped because endpoints did not answer
     * {@code ASK {}}, their names under {@code silent}.
     */
    public record Failure(int attempt, String reason, @JsonInclude(JsonInclude.Include.NON_NULL) List<String> silent) {}

    static final Duration POLL = Duration.ofSeconds(2);

    /** Attempts per profile; the backoff starts at {@link #POLL} and doubles, 30 s of waiting in all. */
    static final int ATTEMPTS = 5;

    /** After this, the warm-up runs against whatever answers; the profiles then fail and say so. */
    static final Duration ENDPOINT_WAIT = Duration.ofMinutes(5);

    /** A failed profile is warmed again, round after round, until it is done or this long after the first pass ended. */
    static final Duration REWARM_BUDGET = Duration.ofMinutes(15);

    /** The longest wait between two rounds of a failed profile; the wait starts at {@link #POLL} and doubles. */
    static final Duration REWARM_CAP = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(Warmup.class);

    private final QueryService service;
    private final Policy policy;
    private final List<String> endpointUrls;
    private final Map<String, String> names;
    private final boolean enabled;
    private final Predicate<String> answers;
    private final Duration poll;
    private final Duration endpointWait;
    private final Duration budget;
    private volatile Summary summary;

    @Autowired
    public Warmup(QueryService service, Policy policy, Endpoints endpoints,
                  @Value("${atelier.warmup.enabled:true}") boolean enabled) {
        this(service, policy, endpoints, enabled, url -> answersAsk(endpoints.httpClient(), url), POLL, ENDPOINT_WAIT, REWARM_BUDGET);
    }

    Warmup(QueryService service, Policy policy, Endpoints endpoints, boolean enabled, Predicate<String> answers,
           Duration poll) {
        this(service, policy, endpoints, enabled, answers, poll, ENDPOINT_WAIT, REWARM_BUDGET);
    }

    /**
     * @param answers whether the SPARQL endpoint at a URL answers; every endpoint is asked again after {@code poll} until all do.
     * @param poll also the first backoff between a profile's attempts, doubled after each, and between its rounds
     * @param endpointWait how long the first pass waits for every endpoint to answer
     * @param budget how long after the first pass a failed profile is warmed again
     */
    Warmup(QueryService service, Policy policy, Endpoints endpoints, boolean enabled, Predicate<String> answers,
           Duration poll, Duration endpointWait, Duration budget) {
        this.service = service;
        this.policy = policy;
        this.endpointUrls = List.copyOf(endpoints.namesByUrl().keySet());
        this.names = Map.copyOf(endpoints.namesByUrl());
        this.enabled = enabled;
        this.answers = answers;
        this.poll = poll;
        this.endpointWait = endpointWait;
        this.budget = budget;
        this.summary = new Summary(enabled ? "pending" : "disabled", Map.of());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) return;
        Thread.ofPlatform().daemon().name("warmup").start(this::run);
    }

    public Summary summary() {
        return summary;
    }

    /**
     * Waits for the endpoints, then warms each profile once, in policy order; then warms every failed profile again, in
     * rounds with a wait that doubles up to {@link #REWARM_CAP}, until each is done or the budget is spent.
     */
    void run() {
        awaitEndpoints();
        Map<String, Outcome> outcomes = new LinkedHashMap<>();
        for (Policy.Profile profile : policy.profiles()) {
            outcomes.put(profile.name(), warm(profile, 0));
            publish("running", outcomes);
        }
        long deadline = System.nanoTime() + budget.toNanos();
        Duration wait = poll;
        while (outcomes.values().stream().anyMatch(o -> !o.status().equals("done"))) {
            publish("warming", outcomes);
            boolean last = System.nanoTime() + wait.toNanos() >= deadline;
            if (last || !sleep(wait)) {
                outcomes.replaceAll((name, o) -> o.status().equals("done") ? o : new Outcome("failed", o.ms(), o.attempts(), o.failures()));
                outcomes.forEach((name, o) -> {
                    if (o.status().equals("failed")) log.warn("Warm-up {}: failed after {} attempts; the warm-up budget is spent", name, o.attempts());
                });
                break;
            }
            for (Policy.Profile profile : policy.profiles()) {
                Outcome before = outcomes.get(profile.name());
                if (before.status().equals("done")) continue;
                Outcome round = warm(profile, before.attempts());
                List<Failure> failures = new ArrayList<>(before.failures());
                failures.addAll(round.failures());
                outcomes.put(profile.name(), new Outcome(round.status(), round.ms(), before.attempts() + round.attempts(), List.copyOf(failures)));
                publish("warming", outcomes);
            }
            wait = Collections.min(List.of(wait.multipliedBy(2), REWARM_CAP));
        }
        publish("done", outcomes);
    }

    private void publish(String status, Map<String, Outcome> outcomes) {
        summary = new Summary(status, Collections.unmodifiableMap(new LinkedHashMap<>(outcomes)));
    }

    /** One round of a profile's attempts, numbered after the {@code previous} attempts of its earlier rounds. */
    private Outcome warm(Policy.Profile profile, int previous) {
        long start = System.nanoTime();
        List<Failure> failures = new ArrayList<>();
        int attempt = 0;
        while (attempt < ATTEMPTS) {
            if (attempt > 0 && !sleep(poll.multipliedBy(1L << (attempt - 1)))) break;
            attempt++;
            List<String> silent = silent().stream().map(names::get).toList();
            if (!silent.isEmpty()) {
                failed(profile, new Failure(previous + attempt, "endpoints not answering ASK {}", silent), failures);
                continue;
            }
            try {
                Caller caller = new Caller(profile, "warmup");
                Answer answer = service.answer(caller);
                String product = firstScreen(caller);
                Json.Timings timings = answer.timings();
                log.info("Warm-up {}: done on attempt {}, {} interfaces in {} ms (federation {} ms, validation {} ms), first screen {}",
                        profile.name(), previous + attempt, answer.interfaces().size(), timings.totalMs(), timings.federationMs(),
                        timings.validationMs(), product == null ? "without a product" : "of " + product);
                return new Outcome("done", timings.totalMs(), attempt, List.copyOf(failures));
            } catch (RuntimeException e) {
                failed(profile, new Failure(previous + attempt, withCause(e), null), failures);
            }
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        log.warn("Warm-up {}: attempts {} to {} failed in {} ms; warmed again", profile.name(), previous + 1, previous + attempt, ms);
        return new Outcome("warming", ms, attempt, List.copyOf(failures));
    }

    /**
     * The requests a profile's first screen sends: the product list, then the parts, placements and interfaces of the
     * list's first product, then of every other product, since a PLM's texts for a product name the parts of that site the
     * profile may see and two profiles that see different parts of a site send it different texts; the first product, or
     * null when the list is empty.
     */
    private String firstScreen(Caller caller) {
        List<Json.ProductListing> products = service.productList(caller).products();
        for (Json.ProductListing listed : products) {
            service.parts(caller, listed.key(), null, null);
            service.placements(caller, listed.key(), null, null);
            service.interfaces(caller, listed.key(), null);
        }
        return products.isEmpty() ? null : products.get(0).key();
    }

    private static void failed(Policy.Profile profile, Failure failure, List<Failure> failures) {
        failures.add(failure);
        log.warn("Warm-up {}: attempt {} failed: {}{}", profile.name(), failure.attempt(), failure.reason(),
                failure.silent() == null ? "" : " " + failure.silent());
    }

    /** The failure and its root cause: a federated endpoint failure wraps the refused connection or the timeout. */
    private static String withCause(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root == e ? e.toString() : e + ", caused by " + root;
    }

    private List<String> silent() {
        return endpointUrls.stream().filter(url -> !answers.test(url)).toList();
    }

    /** False when interrupted. */
    private static boolean sleep(Duration duration) {
        try {
            Thread.sleep(duration);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void awaitEndpoints() {
        long deadline = System.nanoTime() + endpointWait.toNanos();
        while (true) {
            List<String> silent = silent();
            if (silent.isEmpty()) return;
            if (System.nanoTime() > deadline) {
                log.warn("Warm-up: endpoints not answering after {} s: {}", endpointWait.toSeconds(), silent);
                return;
            }
            if (!sleep(poll)) return;
        }
    }

    /**
     * Whether the SPARQL endpoint at {@code url} answers {@code ASK {}} with a 2xx status, through the endpoints' client
     * (the link store's probe is signed like its requests).
     */
    static boolean answersAsk(HttpClient http, String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/sparql-results+json")
                .POST(HttpRequest.BodyPublishers.ofString("query=" + URLEncoder.encode("ASK {}", StandardCharsets.UTF_8)))
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() / 100 == 2;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
