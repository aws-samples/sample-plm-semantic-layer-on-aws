// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.federation;

import atelier.query.neptune.NeptuneAuth;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.jena.http.HttpEnv;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP;
import org.apache.jena.sparql.exec.http.QuerySendMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SPARQL endpoints of the federation: one Ontop virtual graph per source (each PLM and the Atelier
 * core database) and the link store. Endpoint names ("ontop-fr", ..., "ontop-core", "neptune")
 * are the ones reported in provenance. Every request to them has a connect timeout ({@code atelier.sparql.connect-timeout})
 * and a response timeout ({@code atelier.sparql.timeout}, the time until the endpoint's answer begins); an endpoint
 * that exceeds either fails the request as an unreachable one does. With {@code NEPTUNE_IAM_AUTH} true, every request
 * to the link store is signed with SigV4 under the task role (see {@link NeptuneAuth}); the Ontop endpoints are never
 * signed. The client follows no redirect: the JDK client carries a request's headers to the redirect target, and a
 * signature must reach only the endpoint it was made for.
 */
@Component
public class Endpoints {
    public static final String LINK_STORE = "neptune";

    /** Source code of the Atelier core graph (part tags); its endpoint is named {@code ontop-core}. */
    public static final String CORE = "core";

    private final Map<String, String> ontopBySource = new LinkedHashMap<>();
    private final String linkStoreUrl;
    private final HttpClient http;
    private final Duration timeout;

    @Autowired
    public Endpoints(@Value("${ONTOP_FR_URL}") String fr, @Value("${ONTOP_DE_URL}") String de,
                     @Value("${ONTOP_UK_URL}") String uk, @Value("${ONTOP_ES_URL}") String es,
                     @Value("${ONTOP_CORE_URL}") String core, @Value("${NEPTUNE_SPARQL_URL}") String neptune,
                     @Value("${NEPTUNE_IAM_AUTH:false}") boolean neptuneIamAuth,
                     @Value("${atelier.sparql.connect-timeout:5s}") Duration connectTimeout,
                     @Value("${atelier.sparql.timeout:30s}") Duration timeout) {
        this(fr, de, uk, es, core, neptune, connectTimeout, timeout, neptuneIamAuth);
    }

    /** Endpoints reached with unsigned requests: a link store without IAM authentication (the fixture stack, the tests). */
    public Endpoints(String fr, String de, String uk, String es, String core, String neptune, Duration connectTimeout, Duration timeout) {
        this(fr, de, uk, es, core, neptune, connectTimeout, timeout, false);
    }

    private Endpoints(String fr, String de, String uk, String es, String core, String neptune, Duration connectTimeout, Duration timeout,
                      boolean neptuneIamAuth) {
        ontopBySource.put("fr", fr);
        ontopBySource.put("de", de);
        ontopBySource.put("uk", uk);
        ontopBySource.put("es", es);
        ontopBySource.put(CORE, core);
        this.linkStoreUrl = neptune;
        this.http = NeptuneAuth.client(baseClient(connectTimeout), neptuneIamAuth, neptune);
        this.timeout = timeout;
    }

    /**
     * The client every endpoint is reached with, before signing: Jena's builder with the connect timeout, following no
     * redirect. Jena's own default follows them, and the JDK client carries the request's headers to the redirect
     * target, where a signed {@code Authorization} header must never arrive.
     */
    public static HttpClient baseClient(Duration connectTimeout) {
        return HttpEnv.httpClientBuilder().connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /**
     * {@code query} against the SPARQL endpoint at {@code url}, within the connect and response timeouts. A request to
     * the link store is a form POST, so its text travels in the body the signature covers and never in the URL.
     */
    public QueryExecution execution(String url, String query) {
        QuerySendMode sendMode = url.equals(linkStoreUrl) ? QuerySendMode.asPostForm : QuerySendMode.systemDefault;
        return QueryExecutionHTTP.create().endpoint(url).httpClient(http).sendMode(sendMode)
                .timeout(timeout.toMillis(), TimeUnit.MILLISECONDS).query(query).build();
    }

    /** The HTTP client every endpoint is reached with; it signs the link store's requests when {@code NEPTUNE_IAM_AUTH} is true. */
    public HttpClient httpClient() {
        return http;
    }

    /** URL of the Ontop endpoint of a PLM code or of {@link #CORE}. */
    public String ontop(String source) {
        return ontopBySource.get(source);
    }

    /** The Ontop sources, PLMs first, then {@link #CORE}. */
    public List<String> sources() {
        return List.copyOf(ontopBySource.keySet());
    }

    public String linkStore() {
        return linkStoreUrl;
    }

    public static String ontopName(String source) {
        return "ontop-" + source;
    }

    /** Source of an Ontop endpoint name ("ontop-fr" gives "fr", "ontop-core" gives "core"); null for the link store. */
    public static String sourceOf(String endpointName) {
        return endpointName.startsWith("ontop-") ? endpointName.substring("ontop-".length()) : null;
    }

    /** Endpoint name by URL, in the order they are reported. */
    public Map<String, String> namesByUrl() {
        Map<String, String> names = new LinkedHashMap<>();
        ontopBySource.forEach((source, url) -> names.put(url, ontopName(source)));
        names.put(linkStoreUrl, LINK_STORE);
        return names;
    }
}
