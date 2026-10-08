// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core;

import atelier.plm.core.neptune.NeptuneAuth;
import atelier.plm.core.neptune.NeptuneSigV4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

/**
 * The signer against a signature computed here from the specification, with a fixed credential pair and clock: the
 * form POST of a SPARQL request, the Graph Store PUT with the graph IRI in the query string, a request without a body,
 * session credentials; and the switch's resolution of the region and of what is signed. The SDK signer sends the hash
 * of the body as the header {@code x-amz-content-sha256} as well, and signs it with the others. The bytes the store
 * receives are compared with the signature in {@link GraphStoreSigningTest}.
 */
class NeptuneSigV4Test {
    static final String KEY = "AKIDQUERYTESTKEY00001"; // gitleaks:allow (a test value, not a credential) pragma: allowlist secret
    static final String SECRET = "query-test-secret-key-not-a-credential"; // gitleaks:allow (a test value, not a credential) pragma: allowlist secret
    static final String REGION = "eu-west-1";
    static final Instant AT = Instant.parse("2026-01-01T00:00:00Z");
    static final String AMZ_DATE = "20260101T000000Z";
    static final String SCOPE = "Credential=" + KEY + "/20260101/" + REGION + "/neptune-db/aws4_request";
    static final URI SPARQL = URI.create("https://links.cluster-c0ffee.eu-west-1.neptune.amazonaws.com:8182/sparql");
    static final String HOST = "links.cluster-c0ffee.eu-west-1.neptune.amazonaws.com:8182";
    static final String FORM = "application/x-www-form-urlencoded";
    static final String RESULTS = "application/sparql-results+json";

    /** The headers the signature covers: the request's, the host as the JDK client sends it, the date and the body hash. */
    private static Map<String, String> signedHeaders(Map<String, String> requestHeaders, byte[] body) {
        Map<String, String> all = new java.util.HashMap<>(requestHeaders);
        all.put("host", HOST);
        all.put("x-amz-date", AMZ_DATE);
        all.put("x-amz-content-sha256", SigV4Oracle.sha256Hex(body));
        return all;
    }

    static NeptuneSigV4 signer() {
        return new NeptuneSigV4(StaticCredentialsProvider.create(AwsBasicCredentials.create(KEY, SECRET)), REGION, Clock.fixed(AT, ZoneOffset.UTC));
    }

    private static HttpRequest formPost(String body) {
        return HttpRequest.newBuilder(SPARQL).timeout(Duration.ofSeconds(7))
                .header("Content-Type", FORM).header("Accept", RESULTS)
                .POST(BodyPublishers.ofString(body, StandardCharsets.US_ASCII)).build();
    }

    @Test
    void aFormPostIsSignedOverMethodPathHostHeadersDateAndBody() {
        String body = "query=ASK%20%7B%7D";
        HttpRequest signed = signer().sign(formPost(body), body.getBytes(StandardCharsets.US_ASCII));

        byte[] bytes = body.getBytes(StandardCharsets.US_ASCII);
        String expected = SigV4Oracle.authorization("POST", SPARQL, signedHeaders(Map.of("Content-Type", FORM, "Accept", RESULTS), bytes),
                bytes, AT, KEY, SECRET, REGION);
        assertThat(expected).startsWith("AWS4-HMAC-SHA256 " + SCOPE
                + ", SignedHeaders=accept;content-type;host;x-amz-content-sha256;x-amz-date, Signature=");
        assertThat(signed.headers().firstValue("Authorization")).hasValue(expected);
        assertThat(signed.headers().firstValue("X-Amz-Date")).hasValue(AMZ_DATE);
        assertThat(signed.headers().firstValue("x-amz-content-sha256")).hasValue(SigV4Oracle.sha256Hex(bytes));
        assertThat(signed.headers().firstValue("X-Amz-Security-Token")).isEmpty();
        assertThat(signed.headers().map().keySet()).as("the host header is the JDK client's").noneMatch(name -> name.equalsIgnoreCase("Host"));
        assertThat(signed.headers().firstValue("Content-Type")).hasValue(FORM);
        assertThat(signed.headers().firstValue("Accept")).hasValue(RESULTS);
        assertThat(signed.method()).isEqualTo("POST");
        assertThat(signed.uri()).isEqualTo(SPARQL);
        assertThat(signed.timeout()).hasValue(Duration.ofSeconds(7));
        assertThat(signed.bodyPublisher().orElseThrow().contentLength()).as("the body sent is the body signed").isEqualTo(body.length());
    }

    @Test
    void theBodyIsPartOfTheSignature() {
        String body = "query=ASK%20%7B%7D";
        HttpRequest one = signer().sign(formPost(body), body.getBytes(StandardCharsets.US_ASCII));
        HttpRequest other = signer().sign(formPost(body + "."), (body + ".").getBytes(StandardCharsets.US_ASCII));
        assertThat(other.headers().firstValue("Authorization")).isNotEqualTo(one.headers().firstValue("Authorization"));
        byte[] bytes = (body + ".").getBytes(StandardCharsets.US_ASCII);
        assertThat(other.headers().firstValue("Authorization")).hasValue(SigV4Oracle.authorization("POST", SPARQL,
                signedHeaders(Map.of("Content-Type", FORM, "Accept", RESULTS), bytes), bytes, AT, KEY, SECRET, REGION));
    }

