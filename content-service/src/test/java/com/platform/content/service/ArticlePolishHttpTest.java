package com.platform.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.content.api.req.ArticlePolishReq;
import com.platform.content.api.req.ArticlePolishReq.Segment;
import com.platform.content.config.ArticlePolishProperties;
import com.platform.content.controller.ArticlePolishController;
import com.platform.content.service.ai.ArticlePolishClient;
import com.platform.kernel.exception.BusinessException;
import com.platform.web.support.exception.GlobalExceptionHandler;
import com.platform.web.support.security.HeaderAuthenticationFilter;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP model adapter behind the MVC/auth/validation chain; provider is a local deterministic fixture. */
class ArticlePolishHttpTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ArticlePolishProperties config = new ArticlePolishProperties();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger delayMillis = new AtomicInteger();
    private final AtomicReference<String> response = new AtomicReference<>();
    private final AtomicReference<String> received = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private HttpServer provider;
    private ArticlePolishClient client;
    private MockMvc mvc;
    private final ArticlePolishReq input = new ArticlePolishReq(List.of(new Segment("0.0", "原来的文字")));

    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        com.sun.net.httpserver.HttpHandler handler = exchange -> {
            calls.incrementAndGet();
            requestPath.set(exchange.getRequestURI().getPath());
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if (delayMillis.get() > 0) {
                try { Thread.sleep(delayMillis.get()); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        };
        provider.createContext("/chat/completions", handler);
        provider.createContext("/v1/chat/completions", handler);
        provider.start();
        config.setBaseUrl("http://127.0.0.1:" + provider.getAddress().getPort());
        config.setApiKey("local-test-key");
        config.setTimeoutSeconds(5);
        client = new ArticlePolishClient(config, mapper);
        response.set(envelope(Map.of("segments", List.of(Map.of("id", "0.0", "text", "润色后的文字"))), "stop"));
        var redis = mock(StringRedisTemplate.class);
        doReturn(1L).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        mvc = MockMvcBuilders.standaloneSetup(new ArticlePolishController(new ArticlePolishService(client, redis)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new HeaderAuthenticationFilter("local-internal-test-token")).build();
    }
    @AfterEach void stop() { if (provider != null) provider.stop(0); }
    private String envelope(Object content, String finishReason) throws Exception {
        return mapper.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", finishReason,
                "message", Map.of("content", mapper.writeValueAsString(content))))));
    }
    @Test void authenticatedEndpointReachesProviderAndReturnsTypedSuggestion() throws Exception {
        mvc.perform(post("/api/v1/articles/ai-polish").header("X-Internal-Token", "local-internal-test-token")
                        .header("X-User-Id", "12").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(input)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.segments[0].text").value("润色后的文字"));
        assertEquals("Bearer local-test-key", authorization.get());
        var body = mapper.readTree(received.get());
        assertEquals("deepseek-flash", body.path("model").asText());
        assertEquals("json_object", body.path("response_format").path("type").asText());
        assertEquals("disabled", body.path("thinking").path("type").asText());
        assertFalse(body.path("stream").asBoolean());
        assertEquals("原来的文字", mapper.readTree(body.path("messages").path(1).path("content").asText())
                .path("segments").path(0).path("text").asText());
    }
    @Test void unauthenticatedAndForgedIdentityCannotReachModel() throws Exception {
        for (String token : List.of("", "wrong-token")) {
            mvc.perform(post("/api/v1/articles/ai-polish").header("X-Internal-Token", token).header("X-User-Id", "12")
                            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(input)))
                    .andExpect(status().isUnauthorized());
        }
        assertEquals(0, calls.get());
    }
    @Test void beanValidationRejectsMalformedRequestsBeforePaidCall() throws Exception {
        for (String body : List.of("{}", "{\"segments\":[]}", "{\"segments\":[null]}",
                "{\"segments\":[{\"id\":\"0.0\",\"text\":\"\"}]}",
                "{\"segments\":[{\"id\":\"bad\",\"text\":\"body\"}]}")) {
            mvc.perform(post("/api/v1/articles/ai-polish").header("X-Internal-Token", "local-internal-test-token")
                            .header("X-User-Id", "12").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, calls.get());
    }
    @Test void providerFailureDoesNotLeakResponseBodyOrKey() {
        for (int code : new int[]{401, 403, 500}) {
            status.set(code); response.set("provider-secret-local-test-key");
            int before = calls.get();
            var error = assertThrows(BusinessException.class, () -> client.generate(input));
            assertEquals(502, error.getCode());
            assertFalse(error.getMessage().contains("local-test-key"));
            assertEquals(before + 1, calls.get(), "LangChain4j must not retry potentially billable requests");
        }
        status.set(429);
        assertEquals(429, assertThrows(BusinessException.class, () -> client.generate(input)).getCode());
    }
    @Test void rejectsTruncatedMalformedAndNonTextProviderContent() throws Exception {
        for (String output : List.of("", "not json", envelope(Map.of("segments", List.of()), "length"),
                envelope(Map.of("segments", List.of(Map.of("id", "0.0", "text", 123))), "stop"))) {
            response.set(output);
            assertEquals(502, assertThrows(BusinessException.class, () -> client.generate(input)).getCode());
        }
    }
    @Test void boundedBodyAndNoRedirectProtectCredentials() {
        response.set("x".repeat(1_048_577));
        assertEquals(502, assertThrows(BusinessException.class, () -> client.generate(input)).getCode());
        status.set(302); response.set("redirect");
        assertEquals(502, assertThrows(BusinessException.class, () -> client.generate(input)).getCode());
    }
    @Test void configurationIsExplicitAndRejectsInsecureRemoteEndpoints() {
        for (String endpoint : List.of("", "not-a-url", "http://example.com", "https://user:secret@example.com/path", "https://example.com?key=secret")) {
            config.setBaseUrl(endpoint);
            assertEquals(503, assertThrows(BusinessException.class, client::checkConfigured).getCode());
        }
        assertEquals(0, calls.get());
    }

    @Test void completeRequestHasBoundedTimeout() {
        delayMillis.set(6500);
        long started = System.nanoTime();
        assertEquals(504, assertThrows(BusinessException.class, () -> client.generate(input)).getCode());
        assertTrue(java.time.Duration.ofNanos(System.nanoTime() - started).toMillis() < 6200);
    }

    @Test void legacyFullEndpointStillOverridesDefaultBaseUrl() {
        config.setBaseUrl("https://unused.invalid");
        config.setEndpoint("http://127.0.0.1:" + provider.getAddress().getPort() + "/chat/completions");
        assertEquals("润色后的文字", client.generate(input).segments().get(0).text());
        assertEquals(1, calls.get());
    }

    @Test void defaultsOnlyRequireADeepSeekKey() {
        var defaults = new ArticlePolishProperties();
        assertEquals("https://api.deepseek.com", defaults.getBaseUrl());
        assertEquals("deepseek-flash", defaults.getModel());
        var adapter = new ArticlePolishClient(defaults, mapper);
        assertEquals(503, assertThrows(BusinessException.class, adapter::checkConfigured).getCode());
        defaults.setApiKey("local-test-key");
        assertDoesNotThrow(adapter::checkConfigured);
        assertEquals(0, calls.get());
    }

    @Test void proxyConfigurationIsExplicitAndValidatedWithoutLeakingCredentials() {
        var proxyConfig = new ArticlePolishProperties();
        proxyConfig.setApiKey("local-test-key");
        var adapter = new ArticlePolishClient(proxyConfig, mapper);
        for (String proxy : List.of("https://127.0.0.1:7890", "http://127.0.0.1", "http://user:secret@127.0.0.1:7890", "not-a-url")) {
            proxyConfig.setProxyUrl(proxy);
            var error = assertThrows(BusinessException.class, adapter::checkConfigured);
            assertEquals(503, error.getCode());
            assertFalse(error.getMessage().contains("secret"));
        }
        proxyConfig.setProxyUrl("http://127.0.0.1:7890");
        assertDoesNotThrow(adapter::checkConfigured);
    }

    @Test void optionalV1PrefixAndTrailingSlashAreNotDuplicated() {
        config.setBaseUrl("http://127.0.0.1:" + provider.getAddress().getPort() + "/v1/");
        assertEquals("润色后的文字", client.generate(input).segments().get(0).text());
        assertEquals("/v1/chat/completions", requestPath.get());
    }

    @Test void refusalAlongsideValidContentIsNotApplied() throws Exception {
        var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(response.get());
        ((com.fasterxml.jackson.databind.node.ObjectNode) envelope.path("choices").path(0).path("message"))
                .put("refusal", "not allowed");
        response.set(mapper.writeValueAsString(envelope));
        assertEquals(502, assertThrows(BusinessException.class, () -> client.generate(input)).getCode());
    }

    @Test void deepSeekEnvironmentOverridesLegacyConfiguration() throws Exception {
        var environment = new org.springframework.mock.env.MockEnvironment()
                .withProperty("AI_POLISH_API_KEY", "legacy-fixture-key")
                .withProperty("AI_POLISH_MODEL", "legacy-fixture-model");
        var source = new org.springframework.boot.env.YamlPropertySourceLoader()
                .load("polish-config", new org.springframework.core.io.ClassPathResource("application.yml")).get(0);
        environment.getPropertySources().addLast(source);
        assertEquals("legacy-fixture-key", environment.getProperty("platform.ai.polish.api-key"));
        assertEquals("legacy-fixture-model", environment.getProperty("platform.ai.polish.model"));
        environment.withProperty("DEEPSEEK_API_KEY", "deepseek-fixture-key")
                .withProperty("DEEPSEEK_MODEL", "deepseek-pro")
                .withProperty("DEEPSEEK_BASE_URL", "http://127.0.0.1:9999/v1");
        assertEquals("deepseek-fixture-key", environment.getProperty("platform.ai.polish.api-key"));
        assertEquals("deepseek-pro", environment.getProperty("platform.ai.polish.model"));
        assertEquals("http://127.0.0.1:9999/v1", environment.getProperty("platform.ai.polish.base-url"));
    }
}
