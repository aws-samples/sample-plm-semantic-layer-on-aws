// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.plm.core.graph.GraphStore;
import atelier.plm.core.neptune.NeptuneAuth;
import atelier.plm.core.neptune.SigningHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The store's writes and count through the signing client, against a {@link StubNeptune} that records what it
 * receives: the Graph Store PUT and POST and the SPARQL count each arrive with a signature that is the one the
 * specification gives for the very bytes and headers received, and the Turtle arrives intact (the stub parses it and
 * counts it). Through the switch off, nothing is signed.
 */
class GraphStoreSigningTest {
    private static final String GRAPH = "https://example.com/atelier/graph/links";
    private static final byte[] ONE = ("<https://example.com/atelier/fr/plug/P1> <https://example.com/atelier/ontology#matesWith> "
            + "<https://example.com/atelier/de/plug/P2> .\n").getBytes(StandardCharsets.UTF_8);
    private static final byte[] TWO = ("<https://example.com/atelier/uk/plug/P3> <https://example.com/atelier/ontology#matesWith> "
            + "<https://example.com/atelier/es/plug/P4> .\n").getBytes(StandardCharsets.UTF_8);

    private static URI gsp(StubNeptune neptune) {
        return URI.create(neptune.sparqlUrl() + "/gsp/?graph=" + URLEncoder.encode(GRAPH, StandardCharsets.UTF_8));
    }

    /** The {@code Authorization} the specification gives for a request as the stub received it, over the headers it names as signed. */
    private static String expected(StubNeptune.Seen seen, URI uri) {
        String authorization = seen.headers().get("authorization");
        String names = authorization.substring(authorization.indexOf("SignedHeaders=") + "SignedHeaders=".length(), authorization.indexOf(", Signature="));
        Map<String, String> signed = new LinkedHashMap<>();
        for (String name : names.split(";")) {
            signed.put(name, seen.headers().get(name));
        }
        return SigV4Oracle.authorization(seen.method(), uri, signed, seen.bytes(), NeptuneSigV4Test.AT,
                NeptuneSigV4Test.KEY, NeptuneSigV4Test.SECRET, NeptuneSigV4Test.REGION);
    }

    @Test
    void everyRequestArrivesSignedOverTheBytesAndHeadersTheStoreReceives() throws IOException {
        try (StubNeptune neptune = new StubNeptune()) {
            URI sparql = URI.create(neptune.sparqlUrl());
            HttpClient client = new SigningHttpClient(HttpClient.newHttpClient(), NeptuneAuth.signs(sparql), NeptuneSigV4Test.signer());
            GraphStore store = new GraphStore(neptune.sparqlUrl(), "", client, new ObjectMapper());

            store.put(GRAPH, ONE);
            store.append(GRAPH, TWO);
            assertThat(store.count(GRAPH)).isEqualTo(2);

            assertThat(neptune.seen).hasSize(3);
            StubNeptune.Seen put = neptune.seen.get(0);
            StubNeptune.Seen post = neptune.seen.get(1);
            StubNeptune.Seen count = neptune.seen.get(2);
            assertThat(put.method()).isEqualTo("PUT");
            assertThat(put.bytes()).as("the Turtle arrives as it was signed").isEqualTo(ONE);
            assertThat(post.method()).isEqualTo("POST");
            assertThat(post.bytes()).isEqualTo(TWO);
            assertThat(count.method()).isEqualTo("POST");
            assertThat(count.path()).isEqualTo("/sparql");
            for (StubNeptune.Seen seen : neptune.seen) {
                assertThat(seen.headers()).containsKeys("authorization", "x-amz-date", "x-amz-content-sha256");
                assertThat(seen.headers().get("authorization")).startsWith("AWS4-HMAC-SHA256 " + NeptuneSigV4Test.SCOPE + ", SignedHeaders=");
                assertThat(seen.headers().get("x-amz-content-sha256")).isEqualTo(SigV4Oracle.sha256Hex(seen.bytes()));
                assertThat(seen.headers().get("host")).isEqualTo(sparql.getAuthority());
            }
            assertThat(put.headers().get("authorization")).isEqualTo(expected(put, gsp(neptune)));
            assertThat(post.headers().get("authorization")).isEqualTo(expected(post, gsp(neptune)));
            assertThat(count.headers().get("authorization")).isEqualTo(expected(count, sparql));
        }
    }

    @Test
    void withTheSwitchOffNothingIsSigned() throws IOException {
        try (StubNeptune neptune = new StubNeptune()) {
            GraphStore store = new GraphStore(neptune.sparqlUrl(), "", false, new ObjectMapper());
            store.put(GRAPH, ONE);
            assertThat(store.count(GRAPH)).isEqualTo(1);
            assertThat(neptune.seen).hasSize(2);
            assertThat(neptune.seen).allSatisfy(seen -> assertThat(seen.headers()).doesNotContainKeys("authorization", "x-amz-date", "x-amz-content-sha256"));
        }
    }
}
