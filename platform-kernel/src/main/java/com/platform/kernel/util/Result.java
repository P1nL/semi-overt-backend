package com.platform.kernel.util;

import lombok.Data;

/**
 * 统一响应模型，封装接口返回的状态码、消息和业务数据。
 */
@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    private Result() {
    }

    /**
     * 构造成功响应，并携带业务数据。
     */
    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.code = 200;
        result.message = "success";
        result.data = data;
        return result;
    }

    /**
     * 构造不带业务数据的成功响应。
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 构造失败响应，不携带附加数据。
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return fail(code, message, null);
    }

    /**
     * 构造失败响应，可选携带附加数据。
     */
    public static <T> Result<T> fail(Integer code, String message, T data) {
        Result<T> result = new Result<>();
        result.code = code;
        result.message = message;
        result.data = data;
        return result;
    }

    /**
     * 构造 400 Bad Request 响应。
     */
    public static <T> Result<T> badRequest(String message) {
        return fail(400, message);
    }

    /**
     * 构造 401 Unauthorized 响应。
     */
    public static <T> Result<T> unauthorized(String message) {
        return fail(401, message);
    }

    /**
     * 构造 403 Forbidden 响应。
     */
    public static <T> Result<T> forbidden(String message) {
        return fail(403, message);
    }

    /**
     * 构造 404 Not Found 响应。
     */
    public static <T> Result<T> notFound(String message) {
        return fail(404, message);
    }

    /**
     * 构造 409 Conflict 响应。
     */
    public static <T> Result<T> conflict(String message) {
        return fail(409, message);
    }

    /**
     * 构造 429 Too Many Requests 响应。
     */
    public static <T> Result<T> tooManyRequests(String message) {
        return fail(429, message);
    }

    /**
     * 构造 500 Internal Server Error 响应。
     */
    public static <T> Result<T> serverError(String message) {
        return fail(500, message);
    }
}
