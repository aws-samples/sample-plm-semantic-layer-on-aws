// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.neptune;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.federation.Endpoints;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP;
import org.apache.jena.sparql.exec.http.QuerySendMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The signing client under Jena, against a loopback server standing in for the link store that records what it
 * receives: the request Jena sends to the link store arrives as a form POST whose signature is the one the
 * specification gives for the very bytes and headers received; a request to any other origin through the same client,
 * and every request of endpoints built without the switch, arrive unsigned; a 3xx answer from the link store comes back
 * to the caller, the client following no redirect.
 */
class SigningHttpClientTest {
    /** One request received: method, path, the headers (lowercased names, first values) and the body. */
    record Received(String method, String path, Map<String, String> headers, byte[] body) {
    }

    private final List<Received> received = new ArrayList<>();
    private HttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            record(exchange);
            byte[] answer = "{\"head\":{},\"boolean\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/sparql-results+json");
            exchange.sendResponseHeaders(200, answer.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(answer);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void record(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new TreeMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), values.get(0)));
        received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers, exchange.getRequestBody().readAllBytes()));
    }

    private String origin() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String linkStore() {
        return origin() + "/sparql";
    }

    private SigningHttpClient signing(URI signedOrigin) {
        return new SigningHttpClient(HttpClient.newHttpClient(), NeptuneAuth.signs(signedOrigin), NeptuneSigV4Test.signer());
    }

    private static boolean ask(HttpClient client, String url) {
        try (QueryExecution exec = QueryExecutionHTTP.create().endpoint(url).httpClient(client).sendMode(QuerySendMode.asPostForm).query("ASK {}").build()) {
            return exec.execAsk();
        }
    }

    @Test
    void theRequestJenaSendsToTheLinkStoreArrivesSignedOverTheBytesAndHeadersReceived() {
        assertThat(ask(signing(URI.create(linkStore())), linkStore())).isTrue();

        Received request = received.get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/sparql");
        assertThat(request.headers().get("content-type")).startsWith("application/x-www-form-urlencoded");
        String form = new String(request.body(), StandardCharsets.US_ASCII);
        assertThat(form).startsWith("query=");
        assertThat(URLDecoder.decode(form.substring("query=".length()), StandardCharsets.UTF_8)).isEqualTo("ASK {}");
        assertThat(request.headers().get("host")).isEqualTo("127.0.0.1:" + server.getAddress().getPort());
        assertThat(request.headers().get("x-amz-date")).isEqualTo(NeptuneSigV4Test.AMZ_DATE);

        String authorization = request.headers().get("authorization");
        assertThat(authorization).startsWith("AWS4-HMAC-SHA256 " + NeptuneSigV4Test.SCOPE + ", SignedHeaders=");
        String signedHeaders = authorization.substring(authorization.indexOf("SignedHeaders=") + "SignedHeaders=".length(), authorization.indexOf(", Signature="));
        assertThat(signedHeaders.split(";")).contains("accept", "content-type", "host", "x-amz-content-sha256", "x-amz-date");
        assertThat(request.headers().get("x-amz-content-sha256")).isEqualTo(SigV4Oracle.sha256Hex(request.body()));
        Map<String, String> signed = new LinkedHashMap<>();
        for (String name : signedHeaders.split(";")) {
            signed.put(name, request.headers().get(name));
        }
        assertThat(authorization).isEqualTo(SigV4Oracle.authorization("POST", URI.create(linkStore()), signed, request.body(),
                NeptuneSigV4Test.AT, NeptuneSigV4Test.KEY, NeptuneSigV4Test.SECRET, NeptuneSigV4Test.REGION));
    }

    @Test
    void aRequestToAnotherOriginThroughTheSigningClientIsNotSigned() {
        assertThat(ask(signing(URI.create("https://links.cluster-c0ffee.eu-west-1.neptune.amazonaws.com:8182/sparql")), linkStore())).isTrue();
        assertThat(received.get(0).headers()).doesNotContainKeys("authorization", "x-amz-date");
    }

    @Test
    void aRedirectFromTheLinkStoreComesBackToTheCallerAndIsNotFollowed() throws Exception {
        server.createContext("/redirect", exchange -> {
            record(exchange);
            exchange.getResponseHeaders().add("Location", origin() + "/elsewhere");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        HttpClient signing = new SigningHttpClient(Endpoints.baseClient(Duration.ofSeconds(2)), NeptuneAuth.signs(URI.create(origin())), NeptuneSigV4Test.signer());
        HttpRequest request = HttpRequest.newBuilder(URI.create(origin() + "/redirect")).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("query=ASK%20%7B%7D")).build();

        HttpResponse<Void> response = signing.send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(received).extracting(Received::path).as("the redirect target is never requested").containsExactly("/redirect");
        assertThat(received.get(0).headers()).containsKey("authorization");
        assertThat(signing.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
        Endpoints endpoints = new Endpoints(origin() + "/ontop-fr", origin() + "/ontop-de", origin() + "/ontop-uk", origin() + "/ontop-es",
                origin() + "/ontop-core", linkStore(), Duration.ofSeconds(2), Duration.ofSeconds(5));
        assertThat(endpoints.httpClient().followRedirects()).as("the endpoints' client, signing or not").isEqualTo(HttpClient.Redirect.NEVER);
    }

    @Test
    void endpointsWithoutTheSwitchSendUnsignedRequestsAndTheLinkStoresAsAFormPost() throws Exception {
        Endpoints endpoints = new Endpoints(origin() + "/ontop-fr", origin() + "/ontop-de", origin() + "/ontop-uk", origin() + "/ontop-es",
                origin() + "/ontop-core", linkStore(), Duration.ofSeconds(2), Duration.ofSeconds(5));
        try (QueryExecution exec = endpoints.execution(endpoints.linkStore(), "ASK {}")) {
            assertThat(exec.execAsk()).isTrue();
        }
        try (QueryExecution exec = endpoints.execution(endpoints.ontop("fr"), "ASK {}")) {
            assertThat(exec.execAsk()).isTrue();
        }
        HttpRequest probe = HttpRequest.newBuilder(URI.create(linkStore())).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("query=ASK%20%7B%7D")).build();
        assertThat(endpoints.httpClient().send(probe, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);

        assertThat(received).hasSize(3);
        assertThat(received).allSatisfy(r -> assertThat(r.headers()).doesNotContainKeys("authorization", "x-amz-date"));
        assertThat(received.get(0).method()).as("the link store's request is a form POST").isEqualTo("POST");
        assertThat(received.get(0).headers().get("content-type")).startsWith("application/x-www-form-urlencoded");
        assertThat(received.get(1).method()).as("an Ontop endpoint keeps Jena's default, a GET for a short request").isEqualTo("GET");
        assertThat(received.get(1).path()).isEqualTo("/ontop-fr");
    }
}
