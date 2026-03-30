package com.platform.review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 审核服务启动类，负责启动审核模块应用上下文。
 */

@SpringBootApplication(scanBasePackages = {"com.platform", "com.platform.common"})
@EnableFeignClients(basePackages = "com.platform")
@EnableScheduling
@MapperScan("com.platform.mapper")
public class ReviewServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewServiceApplication.class, args);
    }
}
