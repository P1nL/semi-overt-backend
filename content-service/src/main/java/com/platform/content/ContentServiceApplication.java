package com.platform.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.review.client.ReviewReasonClient;
import com.platform.contract.review.client.ReviewTaskClient;
import com.platform.events.config.PlatformEventsConfig;
import com.platform.web.support.config.PlatformWebSupportConfig;

/**
 * 内容服务启动类，负责启动内容模块应用上下文。
 */

@SpringBootApplication(scanBasePackageClasses = ContentServiceApplication.class)
@Import({PlatformWebSupportConfig.class, PlatformEventsConfig.class})
@EnableFeignClients(clients = {AuthUserQueryClient.class, ReviewReasonClient.class, ReviewTaskClient.class})
@EnableScheduling
@MapperScan({"com.platform.content.mapper", "com.platform.events.mapper"})
public class ContentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentServiceApplication.class, args);
    }
}
