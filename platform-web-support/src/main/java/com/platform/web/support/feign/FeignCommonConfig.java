package com.platform.web.support.feign;

import feign.Request;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.TimeUnit;

/**
 * Feign 通用配置类，统一注册服务间调用需要的拦截器与编码配置。
 */

@Configuration
public class FeignCommonConfig {

    @Bean
    public FeignHeaderRelayInterceptor feignHeaderRelayInterceptor(
            @Value("${platform.internal.token:}") String internalToken) {
        return new FeignHeaderRelayInterceptor(internalToken);
    }

    @Bean
    public Request.Options feignRequestOptions(
            @Value("${platform.feign.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${platform.feign.read-timeout-ms:5000}") long readTimeoutMs) {
        return new Request.Options(
                connectTimeoutMs,
                TimeUnit.MILLISECONDS,
                readTimeoutMs,
                TimeUnit.MILLISECONDS,
                true
        );
    }

    @Bean
    public Retryer feignRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
