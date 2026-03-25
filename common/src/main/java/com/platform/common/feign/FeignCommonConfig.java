package com.platform.common.feign;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignCommonConfig {

    @Bean
    public FeignHeaderRelayInterceptor feignHeaderRelayInterceptor() {
        return new FeignHeaderRelayInterceptor();
    }
}
