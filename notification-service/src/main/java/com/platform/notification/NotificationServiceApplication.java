package com.platform.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.mybatis.spring.annotation.MapperScan;
import com.platform.events.config.PlatformEventsConfig;
import com.platform.web.support.config.PlatformWebSupportConfig;

/**
 * 通知服务启动类，负责启动通知模块应用上下文。
 */

@SpringBootApplication(scanBasePackageClasses = NotificationServiceApplication.class)
@Import({PlatformWebSupportConfig.class, PlatformEventsConfig.class})
@MapperScan({"com.platform.notification.mapper", "com.platform.events.mapper"})
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
