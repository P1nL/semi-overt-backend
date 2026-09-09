package com.platform.gateway.filter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.gateway.session.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class GatewayAuthFilterTest {
    SessionAuthorityClient authority;GatewayAuthFilter filter;
    @BeforeEach void setup(){authority=mock(SessionAuthorityClient.class);when(authority.internalToken()).thenReturn("test-internal");filter=new GatewayAuthFilter(authority,new ClientIpResolver(""),new ObjectMapper());}
    @Test void publicInvalidTokenAnonymousAndForgedHeadersRemoved(){
        when(authority.validate("revoked")).thenReturn(Mono.empty());var e=MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/articles/12").header("Authorization","Bearer revoked").header("X-User-Id","1").header("X-User-Role","ADMIN").header("X-Internal-Token","forged"));
        var seen=new AtomicReference<org.springframework.web.server.ServerWebExchange>();StepVerifier.create(filter.filter(e,x->{seen.set(x);return Mono.empty();})).verifyComplete();
        assertNotNull(seen.get());assertNull(seen.get().getRequest().getHeaders().getFirst("X-User-Id"));assertEquals("test-internal",seen.get().getRequest().getHeaders().getFirst("X-Internal-Token"));
    }
    @Test void protectedInvalidTokenDenied(){when(authority.validate("revoked")).thenReturn(Mono.empty());var e=MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/articles/drafts").header("Authorization","Bearer revoked"));StepVerifier.create(filter.filter(e,x->Mono.error(new AssertionError("must not forward")))).verifyComplete();assertEquals(401,e.getResponse().getStatusCode().value());}
    @Test void authorityOutageIs503Not401(){when(authority.validate("valid")).thenReturn(Mono.error(new IllegalStateException()));var e=MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/articles/drafts").header("Authorization","Bearer valid"));StepVerifier.create(filter.filter(e,x->Mono.empty())).verifyComplete();assertEquals(503,e.getResponse().getStatusCode().value());}
    @Test void cookieLogoutDoesNotValidateBearer(){when(authority.consume(anyString(),isNull(),eq("WRITE"))).thenReturn(Mono.just(new SessionAuthorityClient.Budget(true,0)));var e=MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/logout").header("Cookie","semi_overt_refresh=opaque").header("Authorization","Bearer obsolete"));var seen=new AtomicReference<String>();StepVerifier.create(filter.filter(e,x->{seen.set(x.getRequest().getHeaders().getFirst("Cookie"));return Mono.empty();})).verifyComplete();assertEquals("semi_overt_refresh=opaque",seen.get());verify(authority,never()).validate(anyString());}
    @Test void budgetRejectSetsRetryAfter(){when(authority.consume(anyString(),isNull(),eq("SEARCH"))).thenReturn(Mono.just(new SessionAuthorityClient.Budget(false,30)));var e=MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/search/articles"));StepVerifier.create(filter.filter(e,x->Mono.error(new AssertionError()))).verifyComplete();assertEquals(429,e.getResponse().getStatusCode().value());assertEquals("30",e.getResponse().getHeaders().getFirst("Retry-After"));}
    @Test void validIdentityUsesAuthorityRoleNotClientRole(){when(authority.validate("valid")).thenReturn(Mono.just(new SessionAuthorityClient.Identity(7L,"writer","USER")));var e=MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/reviews/pending").header("Authorization","Bearer valid").header("X-User-Role","ADMIN"));StepVerifier.create(filter.filter(e,x->Mono.error(new AssertionError()))).verifyComplete();assertEquals(403,e.getResponse().getStatusCode().value());}
    @Test void internalPathsNeverPublic(){var e=MockServerWebExchange.from(MockServerHttpRequest.post("/internal/auth/session/validate"));StepVerifier.create(filter.filter(e,x->Mono.error(new AssertionError()))).verifyComplete();assertEquals(404,e.getResponse().getStatusCode().value());}
}
