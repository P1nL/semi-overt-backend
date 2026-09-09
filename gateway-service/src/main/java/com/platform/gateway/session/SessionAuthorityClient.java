package com.platform.gateway.session;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.Map;
@Component
public class SessionAuthorityClient {
    public record Identity(Long userId,String username,String role){}
    public record Budget(boolean allowed,long retryAfterSeconds){}
    private record IdentityEnvelope(int code,Identity data){}
    private record BudgetEnvelope(int code,Budget data){}
    private final WebClient client; private final String internalToken;
    public SessionAuthorityClient(@Qualifier("sessionWebClientBuilder") WebClient.Builder builder,
            @Value("${platform.auth-service.base-url:http://auth-service}") String base,
            @Value("${platform.internal.token:}") String token){
        if(token==null||token.isBlank())throw new IllegalArgumentException("Internal token required");
        internalToken=token;client=builder.baseUrl(base).defaultHeader("X-Internal-Token",token).build();
    }
    public String internalToken(){return internalToken;}
    public Mono<Identity> validate(String token){
        return client.post().uri("/internal/auth/session/validate").bodyValue(Map.of("token",token)).exchangeToMono(r->{
            if(r.statusCode().value()==401)return r.releaseBody().then(Mono.empty());
            if(!r.statusCode().is2xxSuccessful())return r.releaseBody().then(Mono.error(new IllegalStateException("Auth authority unavailable")));
            return r.bodyToMono(IdentityEnvelope.class).switchIfEmpty(Mono.error(new IllegalStateException("Empty auth response"))).flatMap(e->{
                if(e.code()!=200||e.data()==null||e.data().userId()==null||e.data().username()==null||!("USER".equals(e.data().role())||"ADMIN".equals(e.data().role())))return Mono.error(new IllegalStateException("Invalid auth response"));
                return Mono.just(e.data());
            });
        }).timeout(Duration.ofSeconds(3));
    }
    public Mono<Budget> consume(String ip,Long userId,String operation){
        var body=new java.util.HashMap<String,Object>();body.put("clientIp",ip);body.put("userId",userId);body.put("operation",operation);
        return client.post().uri("/internal/auth/budget/consume").bodyValue(body).retrieve().bodyToMono(BudgetEnvelope.class)
            .switchIfEmpty(Mono.error(new IllegalStateException("Empty budget response"))).flatMap(e->e.code()==200&&e.data()!=null?Mono.just(e.data()):Mono.error(new IllegalStateException("Invalid budget response")))
            .timeout(Duration.ofSeconds(3));
    }
}
