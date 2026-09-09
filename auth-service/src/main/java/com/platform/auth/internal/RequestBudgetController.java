package com.platform.auth.internal;
import com.platform.auth.session.JdbcRequestBudget;
import com.platform.kernel.util.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/internal/auth/budget")
public class RequestBudgetController {
    private final JdbcRequestBudget budget;
    @org.springframework.beans.factory.annotation.Value("${platform.rate-limit.write.max-requests:120}") private int writeMax=120;
    @org.springframework.beans.factory.annotation.Value("${platform.rate-limit.search.max-requests:30}") private int searchMax=30;
    @org.springframework.beans.factory.annotation.Value("${platform.storage.max-uploads-per-user-per-day:100}") private int uploadMax=100;
    public RequestBudgetController(JdbcRequestBudget budget){this.budget=budget;}
    public record Request(@NotBlank @Size(max=64) String clientIp,Long userId,@NotBlank @Pattern(regexp="WRITE|SEARCH|UPLOAD") String operation){}
    public record Decision(boolean allowed,long retryAfterSeconds){}
    @PostMapping("/consume")
    public Result<Decision> consume(@Valid @RequestBody Request r){
        int ipLimit=r.operation().equals("SEARCH")?searchMax:writeMax;
        var limits=new java.util.ArrayList<JdbcRequestBudget.Limit>();
        String ip=digest(r.clientIp());
        limits.add(new JdbcRequestBudget.Limit((r.operation().equals("SEARCH")?"search":"write")+":ip:"+ip,ipLimit,60));
        if(r.userId()!=null){
            limits.add(new JdbcRequestBudget.Limit((r.operation().equals("SEARCH")?"search":"write")+":user:"+digest(r.userId().toString()),ipLimit,60));
            if(r.operation().equals("UPLOAD"))limits.add(new JdbcRequestBudget.Limit("upload:day:user:"+digest(r.userId()+":"+java.time.LocalDate.now(java.time.ZoneOffset.UTC)),uploadMax,86400));
        }
        var decision=budget.tryAcquire(limits);
        return Result.ok(new Decision(decision.allowed(),decision.retryAfterSeconds()));
    }
    private String digest(String value){
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
