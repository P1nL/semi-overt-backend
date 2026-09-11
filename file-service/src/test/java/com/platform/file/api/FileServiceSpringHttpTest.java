package com.platform.file.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.file.FileServiceApplication;
import com.platform.file.support.TestImageFixtures;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = FileServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "platform.internal.token=s4-test-internal-token",
                "storage.type=local",
                "storage.access-prefix=/static/uploads",
                "storage.max-file-size=5242880",
                "storage.max-image-dimension=8192",
                "storage.max-image-pixels=24000000",
                "storage.max-concurrent-image-decodes=2",
                "spring.servlet.multipart.max-file-size=5MB",
                "spring.servlet.multipart.max-request-size=6MB",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.config.import-check.enabled=false",
                "spring.config.import=",
                "server.port=0"
        }
)
class FileServiceSpringHttpTest {

    private static final Path UPLOAD_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"),
            "semi-overt-file-service-http-" + UUID.randomUUID());
    private static final String INTERNAL_TOKEN = "s4-test-internal-token";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.upload-path", () -> UPLOAD_ROOT.toString());
    }

    @AfterAll
    static void removeTestStorage() throws IOException {
        if (Files.exists(UPLOAD_ROOT)) {
            try (var paths = Files.walk(UPLOAD_ROOT)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        throw new IllegalStateException("Could not remove test storage " + path, ex);
                    }
                });
            }
        }
    }

    @Test
    void startsAllFileBeansAndUploadsThroughRealHttpThenServesStaticObject() throws Exception {
        byte[] png = TestImageFixtures.png(java.awt.Color.decode("#336699"));
        Path victim = UPLOAD_ROOT.resolve("victim/old.png");
        Files.createDirectories(victim.getParent());
        Files.write(victim, "do-not-delete".getBytes(StandardCharsets.UTF_8));

        HttpResponse<byte[]> anonymous = sendMultipart(
                "/api/v1/upload", png, "cover.png", "image/png",
                Map.of("bizType", "COVER"), Map.of());
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(json(anonymous).path("code").asInt()).isEqualTo(401);

        HttpResponse<byte[]> uploaded = sendMultipart(
                "/api/v1/upload", png, "cover.png", "image/png",
                Map.of("bizType", "COVER", "articleId", "9", "oldUrl", "/static/uploads/victim/old.png"),
                authenticatedHeaders());
        assertThat(uploaded.statusCode()).isEqualTo(200);
        JsonNode body = json(uploaded);
        assertThat(body.path("code").asInt()).isEqualTo(200);
        assertThat(body.path("data").path("width").asInt()).isEqualTo(2);
        assertThat(body.path("data").path("height").asInt()).isEqualTo(2);
        assertThat(body.path("data").path("size").asLong()).isEqualTo(png.length);
        assertThat(body.path("data").path("dominantColor").asText()).isEqualTo("#336699");

        String staticUrl = body.path("data").path("url").asText();
        assertThat(staticUrl).startsWith("/static/uploads/");
        HttpResponse<byte[]> staticResponse = send("GET", staticUrl, null, Map.of());
        assertThat(staticResponse.statusCode()).isEqualTo(200);
        assertThat(staticResponse.body()).isEqualTo(png);
        assertThat(Files.readString(victim)).isEqualTo("do-not-delete");
    }

    @Test
    void refusesStaticTraversalOutsideConfiguredRoot() throws Exception {
        byte[] outsideBytes = TestImageFixtures.png(java.awt.Color.RED);
        Path outside = UPLOAD_ROOT.resolveSibling(UPLOAD_ROOT.getFileName() + "-outside.png");
        Files.write(outside, outsideBytes);
        try {
            HttpResponse<byte[]> response = send(
                    "GET", "/static/uploads/%2e%2e/" + outside.getFileName(), null, Map.of());
            assertThat(response.statusCode()).isNotEqualTo(200);
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void returnsJsonBadRequestForMultipartFileOverFiveMiB() throws Exception {
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        HttpResponse<byte[]> response = sendMultipart(
                "/api/v1/upload", oversized, "oversized.png", "image/png",
                Map.of("bizType", "COVER"), authenticatedHeaders());

        assertThat(response.statusCode()).isIn(400, 413);
        JsonNode body = json(response);
        assertThat(body.path("code").asInt()).isIn(400, 413);
        assertThat(body.path("message").asText()).containsIgnoringCase("large");
    }

    @Test
    void returnsJsonBadRequestForEmptyMultipartFile() throws Exception {
        HttpResponse<byte[]> response = sendMultipart(
                "/api/v1/upload", new byte[0], "empty.png", "image/png",
                Map.of("bizType", "COVER"), authenticatedHeaders());

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode body = json(response);
        assertThat(body.path("code").asInt()).isEqualTo(400);
        assertThat(body.path("message").asText()).contains("required");
    }

    private Map<String, String> authenticatedHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Internal-Token", INTERNAL_TOKEN);
        headers.put("X-User-Id", "42");
        headers.put("X-Username", "s4-http-test");
        headers.put("X-User-Role", "USER");
        return headers;
    }

    private HttpResponse<byte[]> sendMultipart(String path, byte[] file, String filename,
                                                String contentType, Map<String, String> fields,
                                                Map<String, String> headers) throws Exception {
        String boundary = "----s4Boundary" + UUID.randomUUID();
        byte[] body = multipart(boundary, file, filename, contentType, fields);
        Map<String, String> requestHeaders = new LinkedHashMap<>(headers);
        requestHeaders.put("Content-Type", "multipart/form-data; boundary=" + boundary);
        return send("POST", path, body, requestHeaders);
    }

    private byte[] multipart(String boundary, byte[] file, String filename, String contentType,
                             Map<String, String> fields) throws IOException {
        var output = new java.io.ByteArrayOutputStream();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            output.write(field.getValue().getBytes(StandardCharsets.UTF_8));
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(file);
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private HttpResponse<byte[]> send(String method, String path, byte[] body,
                                      Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path));
        headers.forEach(builder::header);
        if ("GET".equals(method)) {
            builder.GET();
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body));
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private JsonNode json(HttpResponse<byte[]> response) throws Exception {
        return objectMapper.readTree(response.body());
    }
}
