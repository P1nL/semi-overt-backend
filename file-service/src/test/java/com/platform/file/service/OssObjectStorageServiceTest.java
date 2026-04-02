package com.platform.file.service;

import com.aliyun.oss.OSS;
import com.platform.file.config.StorageConfig;
import com.platform.file.service.impl.OssObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OssObjectStorageServiceTest {

    private StorageConfig storageConfig;
    private StorageConfig.Oss ossConfig;
    private OssClientFactory ossClientFactory;
    private OSS ossClient;
    private OssObjectStorageService service;

    @BeforeEach
    void setUp() {
        storageConfig = new StorageConfig();
        ossConfig = storageConfig.getOss();
        ossConfig.setEndpoint("oss-cn-hangzhou.aliyuncs.com");
        ossConfig.setBucket("now-demo-prod");
        ossConfig.setAccessKeyId("ak");
        ossConfig.setAccessKeySecret("sk");
        ossConfig.setPublicBaseUrl("https://cdn.example.com/");

        ossClientFactory = mock(OssClientFactory.class);
        ossClient = mock(OSS.class);
        when(ossClientFactory.createClient(ossConfig)).thenReturn(ossClient);
        service = new OssObjectStorageService(storageConfig, ossClientFactory);
    }

    @Test
    void storeShouldUploadToOssAndReturnPublicUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.webp",
                "image/webp",
                "oss-body".getBytes(StandardCharsets.UTF_8)
        );

        String storedUrl = service.store("2026/04/01/test.webp", file);

        assertThat(storedUrl).isEqualTo("https://cdn.example.com/2026/04/01/test.webp");
        verify(ossClient).putObject(eq("now-demo-prod"), eq("2026/04/01/test.webp"), isA(java.io.InputStream.class), any());
        verify(ossClient).shutdown();
    }

    @Test
    void validateReadinessShouldFailWhenBucketDoesNotExist() {
        when(ossClient.doesBucketExist("now-demo-prod")).thenReturn(false);

        assertThatThrownBy(() -> service.validateReadiness())
                .hasMessageContaining("OSS bucket does not exist");
        verify(ossClient).shutdown();
    }
}
