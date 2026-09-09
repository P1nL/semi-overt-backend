package com.platform.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.client.HttpClient;
import reactor.core.publisher.Mono;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

/** Real loopback HTTP transport through the production WebFilter (no external services). */
class ApiCompatibilityHttpTest {
    @Test void aliasesReachHandlerBeforePathSelectionWithBodyAndQueryIntact() {
        var handler = WebHttpHandlerBuilder.webHandler(exchange -> {
            String path=exchange.getRequest().getPath().value();
            boolean routed=path.equals("/api/v1/reviews/42/action");
            exchange.getResponse().setStatusCode(routed?org.springframework.http.HttpStatus.OK:org.springframework.http.HttpStatus.NOT_FOUND);
            return org.springframework.core.io.buffer.DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(buffer->{
                    String body=buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
                    org.springframework.core.io.buffer.DataBufferUtils.release(buffer);
                    byte[] bytes=(path+"?"+exchange.getRequest().getURI().getRawQuery()+"|"+body).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
                });
        }).filter(new ApiCompatibilityWebFilter()).build();
        var server=HttpServer.create().host("127.0.0.1").port(0).handle(new ReactorHttpHandlerAdapter(handler)).bindNow();
        try {
            for(String prefix:new String[]{"/api","/api/v1"}) {
                String response=HttpClient.create().post().uri("http://127.0.0.1:"+server.port()+prefix+"/review/42/decision?q=a%2Fb")
                    .send(reactor.netty.ByteBufFlux.fromString(Mono.just("{\"action\":\"APPROVE\"}")))
                    .responseSingle((r,b)->{assertEquals(200,r.status().code());return b.asString();}).block(Duration.ofSeconds(10));
                assertEquals("/api/v1/reviews/42/action?q=a%2Fb|{\"action\":\"APPROVE\"}",response);
            }
        } finally {server.disposeNow();}
    }
}
