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
     * 构造 400 Bad Request 业务异常。
     */
    public static BusinessException badRequest(String message) {
        return new BusinessException(400, message);
    }

    /**
     * 构造 401 Unauthorized 业务异常。
     */
    public static BusinessException unauthorized(String message) {
        return new BusinessException(401, message);
    }

    /**
     * 构造 403 Forbidden 业务异常。
     */
    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message);
    }

    /**
     * 构造 404 Not Found 业务异常。
     */
    public static BusinessException notFound(String message) {
        return new BusinessException(404, message);
    }

    /**
     * 构造 409 Conflict 业务异常。
     */
    public static BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }

    /**
     * 构造 429 Too Many Requests 业务异常。
     */
    public static BusinessException tooManyRequests(String message) {
        return new BusinessException(429, message);
    }

    /**
     * 构造 429 Too Many Requests 业务异常，并附带额外数据。
     */
    public static BusinessException tooManyRequests(String message, Object details) {
        return new BusinessException(429, message, details);
    }

    /**
     * 构造 500 Internal Server Error 业务异常。
     */
    public static BusinessException serverError(String message) {
        return new BusinessException(500, message);
    }
}