    @Test
    void sessionCredentialsAddTheTokenToTheSignedHeaders() {
        String token = "session-token-test-value"; // gitleaks:allow (a test value, not a credential) pragma: allowlist secret
        NeptuneSigV4 session = new NeptuneSigV4(StaticCredentialsProvider.create(AwsSessionCredentials.create(KEY, SECRET, token)),
                REGION, Clock.fixed(AT, ZoneOffset.UTC));
        String body = "query=ASK%20%7B%7D";
        byte[] bytes = body.getBytes(StandardCharsets.US_ASCII);
        HttpRequest signed = session.sign(formPost(body), bytes);
        assertThat(signed.headers().firstValue("X-Amz-Security-Token")).hasValue(token);
        assertThat(signed.headers().firstValue("Authorization")).hasValue(SigV4Oracle.authorization("POST", SPARQL,
                signedHeaders(Map.of("Content-Type", FORM, "Accept", RESULTS, "x-amz-security-token", token), bytes), bytes, AT, KEY, SECRET, REGION));
        assertThat(signed.headers().firstValue("Authorization").orElseThrow())
                .contains("SignedHeaders=accept;content-type;host;x-amz-content-sha256;x-amz-date;x-amz-security-token,");
    }

    @Test
    void aGraphStorePutIsSignedOverItsQueryStringAndTurtleBody() {
        URI gsp = URI.create(SPARQL + "/gsp/?graph=https%3A%2F%2Fexample.com%2Fatelier%2Fgraph%2Flinks");
        byte[] turtle = "<https://example.com/atelier/fr/plug/P1> <https://example.com/atelier/ontology#matesWith> <https://example.com/atelier/de/plug/P2> .\n"
                .getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(gsp).header("Content-Type", "text/turtle").PUT(BodyPublishers.ofByteArray(turtle)).build();
        HttpRequest signed = signer().sign(request, turtle);
        assertThat(signed.headers().firstValue("Authorization")).hasValue(SigV4Oracle.authorization("PUT", gsp,
                signedHeaders(Map.of("Content-Type", "text/turtle"), turtle), turtle, AT, KEY, SECRET, REGION));
        assertThat(signed.method()).isEqualTo("PUT");
        assertThat(signed.bodyPublisher().orElseThrow().contentLength()).isEqualTo(turtle.length);
    }

    @Test
    void aRequestWithoutBodySignsTheHashOfNothingAndItsQueryString() {
        URI get = URI.create(SPARQL + "?query=ASK%20%7B%7D");
        HttpRequest signed = signer().sign(HttpRequest.newBuilder(get).header("Accept", RESULTS).GET().build(), new byte[0]);
        assertThat(signed.headers().firstValue("Authorization")).hasValue(SigV4Oracle.authorization("GET", get,
                signedHeaders(Map.of("Accept", RESULTS), new byte[0]), new byte[0], AT, KEY, SECRET, REGION));
        assertThat(signed.bodyPublisher().orElseThrow().contentLength()).isZero();
    }

    @Test
    void theRegionIsTheEnvironmentsElseTheEndpointHostsAndTheSwitchOffLeavesTheClientAlone() {
        assertThat(NeptuneAuth.region(SPARQL, "eu-central-1")).isEqualTo("eu-central-1");
        assertThat(NeptuneAuth.region(SPARQL, null)).isEqualTo("eu-west-1");
        assertThat(NeptuneAuth.region(URI.create("https://links.cluster-c0ffee.ap-northeast-1.neptune.amazonaws.com:8182/sparql"), "")).isEqualTo("ap-northeast-1");
        assertThatThrownBy(() -> NeptuneAuth.region(URI.create("http://links:7878/query"), null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("AWS_REGION");

        assertThat(NeptuneAuth.signs(SPARQL)).accepts(SPARQL, URI.create(SPARQL + "/gsp/?graph=x"), URI.create("https://" + HOST + "/status"))
                .rejects(URI.create("https://links.cluster-c0ffee.eu-west-1.neptune.amazonaws.com/sparql"), URI.create("http://ontop-fr.local:8080/sparql"),
                        // the endpoint's host as the start of a longer host, and as userinfo in front of another host
                        URI.create("https://links.cluster-c0ffee.eu-west-1.neptune.amazonaws.com.attacker.example:8182/sparql"),
                        URI.create("https://" + HOST + ".attacker.example/sparql"),
                        URI.create("https://links.cluster-c0ffee.eu-west-1.neptune.amazonaws.com@example.com:8182/sparql"),
                        URI.create("https://" + HOST + "@example.com/sparql"));

        java.net.http.HttpClient base = java.net.http.HttpClient.newHttpClient();
        assertThat(NeptuneAuth.client(base, false, SPARQL.toString())).isSameAs(base);
    }
}
