// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A JDK HttpServer standing in for the API Gateway in front of the PLM services: every request under
 * {@code /api/} is recorded (method, path, headers by lower-case name, body) and answered 200 with a
 * PLM-shaped JSON body; {@link #failWith} makes the {@link #failAt}-th request (every request when
 * {@code failAt} is 0) answer that status with {@link #failBody}.
 */
final class StubPlmApi implements AutoCloseable {

    /** One request received: HTTP method, path, headers by lower-case name and body. */
    record Seen(String method, String path, Map<String, String> headers, String body) {
    }

    final List<Seen> seen = new ArrayList<>();
    volatile int failWith = 0;
    volatile int failAt = 0;
    volatile String failBody = "{\"error\":\"stub failure\",\"reason\":\"stub-failure\"}";

    private final HttpServer server;

    StubPlmApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/", this::handle);
        server.start();
    }

    /** The API base as {@code PLM_API_BASE} names it. */
    String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
    }

    void clear() {
        seen.clear();
        failWith = 0;
        failAt = 0;
    }

    private void handle(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new TreeMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), values.get(0)));
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        seen.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers, body));
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (failWith != 0 && (failAt == 0 || seen.size() == failAt)) {
            answer(exchange, failWith, failBody);
            return;
        }
        String plm = exchange.getRequestURI().getPath().split("/")[2].toUpperCase(Locale.ROOT);
        answer(exchange, 200, "{\"plm\":\"" + plm + "\",\"eventId\":\"evt-" + seen.size() + "\"}");
    }

    private static void answer(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
