package com.platform.events.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(RabbitEventConfig.class)
@ComponentScan(basePackages = "com.platform.events.support")
public class PlatformEventsConfig {
}
