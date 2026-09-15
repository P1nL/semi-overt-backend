package com.platform.content.service.ai;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.io.IOException;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** LangChain4j HTTP SPI: keep the existing response cap, total deadline and no-redirect policy. */
final class BoundedLangChainHttpClient implements HttpClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final java.net.http.HttpClient SHARED = createTransport(CONNECT_TIMEOUT, null);
    private final java.net.http.HttpClient transport;
    private final Duration timeout;

    private BoundedLangChainHttpClient(java.net.http.HttpClient transport, Duration timeout) {
        this.transport = transport;
        this.timeout = timeout;
    }

    private static java.net.http.HttpClient createTransport(Duration connectTimeout, URI proxyUrl) {
        var builder = java.net.http.HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER);
        if (proxyUrl != null) {
            builder.proxy(ProxySelector.of(InetSocketAddress.createUnresolved(proxyUrl.getHost(), proxyUrl.getPort())));
        }
        return builder.build();
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        java.net.http.HttpRequest wireRequest;
        try {
            var builder = java.net.http.HttpRequest.newBuilder(URI.create(request.url())).timeout(timeout);
            request.headers().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
            wireRequest = builder.method(request.method().name(), request.body() == null
                    ? java.net.http.HttpRequest.BodyPublishers.noBody()
                    : java.net.http.HttpRequest.BodyPublishers.ofString(request.body())).build();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid model HTTP configuration");
        }
        var pending = transport.sendAsync(wireRequest, ignored -> new BoundedBodySubscriber());
        HttpResponse<byte[]> response;
        try {
            response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new RequestTimeoutException();
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new RequestInterruptedException();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof java.net.http.HttpTimeoutException) throw new RequestTimeoutException();
            throw new IllegalStateException("Model HTTP request failed", e.getCause());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new HttpException(response.statusCode(), "DeepSeek HTTP request failed");
        return SuccessfulHttpResponse.builder().statusCode(response.statusCode())
                .headers(response.headers().map()).body(response.body()).build();
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        throw new UnsupportedOperationException("Article polish uses non-streaming JSON responses");
    }

    static final class Builder implements HttpClientBuilder {
        private Duration connectTimeout = CONNECT_TIMEOUT;
        private Duration readTimeout;
        private final URI proxyUrl;
        Builder(Duration readTimeout, URI proxyUrl) { this.readTimeout = readTimeout; this.proxyUrl = proxyUrl; }
        public Duration connectTimeout() { return connectTimeout; }
        public HttpClientBuilder connectTimeout(Duration timeout) { connectTimeout = timeout; return this; }
        public Duration readTimeout() { return readTimeout; }
        public HttpClientBuilder readTimeout(Duration timeout) { readTimeout = timeout; return this; }
        public HttpClient build() {
            boolean shared = proxyUrl == null && CONNECT_TIMEOUT.equals(connectTimeout);
            return new BoundedLangChainHttpClient(shared ? SHARED : createTransport(connectTimeout, proxyUrl), readTimeout);
        }
    }

    static final class RequestTimeoutException extends RuntimeException {
        RequestTimeoutException() { super("Model HTTP request timed out"); }
    }
    static final class RequestInterruptedException extends RuntimeException {
        RequestInterruptedException() { super("Model HTTP request interrupted"); }
    }

    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long bytes;
        private boolean failed;
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        public void onNext(List<ByteBuffer> chunks) {
            if (failed) return;
            for (ByteBuffer chunk : chunks) bytes += chunk.remaining();
            if (bytes > 1_048_576) {
                failed = true;
                subscription.cancel();
                delegate.onError(new IOException("Provider response exceeds limit"));
                return;
            }
            delegate.onNext(chunks);
        }
        public void onError(Throwable error) { if (!failed) delegate.onError(error); }
        public void onComplete() { if (!failed) delegate.onComplete(); }
    }
}
