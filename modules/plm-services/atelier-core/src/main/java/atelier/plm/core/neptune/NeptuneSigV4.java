// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.neptune;

import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;

/**
 * Signature Version 4 for Amazon Neptune's data plane (service {@code neptune-db}), with the AWS SDK's signer: the
 * signature covers the method, the path, the query string, the host with its port, every header of the request, the
 * signing time and the hash of the body, under the credentials the provider resolves (the task role).
 */
public final class NeptuneSigV4 {
    public static final String SERVICE = "neptune-db";

    /** Headers the JDK client sets itself and refuses from the caller; the signer's values for them are left to it. */
    private static final Set<String> RESTRICTED = Set.of("host", "content-length", "connection", "expect", "upgrade");

    private final AwsV4HttpSigner signer = AwsV4HttpSigner.create();
    private final AwsCredentialsProvider credentials;
    private final String region;
    private final Clock clock;

    public NeptuneSigV4(AwsCredentialsProvider credentials, String region, Clock clock) {
        this.credentials = credentials;
        this.region = region;
        this.clock = clock;
    }

    /**
     * {@code request} with its signature: {@code Authorization}, {@code X-Amz-Date} and, with session credentials,
     * {@code X-Amz-Security-Token} added to its headers, and {@code body}, the bytes exactly as they are sent, as its
     * body. The host is signed as the JDK client sends it (host and port) and left to the client.
     */
    public HttpRequest sign(HttpRequest request, byte[] body) {
        SdkHttpRequest.Builder unsigned = SdkHttpRequest.builder()
                .method(SdkHttpMethod.fromValue(request.method()))
                .uri(request.uri());
        request.headers().map().forEach(unsigned::putHeader);
        SignedRequest signed = signer.sign(r -> r.identity(credentials.resolveCredentials())
                .request(unsigned.build())
                .payload(ContentStreamProvider.fromByteArray(body))
                .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, SERVICE)
                .putProperty(AwsV4HttpSigner.REGION_NAME, region)
                .putProperty(HttpSigner.SIGNING_CLOCK, clock));
        HttpRequest.Builder result = HttpRequest.newBuilder(request, (name, value) -> true)
                .method(request.method(), body.length == 0 ? BodyPublishers.noBody() : BodyPublishers.ofByteArray(body));
        Set<String> present = request.headers().map().keySet().stream().map(n -> n.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        signed.request().headers().forEach((String name, List<String> values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!RESTRICTED.contains(lower) && !present.contains(lower)) {
                values.forEach(value -> result.header(name, value));
            }
        });
        return result.build();
    }
}
