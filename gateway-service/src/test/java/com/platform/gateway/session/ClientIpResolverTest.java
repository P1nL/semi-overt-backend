package com.platform.gateway.session;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import java.net.InetSocketAddress;
import static org.junit.jupiter.api.Assertions.*;
class ClientIpResolverTest {
    @Test void spoofedHeadersIgnoredUnlessSocketPeerTrusted(){
        var request=MockServerHttpRequest.get("/").remoteAddress(new InetSocketAddress("127.0.0.1",1234)).header("X-Forwarded-For","198.51.100.7").build();
        assertEquals("127.0.0.1",new ClientIpResolver("").resolve(request));
        assertEquals("198.51.100.7",new ClientIpResolver("127.0.0.1/32").resolve(request));
    }
    @Test void malformedForwardChainFallsBackToSocket(){
        var request=MockServerHttpRequest.get("/").remoteAddress(new InetSocketAddress("127.0.0.1",1234)).header("X-Forwarded-For","untrusted.example").build();
        assertEquals("127.0.0.1",new ClientIpResolver("127.0.0.1/32").resolve(request));
    }
}
