package com.platform.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关JSON异常HandlerTest配置类，负责当前模块相关组件的装配与框架行为配置。
 */

class GatewayJsonExceptionHandlerTest {

    private GatewayJsonExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GatewayJsonExceptionHandler(new ObjectMapper());
    }

    @Test
    void responseStatusExceptionReturnsJsonPayload() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/missing").build());

        StepVerifier.create(handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getHeaders().getContentType()).hasToString("application/json");
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":404");
    }

    @Test
    void timeoutExceptionMapsToGatewayTimeout() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/home").build());

        StepVerifier.create(handler.handle(exchange, new TimeoutException("timed out")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":504");
    }
}
