package com.platform.gateway.session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.reactive.function.client.WebClient;
@Configuration
public class SessionClientConfig {
    @Bean @LoadBalanced public WebClient.Builder sessionWebClientBuilder(){return WebClient.builder();}
}
