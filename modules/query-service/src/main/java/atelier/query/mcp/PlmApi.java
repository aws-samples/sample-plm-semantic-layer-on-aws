// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mcp;

import atelier.query.api.QueryController;
import atelier.query.policy.Caller;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The PLM and core services as the {@code catalogue} and {@code sql} tools and the change list
 * reach them: through the API Gateway at {@code PLM_API_BASE} (the base the browser uses, e.g.
 * {@code https://.../api}), with the origin secret in {@code x-origin-verify}, the caller's profile
 * in {@code x-atelier-profile} and its actor in {@code x-atelier-actor}. A path is relative to the base
 * ({@code uk/catalogue}, {@code core/changes}).
 * The service's JSON is returned as it came, with its status: the PLM service owns validation and
 * execution, so a 400 with its reason and suggestions reaches the caller unchanged. A transport
 * failure is reported without its detail (which is logged here). Without {@code PLM_API_BASE}
 * every call reports the services unavailable.
 */
@Component
public class PlmApi {
    static final String ORIGIN_HEADER = "x-origin-verify";
    private static final Logger log = LoggerFactory.getLogger(PlmApi.class);
    static final Duration TIMEOUT = Duration.ofSeconds(15);

    /** A PLM service answer: HTTP status and body (JSON) as received. */
    public record Reply(int status, String body) {
        public boolean ok() {
            return status / 100 == 2;
        }
    }

    private final String base;
    private final Supplier<String> originSecret;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    public PlmApi(@Value("${PLM_API_BASE:}") String base, OriginSecret originSecret) {
        this(base, (Supplier<String>) originSecret);
    }

    /** @param base the API base without trailing slash, blank when the PLM services are not reachable. */
    public PlmApi(String base, Supplier<String> originSecret) {
        this.base = base.isBlank() ? null : base.replaceAll("/+$", "");
        this.originSecret = originSecret;
    }

    public boolean configured() {
        return base != null;
    }

    /** {@code GET /{plm}/catalogue}. */
    public Reply catalogue(String plm, Caller caller) {
        return get(plm + "/catalogue", caller);
    }

    /** {@code POST /{plm}/sql} with {@code { sql, purpose }}. */
    public Reply sql(String plm, String sql, String purpose, Caller caller) {
        Map<String, String> statement = new LinkedHashMap<>();
        statement.put("sql", sql);
        statement.put("purpose", purpose);
        return post(plm + "/sql", statement, caller);
    }

    public Reply get(String path, Caller caller) {
        return send(request(path, caller).GET().build());
    }

    /** POST with {@code body} serialised as JSON (a Jackson tree or any bean). */
    public Reply post(String path, Object body, Caller caller) {
        try {
            return send(request(path, caller).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private HttpRequest.Builder request(String path, Caller caller) {
        if (base == null) {
            throw new IllegalStateException("PLM_API_BASE is not set: the PLM and core services are not reachable here");
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + "/" + path)).timeout(TIMEOUT)
                .header("Accept", "application/json").header(QueryController.PROFILE_HEADER, caller.profile().name())
                .header(QueryController.ACTOR_HEADER, caller.actor());
        String secret = originSecret.get();
        return secret == null ? request : request.header(ORIGIN_HEADER, secret);
    }

    private Reply send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(), response.body());
        } catch (IOException e) {
            log.warn("PLM service call {} failed: {}", request.uri().getPath(), e.toString());
            throw new IllegalStateException("the PLM service did not answer");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the PLM service");
        }
    }
}
