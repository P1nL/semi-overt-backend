package com.platform.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 认证服务启动类，负责启动认证模块应用上下文。
 */

@SpringBootApplication(scanBasePackages = {"com.platform", "com.platform.common"})
@EnableFeignClients(basePackages = "com.platform")
@MapperScan("com.platform.mapper")
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
