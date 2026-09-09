package com.platform.auth;

import com.platform.contract.content.client.ContentProfileClient;
import com.platform.web.support.config.PlatformWebSupportConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackageClasses = AuthServiceApplication.class)
@Import(PlatformWebSupportConfig.class)
@EnableFeignClients(clients = ContentProfileClient.class)
@MapperScan("com.platform.auth.mapper")
@EnableScheduling
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
