package com.platform.web.support.config;

import com.platform.web.support.exception.GlobalExceptionHandler;
import com.platform.web.support.feign.FeignCommonConfig;
import com.platform.web.support.health.TcpConnectivityChecker;
import com.platform.web.support.security.JwtProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
        CommonJacksonConfig.class,
        FeignCommonConfig.class,
        GlobalExceptionHandler.class
})
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class PlatformWebSupportConfig {

    @Bean
    public TcpConnectivityChecker tcpConnectivityChecker() {
        return new TcpConnectivityChecker();
    }
}
