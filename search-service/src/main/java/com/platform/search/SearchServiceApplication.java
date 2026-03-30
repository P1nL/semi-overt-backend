package com.platform.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 搜索服务启动类，负责启动搜索模块应用上下文。
 */

@SpringBootApplication(scanBasePackages = {"com.platform", "com.platform.common"})
@EnableFeignClients(basePackages = "com.platform")
@EnableElasticsearchRepositories(basePackages = "com.platform.repository")
@MapperScan("com.platform.mapper")
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
