// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import atelier.query.demo.LiveGraphs;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * An endpoint that accepts the connection and never answers fails its request once the configured timeout has
 * passed, the way an endpoint that refuses the connection fails: an {@link EndpointFailure} naming the endpoint
 * from the federation, "the link store did not answer" from the change feed.
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class EndpointTimeoutTest {
    private static final String CONSTRUCT = "CONSTRUCT WHERE { ?s ?p ?o }";
    private static final long BOUND_MS = 5_000;

    private final List<Socket> accepted = new ArrayList<>();
    private ServerSocket silent;
    private String refusingUrl;

    @BeforeEach
    void start() throws IOException {
        // nosemgrep: java.lang.security.audit.crypto.unencrypted-socket, java.lang.security.audit.crypto.unencrypted-socket.unencrypted-socket -- a loopback test server standing in for a SPARQL endpoint; no data crosses it
        silent = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread.ofVirtual().start(() -> {
            try {
                while (true) accepted.add(silent.accept());
            } catch (IOException closed) {
                // the test is over
            }
        });
        // nosemgrep: java.lang.security.audit.crypto.unencrypted-socket, java.lang.security.audit.crypto.unencrypted-socket.unencrypted-socket -- a loopback test server bound only to learn a free port that refuses connections
        try (ServerSocket closed = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            refusingUrl = "http://127.0.0.1:" + closed.getLocalPort() + "/query";
        }
    }

    @AfterEach
    void stop() throws IOException {
        silent.close();
        for (Socket socket : accepted) socket.close();
    }

    private String silentUrl() {
        return "http://127.0.0.1:" + silent.getLocalPort() + "/query";
    }

    private static Endpoints endpoints(String linkStore) {
        return new Endpoints("http://fr/sparql", "http://de/sparql", "http://uk/sparql", "http://es/sparql",
                "http://core/sparql", linkStore, Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    private static EndpointFailure federationFailure(String linkStore) {
        Federator federator = new Federator(endpoints(linkStore));
        try {
            federator.send(new CallRecorder(federator.endpoints().namesByUrl()), linkStore, CONSTRUCT);
        } catch (EndpointFailure failure) {
            return failure;
        }
        throw new AssertionError("the request to " + linkStore + " succeeded");
    }

    @Test
    void silentEndpointFailsItsArmLikeARefusingOne() {
        EndpointFailure refused = federationFailure(refusingUrl);

        long start = System.nanoTime();
        EndpointFailure timedOut = federationFailure(silentUrl());
        long ms = (System.nanoTime() - start) / 1_000_000;

        assertThat(ms).as("time until the silent endpoint's request failed").isLessThan(BOUND_MS);
        assertThat(timedOut.endpoint()).isEqualTo(refused.endpoint()).isEqualTo(Endpoints.LINK_STORE);
        assertThat(timedOut.getMessage()).isEqualTo(refused.getMessage());
    }

    @Test
    void silentLinkStoreFailsTheChangeFeedLikeARefusingOne() {
        String graph = "https://example.com/atelier/graph/links";
        assertThatThrownBy(() -> new LiveGraphs(endpoints(refusingUrl)).graph(graph))
                .isInstanceOf(IllegalStateException.class).hasMessage("the link store did not answer");

        long start = System.nanoTime();
        assertThatThrownBy(() -> new LiveGraphs(endpoints(silentUrl())).graph(graph))
                .isInstanceOf(IllegalStateException.class).hasMessage("the link store did not answer");
        assertThat((System.nanoTime() - start) / 1_000_000).as("time until the silent link store's read failed").isLessThan(BOUND_MS);
    }
}
