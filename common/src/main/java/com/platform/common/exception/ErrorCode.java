package com.platform.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    CONFLICT(409),
    TOO_MANY_REQUESTS(429),
    SERVER_ERROR(500),
    REMOTE_CALL_ERROR(550);

    private final int code;

    ErrorCode(int code) {
        this.code = code;
    }
}
