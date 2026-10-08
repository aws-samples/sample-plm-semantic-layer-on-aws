// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

/**
 * One JDK HttpServer standing in for the API Gateway (the PLM and core services under {@code /api/}),
 * the link store's SPARQL endpoint ({@code /query}, answered from an in-memory dataset) and its
 * Graph Store endpoint ({@code /store}, which applies a {@code PUT} or {@code POST} to that dataset
 * so that a write the query service must never make is recorded and visible, not lost in a 404).
 * Records every request it receives in order.
 */
final class StubGateway implements AutoCloseable {
    /** One request received: method, path, query string, the three forwarded headers, content type and body. */
    record Seen(String method, String path, String query, Map<String, String> headers, String contentType, String body) {}

    /** What a gateway route answers: status and JSON body. */
    record Answer(int status, String body) {}

    final List<Seen> seen = new ArrayList<>();
    private final Map<String, Function<Seen, Answer>> routes = new LinkedHashMap<>();
    private final HttpServer server;
    private final Dataset dataset;

    StubGateway(Dataset dataset) {
        this.dataset = dataset;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/api/", this::gateway);
        server.createContext("/query", this::sparql);
        server.createContext("/store", this::graphStore);
        server.start();
    }

    /** Routes {@code "METHOD /api/path"} to an answer computed from the request. */
    StubGateway route(String methodAndPath, Function<Seen, Answer> answer) {
        routes.put(methodAndPath, answer);
        return this;
    }

    StubGateway route(String methodAndPath, int status, String body) {
        return route(methodAndPath, seen -> new Answer(status, body));
    }

    String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** The requests seen under {@code /api/}, in order. */
    List<Seen> gatewayCalls() {
        return seen.stream().filter(s -> s.path().startsWith("/api/")).toList();
    }

    /** The Graph Store requests (PUT or POST on the link store), in order. */
    List<Seen> graphStore() {
        return seen.stream().filter(s -> s.path().equals("/store")).toList();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void gateway(HttpExchange exchange) throws IOException {
        Seen request = record(exchange);
        Function<Seen, Answer> route = routes.get(request.method() + " " + request.path());
        Answer answer = route == null ? new Answer(404, "{\"error\":\"no route " + request.method() + " " + request.path() + "\"}")
                : route.apply(request);
        reply(exchange, answer.status(), "application/json", answer.body());
    }

    private void sparql(HttpExchange exchange) throws IOException {
        Seen request = record(exchange);
        String query = param(request.method().equals("GET") ? request.query() : request.body(), "query");
        try (QueryExecution exec = QueryExecutionFactory.create(query, dataset)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ResultSetFormatter.outputAsJSON(out, exec.execSelect());
            reply(exchange, 200, "application/sparql-results+json", out.toString(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            reply(exchange, 400, "text/plain", e.toString());
        }
    }

    private void graphStore(HttpExchange exchange) throws IOException {
        Seen request = record(exchange);
        String graph = param(request.query(), "graph");
        Model sent = ModelFactory.createDefaultModel();
        RDFDataMgr.read(sent, new StringReader(request.body()), null, Lang.TURTLE);
        switch (request.method()) {
            case "PUT" -> dataset.replaceNamedModel(graph, sent);
            case "POST" -> dataset.getNamedModel(graph).add(sent);
            default -> {
                reply(exchange, 405, "text/plain", "");
                return;
            }
        }
        reply(exchange, 204, "text/plain", "");
    }

    private Seen record(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : List.of("x-origin-verify", "x-atelier-profile", "x-atelier-actor")) {
            headers.put(name, exchange.getRequestHeaders().getFirst(name));
        }
        Seen request = new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), exchange.getRequestURI().getRawQuery(),
                headers, exchange.getRequestHeaders().getFirst("Content-Type"), body);
        synchronized (seen) {
            seen.add(request);
        }
        return request;
    }

    private static void reply(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /** The value of {@code name} in a form-encoded string (URL query or POST body). */
    static String param(String encoded, String name) {
        if (encoded == null) return null;
        for (String pair : encoded.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
