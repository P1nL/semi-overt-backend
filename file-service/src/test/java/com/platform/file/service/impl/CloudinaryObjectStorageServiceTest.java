package com.platform.file.service.impl;

import com.platform.file.config.StorageConfig;
import com.platform.file.support.TestImageFixtures;
import com.platform.kernel.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CloudinaryObjectStorageServiceTest {

    @Test
    void sendsOfficialUploadEndpointBasicAuthAndValidatedMultipartProtocol() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        StorageConfig config = config();
        CloudinaryObjectStorageService service = new CloudinaryObjectStorageService(builder, config);
        String auth = "Basic " + Base64.getEncoder().encodeToString("test-key:test-secret".getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/upload"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.startsWith("multipart/form-data;")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("public_id")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("overwrite")))
                .andRespond(withSuccess("{\"secure_url\":\"https://res.cloudinary.com/demo/image/upload/v1/semi-overt/object.png\"}",
                        MediaType.APPLICATION_JSON));

        String url = service.store("2026/09/11/object.png", TestImageFixtures.png(java.awt.Color.BLUE), "image/png");

        assertThat(url).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/semi-overt/object.png");
        server.verify();
        assertThat(config.toString()).doesNotContain("test-secret", "test-key");
    }

    @Test
    void sendsBasicAuthMultipartToLoopbackHttpMockWithoutExternalNetwork() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1_1/demo/image/upload", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"secure_url\":\"https://res.cloudinary.com/demo/image/upload/v1/semi-overt/object.png\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try {
            StorageConfig config = config();
            config.getCloudinary().setApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            var httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            var factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(Duration.ofSeconds(5));
            CloudinaryObjectStorageService service = new CloudinaryObjectStorageService(
                    RestClient.builder().requestFactory(factory), config);

            assertThat(service.store("2026/09/11/object.png", TestImageFixtures.png(java.awt.Color.BLUE), "image/png"))
                    .isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/semi-overt/object.png");
            assertThat(authorization).hasValue("Basic " + Base64.getEncoder()
                    .encodeToString("test-key:test-secret".getBytes(StandardCharsets.UTF_8)));
            assertThat(requestBody).hasValueSatisfying(body -> {
                assertThat(body).contains("name=\"file\"");
                assertThat(body).contains("name=\"public_id\"");
                assertThat(body).contains("name=\"overwrite\"");
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sanitizesProviderFailureAndDoesNotExposeProviderBody() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CloudinaryObjectStorageService service = new CloudinaryObjectStorageService(builder, config());
        server.expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/upload"))
                .andRespond(withServerError().body("provider-secret-error-body"));

        assertThatThrownBy(() -> service.store("2026/09/11/object.png",
                TestImageFixtures.png(java.awt.Color.BLUE), "image/png"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cloudinary image upload failed")
                .hasNoCause();
        server.verify();
    }

    @Test
    void failsClosedWhenCredentialsAreMissing() {
        StorageConfig config = config();
        config.getCloudinary().setApiSecret("");

        assertThatThrownBy(() -> new CloudinaryObjectStorageService(RestClient.builder(), config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api-secret");
    }

    @Test
    void rejectsProviderDeliveryUrlOutsideConfiguredCloudinaryHost() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CloudinaryObjectStorageService service = new CloudinaryObjectStorageService(builder, config());
        server.expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/upload"))
                .andRespond(withSuccess("{\"secure_url\":\"https://evil.example/steal\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.store("2026/09/11/object.png",
                TestImageFixtures.png(java.awt.Color.BLUE), "image/png"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid delivery URL");
        server.verify();
    }

    @Test
    void rejectsNonLoopbackHttpApiBaseUrlToPreventPlaintextSecretDisclosure() {
        StorageConfig config = config();
        config.getCloudinary().setApiBaseUrl("http://evil.example");

        assertThatThrownBy(() -> new CloudinaryObjectStorageService(RestClient.builder(), config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS URL");
    }

    @Test
    void permitsLoopbackHttpOnlyForLocalMockServer() {
        StorageConfig config = config();
        config.getCloudinary().setApiBaseUrl("http://127.0.0.1:18099");

        assertThatCode(() -> new CloudinaryObjectStorageService(RestClient.builder(), config))
                .doesNotThrowAnyException();
    }

    private StorageConfig config() {
        StorageConfig config = new StorageConfig();
        config.setType("cloudinary");
        config.getCloudinary().setCloudName("demo");
        config.getCloudinary().setApiKey("test-key");
        config.getCloudinary().setApiSecret("test-secret");
        return config;
    }
}
