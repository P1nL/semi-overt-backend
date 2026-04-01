package com.platform.kernel.exception;

import lombok.Getter;

/**
 * 业务异常类型，用于在服务层抛出带有业务语义的错误。
 */

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

    /**
     * 执行请求。
     */
    public static BusinessException badRequest(String message) {
        return new BusinessException(400, message);
    }

    /**
     * 执行相关数据。
     */
    public static BusinessException unauthorized(String message) {
        return new BusinessException(401, message);
    }

    /**
     * 执行相关数据。
     */
    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message);
    }

    /**
     * 执行found。
     */
    public static BusinessException notFound(String message) {
        return new BusinessException(404, message);
    }

    /**
     * 执行相关数据。
     */
    public static BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }

    /**
     * 执行manyrequests。
     */
    public static BusinessException tooManyRequests(String message) {
        return new BusinessException(429, message);
    }

    /**
     * 执行manyrequests。
     */
    public static BusinessException tooManyRequests(String message, Object details) {
        return new BusinessException(429, message, details);
    }

    /**
     * 执行错误。
     */
    public static BusinessException serverError(String message) {
        return new BusinessException(500, message);
    }
}
