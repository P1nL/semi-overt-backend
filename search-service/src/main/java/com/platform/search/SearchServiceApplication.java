package com.platform.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.mybatis.spring.annotation.MapperScan;
import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.events.config.PlatformEventsConfig;
import com.platform.web.support.config.PlatformWebSupportConfig;

/**
 * 搜索服务启动类，负责启动搜索模块应用上下文。
 */

@SpringBootApplication(scanBasePackageClasses = SearchServiceApplication.class)
@Import({PlatformWebSupportConfig.class, PlatformEventsConfig.class})
@EnableFeignClients(clients = AuthUserQueryClient.class)
@MapperScan({"com.platform.search.mapper", "com.platform.events.mapper"})
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
