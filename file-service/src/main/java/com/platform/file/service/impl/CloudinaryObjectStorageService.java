package com.platform.file.service.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.platform.file.config.StorageConfig;
import com.platform.file.service.ObjectStorageService;
import com.platform.kernel.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

/**
 * Server-side Cloudinary Upload API adapter. Cloudinary's official REST
 * endpoint is /v1_1/{cloudName}/image/upload and Basic Authentication uses
 * the API key and API secret; no client-side unsigned upload is used here.
 */
@Slf4j
public class CloudinaryObjectStorageService implements ObjectStorageService {

    private final StorageConfig.Cloudinary config;
    private final RestClient client;
    private final String deliveryBaseUrl;

    public CloudinaryObjectStorageService(RestClient.Builder builder, StorageConfig storageConfig) {
        this.config = Objects.requireNonNull(storageConfig, "storageConfig").getCloudinary();
        validateConfig(config);
        String apiBaseUrl = normalizeApiBaseUrl(config.getApiBaseUrl());
        String uploadUri = apiBaseUrl + "/v1_1/" + config.getCloudName() + "/image/upload";
        this.deliveryBaseUrl = normalizeDeliveryBaseUrl(config.getDeliveryBaseUrl(), config.getCloudName());
        this.client = builder
                .baseUrl(uploadUri)
                .defaultHeaders(headers -> headers.setBasicAuth(config.getApiKey(), config.getApiSecret()))
                .build();
    }

    @Override
    public String store(String objectKey, byte[] bytes, String contentType) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IOException("Uploaded file is empty");
        }
        if (objectKey == null || objectKey.isBlank() || objectKey.contains("..")
                || objectKey.startsWith("/") || objectKey.contains("\\")) {
            throw new IOException("Invalid generated Cloudinary object key");
        }

        String filename = objectKey.substring(objectKey.lastIndexOf('/') + 1);
        String publicId = "semi-overt/" + objectKey.replaceFirst("\\.[^.]+$", "");
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        form.add("public_id", publicId);
        form.add("overwrite", "false");

        final CloudinaryResponse response;
        try {
            response = client.post()
                    .uri("")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(CloudinaryResponse.class);
        } catch (RestClientException ex) {
            // Provider bodies can contain request details. Never expose them or
            // the configured credentials through the public error envelope.
            throw BusinessException.serverError("Cloudinary image upload failed");
        }
        if (response == null || !isAllowedDeliveryUrl(response.secureUrl())) {
            throw BusinessException.serverError("Cloudinary returned an invalid delivery URL");
        }
        return response.secureUrl();
    }

    @Override
    public void delete(String objectKey) {
        // Deletion is intentionally not exposed through client oldUrl. A future
        // trusted owner-aware cleanup job may call a provider-specific method.
    }

    @Override
    public void validateReadiness() {
        // Configuration validation is deliberately fail-closed. Avoid making a
        // provider network call on every actuator readiness request.
        validateConfig(config);
    }

    private boolean isAllowedDeliveryUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI actual = URI.create(value);
            URI expected = URI.create(deliveryBaseUrl);
            String expectedPath = expected.getPath().endsWith("/")
                    ? expected.getPath() : expected.getPath() + "/";
            return "https".equalsIgnoreCase(actual.getScheme())
                    && expected.getHost().equalsIgnoreCase(actual.getHost())
                    && Objects.equals(expected.getPort(), actual.getPort())
                    && actual.getUserInfo() == null
                    && actual.getQuery() == null
                    && actual.getFragment() == null
                    && actual.getPath().startsWith(expectedPath);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static void validateConfig(StorageConfig.Cloudinary value) {
        if (value == null || value.getCloudName() == null
                || !value.getCloudName().matches("[A-Za-z0-9_-]+")
                || value.getApiKey() == null || value.getApiKey().isBlank()
                || value.getApiSecret() == null || value.getApiSecret().isBlank()) {
            throw new IllegalStateException(
                    "Cloudinary storage requires storage.cloudinary.cloud-name, api-key and api-secret");
        }
        validateEndpoint(value.getApiBaseUrl(), "storage.cloudinary.api-base-url", true);
        String delivery = value.getDeliveryBaseUrl();
        if (delivery != null && !delivery.isBlank()) {
            validateEndpoint(delivery, "storage.cloudinary.delivery-base-url", false);
        }
    }

    private static String normalizeApiBaseUrl(String value) {
        return trimTrailingSlash(value);
    }

    private static String normalizeDeliveryBaseUrl(String configured, String cloudName) {
        String value = configured == null || configured.isBlank()
                ? "https://res.cloudinary.com/" + cloudName + "/image/upload/"
                : configured;
        return trimTrailingSlash(value) + "/";
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static void validateEndpoint(String value, String property, boolean allowLoopbackHttp) {
        try {
            URI uri = URI.create(value);
            boolean validScheme = "https".equalsIgnoreCase(uri.getScheme())
                    || (allowLoopbackHttp && "http".equalsIgnoreCase(uri.getScheme())
                    && isLoopbackHost(uri.getHost()));
            if (!validScheme || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException ex) {
            throw new IllegalStateException(property + " must be an HTTPS URL; HTTP is allowed only for loopback mock servers");
        }
    }

    private static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "::1".equals(host) || "[::1]".equals(host);
    }

    record CloudinaryResponse(@JsonProperty("secure_url") String secureUrl) {
    }
}