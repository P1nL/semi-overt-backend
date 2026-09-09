package com.platform.gateway.session;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
class SessionAuthorityClientHttpTest {
    @Test void actualHttpInternalTokenAndFailureSemantics(){
        var server=HttpServer.create().host("127.0.0.1").port(0).handle((request,response)->{
            assertEquals("test-only-token",request.requestHeaders().get("X-Internal-Token"));
            return request.receive().aggregate().asString().flatMap(body->{
                if(body.contains("invalid"))return response.status(401).sendString(Mono.just("{}")).then();
                if(body.contains("unavailable"))return response.status(503).sendString(Mono.just("{}")).then();
                String json=request.uri().contains("budget")?"{\"code\":200,\"data\":{\"allowed\":false,\"retryAfterSeconds\":9}}":"{\"code\":200,\"data\":{\"userId\":3,\"username\":\"writer\",\"role\":\"USER\"}}";
                return response.header("Content-Type","application/json").sendString(Mono.just(json)).then();
            });
        }).bindNow();
        try{
            var client=new SessionAuthorityClient(WebClient.builder(),"http://127.0.0.1:"+server.port(),"test-only-token");
            assertEquals(3,client.validate("valid").block(Duration.ofSeconds(5)).userId());
            assertNull(client.validate("invalid").block(Duration.ofSeconds(5)));
            assertThrows(Exception.class,()->client.validate("unavailable").block(Duration.ofSeconds(5)));
            assertFalse(client.consume("127.0.0.1",3L,"WRITE").block(Duration.ofSeconds(5)).allowed());
        }finally{server.disposeNow();}
    }
}
