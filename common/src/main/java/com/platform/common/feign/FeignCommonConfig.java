package com.platform.common.feign;

import feign.Request;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.TimeUnit;

@Configuration
public class FeignCommonConfig {

    @Bean
    public FeignHeaderRelayInterceptor feignHeaderRelayInterceptor() {
        return new FeignHeaderRelayInterceptor();
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
