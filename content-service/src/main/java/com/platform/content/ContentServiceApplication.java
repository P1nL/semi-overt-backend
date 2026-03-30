package com.platform.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 内容服务启动类，负责启动内容模块应用上下文。
 */

@SpringBootApplication(scanBasePackages = {"com.platform", "com.platform.common"})
@EnableFeignClients(basePackages = "com.platform")
@EnableScheduling
@MapperScan("com.platform.mapper")
public class ContentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentServiceApplication.class, args);
    }
}
