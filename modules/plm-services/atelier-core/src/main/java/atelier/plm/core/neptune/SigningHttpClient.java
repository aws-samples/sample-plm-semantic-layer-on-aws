// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.neptune;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * An {@link HttpClient} that signs, with {@link NeptuneSigV4}, every request whose URI the predicate accepts (the
 * Neptune endpoint) before handing it to the client it wraps; any other request passes unchanged. The body of a
 * signed request is read from its publisher, so the bytes signed are the bytes sent. Every path through the JDK
 * client, {@code send} and both {@code sendAsync}, signs.
 */
public final class SigningHttpClient extends HttpClient {
    private final HttpClient delegate;
    private final Predicate<URI> signs;
    private final NeptuneSigV4 signer;

    public SigningHttpClient(HttpClient delegate, Predicate<URI> signs, NeptuneSigV4 signer) {
        this.delegate = delegate;
        this.signs = signs;
        this.signer = signer;
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException {
        return delegate.send(prepare(request), handler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        return delegate.sendAsync(prepare(request), handler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                                                            HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        return delegate.sendAsync(prepare(request), handler, pushPromiseHandler);
    }

    private HttpRequest prepare(HttpRequest request) {
        return signs.test(request.uri()) ? signer.sign(request, body(request)) : request;
    }

    /** The bytes a request's body publisher emits; none when the request has no body. */
    static byte[] body(HttpRequest request) {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElse(null);
        if (publisher == null) {
            return new byte[0];
        }
        CompletableFuture<byte[]> bytes = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            private final ByteArrayOutputStream out = new ByteArrayOutputStream();

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                out.writeBytes(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                bytes.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                bytes.complete(out.toByteArray());
            }
        });
        return bytes.orTimeout(10, TimeUnit.SECONDS).join();
    }

    // The client's settings are the wrapped client's.

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return delegate.cookieHandler();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return delegate.connectTimeout();
    }

    @Override
    public Redirect followRedirects() {
        return delegate.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return delegate.proxy();
    }

    @Override
    public SSLContext sslContext() {
        return delegate.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return delegate.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return delegate.authenticator();
    }

    @Override
    public Version version() {
        return delegate.version();
    }

    @Override
    public Optional<Executor> executor() {
        return delegate.executor();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
