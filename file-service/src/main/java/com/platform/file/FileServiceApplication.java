package com.platform.file;

import com.platform.common.config.RabbitEventConfig;
import com.platform.common.support.EventConsumeService;
import com.platform.common.support.EventListenerExecutor;
import com.platform.common.support.EventOutboxService;
import com.platform.common.support.OutboxPublisherSupport;
import com.platform.common.support.RabbitRetrySupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(
        basePackages = {"com.platform", "com.platform.common"},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        RabbitEventConfig.class,
                        EventConsumeService.class,
                        EventOutboxService.class,
                        EventListenerExecutor.class,
                        OutboxPublisherSupport.class,
                        RabbitRetrySupport.class
                }
        )
)
public class FileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
