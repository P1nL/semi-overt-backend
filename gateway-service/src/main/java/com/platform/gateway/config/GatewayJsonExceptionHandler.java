package com.platform.gateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.util.Result;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * 网关统一异常输出处理器。
 * 把路由、超时、权限等异常转换为稳定的 JSON 响应，避免默认 HTML 错误页返回给前端。
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GatewayJsonExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    /**
     * 处理网关层未捕获异常并输出统一错误结构。
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = resolveStatus(ex);
        if (status.is5xxServerError()) {
            log.error("Gateway request failed with status {}", status.value(), ex);
        } else {
            log.warn("Gateway request failed with status {}: {}", status.value(), ex.getMessage());
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(toJson(buildResult(status)).getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * 根据异常类型推断 HTTP 状态码。
     */
    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException responseStatusException) {
            HttpStatus status = HttpStatus.resolve(responseStatusException.getStatusCode().value());
            return status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        }
        if (ex instanceof TimeoutException
                || ex instanceof ReadTimeoutException
                || ex instanceof ConnectTimeoutException) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * 根据状态码构造统一 Result 响应体。
     */
    private Result<?> buildResult(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> Result.badRequest("Request parameters are invalid");
            case UNAUTHORIZED -> Result.unauthorized("Authentication required or token is invalid");
            case FORBIDDEN -> Result.forbidden("Access denied");
            case NOT_FOUND -> Result.notFound("Route not found");
            case TOO_MANY_REQUESTS -> Result.tooManyRequests("Too many requests");
            case SERVICE_UNAVAILABLE -> Result.fail(status.value(), "Service temporarily unavailable");
            case GATEWAY_TIMEOUT -> Result.fail(status.value(), "Upstream service timed out");
            default -> Result.serverError("Gateway internal error");
        };
    }

    /**
     * 序列化错误响应体。
     */
    private String toJson(Result<?> result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            return "{\"code\":500,\"message\":\"serialize error\",\"data\":null}";
        }
    }
}
