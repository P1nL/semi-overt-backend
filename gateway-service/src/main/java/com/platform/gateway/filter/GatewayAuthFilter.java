package com.platform.gateway.filter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.gateway.session.SessionAuthorityClient;
import com.platform.gateway.session.ClientIpResolver;
import com.platform.kernel.util.Result;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Component
public class GatewayAuthFilter implements GlobalFilter,Ordered {
    private final SessionAuthorityClient authority;private final ClientIpResolver ips;private final ObjectMapper mapper;
    public GatewayAuthFilter(SessionAuthorityClient authority,ClientIpResolver ips,ObjectMapper mapper){this.authority=authority;this.ips=ips;this.mapper=mapper;}
    public int getOrder(){return -100;}
    public Mono<Void> filter(ServerWebExchange exchange,GatewayFilterChain chain){
        String path=exchange.getRequest().getPath().value();String method=exchange.getRequest().getMethod().name();
        if(path.startsWith("/internal/"))return error(exchange,404,"Not found",0);
        String ip=ips.resolve(exchange.getRequest());
        var request=exchange.getRequest().mutate().headers(h->{
            for(String name:List.of("X-User-Id","X-Username","X-User-Role","X-Internal-Token","X-Verified-Client-IP","X-Forwarded-For","X-Real-IP","Forwarded"))h.remove(name);
            h.set("X-Internal-Token",authority.internalToken());h.set("X-Verified-Client-IP",ip);
            h.set("X-Trace-Id",UUID.randomUUID().toString());
        }).build();
        var clean=exchange.mutate().request(request).build();
        boolean auth=method.equals("POST") && Set.of("/api/v1/auth/login","/api/v1/auth/register","/api/v1/auth/register-code","/api/v1/auth/refresh","/api/v1/auth/logout","/api/v1/auth/forgot-password","/api/v1/auth/reset-password").contains(path);
        String bearer=request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        Mono<Optional<SessionAuthorityClient.Identity>> identity=auth || bearer==null || !bearer.startsWith("Bearer ")
            ?Mono.just(Optional.empty()):authority.validate(bearer.substring(7).trim()).map(Optional::of).defaultIfEmpty(Optional.empty());
        return identity.flatMap(result->{
            if(!auth&&!isPublic(method,path)&&result.isEmpty())return error(clean,401,"会话已失效，请重新登录",0);
            if(result.isPresent()&&(path.startsWith("/api/v1/admin/")||path.startsWith("/api/v1/reviews/")&&!path.matches("/api/v1/reviews/[0-9]+/logs"))&&!"ADMIN".equals(result.get().role()))return error(clean,403,"没有操作权限",0);
            var forwarded=clean;
            if(result.isPresent()){
                var user=result.get();forwarded=clean.mutate().request(clean.getRequest().mutate().headers(h->{h.set("X-User-Id",user.userId().toString());h.set("X-Username",user.username());h.set("X-User-Role",user.role());}).build()).build();
            }
            ServerWebExchange finalExchange=forwarded;
            String operation=method.equals("GET")&&path.startsWith("/api/v1/search")?"SEARCH":Set.of("POST","PUT","PATCH","DELETE").contains(method)?(path.startsWith("/api/v1/uploads")?"UPLOAD":"WRITE"):null;
            if(operation==null)return chain.filter(finalExchange);
            return authority.consume(ip,result.map(SessionAuthorityClient.Identity::userId).orElse(null),operation)
                .flatMap(b->b.allowed()?chain.filter(finalExchange):error(finalExchange,429,"请求过于频繁，请稍后重试",b.retryAfterSeconds()));
        }).onErrorResume(e->error(clean,503,"认证服务暂时不可用，请稍后重试",0));
    }
    private boolean isPublic(String method,String path){
        return method.equals("OPTIONS")||path.startsWith("/static/uploads/")||method.equals("GET")&&(
            path.equals("/api/v1/home")||path.startsWith("/api/v1/categories/")||path.startsWith("/api/v1/search/")
            ||path.matches("/api/v1/articles/[0-9]+")||path.matches("/api/v1/users/[^/]+/profile")||path.matches("/api/v1/reviews/[0-9]+/logs"));
    }
    private Mono<Void> error(ServerWebExchange e,int code,String message,long retry){
        if(e.getResponse().isCommitted())return Mono.error(new IllegalStateException("Response already committed"));
        e.getResponse().setStatusCode(HttpStatusCode.valueOf(code));e.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if(retry>0)e.getResponse().getHeaders().set("Retry-After",Long.toString(retry));
        try{return e.getResponse().writeWith(Mono.just(e.getResponse().bufferFactory().wrap(mapper.writeValueAsBytes(Result.fail(code,message)))));}
        catch(Exception failure){return Mono.error(failure);}
    }
}
