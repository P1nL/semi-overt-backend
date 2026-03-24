package com.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 内容创作平台后端启动类
 * @EnableScheduling 开启定时任务（草稿刷盘等）
 */
@SpringBootApplication
@MapperScan("com.platform.mapper")
@EnableScheduling
public class NowDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(NowDemoApplication.class, args);
    }
}