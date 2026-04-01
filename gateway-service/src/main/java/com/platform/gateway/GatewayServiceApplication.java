package com.platform.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import com.platform.web.support.config.PlatformWebSupportConfig;
import com.platform.web.support.security.JwtProperties;

@SpringBootApplication(scanBasePackageClasses = GatewayServiceApplication.class, exclude = {DataSourceAutoConfiguration.class})
@Import(PlatformWebSupportConfig.class)
/**
 * 缃戝叧鏈嶅姟鍚姩绫伙紝璐熻矗鍚姩 API Gateway 搴旂敤涓婁笅鏂囥€?
 */

@EnableConfigurationProperties(JwtProperties.class)
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}

