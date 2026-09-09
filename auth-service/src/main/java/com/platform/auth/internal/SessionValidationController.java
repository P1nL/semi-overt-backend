package com.platform.auth.internal;
import com.platform.auth.session.DeviceSessionService;
import com.platform.kernel.exception.BusinessException;
import com.platform.kernel.util.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/internal/auth/session")
public class SessionValidationController {
    private final DeviceSessionService service;
    public SessionValidationController(DeviceSessionService service){this.service=service;}
    public record Request(@NotBlank @Size(max=8192) String token) {
        @Override public String toString(){return "Request[redacted]";}
    }
    @PostMapping("/validate")
    public Result<DeviceSessionService.Identity> validate(@Valid @RequestBody Request request){
        var identity=service.validateAccess(request.token());
        if(identity==null)throw BusinessException.unauthorized("会话已失效");
        return Result.ok(identity);
    }
}
