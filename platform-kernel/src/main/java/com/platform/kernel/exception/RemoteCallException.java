package com.platform.kernel.exception;

import lombok.Getter;

/**
 * 远程调用异常，表示下游服务返回异常或不可用。
 */

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
