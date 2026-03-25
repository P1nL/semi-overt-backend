package com.platform.common.exception;

import lombok.Getter;

@Getter
public class RemoteCallException extends RuntimeException {

    private final Integer code;

    public RemoteCallException(String message) {
        this(message, ErrorCode.REMOTE_CALL_ERROR.getCode());
    }

    public RemoteCallException(String message, Integer code) {
        super(message);
        this.code = code;
    }
}
