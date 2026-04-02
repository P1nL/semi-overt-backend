package com.platform.file.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.platform.file.config.StorageConfig;
import com.platform.file.service.OssClientFactory;
import org.springframework.stereotype.Component;

@Component
public class DefaultOssClientFactory implements OssClientFactory {

    @Override
    public OSS createClient(StorageConfig.Oss ossConfig) {
        return new OSSClientBuilder().build(
                ossConfig.getEndpoint(),
                ossConfig.getAccessKeyId(),
                ossConfig.getAccessKeySecret()
        );
    }
}
