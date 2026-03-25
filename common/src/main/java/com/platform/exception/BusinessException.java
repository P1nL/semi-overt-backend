package com.platform.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;
    private final Object details;

    public BusinessException(Integer code, String message) {
        this(code, message, null);
    }

    public BusinessException(Integer code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(400, message);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(401, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(404, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }

    public static BusinessException tooManyRequests(String message) {
        return new BusinessException(429, message);
    }

    public static BusinessException tooManyRequests(String message, Object details) {
        return new BusinessException(429, message, details);
    }

    public static BusinessException serverError(String message) {
        return new BusinessException(500, message);
    }
}
