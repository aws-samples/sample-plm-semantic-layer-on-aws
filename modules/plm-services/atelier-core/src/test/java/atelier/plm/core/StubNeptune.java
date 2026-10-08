// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A JDK HttpServer standing in for Neptune: the Graph Store Protocol at {@code /sparql/gsp/?graph=}
 * ({@code PUT} replaces the named graph, {@code POST} adds to it, each body parsed as Turtle into a
 * Jena model per graph) and the SPARQL endpoint at {@code /sparql} answering the
 * {@code SELECT (COUNT(*) AS ?n) ... GRAPH <g>} the core sends with that model's size. Records every
 * request; {@link #failWith} makes the next requests answer that status.
 */
final class StubNeptune implements AutoCloseable {

    /**
     * One request received: HTTP method, path, the {@code graph} query parameter (GSP) or the SPARQL query, content type,
     * body (its exact bytes under {@code bytes}) and headers (lowercased names, first values).
     */
    record Seen(String method, String path, String graph, String contentType, String body, byte[] bytes, Map<String, String> headers) {
    }

    private static final Pattern GRAPH_IN_QUERY = Pattern.compile("GRAPH <([^>]+)>");

    final List<Seen> seen = new ArrayList<>();
    final Map<String, Model> graphs = new ConcurrentHashMap<>();
    volatile int failWith = 0;

    private final HttpServer server;

    StubNeptune() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sparql/gsp/", this::graphStore);
        server.createContext("/sparql", this::sparql);
        server.start();
    }

    String sparqlUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/sparql";
    }

    long size(String graph) {
        Model model = graphs.get(graph);
        return model == null ? 0 : model.size();
    }

    void load(String graph, byte[] turtle) {
        graphs.put(graph, parse(turtle));
    }

    void clear() {
        seen.clear();
        graphs.clear();
        failWith = 0;
    }

    private void graphStore(HttpExchange exchange) throws IOException {
        String graph = param(exchange.getRequestURI().getQuery(), "graph");
        byte[] body = exchange.getRequestBody().readAllBytes();
        seen.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), graph,
                exchange.getRequestHeaders().getFirst("Content-Type"), new String(body, StandardCharsets.UTF_8), body, headers(exchange)));
        if (failWith != 0) {
            answer(exchange, failWith, "stub failure");
            return;
        }
        Model incoming = parse(body);
        switch (exchange.getRequestMethod()) {
            case "PUT" -> graphs.put(graph, incoming);
            case "POST" -> graphs.computeIfAbsent(graph, g -> ModelFactory.createDefaultModel()).add(incoming);
            default -> {
                answer(exchange, 405, "method not allowed");
                return;
            }
        }
        answer(exchange, "PUT".equals(exchange.getRequestMethod()) ? 204 : 200, "");
    }

    private void sparql(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        String body = new String(bytes, StandardCharsets.UTF_8);
        String query = param(body, "query");
        Matcher graph = GRAPH_IN_QUERY.matcher(query == null ? "" : query);
        String iri = graph.find() ? graph.group(1) : null;
        seen.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), iri,
                exchange.getRequestHeaders().getFirst("Content-Type"), query, bytes, headers(exchange)));
        if (failWith != 0) {
            answer(exchange, failWith, "stub failure");
            return;
        }
        exchange.getResponseHeaders().add("Content-Type", "application/sparql-results+json");
        answer(exchange, 200, "{\"head\":{\"vars\":[\"n\"]},\"results\":{\"bindings\":[{\"n\":{\"type\":\"literal\","
                + "\"datatype\":\"http://www.w3.org/2001/XMLSchema#integer\",\"value\":\"" + size(iri) + "\"}}]}}");
    }

    private static Map<String, String> headers(HttpExchange exchange) {
        Map<String, String> headers = new java.util.TreeMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(java.util.Locale.ROOT), values.get(0)));
        return headers;
    }

    private static String param(String encoded, String name) {
        if (encoded == null) {
            return null;
        }
        for (String pair : encoded.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static Model parse(byte[] turtle) {
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, new ByteArrayInputStream(turtle), Lang.TURTLE);
        return model;
    }

    private static void answer(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
