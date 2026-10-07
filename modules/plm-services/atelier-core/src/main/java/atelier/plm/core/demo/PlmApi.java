// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.demo;

import atelier.plm.common.demo.DemoAccess;
import atelier.plm.common.demo.DemoRejectedException;
import atelier.plm.common.policy.Policy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * The site PLM services as the demo reset reaches them: through the API Gateway at
 * {@code PLM_API_BASE} (the base the browser uses, e.g. {@code https://.../api}), with the origin
 * secret in {@value #ORIGIN_HEADER}, the export-control officer in {@value Policy#HEADER} and
 * {@value #ACTOR} in {@value #ACTOR_HEADER}. The PLM's JSON is returned as it came, with its
 * status: the PLM owns validation and execution. A transport failure is 502 naming the PLM, never
 * the URL (the detail is logged here). Without {@code PLM_API_BASE} every call answers 503.
 */
@Component
public class PlmApi {

    static final String ORIGIN_HEADER = "x-origin-verify";
    static final String ACTOR_HEADER = "x-atelier-actor";
    static final String ACTOR = "atelier-core";
    static final Duration TIMEOUT = Duration.ofSeconds(15);
    /** The codes of the PLMs that take value corrections, as the request path names them. */
    public static final List<String> PLMS = List.of("fr", "de", "uk", "es");

    private static final Logger log = LoggerFactory.getLogger(PlmApi.class);

    /** A PLM service answer: HTTP status and body (JSON) as received. */
    public record Reply(int status, String body) {
        public boolean ok() {
            return status / 100 == 2;
        }
    }

    private final String base;
    private final Supplier<String> originSecret;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    public PlmApi(@Value("${PLM_API_BASE:}") String base, OriginSecret originSecret, ObjectMapper json) {
        this(base, (Supplier<String>) originSecret, json);
    }

    /** @param base the API base without trailing slash, blank when the PLM services are not reachable. */
    public PlmApi(String base, Supplier<String> originSecret, ObjectMapper json) {
        this.base = base.isBlank() ? null : base.replaceAll("/+$", "");
        this.originSecret = originSecret;
        this.json = json;
    }

    public boolean configured() {
        return base != null;
    }

    /**
     * {@code POST /{plm}/demo/update} as the officer, {@code body} serialised as JSON. The path is built
     * from the entry of {@link #PLMS} equal to {@code plm}, never from the argument itself; any other
     * value is 400 {@code unknown-plm} and nothing is sent.
     */
    public Reply update(String plm, Object body) {
        String code = PLMS.stream().filter(p -> p.equals(plm)).findFirst()
                .orElseThrow(() -> new DemoRejectedException(HttpStatus.BAD_REQUEST, "not a PLM code: one of " + PLMS + " is required", "unknown-plm"));
        if (base == null) {
            throw new DemoRejectedException(HttpStatus.SERVICE_UNAVAILABLE, "PLM API not configured: PLM_API_BASE is not set",
                    "plm-api-not-configured");
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + "/" + code + "/demo/update"))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header(Policy.HEADER, DemoAccess.OFFICER)
                .header(ACTOR_HEADER, ACTOR);
        String secret = originSecret.get();
        if (secret != null) {
            request.header(ORIGIN_HEADER, secret);
        }
        try {
            HttpResponse<String> response = http.send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(), response.body());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialise the update", e);
        } catch (IOException e) {
            log.warn("PLM service call {}/demo/update failed: {}", code, e.toString());
            throw new DemoRejectedException(HttpStatus.BAD_GATEWAY, "the " + code.toUpperCase(Locale.ROOT) + " PLM did not answer", "plm-service");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DemoRejectedException(HttpStatus.BAD_GATEWAY, "interrupted while waiting for the " + code.toUpperCase(Locale.ROOT) + " PLM",
                    "plm-service");
        }
    }
}
