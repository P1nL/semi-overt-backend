package com.platform.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.mybatis.spring.annotation.MapperScan;
import com.platform.contract.content.client.ContentProfileClient;
import com.platform.web.support.config.PlatformWebSupportConfig;

/**
 * 认证服务启动类，负责启动认证模块应用上下文。
 */

@SpringBootApplication(scanBasePackageClasses = AuthServiceApplication.class)
@Import(PlatformWebSupportConfig.class)
@EnableFeignClients(clients = ContentProfileClient.class)
@MapperScan("com.platform.auth.mapper")
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
