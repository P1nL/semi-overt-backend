package com.platform.gateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.kernel.util.Result;
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
 * 缃戝叧缁熶竴寮傚父杈撳嚭澶勭悊鍣ㄣ€?
 * 鎶婅矾鐢便€佽秴鏃躲€佹潈闄愮瓑寮傚父杞崲涓虹ǔ瀹氱殑 JSON 鍝嶅簲锛岄伩鍏嶉粯璁?HTML 閿欒椤佃繑鍥炵粰鍓嶇銆?
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GatewayJsonExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    /**
     * 澶勭悊缃戝叧灞傛湭鎹曡幏寮傚父骞惰緭鍑虹粺涓€閿欒缁撴瀯銆?
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
     * 鏍规嵁寮傚父绫诲瀷鎺ㄦ柇 HTTP 鐘舵€佺爜銆?
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
     * 鏍规嵁鐘舵€佺爜鏋勯€犵粺涓€ Result 鍝嶅簲浣撱€?
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
     * 搴忓垪鍖栭敊璇搷搴斾綋銆?
     */
    private String toJson(Result<?> result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            return "{\"code\":500,\"message\":\"serialize error\",\"data\":null}";
        }
    }
}

