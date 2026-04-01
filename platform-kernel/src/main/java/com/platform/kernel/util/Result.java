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
     * 执行相关数据。
     */
    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.code = 200;
        result.message = "success";
        result.data = data;
        return result;
    }

    /**
     * 执行相关数据。
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 执行相关数据。
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return fail(code, message, null);
    }

    /**
     * 执行相关数据。
     */
    public static <T> Result<T> fail(Integer code, String message, T data) {
        Result<T> result = new Result<>();
        result.code = code;
        result.message = message;
        result.data = data;
        return result;
    }

    /**
     * 执行请求。
     */
    public static <T> Result<T> badRequest(String message) {
        return fail(400, message);
    }

    /**
     * 执行相关数据。
     */
    public static <T> Result<T> unauthorized(String message) {
        return fail(401, message);
    }

    /**
     * 执行相关数据。
     */
    public static <T> Result<T> forbidden(String message) {
        return fail(403, message);
    }

    /**
     * 执行found。
     */
    public static <T> Result<T> notFound(String message) {
        return fail(404, message);
    }

    /**
     * 执行相关数据。
     */
    public static <T> Result<T> conflict(String message) {
        return fail(409, message);
    }

    /**
     * 执行manyrequests。
     */
    public static <T> Result<T> tooManyRequests(String message) {
        return fail(429, message);
    }

    /**
     * 执行错误。
     */
    public static <T> Result<T> serverError(String message) {
        return fail(500, message);
    }
}
