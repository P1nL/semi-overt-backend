package com.platform.common.context;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserContext {
    Long userId;
    String username;
    String role;
    String traceId;
}
