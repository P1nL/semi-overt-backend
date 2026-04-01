package com.platform.review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.content.client.ContentReviewClient;
import com.platform.events.config.PlatformEventsConfig;
import com.platform.web.support.config.PlatformWebSupportConfig;

/**
 * 审核服务启动类，负责启动审核模块应用上下文。
 */

@SpringBootApplication(scanBasePackageClasses = ReviewServiceApplication.class)
@Import({PlatformWebSupportConfig.class, PlatformEventsConfig.class})
@EnableFeignClients(clients = {AuthUserQueryClient.class, ContentReviewClient.class})
@EnableScheduling
@MapperScan({"com.platform.review.mapper", "com.platform.events.mapper"})
public class ReviewServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewServiceApplication.class, args);
    }
}
