package com.platform.file.service;

import com.aliyun.oss.OSS;
import com.platform.file.config.StorageConfig;

public interface OssClientFactory {

    OSS createClient(StorageConfig.Oss ossConfig);
}
