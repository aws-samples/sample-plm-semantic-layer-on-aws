// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.neptune;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

/**
 * The switch {@code NEPTUNE_IAM_AUTH}. On, every request to the Neptune endpoint (its scheme, host and port) is signed
 * with SigV4 under the task role, resolved by the default credentials provider chain, for the region named by
 * {@code AWS_REGION} or, without it, by the endpoint's host name. Off, the client is used as it is: a link store
 * without IAM authentication, the local fixture stack.
 */
public final class NeptuneAuth {
    private static final Pattern REGION_IN_HOST = Pattern.compile("\\.([a-z]{2}-[a-z]+-\\d)\\.neptune\\.amazonaws\\.com$");

    private NeptuneAuth() {
    }

    /** {@code base} as it is, or wrapped so that every request to {@code endpoint} is signed. */
    public static HttpClient client(HttpClient base, boolean iamAuth, String endpoint) {
        if (!iamAuth) {
            return base;
        }
        URI uri = URI.create(endpoint);
        NeptuneSigV4 signer = new NeptuneSigV4(DefaultCredentialsProvider.builder().build(), region(uri, System.getenv("AWS_REGION")), Clock.systemUTC());
        return new SigningHttpClient(base, signs(uri), signer);
    }

    /** Whether a URI is on the endpoint's scheme, host and port. */
    public static Predicate<URI> signs(URI endpoint) {
        String origin = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
        return uri -> {
            String s = uri.toString();
            return s.equals(origin) || s.startsWith(origin + "/");
        };
    }

    /** The cluster's region: {@code envRegion} (the value of {@code AWS_REGION}), else the region in the endpoint's host name. */
    public static String region(URI endpoint, String envRegion) {
        if (envRegion != null && !envRegion.isBlank()) {
            return envRegion;
        }
        Matcher host = REGION_IN_HOST.matcher(endpoint.getHost() == null ? "" : endpoint.getHost());
        if (host.find()) {
            return host.group(1);
        }
        throw new IllegalStateException("NEPTUNE_IAM_AUTH is on but neither AWS_REGION nor the host of the Neptune endpoint names a region");
    }
}
