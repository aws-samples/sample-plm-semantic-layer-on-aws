// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import atelier.plm.common.demo.DemoRejectedException;
import atelier.plm.core.neptune.NeptuneAuth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Atelier's link store (Neptune) as the core service writes it: a named graph replaced ({@code PUT}) or
 * added to ({@code POST}) through the Graph Store Protocol at {@code NEPTUNE_GSP_URL} (default: the
 * SPARQL URL {@code NEPTUNE_SPARQL_URL} plus {@code /gsp/}, Neptune's layout), and its triple count
 * read back with one SPARQL {@code COUNT} over the SPARQL URL. With {@code NEPTUNE_IAM_AUTH} true,
 * every request is signed with SigV4 under the task role (see {@link NeptuneAuth}); the Turtle body of
 * a PUT or POST and the form body of the count are part of the signature. Without
 * {@code NEPTUNE_SPARQL_URL} the store is unconfigured and every write answers 503. A store failure is
 * 502 naming the link store, never the URL; the detail is logged here.
 */
@Component
public class GraphStore {

    private static final Logger log = LoggerFactory.getLogger(GraphStore.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final String sparqlUrl;
    private final String gspUrl;
    private final ObjectMapper json;
    private final HttpClient http;

    @Autowired
    public GraphStore(@Value("${NEPTUNE_SPARQL_URL:}") String sparqlUrl, @Value("${NEPTUNE_GSP_URL:}") String gspUrl,
                      @Value("${NEPTUNE_IAM_AUTH:false}") boolean neptuneIamAuth, ObjectMapper json) {
        this(sparqlUrl, gspUrl, NeptuneAuth.client(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                neptuneIamAuth && !sparqlUrl.isBlank(), sparqlUrl.strip()), json);
    }

    /** The store reached through {@code http}, which signs or not. */
    public GraphStore(String sparqlUrl, String gspUrl, HttpClient http, ObjectMapper json) {
        this.sparqlUrl = sparqlUrl.strip();
        this.gspUrl = gspUrl.isBlank() ? this.sparqlUrl.replaceAll("/+$", "") + "/gsp/" : gspUrl.strip();
        this.http = http;
        this.json = json;
    }

    public boolean configured() {
        return !sparqlUrl.isBlank();
    }

    /** Replaces the named graph with {@code turtle}: Graph Store {@code PUT ?graph=<iri>}, {@code text/turtle}. */
    public void put(String graph, byte[] turtle) {
        send("PUT", graph, turtle);
    }

    /** Adds {@code turtle} to the named graph, keeping what it holds: Graph Store {@code POST ?graph=<iri>}, {@code text/turtle}. */
    public void append(String graph, byte[] turtle) {
        send("POST", graph, turtle);
    }

    /** The named graph's triple count, as {@code SELECT (COUNT(*) AS ?n)} answers it. */
    public long count(String graph) {
        requireConfigured();
        String query = "SELECT (COUNT(*) AS ?n) WHERE { GRAPH <" + graph + "> { ?s ?p ?o } }";
        HttpRequest request = HttpRequest.newBuilder(URI.create(sparqlUrl))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/sparql-results+json")
                .POST(HttpRequest.BodyPublishers.ofString("query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)))
                .build();
        String body = exchange(request, "count", graph);
        try {
            JsonNode n = json.readTree(body).path("results").path("bindings").path(0).path("n").path("value");
            if (!n.isTextual()) {
                throw new IOException("no ?n binding");
            }
            return Long.parseLong(n.asText());
        } catch (IOException | NumberFormatException e) {
            log.warn("Link store count of <{}> is not a SPARQL JSON count: {}", graph, e.toString());
            throw new DemoRejectedException(HttpStatus.BAD_GATEWAY, "the link store's count of " + graph + " is unreadable", "link-store");
        }
    }

    private void send(String method, String graph, byte[] turtle) {
        requireConfigured();
        HttpRequest request = HttpRequest.newBuilder(URI.create(gspUrl + "?graph=" + URLEncoder.encode(graph, StandardCharsets.UTF_8)))
                .timeout(TIMEOUT)
                .header("Content-Type", "text/turtle")
                .method(method, HttpRequest.BodyPublishers.ofByteArray(turtle))
                .build();
        exchange(request, method, graph);
    }

    private String exchange(HttpRequest request, String operation, String graph) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("Link store {} of <{}> answered {}: {}", operation, graph, response.statusCode(), response.body());
                throw new DemoRejectedException(HttpStatus.BAD_GATEWAY,
                        "the link store refused the " + operation + " of " + graph + " (HTTP " + response.statusCode() + ")", "link-store");
            }
            return response.body();
        } catch (IOException e) {
            log.warn("Link store {} of <{}> failed: {}", operation, graph, e.toString());
            throw new DemoRejectedException(HttpStatus.BAD_GATEWAY, "the link store did not answer", "link-store");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DemoRejectedException(HttpStatus.BAD_GATEWAY, "interrupted while waiting for the link store", "link-store");
        }
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new DemoRejectedException(HttpStatus.SERVICE_UNAVAILABLE, "link store not configured: NEPTUNE_SPARQL_URL is not set",
                    "link-store-not-configured");
        }
    }
}
